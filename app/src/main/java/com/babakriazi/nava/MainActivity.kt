package com.babakriazi.nava

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.media3.common.Player
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.babakriazi.nava.data.MusicRepository
import com.babakriazi.nava.data.Song
import com.babakriazi.nava.playback.PlayerController
import com.google.android.material.card.MaterialCardView
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class MainActivity : AppCompatActivity() {

    lateinit var repository: MusicRepository
    var allSongs: List<Song> = emptyList()

    private lateinit var miniPlayer: MaterialCardView
    private lateinit var miniTitle: TextView
    private lateinit var miniPlayPause: ImageButton

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            loadLibrary()
        } else {
            Toast.makeText(this, getString(R.string.permission_required), Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        repository = MusicRepository(this)

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Nava"

        val viewPager = findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.viewPager)
        val tabLayout = findViewById<TabLayout>(R.id.tabLayout)
        viewPager.adapter = PagerAdapter(this)
        TabLayoutMediator(tabLayout, viewPager) { tab, pos ->
            tab.text = if (pos == 0) getString(R.string.library) else getString(R.string.playlists)
        }.attach()

        miniPlayer = findViewById(R.id.miniPlayer)
        miniTitle = findViewById(R.id.miniTitle)
        miniPlayPause = findViewById(R.id.miniPlayPause)
        val miniNext = findViewById<ImageButton>(R.id.miniNext)

        miniPlayer.setOnClickListener {
            startActivity(Intent(this, NowPlayingActivity::class.java))
        }
        miniPlayPause.setOnClickListener { PlayerController.togglePlayPause(); updateMini() }
        miniNext.setOnClickListener { PlayerController.next() }

        requestPerms()
        PlayerController.connect(this) {
            PlayerController.controller?.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) { updateMini() }
                override fun onMediaItemTransition(item: androidx.media3.common.MediaItem?, reason: Int) { updateMini() }
            })
            updateMini()
        }
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
        if (need) permissionLauncher.launch(perms.toTypedArray()) else loadLibrary()
    }

    fun loadLibrary() {
        allSongs = repository.scanSongs()
        supportFragmentManager.fragments.forEach {
            if (it is LibraryFragment) it.refresh(allSongs)
            if (it is PlaylistsFragment) it.refresh()
        }
    }

    fun playSongList(songs: List<Song>, index: Int) {
        PlayerController.connect(this) {
            PlayerController.playSongs(songs, index)
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
        val meta = c.mediaMetadata
        miniTitle.text = meta.title ?: ""
        miniPlayPause.setImageResource(
            if (c.isPlaying) android.R.drawable.ic_media_pause
            else android.R.drawable.ic_media_play
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        // keep service running; don't disconnect
    }

    private class PagerAdapter(fa: FragmentActivity) : FragmentStateAdapter(fa) {
        override fun getItemCount() = 2
        override fun createFragment(position: Int): Fragment =
            if (position == 0) LibraryFragment() else PlaylistsFragment()
    }
}
