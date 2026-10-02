package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface LyricsDao {

    @Query("SELECT lyrics FROM lyrics WHERE audioPath = :path LIMIT 1")
    suspend fun getByPath(path: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(entry: LyricsEntry)

    @Query("DELETE FROM lyrics WHERE audioPath = :path")
    suspend fun deleteByPath(path: String)
}
