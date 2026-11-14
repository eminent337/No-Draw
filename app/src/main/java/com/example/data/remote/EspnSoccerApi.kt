package com.example.data.remote

import com.example.data.model.EspnScoreboardResponse
import com.example.data.model.EspnTeamScheduleResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface EspnSoccerApi {
    @GET("apis/site/v2/sports/soccer/{league}/scoreboard")
    suspend fun getScoreboard(
        @Path("league") league: String,
        @Query("dates") dates: String? = null,
        @Query("limit") limit: Int = 200,
    ): EspnScoreboardResponse

    @GET("apis/site/v2/sports/soccer/{league}/teams/{teamId}/schedule")
    suspend fun getTeamSchedule(
        @Path("league") league: String,
        @Path("teamId") teamId: String,
    ): EspnTeamScheduleResponse
}
