package com.liquid.player.data

import android.content.Context

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("liquid_prefs", Context.MODE_PRIVATE)

    fun favorites(): Set<String> = sp.getStringSet("fav", emptySet())?.toSet() ?: emptySet()

    fun setFavorites(value: Set<String>) {
        sp.edit().putStringSet("fav", value.toSet()).apply()
    }

    fun playCount(key: String): Int = sp.getInt("pc_$key", 0)

    fun incrementPlay(key: String) {
        sp.edit().putInt("pc_$key", playCount(key) + 1).apply()
    }

    fun allPlayCounts(keys: Collection<String>): Map<String, Int> =
        keys.associateWith { playCount(it) }.filterValues { it > 0 }
}
