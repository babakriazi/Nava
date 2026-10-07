package com.babakriazi.nava

import android.app.Activity
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
            getString(R.string.rename),
            getString(R.string.set_ringtone),
            getString(R.string.play_all),
            getString(R.string.delete)
        )
        AlertDialog.Builder(requireContext())
            .setTitle(song.title)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> showAddToPlaylist(song)
                    1 -> showRename(song)
                    2 -> setAsRingtone(song)
                    3 -> {
                        val main = activity as? MainActivity ?: return@setItems
                        val list = main.sortedSongs()
                        val idx = list.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
                        main.playSongList(list, idx)
                    }
                    4 -> confirmDelete(song)
                }
            }
            .show()
    }

    private fun showRename(song: Song) {
        val input = EditText(requireContext()).apply {
            setText(song.title)
            setSelection(song.title.length)
            setPadding(48, 32, 48, 32)
            hint = getString(R.string.new_title)
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.rename)
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                val newTitle = input.text.toString().trim()
                if (newTitle.isNotBlank() && newTitle != song.title) {
                    renameSong(song, newTitle)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun renameSong(song: Song, newTitle: String) {
        try {
            val uri = Uri.parse(song.uri)
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.TITLE, newTitle)
                put(MediaStore.Audio.Media.DISPLAY_NAME, newTitle)
            }
            val rows = requireContext().contentResolver.update(uri, values, null, null)
            if (rows > 0) {
                Toast.makeText(requireContext(), R.string.renamed, Toast.LENGTH_SHORT).show()
                (activity as? MainActivity)?.loadLibrary()
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Need user consent to write
                try {
                    val request = MediaStore.createWriteRequest(
                        requireContext().contentResolver,
                        listOf(uri)
                    )
                    // Store pending rename in arguments via activity result is complex;
                    // ask user to grant then try again
                    startIntentSenderForResult(request.intentSender, 1002, null, 0, 0, 0, null)
                    Toast.makeText(
                        requireContext(),
                        "Allow edit, then rename again",
                        Toast.LENGTH_LONG
                    ).show()
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Rename failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(requireContext(), "Could not rename", Toast.LENGTH_SHORT).show()
            }
        } catch (e: SecurityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val uri = Uri.parse(song.uri)
                    val request = MediaStore.createWriteRequest(
                        requireContext().contentResolver,
                        listOf(uri)
                    )
                    startIntentSenderForResult(request.intentSender, 1002, null, 0, 0, 0, null)
                    Toast.makeText(
                        requireContext(),
                        "Allow edit, then rename again",
                        Toast.LENGTH_LONG
                    ).show()
                } catch (ex: Exception) {
                    Toast.makeText(requireContext(), "Rename failed: ${ex.message}", Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(requireContext(), "Permission denied", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Rename failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
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
        val act = activity ?: return
        try {
            val uri = Uri.parse(song.uri)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val request = MediaStore.createDeleteRequest(
                    act.contentResolver,
                    listOf(uri)
                )
                startIntentSenderForResult(request.intentSender, 1001, null, 0, 0, 0, null)
            } else {
                val rows = act.contentResolver.delete(uri, null, null)
                if (rows > 0) {
                    Toast.makeText(requireContext(), "Deleted", Toast.LENGTH_SHORT).show()
                    (act as? MainActivity)?.loadLibrary()
                } else {
                    Toast.makeText(requireContext(), "Could not delete file", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Delete failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                1001 -> {
                    Toast.makeText(requireContext(), "Deleted", Toast.LENGTH_SHORT).show()
                    (activity as? MainActivity)?.loadLibrary()
                }
                1002 -> {
                    Toast.makeText(requireContext(), "Permission granted — rename again", Toast.LENGTH_SHORT).show()
                }
            }
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
