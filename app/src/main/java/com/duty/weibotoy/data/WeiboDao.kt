package com.duty.weibotoy.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.paging.PagingSource
import kotlinx.coroutines.flow.Flow

@Dao
interface WeiboDao {

    @Query("SELECT * FROM weibos ORDER BY created_at_long DESC")
    fun getAllWeibosFlow(): Flow<List<WeiboEntity>>

    @Query("SELECT * FROM weibos WHERE is_gap = 0 ORDER BY created_at_long DESC")
    fun getWeibosPagingSource(): PagingSource<Int, WeiboEntity>

    @Query("SELECT * FROM weibos ORDER BY created_at_long DESC")
    suspend fun getAllWeibosList(): List<WeiboEntity>

    @Query("SELECT MAX(id) FROM weibos WHERE is_gap = 0")
    suspend fun getNewestWeiboId(): Long?

    @Query("SELECT MIN(id) FROM weibos WHERE is_gap = 0")
    suspend fun getOldestWeiboId(): Long?

    @Query("SELECT * FROM weibos WHERE id = :id AND is_gap = 1")
    suspend fun getGapById(id: Long): WeiboEntity?

    @Query("SELECT * FROM weibos WHERE is_gap = 1")
    suspend fun getAllGaps(): List<WeiboEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(weibo: WeiboEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplaceAll(weibos: List<WeiboEntity>)

    @Query("UPDATE weibos SET gap_max_id = :maxId WHERE id = :gapId")
    suspend fun updateGapMaxId(gapId: Long, maxId: Long)

    @Query("DELETE FROM weibos WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM weibos WHERE created_at_long < :timestamp AND is_gap = 0")
    suspend fun deleteOlderThan(timestamp: Long)

    @Query("SELECT COUNT(*) FROM weibos WHERE is_gap = 0")
    suspend fun countNonGap(): Int

    @Query("DELETE FROM weibos WHERE id IN (SELECT id FROM weibos WHERE is_gap = 0 ORDER BY created_at_long ASC LIMIT :count)")
    suspend fun deleteOldest(count: Int)
}
