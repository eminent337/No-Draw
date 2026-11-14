package com.example.domain.analytics

import com.example.data.model.AnalyzedMatch
import com.example.data.model.MatchOutcome
import com.example.data.model.MatchOutcomeAnalysis
import com.example.data.model.MatchState
import com.example.data.model.NoDrawTier
import com.example.data.model.PreviousMatchRecord
import com.example.data.model.RawMatchFixture
import com.example.data.model.ScorelineProbability
import com.example.data.model.TeamMatchProfile
import com.example.data.model.TeamSummary
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Pure statistical match outcome & draw-avoidance engine.
 *
 * 1. Identifies all teams that played previous matches from completed fixtures.
 * 2. Builds attack, defense, home/away splits, form streaks, and draw propensity per team.
 * 3. Uses a calibrated Bivariate Poisson scoreline grid (0..5 x 0..5 goals) blended with
 *    historical draw tendencies to evaluate 1/X/2, Double Chance, Over 2.5, BTTS, and
 *    rank the best matches least likely to end in a draw.
 */
object MatchAnalyticsEngine {

    private const val DEFAULT_LEAGUE_GOALS_PER_TEAM = 1.38
    private const val HOME_ADVANTAGE_FACTOR = 1.12
    private const val AWAY_FACTOR = 0.91
    private const val MAX_GRID_GOALS = 5

