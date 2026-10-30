package com.rhythm.music

import android.app.Activity
import android.app.Dialog
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.widget.*
import android.text.InputType
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.concurrent.thread

class MainActivity : Activity() {

    private val bg = Color.rgb(10, 13, 19)
private val panel = Color.rgb(27, 34, 45)
private val gold = Color.rgb(184, 199, 217)
private val muted = Color.rgb(157, 169, 184)

    private lateinit var results: LinearLayout
    private lateinit var searchInput: EditText
    private lateinit var miniTitle: TextView
    private var player: MediaPlayer? = null
    private var currentSongName = "Nothing playing"
    private var currentSongArtist = "Unknown artist"
    private var isLiked: Boolean = false
    private lateinit var playerTitle: TextView
private lateinit var playerArtist: TextView
private lateinit var seekBar: SeekBar
private lateinit var durationText: TextView
private lateinit var likeButton: TextView
private var isPlayerVisible = false

    private fun dp(v: Int): Int =
        (v * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, radius: Int = 18): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }
    }

    private fun label(
        value: String,
        size: Float,
        color: Int,
        bold: Boolean = false
    ): TextView {
        return TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(null, Typeface.BOLD)
        }
    }

    private fun addSpace(parent: LinearLayout, height: Int) {
        parent.addView(View(this), LinearLayout.LayoutParams(1, dp(height)))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = bg
        window.navigationBarColor = bg

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }

        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(22), dp(22), dp(120))
        }

        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val brand = label("RHYTHM", 27f, gold, true)
        header.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(label("●  PREMIUM", 11f, gold, true))
        content.addView(header)

        addSpace(content, 8)
        content.addView(label("FEEL EVERY BEAT", 10f, muted, true))
        addSpace(content, 28)

        val greeting = label(
    "An experience\nbeyond listening.",
    32f,
    Color.WHITE,
    true
).apply {
    letterSpacing = -0.02f
    setLineSpacing(dp(5).toFloat(), 1f)
}
content.addView(greeting)

addSpace(content, 10)

content.addView(
    label(
        "Discover sound. Experience emotion.",
        14f,
        muted
    )
)

        addSpace(content, 18)

        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(22))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf( Color.rgb(42, 57, 78),Color.rgb(10, 13, 19)
)
            ).apply { cornerRadius = dp(24).toFloat() }
        }

        hero.addView(label("✦  CURATED FOR YOUR SENSES", 11f, gold, true))
addSpace(hero, 18)

hero.addView(
    label(
        "Every beat.\nA new feeling.",
        28f,
        Color.WHITE,
        true
    )
)

addSpace(hero, 10)

hero.addView(
    label(
        "A more personal way to experience music.",
        13f,
        Color.rgb(205, 214, 225)
    )
)
        addSpace(hero, 18)

        val explore = Button(this).apply {
            text = "EXPLORE MUSIC"
            setTextColor(Color.BLACK)
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            background = rounded(gold, 24)
            setOnClickListener {
                scroll.smoothScrollTo(0, searchInput.top)
                searchInput.requestFocus()
            }
        }
        hero.addView(explore, LinearLayout.LayoutParams(-2, dp(46)))
        content.addView(hero)

        addSpace(content, 28)
        content.addView(label("DISCOVER", 12f, gold, true))
        addSpace(content, 12)
        content.addView(label("Find your vibe", 23f, Color.WHITE, true))
        addSpace(content, 14)

        searchInput = EditText(this).apply {
            hint = "Search songs or artists"
            setTextColor(Color.WHITE)
            setHintTextColor(muted)
            textSize = 15f
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            inputType = InputType.TYPE_CLASS_TEXT
            setPadding(dp(16), 0, dp(16), 0)
            background = rounded(panel, 16)
        }
        content.addView(searchInput, LinearLayout.LayoutParams(-1, dp(54)))

        addSpace(content, 10)

        val searchButton = Button(this).apply {
            text = "SEARCH MUSIC"
            setTextColor(Color.BLACK)
            setTypeface(null, Typeface.BOLD)
            background = rounded(gold, 16)
            setOnClickListener { searchMusic() }
        }
        content.addView(searchButton, LinearLayout.LayoutParams(-1, dp(50)))

        searchInput.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_SEARCH) {
                searchMusic()
                true
            } else false
        }

        addSpace(content, 28)
        content.addView(label("YOUR MOOD", 12f, gold, true))
        addSpace(content, 14)

        val moods = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val moodNames = listOf("Chill", "Energy", "Focus")
        moodNames.forEach { mood ->
            val card = TextView(this).apply {
                text = "✦  $mood"
                textSize = 13f
                gravity = Gravity.CENTER
                setTextColor(gold)
                background = rounded(panel, 16)
                setPadding(dp(12), dp(16), dp(12), dp(16))
            }
            val params = LinearLayout.LayoutParams(0, dp(52), 1f)
            params.marginEnd = dp(7)
            moods.addView(card, params)
        }
        content.addView(moods)

        addSpace(content, 28)
        content.addView(label("MADE FOR YOUR EARS", 12f, gold, true))
        addSpace(content, 12)

        results = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        results.addView(label("Search to discover music.", 14f, muted))
        content.addView(results)

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(12), dp(10), dp(12))
            setBackgroundColor(Color.rgb(15, 20, 29))
        }

        listOf("⌂  Home", "⌕  Search", "♡  Liked", "♫  Library").forEach { item ->
            val tab = TextView(this).apply {
                text = item
                gravity = Gravity.CENTER
                textSize = 11f
                setTextColor(if (item.contains("Home")) gold else muted)
                setPadding(dp(5), dp(8), dp(5), dp(8))
            }
            bottom.addView(tab, LinearLayout.LayoutParams(0, -2, 1f))
        }
        root.addView(bottom)

        val mini = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = rounded(Color.rgb(35, 46, 62), 14)
        }

        miniTitle = label("Nothing playing", 13f, Color.WHITE, true)
        miniTitle.setOnClickListener {
    if (currentSongName != "Nothing playing") {
        openPlayerScreen()
    }
        }
        mini.addView(miniTitle, LinearLayout.LayoutParams(0, -2, 1f))

        val playPause = TextView(this).apply {
            text = "▶"
            textSize = 20f
            setTextColor(gold)
            setPadding(dp(14), dp(5), dp(5), dp(5))
            setOnClickListener {
                try {
                    if (player?.isPlaying == true) {
                        player?.pause()
                        text = "▶"
                    } else {
                        player?.start()
                        text = "Ⅱ"
                    }
                } catch (_: Exception) { }
            }
        }
        mini.addView(playPause)
        val miniParams = LinearLayout.LayoutParams(-1, dp(52))
        miniParams.setMargins(dp(14), dp(4), dp(14), dp(8))
        root.addView(mini, miniParams)
        val developerCredit = label(
    "Developed by Shubham Upadhyay",
    11f,
    muted
).apply {
    gravity = Gravity.CENTER
    setPadding(0, dp(10), 0, dp(12))
}

