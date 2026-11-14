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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.MatchSortOrder
import com.example.ui.components.LeagueFilterRow
import com.example.ui.components.NoDrawMatchCard
import com.example.ui.theme.LocalSportsPalette
import com.example.ui.viewmodel.NoDrawUiState

@Composable
fun MatchesScreen(
    uiState: NoDrawUiState,
    onSearchQueryChange: (String) -> Unit,
    onSelectLeague: (String) -> Unit,
    onSelectSortOrder: (MatchSortOrder) -> Unit,
    onMatchClick: (String) -> Unit,
    onToggleShortlist: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalSportsPalette.current
    var selectedSegment by rememberSaveable { mutableIntStateOf(0) } // 0 = Upcoming, 1 = Previous Played
    var selectedWeekDayKey by rememberSaveable { mutableStateOf<String?>(null) }

    val selectedDayMatchIds = if (selectedSegment == 0 && selectedWeekDayKey != null) {
        uiState.weeklyBestTwo
            .firstOrNull { it.dayKey == selectedWeekDayKey }
            ?.allDayMatches
            ?.map { it.fixture.id }
            ?.toSet()
    } else {
        null
    }

    val displayedMatches = if (selectedSegment == 0) {
        if (selectedDayMatchIds != null) {
            uiState.upcomingMatches.filter { it.fixture.id in selectedDayMatchIds }
        } else {
            uiState.upcomingMatches
        }
    } else {
        uiState.completedMatches
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("matches_explorer_list"),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "search_bar") {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("Search team or league...") },
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = "Search matches")
                },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { onSearchQueryChange("") },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("clear_matches_search"),
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .testTag("matches_search_input"),
            )
        }

        item(key = "segment_toggle") {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                SegmentedButton(
                    selected = selectedSegment == 0,
                    onClick = { selectedSegment = 0 },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("segment_upcoming_matches"),
                ) {
                    Text("Upcoming (${uiState.upcomingMatches.size})")
                }
                SegmentedButton(
                    selected = selectedSegment == 1,
                    onClick = { selectedSegment = 1 },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("segment_previous_matches"),
                ) {
                    Text("Previous (${uiState.completedMatches.size})")
                }
            }
        }

        item(key = "league_filter") {
            LeagueFilterRow(
                selectedLeagueCode = uiState.selectedLeagueCode,
                onSelectLeague = onSelectLeague,
                leagues = uiState.availableLeagues,
            )
        }

        if (selectedSegment == 0) {
            item(key = "sort_chips") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(MatchSortOrder.entries, key = { it.name }) { order ->
                        FilterChip(
                            selected = uiState.matchSortOrder == order,
                            onClick = { onSelectSortOrder(order) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("sort_match_${order.name.lowercase()}"),
                            label = {
                                Text(order.label, style = MaterialTheme.typography.labelMedium)
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = palette.homeContainer,
                                selectedLabelColor = palette.homeAccent,
                            ),
                        )
                    }
                }
            }

            if (uiState.weeklyBestTwo.isNotEmpty()) {
                item(key = "weekly_day_chips") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item(key = "week_all") {
                            FilterChip(
                                selected = selectedWeekDayKey == null,
                                onClick = { selectedWeekDayKey = null },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("matches_week_chip_all"),
                                label = {
                                    Text("All Week (${uiState.upcomingMatches.size})", style = MaterialTheme.typography.labelMedium)
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = palette.noDrawContainer,
                                    selectedLabelColor = palette.noDrawPrimary,
                                ),
                            )
                        }
                        items(uiState.weeklyBestTwo, key = { "mw_${it.dayKey}" }) { day ->
                            val selected = selectedWeekDayKey == day.dayKey
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    selectedWeekDayKey = if (selected) null else day.dayKey
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("matches_week_chip_${day.dayKey}"),
                                label = {
                                    Text("${day.shortDayLabel} (${day.totalDayMatches})", style = MaterialTheme.typography.labelMedium)
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

        if (displayedMatches.isEmpty()) {
            item(key = "empty_list") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, palette.cardBorder),
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = if (selectedSegment == 0) {
                                "No Upcoming Fixtures Found"
                            } else {
                                "No Completed Matches Found"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Try clearing your search filter or switching to All Leagues.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        } else {
            items(displayedMatches, key = { it.fixture.id }) { match ->
                NoDrawMatchCard(
                    match = match,
                    onClick = { onMatchClick(match.fixture.id) },
                    onToggleShortlist = { onToggleShortlist(match.fixture.id) },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    }
}
