package com.iptvplayer.app

import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView

@OptIn(UnstableApi::class)
class PlayerActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private var index = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        playerView = findViewById(R.id.playerView)
        index = savedInstanceState?.getInt("index") ?: intent.getIntExtra("index", 0)
        if (Playlist.current.isEmpty()) {
            finish()
            return
        }
        hideSystemUi()
    }

    override fun onStart() {
        super.onStart()
        initPlayer()
    }

    override fun onStop() {
        super.onStop()
        player?.release()
        player = null
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("index", index)
    }

    private fun initPlayer() {
        if (Playlist.current.isEmpty() || player != null) return
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)
        val sourceFactory = DefaultMediaSourceFactory(DefaultDataSource.Factory(this, http))

        player = ExoPlayer.Builder(this).setMediaSourceFactory(sourceFactory).build().also { p ->
            playerView.player = p
            p.playWhenReady = true
            p.addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                        p.seekToDefaultPosition()
                        p.prepare()
                    } else {
                        Toast.makeText(
                            this@PlayerActivity,
                            "Não foi possível reproduzir este canal (${error.errorCodeName})",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            })
        }
        playChannel(index, showName = false)
    }

    private fun playChannel(i: Int, showName: Boolean = true) {
        val list = Playlist.current
        if (list.isEmpty()) return
        index = ((i % list.size) + list.size) % list.size
        val ch = list[index]
        val builder = MediaItem.Builder()
            .setUri(ch.url)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(ch.name).build())
        if (ch.url.contains(".m3u8", ignoreCase = true)) builder.setMimeType(MimeTypes.APPLICATION_M3U8)
        player?.apply {
            setMediaItem(builder.build())
            prepare()
        }
        if (showName) Toast.makeText(this, ch.name, Toast.LENGTH_SHORT).show()
    }

    /** Controle remoto: cima/baixo e CH+/CH- trocam de canal quando os controles estão ocultos. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && !playerView.isControllerFullyVisible) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_DPAD_UP -> {
                    playChannel(index - 1); return true
                }
                KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_DPAD_DOWN -> {
                    playChannel(index + 1); return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun hideSystemUi() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, playerView).let {
            it.hide(WindowInsetsCompat.Type.systemBars())
            it.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
