package com.babakriazi.nava.data

import android.content.Context

class Prefs(context: Context) {

    private val p = context.getSharedPreferences("nava_state", Context.MODE_PRIVATE)

    fun getLastSongIds(): List<Long> {
        val s = p.getString("last_song_ids", "") ?: ""
        if (s.isBlank()) return emptyList()
        return s.split(",").mapNotNull { it.toLongOrNull() }
    }

    fun setLastSongIds(value: List<Long>) {
        p.edit().putString("last_song_ids", value.joinToString(",")).apply()
    }

    fun getLastIndex(): Int = p.getInt("last_index", 0)
    fun setLastIndex(v: Int) { p.edit().putInt("last_index", v).apply() }

    fun getLastPosition(): Long = p.getLong("last_position", 0L)
    fun setLastPosition(v: Long) { p.edit().putLong("last_position", v).apply() }

    fun getShuffle(): Boolean = p.getBoolean("shuffle", false)
    fun setShuffle(v: Boolean) { p.edit().putBoolean("shuffle", v).apply() }

    fun getRepeatMode(): Int = p.getInt("repeat_mode", 0)
    fun setRepeatMode(v: Int) { p.edit().putInt("repeat_mode", v).apply() }

    fun getEqEnabled(): Boolean = p.getBoolean("eq_enabled", true)
    fun setEqEnabled(v: Boolean) { p.edit().putBoolean("eq_enabled", v).apply() }

    fun getBass(): Int = p.getInt("bass", 0)
    fun setBass(v: Int) { p.edit().putInt("bass", v).apply() }

    fun getVirt(): Int = p.getInt("virt", 0)
    fun setVirt(v: Int) { p.edit().putInt("virt", v).apply() }

    fun saveBandLevel(band: Int, level: Int) {
        p.edit().putInt("band_$band", level).apply()
    }

    fun getBandLevel(band: Int, default: Int = 0): Int =
        p.getInt("band_$band", default)

    fun getBandsSaved(): Boolean = p.getBoolean("bands_saved", false)
    fun setBandsSaved(v: Boolean) { p.edit().putBoolean("bands_saved", v).apply() }
}