    fun buildTeamProfiles(allFixtures: List<RawMatchFixture>): Map<String, TeamMatchProfile> {
        val completed = allFixtures
            .filter { it.state == MatchState.COMPLETED && it.homeScore != null && it.awayScore != null }
            .sortedByDescending { it.dateEpochMillis }

        val teamSummaries = mutableMapOf<String, TeamSummary>()
        val recordsByTeam = mutableMapOf<String, MutableList<PreviousMatchRecord>>()
        val formHintByTeam = mutableMapOf<String, String>()
        val recordHintByTeam = mutableMapOf<String, String>()

        // Collect metadata from all fixtures (including upcoming) so we also capture latest form/records
        allFixtures.forEach { f ->
            teamSummaries[f.homeTeam.id] = f.homeTeam
            teamSummaries[f.awayTeam.id] = f.awayTeam
            if (!f.homeFormString.isNullOrBlank()) formHintByTeam[f.homeTeam.id] = f.homeFormString
            if (!f.awayFormString.isNullOrBlank()) formHintByTeam[f.awayTeam.id] = f.awayFormString
            if (!f.homeRecordSummary.isNullOrBlank()) recordHintByTeam[f.homeTeam.id] = f.homeRecordSummary
            if (!f.awayRecordSummary.isNullOrBlank()) recordHintByTeam[f.awayTeam.id] = f.awayRecordSummary
        }

        completed.forEach { match ->
            val hGoals = match.homeScore ?: return@forEach
            val aGoals = match.awayScore ?: return@forEach

            val homeOutcome = when {
                hGoals > aGoals -> MatchOutcome.WIN
                hGoals < aGoals -> MatchOutcome.LOSS
                else -> MatchOutcome.DRAW
            }
            val awayOutcome = when {
                aGoals > hGoals -> MatchOutcome.WIN
                aGoals < hGoals -> MatchOutcome.LOSS
                else -> MatchOutcome.DRAW
            }

            recordsByTeam.getOrPut(match.homeTeam.id) { mutableListOf() }.add(
                PreviousMatchRecord(
                    eventId = match.id,
                    dateIso = match.dateIso,
                    dateEpochMillis = match.dateEpochMillis,
                    leagueCode = match.leagueCode,
                    leagueName = match.leagueName,
                    teamId = match.homeTeam.id,
                    opponentId = match.awayTeam.id,
                    opponentName = match.awayTeam.shortName,
                    opponentLogoUrl = match.awayTeam.logoUrl,
                    isHome = true,
                    goalsFor = hGoals,
                    goalsAgainst = aGoals,
                    outcome = homeOutcome,
                )
            )

            recordsByTeam.getOrPut(match.awayTeam.id) { mutableListOf() }.add(
                PreviousMatchRecord(
                    eventId = match.id,
                    dateIso = match.dateIso,
                    dateEpochMillis = match.dateEpochMillis,
                    leagueCode = match.leagueCode,
                    leagueName = match.leagueName,
                    teamId = match.awayTeam.id,
                    opponentId = match.homeTeam.id,
                    opponentName = match.homeTeam.shortName,
                    opponentLogoUrl = match.homeTeam.logoUrl,
                    isHome = false,
                    goalsFor = aGoals,
                    goalsAgainst = hGoals,
                    outcome = awayOutcome,
                )
            )
        }

        val profiles = mutableMapOf<String, TeamMatchProfile>()
        recordsByTeam.forEach { (teamId, rawMatches) ->
            val team = teamSummaries[teamId] ?: return@forEach
            val matches = rawMatches.distinctBy { it.eventId }.sortedByDescending { it.dateEpochMillis }
            if (matches.isEmpty()) return@forEach

            val seasonTriple = parseRecordSummary(recordHintByTeam[teamId])
            val recentWins = matches.count { it.outcome == MatchOutcome.WIN }
            val recentDraws = matches.count { it.outcome == MatchOutcome.DRAW }
            val recentLosses = matches.count { it.outcome == MatchOutcome.LOSS }

            val totalMatches = max(matches.size, seasonTriple?.let { it.first + it.second + it.third } ?: 0)
            val totalWins = seasonTriple?.first ?: recentWins
            val totalDraws = seasonTriple?.second ?: recentDraws
            val totalLosses = seasonTriple?.third ?: recentLosses

            // Blend recent match draw rate (65%) with season draw rate (35%) for responsiveness
            val recentDrawRate = recentDraws.toDouble() / matches.size.toDouble()
            val seasonDrawRate = if (totalMatches > 0) totalDraws.toDouble() / totalMatches.toDouble() else recentDrawRate
            val blendedDrawRate = (0.65 * recentDrawRate + 0.35 * seasonDrawRate).coerceIn(0.0, 1.0)

            var nonDrawStreak = 0
            for (rec in matches) {
                if (rec.outcome != MatchOutcome.DRAW) {
                    nonDrawStreak++
                } else {
                    break
                }
            }

            val recentForm = matches.take(5).map { it.outcome }
            val avgScored = matches.map { it.goalsFor }.average()
            val avgConceded = matches.map { it.goalsAgainst }.average()

            val homeMatches = matches.filter { it.isHome }
            val awayMatches = matches.filter { !it.isHome }

            val homeAvgScored = if (homeMatches.isNotEmpty()) homeMatches.map { it.goalsFor }.average() else avgScored
            val homeAvgConceded = if (homeMatches.isNotEmpty()) homeMatches.map { it.goalsAgainst }.average() else avgConceded
            val awayAvgScored = if (awayMatches.isNotEmpty()) awayMatches.map { it.goalsFor }.average() else avgScored
            val awayAvgConceded = if (awayMatches.isNotEmpty()) awayMatches.map { it.goalsAgainst }.average() else avgConceded

            val cleanSheetRate = matches.count { it.goalsAgainst == 0 }.toDouble() / matches.size
            val bttsRate = matches.count { it.goalsFor > 0 && it.goalsAgainst > 0 }.toDouble() / matches.size
            val over25Rate = matches.count { it.goalsFor + it.goalsAgainst >= 3 }.toDouble() / matches.size

            val decisiveBonus = min(nonDrawStreak * 4.0, 20.0)
            val goalVolatilityBonus = min((avgScored + avgConceded) * 3.5, 14.0)
            val decisiveIndex = ((1.0 - blendedDrawRate) * 72.0 + decisiveBonus + goalVolatilityBonus)
                .roundToInt()
                .coerceIn(15, 99)

            profiles[teamId] = TeamMatchProfile(
                team = team,
                previousMatches = matches,
                matchesPlayed = totalMatches,
                wins = totalWins,
                draws = totalDraws,
                losses = totalLosses,
                drawRate = blendedDrawRate,
                nonDrawStreak = nonDrawStreak,
                recentForm = recentForm,
                avgGoalsScored = avgScored,
                avgGoalsConceded = avgConceded,
                homeAvgGoalsScored = homeAvgScored,
                homeAvgGoalsConceded = homeAvgConceded,
                awayAvgGoalsScored = awayAvgScored,
                awayAvgGoalsConceded = awayAvgConceded,
                cleanSheetRate = cleanSheetRate,
                bttsRate = bttsRate,
                over25Rate = over25Rate,
                decisiveIndex = decisiveIndex,
                seasonRecordSummary = recordHintByTeam[teamId],
            )
        }

        return profiles
    }

