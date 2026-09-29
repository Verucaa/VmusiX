package com.zaaam.vmusix.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Skema v1. VmusiX memakai package sendiri (`com.zaaam.vmusix`) dan keystore
 * sendiri, jadi ini app baru dengan data baru — tidak ada jalur upgrade dari
 * Liphify, makanya tidak ada tabel migrasi.
 *
 * Kalau suatu saat skema berubah: tambah MIGRATION_x_y + bump `version` di
 * bawah. DILARANG `fallbackToDestructiveMigration()` untuk upgrade — itu
 * menghapus library user tanpa warning.
 */
@Database(
    entities = [
        TrackEntity::class,
        PlaylistEntity::class,
        PlaylistTrackEntity::class,
        HistoryEntity::class,
        QueueEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun historyDao(): HistoryDao
    abstract fun queueDao(): QueueDao
}