content.addView(developerCredit)
        setContentView(root)
    }
     private fun openPlayerScreen() {
    val dialog = Dialog(this)
    val layout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(24), dp(30), dp(24), dp(30))
        setBackgroundColor(bg)
    }

    val artwork = TextView(this).apply {
        text = "♫"
        textSize = 100f
        gravity = Gravity.CENTER
        setTextColor(gold)
        background = rounded(panel, 24)
    }
    layout.addView(artwork, LinearLayout.LayoutParams(-1, dp(280)))

    layout.addView(label("NOW PLAYING", 12f, gold, true))
    layout.addView(label(currentSongName, 24f, Color.WHITE, true))
    layout.addView(label(currentSongArtist, 15f, muted))

    val close = Button(this).apply {
        text = "CLOSE PLAYER"
        setOnClickListener { dialog.dismiss() }
    }
    layout.addView(close)

    dialog.setContentView(layout)
    dialog.window?.setLayout(-1, -1)
    dialog.show()
    dialog.window?.setLayout(-1, -1)
     }
    private fun searchMusic() {
        val term = searchInput.text.toString().trim()
        if (term.isEmpty()) {
            Toast.makeText(this, "Enter a song or artist name", Toast.LENGTH_SHORT).show()
            return
        }

        results.removeAllViews()
        results.addView(label("Searching...", 14f, gold))

        thread {
            try {
                val url = URL(
                    "https://api.jamendo.com/v3.0/tracks/?client_id=1c57991d&format=json&limit=15&search=" +
                        URLEncoder.encode(term, "UTF-8")
                )
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                val data = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()

                val tracks = JSONObject(data).getJSONArray("results")

                runOnUiThread {
                    results.removeAllViews()
                    for (i in 0 until tracks.length()) {
                        val track = tracks.getJSONObject(i)
                        val name = track.optString("name", "Unknown track")
                        val artist = track.optString("artist_name", "Unknown artist")
                        val audio = track.optString("audio", "")

                        val item = LinearLayout(this).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                            setPadding(dp(12), dp(13), dp(12), dp(13))
                            background = rounded(panel, 14)
                        }

                        val info = LinearLayout(this).apply {
                            orientation = LinearLayout.VERTICAL
                        }
                        info.addView(label(name, 14f, Color.WHITE, true))
                        info.addView(label(artist, 12f, muted))
                        item.addView(info, LinearLayout.LayoutParams(0, -2, 1f))

                        val play = TextView(this).apply {
                            text = "▶"
                            textSize = 20f
                            setTextColor(gold)
                            setPadding(dp(12), dp(8), dp(5), dp(8))
                            setOnClickListener { playMusic(audio, name, artist) }
                        }
                        item.addView(play)

                        val params = LinearLayout.LayoutParams(-1, -2)
                        params.bottomMargin = dp(9)
                        results.addView(item, params)
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    results.removeAllViews()
                    results.addView(label("Could not load music. Check your internet or API access.", 13f, muted))
                }
            }
        }
    }

    private fun playMusic(
    audioUrl: String,
    name: String,
    artist: String = "Unknown artist"
) {
    if (audioUrl.isBlank()) return

    try {
        player?.release()

        currentSongName = name
        currentSongArtist = artist
        miniTitle.text = name

        player = MediaPlayer().apply {
            setDataSource(audioUrl)

            setOnPreparedListener {
                it.start()
                miniTitle.text = currentSongName
            }

            setOnErrorListener { _, _, _ ->
                Toast.makeText(
                    this@MainActivity,
                    "Playback failed",
                    Toast.LENGTH_SHORT
                ).show()
                true
            }

            prepareAsync()
        }

    } catch (_: Exception) {
        Toast.makeText(
            this,
            "Unable to play this track",
            Toast.LENGTH_SHORT
        ).show()
    }
}

override fun onDestroy() {
    player?.release()
    player = null
    super.onDestroy()
}
}
