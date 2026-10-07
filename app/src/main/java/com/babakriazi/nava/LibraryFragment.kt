package com.babakriazi.nava

import android.content.ContentValues
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
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
            onMore = { song -> showSongMenu(song) }
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
            refresh(main.sortedSongs())
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

    private fun showSongMenu(song: Song) {
        val items = arrayOf(
            getString(R.string.add_to_playlist),
            getString(R.string.set_ringtone),
            getString(R.string.play_all),
            getString(R.string.delete)
        )
        AlertDialog.Builder(requireContext())
            .setTitle(song.title)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> showAddToPlaylist(song)
                    1 -> setAsRingtone(song)
                    2 -> {
                        val main = activity as? MainActivity ?: return@setItems
                        val list = main.sortedSongs()
                        val idx = list.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
                        main.playSongList(list, idx)
                    }
                    3 -> confirmDelete(song)
                }
            }
            .show()
    }

    private fun confirmDelete(song: Song) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete song?")
            .setMessage("Remove \"${song.title}\" from this device?\n\nThis cannot be undone.")
            .setPositiveButton("Delete") { _, _ -> deleteSong(song) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteSong(song: Song) {
        try {
            val uri = Uri.parse(song.uri)
            val rows = requireContext().contentResolver.delete(uri, null, null)
            if (rows > 0) {
                Toast.makeText(requireContext(), "Deleted", Toast.LENGTH_SHORT).show()
                (activity as? MainActivity)?.loadLibrary()
            } else {
                // Fallback: hide from library by filtering after rescan may still show
                // Try MediaStore delete by id
                val delUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                val deleted = requireContext().contentResolver.delete(
                    delUri,
                    "${MediaStore.Audio.Media._ID}=?",
                    arrayOf(song.id.toString())
                )
                if (deleted > 0) {
                    Toast.makeText(requireContext(), "Deleted", Toast.LENGTH_SHORT).show()
                    (activity as? MainActivity)?.loadLibrary()
                } else {
                    Toast.makeText(
                        requireContext(),
                        "Could not delete (system may protect this file)",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        } catch (e: SecurityException) {
            Toast.makeText(
                requireContext(),
                "Permission denied — Android may require confirmation dialog",
                Toast.LENGTH_LONG
            ).show()
            // On Android 10+ may need createDeleteRequest
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val uri = Uri.parse(song.uri)
                    val request = MediaStore.createDeleteRequest(
                        requireContext().contentResolver,
                        listOf(uri)
                    )
                    startIntentSenderForResult(request.intentSender, 1001, null, 0, 0, 0)
                } catch (ex: Exception) {
                    Toast.makeText(requireContext(), "Delete failed: ${ex.message}", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Delete failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
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
                val pl = playlists[which]
                if (pl.songIds.contains(song.id)) {
                    Toast.makeText(requireContext(), R.string.already_in_playlist, Toast.LENGTH_SHORT).show()
                } else {
                    main.repository.addSongToPlaylist(pl.id, song.id)
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.added_to_playlist, pl.name),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .show()
    }

    private fun setAsRingtone(song: Song) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.System.canWrite(requireContext())) {
                Toast.makeText(requireContext(), "Allow modify system settings", Toast.LENGTH_LONG).show()
                startActivity(
                    Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                        data = Uri.parse("package:${requireContext().packageName}")
                    }
                )
                return
            }
            val uri = Uri.parse(song.uri)
            RingtoneManager.setActualDefaultRingtoneUri(
                requireContext(),
                RingtoneManager.TYPE_RINGTONE,
                uri
            )
            try {
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.IS_RINGTONE, true)
                }
                requireContext().contentResolver.update(uri, values, null, null)
            } catch (_: Exception) {}
            Toast.makeText(requireContext(), "Set as ringtone", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Could not set ringtone: ${e.message}", Toast.LENGTH_LONG).show()
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
                artist.text = if (song.artist.isBlank() || song.artist == "<unknown>")
                    itemView.context.getString(R.string.unknown_artist) else song.artist
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
