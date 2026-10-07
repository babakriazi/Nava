package com.babakriazi.nava.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import org.json.JSONArray
import org.json.JSONObject

class MusicRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("nava_playlists", Context.MODE_PRIVATE)

    fun scanSongs(): List<Song> {
        val songs = mutableListOf<Song>()
        val collection = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sort = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        context.contentResolver.query(collection, projection, selection, null, sort)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val albumIdCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)

            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id).toString()
                val albumId = c.getLong(albumIdCol)
                val artUri = ContentUris.withAppendedId(
                    android.net.Uri.parse("content://media/external/audio/albumart"),
                    albumId
                ).toString()

                songs.add(
                    Song(
                        id = id,
                        title = c.getString(titleCol) ?: "Unknown",
                        artist = c.getString(artistCol) ?: "Unknown Artist",
                        album = c.getString(albumCol) ?: "Unknown Album",
                        duration = c.getLong(durCol),
                        uri = uri,
                        albumArtUri = artUri
                    )
                )
            }
        }
        return songs
    }

    fun getPlaylists(): MutableList<Playlist> {
        val json = prefs.getString("playlists", "[]") ?: "[]"
        val arr = JSONArray(json)
        val list = mutableListOf<Playlist>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val ids = mutableListOf<Long>()
            val idArr = o.optJSONArray("songIds") ?: JSONArray()
            for (j in 0 until idArr.length()) ids.add(idArr.getLong(j))
            list.add(Playlist(o.getString("id"), o.getString("name"), ids))
        }
        return list
    }

    fun savePlaylists(playlists: List<Playlist>) {
        val arr = JSONArray()
        playlists.forEach { p ->
            arr.put(JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("songIds", JSONArray(p.songIds))
            })
        }
        prefs.edit().putString("playlists", arr.toString()).apply()
    }

    fun addPlaylist(name: String): Playlist {
        val list = getPlaylists()
        val p = Playlist(name = name)
        list.add(p)
        savePlaylists(list)
        return p
    }

    fun deletePlaylist(id: String) {
        savePlaylists(getPlaylists().filter { it.id != id })
    }

    fun renamePlaylist(id: String, name: String) {
        val list = getPlaylists()
        list.find { it.id == id }?.name = name
        savePlaylists(list)
    }

    fun addSongToPlaylist(playlistId: String, songId: Long) {
        val list = getPlaylists()
        list.find { it.id == playlistId }?.let {
            if (!it.songIds.contains(songId)) it.songIds.add(songId)
        }
        savePlaylists(list)
    }

    fun removeSongFromPlaylist(playlistId: String, songId: Long) {
        val list = getPlaylists()
        list.find { it.id == playlistId }?.songIds?.remove(songId)
        savePlaylists(list)
    }
}
