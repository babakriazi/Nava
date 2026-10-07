package com.babakriazi.nava

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.media3.common.Player
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.babakriazi.nava.data.MusicRepository
import com.babakriazi.nava.data.Prefs
import com.babakriazi.nava.data.Song
import com.babakriazi.nava.playback.PlayerController
import com.google.android.material.card.MaterialCardView
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class MainActivity : AppCompatActivity() {

    lateinit var repository: MusicRepository
    lateinit var prefs: Prefs
    var allSongs: List<Song> = emptyList()
    var sortMode: SortMode = SortMode.TITLE

    private lateinit var miniPlayer: MaterialCardView
    private lateinit var miniTitle: TextView
    private lateinit var miniPlayPause: ImageButton
    private var sleepTimer: CountDownTimer? = null
    private var restoredOnce = false

    enum class SortMode { TITLE, ARTIST, DURATION }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            loadLibrary()
            tryRestoreLastSession()
        } else {
            Toast.makeText(this, getString(R.string.permission_required), Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        repository = MusicRepository(this)
        prefs = Prefs(this)

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = getString(R.string.app_name)

        val viewPager = findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.viewPager)
        val tabLayout = findViewById<TabLayout>(R.id.tabLayout)
        viewPager.adapter = PagerAdapter(this)
        TabLayoutMediator(tabLayout, viewPager) { tab, pos ->
            tab.text = if (pos == 0) getString(R.string.library) else getString(R.string.playlists)
        }.attach()

        miniPlayer = findViewById(R.id.miniPlayer)
        miniTitle = findViewById(R.id.miniTitle)
        miniPlayPause = findViewById(R.id.miniPlayPause)
        findViewById<ImageButton>(R.id.miniNext).setOnClickListener { PlayerController.next() }

        miniPlayer.setOnClickListener {
            startActivity(Intent(this, NowPlayingActivity::class.java))
        }
        miniPlayPause.setOnClickListener { PlayerController.togglePlayPause(); updateMini() }

        requestPerms()
        PlayerController.connect(this) {
            PlayerController.controller?.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    updateMini()
                    if (!isPlaying) PlayerController.saveState(this@MainActivity)
                }
                override fun onMediaItemTransition(item: androidx.media3.common.MediaItem?, reason: Int) {
                    updateMini()
                    PlayerController.saveState(this@MainActivity)
                }
            })
            updateMini()
        }
    }

    override fun onPause() {
        super.onPause()
        PlayerController.saveState(this)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_play_all -> {
                if (allSongs.isNotEmpty()) playSongList(sortedSongs(), 0)
                return true
            }
            R.id.action_shuffle_all -> {
                if (allSongs.isNotEmpty()) {
                    playSongList(sortedSongs(), 0)
                    PlayerController.setShuffle(true)
                    prefs.setShuffle(true)
                }
                return true
            }
            R.id.action_sort -> {
                AlertDialog.Builder(this)
                    .setTitle(R.string.sort)
                    .setItems(arrayOf(getString(R.string.sort_title), getString(R.string.sort_artist), getString(R.string.sort_duration))) { _, which ->
                        sortMode = when (which) {
                            1 -> SortMode.ARTIST
                            2 -> SortMode.DURATION
                            else -> SortMode.TITLE
                        }
                        supportFragmentManager.fragments.forEach {
                            if (it is LibraryFragment) it.refresh(sortedSongs())
                        }
                    }.show()
                return true
            }
            R.id.action_equalizer -> {
                startActivity(Intent(this, EqualizerActivity::class.java))
                return true
            }
            R.id.action_sleep -> {
                val options = arrayOf("Off", "15 min", "30 min", "45 min", "60 min", "90 min")
                val minutes = intArrayOf(0, 15, 30, 45, 60, 90)
                AlertDialog.Builder(this)
                    .setTitle(R.string.sleep_timer)
                    .setItems(options) { _, which ->
                        sleepTimer?.cancel()
                        val min = minutes[which]
                        if (min == 0) {
                            Toast.makeText(this, "Sleep timer off", Toast.LENGTH_SHORT).show()
                            return@setItems
                        }
                        sleepTimer = object : CountDownTimer(min * 60_000L, 30_000L) {
                            override fun onTick(m: Long) {}
                            override fun onFinish() {
                                PlayerController.controller?.pause()
                                PlayerController.saveState(this@MainActivity)
                                Toast.makeText(this@MainActivity, "Sleep timer — paused", Toast.LENGTH_LONG).show()
                            }
                        }.start()
                        Toast.makeText(this, "Sleep in $min min", Toast.LENGTH_SHORT).show()
                    }.show()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    fun sortedSongs(): List<Song> = when (sortMode) {
        SortMode.TITLE -> allSongs.sortedBy { it.title.lowercase() }
        SortMode.ARTIST -> allSongs.sortedBy { it.artist.lowercase() }
        SortMode.DURATION -> allSongs.sortedBy { it.duration }
    }

    private fun requestPerms() {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.READ_MEDIA_AUDIO)
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            perms.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        val need = perms.any {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (need) permissionLauncher.launch(perms.toTypedArray()) else {
            loadLibrary()
            tryRestoreLastSession()
        }
    }

    fun loadLibrary() {
        allSongs = repository.scanSongs()
        supportFragmentManager.fragments.forEach {
            if (it is LibraryFragment) it.refresh(sortedSongs())
            if (it is PlaylistsFragment) it.refresh()
        }
    }

    private fun tryRestoreLastSession() {
        if (restoredOnce) return
        restoredOnce = true
        val ids = prefs.getLastSongIds()
        if (ids.isEmpty() || allSongs.isEmpty()) return
        val queue = ids.mapNotNull { id -> allSongs.find { it.id == id } }
        if (queue.isEmpty()) return
        val index = prefs.getLastIndex().coerceIn(0, queue.size - 1)
        val pos = prefs.getLastPosition()
        PlayerController.connect(this) {
            val c = PlayerController.controller ?: return@connect
            if (c.mediaItemCount == 0) {
                PlayerController.playSongs(queue, index, pos)
                c.pause()
                PlayerController.setShuffle(prefs.getShuffle())
                PlayerController.setRepeat(prefs.getRepeatMode())
                updateMini()
            }
        }
    }

    fun playSongList(songs: List<Song>, index: Int) {
        PlayerController.connect(this) {
            PlayerController.playSongs(songs, index)
            prefs.setLastSongIds(songs.map { it.id })
            prefs.setLastIndex(index)
            prefs.setLastPosition(0)
            updateMini()
        }
    }

    private fun updateMini() {
        val c = PlayerController.controller
        if (c == null || c.mediaItemCount == 0) {
            miniPlayer.visibility = View.GONE
            return
        }
        miniPlayer.visibility = View.VISIBLE
        miniTitle.text = c.mediaMetadata.title ?: ""
        miniPlayPause.setImageResource(
            if (c.isPlaying) android.R.drawable.ic_media_pause
            else android.R.drawable.ic_media_play
        )
    }

    override fun onDestroy() {
        sleepTimer?.cancel()
        PlayerController.saveState(this)
        super.onDestroy()
    }

    private class PagerAdapter(fa: FragmentActivity) : FragmentStateAdapter(fa) {
        override fun getItemCount() = 2
        override fun createFragment(position: Int): Fragment =
            if (position == 0) LibraryFragment() else PlaylistsFragment()
    }
}
