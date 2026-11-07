package com.whatsgo.app.core.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        MessageEntity::class,
        ProfileEntity::class,
        ContactEntity::class,
        StatusEntity::class,
        StatusViewerEntity::class
    ],
    version = 1
)

abstract class AppDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun profileDao(): ProfileDao
    abstract fun contactDao(): ContactDao
    abstract fun statusDao(): StatusDao

    companion object {
        @Volatile private var instance: AppDatabase? = null
        fun get(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "whatsgo_messages.db"
                )
                    .fallbackToDestructiveMigration(true)
                    .build().also { instance = it }
            }
        }
    }
}
