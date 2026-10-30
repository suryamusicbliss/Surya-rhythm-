
package com.rhythm.music

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray
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
        getSharedPreferences("rhythm_data", MODE_PRIVATE)
    }

    private val tracks = mutableListOf<Track>()
    private val favourites = mutableSetOf<String>()

    private var currentIndex = -1
    private var currentTitle = "Choose a song"
    private var currentArtist = "Rhythm Music"
    private var isPlaying = false

    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var miniTitle: TextView
    private lateinit var miniPlay: TextView
    private lateinit var searchInput: EditText
    private lateinit var receiver: BroadcastReceiver

    companion object {
        private const val ACTION_PLAY = "com.rhythm.music.PLAY"
        private const val ACTION_TOGGLE = "com.rhythm.music.TOGGLE"
        private const val ACTION_NEXT = "com.rhythm.music.NEXT"
        private const val ACTION_PREVIOUS = "com.rhythm.music.PREVIOUS"
        private const val ACTION_SEEK = "com.rhythm.music.SEEK"
        private const val STATE_ACTION = "com.rhythm.music.STATE"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        loadFavourites()
        requestNotificationPermission()
        setupReceiver()
        buildHome()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
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
        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action != STATE_ACTION) return

                currentTitle = intent.getStringExtra("title") ?: currentTitle
                currentArtist = intent.getStringExtra("artist") ?: currentArtist
                isPlaying = intent.getBooleanExtra("playing", false)

                if (::miniTitle.isInitialized) {
                    miniTitle.text = "$currentTitle • $currentArtist"
                    miniPlay.text = if (isPlaying) "Ⅱ" else "▶"
                }
            }
        }

        val filter = IntentFilter(STATE_ACTION)

        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(receiver, filter)
        }
    }

    private fun buildHome() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(20, 20, 20, 12)
        }

        val scroll = ScrollView(this)

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        scroll.addView(content)

        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        addMiniPlayer()
        setContentView(root)
        showHome()
    }

    private fun addMiniPlayer() {
        val mini = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(12, 12, 12, 12)
            setBackgroundColor(panel)
        }

        miniTitle = TextView(this).apply {
            text = "Choose a song"
            setTextColor(white)
            textSize = 13f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }

        miniPlay = TextView(this).apply {
            text = "▶"
            textSize = 23f
            setTextColor(platinum)
            gravity = Gravity.CENTER
            setPadding(18, 4, 8, 4)
            setOnClickListener {
                sendAction(ACTION_TOGGLE)
            }
            setOnLongClickListener {
                showHint("Play / Pause")
                true
            }
        }

        mini.addView(
            miniTitle,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        mini.addView(miniPlay)

        mini.setOnClickListener {
            openPlayer()
        }

        root.addView(
            mini,
            LinearLayout.LayoutParams(-1, -2)
        )
    }

    private fun clearContent() {
        content.removeAllViews()
    }

    private fun heading(text: String) {
        val view = TextView(this).apply {
            this.text = text
            textSize = 25f
            setTextColor(platinum)
            setPadding(0, 18, 0, 14)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        content.addView(view)
    }

    private fun addText(
        text: String,
        size: Float = 15f,
        color: Int = white
    ): TextView {
        val view = TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            setPadding(4, 8, 4, 8)
        }

        content.addView(view)
        return view
    }

    private fun makeButton(
        text: String,
        action: () -> Unit
    ): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 15f
            setTextColor(platinum)
            gravity = Gravity.CENTER
            setPadding(14, 13, 14, 13)
            setBackgroundColor(panel)

            setOnClickListener {
                action()
            }

            setOnLongClickListener {
                showHint(text)
                true
            }
        }
    }

    private fun showHome() {
        clearContent()

        addText("R H Y T H M", 14f, platinum)
        heading("An experience\nbeyond listening.")
        addText("FEEL EVERY BEAT", 12f, muted)
        addText("Every beat.\nA new feeling.", 23f, white)

        searchInput = EditText(this).apply {
            hint = "Search songs or artists"
            setHintTextColor(muted)
            setTextColor(white)
            textSize = 15f
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT
            setPadding(14, 12, 14, 12)
            setBackgroundColor(panel)
        }

        content.addView(
            searchInput,
            LinearLayout.LayoutParams(-1, -2)
        )

        content.addView(makeButton("⌕  SEARCH") {
            searchSongs(searchInput.text.toString().trim())
        })

        heading("Explore")

        content.addView(makeButton("♫  All Music") {
            showAllSongs()
        })

        content.addView(makeButton("♡  Favourites") {
            showFavourites()
        })

        content.addView(makeButton("▤  Playlists") {
            showPlaylists()
        })

        addText(
            "Developed by Shubham Upadhyay",
            12f,
            muted
        )
    }

    private fun searchSongs(term: String) {
        if (term.isBlank()) {
            Toast.makeText(
                this,
                "Enter a song or artist name",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        clearContent()
        heading("Searching…")

        thread {
            var connection: HttpURLConnection? = null

            try {
                val query = URLEncoder.encode(term, "UTF-8")

                val address =
                    "https://api.jamendo.com/v3.0/tracks/" +
                    "?client_id=$clientId&format=json&limit=25&search=$query"

                connection =
                    URL(address).openConnection() as HttpURLConnection

                connection.connectTimeout = 15000
                connection.readTimeout = 15000

                val response = connection.inputStream.bufferedReader().use {
                    it.readText()
                }

                val json = JSONObject(response)
                val data = json.getJSONArray("results")
                val found = mutableListOf<Track>()

                for (i in 0 until data.length()) {
                    val item = data.getJSONObject(i)
                    val audio = item.optString("audio")

                    if (audio.isNotBlank() && audio != "null") {
                        found.add(
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

                runOnUiThread {
                    tracks.clear()
                    tracks.addAll(found)
                    showAllSongs()

                    if (found.isEmpty()) {
                        Toast.makeText(
                            this,
                            "No songs found",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(
                        this,
                        "Search failed: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    showHome()
                }

            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun showAllSongs() {
        clearContent()
        heading("Discover Music")

        if (tracks.isEmpty()) {
            addText(
                "Search for a song to discover music.",
                14f,
                muted
            )
            return
        }

        tracks.forEachIndexed { index, track ->
            addTrackRow(track, index)
        }
    }

    private fun addTrackRow(track: Track, index: Int) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(10, 10, 10, 10)
            setBackgroundColor(panel)
        }

        val details = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        details.addView(TextView(this).apply {
            text = track.name
            textSize = 15f
            setTextColor(white)
        })

        details.addView(TextView(this).apply {
            text = track.artist
            textSize = 12f
            setTextColor(muted)
        })

        val play = TextView(this).apply {
            text = "▶"
            textSize = 22f
            setTextColor(platinum)
            setPadding(14, 8, 14, 8)

            setOnClickListener {
                playTrack(index)
            }

            setOnLongClickListener {
                showHint("Play")
                true
            }
        }

        val like = TextView(this).apply {
            text = if (favourites.contains(track.audioUrl)) "♥" else "♡"
            textSize = 22f
            setTextColor(platinum)
            setPadding(10, 8, 10, 8)

            setOnClickListener {
                toggleFavourite(track)
                text = if (favourites.contains(track.audioUrl)) "♥" else "♡"
            }

            setOnLongClickListener {
                showHint("Favourite")
                true
            }
        }

        row.addView(
            details,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        row.addView(like)
        row.addView(play)

        row.setOnClickListener {
            playTrack(index)
        }

        val params = LinearLayout.LayoutParams(-1, -2)
        params.setMargins(0, 5, 0, 5)

        content.addView(row, params)
    }

    private fun playTrack(index: Int) {
        if (index !in tracks.indices) return

        currentIndex = index
        val selected = tracks[index]

        val intent = Intent(this, PlaybackService::class.java).apply {
            action = ACTION_PLAY

            putStringArrayListExtra(
                "urls",
                ArrayList(tracks.map { it.audioUrl })
            )

            putStringArrayListExtra(
                "names",
                ArrayList(tracks.map { it.name })
            )

            putStringArrayListExtra(
                "artists",
                ArrayList(tracks.map { it.artist })
            )

            putExtra("index", index)
        }

        try {
            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }

            currentTitle = selected.name
            currentArtist = selected.artist

            miniTitle.text = "$currentTitle • $currentArtist"

            Toast.makeText(
                this,
                "Playing ${selected.name}",
                Toast.LENGTH_SHORT
            ).show()

        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Unable to play song: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun sendAction(action: String, position: Int? = null) {
        val intent = Intent(this, PlaybackService::class.java).apply {
            this.action = action
            position?.let {
                putExtra("position", it)
            }
        }

        try {
            startService(intent)
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Player service is not running",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun openPlayer() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(24, 24, 24, 24)
            setBackgroundColor(bg)
        }

        val title = TextView(this).apply {
            text = currentTitle
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(white)
        }

        val artist = TextView(this).apply {
            text = currentArtist
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(muted)
        }

        val controls = LinearLayout(this).apply {
            gravity = Gravity.CENTER
        }

        fun control(
            symbol: String,
            hint: String,
            action: () -> Unit
        ): TextView {
            return TextView(this).apply {
                text = symbol
                textSize = 25f
                setTextColor(platinum)
                setPadding(16, 16, 16, 16)

                setOnClickListener {
                    action()
                }

                setOnLongClickListener {
                    showHint(hint)
                    true
                }
            }
        }

        controls.addView(control("|◀", "Previous") {
            sendAction(ACTION_PREVIOUS)
        })

        controls.addView(control("▶ / Ⅱ", "Play / Pause") {
            sendAction(ACTION_TOGGLE)
        })

        controls.addView(control("▶|", "Next") {
            sendAction(ACTION_NEXT)
        })

        val seek = SeekBar(this)
        seek.max = 1000

        seek.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    bar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {}

                override fun onStartTrackingTouch(bar: SeekBar?) {}

                override fun onStopTrackingTouch(bar: SeekBar?) {
                    sendAction(
                        ACTION_SEEK,
                        bar?.progress ?: 0
                    )
                }
            }
        )

        box.addView(title)
        box.addView(artist)
        box.addView(controls)
        box.addView(seek)

        val dialog = android.app.AlertDialog.Builder(this)
            .setView(box)
            .setNegativeButton("Close", null)
            .create()

        dialog.show()
    }

    private fun toggleFavourite(track: Track) {
        if (favourites.contains(track.audioUrl)) {
            favourites.remove(track.audioUrl)
        } else {
            favourites.add(track.audioUrl)
        }

        saveFavourites()
    }

    private fun loadFavourites() {
        val array = JSONArray(
            prefs.getString("favourites", "[]")
        )

        for (i in 0 until array.length()) {
            favourites.add(array.optString(i))
        }
    }

    private fun saveFavourites() {
        val array = JSONArray()

        favourites.forEach {
            array.put(it)
        }

        prefs.edit()
            .putString("favourites", array.toString())
            .apply()
    }

    private fun showFavourites() {
        clearContent()
        heading("Your Favourites")

        val likedTracks = tracks.filter {
            favourites.contains(it.audioUrl)
        }

        if (likedTracks.isEmpty()) {
            addText(
                "No favourites in the current search list.",
                14f,
                muted
            )
            return
        }

        likedTracks.forEach { track ->
            val index = tracks.indexOf(track)
            addTrackRow(track, index)
        }
    }

    private fun showPlaylists() {
        clearContent()
        heading("Your Playlists")

        content.addView(makeButton("+  Create Playlist") {
            val input = EditText(this)
            input.hint = "Playlist name"

            android.app.AlertDialog.Builder(this)
                .setTitle("Create Playlist")
                .setView(input)
                .setPositiveButton("Create") { _, _ ->
                    val name = input.text.toString().trim()

                    if (name.isNotEmpty()) {
                        val list = getPlaylistNames().toMutableSet()
                        list.add(name)
                        savePlaylistNames(list)
                        showPlaylists()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        })

        getPlaylistNames().forEach { name ->
            content.addView(makeButton("♫  $name") {
                Toast.makeText(
                    this,
                    "Playlist created: $name. Add-song management is not implemented yet.",
                    Toast.LENGTH_LONG
                ).show()
            })
        }
    }

    private fun getPlaylistNames(): Set<String> {
   
