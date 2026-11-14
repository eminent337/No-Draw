package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.example.data.model.AnalyzedMatch
import com.example.data.model.MatchOutcome
import com.example.data.model.MatchState
import com.example.data.model.NoDrawTier
import com.example.data.model.SupportedLeagues
import com.example.ui.theme.LocalSportsPalette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

object MatchTimeFormatter {
    fun formatKickoff(epochMillis: Long): String {
        if (epochMillis <= 0L) return "TBD"
        val fmt = SimpleDateFormat("EEE, MMM d • HH:mm", Locale.getDefault())
        return fmt.format(Date(epochMillis))
    }

    fun formatShortDate(epochMillis: Long): String {
        if (epochMillis <= 0L) return ""
        val fmt = SimpleDateFormat("MMM d", Locale.getDefault())
        return fmt.format(Date(epochMillis))
    }
}

@Composable
fun TeamCrest(
    logoUrl: String?,
    abbreviation: String,
    teamName: String,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier,
) {
    val palette = LocalSportsPalette.current
    Surface(
        modifier = modifier.size(size),
        shape = CircleShape,
        color = palette.elevatedCard,
        border = BorderStroke(1.dp, palette.cardBorder),
    ) {
        if (!logoUrl.isNullOrBlank()) {
            SubcomposeAsyncImage(
                model = logoUrl,
                contentDescription = "$teamName crest",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .padding(5.dp)
                    .clip(CircleShape),
                error = {
                    MonogramFallback(abbreviation = abbreviation)
                },
                loading = {
                    MonogramFallback(abbreviation = abbreviation)
                },
            )
        } else {
            MonogramFallback(abbreviation = abbreviation)
        }
    }
}

@Composable
private fun MonogramFallback(abbreviation: String) {
    Box(contentAlignment = Alignment.Center) {
        Text(
            text = abbreviation.take(3).uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
fun FormBadgeRow(
    outcomes: List<MatchOutcome>,
    modifier: Modifier = Modifier,
) {
    val palette = LocalSportsPalette.current
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        outcomes.take(5).forEach { outcome ->
            val (bg, fg, label) = when (outcome) {
                MatchOutcome.WIN -> Triple(palette.noDrawContainer, palette.noDrawPrimary, "W")
                MatchOutcome.DRAW -> Triple(palette.drawWarningContainer, palette.drawWarning, "D")
                MatchOutcome.LOSS -> Triple(palette.lossDangerContainer, palette.lossDanger, "L")
            }
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(bg),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = fg,
                )
            }
        }
    }
}

@Composable
fun OutcomeProbabilityBar(
    homeProb: Double,
    drawProb: Double,
    awayProb: Double,
    homeAbbr: String,
    awayAbbr: String,
    modifier: Modifier = Modifier,
) {
    val palette = LocalSportsPalette.current
    val animHome by animateFloatAsState(
        targetValue = homeProb.toFloat().coerceIn(0.05f, 0.90f),
        animationSpec = tween(durationMillis = 350),
        label = "homeProb",
    )
    val animDraw by animateFloatAsState(
        targetValue = drawProb.toFloat().coerceIn(0.05f, 0.60f),
        animationSpec = tween(durationMillis = 350),
        label = "drawProb",
    )
    val animAway by animateFloatAsState(
        targetValue = awayProb.toFloat().coerceIn(0.05f, 0.90f),
        animationSpec = tween(durationMillis = 350),
        label = "awayProb",
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "1 $homeAbbr ${(homeProb * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = palette.homeAccent,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "X ${(drawProb * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = palette.drawWarning,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "2 $awayAbbr ${(awayProb * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = palette.awayAccent,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(palette.elevatedCard),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Box(
                modifier = Modifier
                    .weight(animHome)
                    .fillMaxHeight()
                    .background(palette.homeAccent)
            )
            Box(
                modifier = Modifier
                    .weight(animDraw)
                    .fillMaxHeight()
                    .background(palette.drawWarning)
            )
            Box(
                modifier = Modifier
                    .weight(animAway)
                    .fillMaxHeight()
                    .background(palette.awayAccent)
            )
        }
    }
}

