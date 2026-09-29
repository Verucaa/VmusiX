package com.zaaam.vmusix.core

import android.content.Context
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Crash log sederhana: tulis stack trace terakhir ke `filesDir/crash.log`.
 *
 * Kenapa perlu: troubleshooting "force close" biasanya butuh adb + logcat,
 * yang tidak bisa dilakukan user awam di HP-nya sendiri. Dengan ini, begitu
 * app dibuka lagi, errornya bisa dibaca langsung di dalam app.
 *
 * sengaja tidak pakai library crash reporter: satu file teks + satu dialog
 * cukup, dan tidak mengirim data ke mana pun.
 */
object CrashLog {
    private const val FILE = "crash.log"
    private const val MAX_CHARS = 16_000

    fun install(ctx: Context, previous: Thread.UncaughtExceptionHandler?) {
        val app = ctx.applicationContext
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write(app, thread.name, error) }
            // Selalu teruskan ke handler sistem (biar app tetap mati seperti
            // seharusnya, dan process manager bisa me-restart dengan benar).
            previous?.uncaughtException(thread, error)
        }
    }

    fun write(ctx: Context, thread: String, error: Throwable) {
        val sw = StringWriter()
        PrintWriter(sw).use { error.printStackTrace(it) }
        val body = buildString {
            append("time: ").append(java.util.Date())
            append("\nthread: ").append(thread)
            append("\n\n").append(sw.toString())
        }
        runCatching {
            ctx.openFileOutput(FILE, Context.MODE_APPEND).use {
                it.write(body.takeLast(MAX_CHARS).toByteArray())
            }
        }
    }

    fun read(ctx: Context): String? = runCatching {
        ctx.openFileInput(FILE).bufferedReader().use { it.readText().takeLast(MAX_CHARS) }
    }.getOrNull()?.takeIf { it.isNotBlank() }

    fun clear(ctx: Context) {
        runCatching { ctx.deleteFile(FILE) }
    }
}