    fun analyzeFixtures(
        fixtures: List<RawMatchFixture>,
        teamProfiles: Map<String, TeamMatchProfile>,
        shortlistedIds: Set<String> = emptySet(),
    ): List<AnalyzedMatch> {
        // Estimate league-wide goal baseline from observed previous matches
        val leagueAvgByCode = fixtures
            .filter { it.state == MatchState.COMPLETED && it.homeScore != null && it.awayScore != null }
            .groupBy { it.leagueCode }
            .mapValues { (_, matches) ->
                val avg = matches.flatMap { listOf(it.homeScore ?: 0, it.awayScore ?: 0) }.average()
                avg.coerceIn(1.05, 1.85)
            }

        return fixtures.map { fixture ->
            val homeProfile = teamProfiles[fixture.homeTeam.id]
            val awayProfile = teamProfiles[fixture.awayTeam.id]
            val leagueGoalBaseline = leagueAvgByCode[fixture.leagueCode] ?: DEFAULT_LEAGUE_GOALS_PER_TEAM
            val analysis = evaluateMatchOutcomes(
                fixture = fixture,
                homeProfile = homeProfile,
                awayProfile = awayProfile,
                leagueAvgGoals = leagueGoalBaseline,
            )
            AnalyzedMatch(
                fixture = fixture,
                homeProfile = homeProfile,
                awayProfile = awayProfile,
                analysis = analysis,
                isShortlisted = shortlistedIds.contains(fixture.id),
            )
        }
    }

