package com.example

import com.example.data.model.MatchOutcome
import com.example.data.model.MatchState
import com.example.data.model.RawMatchFixture
import com.example.data.model.TeamSummary
import com.example.domain.analytics.MatchAnalyticsEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchAnalyticsEngineTest {

    private fun team(id: String, name: String) = TeamSummary(
        id = id,
        name = name,
        shortName = name,
        abbreviation = name.take(3).uppercase(),
        logoUrl = null,
        leagueCode = "eng.1",
        leagueName = "Premier League",
    )

    private fun completedMatch(
        id: String,
        home: TeamSummary,
        away: TeamSummary,
        hScore: Int,
        aScore: Int,
        epoch: Long,
    ) = RawMatchFixture(
        id = id,
        leagueCode = "eng.1",
        leagueName = "Premier League",
        dateIso = "2026-11-01T15:00:00Z",
        dateEpochMillis = epoch,
        state = MatchState.COMPLETED,
        statusDetail = "FT",
        venueName = null,
        homeTeam = home,
        awayTeam = away,
        homeScore = hScore,
        awayScore = aScore,
        homeFormString = null,
        awayFormString = null,
        homeRecordSummary = null,
        awayRecordSummary = null,
    )

    private fun upcomingMatch(
        id: String,
        home: TeamSummary,
        away: TeamSummary,
    ) = RawMatchFixture(
        id = id,
        leagueCode = "eng.1",
        leagueName = "Premier League",
        dateIso = "2026-11-15T15:00:00Z",
        dateEpochMillis = 1_800_000_000_000L,
        state = MatchState.UPCOMING,
        statusDetail = "Scheduled",
        venueName = null,
        homeTeam = home,
        awayTeam = away,
        homeScore = null,
        awayScore = null,
        homeFormString = null,
        awayFormString = null,
        homeRecordSummary = null,
        awayRecordSummary = null,
    )

    @Test
    fun buildsTeamProfilesAndRanksDecisiveMatchHigherThanDrawProneMatch() {
        val tAttack = team("1", "City")
        val tWeak = team("2", "Rovers")
        val tDrawA = team("3", "United")
        val tDrawB = team("4", "Athletic")

        val history = listOf(
            // City wins decisively in 4 straight non-draw matches
            completedMatch("h1", tAttack, tWeak, 3, 0, 1000L),
            completedMatch("h2", tAttack, tDrawA, 3, 1, 2000L),
            completedMatch("h3", tWeak, tAttack, 0, 4, 3000L),
            completedMatch("h4", tAttack, tDrawB, 2, 0, 4000L),
            // Rovers also never draws (losses)
            completedMatch("h5", tDrawA, tWeak, 2, 1, 5000L),
            completedMatch("h6", tWeak, tDrawB, 1, 3, 6000L),
            // United vs Athletic keep drawing 1-1 / 0-0
            completedMatch("h7", tDrawA, tDrawB, 1, 1, 7000L),
            completedMatch("h8", tDrawB, tDrawA, 0, 0, 8000L),
            completedMatch("h9", tDrawA, tDrawB, 1, 1, 9000L),
        )

        val upcoming = listOf(
            upcomingMatch("u_decisive", tAttack, tWeak),
            upcomingMatch("u_drawy", tDrawA, tDrawB),
        )

        val allFixtures = history + upcoming
        val profiles = MatchAnalyticsEngine.buildTeamProfiles(allFixtures)

        assertEquals(4, profiles.size)
        val cityProfile = profiles.getValue("1")
        assertEquals(4, cityProfile.nonDrawStreak)
        assertEquals(0.0, cityProfile.drawRate, 0.001)
        assertTrue(cityProfile.recentForm.all { it == MatchOutcome.WIN })

        val analyzed = MatchAnalyticsEngine.analyzeFixtures(upcoming, profiles)
            .associateBy { it.fixture.id }

        val decisiveAnalysis = analyzed.getValue("u_decisive").analysis
        val drawyAnalysis = analyzed.getValue("u_drawy").analysis

        // Probabilities must sum to 1.0
        assertEquals(1.0, decisiveAnalysis.homeWinProb + decisiveAnalysis.drawProb + decisiveAnalysis.awayWinProb, 0.01)
        assertEquals(1.0, drawyAnalysis.homeWinProb + drawyAnalysis.drawProb + drawyAnalysis.awayWinProb, 0.01)

        // Decisive fixture must have substantially higher No-Draw probability than draw-prone fixture
        assertTrue(
            "Expected decisive noDrawProb (${decisiveAnalysis.noDrawProb}) > drawy noDrawProb (${drawyAnalysis.noDrawProb})",
            decisiveAnalysis.noDrawProb > drawyAnalysis.noDrawProb + 0.12
        )
    }

    @Test
    fun moshiParsesBothScoreboardStringScoresAndScheduleObjectScores() {
        val moshi = com.example.data.remote.NetworkModule.moshi
        val adapter = moshi.adapter(com.example.data.model.EspnScoreboardResponse::class.java)

        val json = """
            {
              "day": {"date": "2026-11-14"},
              "leagues": [
                {
                  "id": "23",
                  "uid": "s:600~l:23",
                  "name": "English Premier League",
                  "slug": "eng.1",
                  "calendar": ["2026-11-08T00:00Z", {"startDate": "2026-11-14T00:00Z"}]
                }
              ],
              "events": [
                {
                  "id": "101",
                  "uid": "s:600~l:23~e:101",
                  "date": "2026-11-14T15:00Z",
                  "competitions": [
                    {
                      "competitors": [
                        {"id": "1", "homeAway": "home", "score": "3", "team": {"id": "1", "displayName": "Arsenal"}},
                        {"id": "2", "homeAway": "away", "score": {"displayValue": "1", "value": 1.0}, "team": {"id": "2", "displayName": "Chelsea"}}
                      ]
                    }
                  ],
                  "status": {"type": {"state": "post", "shortDetail": "FT"}}
                }
              ]
            }
        """.trimIndent()

        val parsed = adapter.fromJson(json)
        assertEquals("2026-11-14", parsed?.day?.date)
        assertEquals(2, parsed?.leagues?.firstOrNull()?.calendar?.size)
        val competitors = parsed?.events?.firstOrNull()?.competitions?.firstOrNull()?.competitors
        assertEquals("3", competitors?.get(0)?.score?.value)
        assertEquals("1", competitors?.get(1)?.score?.value)
    }

    @Test
    fun selectDailyBestTwoForWeekPicksTopTwoNonDrawMatchesPerDay() {
        val utc = java.util.TimeZone.getTimeZone("UTC")
        val cal = java.util.Calendar.getInstance(utc).apply {
            set(2026, java.util.Calendar.NOVEMBER, 14, 12, 0, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val day0Noon = cal.timeInMillis
        val day1Noon = day0Noon + 24 * 3_600_000L

        val strongHome = team("10", "StrongHome")
        val weakAway = team("11", "WeakAway")
        val evenA = team("12", "EvenA")
        val evenB = team("13", "EvenB")

        val history = listOf(
            completedMatch("p1", strongHome, weakAway, 4, 0, day0Noon - 10 * 86_400_000L),
            completedMatch("p2", strongHome, evenA, 3, 0, day0Noon - 7 * 86_400_000L),
            completedMatch("p3", evenA, evenB, 1, 1, day0Noon - 5 * 86_400_000L),
            completedMatch("p4", evenB, evenA, 0, 0, day0Noon - 3 * 86_400_000L),
        )

        fun dayFixture(id: String, h: TeamSummary, a: TeamSummary, epoch: Long, state: MatchState) =
            RawMatchFixture(
                id = id,
                leagueCode = "eng.1",
                leagueName = "Premier League",
                dateIso = "2026-11-14T15:00:00Z",
                dateEpochMillis = epoch,
                state = state,
                statusDetail = if (state == MatchState.LIVE) "45'" else "Scheduled",
                venueName = null,
                homeTeam = h,
                awayTeam = a,
                homeScore = if (state == MatchState.LIVE) 1 else null,
                awayScore = if (state == MatchState.LIVE) 0 else null,
                homeFormString = null,
                awayFormString = null,
                homeRecordSummary = null,
                awayRecordSummary = null,
            )

        val slate = listOf(
            dayFixture("d0_best", strongHome, weakAway, day0Noon, MatchState.LIVE),
            dayFixture("d0_second", strongHome, evenA, day0Noon + 3_600_000L, MatchState.UPCOMING),
            dayFixture("d0_worst", evenA, evenB, day0Noon + 7_200_000L, MatchState.UPCOMING),
            dayFixture("d1_only", strongHome, evenB, day1Noon, MatchState.UPCOMING),
        )

        val profiles = MatchAnalyticsEngine.buildTeamProfiles(history + slate)
        val analyzed = MatchAnalyticsEngine.analyzeFixtures(slate, profiles)

        val weekly = MatchAnalyticsEngine.selectDailyBestTwoForWeek(
            analyzedMatches = analyzed,
            nowMillis = day0Noon,
            daysCount = 7,
            timeZone = utc,
        )

        assertEquals(7, weekly.size)
        // Day 0 had 3 active matches, should pick exactly the best 2 (excluding d0_worst) while keeping all 3 in allDayMatches
        assertEquals(3, weekly[0].totalDayMatches)
        assertEquals(1, weekly[0].liveMatchesCount)
        assertEquals(2, weekly[0].bestTwoMatches.size)
        assertEquals(listOf("d0_best", "d0_second"), weekly[0].bestTwoMatches.map { it.fixture.id })
        assertEquals(listOf("d0_best", "d0_second", "d0_worst"), weekly[0].allDayMatches.map { it.fixture.id })

        // Day 1 had 1 match
        assertEquals(1, weekly[1].bestTwoMatches.size)
        assertEquals("d1_only", weekly[1].bestTwoMatches.first().fixture.id)

        // Live-only filter returns only the live match on day 0
        val liveOnlyWeekly = MatchAnalyticsEngine.selectDailyBestTwoForWeek(
            analyzedMatches = analyzed,
            nowMillis = day0Noon,
            daysCount = 7,
            liveOnly = true,
            timeZone = utc,
        )
        assertEquals(1, liveOnlyWeekly[0].bestTwoMatches.size)
        assertEquals("d0_best", liveOnlyWeekly[0].bestTwoMatches.first().fixture.id)
    }

    @Test
    fun impliedDecimalOddsComputesFairNoDrawOdds() {
        assertEquals(1.25, com.example.data.model.OddsFormatter.impliedDecimalOdds(0.80), 0.001)
        assertEquals(2.00, com.example.data.model.OddsFormatter.impliedDecimalOdds(0.50), 0.001)
        assertEquals("1.25", com.example.data.model.OddsFormatter.formatOdds(0.80))
        assertEquals("2.00", com.example.data.model.OddsFormatter.formatOdds(0.50))
        // Clamps extreme probabilities safely
        assertEquals("1.02", com.example.data.model.OddsFormatter.formatOdds(1.0))
        assertEquals("20.00", com.example.data.model.OddsFormatter.formatOdds(0.01))
    }
}
