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
        ChannelEntity::class,
        StickerEntity::class,
        ReceiptEntity::class
    ],
    version = 8,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun profileDao(): ProfileDao
    abstract fun contactDao(): ContactDao
    abstract fun statusDao(): StatusDao
    abstract fun channelDao(): ChannelDao
    abstract fun stickerDao(): StickerDao
    abstract fun receiptDao(): ReceiptDao

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

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_messages_chatJid_fromMe_isRead` " +
                            "ON `messages` (`chatJid`, `fromMe`, `isRead`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_messages_quotedId` " +
                            "ON `messages` (`quotedId`)"
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN mediaSeconds INTEGER")
                db.execSQL("ALTER TABLE messages ADD COLUMN mediaWaveform BLOB")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN mediaAnimated INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE TABLE IF NOT EXISTS `stickers` (`id` TEXT NOT NULL, `url` TEXT NOT NULL, `directPath` TEXT NOT NULL, `mediaKey` BLOB NOT NULL, `fileEncSha256` BLOB NOT NULL, `fileSha256` BLOB NOT NULL, `fileLength` INTEGER NOT NULL, `mimetype` TEXT NOT NULL, `animated` INTEGER NOT NULL, `width` INTEGER NOT NULL, `height` INTEGER NOT NULL, `localPath` TEXT, `isFavorite` INTEGER NOT NULL, `lastUsed` INTEGER NOT NULL, `receivedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_stickers_isFavorite` ON `stickers` (`isFavorite`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_stickers_lastUsed` ON `stickers` (`lastUsed`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_stickers_receivedAt` ON `stickers` (`receivedAt`)")
            }
        }
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN mediaFileName TEXT")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE messages ADD COLUMN isForwarded INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE messages ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE messages ADD COLUMN pinnedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE messages ADD COLUMN pinExpiresAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE TABLE IF NOT EXISTS `message_receipts` (`messageId` TEXT NOT NULL, `jid` TEXT NOT NULL, `deliveredAt` INTEGER NOT NULL, `readAt` INTEGER NOT NULL, PRIMARY KEY(`messageId`, `jid`))")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN deletedAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun get(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "whatsgo_messages.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                    .fallbackToDestructiveMigration(true)
                    .build().also { instance = it }
            }
        }
    }
}
