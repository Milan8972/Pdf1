package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

enum class DocumentSource {
    OPENED,
    CREATED,
    DOWNLOADS,
    SCANNED
}

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val filePath: String,
    val mimeType: String = "application/pdf",
    val sizeBytes: Long = 0L,
    val pageCount: Int = 1,
    val source: String = DocumentSource.CREATED.name,
    val isFavorite: Boolean = false,
    val isProtected: Boolean = false,
    val passwordHash: String? = null,
    val allowPrint: Boolean = true,
    val allowCopy: Boolean = true,
    val allowEdit: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val lastOpenedAt: Long = System.currentTimeMillis(),
    val thumbnailPath: String? = null,
    val extractedText: String = "",
    val isTrashed: Boolean = false
)

@Entity(tableName = "signatures")
data class SignatureEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val type: String, // DRAWN, TYPED, IMAGE
    val imagePath: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "scanned_images")
data class ScannedImageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val imagePath: String,
    val filterName: String = "Auto-enhance",
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents WHERE isTrashed = 0 ORDER BY lastOpenedAt DESC")
    fun getAllDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE isTrashed = 1 ORDER BY lastOpenedAt DESC")
    fun getTrashedDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): DocumentEntity?

    @Query("SELECT COUNT(*) FROM documents")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(doc: DocumentEntity): Long

    @Update
    suspend fun update(doc: DocumentEntity)

    @Delete
    suspend fun delete(doc: DocumentEntity)

    @Query("DELETE FROM documents WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM documents WHERE isTrashed = 1")
    suspend fun emptyBin()

    @Query("UPDATE documents SET isTrashed = :trashed WHERE id = :id")
    suspend fun setTrashed(id: Long, trashed: Boolean)

    @Query("UPDATE documents SET isTrashed = :trashed WHERE id IN (:ids)")
    suspend fun setTrashedByIds(ids: List<Long>, trashed: Boolean)

    @Query("UPDATE documents SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("UPDATE documents SET name = :newName WHERE id = :id")
    suspend fun rename(id: Long, newName: String)

    @Query("UPDATE documents SET lastOpenedAt = :timestamp WHERE id = :id")
    suspend fun touchOpened(id: Long, timestamp: Long = System.currentTimeMillis())
}

@Dao
interface SignatureDao {
    @Query("SELECT * FROM signatures ORDER BY createdAt DESC")
    fun getAllSignatures(): Flow<List<SignatureEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(signature: SignatureEntity): Long

    @Query("DELETE FROM signatures WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface ScannedImageDao {
    @Query("SELECT * FROM scanned_images ORDER BY createdAt DESC")
    fun getAllScannedImages(): Flow<List<ScannedImageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ScannedImageEntity): Long

    @Query("DELETE FROM scanned_images WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Database(
    entities = [DocumentEntity::class, SignatureEntity::class, ScannedImageEntity::class],
    version = 2,
    exportSchema = false
)
abstract class PaperPilotDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao
    abstract fun signatureDao(): SignatureDao
    abstract fun scannedImageDao(): ScannedImageDao

    companion object {
        @Volatile
        private var INSTANCE: PaperPilotDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE documents ADD COLUMN isTrashed INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): PaperPilotDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    PaperPilotDatabase::class.java,
                    "paperpilot_local.db"
                ).addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
