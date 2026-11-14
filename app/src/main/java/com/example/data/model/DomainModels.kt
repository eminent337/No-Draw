package com.example.data.model

enum class MatchState {
    UPCOMING,
    LIVE,
    COMPLETED,
}

enum class MatchOutcome {
    WIN,
    DRAW,
    LOSS,
}

enum class NoDrawTier(val label: String) {
    ELITE_LOCK("Elite No-Draw"),
    STRONG_DECISIVE("Strong Decisive"),
    MODERATE("Moderate Edge"),
    DRAW_RISK("High Draw Risk"),
}

data class TeamSummary(
    val id: String,
    val name: String,
    val shortName: String,
    val abbreviation: String,
    val logoUrl: String?,
    val leagueCode: String,
    val leagueName: String,
)

data class PreviousMatchRecord(
    val eventId: String,
    val dateIso: String,
    val dateEpochMillis: Long,
    val leagueCode: String,
    val leagueName: String,
    val teamId: String,
    val opponentId: String,
    val opponentName: String,
    val opponentLogoUrl: String?,
    val isHome: Boolean,
    val goalsFor: Int,
    val goalsAgainst: Int,
    val outcome: MatchOutcome,
)

data class TeamMatchProfile(
    val team: TeamSummary,
    val previousMatches: List<PreviousMatchRecord>,
    val matchesPlayed: Int,
    val wins: Int,
    val draws: Int,
    val losses: Int,
    val drawRate: Double,
    val nonDrawStreak: Int,
    val recentForm: List<MatchOutcome>,
    val avgGoalsScored: Double,
    val avgGoalsConceded: Double,
    val homeAvgGoalsScored: Double,
    val homeAvgGoalsConceded: Double,
    val awayAvgGoalsScored: Double,
    val awayAvgGoalsConceded: Double,
    val cleanSheetRate: Double,
    val bttsRate: Double,
    val over25Rate: Double,
    val decisiveIndex: Int,
    val seasonRecordSummary: String?,
)

data class ScorelineProbability(
    val homeGoals: Int,
    val awayGoals: Int,
    val probability: Double,
) {
    val isDraw: Boolean get() = homeGoals == awayGoals
    val label: String get() = "$homeGoals - $awayGoals"
}

data class MatchOutcomeAnalysis(
    val homeWinProb: Double,
    val drawProb: Double,
    val awayWinProb: Double,
    val noDrawProb: Double,
    val doubleChance1X: Double,
    val doubleChanceX2: Double,
    val expectedHomeGoals: Double,
    val expectedAwayGoals: Double,
    val over25Prob: Double,
    val bttsProb: Double,
    val topScorelines: List<ScorelineProbability>,
    val scorelineGrid: List<List<Double>>, // 5x5 matrix [homeGoals][awayGoals]
    val primaryLeaning: String,
    val recommendedSideCode: String, // "1", "2", or "12"
    val confidenceScore: Int,
    val tier: NoDrawTier,
    val selectionReasons: List<String>,
) {
    val noDrawImpliedOdds: Double get() = OddsFormatter.impliedDecimalOdds(noDrawProb)
}

object OddsFormatter {
    fun impliedDecimalOdds(probability: Double): Double =
        1.0 / probability.coerceIn(0.05, 0.98)

    fun formatOdds(probability: Double): String =
        String.format(java.util.Locale.US, "%.2f", impliedDecimalOdds(probability))
}

data class RawMatchFixture(
    val id: String,
    val leagueCode: String,
    val leagueName: String,
    val dateIso: String,
    val dateEpochMillis: Long,
    val state: MatchState,
    val statusDetail: String,
    val venueName: String?,
    val homeTeam: TeamSummary,
    val awayTeam: TeamSummary,
    val homeScore: Int?,
    val awayScore: Int?,
    val homeFormString: String?,
    val awayFormString: String?,
    val homeRecordSummary: String?,
    val awayRecordSummary: String?,
)

data class AnalyzedMatch(
    val fixture: RawMatchFixture,
    val homeProfile: TeamMatchProfile?,
    val awayProfile: TeamMatchProfile?,
    val analysis: MatchOutcomeAnalysis,
    val isShortlisted: Boolean = false,
)

enum class MatchSortOrder(val label: String) {
    HIGHEST_NO_DRAW("Best No-Draw %"),
    SOONEST_KICKOFF("Soonest Kickoff"),
    HIGHEST_XG("Highest xG"),
}

enum class TeamSortOrder(val label: String) {
    LOWEST_DRAW_RATE("Lowest Draw %"),
    LONGEST_NO_DRAW_STREAK("No-Draw Streak"),
    MOST_GOALS("Goals / Match"),
}

data class DailyBestSelection(
    val dayKey: String,
    val dayLabel: String,
    val shortDayLabel: String,
    val dayStartMillis: Long,
    val totalDayMatches: Int,
    val liveMatchesCount: Int,
    val bestTwoMatches: List<AnalyzedMatch>,
    val combinedTwoPickNoDrawProb: Double,
    val allDayMatches: List<AnalyzedMatch> = emptyList(),
)
