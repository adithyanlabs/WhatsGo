package com.whatsgo.app.core.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        MessageEntity::class,
        ProfileEntity::class,
        ContactEntity::class,
        StatusEntity::class,
        StatusViewerEntity::class,
        ChannelEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun profileDao(): ProfileDao
    abstract fun contactDao(): ContactDao
    abstract fun statusDao(): StatusDao
    abstract fun channelDao(): ChannelDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `channels` (" +
                            "`jid` TEXT NOT NULL, " +
                            "`name` TEXT NOT NULL, " +
                            "`description` TEXT NOT NULL, " +
                            "`subscribers` INTEGER NOT NULL, " +
                            "`verified` INTEGER NOT NULL, " +
                            "`muted` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`jid`))"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_messages_chatJid_timestamp` " +
                            "ON `messages` (`chatJid`, `timestamp`)"
                )
            }
        }

        fun get(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "whatsgo_messages.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration(true)
                    .build().also { instance = it }
            }
        }
    }
}