    fun evaluateMatchOutcomes(
        fixture: RawMatchFixture,
        homeProfile: TeamMatchProfile?,
        awayProfile: TeamMatchProfile?,
        leagueAvgGoals: Double = DEFAULT_LEAGUE_GOALS_PER_TEAM,
    ): MatchOutcomeAnalysis {
        // Compute Bayesian-smoothed attack and defense strengths
        val hAttack = smoothedRate(
            observed = homeProfile?.let { 0.55 * it.homeAvgGoalsScored + 0.45 * it.avgGoalsScored },
            sampleSize = homeProfile?.previousMatches?.size ?: 0,
            prior = leagueAvgGoals,
        )
        val hDefense = smoothedRate(
            observed = homeProfile?.let { 0.55 * it.homeAvgGoalsConceded + 0.45 * it.avgGoalsConceded },
            sampleSize = homeProfile?.previousMatches?.size ?: 0,
            prior = leagueAvgGoals,
        )
        val aAttack = smoothedRate(
            observed = awayProfile?.let { 0.55 * it.awayAvgGoalsScored + 0.45 * it.avgGoalsScored },
            sampleSize = awayProfile?.previousMatches?.size ?: 0,
            prior = leagueAvgGoals,
        )
        val aDefense = smoothedRate(
            observed = awayProfile?.let { 0.55 * it.awayAvgGoalsConceded + 0.45 * it.avgGoalsConceded },
            sampleSize = awayProfile?.previousMatches?.size ?: 0,
            prior = leagueAvgGoals,
        )

        val homeFormBoost = formMomentumMultiplier(homeProfile, fixture.homeFormString)
        val awayFormBoost = formMomentumMultiplier(awayProfile, fixture.awayFormString)

        val lambdaHome = ((hAttack / leagueAvgGoals) * (aDefense / leagueAvgGoals) * leagueAvgGoals * HOME_ADVANTAGE_FACTOR * homeFormBoost)
            .coerceIn(0.40, 3.60)
        val lambdaAway = ((aAttack / leagueAvgGoals) * (hDefense / leagueAvgGoals) * leagueAvgGoals * AWAY_FACTOR * awayFormBoost)
            .coerceIn(0.32, 3.20)

        // Build 6x6 Poisson scoreline probability matrix
        val matrix = Array(MAX_GRID_GOALS + 1) { i ->
            DoubleArray(MAX_GRID_GOALS + 1) { j ->
                poisson(lambdaHome, i) * poisson(lambdaAway, j)
            }
        }
        val totalMass = matrix.sumOf { row -> row.sum() }.coerceAtLeast(1e-9)

        var rawHomeWin = 0.0
        var rawDraw = 0.0
        var rawAwayWin = 0.0
        var over25 = 0.0
        var btts = 0.0
        val scorelines = mutableListOf<ScorelineProbability>()

        for (i in 0..MAX_GRID_GOALS) {
            for (j in 0..MAX_GRID_GOALS) {
                val p = matrix[i][j] / totalMass
                when {
                    i > j -> rawHomeWin += p
                    i == j -> rawDraw += p
                    else -> rawAwayWin += p
                }
                if (i + j >= 3) over25 += p
                if (i >= 1 && j >= 1) btts += p
                scorelines.add(ScorelineProbability(i, j, p))
            }
        }

        // Calibrate draw probability with historical draw propensity of both teams
        val combinedEmpiricalDrawRate = when {
            homeProfile != null && awayProfile != null -> (homeProfile.drawRate + awayProfile.drawRate) / 2.0
            homeProfile != null -> (homeProfile.drawRate + 0.25) / 2.0
            awayProfile != null -> (awayProfile.drawRate + 0.25) / 2.0
            else -> rawDraw
        }

        // Streak adjustment: teams with long non-draw streaks and decisive scoring gap reduce draw odds slightly
        val avgNonDrawStreak = ((homeProfile?.nonDrawStreak ?: 0) + (awayProfile?.nonDrawStreak ?: 0)) / 2.0
        val streakFactor = if (avgNonDrawStreak >= 3.0) -0.025 else 0.0

        val calibratedDraw = (0.68 * rawDraw + 0.32 * combinedEmpiricalDrawRate + streakFactor)
            .coerceIn(0.08, 0.44)

        val nonDrawScale = if ((rawHomeWin + rawAwayWin) > 1e-6) {
            (1.0 - calibratedDraw) / (rawHomeWin + rawAwayWin)
        } else {
            0.5
        }
        val homeWinProb = (rawHomeWin * nonDrawScale).coerceIn(0.02, 0.90)
        val awayWinProb = (rawAwayWin * nonDrawScale).coerceIn(0.02, 0.90)
        val noDrawProb = (homeWinProb + awayWinProb).coerceIn(0.0, 0.96)

        // Penalize confidence slightly if sample size is thin (<3 previous matches per team)
        val minSample = min(
            homeProfile?.previousMatches?.size ?: 0,
            awayProfile?.previousMatches?.size ?: 0,
        )
        val dataCoveragePenalty = when {
            minSample >= 4 -> 0
            minSample >= 2 -> 4
            minSample >= 1 -> 8
            else -> 16
        }
        val confidenceScore = ((noDrawProb * 100.0) - dataCoveragePenalty * 0.45)
            .roundToInt()
            .coerceIn(30, 96)

        val tier = when {
            noDrawProb >= 0.775 && minSample >= 2 -> NoDrawTier.ELITE_LOCK
            noDrawProb >= 0.72 -> NoDrawTier.STRONG_DECISIVE
            noDrawProb >= 0.655 -> NoDrawTier.MODERATE
            else -> NoDrawTier.DRAW_RISK
        }

        val leaning = when {
            homeWinProb - awayWinProb >= 0.14 -> "Home Win (${fixture.homeTeam.abbreviation})"
            awayWinProb - homeWinProb >= 0.14 -> "Away Win (${fixture.awayTeam.abbreviation})"
            homeWinProb >= awayWinProb -> "Either Side • Slight Home Edge"
            else -> "Either Side • Slight Away Edge"
        }
        val recommendedSideCode = when {
            homeWinProb - awayWinProb >= 0.18 -> "1 / 12"
            awayWinProb - homeWinProb >= 0.18 -> "2 / 12"
            else -> "12 (No Draw)"
        }

        val reasons = buildSelectionReasons(
            fixture = fixture,
            homeProfile = homeProfile,
            awayProfile = awayProfile,
            lambdaHome = lambdaHome,
            lambdaAway = lambdaAway,
            drawProb = calibratedDraw,
            over25Prob = over25,
            homeWinProb = homeWinProb,
            awayWinProb = awayWinProb,
        )

        val grid5x5 = (0..4).map { i ->
            (0..4).map { j ->
                matrix[i][j] / totalMass
            }
        }

        return MatchOutcomeAnalysis(
            homeWinProb = homeWinProb,
            drawProb = calibratedDraw,
            awayWinProb = awayWinProb,
            noDrawProb = noDrawProb,
            doubleChance1X = (homeWinProb + calibratedDraw).coerceIn(0.0, 0.99),
            doubleChanceX2 = (awayWinProb + calibratedDraw).coerceIn(0.0, 0.99),
            expectedHomeGoals = lambdaHome,
            expectedAwayGoals = lambdaAway,
            over25Prob = over25.coerceIn(0.05, 0.95),
            bttsProb = btts.coerceIn(0.05, 0.95),
            topScorelines = scorelines.sortedByDescending { it.probability }.take(6),
            scorelineGrid = grid5x5,
            primaryLeaning = leaning,
            recommendedSideCode = recommendedSideCode,
            confidenceScore = confidenceScore,
            tier = tier,
            selectionReasons = reasons,
        )
    }

