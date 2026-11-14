package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.CachedFixtureEntity
import com.example.data.local.NoDrawDatabase
import com.example.data.model.MatchSortOrder
import com.example.data.model.MatchState
import com.example.data.model.RawMatchFixture
import com.example.data.model.TeamSortOrder
import com.example.data.model.TeamSummary
import com.example.domain.analytics.MatchAnalyticsEngine
import com.example.ui.screens.DailyBestScreen
import com.example.ui.screens.MatchDetailScreen
import com.example.ui.screens.MatchesScreen
import com.example.ui.screens.RadarScreen
import com.example.ui.screens.ShortlistScreen
import com.example.ui.screens.TeamDetailScreen
import com.example.ui.screens.TeamsScreen
import com.example.ui.theme.NoDrawTheme
import com.example.ui.viewmodel.NoDrawUiState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NoDrawIntegrationTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var db: NoDrawDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, NoDrawDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun buildSampleFixtures(): List<RawMatchFixture> {
        val now = System.currentTimeMillis()
        val arsenal = TeamSummary("359", "Arsenal", "Arsenal", "ARS", null, "eng.1", "Premier League")
        val wolves = TeamSummary("380", "Wolves", "Wolves", "WOL", null, "eng.1", "Premier League")
        val madrid = TeamSummary("86", "Real Madrid", "Real Madrid", "RMA", null, "esp.1", "La Liga")
        val getafe = TeamSummary("2922", "Getafe", "Getafe", "GET", null, "esp.1", "La Liga")

        fun prev(id: String, h: TeamSummary, a: TeamSummary, hs: Int, ascore: Int, daysAgo: Int) =
            RawMatchFixture(
                id = id,
                leagueCode = h.leagueCode,
                leagueName = h.leagueName,
                dateIso = "2026-11-01T15:00:00Z",
                dateEpochMillis = now - daysAgo * 86_400_000L,
                state = MatchState.COMPLETED,
                statusDetail = "FT",
                venueName = "Stadium",
                homeTeam = h,
                awayTeam = a,
                homeScore = hs,
                awayScore = ascore,
                homeFormString = "WWWWW",
                awayFormString = "LLLLL",
                homeRecordSummary = "10-1-1",
                awayRecordSummary = "2-2-8",
            )

        fun upcoming(id: String, h: TeamSummary, a: TeamSummary, hoursAhead: Int, state: MatchState = MatchState.UPCOMING) =
            RawMatchFixture(
                id = id,
                leagueCode = h.leagueCode,
                leagueName = h.leagueName,
                dateIso = "2026-11-14T15:00:00Z",
                dateEpochMillis = now + hoursAhead * 3_600_000L,
                state = state,
                statusDetail = if (state == MatchState.LIVE) "62'" else "Scheduled",
                venueName = "Emirates Stadium",
                homeTeam = h,
                awayTeam = a,
                homeScore = if (state == MatchState.LIVE) 2 else null,
                awayScore = if (state == MatchState.LIVE) 0 else null,
                homeFormString = "WWWWW",
                awayFormString = "LLLLL",
                homeRecordSummary = "10-1-1",
                awayRecordSummary = "2-2-8",
            )

        return listOf(
            prev("p1", arsenal, wolves, 3, 0, 14),
            prev("p2", arsenal, wolves, 2, 0, 7),
            prev("p3", madrid, getafe, 4, 1, 10),
            prev("p4", madrid, getafe, 2, 0, 4),
            upcoming("u1", arsenal, wolves, 1, MatchState.LIVE),
            upcoming("u2", madrid, getafe, 3, MatchState.UPCOMING),
        )
    }

    private fun buildUiState(): NoDrawUiState {
        val fixtures = buildSampleFixtures()
        val profiles = MatchAnalyticsEngine.buildTeamProfiles(fixtures)
        val analyzed = MatchAnalyticsEngine.analyzeFixtures(fixtures, profiles, shortlistedIds = setOf("u1"))
        val weekly = MatchAnalyticsEngine.selectDailyBestTwoForWeek(analyzed, nowMillis = System.currentTimeMillis())

        return NoDrawUiState(
            isLoading = false,
            isRefreshing = false,
            totalPreviousMatchesAnalyzed = 4,
            totalUpcomingMatchesAnalyzed = 2,
            totalTeamsIdentified = profiles.size,
            eliteLocksCount = 2,
            topNoDrawPicks = analyzed.filter { it.fixture.state != MatchState.COMPLETED },
            topNoDrawTeams = profiles.values.toList(),
            weeklyBestTwo = weekly,
            upcomingMatches = analyzed.filter { it.fixture.state != MatchState.COMPLETED },
            completedMatches = analyzed.filter { it.fixture.state == MatchState.COMPLETED },
            identifiedTeams = profiles.values.toList(),
            shortlistedMatches = analyzed.filter { it.isShortlisted },
            allAnalyzedById = analyzed.associateBy { it.fixture.id },
            allTeamsById = profiles,
            nextMatchByTeamId = mapOf(
                "359" to analyzed.first { it.fixture.id == "u1" },
                "380" to analyzed.first { it.fixture.id == "u1" },
                "86" to analyzed.first { it.fixture.id == "u2" },
                "2922" to analyzed.first { it.fixture.id == "u2" },
            ),
        )
    }

    @Test
    fun roomDaoAtomicReplaceAndBatchShortlistWork() = runTest {
        val dao = db.noDrawDao()
        val fixtures = buildSampleFixtures().map { CachedFixtureEntity.fromDomain(it) }
        dao.replaceFixtures(fixtures)

        val stored = dao.observeCachedFixtures().first()
        assertEquals(fixtures.size, stored.size)

        dao.addAllToShortlist(
            listOf(
                com.example.data.local.ShortlistEntity("u1"),
                com.example.data.local.ShortlistEntity("u2"),
            )
        )
        val shortlist = dao.observeShortlist().first()
        assertEquals(2, shortlist.size)
        assertTrue(shortlist.any { it.matchId == "u1" })
        assertTrue(shortlist.any { it.matchId == "u2" })
    }

    @Test
    fun allCoreScreensRenderAndHandlePrimaryInteractions() {
        val state = buildUiState()
        var clickedMatchId: String? = null
        var clickedTeamId: String? = null
        var openedDailyBest = false

        // 1. RadarScreen
        composeRule.setContent {
            NoDrawTheme {
                RadarScreen(
                    uiState = state,
                    onSelectLeague = {},
                    onUpdateMinNoDrawPercent = {},
                    onRefresh = {},
                    onMatchClick = { clickedMatchId = it },
                    onTeamClick = { clickedTeamId = it },
                    onToggleShortlist = {},
                    onOpenDailyBest = { openedDailyBest = true },
                )
            }
        }
        composeRule.onNodeWithTag("radar_open_weekly_best2").performClick()
        assertTrue(openedDailyBest)
        composeRule.onNodeWithTag("radar_best2_item_u1").performClick()
        assertEquals("u1", clickedMatchId)
        composeRule.onNodeWithTag("radar_match_list")
            .performScrollToNode(hasTestTag("top_teams_carousel"))
        composeRule.onNodeWithTag("top_teams_carousel")
            .performScrollToNode(hasTestTag("spotlight_team_359"))
        composeRule.onNodeWithTag("spotlight_team_359").performClick()
        assertEquals("359", clickedTeamId)
    }

    @Test
    fun dailyBestScreenRendersAndPinsWeeklyMatches() {
        val state = buildUiState()
        var pinnedBatchCount = 0

        composeRule.setContent {
            NoDrawTheme {
                DailyBestScreen(
                    uiState = state,
                    onSelectLeague = {},
                    onToggleLiveOnly = {},
                    onSelectDayKey = {},
                    onPinMatches = { pinnedBatchCount = it.size },
                    onMatchClick = {},
                    onToggleShortlist = {},
                )
            }
        }
        composeRule.onNodeWithText("7-Day Best 2 Picks").assertIsDisplayed()
        composeRule.onNodeWithTag("pin_all_weekly_button").performClick()
        assertEquals(2, pinnedBatchCount)
    }

    @Test
    fun matchAndTeamDetailScreensRenderCleanly() {
        val state = buildUiState()

        composeRule.setContent {
            NoDrawTheme {
                MatchDetailScreen(
                    match = state.allAnalyzedById["u1"],
                    onBack = {},
                    onToggleShortlist = {},
                    onTeamClick = {},
                )
            }
        }
        val expectedNoDrawOdds = com.example.data.model.OddsFormatter.formatOdds(
            state.allAnalyzedById.getValue("u1").analysis.noDrawProb
        )
        composeRule.onAllNodesWithText("Arsenal")[0].assertIsDisplayed()
        composeRule.onNodeWithText("All Possible Outcome Probabilities").assertExists()
        composeRule.onNodeWithText("Odds $expectedNoDrawOdds").assertExists()
    }

    @Test
    fun teamDetailScreenRendersCleanly() {
        val state = buildUiState()

        composeRule.setContent {
            NoDrawTheme {
                TeamDetailScreen(
                    profile = state.allTeamsById["359"],
                    teamUpcomingMatches = state.upcomingMatches,
                    onBack = {},
                    onMatchClick = {},
                    onToggleShortlist = {},
                )
            }
        }
        composeRule.onAllNodesWithText("Arsenal")[0].assertIsDisplayed()
    }

    @Test
    fun shortlistScreenRendersCleanly() {
        val state = buildUiState()
        val expectedNoDrawOdds = com.example.data.model.OddsFormatter.formatOdds(
            state.shortlistedMatches.first().analysis.noDrawProb
        )

        composeRule.setContent {
            NoDrawTheme {
                ShortlistScreen(
                    uiState = state,
                    onMatchClick = {},
                    onToggleShortlist = {},
                    onClearShortlist = {},
                    onGoToRadar = {},
                )
            }
        }
        composeRule.onNodeWithText("Pinned Decisive Shortlist").assertIsDisplayed()
        composeRule.onNodeWithText("Joint No-Draw (Odds $expectedNoDrawOdds)").assertExists()
    }

    @Test
    fun matchesScreenRendersAndTogglesSegments() {
        val state = buildUiState()

        composeRule.setContent {
            NoDrawTheme {
                MatchesScreen(
                    uiState = state,
                    onSearchQueryChange = {},
                    onSelectLeague = {},
                    onSelectSortOrder = { _: MatchSortOrder -> },
                    onMatchClick = {},
                    onToggleShortlist = {},
                )
            }
        }
        composeRule.onNodeWithTag("matches_week_chip_all").assertIsDisplayed()
        composeRule.onNodeWithTag("segment_previous_matches").performClick()
        composeRule.onNodeWithTag("matches_search_input").assertIsDisplayed()
    }

    @Test
    fun teamsScreenRendersCleanly() {
        val state = buildUiState()
        var clickedWeeklyMatchId: String? = null

        composeRule.setContent {
            NoDrawTheme {
                TeamsScreen(
                    uiState = state,
                    onSearchQueryChange = {},
                    onSelectLeague = {},
                    onSelectSortOrder = { _: TeamSortOrder -> },
                    onTeamClick = {},
                    onMatchClick = { clickedWeeklyMatchId = it },
                )
            }
        }
        composeRule.onNodeWithText("Previous Match Team Ledger").assertIsDisplayed()
        composeRule.onNodeWithTag("teams_ledger_list")
            .performScrollToNode(hasTestTag("team_weekly_fixture_359"))
        composeRule.onNodeWithTag("team_weekly_fixture_359").performClick()
        assertEquals("u1", clickedWeeklyMatchId)
    }
}
