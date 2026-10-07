package com.babakriazi.nava.data

import android.content.Context
import android.content.SharedPreferences

class Prefs(context: Context) {

    private val p: SharedPreferences =
        context.getSharedPreferences("nava_state", Context.MODE_PRIVATE)

    // --- Playback resume ---
    var lastSongIds: List<Long>
        get() {
            val s = p.getString("last_song_ids", "") ?: ""
            if (s.isBlank()) return emptyList()
            return s.split(",").mapNotNull { it.toLongOrNull() }
        }
        set(value) {
            p.edit().putString("last_song_ids", value.joinToString(",")).apply()
        }

    var lastIndex: Int
        get() = p.getInt("last_index", 0)
        set(v) = p.edit().putInt("last_index", v).apply()

    var lastPosition: Long
        get() = p.getLong("last_position", 0L)
        set(v) = p.edit().putLong("last_position", v).apply()

    var shuffle: Boolean
        get() = p.getBoolean("shuffle", false)
        set(v) = p.edit().putBoolean("shuffle", v).apply()

    var repeatMode: Int
        get() = p.getInt("repeat_mode", 0) // 0 off, 1 one, 2 all (Player constants)
        set(v) = p.edit().putInt("repeat_mode", v).apply()

    // --- Equalizer ---
    var eqEnabled: Boolean
        get() = p.getBoolean("eq_enabled", true)
        set(v) = p.edit().putBoolean("eq_enabled", v).apply()

    var bassStrength: Int
        get() = p.getInt("bass", 0)
        set(v) = p.edit().putInt("bass", v).apply()

    var virtStrength: Int
        get() = p.getInt("virt", 0)
        set(v) = p.edit().putInt("virt", v).apply()

    fun saveBandLevel(band: Int, level: Int) {
        p.edit().putInt("band_$band", level).apply()
    }

    fun getBandLevel(band: Int, default: Int = 0): Int =
        p.getInt("band_$band", default)

    var bandsSaved: Boolean
        get() = p.getBoolean("bands_saved", false)
        set(v) = p.edit().putBoolean("bands_saved", v).apply()
}
