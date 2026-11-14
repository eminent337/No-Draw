package com.example.data.model

data class LeagueConfig(
    val code: String,
    val displayName: String,
    val shortBadge: String,
    val country: String,
)

object SupportedLeagues {
    val CORE_LEAGUES = listOf(
        LeagueConfig("eng.1", "Premier League", "EPL", "England"),
        LeagueConfig("esp.1", "La Liga", "LAL", "Spain"),
        LeagueConfig("ita.1", "Serie A", "SER", "Italy"),
        LeagueConfig("ger.1", "Bundesliga", "BUN", "Germany"),
        LeagueConfig("fra.1", "Ligue 1", "LI1", "France"),
        LeagueConfig("uefa.champions", "Champions League", "UCL", "Europe"),
        LeagueConfig("uefa.europa", "Europa League", "UEL", "Europe"),
        LeagueConfig("usa.1", "MLS", "MLS", "USA"),
        LeagueConfig("uefa.nations", "UEFA Nations League", "UNL", "Europe"),
        LeagueConfig("fifa.worldq.uefa", "WCQ Europe", "WCQ", "FIFA"),
        LeagueConfig("fifa.worldq.conmebol", "WCQ South America", "CON", "FIFA"),
        LeagueConfig("fifa.friendly", "Int'l Friendlies", "INT", "FIFA"),
    )

    val ALL = CORE_LEAGUES

    fun byCode(code: String): LeagueConfig =
        CORE_LEAGUES.firstOrNull { it.code == code }
            ?: LeagueConfig(
                code = code,
                displayName = code.replace(".", " ").uppercase(),
                shortBadge = code.substringBefore('.').take(4).uppercase(),
                country = "",
            )
}
