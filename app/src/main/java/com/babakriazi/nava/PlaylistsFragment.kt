package com.babakriazi.nava

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.babakriazi.nava.data.Playlist
import com.google.android.material.floatingactionbutton.FloatingActionButton

class PlaylistsFragment : Fragment() {

    private lateinit var adapter: PlaylistAdapter
    private lateinit var recycler: RecyclerView
    private lateinit var empty: TextView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_playlists, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        recycler = view.findViewById(R.id.recyclerPlaylists)
        empty = view.findViewById(R.id.txtEmpty)
        val fab = view.findViewById<FloatingActionButton>(R.id.fabAdd)

        adapter = PlaylistAdapter(
            onClick = { playlist -> openPlaylist(playlist) },
            onLongClick = { playlist -> showPlaylistMenu(playlist) }
        )
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        fab.setOnClickListener { createPlaylist() }
        refresh()
    }

    fun refresh() {
        val main = activity as? MainActivity ?: return
        val list = main.repository.getPlaylists()
        adapter.submit(list)
        empty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun createPlaylist() {
        val input = EditText(requireContext()).apply {
            hint = getString(R.string.playlist_name)
            setPadding(48, 32, 48, 32)
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.create_playlist)
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotBlank()) {
                    (activity as MainActivity).repository.addPlaylist(name)
                    refresh()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openPlaylist(playlist: Playlist) {
        val main = activity as? MainActivity ?: return
        val songs = main.allSongs.filter { playlist.songIds.contains(it.id) }
        if (songs.isEmpty()) {
            Toast.makeText(requireContext(), "Playlist is empty", Toast.LENGTH_SHORT).show()
            return
        }
        main.playSongList(songs, 0)
    }

    private fun showPlaylistMenu(playlist: Playlist) {
        AlertDialog.Builder(requireContext())
            .setTitle(playlist.name)
            .setItems(arrayOf(getString(R.string.rename), getString(R.string.delete))) { _, which ->
                val main = activity as MainActivity
                if (which == 0) {
                    val input = EditText(requireContext()).apply {
                        setText(playlist.name)
                        setPadding(48, 32, 48, 32)
                    }
                    AlertDialog.Builder(requireContext())
                        .setTitle(R.string.rename)
                        .setView(input)
                        .setPositiveButton("OK") { _, _ ->
                            val n = input.text.toString().trim()
                            if (n.isNotBlank()) {
                                main.repository.renamePlaylist(playlist.id, n)
                                refresh()
                            }
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                } else {
                    main.repository.deletePlaylist(playlist.id)
                    refresh()
                }
            }
            .show()
    }

    private class PlaylistAdapter(
        val onClick: (Playlist) -> Unit,
        val onLongClick: (Playlist) -> Unit
    ) : RecyclerView.Adapter<PlaylistAdapter.VH>() {

        private var items = listOf<Playlist>()

        fun submit(list: List<Playlist>) {
            items = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_playlist, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])
        override fun getItemCount() = items.size

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            private val name: TextView = v.findViewById(R.id.txtName)
            private val count: TextView = v.findViewById(R.id.txtCount)

            fun bind(p: Playlist) {
                name.text = p.name
                count.text = itemView.context.getString(R.string.songs_count, p.songIds.size)
                itemView.setOnClickListener { onClick(p) }
                itemView.setOnLongClickListener { onLongClick(p); true }
            }
        }
    }
}
