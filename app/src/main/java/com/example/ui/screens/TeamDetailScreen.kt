package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.AnalyzedMatch
import com.example.data.model.TeamMatchProfile
import com.example.ui.components.FormBadgeRow
import com.example.ui.components.NoDrawMatchCard
import com.example.ui.components.TeamCrest
import com.example.ui.theme.LocalSportsPalette
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamDetailScreen(
    profile: TeamMatchProfile?,
    teamUpcomingMatches: List<AnalyzedMatch>,
    onBack: () -> Unit,
    onMatchClick: (String) -> Unit,
    onToggleShortlist: (String) -> Unit,
) {
    val palette = LocalSportsPalette.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = profile?.team?.name ?: "Team Profile",
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
                            .testTag("team_detail_back_button"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        if (profile == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text("Team statistics unavailable.")
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Team Header Stats Card
            item(key = "team_summary_card") {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, palette.cardBorder),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TeamCrest(
                                logoUrl = profile.team.logoUrl,
                                abbreviation = profile.team.abbreviation,
                                teamName = profile.team.name,
                                size = 56.dp,
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = profile.team.name,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = "${profile.team.leagueName} • Record ${profile.wins}W-${profile.draws}D-${profile.losses}L",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                FormBadgeRow(outcomes = profile.recentForm)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            StatPill(
                                label = "Draw Rate",
                                value = "${(profile.drawRate * 100).roundToInt()}%",
                                modifier = Modifier.weight(1f),
                            )
                            StatPill(
                                label = "No-Draw Streak",
                                value = "${profile.nonDrawStreak}",
                                modifier = Modifier.weight(1f),
                            )
                            StatPill(
                                label = "Decisive Index",
                                value = "${profile.decisiveIndex}",
                                modifier = Modifier.weight(1f),
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            StatPill(
                                label = "Goals For/M",
                                value = "%.2f".format(profile.avgGoalsScored),
                                modifier = Modifier.weight(1f),
                            )
                            StatPill(
                                label = "Goals Ag/M",
                                value = "%.2f".format(profile.avgGoalsConceded),
                                modifier = Modifier.weight(1f),
                            )
                            StatPill(
                                label = "Over 2.5 %",
                                value = "${(profile.over25Rate * 100).roundToInt()}%",
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            if (teamUpcomingMatches.isNotEmpty()) {
                item(key = "upcoming_header") {
                    Text(
                        text = "Upcoming Fixtures & No-Draw Rating",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                items(teamUpcomingMatches, key = { "up_${it.fixture.id}" }) { match ->
                    NoDrawMatchCard(
                        match = match,
                        onClick = { onMatchClick(match.fixture.id) },
                        onToggleShortlist = { onToggleShortlist(match.fixture.id) },
                    )
                }
            }

            item(key = "prev_header") {
                Text(
                    text = "Previous Matches Played (${profile.previousMatches.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            items(profile.previousMatches, key = { it.eventId }) { rec ->
                Card(
                    shape = MaterialTheme.shapes.small,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, palette.cardBorder),
                ) {
                    PreviousMatchRowItem(
                        record = rec,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
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
                color = palette.noDrawPrimary,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
