package com.ammiratv.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ammiratv.core.database.entity.ProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {

    @Query("SELECT * FROM profile ORDER BY id ASC")
    fun getAll(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profile WHERE id = :id")
    suspend fun getById(id: Long): ProfileEntity?

    @Query("SELECT COUNT(*) FROM profile")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: ProfileEntity): Long

    @Update
    suspend fun update(profile: ProfileEntity)

    @Query("DELETE FROM profile WHERE id = :id")
    suspend fun deleteById(id: Long)
}
