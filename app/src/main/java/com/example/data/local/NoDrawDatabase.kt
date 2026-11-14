package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.MatchState
import com.example.data.model.RawMatchFixture
import com.example.data.model.TeamSummary
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "shortlisted_matches")
data class ShortlistEntity(
    @PrimaryKey val matchId: String,
    val addedAtMillis: Long = System.currentTimeMillis(),
)

@Entity(tableName = "cached_fixtures")
data class CachedFixtureEntity(
    @PrimaryKey val id: String,
    val leagueCode: String,
    val leagueName: String,
    val dateIso: String,
    val dateEpochMillis: Long,
    val stateName: String,
    val statusDetail: String,
    val venueName: String?,
    val homeTeamId: String,
    val homeTeamName: String,
    val homeTeamShortName: String,
    val homeTeamAbbr: String,
    val homeTeamLogo: String?,
    val awayTeamId: String,
    val awayTeamName: String,
    val awayTeamShortName: String,
    val awayTeamAbbr: String,
    val awayTeamLogo: String?,
    val homeScore: Int?,
    val awayScore: Int?,
    val homeFormString: String?,
    val awayFormString: String?,
    val homeRecordSummary: String?,
    val awayRecordSummary: String?,
    val cachedAtMillis: Long = System.currentTimeMillis(),
) {
    fun toDomain(): RawMatchFixture = RawMatchFixture(
        id = id,
        leagueCode = leagueCode,
        leagueName = leagueName,
        dateIso = dateIso,
        dateEpochMillis = dateEpochMillis,
        state = MatchState.entries.firstOrNull { it.name == stateName } ?: MatchState.UPCOMING,
        statusDetail = statusDetail,
        venueName = venueName,
        homeTeam = TeamSummary(
            id = homeTeamId,
            name = homeTeamName,
            shortName = homeTeamShortName,
            abbreviation = homeTeamAbbr,
            logoUrl = homeTeamLogo,
            leagueCode = leagueCode,
            leagueName = leagueName,
        ),
        awayTeam = TeamSummary(
            id = awayTeamId,
            name = awayTeamName,
            shortName = awayTeamShortName,
            abbreviation = awayTeamAbbr,
            logoUrl = awayTeamLogo,
            leagueCode = leagueCode,
            leagueName = leagueName,
        ),
        homeScore = homeScore,
        awayScore = awayScore,
        homeFormString = homeFormString,
        awayFormString = awayFormString,
        homeRecordSummary = homeRecordSummary,
        awayRecordSummary = awayRecordSummary,
    )

    companion object {
        fun fromDomain(fixture: RawMatchFixture): CachedFixtureEntity = CachedFixtureEntity(
            id = fixture.id,
            leagueCode = fixture.leagueCode,
            leagueName = fixture.leagueName,
            dateIso = fixture.dateIso,
            dateEpochMillis = fixture.dateEpochMillis,
            stateName = fixture.state.name,
            statusDetail = fixture.statusDetail,
            venueName = fixture.venueName,
            homeTeamId = fixture.homeTeam.id,
            homeTeamName = fixture.homeTeam.name,
            homeTeamShortName = fixture.homeTeam.shortName,
            homeTeamAbbr = fixture.homeTeam.abbreviation,
            homeTeamLogo = fixture.homeTeam.logoUrl,
            awayTeamId = fixture.awayTeam.id,
            awayTeamName = fixture.awayTeam.name,
            awayTeamShortName = fixture.awayTeam.shortName,
            awayTeamAbbr = fixture.awayTeam.abbreviation,
            awayTeamLogo = fixture.awayTeam.logoUrl,
            homeScore = fixture.homeScore,
            awayScore = fixture.awayScore,
            homeFormString = fixture.homeFormString,
            awayFormString = fixture.awayFormString,
            homeRecordSummary = fixture.homeRecordSummary,
            awayRecordSummary = fixture.awayRecordSummary,
        )
    }
}

@Dao
interface NoDrawDao {
    @Query("SELECT * FROM shortlisted_matches ORDER BY addedAtMillis DESC")
    fun observeShortlist(): Flow<List<ShortlistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addToShortlist(entity: ShortlistEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addAllToShortlist(entities: List<ShortlistEntity>)

    @Query("DELETE FROM shortlisted_matches WHERE matchId = :matchId")
    suspend fun removeFromShortlist(matchId: String)

    @Query("DELETE FROM shortlisted_matches")
    suspend fun clearShortlist()

    @Query("SELECT * FROM cached_fixtures ORDER BY dateEpochMillis ASC")
    fun observeCachedFixtures(): Flow<List<CachedFixtureEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFixtures(fixtures: List<CachedFixtureEntity>)

    @Query("DELETE FROM cached_fixtures")
    suspend fun clearFixtures()

    @androidx.room.Transaction
    suspend fun replaceFixtures(fixtures: List<CachedFixtureEntity>) {
        clearFixtures()
        upsertFixtures(fixtures)
    }
}

@Database(
    entities = [ShortlistEntity::class, CachedFixtureEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class NoDrawDatabase : RoomDatabase() {
    abstract fun noDrawDao(): NoDrawDao

    companion object {
        @Volatile
        private var INSTANCE: NoDrawDatabase? = null

        fun getInstance(context: Context): NoDrawDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    NoDrawDatabase::class.java,
                    "nodraw_analytics.db",
                ).fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
