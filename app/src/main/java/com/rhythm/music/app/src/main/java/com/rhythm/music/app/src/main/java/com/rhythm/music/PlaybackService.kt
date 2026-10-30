package com.rhythm.music

import android.app.*
import android.content.*
import android.media.*
import android.net.Uri
import android.os.*
import androidx.core.app.NotificationCompat

class PlaybackService : Service() {

    companion object {
        const val PLAY = "RHYTHM_PLAY"
        const val TOGGLE = "RHYTHM_TOGGLE"
        const val NEXT = "RHYTHM_NEXT"
        const val PREVIOUS = "RHYTHM_PREVIOUS"
        const val SEEK = "RHYTHM_SEEK"
        const val STATE = "RHYTHM_STATE"

        const val CHANNEL = "rhythm_music"
        const val NOTIFICATION_ID = 101
    }

    private var player: MediaPlayer? = null
    private var urls = arrayListOf<String>()
    private var names = arrayListOf<String>()
    private var artists = arrayListOf<String>()
    private var index = 0

    private val handler = Handler(Looper.getMainLooper())

    private var title = "Nothing playing"
    private var artist = "Unknown artist"

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        when (intent?.action) {

            PLAY -> {
                urls = intent.getStringArrayListExtra("urls")
                    ?: arrayListOf()

                names = intent.getStringArrayListExtra("names")
                    ?: arrayListOf()

                artists = intent.getStringArrayListExtra("artists")
                    ?: arrayListOf()

                index = intent.getIntExtra("index", 0)

                playCurrent()
            }

            TOGGLE -> {
                val p = player ?: return START_NOT_STICKY

                if (p.isPlaying) {
                    p.pause()
                } else {
                    p.start()
                }

                publish()
            }

            NEXT -> {
                if (index < urls.lastIndex) {
                    index++
                    playCurrent()
                }
            }

            PREVIOUS -> {
                if (index > 0) {
                    index--
                    playCurrent()
                }
            }

            SEEK -> {
                val p = player
                if (p != null && p.isPlaying || p != null) {
                    val position = intent.getIntExtra("position", 0)
                    p.seekTo(position.coerceIn(0, p.duration))
                }
            }
        }

        return START_NOT_STICKY
    }

    private fun playCurrent() {
        if (urls.isEmpty() || index !in urls.indices) return

        title = names.getOrNull(index) ?: "Unknown track"
        artist = artists.getOrNull(index) ?: "Unknown artist"

        player?.release()

        try {
            player = MediaPlayer().apply {

                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(
                            AudioAttributes.CONTENT_TYPE_MUSIC
                        )
                        .build()
                )

                setDataSource(
                    this@PlaybackService,
                    Uri.parse(urls[index])
                )

                setOnPreparedListener {
                    it.start()
                    startForeground(
                        NOTIFICATION_ID,
                        notification()
                    )
                    publish()
                    startUpdates()
                }

                setOnCompletionListener {
                    if (index < urls.lastIndex) {
                        index++
                        playCurrent()
                    } else {
                        publish()
                    }
                }

                setOnErrorListener { _, _, _ ->
                    publish()
                    true
                }

                prepareAsync()
            }

            startForeground(
                NOTIFICATION_ID,
                notification()
            )

        } catch (_: Exception) {
            publish()
        }
    }

    private fun startUpdates() {
        handler.removeCallbacksAndMessages(null)

        handler.post(object : Runnable {
            override fun run() {
                publish()
                if (player != null) {
                    handler.postDelayed(this, 1000)
                }
            }
        })
    }

    private fun publish() {
        val p = player

        sendBroadcast(
            Intent(STATE).setPackage(packageName).apply {
                putExtra("title", title)
                putExtra("artist", artist)
                putExtra("playing", p?.isPlaying == true)
                putExtra("duration", p?.duration ?: 0)
                putExtra("position", p?.currentPosition ?: 0)
            }
        )

        val manager =
            getSystemService(NOTIFICATION_SERVICE)
                as NotificationManager

        manager.notify(NOTIFICATION_ID, notification())
    }

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE
        )

        fun action(
            label: String,
            command: String,
            request: Int
        ): NotificationCompat.Action {

            val pending = PendingIntent.getService(
                this,
                request,
                Intent(this, PlaybackService::class.java)
                    .setAction(command),
                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
            )

            val icon = when (command) {
                PREVIOUS -> android.R.drawable.ic_media_previous
                NEXT -> android.R.drawable.ic_media_next
                else -> if (player?.isPlaying == true)
                    android.R.drawable.ic_media_pause
                else android.R.drawable.ic_media_play
            }

            return NotificationCompat.Action(
                icon,
                label,
                pending
            )
        }

        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(artist)
            .setContentIntent(open)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .addAction(action("Previous", PREVIOUS, 2))
            .addAction(action("Play/Pause", TOGGLE, 3))
            .addAction(action("Next", NEXT, 4))
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL,
                "Rhythm Music",
                NotificationManager.IMPORTANCE_LOW
            )

            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        player?.release()
        player = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
