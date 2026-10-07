package com.babakriazi.nava

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.babakriazi.nava.data.Song

class LibraryFragment : Fragment() {

    private var songs: List<Song> = emptyList()
    private lateinit var adapter: SongAdapter
    private lateinit var recycler: RecyclerView
    private lateinit var empty: TextView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_library, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        recycler = view.findViewById(R.id.recyclerSongs)
        empty = view.findViewById(R.id.txtEmpty)
        val search = view.findViewById<EditText>(R.id.edtSearch)

        adapter = SongAdapter(
            onClick = { song, list ->
                val idx = list.indexOf(song)
                (activity as? MainActivity)?.playSongList(list, idx.coerceAtLeast(0))
            },
            onMore = { song -> showAddToPlaylist(song) }
        )
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filter(s?.toString() ?: "")
            }
        })

        val main = activity as? MainActivity
        if (main != null && main.allSongs.isNotEmpty()) {
            refresh(main.allSongs)
        }
    }

    fun refresh(list: List<Song>) {
        songs = list
        adapter.submit(list)
        empty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun filter(q: String) {
        val filtered = if (q.isBlank()) songs else songs.filter {
            it.title.contains(q, true) || it.artist.contains(q, true) || it.album.contains(q, true)
        }
        adapter.submit(filtered)
    }

    private fun showAddToPlaylist(song: Song) {
        val main = activity as? MainActivity ?: return
        val playlists = main.repository.getPlaylists()
        if (playlists.isEmpty()) {
            Toast.makeText(requireContext(), "Create a playlist first", Toast.LENGTH_SHORT).show()
            return
        }
        val names = playlists.map { it.name }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.add_to_playlist)
            .setItems(names) { _, which ->
                main.repository.addSongToPlaylist(playlists[which].id, song.id)
                Toast.makeText(requireContext(), "Added to ${names[which]}", Toast.LENGTH_SHORT).show()
            }
            .show()
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
                duration.text = formatTime(song.duration)
                itemView.setOnClickListener { onClick(song, items) }
                more.setOnClickListener { onMore(song) }
            }

            private fun formatTime(ms: Long): String {
                val s = (ms / 1000).toInt()
                return "%d:%02d".format(s / 60, s % 60)
            }
        }
    }
}
