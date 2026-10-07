package com.babakriazi.nava

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.babakriazi.nava.data.MusicRepository
import com.babakriazi.nava.data.Prefs
import com.babakriazi.nava.data.Song
import com.babakriazi.nava.playback.PlayerController
import com.google.android.material.button.MaterialButton

class PlaylistDetailActivity : AppCompatActivity() {

    private lateinit var repo: MusicRepository
    private lateinit var playlistId: String
    private var songs: List<Song> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_playlist_detail)

        playlistId = intent.getStringExtra("playlist_id") ?: run { finish(); return }
        val name = intent.getStringExtra("playlist_name") ?: "Playlist"

        repo = MusicRepository(this)

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        toolbar.setTitle(name)
        toolbar.setNavigationOnClickListener { finish() }

        val recycler = findViewById<RecyclerView>(R.id.recycler)
        val empty = findViewById<TextView>(R.id.txtEmpty)
        val adapter = SongAdapter(
            onClick = { song, list ->
                val idx = list.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
                playList(list, idx, shuffle = false)
            },
            onMore = { song ->
                AlertDialog.Builder(this)
                    .setTitle(song.title)
                    .setItems(arrayOf(getString(R.string.remove_from_playlist))) { _, _ ->
                        repo.removeSongFromPlaylist(playlistId, song.id)
                        loadSongs(recycler, empty, adapter, toolbar)
                    }
                    .show()
            }
        )
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        findViewById<MaterialButton>(R.id.btnPlayAll).setOnClickListener {
            if (songs.isEmpty()) {
                Toast.makeText(this, R.string.empty_playlist, Toast.LENGTH_SHORT).show()
            } else playList(songs, 0, false)
        }
        findViewById<MaterialButton>(R.id.btnShuffle).setOnClickListener {
            if (songs.isEmpty()) {
                Toast.makeText(this, R.string.empty_playlist, Toast.LENGTH_SHORT).show()
            } else playList(songs, 0, true)
        }

        loadSongs(recycler, empty, adapter, toolbar)
    }

    private fun loadSongs(
        recycler: RecyclerView,
        empty: TextView,
        adapter: SongAdapter,
        toolbar: com.google.android.material.appbar.MaterialToolbar
    ) {
        val pl = repo.getPlaylists().find { it.id == playlistId }
        val all = repo.scanSongs()
        songs = pl?.songIds?.mapNotNull { id -> all.find { it.id == id } } ?: emptyList()
        adapter.submit(songs)
        empty.visibility = if (songs.isEmpty()) View.VISIBLE else View.GONE
        recycler.visibility = if (songs.isEmpty()) View.GONE else View.VISIBLE
        toolbar.subtitle = getString(R.string.songs_count, songs.size)
    }

    private fun playList(list: List<Song>, index: Int, shuffle: Boolean) {
        PlayerController.connect(this) {
            PlayerController.playSongs(list, index)
            PlayerController.setShuffle(shuffle)
            val prefs = Prefs(this)
            prefs.lastSongIds = list.map { it.id }
            prefs.lastIndex = index
            prefs.shuffle = shuffle
        }
    }

    private class SongAdapter(
        val onClick: (Song, List<Song>) -> Unit,
        val onMore: (Song) -> Unit
    ) : RecyclerView.Adapter<SongAdapter.VH>() {

        private var items = listOf<Song>()

        fun submit(list: List<Song>) {
            items = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_song, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])
        override fun getItemCount() = items.size

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            private val title: TextView = v.findViewById(R.id.txtTitle)
            private val artist: TextView = v.findViewById(R.id.txtArtist)
            private val duration: TextView = v.findViewById(R.id.txtDuration)
            private val more: ImageButton = v.findViewById(R.id.btnMore)

            fun bind(song: Song) {
                title.text = song.title
                artist.text = song.artist
                val s = (song.duration / 1000).toInt()
                duration.text = "%d:%02d".format(s / 60, s % 60)
                itemView.setOnClickListener { onClick(song, items) }
                more.setOnClickListener { onMore(song) }
            }
        }
    }
}
