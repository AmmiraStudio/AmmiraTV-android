package com.ammiratv.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ammiratv.core.database.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Query("SELECT * FROM category WHERE playlistId = :playlistId AND isHidden = 0 ORDER BY sortOrder ASC, id ASC")
    fun getByPlaylist(playlistId: Long): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM category WHERE playlistId = :playlistId AND type = :type AND isHidden = 0 ORDER BY sortOrder ASC, id ASC")
    fun getByPlaylistAndType(playlistId: Long, type: String): Flow<List<CategoryEntity>>

    /** Returns the generated row ids, in the same order as [categories]. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categories: List<CategoryEntity>): List<Long>

    @Query("DELETE FROM category WHERE playlistId = :playlistId")
    suspend fun deleteByPlaylist(playlistId: Long)
}
