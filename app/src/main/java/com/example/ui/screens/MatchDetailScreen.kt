package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.AnalyzedMatch
import com.example.data.model.MatchOutcome
import com.example.data.model.MatchState
import com.example.data.model.PreviousMatchRecord
import com.example.data.model.ScorelineProbability
import com.example.data.model.TeamMatchProfile
import com.example.ui.components.MatchTimeFormatter
import com.example.ui.components.NoDrawTierBadge
import com.example.ui.components.OutcomeProbabilityBar
import com.example.ui.components.TeamCrest
import com.example.ui.theme.LocalSportsPalette
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchDetailScreen(
    match: AnalyzedMatch?,
    onBack: () -> Unit,
    onToggleShortlist: (String) -> Unit,
    onTeamClick: (String) -> Unit,
) {
    val palette = LocalSportsPalette.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = match?.fixture?.leagueName ?: "Match Outcomes",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("detail_back_button"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    if (match != null) {
                        IconButton(
                            onClick = { onToggleShortlist(match.fixture.id) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("detail_bookmark_button"),
                        ) {
                            Icon(
                                imageVector = if (match.isShortlisted) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                contentDescription = "Toggle shortlist",
                                tint = if (match.isShortlisted) palette.noDrawPrimary else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        if (match == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text("Match telemetry unavailable.")
            }
            return@Scaffold
        }

        val fixture = match.fixture
        val analysis = match.analysis
        val noDrawPct = (analysis.noDrawProb * 100.0).roundToInt()

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 1. Matchup Header & Hero No-Draw Callout
            item(key = "matchup_hero") {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, palette.cardBorder),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        NoDrawTierBadge(tier = analysis.tier)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (fixture.state == MatchState.COMPLETED) {
                                "FINAL SCORE • ${MatchTimeFormatter.formatShortDate(fixture.dateEpochMillis)}"
                            } else {
                                MatchTimeFormatter.formatKickoff(fixture.dateEpochMillis)
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (!fixture.venueName.isNullOrBlank()) {
                            Text(
                                text = fixture.venueName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("detail_home_team_btn")
                                    .clickable { onTeamClick(fixture.homeTeam.id) },
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                TeamCrest(
                                    logoUrl = fixture.homeTeam.logoUrl,
                                    abbreviation = fixture.homeTeam.abbreviation,
                                    teamName = fixture.homeTeam.name,
                                    size = 56.dp,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = fixture.homeTeam.shortName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                )
                                Text(
                                    text = "HOME",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = palette.homeAccent,
                                )
                            }

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(horizontal = 8.dp),
                            ) {
                                if (fixture.state != MatchState.UPCOMING && fixture.homeScore != null && fixture.awayScore != null) {
                                    Text(
                                        text = "${fixture.homeScore} : ${fixture.awayScore}",
                                        style = MaterialTheme.typography.displayMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                } else {
                                    Text(
                                        text = "$noDrawPct%",
                                        style = MaterialTheme.typography.displayMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = palette.noDrawPrimary,
                                    )
                                    Text(
                                        text = "NO DRAW (12)",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = palette.noDrawPrimary,
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = palette.noDrawContainer,
                                ) {
                                    Text(
                                        text = analysis.primaryLeaning,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = palette.noDrawPrimary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    )
                                }
                            }

                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("detail_away_team_btn")
                                    .clickable { onTeamClick(fixture.awayTeam.id) },
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                TeamCrest(
                                    logoUrl = fixture.awayTeam.logoUrl,
                                    abbreviation = fixture.awayTeam.abbreviation,
                                    teamName = fixture.awayTeam.name,
                                    size = 56.dp,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = fixture.awayTeam.shortName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                )
                                Text(
                                    text = "AWAY",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = palette.awayAccent,
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))
                        OutcomeProbabilityBar(
                            homeProb = analysis.homeWinProb,
                            drawProb = analysis.drawProb,
                            awayProb = analysis.awayWinProb,
                            homeAbbr = fixture.homeTeam.abbreviation,
                            awayAbbr = fixture.awayTeam.abbreviation,
                        )
                    }
                }
            }

            // 2. All Possible Outcome Markets Grid
            item(key = "outcome_markets") {
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, palette.cardBorder),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = "All Possible Outcome Probabilities",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            MarketTile(
                                label = "1 (Home)",
                                value = "${(analysis.homeWinProb * 100).roundToInt()}%",
                                sublabel = "Odds ${com.example.data.model.OddsFormatter.formatOdds(analysis.homeWinProb)}",
                                accent = palette.homeAccent,
                                modifier = Modifier.weight(1f),
                            )
                            MarketTile(
                                label = "X (Draw)",
                                value = "${(analysis.drawProb * 100).roundToInt()}%",
                                sublabel = "Odds ${com.example.data.model.OddsFormatter.formatOdds(analysis.drawProb)}",
                                accent = palette.drawWarning,
                                modifier = Modifier.weight(1f),
                            )
                            MarketTile(
                                label = "2 (Away)",
                                value = "${(analysis.awayWinProb * 100).roundToInt()}%",
                                sublabel = "Odds ${com.example.data.model.OddsFormatter.formatOdds(analysis.awayWinProb)}",
                                accent = palette.awayAccent,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            MarketTile(
                                label = "12 (No Draw)",
                                value = "${(analysis.noDrawProb * 100).roundToInt()}%",
                                sublabel = "Odds ${com.example.data.model.OddsFormatter.formatOdds(analysis.noDrawProb)}",
                                accent = palette.noDrawPrimary,
                                modifier = Modifier.weight(1f),
                            )
                            MarketTile(
                                label = "Over 2.5 Goals",
                                value = "${(analysis.over25Prob * 100).roundToInt()}%",
                                sublabel = "Odds ${com.example.data.model.OddsFormatter.formatOdds(analysis.over25Prob)}",
                                accent = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            MarketTile(
                                label = "BTTS (Yes)",
                                value = "${(analysis.bttsProb * 100).roundToInt()}%",
                                sublabel = "Odds ${com.example.data.model.OddsFormatter.formatOdds(analysis.bttsProb)}",
                                accent = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            MarketTile(
                                label = "Double 1X",
                                value = "${(analysis.doubleChance1X * 100).roundToInt()}%",
                                sublabel = "Odds ${com.example.data.model.OddsFormatter.formatOdds(analysis.doubleChance1X)}",
                                accent = palette.homeAccent,
                                modifier = Modifier.weight(1f),
                            )
                            MarketTile(
                                label = "Double X2",
                                value = "${(analysis.doubleChanceX2 * 100).roundToInt()}%",
                                sublabel = "Odds ${com.example.data.model.OddsFormatter.formatOdds(analysis.doubleChanceX2)}",
                                accent = palette.awayAccent,
                                modifier = Modifier.weight(1f),
                            )
                            MarketTile(
                                label = "Proj. xG",
                                value = "${"%.2f".format(analysis.expectedHomeGoals)} - ${"%.2f".format(analysis.expectedAwayGoals)}",
                                sublabel = "Expected",
                                accent = palette.noDrawPrimary,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            // 3. Top Most Likely Scorelines + 5x5 Matrix
            item(key = "scoreline_matrix") {
                ScorelineBreakdownCard(
                    homeAbbr = fixture.homeTeam.abbreviation,
                    awayAbbr = fixture.awayTeam.abbreviation,
                    topScorelines = analysis.topScorelines,
                    grid = analysis.scorelineGrid,
                )
            }

            // 4. Statistical Selection Reasons
            item(key = "reasons_card") {
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, palette.cardBorder),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "Statistical Draw-Avoidance Factors",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        analysis.selectionReasons.forEach { reason ->
                            Row(
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = palette.noDrawPrimary,
                                    modifier = Modifier
                                        .padding(top = 2.dp)
                                        .size(16.dp),
                                )
                                Text(
                                    text = reason,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }

            // 5. Previous Matches Played by Both Teams
            item(key = "previous_matches_comparison") {
                PreviousMatchesComparisonCard(
                    homeName = fixture.homeTeam.shortName,
                    awayName = fixture.awayTeam.shortName,
                    homeProfile = match.homeProfile,
                    awayProfile = match.awayProfile,
                    onTeamClick = onTeamClick,
                )
            }
        }
    }
}

@Composable
private fun MarketTile(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
    sublabel: String? = null,
) {
    val palette = LocalSportsPalette.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = palette.elevatedCard,
        border = BorderStroke(1.dp, palette.cardBorder),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = accent,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (!sublabel.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = sublabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun ScorelineBreakdownCard(
    homeAbbr: String,
    awayAbbr: String,
    topScorelines: List<ScorelineProbability>,
    grid: List<List<Double>>,
) {
    val palette = LocalSportsPalette.current
    val maxProb = grid.flatten().maxOrNull()?.coerceAtLeast(0.01) ?: 0.15

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, palette.cardBorder),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Exact Scoreline Probability Matrix",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Most likely scorelines (draws highlighted in amber)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Top 6 scoreline chips in 2 rows of 3
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                topScorelines.chunked(3).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        rowItems.forEach { item ->
                            val chipAccent = if (item.isDraw) palette.drawWarning else palette.noDrawPrimary
                            val chipBg = if (item.isDraw) palette.drawWarningContainer else palette.noDrawContainer
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                color = chipBg,
                                border = BorderStroke(1.dp, chipAccent.copy(alpha = 0.4f)),
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(
                                        text = item.label,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = chipAccent,
                                    )
                                    Text(
                                        text = "${(item.probability * 100).roundToInt()}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$homeAbbr Goals (Rows) vs $awayAbbr Goals (Columns)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // 5x5 heatmap grid
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(modifier = Modifier.width(24.dp))
                    for (a in 0..4) {
                        Text(
                            text = a.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = palette.awayAccent,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                for (h in 0..4) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = h.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = palette.homeAccent,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(24.dp),
                        )
                        for (a in 0..4) {
                            val p = grid.getOrNull(h)?.getOrNull(a) ?: 0.0
                            val intensity = (p / maxProb).toFloat().coerceIn(0.08f, 1.0f)
                            val baseColor = when {
                                h == a -> palette.drawWarning
                                h > a -> palette.homeAccent
                                else -> palette.awayAccent
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(baseColor.copy(alpha = 0.12f + 0.52f * intensity)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "${(p * 100).roundToInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (intensity > 0.65f) FontWeight.Bold else FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviousMatchesComparisonCard(
    homeName: String,
    awayName: String,
    homeProfile: TeamMatchProfile?,
    awayProfile: TeamMatchProfile?,
    onTeamClick: (String) -> Unit,
) {
    val palette = LocalSportsPalette.current
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, palette.cardBorder),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Previous Matches Played",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            TeamRecentMatchesBlock(
                teamName = homeName,
                profile = homeProfile,
                onViewAll = { homeProfile?.let { onTeamClick(it.team.id) } },
            )

            HorizontalDivider(color = palette.cardBorder)

            TeamRecentMatchesBlock(
                teamName = awayName,
                profile = awayProfile,
                onViewAll = { awayProfile?.let { onTeamClick(it.team.id) } },
            )
        }
    }
}

@Composable
private fun TeamRecentMatchesBlock(
    teamName: String,
    profile: TeamMatchProfile?,
    onViewAll: () -> Unit,
) {
    val palette = LocalSportsPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "$teamName (${profile?.previousMatches?.size ?: 0} matches)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            if (profile != null) {
                Text(
                    text = "Draw rate: ${(profile.drawRate * 100).roundToInt()}% • View all",
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.noDrawPrimary,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clickable(onClick = onViewAll)
                        .padding(horizontal = 4.dp, vertical = 12.dp),
                )
            }
        }

        val matches = profile?.previousMatches?.take(5).orEmpty()
        if (matches.isEmpty()) {
            Text(
                text = "No recent league fixtures observed in current window.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            matches.forEach { rec ->
                PreviousMatchRowItem(record = rec)
            }
        }
    }
}

@Composable
fun PreviousMatchRowItem(
    record: PreviousMatchRecord,
    modifier: Modifier = Modifier,
) {
    val palette = LocalSportsPalette.current
    val (bg, fg, tag) = when (record.outcome) {
        MatchOutcome.WIN -> Triple(palette.noDrawContainer, palette.noDrawPrimary, "W")
        MatchOutcome.DRAW -> Triple(palette.drawWarningContainer, palette.drawWarning, "D")
        MatchOutcome.LOSS -> Triple(palette.lossDangerContainer, palette.lossDanger, "L")
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f),
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(bg),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = tag,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = fg,
                )
            }
            Text(
                text = "${if (record.isHome) "vs" else "@"} ${record.opponentName}",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = MatchTimeFormatter.formatShortDate(record.dateEpochMillis),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = palette.elevatedCard,
            ) {
                Text(
                    text = "${record.goalsFor} - ${record.goalsAgainst}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
    }
}
