package com.example.data.model

import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonReader
import com.squareup.moshi.ToJson

data class FlexScore(val value: String?)

class FlexScoreAdapter {
    @FromJson
    fun fromJson(reader: JsonReader): FlexScore {
        return when (reader.peek()) {
            JsonReader.Token.STRING -> FlexScore(reader.nextString())
            JsonReader.Token.NUMBER -> FlexScore(reader.nextDouble().toInt().toString())
            JsonReader.Token.BEGIN_OBJECT -> {
                var display: String? = null
                var numeric: String? = null
                reader.beginObject()
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "displayValue" -> {
                            display = if (reader.peek() == JsonReader.Token.STRING) {
                                reader.nextString()
                            } else {
                                reader.skipValue(); null
                            }
                        }
                        "value" -> {
                            numeric = if (reader.peek() == JsonReader.Token.NUMBER) {
                                reader.nextDouble().toInt().toString()
                            } else {
                                reader.skipValue(); null
                            }
                        }
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
                FlexScore(display ?: numeric)
            }
            JsonReader.Token.NULL -> {
                reader.nextNull<Unit>()
                FlexScore(null)
            }
            else -> {
                reader.skipValue()
                FlexScore(null)
            }
        }
    }

    @ToJson
    fun toJson(score: FlexScore?): String? = score?.value
}

data class FlexCalendarDate(val isoDate: String?)

class FlexCalendarDateAdapter {
    @FromJson
    fun fromJson(reader: JsonReader): FlexCalendarDate {
        return when (reader.peek()) {
            JsonReader.Token.STRING -> FlexCalendarDate(reader.nextString())
            JsonReader.Token.BEGIN_OBJECT -> {
                var startDate: String? = null
                var value: String? = null
                reader.beginObject()
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "startDate" -> {
                            startDate = if (reader.peek() == JsonReader.Token.STRING) {
                                reader.nextString()
                            } else {
                                reader.skipValue(); null
                            }
                        }
                        "value" -> {
                            value = if (reader.peek() == JsonReader.Token.STRING) {
                                reader.nextString()
                            } else {
                                reader.skipValue(); null
                            }
                        }
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
                FlexCalendarDate(startDate ?: value)
            }
            else -> {
                reader.skipValue()
                FlexCalendarDate(null)
            }
        }
    }

    @ToJson
    fun toJson(item: FlexCalendarDate?): String? = item?.isoDate
}

@JsonClass(generateAdapter = true)
data class EspnScoreboardResponse(
    val events: List<EspnEvent>? = null,
    val leagues: List<EspnLeagueMeta>? = null,
    val day: EspnDay? = null,
)

@JsonClass(generateAdapter = true)
data class EspnTeamScheduleResponse(
    val events: List<EspnEvent>? = null,
)

@JsonClass(generateAdapter = true)
data class EspnDay(
    val date: String? = null,
)

@JsonClass(generateAdapter = true)
data class EspnLeagueMeta(
    val id: String? = null,
    val uid: String? = null,
    val name: String? = null,
    val abbreviation: String? = null,
    val slug: String? = null,
    val calendar: List<FlexCalendarDate>? = null,
)

@JsonClass(generateAdapter = true)
data class EspnEvent(
    val id: String? = null,
    val uid: String? = null,
    val date: String? = null,
    val name: String? = null,
    val shortName: String? = null,
    val competitions: List<EspnCompetition>? = null,
    val status: EspnStatus? = null,
)

@JsonClass(generateAdapter = true)
data class EspnCompetition(
    val id: String? = null,
    val date: String? = null,
    val competitors: List<EspnCompetitor>? = null,
    val venue: EspnVenue? = null,
    val status: EspnStatus? = null,
)

@JsonClass(generateAdapter = true)
data class EspnVenue(
    val fullName: String? = null,
)

@JsonClass(generateAdapter = true)
data class EspnCompetitor(
    val id: String? = null,
    val homeAway: String? = null,
    val winner: Boolean? = null,
    val score: FlexScore? = null,
    val form: String? = null,
    val team: EspnTeam? = null,
    val records: List<EspnRecord>? = null,
)

@JsonClass(generateAdapter = true)
data class EspnTeam(
    val id: String? = null,
    val displayName: String? = null,
    val shortDisplayName: String? = null,
    val abbreviation: String? = null,
    val logo: String? = null,
    val logos: List<EspnTeamLogo>? = null,
    val color: String? = null,
)

@JsonClass(generateAdapter = true)
data class EspnTeamLogo(
    val href: String? = null,
)

@JsonClass(generateAdapter = true)
data class EspnRecord(
    val name: String? = null,
    val type: String? = null,
    val summary: String? = null,
)

@JsonClass(generateAdapter = true)
data class EspnStatus(
    val clock: Double? = null,
    val displayClock: String? = null,
    val type: EspnStatusType? = null,
)

@JsonClass(generateAdapter = true)
data class EspnStatusType(
    val id: String? = null,
    val name: String? = null,
    val state: String? = null,
    val completed: Boolean? = null,
    val description: String? = null,
    val shortDetail: String? = null,
)
