package com.rhythm.music

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private val bg = Color.rgb(10, 13, 19)
    private val panel = Color.rgb(27, 34, 45)
    private val platinum = Color.rgb(184, 199, 217)
    private val muted = Color.rgb(157, 169, 184)
    private val white = Color.WHITE

    private val clientId = "1c57991d"

    private val prefs by lazy {
        getSharedPreferences("rhythm_data", Context.MODE_PRIVATE)
    }

    private val tracks = mutableListOf<Track>()
    private val favourites = mutableSetOf<String>()

    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var miniPlayer: TextView

    private var currentIndex = -1
    private var currentTitle = "Nothing playing"
    private var currentArtist = ""
    private var isPlaying = false
    private var receiverRegistered = false

    companion object {
        const val ACTION_PLAY = "com.rhythm.music.PLAY"
        const val ACTION_TOGGLE = "com.rhythm.music.TOGGLE"
        const val ACTION_NEXT = "com.rhythm.music.NEXT"
        const val ACTION_PREVIOUS = "com.rhythm.music.PREVIOUS"
        const val ACTION_SEEK = "com.rhythm.music.SEEK"
        const val ACTION_STATE = "com.rhythm.music.STATE"
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ACTION_STATE) return

            currentTitle = intent.getStringExtra("title") ?: currentTitle
            currentArtist = intent.getStringExtra("artist") ?: currentArtist
            isPlaying = intent.getBooleanExtra("playing", isPlaying)

            updateMiniPlayer()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        favourites.addAll(
            prefs.getStringSet("favourites", emptySet()) ?: emptySet()
        )

        requestNotificationPermission()
        setupReceiver()
        buildHome()
    }

    private fun requestNotificationPermission() {
        if (
            Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                100
            )
        }
    }

    private fun setupReceiver() {
        val filter = IntentFilter(ACTION_STATE)

        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(receiver, filter)
        }

        receiverRegistered = true
    }

    override fun onDestroy() {
        if (receiverRegistered) {
            unregisterReceiver(receiver)
            receiverRegistered = false
        }
        super.onDestroy()
    }

    private fun showHint(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun buildHome() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(20, 18, 20, 12)
        }

        val header = TextView(this).apply {
            text = "RHYTHM"
            textSize = 30f
            setTextColor(platinum)
            gravity = Gravity.CENTER
            letterSpacing = 0.18f
        }

        root.addView(
            header,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                55
            )
        )

        val tagline = TextView(this).apply {
            text = "FEEL EVERY BEAT"
            textSize = 11f
            setTextColor(muted)
            gravity = Gravity.CENTER
            letterSpacing = 0.25f
        }

        root.addView(
            tagline,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                25
            )
        )

        val searchBox = EditText(this).apply {
            hint = "Search songs or artists..."
            setHintTextColor(muted)
            setTextColor(white)
            textSize = 15f
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT
            setPadding(16, 10, 16, 10)
            setBackgroundColor(panel)
        }

        root.addView(
            searchBox,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                52
            ).apply {
                topMargin = 18
                bottomMargin = 10
            }
        )

        val searchButton = makeButton("SEARCH")
        root.addView(searchButton)

        searchButton.setOnClickListener {
            val query = searchBox.text.toString().trim()
            if (query.isNotEmpty()) {
                searchSongs(query)
            } else {
                showHint("Enter a song or artist name")
            }
        }

        val navigation = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val allButton = makeButton("ALL MUSIC")
        val favouriteButton = makeButton("FAVOURITES")
        val playlistButton = makeButton("PLAYLISTS")

        navigation.addView(allButton, equalWeight())
        navigation.addView(favouriteButton, equalWeight())
        navigation.addView(playlistButton, equalWeight())

        root.addView(
            navigation,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                55
            ).apply {
                topMargin = 12
            }
        )

        statusText = TextView(this).apply {
            text = "Search for music to get started"
            textSize = 13f
            setTextColor(muted)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(4, 10, 4, 10)
        }

        root.addView(statusText)

        val scrollView = ScrollView(this)

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        scrollView.addView(content)

        root.addView(
            scrollView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        miniPlayer = TextView(this).apply {
            text = "Nothing playing"
            textSize = 13f
            setTextColor(platinum)
            setPadding(12, 14, 12, 14)
            setBackgroundColor(panel)
            gravity = Gravity.CENTER_VERTICAL
        }

        root.addView(
            miniPlayer,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                55
            ).apply {
                topMargin = 8
            }
        )

        miniPlayer.setOnClickListener {
            openPlayer()
        }

        val credit = TextView(this).apply {
            text = "Developed by Shubham Upadhyay"
            textSize = 10f
            setTextColor(muted)
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 0)
        }

        root.addView(credit)

        setContentView(root)

        allButton.setOnClickListener {
            showAllTracks()
        }

        favouriteButton.setOnClickListener {
            showFavourites()
        }

        playlistButton.setOnClickListener {
            showPlaylists()
        }

        updateMiniPlayer()
    }

    private fun equalWeight(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.MATCH_PARENT,
            1f
        ).apply {
            marginEnd = 4
        }
    }

    private fun makeButton(label: String): Button {
        return Button(this).apply {
            text = label
            textSize = 10f
            setTextColor(bg)
            setBackgroundColor(platinum)
            isAllCaps = false
        }
    }

    private fun searchSongs(query: String) {
        statusText.text = "Searching..."

        thread {
            var connection: HttpURLConnection? = null

            try {
                val encodedQuery = URLEncoder.encode(query, "UTF-8")

                val apiUrl =
                    "https://api.jamendo.com/v3.0/tracks/" +
                    "?client_id=$clientId" +
                    "&format=json" +
                    "&limit=30" +
                    "&namesearch=$encodedQuery" +
                    "&audioformat=mp31"

                connection = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15000
                    readTimeout = 15000
                }

                val response = connection.inputStream.bufferedReader().use {
                    it.readText()
                }

                val json = JSONObject(response)
                val results = json.optJSONArray("results")
                val foundTracks = mutableListOf<Track>()

                if (results != null) {
                    for (i in 0 until results.length()) {
                        val item = results.optJSONObject(i) ?: continue
                        val audio = item.optString("audio", "")

                        if (audio.isNotBlank() && audio != "null") {
                            foundTracks.add(
                                Track(
                                    id = item.optString("id"),
                                    name = item.optString("name", "Unknown"),
                                    artist = item.optString(
                                        "artist_name",
                                        "Unknown artist"
                                    ),
                                    audioUrl = audio
                                )
                            )
                        }
                    }
                }

                runOnUiThread {
                    tracks.clear()
                    tracks.addAll(foundTracks)
                    showAllTracks()

                    statusText.text =
                        if (tracks.isEmpty()) {
                            "No songs found. Try another search."
                        } else {
                            "${tracks.size} songs found"
                        }
                }

            } catch (e: Exception) {
                runOnUiThread {
                    statusText.text = "Search failed. Check your internet."
                    showHint(
                        "Unable to load songs: ${e.message ?: "Network error"}"
                    )
                }
            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun showAllTracks() {
        content.removeAllViews()

        if (tracks.isEmpty()) {
            addMessage("No songs loaded. Search for a song first.")
            return
        }

        tracks.forEachIndexed { index, track ->
            addTrackRow(track, index)
        }
    }

    private fun showFavourites() {
        content.removeAllViews()

        val favouriteTracks = tracks.filter {
            favourites.contains(it.audioUrl)
        }

        if (favouriteTracks.isEmpty()) {
            addMessage("No favourites found in the current search results.")
            return
        }

        favouriteTracks.forEach { track ->
            val index = tracks.indexOf(track)
            if (index >= 0) {
                addTrackRow(track, index)
            }
        }
    }

    private fun addTrackRow(track: Track, index: Int) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(12, 10, 8, 10)
            setBackgroundColor(panel)
        }

        val details = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val title = TextView(this).apply {
            text = track.name
            textSize = 15f
            setTextColor(white)
            maxLines = 1
        }

        val artist = TextView(this).apply {
            text = track.artist
            textSize = 12f
            setTextColor(muted)
            maxLines = 1
        }

        details.addView(title)
        details.addView(artist)

        row.addView(
            details,
            LinearLayout.LayoutParams(0, 48, 1f)
        )

        val favouriteButton = Button(this).apply {
            text = if (favourites.contains(track.audioUrl)) "★" else "☆"
            textSize = 20f
            setTextColor(platinum)
            setBackgroundColor(panel)
            minWidth = 48
        }

        row.addView(favouriteButton)

        favouriteButton.setOnClickListener {
            if (favourites.contains(track.audioUrl)) {
                favourites.remove(track.audioUrl)
                favouriteButton.text = "☆"
                showHint("Removed from favourites")
            } else {
                favourites.add(track.audioUrl)
                favouriteButton.text = "★"
                showHint("Added to favourites")
            }

            prefs.edit()
                .putStringSet("favourites", favourites.toSet())
                .apply()
        }

        row.setOnClickListener {
            playTrack(index)
        }

        content.addView(
            row,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 8
            }
        )
    }

    private fun addMessage(message: String) {
        val text = TextView(this).apply {
            this.text = message
            textSize = 14f
            setTextColor(muted)
            gravity = Gravity.CENTER
            setPadding(16, 35, 16, 35)
        }

        content.addView(text)
    }

    private fun playTrack(index: Int) {
        if (index !in tracks.indices) {
            showHint("Song not available")
            return
        }

        currentIndex = index

        val urls = ArrayList<String>()
        val names = ArrayList<String>()
        val artists = ArrayList<String>()

        tracks.forEach {
            urls.add(it.audioUrl)
            names.add(it.name)
            artists.add(it.artist)
        }

        val intent = Intent(this, PlaybackService::class.java).apply {
            action = ACTION_PLAY
            putStringArrayListExtra("urls", urls)
            putStringArrayListExtra("names", names)
            putStringArrayListExtra("artists", artists)
            putExtra("index", index)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(this, intent)
            } else {
                startService(intent)
            }

            currentTitle = tracks[index].name
            currentArtist = tracks[index].artist
            isPlaying = true
            updateMiniPlayer()
            showHint("Playing ${tracks[index].name}")

        } catch (e: Exception) {
            showHint("Unable to start player: ${e.message ?: "Error"}")
        }
    }

    private fun sendAction(action: String, position: Int? = null) {
        val intent = Intent(this, PlaybackService::class.java).apply {
            this.action = action
            if (position != null) {
                putExtra("position", position)
            }
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(this, intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            showHint("Player action failed")
        }
    }

    private fun openPlayer() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 20, 24, 12)
            setBackgroundColor(bg)
        }

        val title = TextView(this).apply {
            text = currentTitle
            textSize = 19f
            setTextColor(platinum)
            gravity = Gravity.CENTER
        }

        val artist = TextView(this).apply {
            text = currentArtist
            textSize = 14f
            setTextColor(muted)
            gravity = Gravity.CENTER
        }

        layout.addView(title)
        layout.addView(artist)

        val seekBar = SeekBar(this).apply {
            max = 1000
            progress = 0
        }

        layout.addView(seekBar)

        seekBar.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                }

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    val progress = seekBar?.progress ?: return
                    sendAction(ACTION_SEEK, progress)
                }
            }
        )

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val previous = makeButton("PREVIOUS")
        val playPause = makeButton(if (isPlaying) "PAUSE" else "PLAY")
        val next = makeButton("NEXT")

        controls.addView(previous, equalWeight())
        controls.addView(playPause, equalWeight())
        controls.addView(next, equalWeight())

        layout.addView(
            controls,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                55
            )
        )

        val playerDialog = AlertDialog.Builder(this)
            .setTitle("RHYTHM PLAYER")
            .setView(layout)
            .setNegativeButton("CLOSE", null)
            .create()

        previous.setOnClickListener {
            sendAction(ACTION_PREVIOUS)
        }

        playPause.setOnClickListener {
            sendAction(ACTION_TOGGLE)
            isPlaying = !isPlaying
            playPause.text = if (isPlaying) "PAUSE" else "PLAY"
            updateMiniPlayer()
        }

        next.setOnClickListener {
            sendAction(ACTION_NEXT)
        }

        playerDialog.show()
    }

    private fun updateMiniPlayer() {
        if (!::miniPlayer.isInitialized) return

        val playStatus = if (isPlaying) "▶" else "Ⅱ"

        miniPlayer.text =
            "$playStatus  $currentTitle  •  $currentArtist"
    }

    private fun getPlaylistNames(): Set<String> {
        return prefs.get
