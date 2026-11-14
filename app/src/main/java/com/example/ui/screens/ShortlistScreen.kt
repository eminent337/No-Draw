package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.components.NoDrawMatchCard
import com.example.ui.theme.LocalSportsPalette
import com.example.ui.viewmodel.NoDrawUiState
import kotlin.math.roundToInt

@Composable
fun ShortlistScreen(
    uiState: NoDrawUiState,
    onMatchClick: (String) -> Unit,
    onToggleShortlist: (String) -> Unit,
    onClearShortlist: () -> Unit,
    onGoToRadar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalSportsPalette.current
    val picks = uiState.shortlistedMatches

    if (picks.isEmpty()) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.BookmarkBorder,
                contentDescription = null,
                tint = palette.noDrawPrimary,
                modifier = Modifier.size(56.dp),
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.empty_shortlist_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.empty_shortlist_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onGoToRadar,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("empty_shortlist_explore_button"),
            ) {
                Text("Explore No-Draw Radar")
            }
        }
        return
    }

    val avgNoDrawPct = (picks.map { it.analysis.noDrawProb }.average() * 100.0).roundToInt()
    val jointNoDrawProb = picks.fold(1.0) { acc, m -> acc * m.analysis.noDrawProb }
    val jointPct = (jointNoDrawProb * 100.0).roundToInt()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("shortlist_match_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "shortlist_summary") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = palette.noDrawContainer),
                border = BorderStroke(1.dp, palette.noDrawPrimary.copy(alpha = 0.4f)),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                text = "Pinned Decisive Shortlist",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "${picks.size} selected fixture${if (picks.size == 1) "" else "s"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        OutlinedButton(
                            onClick = onClearShortlist,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("clear_shortlist_button"),
                        ) {
                            Icon(
                                Icons.Outlined.DeleteOutline,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Text("Clear", modifier = Modifier.padding(start = 4.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "$avgNoDrawPct%",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = palette.noDrawPrimary,
                            )
                            Text(
                                text = "Avg Match No-Draw",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "$jointPct%",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = palette.homeAccent,
                            )
                            Text(
                                text = "Joint No-Draw (Odds ${com.example.data.model.OddsFormatter.formatOdds(jointNoDrawProb)})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        items(picks, key = { it.fixture.id }) { match ->
            NoDrawMatchCard(
                match = match,
                onClick = { onMatchClick(match.fixture.id) },
                onToggleShortlist = { onToggleShortlist(match.fixture.id) },
            )
        }
    }
}
