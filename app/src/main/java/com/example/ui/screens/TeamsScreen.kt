package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.AnalyzedMatch
import com.example.data.model.MatchState
import com.example.data.model.OddsFormatter
import com.example.data.model.TeamMatchProfile
import com.example.data.model.TeamSortOrder
import com.example.ui.components.FormBadgeRow
import com.example.ui.components.LeagueFilterRow
import com.example.ui.components.MatchTimeFormatter
import com.example.ui.components.TeamCrest
import com.example.ui.theme.LocalSportsPalette
import com.example.ui.viewmodel.NoDrawUiState
import kotlin.math.roundToInt

@Composable
fun TeamsScreen(
    uiState: NoDrawUiState,
    onSearchQueryChange: (String) -> Unit,
    onSelectLeague: (String) -> Unit,
    onSelectSortOrder: (TeamSortOrder) -> Unit,
    onTeamClick: (String) -> Unit,
    onMatchClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val palette = LocalSportsPalette.current

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("teams_ledger_list"),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "teams_header") {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text(
                    text = "Previous Match Team Ledger",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "${uiState.identifiedTeams.size} teams profiled from completed fixtures",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item(key = "teams_search") {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("Search team name...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search teams") },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { onSearchQueryChange("") },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("clear_teams_search"),
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear team search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .testTag("teams_search_input"),
            )
        }

        item(key = "teams_league_filter") {
            LeagueFilterRow(
                selectedLeagueCode = uiState.selectedLeagueCode,
                onSelectLeague = onSelectLeague,
                leagues = uiState.availableLeagues,
            )
        }

        item(key = "teams_sort_row") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(TeamSortOrder.entries, key = { it.name }) { order ->
                    FilterChip(
                        selected = uiState.teamSortOrder == order,
                        onClick = { onSelectSortOrder(order) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("sort_team_${order.name.lowercase()}"),
                        label = {
                            Text(order.label, style = MaterialTheme.typography.labelMedium)
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = palette.noDrawContainer,
                            selectedLabelColor = palette.noDrawPrimary,
                        ),
                    )
                }
            }
        }

        items(uiState.identifiedTeams, key = { it.team.id }) { profile ->
            val nextMatch = uiState.nextMatchByTeamId[profile.team.id]
            TeamProfileCard(
                profile = profile,
                nextMatch = nextMatch,
                onClick = { onTeamClick(profile.team.id) },
                onNextMatchClick = { nextMatch?.let { onMatchClick(it.fixture.id) } },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

@Composable
private fun TeamProfileCard(
    profile: TeamMatchProfile,
    nextMatch: AnalyzedMatch?,
    onClick: () -> Unit,
    onNextMatchClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalSportsPalette.current
    val drawPct = (profile.drawRate * 100.0).roundToInt()
    val nonDrawPct = 100 - drawPct
    val accentColor = when {
        drawPct <= 18 -> palette.noDrawPrimary
        drawPct <= 28 -> palette.homeAccent
        else -> palette.drawWarning
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("team_card_${profile.team.id}")
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, palette.cardBorder),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TeamCrest(
                    logoUrl = profile.team.logoUrl,
                    abbreviation = profile.team.abbreviation,
                    teamName = profile.team.name,
                    size = 42.dp,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = profile.team.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${profile.team.leagueName} • ${profile.previousMatches.size} prev tracked",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = palette.elevatedCard,
                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f)),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "$drawPct%",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = accentColor,
                        )
                        Text(
                            text = "DRAW RATE",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FormBadgeRow(outcomes = profile.recentForm)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (profile.nonDrawStreak >= 2) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = palette.noDrawContainer,
                        ) {
                            Text(
                                text = "${profile.nonDrawStreak}W/L Streak",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = palette.noDrawPrimary,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = palette.elevatedCard,
                    ) {
                        Text(
                            text = "${"%.1f".format(profile.avgGoalsScored)} GF • ${"%.1f".format(profile.avgGoalsConceded)} GA",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Decisive Index: ${profile.decisiveIndex}/100 ($nonDrawPct% non-draws)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${profile.wins}W-${profile.draws}D-${profile.losses}L",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(modifier = Modifier.height(5.dp))
            LinearProgressIndicator(
                progress = { profile.decisiveIndex / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = accentColor,
                trackColor = palette.elevatedCard,
            )

            if (nextMatch != null) {
                Spacer(modifier = Modifier.height(10.dp))
                val isHome = nextMatch.fixture.homeTeam.id == profile.team.id
                val opponent = if (isHome) {
                    "vs ${nextMatch.fixture.awayTeam.shortName}"
                } else {
                    "@ ${nextMatch.fixture.homeTeam.shortName}"
                }
                val whenText = if (nextMatch.fixture.state == MatchState.LIVE) {
                    "LIVE ${nextMatch.fixture.statusDetail}"
                } else {
                    MatchTimeFormatter.formatKickoff(nextMatch.fixture.dateEpochMillis)
                }
                val ndPct = (nextMatch.analysis.noDrawProb * 100.0).roundToInt()
                val ndOdds = OddsFormatter.formatOdds(nextMatch.analysis.noDrawProb)

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("team_weekly_fixture_${profile.team.id}")
                        .clickable(onClick = onNextMatchClick),
                    shape = RoundedCornerShape(8.dp),
                    color = palette.elevatedCard,
                    border = BorderStroke(1.dp, palette.cardBorder),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "This Week: $opponent • $whenText",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "$ndPct% ND ($ndOdds)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = palette.noDrawPrimary,
                        )
                    }
                }
            }
        }
    }
}
