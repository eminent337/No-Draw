package com.example.data.repository

import android.content.SharedPreferences
import com.example.data.local.CachedFixtureEntity
import com.example.data.local.NoDrawDao
import com.example.data.local.ShortlistEntity
import com.example.data.model.EspnCompetitor
import com.example.data.model.EspnEvent
import com.example.data.model.EspnLeagueMeta
import com.example.data.model.EspnScoreboardResponse
import com.example.data.model.MatchState
import com.example.data.model.RawMatchFixture
import com.example.data.model.SupportedLeagues
import com.example.data.model.TeamSummary
import com.example.data.remote.EspnSoccerApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class SportsAnalyticsRepository(
    private val api: EspnSoccerApi,
    private val dao: NoDrawDao,
    private val prefs: SharedPreferences? = null,
) {
    private val _serverAnchorMillis = MutableStateFlow(
        prefs?.getLong(KEY_SERVER_ANCHOR_MILLIS, 0L)?.takeIf { it > 0L }
    )
    val serverAnchorMillisFlow: StateFlow<Long?> = _serverAnchorMillis.asStateFlow()

    val cachedFixturesFlow: Flow<List<RawMatchFixture>> =
        dao.observeCachedFixtures().map { list -> list.map { it.toDomain() } }

    val shortlistedIdsFlow: Flow<Set<String>> =
        dao.observeShortlist().map { list -> list.map { it.matchId }.toSet() }

    suspend fun toggleShortlist(matchId: String, currentlySaved: Boolean) {
        if (currentlySaved) {
            dao.removeFromShortlist(matchId)
        } else {
            dao.addToShortlist(ShortlistEntity(matchId = matchId))
        }
    }

    suspend fun addAllToShortlist(matchIds: List<String>) {
        if (matchIds.isEmpty()) return
        dao.addAllToShortlist(matchIds.distinct().map { ShortlistEntity(matchId = it) })
    }

    suspend fun clearShortlist() {
        dao.clearShortlist()
    }

    /**
     * Fetches real upcoming and previous soccer matches using ESPN's single-day and default
     * scoreboard endpoints (avoiding hyphenated date ranges which ESPN rejects with HTTP 400),
     * anchored to ESPN's server clock, plus team schedule enrichment for teams in upcoming matches.
     */
    suspend fun refreshAllLeagues(): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val collected = mutableListOf<RawMatchFixture>()

            // Stage 1: Fetch default server-anchored scoreboards (no dates param)
            val allDefaultDeferred = async {
                runCatching { api.getScoreboard(league = "all", dates = null, limit = 200) }.getOrNull()
            }
            val leagueDefaultResponses = SupportedLeagues.CORE_LEAGUES.map { league ->
                league to async {
                    runCatching { api.getScoreboard(league = league.code, dates = null, limit = 100) }.getOrNull()
                }
            }

            val allDefaultResp = allDefaultDeferred.await()
            val leagueDefaults = leagueDefaultResponses.map { (league, def) -> league to def.await() }

            // Specific-league results added first so canonical league codes take precedence in distinctBy
            leagueDefaults.forEach { (league, resp) ->
                if (resp != null) {
                    collected.addAll(mapResponseEvents(resp, fallbackLeagueCode = league.code, fallbackLeagueName = league.displayName))
                }
            }
            if (allDefaultResp != null) {
                collected.addAll(mapResponseEvents(allDefaultResp, fallbackLeagueCode = "intl", fallbackLeagueName = "Global Football"))
            }

            // Determine server "today" anchor from ESPN response (independent of emulator clock)
            val anchorMillis = resolveServerAnchorMillis(allDefaultResp, leagueDefaults.mapNotNull { it.second })
            _serverAnchorMillis.value = anchorMillis
            prefs?.edit()?.putLong(KEY_SERVER_ANCHOR_MILLIS, anchorMillis)?.apply()

            // Stage 2A: Fetch single-day scoreboards on `all` for past 7 days through next 7 days (full 7-day week ahead)
            val dayOffsets = (-7..7).toList()
            val globalDayResponses = coroutineScope {
                dayOffsets.map { offset ->
                    val dateParam = formatUtcDayWithOffset(anchorMillis, offset)
                    async {
                        runCatching {
                            api.getScoreboard(league = "all", dates = dateParam, limit = 200)
                        }.getOrNull()
                    }
                }.awaitAll()
            }

            // Stage 2B: Fetch recent past + next upcoming matchdays from top club league calendars
            val topClubCodes = setOf("eng.1", "esp.1", "ita.1", "ger.1", "fra.1", "uefa.champions", "usa.1")
            val calendarFetchTasks = leagueDefaults
                .filter { (league, resp) -> league.code in topClubCodes && resp != null }
                .flatMap { (league, resp) ->
                    val calDates = resp?.leagues?.firstOrNull()?.calendar
                        ?.mapNotNull { parseIsoUtcMillis(it.isoDate.orEmpty()).takeIf { ms -> ms > 0L } }
                        ?.sorted()
                        .orEmpty()

                    val pastDays = calDates.filter { it < anchorMillis - 12 * 3_600_000L }.takeLast(4)
                    val futureDays = calDates.filter { it >= anchorMillis - 12 * 3_600_000L }.take(3)
                    (pastDays + futureDays).distinct().map { targetMs ->
                        league to formatUtcDay(targetMs)
                    }
                }

            val calendarResponses = coroutineScope {
                calendarFetchTasks.map { (league, dateParam) ->
                    async {
                        league to runCatching {
                            api.getScoreboard(league = league.code, dates = dateParam, limit = 100)
                        }.getOrNull()
                    }
                }.awaitAll()
            }

            calendarResponses.forEach { (league, resp) ->
                if (resp != null) {
                    collected.addAll(mapResponseEvents(resp, fallbackLeagueCode = league.code, fallbackLeagueName = league.displayName))
                }
            }
            globalDayResponses.forEach { resp ->
                if (resp != null) {
                    collected.addAll(mapResponseEvents(resp, fallbackLeagueCode = "intl", fallbackLeagueName = "Global Football"))
                }
            }

            // Deduplicate preserving canonical league-specific fixtures first
            val dedupedSoFar = collected.distinctBy { it.id }.toMutableList()

            // Stage 3: Enrich previous match histories for teams with upcoming/live fixtures
            val completedCountByTeam = dedupedSoFar
                .filter { it.state == MatchState.COMPLETED }
                .flatMap { listOf(it.homeTeam.id, it.awayTeam.id) }
                .groupingBy { it }
                .eachCount()

            val teamsNeedingHistory = dedupedSoFar
                .filter { it.state == MatchState.UPCOMING || it.state == MatchState.LIVE }
                .flatMap { fixture ->
                    listOf(
                        fixture.homeTeam.id to fixture.leagueCode,
                        fixture.awayTeam.id to fixture.leagueCode,
                    )
                }
                .distinctBy { it.first }
                .filter { (teamId, _) -> (completedCountByTeam[teamId] ?: 0) < 4 }
                .take(36)

            if (teamsNeedingHistory.isNotEmpty()) {
                val scheduleFixtures = coroutineScope {
                    teamsNeedingHistory.map { (teamId, leagueCode) ->
                        async {
                            val leagueInfo = SupportedLeagues.byCode(leagueCode)
                            val scheduleResp = runCatching {
                                api.getTeamSchedule(league = leagueCode, teamId = teamId)
                            }.getOrNull() ?: runCatching {
                                api.getTeamSchedule(league = "all", teamId = teamId)
                            }.getOrNull()

                            scheduleResp?.events.orEmpty().mapNotNull { event ->
                                mapEventToFixture(
                                    event = event,
                                    leagueCode = leagueCode,
                                    leagueName = leagueInfo.displayName,
                                )
                            }
                        }
                    }.awaitAll().flatten()
                }
                dedupedSoFar.addAll(scheduleFixtures)
            }

            val finalMerged = dedupedSoFar.distinctBy { it.id }
            if (finalMerged.isEmpty()) {
                throw IllegalStateException("No live or upcoming matches returned. Check internet connectivity and tap Retry.")
            }

            dao.replaceFixtures(finalMerged.map { CachedFixtureEntity.fromDomain(it) })
            finalMerged.size
        }
    }

    private fun resolveServerAnchorMillis(
        allResp: EspnScoreboardResponse?,
        leagueResps: List<EspnScoreboardResponse>,
    ): Long {
        val dayStr = allResp?.day?.date
            ?: leagueResps.firstNotNullOfOrNull { it.day?.date }
        if (!dayStr.isNullOrBlank()) {
            val parsedDay = parseIsoOrDayUtcMillis(dayStr)
            if (parsedDay > 0L) return parsedDay
        }
        val firstEventDate = allResp?.events?.firstOrNull()?.date
            ?: leagueResps.firstNotNullOfOrNull { it.events?.firstOrNull()?.date }
        if (!firstEventDate.isNullOrBlank()) {
            val parsedEvent = parseIsoUtcMillis(firstEventDate)
            if (parsedEvent > 0L) return parsedEvent
        }
        return System.currentTimeMillis()
    }

    private fun mapResponseEvents(
        response: EspnScoreboardResponse,
        fallbackLeagueCode: String,
        fallbackLeagueName: String,
    ): List<RawMatchFixture> {
        val leaguesByNumericId = mutableMapOf<String, EspnLeagueMeta>()
        response.leagues.orEmpty().forEach { meta ->
            extractLeagueNumericId(meta.uid)?.let { leaguesByNumericId[it] = meta }
            meta.id?.let { leaguesByNumericId[it] = meta }
        }
        val singleLeagueMeta = response.leagues?.singleOrNull()

        return response.events.orEmpty().mapNotNull { event ->
            val numericLeagueId = extractLeagueNumericId(event.uid)
            val matchedMeta = numericLeagueId?.let { leaguesByNumericId[it] } ?: singleLeagueMeta

            val resolvedCode = resolveCanonicalLeagueCode(matchedMeta, fallbackLeagueCode)
            val resolvedName = matchedMeta?.name?.takeIf { it.isNotBlank() } ?: fallbackLeagueName

            mapEventToFixture(
                event = event,
                leagueCode = resolvedCode,
                leagueName = resolvedName,
            )
        }
    }

    private fun resolveCanonicalLeagueCode(meta: EspnLeagueMeta?, fallbackCode: String): String {
        if (fallbackCode != "intl") return fallbackCode
        if (meta == null) return fallbackCode

        val rawSlug = meta.slug.orEmpty().lowercase(Locale.US)
        val rawAbbr = meta.abbreviation.orEmpty().lowercase(Locale.US)
        val rawName = meta.name.orEmpty().lowercase(Locale.US)

        SupportedLeagues.CORE_LEAGUES.firstOrNull { it.code == rawSlug || it.code == rawAbbr }?.let {
            return it.code
        }

        return when {
            rawAbbr == "eng.1" || rawName.contains("english premier league") -> "eng.1"
            rawAbbr == "esp.1" || rawName.contains("laliga") || rawName.contains("spanish primera") -> "esp.1"
            rawAbbr == "ita.1" || rawName.contains("italian serie a") -> "ita.1"
            rawAbbr == "ger.1" || rawName.contains("german bundesliga") -> "ger.1"
            rawAbbr == "fra.1" || rawName.contains("french ligue 1") -> "fra.1"
            rawAbbr == "uefa.champions" || rawName.contains("champions league") -> "uefa.champions"
            rawAbbr == "uefa.europa" || rawName.contains("europa league") -> "uefa.europa"
            rawAbbr == "usa.1" || rawName == "mls" || rawName.contains("major league soccer") -> "usa.1"
            rawAbbr == "uefa.nations" || rawName.contains("nations league") -> "uefa.nations"
            rawName.contains("world cup qualifying") && rawName.contains("uefa") -> "fifa.worldq.uefa"
            rawName.contains("world cup qualifying") && rawName.contains("conmebol") -> "fifa.worldq.conmebol"
            rawName.contains("friendly") || rawName.contains("friendlies") -> "fifa.friendly"
            rawSlug.isNotBlank() -> rawSlug.take(24)
            rawAbbr.isNotBlank() -> rawAbbr.replace(" ", ".")
            else -> fallbackCode
        }
    }

    private fun extractLeagueNumericId(uid: String?): String? {
        if (uid.isNullOrBlank()) return null
        val match = Regex("~l:(\\d+)").find(uid)
        return match?.groupValues?.getOrNull(1)
    }

    private fun formatUtcDayWithOffset(anchorMillis: Long, dayOffset: Int): String {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = anchorMillis
            add(Calendar.DAY_OF_YEAR, dayOffset)
        }
        return formatUtcDay(cal.timeInMillis)
    }

    private fun formatUtcDay(epochMillis: Long): String {
        val fmt = SimpleDateFormat("yyyyMMdd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return fmt.format(Date(epochMillis))
    }

    private fun mapEventToFixture(
        event: EspnEvent,
        leagueCode: String,
        leagueName: String,
    ): RawMatchFixture? {
        val eventId = event.id ?: return null
        val comp = event.competitions?.firstOrNull() ?: return null
        val competitors = comp.competitors.orEmpty()
        val home = competitors.firstOrNull { it.homeAway.equals("home", ignoreCase = true) }
            ?: competitors.getOrNull(0)
            ?: return null
        val away = competitors.firstOrNull { it.homeAway.equals("away", ignoreCase = true) }
            ?: competitors.getOrNull(1)
            ?: return null

        val statusType = comp.status?.type ?: event.status?.type
        val state = when (statusType?.state?.lowercase(Locale.US)) {
            "post" -> MatchState.COMPLETED
            "in" -> MatchState.LIVE
            else -> MatchState.UPCOMING
        }
        if (statusType?.name?.contains("POSTPONED", ignoreCase = true) == true ||
            statusType?.name?.contains("CANCELED", ignoreCase = true) == true ||
            statusType?.name?.contains("CANCELLED", ignoreCase = true) == true
        ) {
            return null
        }

        val isoDate = comp.date ?: event.date.orEmpty()
        val epochMillis = parseIsoUtcMillis(isoDate)
        val statusDetail = statusType?.shortDetail
            ?: statusType?.description
            ?: if (state == MatchState.COMPLETED) "FT" else "Scheduled"

        return RawMatchFixture(
            id = eventId,
            leagueCode = leagueCode,
            leagueName = leagueName,
            dateIso = isoDate,
            dateEpochMillis = epochMillis,
            state = state,
            statusDetail = statusDetail,
            venueName = comp.venue?.fullName,
            homeTeam = mapTeam(home, leagueCode, leagueName),
            awayTeam = mapTeam(away, leagueCode, leagueName),
            homeScore = home.score?.value?.toIntOrNull(),
            awayScore = away.score?.value?.toIntOrNull(),
            homeFormString = home.form,
            awayFormString = away.form,
            homeRecordSummary = home.records?.firstOrNull {
                it.type.equals("total", ignoreCase = true) || it.name.equals("overall", ignoreCase = true)
            }?.summary ?: home.records?.firstOrNull()?.summary,
            awayRecordSummary = away.records?.firstOrNull {
                it.type.equals("total", ignoreCase = true) || it.name.equals("overall", ignoreCase = true)
            }?.summary ?: away.records?.firstOrNull()?.summary,
        )
    }

    private fun mapTeam(
        competitor: EspnCompetitor,
        leagueCode: String,
        leagueName: String,
    ): TeamSummary {
        val team = competitor.team
        val fullName = team?.displayName ?: team?.shortDisplayName ?: "Unknown Team"
        val shortName = team?.shortDisplayName ?: fullName
        val abbr = team?.abbreviation ?: shortName.take(3).uppercase(Locale.US)
        val logoUrl = team?.logo ?: team?.logos?.firstOrNull()?.href
        return TeamSummary(
            id = team?.id ?: competitor.id ?: fullName,
            name = fullName,
            shortName = shortName,
            abbreviation = abbr,
            logoUrl = logoUrl,
            leagueCode = leagueCode,
            leagueName = leagueName,
        )
    }

    private fun parseIsoOrDayUtcMillis(raw: String): Long {
        val iso = parseIsoUtcMillis(raw)
        if (iso > 0L) return iso
        return try {
            // Parse YYYY-MM-DD at midday local time so negative UTC offsets never roll Today back to yesterday
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            sdf.parse("${raw.take(10)} 12:00:00")?.time ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    private fun parseIsoUtcMillis(iso: String): Long {
        if (iso.isBlank()) return 0L
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        )
        for (pattern in patterns) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val date = sdf.parse(iso)
                if (date != null) return date.time
            } catch (_: Exception) {
                // try next pattern
            }
        }
        return 0L
    }

    companion object {
        private const val KEY_SERVER_ANCHOR_MILLIS = "server_anchor_millis"
    }
}
