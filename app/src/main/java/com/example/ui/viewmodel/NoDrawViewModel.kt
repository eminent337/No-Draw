package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.NoDrawDatabase
import com.example.data.model.AnalyzedMatch
import com.example.data.model.DailyBestSelection
import com.example.data.model.LeagueConfig
import com.example.data.model.MatchSortOrder
import com.example.data.model.MatchState
import com.example.data.model.NoDrawTier
import com.example.data.model.SupportedLeagues
import com.example.data.model.TeamMatchProfile
import com.example.data.model.TeamSortOrder
import com.example.data.remote.NetworkModule
import com.example.data.repository.SportsAnalyticsRepository
import com.example.domain.analytics.MatchAnalyticsEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class NoDrawUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val lastSyncMillis: Long = 0L,
    val darkTheme: Boolean = true,
    val selectedLeagueCode: String = "all",
    val minNoDrawPercent: Int = 62,
    val matchSortOrder: MatchSortOrder = MatchSortOrder.HIGHEST_NO_DRAW,
    val teamSortOrder: TeamSortOrder = TeamSortOrder.LOWEST_DRAW_RATE,
    val searchQuery: String = "",
    val liveOnlyDailyBest: Boolean = false,
    val selectedDailyDayKey: String? = null,
    val availableLeagues: List<LeagueConfig> = SupportedLeagues.CORE_LEAGUES,
    val totalPreviousMatchesAnalyzed: Int = 0,
    val totalUpcomingMatchesAnalyzed: Int = 0,
    val totalTeamsIdentified: Int = 0,
    val eliteLocksCount: Int = 0,
    val topNoDrawPicks: List<AnalyzedMatch> = emptyList(),
    val topNoDrawTeams: List<TeamMatchProfile> = emptyList(),
    val weeklyBestTwo: List<DailyBestSelection> = emptyList(),
    val upcomingMatches: List<AnalyzedMatch> = emptyList(),
    val completedMatches: List<AnalyzedMatch> = emptyList(),
    val identifiedTeams: List<TeamMatchProfile> = emptyList(),
    val shortlistedMatches: List<AnalyzedMatch> = emptyList(),
    val allAnalyzedById: Map<String, AnalyzedMatch> = emptyMap(),
    val allTeamsById: Map<String, TeamMatchProfile> = emptyMap(),
    val nextMatchByTeamId: Map<String, AnalyzedMatch> = emptyMap(),
)

private data class FilterPrefs(
    val selectedLeagueCode: String = "all",
    val minNoDrawPercent: Int = 62,
    val matchSortOrder: MatchSortOrder = MatchSortOrder.HIGHEST_NO_DRAW,
    val teamSortOrder: TeamSortOrder = TeamSortOrder.LOWEST_DRAW_RATE,
    val searchQuery: String = "",
    val darkTheme: Boolean = true,
    val liveOnlyDailyBest: Boolean = false,
    val selectedDailyDayKey: String? = null,
)

private data class NetworkStatus(
    val isRefreshing: Boolean = false,
    val hasAttemptedInitialSync: Boolean = false,
    val errorMessage: String? = null,
    val lastSyncMillis: Long = 0L,
)

class NoDrawViewModel(application: Application) : AndroidViewModel(application) {

    private val database = NoDrawDatabase.getInstance(application)
    private val repository = SportsAnalyticsRepository(
        api = NetworkModule.espnSoccerApi,
        dao = database.noDrawDao(),
        prefs = application.getSharedPreferences("nodraw_sync_prefs", android.content.Context.MODE_PRIVATE),
    )

    private val filterPrefs = MutableStateFlow(FilterPrefs())
    private val networkStatus = MutableStateFlow(NetworkStatus())

