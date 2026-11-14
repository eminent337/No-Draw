package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.SportsSoccer
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.model.MatchState
import com.example.ui.screens.DailyBestScreen
import com.example.ui.screens.MatchDetailScreen
import com.example.ui.screens.MatchesScreen
import com.example.ui.screens.RadarScreen
import com.example.ui.screens.ShortlistScreen
import com.example.ui.screens.TeamDetailScreen
import com.example.ui.screens.TeamsScreen
import com.example.ui.theme.LocalSportsPalette
import com.example.ui.theme.NoDrawTheme
import com.example.ui.viewmodel.NoDrawViewModel

object AppRoutes {
    const val RADAR = "radar"
    const val DAILY_BEST = "daily_best"
    const val MATCHES = "matches"
    const val TEAMS = "teams"
    const val SHORTLIST = "shortlist"
    const val MATCH_DETAIL = "match/{matchId}"
    const val TEAM_DETAIL = "team/{teamId}"

    fun matchDetail(matchId: String) = "match/$matchId"
    fun teamDetail(teamId: String) = "team/$teamId"
}

private data class TopLevelTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

private val topLevelTabs = listOf(
    TopLevelTab(AppRoutes.RADAR, "Radar", Icons.Filled.Analytics, Icons.Outlined.Analytics),
    TopLevelTab(AppRoutes.DAILY_BEST, "7-Day", Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth),
    TopLevelTab(AppRoutes.MATCHES, "Matches", Icons.Filled.SportsSoccer, Icons.Outlined.SportsSoccer),
    TopLevelTab(AppRoutes.TEAMS, "Teams", Icons.Filled.Groups, Icons.Outlined.Groups),
    TopLevelTab(AppRoutes.SHORTLIST, "Shortlist", Icons.Filled.Bookmark, Icons.Outlined.BookmarkBorder),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: NoDrawViewModel = viewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            NoDrawTheme(darkTheme = uiState.darkTheme) {
                NoDrawRootApp(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoDrawRootApp(viewModel: NoDrawViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: AppRoutes.RADAR
    val showTopLevelChrome = topLevelTabs.any { it.route == currentRoute }
    val palette = LocalSportsPalette.current

    fun navigateToTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                if (showTopLevelChrome) {
                    TopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "No",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground,
                                )
                                Text(
                                    text = "Draw",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.noDrawPrimary,
                                )
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = { viewModel.toggleTheme() },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("toggle_theme_button"),
                            ) {
                                Icon(
                                    imageVector = if (uiState.darkTheme) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                                    contentDescription = "Switch color theme",
                                )
                            }
                            IconButton(
                                onClick = { viewModel.refreshData() },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("refresh_data_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = "Refresh live match telemetry",
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background,
                        ),
                    )
                }
            },
            bottomBar = {
                if (showTopLevelChrome && !useRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ) {
                        topLevelTabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                modifier = Modifier.testTag("nav_tab_${tab.route}"),
                                icon = {
                                    TabIconWithBadge(
                                        tab = tab,
                                        selected = selected,
                                        shortlistCount = uiState.shortlistedMatches.size,
                                    )
                                },
                                label = {
                                    Text(
                                        text = tab.label,
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                },
                            )
                        }
                    }
                }
            },
            containerColor = MaterialTheme.colorScheme.background,
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                if (showTopLevelChrome && useRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight(),
                    ) {
                        topLevelTabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                modifier = Modifier.testTag("rail_tab_${tab.route}"),
                                icon = {
                                    TabIconWithBadge(
                                        tab = tab,
                                        selected = selected,
                                        shortlistCount = uiState.shortlistedMatches.size,
                                    )
                                },
                                label = {
                                    Text(
                                        text = tab.label,
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                },
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = AppRoutes.RADAR,
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 840.dp),
                    ) {
                        composable(AppRoutes.RADAR) {
                            RadarScreen(
                                uiState = uiState,
                                onSelectLeague = viewModel::selectLeague,
                                onUpdateMinNoDrawPercent = viewModel::updateMinNoDrawPercent,
                                onRefresh = viewModel::refreshData,
                                onMatchClick = { matchId ->
                                    navController.navigate(AppRoutes.matchDetail(matchId))
                                },
                                onTeamClick = { teamId ->
                                    navController.navigate(AppRoutes.teamDetail(teamId))
                                },
                                onToggleShortlist = viewModel::toggleShortlist,
                                onOpenDailyBest = { navigateToTab(AppRoutes.DAILY_BEST) },
                            )
                        }

                        composable(AppRoutes.DAILY_BEST) {
                            DailyBestScreen(
                                uiState = uiState,
                                onSelectLeague = viewModel::selectLeague,
                                onToggleLiveOnly = viewModel::toggleLiveOnlyDailyBest,
                                onSelectDayKey = viewModel::selectDailyDayKey,
                                onPinMatches = { ids ->
                                    viewModel.pinMatches(ids)
                                    navigateToTab(AppRoutes.SHORTLIST)
                                },
                                onMatchClick = { matchId ->
                                    navController.navigate(AppRoutes.matchDetail(matchId))
                                },
                                onToggleShortlist = viewModel::toggleShortlist,
                            )
                        }

                        composable(AppRoutes.MATCHES) {
                            MatchesScreen(
                                uiState = uiState,
                                onSearchQueryChange = viewModel::updateSearchQuery,
                                onSelectLeague = viewModel::selectLeague,
                                onSelectSortOrder = viewModel::selectMatchSortOrder,
                                onMatchClick = { matchId ->
                                    navController.navigate(AppRoutes.matchDetail(matchId))
                                },
                                onToggleShortlist = viewModel::toggleShortlist,
                            )
                        }

                        composable(AppRoutes.TEAMS) {
                            TeamsScreen(
                                uiState = uiState,
                                onSearchQueryChange = viewModel::updateSearchQuery,
                                onSelectLeague = viewModel::selectLeague,
                                onSelectSortOrder = viewModel::selectTeamSortOrder,
                                onTeamClick = { teamId ->
                                    navController.navigate(AppRoutes.teamDetail(teamId))
                                },
                                onMatchClick = { matchId ->
                                    navController.navigate(AppRoutes.matchDetail(matchId))
                                },
                            )
                        }

                        composable(AppRoutes.SHORTLIST) {
                            ShortlistScreen(
                                uiState = uiState,
                                onMatchClick = { matchId ->
                                    navController.navigate(AppRoutes.matchDetail(matchId))
                                },
                                onToggleShortlist = viewModel::toggleShortlist,
                                onClearShortlist = viewModel::clearShortlist,
                                onGoToRadar = { navigateToTab(AppRoutes.RADAR) },
                            )
                        }

                        composable(
                            route = AppRoutes.MATCH_DETAIL,
                            arguments = listOf(navArgument("matchId") { type = NavType.StringType }),
                        ) { entry ->
                            val matchId = entry.arguments?.getString("matchId").orEmpty()
                            MatchDetailScreen(
                                match = uiState.allAnalyzedById[matchId],
                                onBack = { navController.popBackStack() },
                                onToggleShortlist = viewModel::toggleShortlist,
                                onTeamClick = { teamId ->
                                    navController.navigate(AppRoutes.teamDetail(teamId))
                                },
                            )
                        }

                        composable(
                            route = AppRoutes.TEAM_DETAIL,
                            arguments = listOf(navArgument("teamId") { type = NavType.StringType }),
                        ) { entry ->
                            val teamId = entry.arguments?.getString("teamId").orEmpty()
                            val teamUpcoming = uiState.allAnalyzedById.values
                                .filter {
                                    (it.fixture.state == MatchState.UPCOMING || it.fixture.state == MatchState.LIVE) &&
                                        (it.fixture.homeTeam.id == teamId || it.fixture.awayTeam.id == teamId)
                                }
                                .sortedBy { it.fixture.dateEpochMillis }

                            TeamDetailScreen(
                                profile = uiState.allTeamsById[teamId],
                                teamUpcomingMatches = teamUpcoming,
                                onBack = { navController.popBackStack() },
                                onMatchClick = { matchId ->
                                    navController.navigate(AppRoutes.matchDetail(matchId))
                                },
                                onToggleShortlist = viewModel::toggleShortlist,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TabIconWithBadge(
    tab: TopLevelTab,
    selected: Boolean,
    shortlistCount: Int,
) {
    val icon = if (selected) tab.selectedIcon else tab.unselectedIcon
    if (tab.route == AppRoutes.SHORTLIST && shortlistCount > 0) {
        BadgedBox(
            badge = {
                Badge {
                    Text(shortlistCount.toString())
                }
            }
        ) {
            Icon(imageVector = icon, contentDescription = tab.label)
        }
    } else {
        Icon(imageVector = icon, contentDescription = tab.label)
    }
}