@Composable
fun LeagueFilterRow(
    selectedLeagueCode: String,
    onSelectLeague: (String) -> Unit,
    leagues: List<com.example.data.model.LeagueConfig> = SupportedLeagues.ALL,
    modifier: Modifier = Modifier,
) {
    val palette = LocalSportsPalette.current
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "all") {
            val selected = selectedLeagueCode == "all"
            FilterChip(
                selected = selected,
                onClick = { onSelectLeague("all") },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("league_chip_all"),
                label = {
                    Text(
                        text = "All Leagues",
                        style = MaterialTheme.typography.labelLarge,
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = palette.noDrawContainer,
                    selectedLabelColor = palette.noDrawPrimary,
                ),
            )
        }
        items(leagues, key = { it.code }) { league ->
            val selected = selectedLeagueCode == league.code
            FilterChip(
                selected = selected,
                onClick = { onSelectLeague(league.code) },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("league_chip_${league.code}"),
                label = {
                    Text(
                        text = league.displayName,
                        style = MaterialTheme.typography.labelLarge,
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

@Composable
fun NoDrawTierBadge(
    tier: NoDrawTier,
    modifier: Modifier = Modifier,
) {
    val palette = LocalSportsPalette.current
    val (bg, fg) = when (tier) {
        NoDrawTier.ELITE_LOCK -> palette.noDrawContainer to palette.noDrawPrimary
        NoDrawTier.STRONG_DECISIVE -> palette.homeContainer to palette.homeAccent
        NoDrawTier.MODERATE -> palette.drawWarningContainer to palette.drawWarning
        NoDrawTier.DRAW_RISK -> palette.lossDangerContainer to palette.lossDanger
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = bg,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (tier == NoDrawTier.ELITE_LOCK) {
                Icon(
                    imageVector = Icons.Filled.Bolt,
                    contentDescription = null,
                    tint = fg,
                    modifier = Modifier.size(13.dp),
                )
            }
            Text(
                text = tier.label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = fg,
            )
        }
    }
}

@Composable
fun NoDrawMatchCard(
    match: AnalyzedMatch,
    onClick: () -> Unit,
    onToggleShortlist: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalSportsPalette.current
    val fixture = match.fixture
    val analysis = match.analysis
    val noDrawPct = (analysis.noDrawProb * 100.0).roundToInt()

    val badgeColor: Color = when (analysis.tier) {
        NoDrawTier.ELITE_LOCK -> palette.noDrawPrimary
        NoDrawTier.STRONG_DECISIVE -> palette.homeAccent
        NoDrawTier.MODERATE -> palette.drawWarning
        NoDrawTier.DRAW_RISK -> palette.lossDanger
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("match_card_${fixture.id}")
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = if (analysis.tier == NoDrawTier.ELITE_LOCK) 1.25.dp else 1.dp,
            color = if (analysis.tier == NoDrawTier.ELITE_LOCK) {
                palette.noDrawPrimary.copy(alpha = 0.45f)
            } else {
                palette.cardBorder
            },
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Top Meta Row: League + Kickoff + Tier + Bookmark
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = palette.elevatedCard,
                    ) {
                        Text(
                            text = SupportedLeagues.byCode(fixture.leagueCode).shortBadge,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        )
                    }
                    Icon(
                        imageVector = Icons.Outlined.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(13.dp),
                    )
                    Text(
                        text = if (fixture.state == MatchState.LIVE) {
                            "LIVE • ${fixture.statusDetail}"
                        } else if (fixture.state == MatchState.COMPLETED) {
                            "FINAL • ${MatchTimeFormatter.formatShortDate(fixture.dateEpochMillis)}"
                        } else {
                            MatchTimeFormatter.formatKickoff(fixture.dateEpochMillis)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (fixture.state == MatchState.LIVE) {
                            palette.noDrawPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                IconButton(
                    onClick = onToggleShortlist,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("bookmark_match_${fixture.id}"),
                ) {
                    Icon(
                        imageVector = if (match.isShortlisted) {
                            Icons.Filled.Bookmark
                        } else {
                            Icons.Outlined.BookmarkBorder
                        },
                        contentDescription = if (match.isShortlisted) {
                            "Remove ${fixture.homeTeam.shortName} vs ${fixture.awayTeam.shortName} from shortlist"
                        } else {
                            "Save ${fixture.homeTeam.shortName} vs ${fixture.awayTeam.shortName} to shortlist"
                        },
                        tint = if (match.isShortlisted) palette.noDrawPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Center Matchup + Hero No-Draw Percentage Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Home team row
                    TeamMatchRow(
                        logoUrl = fixture.homeTeam.logoUrl,
                        abbr = fixture.homeTeam.abbreviation,
                        name = fixture.homeTeam.shortName,
                        score = if (fixture.state != MatchState.UPCOMING) fixture.homeScore else null,
                        nonDrawStreak = match.homeProfile?.nonDrawStreak ?: 0,
                        recentForm = match.homeProfile?.recentForm.orEmpty(),
                    )
                    // Away team row
                    TeamMatchRow(
                        logoUrl = fixture.awayTeam.logoUrl,
                        abbr = fixture.awayTeam.abbreviation,
                        name = fixture.awayTeam.shortName,
                        score = if (fixture.state != MatchState.UPCOMING) fixture.awayScore else null,
                        nonDrawStreak = match.awayProfile?.nonDrawStreak ?: 0,
                        recentForm = match.awayProfile?.recentForm.orEmpty(),
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                // No-Draw Probability Dial Box
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = palette.elevatedCard,
                    border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.5f)),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "$noDrawPct%",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor,
                        )
                        Text(
                            text = "NO DRAW",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = badgeColor.copy(alpha = 0.14f),
                        ) {
                            Text(
                                text = analysis.recommendedSideCode,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = badgeColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Odds ${com.example.data.model.OddsFormatter.formatOdds(analysis.noDrawProb)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            OutcomeProbabilityBar(
                homeProb = analysis.homeWinProb,
                drawProb = analysis.drawProb,
                awayProb = analysis.awayWinProb,
                homeAbbr = fixture.homeTeam.abbreviation,
                awayAbbr = fixture.awayTeam.abbreviation,
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Tier + Top Reason Footer
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                NoDrawTierBadge(tier = analysis.tier)
                val reason = analysis.selectionReasons.firstOrNull()
                if (!reason.isNullOrBlank()) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp),
                        )
                        Text(
                            text = reason,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TeamMatchRow(
    logoUrl: String?,
    abbr: String,
    name: String,
    score: Int?,
    nonDrawStreak: Int,
    recentForm: List<MatchOutcome>,
) {
    val palette = LocalSportsPalette.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TeamCrest(
            logoUrl = logoUrl,
            abbreviation = abbr,
            teamName = name,
            size = 32.dp,
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (nonDrawStreak >= 3) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = palette.noDrawContainer,
                    ) {
                        Text(
                            text = "${nonDrawStreak}ND",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = palette.noDrawPrimary,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                        )
                    }
                }
            }
            if (recentForm.isNotEmpty()) {
                Spacer(modifier = Modifier.height(3.dp))
                FormBadgeRow(outcomes = recentForm)
            }
        }
        if (score != null) {
            Text(
                text = score.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}