    val uiState: StateFlow<NoDrawUiState> = combine(
        repository.cachedFixturesFlow,
        repository.shortlistedIdsFlow,
        repository.serverAnchorMillisFlow,
        filterPrefs,
        networkStatus,
    ) { fixtures, shortlistedIds, serverAnchor, prefs, net ->
        val teamProfiles = MatchAnalyticsEngine.buildTeamProfiles(fixtures)
        val analyzedAll = MatchAnalyticsEngine.analyzeFixtures(
            fixtures = fixtures,
            teamProfiles = teamProfiles,
            shortlistedIds = shortlistedIds,
        )

        val byId = analyzedAll.associateBy { it.fixture.id }

        val activeLeagues = fixtures
            .groupBy { it.leagueCode }
            .mapNotNull { (code, list) ->
                val upcomingCnt = list.count { it.state == MatchState.UPCOMING || it.state == MatchState.LIVE }
                val known = SupportedLeagues.CORE_LEAGUES.firstOrNull { it.code == code }
                val config = known ?: LeagueConfig(
                    code = code,
                    displayName = list.first().leagueName,
                    shortBadge = list.first().leagueName.take(4).uppercase(),
                    country = "",
                )
                upcomingCnt to config
            }
            .sortedByDescending { it.first }
            .map { it.second }
            .ifEmpty { SupportedLeagues.CORE_LEAGUES }

        val query = prefs.searchQuery.trim().lowercase()
        val matchesLeague: (String) -> Boolean = { code ->
            prefs.selectedLeagueCode == "all" || code == prefs.selectedLeagueCode
        }
        val matchesQuery: (AnalyzedMatch) -> Boolean = { item ->
            query.isBlank() ||
                item.fixture.homeTeam.name.lowercase().contains(query) ||
                item.fixture.awayTeam.name.lowercase().contains(query) ||
                item.fixture.leagueName.lowercase().contains(query)
        }

        // Anchor the 7-day week strictly to authoritative "today" (local clock when aligned with server,
        // or ESPN server clock when device clock differs), never jumping backward to stale past fixtures.
        val systemNow = System.currentTimeMillis()
        val effectiveTodayMillis = when {
            serverAnchor == null -> systemNow
            kotlin.math.abs(systemNow - serverAnchor) <= 18L * 3_600_000L -> systemNow
            else -> serverAnchor
        }
        val allActiveFixtures = analyzedAll.filter {
            it.fixture.state == MatchState.LIVE ||
                (it.fixture.state == MatchState.UPCOMING &&
                    it.fixture.dateEpochMillis >= effectiveTodayMillis - 6L * 3_600_000L)
        }

        val upcomingAndLive = allActiveFixtures
            .filter { matchesLeague(it.fixture.leagueCode) && matchesQuery(it) }

        val sortedUpcoming = when (prefs.matchSortOrder) {
            MatchSortOrder.HIGHEST_NO_DRAW -> upcomingAndLive.sortedByDescending { it.analysis.noDrawProb }
            MatchSortOrder.SOONEST_KICKOFF -> upcomingAndLive.sortedBy { it.fixture.dateEpochMillis }
            MatchSortOrder.HIGHEST_XG -> upcomingAndLive.sortedByDescending {
                it.analysis.expectedHomeGoals + it.analysis.expectedAwayGoals
            }
        }

        val topNoDrawPicks = upcomingAndLive
            .filter { (it.analysis.noDrawProb * 100.0).roundToInt() >= prefs.minNoDrawPercent }
            .sortedWith(
                compareByDescending<AnalyzedMatch> { it.analysis.noDrawProb }
                    .thenByDescending { it.analysis.confidenceScore }
            )

        val upcomingTeamIds = upcomingAndLive
            .flatMap { listOf(it.fixture.homeTeam.id, it.fixture.awayTeam.id) }
            .toSet()

        val topNoDrawTeams = teamProfiles.values
            .filter { matchesLeague(it.team.leagueCode) }
            .sortedWith(
                compareByDescending<TeamMatchProfile> { it.team.id in upcomingTeamIds }
                    .thenBy { it.drawRate }
                    .thenByDescending { it.nonDrawStreak }
                    .thenByDescending { it.decisiveIndex }
            )
            .take(15)

        val weeklyBestTwo = MatchAnalyticsEngine.selectDailyBestTwoForWeek(
            analyzedMatches = allActiveFixtures,
            nowMillis = effectiveTodayMillis,
            daysCount = 7,
            leagueCode = prefs.selectedLeagueCode,
            liveOnly = prefs.liveOnlyDailyBest,
            isRealToday = true,
        )

        val completed = analyzedAll
            .filter { it.fixture.state == MatchState.COMPLETED }
            .filter { matchesLeague(it.fixture.leagueCode) && matchesQuery(it) }
            .sortedByDescending { it.fixture.dateEpochMillis }

        val filteredTeams = teamProfiles.values
            .filter { matchesLeague(it.team.leagueCode) }
            .filter {
                query.isBlank() ||
                    it.team.name.lowercase().contains(query) ||
                    it.team.leagueName.lowercase().contains(query)
            }

        val sortedTeams = when (prefs.teamSortOrder) {
            TeamSortOrder.LOWEST_DRAW_RATE -> filteredTeams.sortedWith(
                compareBy<TeamMatchProfile> { it.drawRate }
                    .thenByDescending { it.nonDrawStreak }
            )
            TeamSortOrder.LONGEST_NO_DRAW_STREAK -> filteredTeams.sortedWith(
                compareByDescending<TeamMatchProfile> { it.nonDrawStreak }
                    .thenBy { it.drawRate }
            )
            TeamSortOrder.MOST_GOALS -> filteredTeams.sortedByDescending {
                it.avgGoalsScored + it.avgGoalsConceded
            }
        }

        val shortlisted = analyzedAll
            .filter { it.isShortlisted }
            .sortedByDescending { it.analysis.noDrawProb }

        val eliteCount = analyzedAll.count {
            (it.fixture.state == MatchState.UPCOMING || it.fixture.state == MatchState.LIVE) &&
                (it.analysis.tier == NoDrawTier.ELITE_LOCK || it.analysis.tier == NoDrawTier.STRONG_DECISIVE)
        }

        val nextMatchByTeamId = buildMap<String, AnalyzedMatch> {
            allActiveFixtures
                .sortedBy { it.fixture.dateEpochMillis }
                .forEach { match ->
                    putIfAbsent(match.fixture.homeTeam.id, match)
                    putIfAbsent(match.fixture.awayTeam.id, match)
                }
        }

        NoDrawUiState(
            isLoading = fixtures.isEmpty() && (!net.hasAttemptedInitialSync || net.isRefreshing),
            isRefreshing = net.isRefreshing,
            errorMessage = net.errorMessage,
            lastSyncMillis = net.lastSyncMillis,
            darkTheme = prefs.darkTheme,
            selectedLeagueCode = prefs.selectedLeagueCode,
            minNoDrawPercent = prefs.minNoDrawPercent,
            matchSortOrder = prefs.matchSortOrder,
            teamSortOrder = prefs.teamSortOrder,
            searchQuery = prefs.searchQuery,
            liveOnlyDailyBest = prefs.liveOnlyDailyBest,
            selectedDailyDayKey = prefs.selectedDailyDayKey,
            availableLeagues = activeLeagues,
            totalPreviousMatchesAnalyzed = fixtures.count { it.state == MatchState.COMPLETED },
            totalUpcomingMatchesAnalyzed = analyzedAll.count {
                it.fixture.state == MatchState.UPCOMING || it.fixture.state == MatchState.LIVE
            },
            totalTeamsIdentified = teamProfiles.size,
            eliteLocksCount = eliteCount,
            topNoDrawPicks = topNoDrawPicks,
            topNoDrawTeams = topNoDrawTeams,
            weeklyBestTwo = weeklyBestTwo,
            upcomingMatches = sortedUpcoming,
            completedMatches = completed,
            identifiedTeams = sortedTeams,
            shortlistedMatches = shortlisted,
            allAnalyzedById = byId,
            allTeamsById = teamProfiles,
            nextMatchByTeamId = nextMatchByTeamId,
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = NoDrawUiState(),
        )

    init {
        refreshData()
    }

    fun refreshData() {
        if (networkStatus.value.isRefreshing) return
        viewModelScope.launch {
            networkStatus.update { it.copy(isRefreshing = true, errorMessage = null) }
            val result = repository.refreshAllLeagues()
            networkStatus.update { current ->
                result.fold(
                    onSuccess = {
                        current.copy(
                            isRefreshing = false,
                            hasAttemptedInitialSync = true,
                            errorMessage = null,
                            lastSyncMillis = System.currentTimeMillis(),
                        )
                    },
                    onFailure = { err ->
                        current.copy(
                            isRefreshing = false,
                            hasAttemptedInitialSync = true,
                            errorMessage = err.message ?: "Network error while syncing fixtures.",
                        )
                    },
                )
            }
        }
    }

    fun selectLeague(leagueCode: String) {
        filterPrefs.update { it.copy(selectedLeagueCode = leagueCode) }
    }

    fun updateMinNoDrawPercent(percent: Int) {
        filterPrefs.update { it.copy(minNoDrawPercent = percent.coerceIn(50, 85)) }
    }

    fun selectMatchSortOrder(order: MatchSortOrder) {
        filterPrefs.update { it.copy(matchSortOrder = order) }
    }

    fun selectTeamSortOrder(order: TeamSortOrder) {
        filterPrefs.update { it.copy(teamSortOrder = order) }
    }

    fun updateSearchQuery(query: String) {
        filterPrefs.update { it.copy(searchQuery = query) }
    }

    fun toggleTheme() {
        filterPrefs.update { it.copy(darkTheme = !it.darkTheme) }
    }

    fun toggleLiveOnlyDailyBest() {
        filterPrefs.update { it.copy(liveOnlyDailyBest = !it.liveOnlyDailyBest) }
    }

    fun selectDailyDayKey(dayKey: String?) {
        filterPrefs.update { current ->
            val next = if (current.selectedDailyDayKey == dayKey) null else dayKey
            current.copy(selectedDailyDayKey = next)
        }
    }

    fun pinMatches(matchIds: List<String>) {
        viewModelScope.launch {
            repository.addAllToShortlist(matchIds)
        }
    }

    fun dismissError() {
        networkStatus.update { it.copy(errorMessage = null) }
    }

    fun toggleShortlist(matchId: String) {
        val currentlySaved = uiState.value.allAnalyzedById[matchId]?.isShortlisted == true
        viewModelScope.launch {
            repository.toggleShortlist(matchId, currentlySaved)
        }
    }

    fun clearShortlist() {
        viewModelScope.launch {
            repository.clearShortlist()
        }
    }
}
