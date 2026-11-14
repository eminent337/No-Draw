package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.SportsSoccer
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.DailyBestSelection
import com.example.data.model.TeamMatchProfile
import com.example.ui.components.FormBadgeRow
import com.example.ui.components.LeagueFilterRow
import com.example.ui.components.NoDrawMatchCard
import com.example.ui.components.TeamCrest
import com.example.ui.theme.LocalSportsPalette
import com.example.ui.viewmodel.NoDrawUiState
import kotlin.math.roundToInt

@Composable
fun RadarScreen(
    uiState: NoDrawUiState,
    onSelectLeague: (String) -> Unit,
    onUpdateMinNoDrawPercent: (Int) -> Unit,
    onRefresh: () -> Unit,
    onMatchClick: (String) -> Unit,
    onTeamClick: (String) -> Unit,
    onToggleShortlist: (String) -> Unit,
    onOpenDailyBest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalSportsPalette.current

    if (uiState.isLoading) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(color = palette.noDrawPrimary)
                Text(
                    text = "Scanning live & upcoming matches and team histories...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("radar_match_list"),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (uiState.isRefreshing) {
            item(key = "sync_bar") {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = palette.noDrawPrimary,
                    trackColor = palette.elevatedCard,
                )
            }
        }

        // Hero Banner Card
        item(key = "hero_banner") {
            HeroRadarBanner(
                upcomingMatchesCount = uiState.totalUpcomingMatchesAnalyzed,
                previousMatchesCount = uiState.totalPreviousMatchesAnalyzed,
                eliteLocksCount = uiState.eliteLocksCount,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        // Today's Best 2 Quick Card linking to 7-Day Daily Best 2
        val firstActiveDay = uiState.weeklyBestTwo.firstOrNull { it.bestTwoMatches.isNotEmpty() }
        if (firstActiveDay != null) {
            item(key = "today_best_two_card") {
                TodayBestTwoPreviewCard(
                    daySelection = firstActiveDay,
                    onOpenWeekly = onOpenDailyBest,
                    onMatchClick = onMatchClick,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

        // Error banner if refresh failed and nothing loaded
        if (!uiState.errorMessage.isNullOrBlank()) {
            item(key = "error_banner") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = palette.lossDangerContainer),
                    border = BorderStroke(1.dp, palette.lossDanger.copy(alpha = 0.5f)),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = uiState.errorMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Button(
                            onClick = onRefresh,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("retry_sync_button"),
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Retry Sync")
                        }
                    }
                }
            }
        }

        // League Filter Chips
        item(key = "league_filters") {
            LeagueFilterRow(
                selectedLeagueCode = uiState.selectedLeagueCode,
                onSelectLeague = onSelectLeague,
                leagues = uiState.availableLeagues,
            )
        }

        // Spotlight Row: Teams with Highest Non-Draw Probability
        if (uiState.topNoDrawTeams.isNotEmpty()) {
            item(key = "top_teams_header") {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        text = "Teams Least Likely to Draw",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Ranked by lowest historical draw rate & decisive streaks",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item(key = "top_teams_carousel") {
                LazyRow(
                    modifier = Modifier.testTag("top_teams_carousel"),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(uiState.topNoDrawTeams, key = { "spot_${it.team.id}" }) { profile ->
                        TeamNoDrawSpotlightCard(
                            profile = profile,
                            onClick = { onTeamClick(profile.team.id) },
                        )
                    }
                }
            }
        }

        // Threshold Filter Card
        item(key = "threshold_card") {
            NoDrawThresholdCard(
                minNoDrawPercent = uiState.minNoDrawPercent,
                selectedCount = uiState.topNoDrawPicks.size,
                onUpdateMinNoDrawPercent = onUpdateMinNoDrawPercent,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        // Section Header
        item(key = "section_header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Upcoming Non-Draw Matches",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = "Best upcoming fixtures ranked by lowest draw risk",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = palette.noDrawContainer,
                ) {
                    Text(
                        text = "${uiState.topNoDrawPicks.size} PICKS",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = palette.noDrawPrimary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
        }

        if (uiState.topNoDrawPicks.isEmpty()) {
            item(key = "empty_picks") {
                EmptyRadarState(
                    minThreshold = uiState.minNoDrawPercent,
                    hasAnyUpcoming = uiState.totalUpcomingMatchesAnalyzed > 0,
                    onLowerThreshold = { onUpdateMinNoDrawPercent(55) },
                    onResetLeague = { onSelectLeague("all") },
                    onRefresh = onRefresh,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        } else {
            items(uiState.topNoDrawPicks, key = { it.fixture.id }) { match ->
                NoDrawMatchCard(
                    match = match,
                    onClick = { onMatchClick(match.fixture.id) },
                    onToggleShortlist = { onToggleShortlist(match.fixture.id) },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

        // Methodology Footer
        item(key = "disclaimer") {
            Text(
                text = stringResource(R.string.methodology_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun TeamNoDrawSpotlightCard(
    profile: TeamMatchProfile,
    onClick: () -> Unit,
) {
    val palette = LocalSportsPalette.current
    val noDrawPct = (100.0 - (profile.drawRate * 100.0)).roundToInt().coerceIn(0, 100)

    Card(
        modifier = Modifier
            .width(172.dp)
            .testTag("spotlight_team_${profile.team.id}")
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, palette.cardBorder),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TeamCrest(
                    logoUrl = profile.team.logoUrl,
                    abbreviation = profile.team.abbreviation,
                    teamName = profile.team.name,
                    size = 34.dp,
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = palette.noDrawContainer,
                ) {
                    Text(
                        text = "$noDrawPct% ND",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = palette.noDrawPrimary,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                    )
                }
            }

            Text(
                text = profile.team.shortName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = "${profile.wins}W-${profile.draws}D-${profile.losses}L • ${profile.nonDrawStreak} streak",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            FormBadgeRow(outcomes = profile.recentForm)
        }
    }
}

@Composable
private fun TodayBestTwoPreviewCard(
    daySelection: DailyBestSelection,
    onOpenWeekly: () -> Unit,
    onMatchClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalSportsPalette.current
    val combinedPct = (daySelection.combinedTwoPickNoDrawProb * 100.0).roundToInt()

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, palette.noDrawPrimary.copy(alpha = 0.4f)),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Filled.EmojiEvents,
                        contentDescription = null,
                        tint = palette.noDrawPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "${daySelection.shortDayLabel}'s Best 2 • $combinedPct%",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                TextButton(
                    onClick = onOpenWeekly,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("radar_open_weekly_best2"),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = "7-Day Week",
                        style = MaterialTheme.typography.labelLarge,
                        color = palette.noDrawPrimary,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = palette.noDrawPrimary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            daySelection.bestTwoMatches.forEachIndexed { idx, match ->
                val noDrawPct = (match.analysis.noDrawProb * 100.0).roundToInt()
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onMatchClick(match.fixture.id) }
                        .testTag("radar_best2_item_${match.fixture.id}"),
                    shape = RoundedCornerShape(10.dp),
                    color = palette.elevatedCard,
                    border = BorderStroke(1.dp, palette.cardBorder),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "#${idx + 1} ${match.fixture.homeTeam.shortName} vs ${match.fixture.awayTeam.shortName}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = palette.noDrawContainer,
                        ) {
                            val oddsText = com.example.data.model.OddsFormatter.formatOdds(match.analysis.noDrawProb)
                            Text(
                                text = "$noDrawPct% • $oddsText",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = palette.noDrawPrimary,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroRadarBanner(
    upcomingMatchesCount: Int,
    previousMatchesCount: Int,
    eliteLocksCount: Int,
    modifier: Modifier = Modifier,
) {
    val palette = LocalSportsPalette.current
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, palette.cardBorder),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Image(
                painter = painterResource(id = R.drawable.img_stadium_radar_banner),
                contentDescription = "Stadium analytics radar banner",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .clip(MaterialTheme.shapes.large),
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.45f),
                                Color.Black.copy(alpha = 0.86f),
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = palette.noDrawPrimary.copy(alpha = 0.18f),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Analytics,
                                contentDescription = null,
                                tint = palette.noDrawPrimary,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = "POISSON + FORM ENGINE",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = palette.noDrawPrimary,
                            )
                        }
                    }
                }

                Text(
                    text = stringResource(R.string.hero_title),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Text(
                    text = stringResource(R.string.hero_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.82f),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    HeroStatChip(
                        value = upcomingMatchesCount.toString(),
                        label = "Upcoming",
                        accent = palette.homeAccent,
                        modifier = Modifier.weight(1f),
                    )
                    HeroStatChip(
                        value = previousMatchesCount.toString(),
                        label = "Prev Played",
                        accent = Color.White,
                        modifier = Modifier.weight(1f),
                    )
                    HeroStatChip(
                        value = eliteLocksCount.toString(),
                        label = "Strong Picks",
                        accent = palette.noDrawPrimary,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroStatChip(
    value: String,
    label: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = Color.Black.copy(alpha = 0.48f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = accent,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.78f),
            )
        }
    }
}

@Composable
private fun NoDrawThresholdCard(
    minNoDrawPercent: Int,
    selectedCount: Int,
    onUpdateMinNoDrawPercent: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalSportsPalette.current
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, palette.cardBorder),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Tune,
                        contentDescription = null,
                        tint = palette.noDrawPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "Minimum No-Draw Confidence",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(
                    text = "$minNoDrawPercent%+ ($selectedCount)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = palette.noDrawPrimary,
                )
            }

            Slider(
                value = minNoDrawPercent.toFloat(),
                onValueChange = { onUpdateMinNoDrawPercent(it.roundToInt()) },
                valueRange = 50f..85f,
                steps = 6,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("no_draw_threshold_slider"),
                colors = SliderDefaults.colors(
                    thumbColor = palette.noDrawPrimary,
                    activeTrackColor = palette.noDrawPrimary,
                ),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(60, 65, 70, 75).forEach { preset ->
                    FilterChip(
                        selected = minNoDrawPercent == preset,
                        onClick = { onUpdateMinNoDrawPercent(preset) },
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("threshold_preset_$preset"),
                        label = {
                            Text(
                                text = "$preset%+",
                                style = MaterialTheme.typography.labelMedium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = palette.noDrawContainer,
                            selectedLabelColor = palette.noDrawPrimary,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyRadarState(
    minThreshold: Int,
    hasAnyUpcoming: Boolean,
    onLowerThreshold: () -> Unit,
    onResetLeague: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalSportsPalette.current
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, palette.cardBorder),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.SportsSoccer,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(40.dp),
            )
            Text(
                text = if (hasAnyUpcoming) {
                    "No Upcoming Matches Above $minThreshold% No-Draw"
                } else {
                    "No Upcoming Fixtures Loaded"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = if (hasAnyUpcoming) {
                    stringResource(R.string.empty_matches_body)
                } else {
                    "Tap Refresh Telemetry to fetch the latest global matchdays."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (hasAnyUpcoming) {
                    OutlinedButton(
                        onClick = onLowerThreshold,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("lower_threshold_button"),
                    ) {
                        Text("Set 55%+")
                    }
                    Button(
                        onClick = onResetLeague,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("reset_league_button"),
                    ) {
                        Text("All Leagues")
                    }
                } else {
                    Button(
                        onClick = onRefresh,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("empty_refresh_button"),
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Refresh Telemetry")
                    }
                }
            }
        }
    }
}
