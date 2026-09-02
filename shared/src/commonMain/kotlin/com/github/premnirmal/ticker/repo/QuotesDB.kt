package com.github.premnirmal.ticker.repo

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.github.premnirmal.ticker.components.ioDispatcher
import com.github.premnirmal.ticker.repo.data.FetchLogRow
import com.github.premnirmal.ticker.repo.data.HoldingRow
import com.github.premnirmal.ticker.repo.data.PropertiesRow
import com.github.premnirmal.ticker.repo.data.QuoteRow
import com.github.premnirmal.ticker.repo.migrations.allMigrations

@Database(
    entities = [QuoteRow::class, HoldingRow::class, PropertiesRow::class, FetchLogRow::class],
    version = 10,
    exportSchema = true
)
@ConstructedBy(QuotesDBConstructor::class)
abstract class QuotesDB : RoomDatabase() {
    abstract fun quoteDao(): QuoteDao
}

// The Room compiler generates the `actual` implementation of this constructor for every target.
// `expect` object boilerplate must declare no members.
@Suppress("KotlinNoActualForExpect", "EXPECT_ACTUAL_LIBRARY_INCOMPATIBILITY")
expect object QuotesDBConstructor : RoomDatabaseConstructor<QuotesDB> {
    override fun initialize(): QuotesDB
}

const val QUOTES_DB_NAME: String = "quotes-db"

/**
 * Finishes building the [QuotesDB] from a platform-provided [builder] (Android supplies a
 * `Context`, iOS a file path). All shared configuration — the bundled SQLite driver, the IO
 * coroutine context and the full migration chain — lives here so every platform builds the database
 * identically and existing Android installs migrate transparently.
 */
fun buildQuotesDB(builder: RoomDatabase.Builder<QuotesDB>): QuotesDB {
    return builder
        .addMigrations(*allMigrations)
        .setDriver(BundledSQLiteDriver())
        // The bundled driver opens a fresh connection per query and has no busy timeout, so
        // concurrent writers (e.g. a fetch's upsert racing a positions import) fail immediately
        // with SQLITE_BUSY. Serialising every DB operation onto a single-threaded context removes
        // the write contention entirely — DB throughput here is far below what one thread allows.
        .setQueryCoroutineContext(ioDispatcher.limitedParallelism(1))
        .build()
}
