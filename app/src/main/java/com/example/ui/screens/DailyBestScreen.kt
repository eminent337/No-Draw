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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.model.DailyBestSelection
import com.example.ui.components.LeagueFilterRow
import com.example.ui.components.NoDrawMatchCard
import com.example.ui.theme.LocalSportsPalette
import com.example.ui.viewmodel.NoDrawUiState
import kotlin.math.roundToInt

@Composable
fun DailyBestScreen(
    uiState: NoDrawUiState,
    onSelectLeague: (String) -> Unit,
    onToggleLiveOnly: () -> Unit,
    onSelectDayKey: (String?) -> Unit,
    onPinMatches: (List<String>) -> Unit,
    onMatchClick: (String) -> Unit,
    onToggleShortlist: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalSportsPalette.current
    val weeklyDays = uiState.weeklyBestTwo
    val totalWeeklyPicks = weeklyDays.sumOf { it.bestTwoMatches.size }
    val totalLiveAcrossWeek = weeklyDays.sumOf { it.liveMatchesCount }
    val allWeeklyMatchIds = weeklyDays.flatMap { day -> day.bestTwoMatches.map { it.fixture.id } }

    val displayedDays = if (uiState.selectedDailyDayKey == null) {
        weeklyDays
    } else {
        weeklyDays.filter { it.dayKey == uiState.selectedDailyDayKey }.ifEmpty { weeklyDays }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("daily_best_list"),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Weekly Summary Banner
        item(key = "weekly_summary_banner") {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, palette.noDrawPrimary.copy(alpha = 0.45f)),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CalendarMonth,
                                contentDescription = null,
                                tint = palette.noDrawPrimary,
                            )
                            Text(
                                text = "7-Day Best 2 Picks",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        if (allWeeklyMatchIds.isNotEmpty()) {
                            Button(
                                onClick = { onPinMatches(allWeeklyMatchIds) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("pin_all_weekly_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.BookmarkAdd,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Pin All ($totalWeeklyPicks)")
                            }
                        }
                    }

                    Text(
                        text = "Automatically selects the 2 matches per day least likely to draw across the entire 7-day slate.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        SummaryStatChip(
                            value = "$totalWeeklyPicks",
                            label = "Weekly Picks",
                            modifier = Modifier.weight(1f),
                        )
                        SummaryStatChip(
                            value = "${weeklyDays.count { it.bestTwoMatches.isNotEmpty() }}/7",
                            label = "Active Days",
                            modifier = Modifier.weight(1f),
                        )
                        SummaryStatChip(
                            value = "$totalLiveAcrossWeek",
                            label = "Live Now",
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        // Day-of-Week Quick Selector + Live Only Toggle
        item(key = "day_selector_row") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "all_week") {
                    FilterChip(
                        selected = uiState.selectedDailyDayKey == null,
                        onClick = { onSelectDayKey(null) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("daily_chip_all_week"),
                        label = { Text("All 7 Days") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = palette.noDrawContainer,
                            selectedLabelColor = palette.noDrawPrimary,
                        ),
                    )
                }
                items(weeklyDays, key = { "chip_${it.dayKey}" }) { day ->
                    val selected = uiState.selectedDailyDayKey == day.dayKey
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectDayKey(day.dayKey) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("daily_chip_${day.dayKey}"),
                        label = {
                            val suffix = if (day.liveMatchesCount > 0) {
                                " • LIVE"
                            } else {
                                " (${day.totalDayMatches})"
                            }
                            Text("${day.shortDayLabel}$suffix")
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = palette.noDrawContainer,
                            selectedLabelColor = palette.noDrawPrimary,
                        ),
                    )
                }
            }
        }

        // Live In-Play Toggle + League Filter
        item(key = "live_and_league_filters") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = !uiState.liveOnlyDailyBest,
                        onClick = { if (uiState.liveOnlyDailyBest) onToggleLiveOnly() },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("daily_mode_live_upcoming"),
                        label = { Text("Live & Upcoming") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = palette.homeContainer,
                            selectedLabelColor = palette.homeAccent,
                        ),
                    )
                    FilterChip(
                        selected = uiState.liveOnlyDailyBest,
                        onClick = { if (!uiState.liveOnlyDailyBest) onToggleLiveOnly() },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("daily_mode_live_only"),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.FiberManualRecord,
                                contentDescription = null,
                                tint = if (uiState.liveOnlyDailyBest) palette.noDrawPrimary else palette.lossDanger,
                                modifier = Modifier.size(10.dp),
                            )
                        },
                        label = { Text("Live In-Play Only ($totalLiveAcrossWeek)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = palette.noDrawContainer,
                            selectedLabelColor = palette.noDrawPrimary,
                        ),
                    )
                }

                LeagueFilterRow(
                    selectedLeagueCode = uiState.selectedLeagueCode,
                    onSelectLeague = onSelectLeague,
                    leagues = uiState.availableLeagues,
                )
            }
        }

        // Day-by-Day Best 2 Sections
        displayedDays.forEach { day ->
            item(key = "day_section_${day.dayKey}") {
                DailyBestSection(
                    day = day,
                    onPinDay = { onPinMatches(day.bestTwoMatches.map { it.fixture.id }) },
                    onMatchClick = onMatchClick,
                    onToggleShortlist = onToggleShortlist,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun DailyBestSection(
    day: DailyBestSelection,
    onPinDay: () -> Unit,
    onMatchClick: (String) -> Unit,
    onToggleShortlist: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalSportsPalette.current
    var showAllMatches by remember(day.dayKey) { mutableStateOf(false) }
    val allDayList = if (day.allDayMatches.isNotEmpty()) day.allDayMatches else day.bestTwoMatches
    val visibleMatches = if (showAllMatches) allDayList else day.bestTwoMatches

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Day Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = day.dayLabel,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    if (day.liveMatchesCount > 0) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = palette.noDrawContainer,
                        ) {
                            Text(
                                text = "${day.liveMatchesCount} LIVE",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = palette.noDrawPrimary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                val combinedPct = (day.combinedTwoPickNoDrawProb * 100.0).roundToInt()
                val combinedOdds = com.example.data.model.OddsFormatter.formatOdds(day.combinedTwoPickNoDrawProb)
                Text(
                    text = if (day.bestTwoMatches.isNotEmpty()) {
                        "Best ${day.bestTwoMatches.size} of ${day.totalDayMatches} • 2-Pick: $combinedPct% (Odds $combinedOdds)"
                    } else {
                        "0 qualifying matches scheduled"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (day.bestTwoMatches.isNotEmpty()) {
                OutlinedButton(
                    onClick = onPinDay,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("pin_day_${day.dayKey}"),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = "Pin ${day.bestTwoMatches.size}",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }

        if (visibleMatches.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, palette.cardBorder),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.EventBusy,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = "No active matches found on this day under current filters.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            visibleMatches.forEachIndexed { index, match ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (index < 2) {
                        Surface(
                            shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 4.dp, bottomEnd = 4.dp),
                            color = if (index == 0) palette.noDrawContainer else palette.homeContainer,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.EmojiEvents,
                                    contentDescription = null,
                                    tint = if (index == 0) palette.noDrawPrimary else palette.homeAccent,
                                    modifier = Modifier.size(14.dp),
                                )
                                Text(
                                    text = "#${index + 1} BEST PICK OF THE DAY",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (index == 0) palette.noDrawPrimary else palette.homeAccent,
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "#${index + 1} RANKED ON ${day.shortDayLabel.uppercase()}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp, start = 4.dp),
                        )
                    }
                    NoDrawMatchCard(
                        match = match,
                        onClick = { onMatchClick(match.fixture.id) },
                        onToggleShortlist = { onToggleShortlist(match.fixture.id) },
                    )
                }
            }

            if (allDayList.size > day.bestTwoMatches.size) {
                TextButton(
                    onClick = { showAllMatches = !showAllMatches },
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("toggle_all_day_${day.dayKey}"),
                ) {
                    Text(
                        text = if (showAllMatches) {
                            "Show Top 2 Only"
                        } else {
                            "Show all ${allDayList.size} weekly matches for ${day.shortDayLabel}"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = palette.noDrawPrimary,
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryStatChip(
    value: String,
    label: String,
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
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = palette.noDrawPrimary,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
