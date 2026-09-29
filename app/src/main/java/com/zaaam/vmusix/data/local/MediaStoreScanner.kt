package com.zaaam.vmusix.data.local

import android.content.ContentUris
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** Folder kanonis library: hanya folder ini yang dibaca. */
const val APP_RELATIVE_PATH = "Music/VmusiX/"

/**
 * Folder lama (sebelum rename aplikasi). Tetap dibaca supaya-library yang sudah
 * ada di perangkat tidak ikut lenyap diam-diam saat upgrade — tanpanya,
 * semua lagu user "hilang" padahal file-nya masih di sana.
 */
const val LEGACY_RELATIVE_PATH = "Music/LiPhify/"

private const val TAG = "VmusiXScan"

/** Batas file yang di-scan per siklus (lihat [MediaStoreScanner.scan]). */
private const val REINDEX_MAX_FILES = 600

/** Lama tunggu MediaStore selesai mengindeks ulang sebelum query. */
private const val REINDEX_TIMEOUT_MS = 8_000L

/**
 * Scan MediaStore TERBATAS ke folder aplikasi ([APP_RELATIVE_PATH]) +
 * folder legacy ([LEGACY_RELATIVE_PATH]) — ringtone/notifikasi/voice note di
 * luar keduanya tidak masuk.
 *
 * Single-flight (Mutex): double-tap Refresh tidak interleave.
 *
 * Dua lapis, karena MediaStore sering "kosong":
 *  1. [reindexFolder] mendaftarkan file yang disalin (ADB, file manager,
 *     Bluetooth) ke MediaStore — file itu belum selalu terindeks saat app
 *     dibuka, dan tanpa ini hasil scan selalu 0 walau folder berisi lagu.
 *  2. Baru query MediaStore di atas.
 */
class MediaStoreScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
) {
    private val mutex = Mutex()

    suspend fun scan(): Int = withContext(Dispatchers.IO) {
        mutex.withLock {
            ensureFolder()
            // Reindeks dulu: MediaStore belum tentu punya entri untuk file baru.
            reindexFolder(appDir())
            appDir().listFiles()?.takeIf { it.isDirectory() }?.let { reindexFolder(it) }
            LEGACY_DIR_NAMES.forEach { legacy ->
                reindexFolder(musicDir().resolve(legacy))
            }

            val items = mutableListOf<TrackEntity>()
            val volumes = if (Build.VERSION.SDK_INT >= 29) {
                try {
                    MediaStore.getExternalVolumeNames(context)
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    Log.w(TAG, "getExternalVolumeNames gagal", e)
                    setOf(MediaStore.VOLUME_EXTERNAL)
                }
            } else {
                setOf(MediaStore.VOLUME_EXTERNAL)
            }
            for (volume in volumes) {
                try {
                    items += queryVolume(volume)
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    Log.w(TAG, "scan volume $volume gagal", e)
                }
            }
            // Sinkronisasi atomik: upsert + hapus yang hilang + purge luar folder
            // dalam satu transaksi, supaya app yang mati di tengah tidak
            // menyisakan DB setengah jadi (lagu dobel / nyassa hilang).
            db.withTransaction {
                db.trackDao().upsertAll(items)
                val keep = items.map { it.contentUri }.toSet()
                val toDelete = db.trackDao().existingUris().filter { it !in keep }
                toDelete.chunked(500).forEach { chunk ->
                    try {
                        db.trackDao().deleteByUris(chunk)
                    } catch (e: Exception) {
                        Log.w(TAG, "delete chunk gagal", e)
                    }
                }
                try {
                    val purged = db.trackDao().purgeOutsideFolders(
                        "Music/VmusiX/%", "Music/VmusiX", "Music/LiPhify/%", "Music/LiPhify",
                    )
                    if (purged > 0) Log.d(TAG, "purge luar folder: $purged baris")
                } catch (e: Exception) {
                    Log.w(TAG, "purge gagal", e)
                }
            }
            Log.d(TAG, "scan selesai: ${items.size} lagu di $APP_RELATIVE_PATH")
            items.size
        }
    }

    private fun musicDir(): File =
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "")

    private fun appDir(): File = musicDir().resolve("VmusiX")

    private val LEGACY_DIR_NAMES = listOf("LiPhify")

    /** Buat folder kanonis (best-effort) supaya ada sebelum user menaruh file. */
    private fun ensureFolder() {
        try {
            val dir = appDir()
            if (!dir.exists()) dir.mkdirs()
        } catch (e: Exception) {
            Log.w(TAG, "ensureFolder gagal", e)
        }
    }

    /**
     * Daftarkan isi folder ke MediaStore lalu tunggu sampai selesai.
     *
     * `MediaScannerConnection.scanFile()` hanya menerima path FILE, bukan
     * direktori — diberi folder diaDiam-diam tidak melakukan apa-apa. Jadi
     * file-nya yang didaftarkan satu per satu.
     *
     * ponytail: dibatasi [REINDEX_MAX_FILES] per siklus. Folder 10k lagu
     * akan direindeks bertahap (sisanya masuk di scan berikutnya). Naikkan
     * batasnya kalau prosesnya benar-benar lama di perangkat.
     */
    private fun reindexFolder(dir: File) {
        if (!dir.isDirectory) return
        val files = try {
            dir.walkTopDown()
                .maxDepth(2)
                .filter { it.isFile }
                .take(REINDEX_MAX_FILES)
                .map { it.absolutePath }
                .toList()
        } catch (e: Exception) {
            Log.w(TAG, "walk ${dir.name} gagal", e)
            return
        }
        if (files.isEmpty()) return
        val latch = CountDownLatch(1)
        try {
            // mimeTypes null = semua jenis, bukan cuma audio/*: file tanpa
            // ekstensi / berekstensi aneh tidak boleh terlewat.
            MediaScannerConnection.scanFile(
                context,
                files.toTypedArray(),
                null,
                object : MediaScannerConnection.OnScanCompletedListener {
                    override fun onScanCompleted(path: String, uri: Uri?) = latch.countDown()
                },
            )
            latch.await(REINDEX_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (e: Exception) {
            Log.w(TAG, "scanFile ${dir.name} gagal", e)
        }
    }

    private fun queryVolume(volume: String): List<TrackEntity> {
        val items = mutableListOf<TrackEntity>()
        val collection = MediaStore.Audio.Media.getContentUri(volume)
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.RELATIVE_PATH,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.SIZE,
        )
        // NOCASE + varian tanpa trailing slash: sebagian OEM menulis path berbeda.
        // NULL durasi/size ditoleransi (OR IS NULL) agar tidak buang lagu sah diam-diam.
        //
        // IS_MUSIC sengaja TIDAK dipakai sebagai filter: banyak OEM (khususnya
        // setelah file disalin lewat ADB/file manager) membiarkan kolom itu 0
        // padahal lagunya sah, sehingga library tampil kosong. Folder sudah
        // di-scope ke folder aplikasi, jadi penyaring ringtone/alarm sudah lebih dari cukup.
        val folderMatch =
            "(" +
                "(${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ? ESCAPE '\\' COLLATE NOCASE" +
                " OR ${MediaStore.Audio.Media.RELATIVE_PATH} = ? COLLATE NOCASE)" +
                " OR " +
                "(${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ? ESCAPE '\\' COLLATE NOCASE" +
                " OR ${MediaStore.Audio.Media.RELATIVE_PATH} = ? COLLATE NOCASE)" +
                ")"
        val selection = folderMatch +
            " AND ${MediaStore.Audio.Media.IS_RINGTONE} = 0" +
            " AND ${MediaStore.Audio.Media.IS_NOTIFICATION} = 0" +
            " AND ${MediaStore.Audio.Media.IS_ALARM} = 0" +
            // 5 detik: cukup menendang noise pendek, tidak membuang intro/segmen pendek.
            " AND (${MediaStore.Audio.Media.DURATION} IS NULL OR ${MediaStore.Audio.Media.DURATION} >= 5000)" +
            " AND (${MediaStore.Audio.Media.SIZE} IS NULL OR ${MediaStore.Audio.Media.SIZE} >= 50000)"
        val args = arrayOf("Music/VmusiX/%", "Music/VmusiX", "Music/LiPhify/%", "Music/LiPhify")
        val albumArtBase = Uri.parse("content://media/external/audio/albumart")
        context.contentResolver.query(collection, projection, selection, args, null)?.use { c ->
            // getColumnIndex (bukan OrThrow): satu kolom hilang di OEM tidak
            // menggugurkan seluruh volume.
            val idCol = c.getColumnIndex(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndex(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndex(MediaStore.Audio.Media.ARTIST)
            val albumCol = c.getColumnIndex(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = c.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)
            val durCol = c.getColumnIndex(MediaStore.Audio.Media.DURATION)
            val addedCol = c.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)
            val pathCol = c.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
            val mimeCol = c.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)
            if (idCol < 0) {
                Log.w(TAG, "volume $volume tanpa _ID, diskip")
                return emptyList()
            }
            Log.d(TAG, "volume $volume cursor=${c.count}")
            fun str(col: Int): String? = if (col < 0) null else try {
                c.getString(col)
            } catch (_: Exception) {
                null
            }
            fun lng(col: Int): Long = if (col < 0) 0L else try {
                c.getLong(col)
            } catch (_: Exception) {
                0L
            }
            while (c.moveToNext()) {
                try {
                    val id = c.getLong(idCol)
                    val uri = ContentUris.withAppendedId(collection, id).toString()
                    val albumId = lng(albumIdCol)
                    items.add(
                        TrackEntity(
                            rowKey = uri,
                            volume = volume,
                            mediaId = id,
                            title = str(titleCol) ?: "Unknown",
                            artist = str(artistCol) ?: "Unknown",
                            album = str(albumCol) ?: "",
                            durationMs = lng(durCol),
                            contentUri = uri,
                            dateAdded = lng(addedCol),
                            artworkUri = if (albumId > 0) {
                                ContentUris.withAppendedId(albumArtBase, albumId).toString()
                            } else {
                                null
                            },
                            relativePath = str(pathCol) ?: "",
                            mimeType = str(mimeCol),
                        ),
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "baris rusak diskip", e)
                }
            }
        }
        return items
    }
}
