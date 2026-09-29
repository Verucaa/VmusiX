package com.zaaam.vmusix.ui.search

import android.content.Context

private const val PREFS = "vmusix_search"
private const val KEY = "recent"

/** Riwayat query terakhir (persisten SharedPreferences), max 8. */
object RecentQueries {
    fun load(ctx: Context): List<String> =
        try {
            (ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "") ?: "")
                .split("\n").filter { it.isNotBlank() }.take(8)
        } catch (_: Exception) {
            emptyList()
        }

    fun save(ctx: Context, q: String) {
        try {
            val cur = load(ctx).toMutableList()
            cur.remove(q)
            cur.add(0, q)
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY, cur.take(8).joinToString("\n")).apply()
        } catch (_: Exception) {
        }
    }
}
