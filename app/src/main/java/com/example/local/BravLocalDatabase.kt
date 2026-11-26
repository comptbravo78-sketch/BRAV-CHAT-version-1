package com.example.local

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
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "offline_outbox")
data class OfflineMessageEntity(
    @PrimaryKey val localId: String,
    val conversationId: String,
    val senderId: String,
    val receiverId: String,
    val plaintext: String,
    val messageType: String,
    val replyToMessageId: String = "",
    val replyToPreview: String = "",
    val forwardedFrom: String = "",
    val peerPublicKey: String = "",
    val isGroup: Boolean = false,
    val groupKeyBase64: String = "",
    val createdAtMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "ai_cache")
data class AiCacheEntity(
    @PrimaryKey val cacheKey: String,
    val model: String,
    val responseText: String,
    val createdAtMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "login_history")
data class LoginHistoryEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val deviceId: String,
    val deviceName: String,
    val countryCode: String = "",
    val city: String = "",
    val createdAtMs: Long = System.currentTimeMillis()
)

@Dao
interface OfflineQueueDao {
    @Query("SELECT * FROM offline_outbox WHERE conversationId = :conversationId ORDER BY createdAtMs ASC")
    fun observePendingForConversation(conversationId: String): Flow<List<OfflineMessageEntity>>

    @Query("SELECT * FROM offline_outbox ORDER BY createdAtMs ASC")
    suspend fun getAllPending(): List<OfflineMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(item: OfflineMessageEntity)

    @Query("DELETE FROM offline_outbox WHERE localId = :localId")
    suspend fun deleteById(localId: String)

    @Query("DELETE FROM offline_outbox WHERE conversationId = :conversationId")
    suspend fun deleteForConversation(conversationId: String)

    @Query("DELETE FROM offline_outbox")
    suspend fun clearAll()
}

@Dao
interface AiCacheDao {
    @Query("SELECT * FROM ai_cache WHERE cacheKey = :cacheKey LIMIT 1")
    suspend fun get(cacheKey: String): AiCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entry: AiCacheEntity)

    @Query("DELETE FROM ai_cache WHERE createdAtMs < :olderThanMs")
    suspend fun prune(olderThanMs: Long)
}

@Dao
interface LoginHistoryDao {
    @Query("SELECT * FROM login_history WHERE userId = :userId ORDER BY createdAtMs DESC LIMIT 20")
    fun observeForUser(userId: String): Flow<List<LoginHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<LoginHistoryEntity>)
}

@Database(
    entities = [
        OfflineMessageEntity::class,
        AiCacheEntity::class,
        LoginHistoryEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class BravLocalDatabase : RoomDatabase() {
    abstract fun offlineQueueDao(): OfflineQueueDao
    abstract fun aiCacheDao(): AiCacheDao
    abstract fun loginHistoryDao(): LoginHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: BravLocalDatabase? = null

        fun getInstance(context: Context): BravLocalDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    BravLocalDatabase::class.java,
                    "brav_chat_local_outbox.db"
                ).fallbackToDestructiveMigration(true).build().also { INSTANCE = it }
            }
        }
    }
}
