package com.rhythm.music

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat

class PlaybackService : Service() {

    companion object {
        const val PLAY = "com.rhythm.music.PLAY"
        const val TOGGLE = "com.rhythm.music.TOGGLE"
        const val NEXT = "com.rhythm.music.NEXT"
        const val PREVIOUS = "com.rhythm.music.PREVIOUS"
        const val SEEK = "com.rhythm.music.SEEK"
        const val STATE = "com.rhythm.music.STATE"

        const val CHANNEL = "rhythm_music"
        const val NOTIFICATION_ID = 101
    }

    private var player: MediaPlayer? = null

    private var urls = arrayListOf<String>()
    private var names = arrayListOf<String>()
    private var artists = arrayListOf<String>()

    private var index = 0
    private var title = "Nothing playing"
    private var artist = "Unknown artist"

    private val handler = Handler(Looper.getMainLooper())

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
                val currentPlayer = player

                if (currentPlayer != null) {
                    try {
                        if (currentPlayer.isPlaying) {
                            currentPlayer.pause()
                        } else {
                            currentPlayer.start()
                        }
                        publish()
                    } catch (_: Exception) {
                        publish()
                    }
                }
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
                val currentPlayer = player

                if (currentPlayer != null) {
                    try {
                        val progress = intent.getIntExtra(
                            "position",
                            0
                        ).coerceIn(0, 1000)

                        val duration = currentPlayer.duration

                        val targetPosition =
                            (duration.toLong() * progress / 1000)
                                .toInt()

                        currentPlayer.seekTo(targetPosition)
                        publish()

                    } catch (_: Exception) {
                    }
                }
            }
        }

        return START_NOT_STICKY
    }

    private fun playCurrent() {
        if (urls.isEmpty() || index !in urls.indices) {
            publish()
            return
        }

        title = names.getOrNull(index) ?: "Unknown track"
        artist = artists.getOrNull(index) ?: "Unknown artist"

        handler.removeCallbacksAndMessages(null)

        try {
            player?.release()
            player = null

            startForeground(
                NOTIFICATION_ID,
                notification()
            )

            val newPlayer = MediaPlayer()
            player = newPlayer

            newPlayer.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(
                        AudioAttributes.CONTENT_TYPE_MUSIC
                    )
                    .build()
            )

            newPlayer.setOnPreparedListener { preparedPlayer ->
                try {
                    preparedPlayer.start()
                    publish()
                    startUpdates()
                } catch (_: Exception) {
                    publish()
                }
            }

            newPlayer.setOnCompletionListener {
                if (index < urls.lastIndex) {
                    index++
                    playCurrent()
                } else {
                    publish()
                }
            }

            newPlayer.setOnErrorListener { _, _, _ ->
                publish()
                true
            }

            newPlayer.setDataSource(urls[index])
            newPlayer.prepareAsync()

        } catch (_: Exception) {
            player?.release()
            player = null
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
        val currentPlayer = player

        val isCurrentlyPlaying = try {
            currentPlayer?.isPlaying == true
        } catch (_: Exception) {
            false
        }

        val duration = try {
            currentPlayer?.duration ?: 0
        } catch (_: Exception) {
            0
        }

        val position = try {
            currentPlayer?.currentPosition ?: 0
        } catch (_: Exception) {
            0
        }

        sendBroadcast(
            Intent(STATE).setPackage(packageName).apply {
                putExtra("title", title)
                putExtra("artist", artist)
                putExtra("playing", isCurrentlyPlaying)
                putExtra("duration", duration)
                putExtra("position", position)
            }
        )

        val manager = getSystemService(
            NOTIFICATION_SERVICE
        ) as NotificationManager

        manager.notify(
            NOTIFICATION_ID,
            notification()
        )
    }

    private fun notification(): Notification {

        val openIntent = Intent(
            this,
            MainActivity::class.java
        )

        val openPendingIntent = PendingIntent.getActivity(
            this,
            1,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE
        )

        fun createAction(
            label: String,
            command: String,
            requestCode: Int
        ): NotificationCompat.Action {

            val icon = when (command) {
                PREVIOUS -> android.R.drawable.ic_media_previous
                NEXT -> android.R.drawable.ic_media_next
                else -> if (player?.isPlaying == true) {
                    android.R.drawable.ic_media_pause
                } else {
                    android.R.drawable.ic_media_play
                }
            }

            val serviceIntent = Intent(
                this,
                PlaybackService::class.java
            ).setAction(command)

            val pendingIntent = PendingIntent.getService(
                this,
                requestCode,
                serviceIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
            )

            return NotificationCompat.Action(
                icon,
                label,
                pendingIntent
            )
        }

        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(artist)
            .setContentIntent(openPendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(player?.isPlaying == true)
            .addAction(
                createAction("Previous", PREVIOUS, 2)
            )
            .addAction(
                createAction("Play/Pause", TOGGLE, 3)
            )
            .addAction(
                createAction("Next", NEXT, 4)
            )
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val channel = NotificationChannel(
                CHANNEL,
                "Rhythm Music",
                NotificationManager.IMPORTANCE_LOW
            )

            val manager = getSystemService(
                NotificationManager::class.java
            )

            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)

        player?.release()
        player = null

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
