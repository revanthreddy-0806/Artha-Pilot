package com.arthapilot.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [TransactionEntity::class, StatementEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class ArthaDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun statementDao(): StatementDao
}
