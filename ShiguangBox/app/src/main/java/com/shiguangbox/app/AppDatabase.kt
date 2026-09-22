package com.shiguangbox.app

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val content: String,
    val category: String = "生活",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val dueAt: Long? = null,
    val remindAt: Long? = null,
    val repeatType: String = "不重复",
    val priority: String = "普通",
    val completed: Boolean = false,
    val completedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String = "",
    val platform: String = "其他",
    val rawText: String = "",
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE createdAt BETWEEN :start AND :end ORDER BY createdAt DESC")
    fun observeBetween(start: Long, end: Long): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<NoteEntity?>

    @Insert
    suspend fun insert(note: NoteEntity): Long

    @Update
    suspend fun update(note: NoteEntity)

    @Delete
    suspend fun delete(note: NoteEntity)
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY completed ASC, COALESCE(dueAt, 9223372036854775807) ASC, createdAt DESC")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE dueAt BETWEEN :start AND :end ORDER BY completed ASC, dueAt ASC")
    fun observeBetween(start: Long, end: Long): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<TaskEntity?>

    @Query("SELECT * FROM tasks WHERE completed = 0 AND remindAt IS NOT NULL AND remindAt > :now")
    suspend fun pendingReminders(now: Long): List<TaskEntity>

    @Insert
    suspend fun insert(task: TaskEntity): Long

    @Update
    suspend fun update(task: TaskEntity)

    @Delete
    suspend fun delete(task: TaskEntity)
}

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites WHERE createdAt BETWEEN :start AND :end ORDER BY createdAt DESC")
    fun observeBetween(start: Long, end: Long): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<FavoriteEntity?>

    @Query("""
        SELECT * FROM favorites
        WHERE title LIKE '%' || :query || '%'
           OR rawText LIKE '%' || :query || '%'
           OR note LIKE '%' || :query || '%'
        ORDER BY createdAt DESC
    """)
    fun observeSearch(query: String): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites WHERE url = :url LIMIT 1")
    suspend fun findByUrl(url: String): FavoriteEntity?

    @Insert
    suspend fun insert(item: FavoriteEntity): Long

    @Update
    suspend fun update(item: FavoriteEntity)

    @Delete
    suspend fun delete(item: FavoriteEntity)
}

@Database(
    entities = [NoteEntity::class, TaskEntity::class, FavoriteEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun taskDao(): TaskDao
    abstract fun favoriteDao(): FavoriteDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS favorites (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        url TEXT NOT NULL,
                        platform TEXT NOT NULL,
                        rawText TEXT NOT NULL,
                        note TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "shiguangbox.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