    private fun buildSelectionReasons(
        fixture: RawMatchFixture,
        homeProfile: TeamMatchProfile?,
        awayProfile: TeamMatchProfile?,
        lambdaHome: Double,
        lambdaAway: Double,
        drawProb: Double,
        over25Prob: Double,
        homeWinProb: Double,
        awayWinProb: Double,
    ): List<String> {
        val reasons = mutableListOf<String>()

        if (homeProfile != null && awayProfile != null) {
            val combinedDrawPct = (((homeProfile.drawRate + awayProfile.drawRate) / 2.0) * 100).roundToInt()
            val totalPrev = homeProfile.previousMatches.size + awayProfile.previousMatches.size
            reasons.add("Combined draw rate is $combinedDrawPct% across $totalPrev recent matches")
        }

        if ((homeProfile?.nonDrawStreak ?: 0) >= 3) {
            reasons.add("${fixture.homeTeam.shortName} on a ${homeProfile?.nonDrawStreak}-match decisive streak (0 draws)")
        }
        if ((awayProfile?.nonDrawStreak ?: 0) >= 3) {
            reasons.add("${fixture.awayTeam.shortName} on a ${awayProfile?.nonDrawStreak}-match decisive streak (0 draws)")
        }

        val xgGap = abs(lambdaHome - lambdaAway)
        if (xgGap >= 0.45) {
            val favorite = if (lambdaHome > lambdaAway) fixture.homeTeam.shortName else fixture.awayTeam.shortName
            reasons.add("Projected xG gap of ${"%.2f".format(xgGap)} favors $favorite for a decisive win")
        }

        if (over25Prob >= 0.56) {
            reasons.add("Open scoring profile (${(over25Prob * 100).roundToInt()}% Over 2.5 goals) lowers 0-0 / 1-1 draw risk")
        }

        val winGapPct = (abs(homeWinProb - awayWinProb) * 100).roundToInt()
        if (winGapPct >= 20) {
            val side = if (homeWinProb > awayWinProb) fixture.homeTeam.shortName else fixture.awayTeam.shortName
            reasons.add("$side holds a +$winGapPct% win-probability edge over opponent")
        }

        if (reasons.isEmpty()) {
            reasons.add("Poisson simulation estimates ${(drawProb * 100).roundToInt()}% draw probability from available form")
        }
        return reasons.take(4)
    }

    private fun smoothedRate(observed: Double?, sampleSize: Int, prior: Double): Double {
        if (observed == null || sampleSize <= 0) return prior
        val weight = 3.0 // Bayesian prior weight equivalent to 3 matches
        return ((observed * sampleSize) + (prior * weight)) / (sampleSize + weight)
    }

    private fun formMomentumMultiplier(profile: TeamMatchProfile?, formString: String?): Double {
        val outcomes = profile?.recentForm?.takeIf { it.isNotEmpty() }
            ?: formString?.mapNotNull { ch ->
                when (ch.uppercaseChar()) {
                    'W' -> MatchOutcome.WIN
                    'D' -> MatchOutcome.DRAW
                    'L' -> MatchOutcome.LOSS
                    else -> null
                }
            }?.take(5)
            ?: return 1.0

        var points = 0.0
        outcomes.forEachIndexed { idx, outcome ->
            val recencyWeight = 1.0 - (idx * 0.12)
            points += when (outcome) {
                MatchOutcome.WIN -> 3.0 * recencyWeight
                MatchOutcome.DRAW -> 1.0 * recencyWeight
                MatchOutcome.LOSS -> 0.0
            }
        }
        val maxPoints = outcomes.indices.sumOf { 3.0 * (1.0 - it * 0.12) }.coerceAtLeast(1.0)
        val ratio = points / maxPoints // 0..1
        return 0.88 + (ratio * 0.24) // 0.88x .. 1.12x
    }

