package com.rhythm.music

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.ViewGroup
import android.widget.*
import android.text.InputType
import android.media.MediaPlayer
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.concurrent.thread

class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var results: LinearLayout
    private var player: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.rgb(15, 17, 27)
        window.navigationBarColor = Color.rgb(15, 17, 27)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 28, 20, 16)
            setBackgroundColor(Color.rgb(15, 17, 27))
        }

        root.addView(TextView(this).apply {
            text = "RHYTHM"
            textSize = 30f
            setTextColor(Color.WHITE)
        })

        val input = EditText(this).apply {
            hint = "Search songs"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
        }

        root.addView(input)

        root.addView(Button(this).apply {
            text = "Set Jamendo Client ID"
            setOnClickListener {
                val field = EditText(this@MainActivity)
                field.hint = "Enter Client ID"

                val prefs = getSharedPreferences("rhythm", MODE_PRIVATE)
                field.setText(prefs.getString("client_id", ""))

                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Jamendo API Setup")
                    .setView(field)
                    .setPositiveButton("Save") { _, _ ->
                        prefs.edit()
                            .putString("client_id", field.text.toString().trim())
                            .apply()
                        status.text = "Client ID saved"
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        })

        root.addView(Button(this).apply {
            text = "Search Music"
            setOnClickListener {
                val term = input.text.toString().trim()
                if (term.isNotEmpty()) searchMusic(term)
            }
        })

        status = TextView(this).apply {
            text = "Welcome to Rhythm"
            setTextColor(Color.WHITE)
            setPadding(0, 15, 0, 10)
        }
        root.addView(status)

        val scroll = ScrollView(this)

        results = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        scroll.addView(results)

        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        setContentView(root)
    }

    private fun searchMusic(term: String) {
        val id = getSharedPreferences("rhythm", MODE_PRIVATE)
            .getString("client_id", "")

        if (id.isNullOrBlank()) {
            status.text = "Please set Jamendo Client ID first"
            return
        }

        status.text = "Searching..."
        results.removeAllViews()

        thread {
            try {
                val q = URLEncoder.encode(term, "UTF-8")
                val url = URL(
                    "https://api.jamendo.com/v3.0/tracks/?client_id=$id&format=json&limit=20&audioformat=mp31&search=$q"
                )

                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 15000
                connection.readTimeout = 15000

                val json = connection.inputStream.bufferedReader().use {
                    it.readText()
                }
                connection.disconnect()

                val tracks = JSONObject(json).optJSONArray("results")

                runOnUiThread {
                    results.removeAllViews()

                    if (tracks == null || tracks.length() == 0) {
                        status.text = "No songs found"
                        return@runOnUiThread
                    }

                    status.text = "${tracks.length()} tracks found"

                    for (i in 0 until tracks.length()) {
                        val track = tracks.getJSONObject(i)
                        val name = track.optString("name")
                        val artist = track.optString("artist_name")
                        val audio = track.optString("audio")

                        val item = LinearLayout(this).apply {
                            orientation = LinearLayout.VERTICAL
                            setPadding(12, 12, 12, 12)
                            setBackgroundColor(Color.rgb(34, 37, 53))
                        }

                        item.addView(TextView(this).apply {
                            text = "$name\n$artist"
                            textSize = 16f
                            setTextColor(Color.WHITE)
                        })

                        item.addView(Button(this).apply {
                            text = "Play Preview"
                            setOnClickListener {
                                playMusic(audio)
                            }
                        })

                        results.addView(item)
                    }
                }

            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "Error: ${e.localizedMessage}"
                }
            }
        }
    }

    private fun playMusic(url: String) {
        try {
            player?.release()

            player = MediaPlayer().apply {
                setDataSource(url)

                setOnPreparedListener {
                    it.start()
                    status.text = "Playing music"
                }

                setOnErrorListener { _, _, _ ->
                    status.text = "Playback failed"
                    true
                }

                prepareAsync()
            }

        } catch (e: Exception) {
            status.text = "Playback failed"
        }
    }

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }
}
