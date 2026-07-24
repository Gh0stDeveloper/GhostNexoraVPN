package com.ghostnexora.vpn.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ghostnexora.vpn.data.model.VpnProfile
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM vpn_profiles ORDER BY name ASC")
    fun getAllProfiles(): Flow<List<VpnProfile>>

    @Query("SELECT * FROM vpn_profiles ORDER BY name ASC")
    suspend fun getAllProfilesOnce(): List<VpnProfile>

    @Query("SELECT * FROM vpn_profiles WHERE enabled = 1 ORDER BY name ASC")
    fun getEnabledProfiles(): Flow<List<VpnProfile>>

    @Query("SELECT * FROM vpn_profiles WHERE is_favorite = 1 ORDER BY name ASC")
    fun getFavoriteProfiles(): Flow<List<VpnProfile>>

    @Query(
        """
        SELECT * FROM vpn_profiles
        WHERE name LIKE '%' || :query || '%'
           OR host LIKE '%' || :query || '%'
        ORDER BY name ASC
        """
    )
    fun searchProfiles(query: String): Flow<List<VpnProfile>>

    @Query("SELECT * FROM vpn_profiles WHERE id = :id LIMIT 1")
    suspend fun getProfileById(id: String): VpnProfile?

    @Query("SELECT * FROM vpn_profiles WHERE id = :id LIMIT 1")
    fun observeProfileById(id: String): Flow<VpnProfile?>

    @Query("SELECT COUNT(*) FROM vpn_profiles")
    fun getProfileCount(): Flow<Int>

    @Query(
        """
        SELECT * FROM vpn_profiles
        WHERE last_used != ''
        ORDER BY last_used DESC
        LIMIT 1
        """
    )
    suspend fun getLastUsedProfile(): VpnProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: VpnProfile)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfiles(profiles: List<VpnProfile>)

    @Update
    suspend fun updateProfile(profile: VpnProfile)

    @Query("UPDATE vpn_profiles SET last_used = :timestamp WHERE id = :id")
    suspend fun updateLastUsed(id: String, timestamp: String)

    @Query("UPDATE vpn_profiles SET is_favorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: String, isFavorite: Boolean)

    @Query("UPDATE vpn_profiles SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean)

    @Delete
    suspend fun deleteProfile(profile: VpnProfile)

    @Query("DELETE FROM vpn_profiles WHERE id = :id")
    suspend fun deleteProfileById(id: String)

    @Query("DELETE FROM vpn_profiles")
    suspend fun deleteAllProfiles()
}