    private fun poisson(lambda: Double, k: Int): Double {
        val fact = (1..max(1, k)).fold(1.0) { acc, i -> if (k == 0) 1.0 else acc * i }
        return (lambda.pow(k.toDouble()) * exp(-lambda)) / fact
    }

    private fun parseRecordSummary(summary: String?): Triple<Int, Int, Int>? {
        if (summary.isNullOrBlank()) return null
        val parts = summary.trim().split("-")
        if (parts.size < 3) return null
        val w = parts[0].toIntOrNull() ?: return null
        val d = parts[1].toIntOrNull() ?: return null
        val l = parts[2].toIntOrNull() ?: return null
        return Triple(w, d, l)
    }

    /**
     * For each day of a 7-day week starting from today, evaluates all active (LIVE / UPCOMING)
     * matches on that day and selects the best 2 matches with the highest probability of not
     * ending in a draw.
     */
    fun selectDailyBestTwoForWeek(
        analyzedMatches: List<AnalyzedMatch>,
        nowMillis: Long = System.currentTimeMillis(),
        daysCount: Int = 7,
        leagueCode: String = "all",
        liveOnly: Boolean = false,
        isRealToday: Boolean = true,
        timeZone: java.util.TimeZone = java.util.TimeZone.getDefault(),
    ): List<com.example.data.model.DailyBestSelection> {
        val baseCal = java.util.Calendar.getInstance(timeZone).apply {
            timeInMillis = nowMillis
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val keyFmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).apply {
            this.timeZone = timeZone
        }
        val fullFmt = java.text.SimpleDateFormat("EEE, MMM d", java.util.Locale.getDefault()).apply {
            this.timeZone = timeZone
        }
        val shortFmt = java.text.SimpleDateFormat("EEE d", java.util.Locale.getDefault()).apply {
            this.timeZone = timeZone
        }

        val leagueFiltered = analyzedMatches.filter {
            leagueCode == "all" || it.fixture.leagueCode == leagueCode
        }

        return (0 until daysCount).map { offset ->
            val dayCal = (baseCal.clone() as java.util.Calendar).apply {
                add(java.util.Calendar.DAY_OF_YEAR, offset)
            }
            val startMs = dayCal.timeInMillis
            val endCal = (dayCal.clone() as java.util.Calendar).apply {
                add(java.util.Calendar.DAY_OF_YEAR, 1)
            }
            val endMs = endCal.timeInMillis

            val dayMatches = leagueFiltered.filter { match ->
                match.fixture.dateEpochMillis in startMs until endMs
            }

            val liveCount = dayMatches.count { it.fixture.state == MatchState.LIVE }
            val candidates = dayMatches.filter { match ->
                if (liveOnly) {
                    match.fixture.state == MatchState.LIVE
                } else {
                    match.fixture.state == MatchState.LIVE || match.fixture.state == MatchState.UPCOMING
                }
            }

            val sortedCandidates = candidates
                .sortedWith(
                    compareByDescending<AnalyzedMatch> { it.analysis.noDrawProb }
                        .thenByDescending { it.analysis.confidenceScore }
                        .thenByDescending { it.analysis.expectedHomeGoals + it.analysis.expectedAwayGoals }
                )
            val bestTwo = sortedCandidates.take(2)

            val combinedProb = if (bestTwo.isEmpty()) {
                0.0
            } else {
                bestTwo.fold(1.0) { acc, m -> acc * m.analysis.noDrawProb }
            }

            val dateObj = dayCal.time
            val dayLabel = when {
                isRealToday && offset == 0 -> "Today • ${fullFmt.format(dateObj)}"
                isRealToday && offset == 1 -> "Tomorrow • ${fullFmt.format(dateObj)}"
                else -> fullFmt.format(dateObj)
            }
            val shortLabel = when {
                isRealToday && offset == 0 -> "Today"
                isRealToday && offset == 1 -> "Tmrw"
                else -> shortFmt.format(dateObj)
            }

            com.example.data.model.DailyBestSelection(
                dayKey = keyFmt.format(dateObj),
                dayLabel = dayLabel,
                shortDayLabel = shortLabel,
                dayStartMillis = startMs,
                totalDayMatches = sortedCandidates.size,
                liveMatchesCount = liveCount,
                bestTwoMatches = bestTwo,
                combinedTwoPickNoDrawProb = combinedProb,
                allDayMatches = sortedCandidates,
            )
        }
    }
}
