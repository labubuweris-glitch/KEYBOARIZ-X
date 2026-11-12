package com.keyboarizx

import android.app.Activity
import android.os.Bundle
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.widget.*

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("keyboard_settings", Context.MODE_PRIVATE) }
    private val cyan = Color.rgb(0, 207, 255)
    private val dark = Color.rgb(8, 13, 20)
    private val panel = Color.rgb(20, 31, 43)
    private val palettes = listOf(
        Palette("Hitam Cyan", Color.WHITE, cyan, dark),
        Palette("Gelap Hijau", Color.WHITE, Color.rgb(0, 230, 160), dark),
        Palette("Terang", Color.rgb(20, 28, 38), Color.rgb(0, 145, 190), Color.rgb(238, 244, 248))
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = dark
        window.navigationBarColor = dark
        window.decorView.setBackgroundColor(dark)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(28), dp(22), dp(20))
        }
        root.addView(TextView(this).apply {
            text = "KEYBOARIZ X"
            textSize = 26f
            setTextColor(cyan)
            typeface = Typeface.DEFAULT_BOLD
        })
        root.addView(TextView(this).apply {
            text = "KEYBOARD FUTURISTIK • OFFLINE"
            textSize = 11f
            setTextColor(Color.LTGRAY)
            setPadding(0, dp(4), 0, dp(24))
        })

        section(root, "AKTIVASI KEYBOARD")
        addAction(root, "Aktifkan KEYBOARIZ X") {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        }
        addAction(root, "Pilih KEYBOARIZ X sebagai default") {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        }

        section(root, "TEMA DAN WARNA")
        val names = palettes.map { it.name }
        val spinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, names)
            setSelection(palettes.indexOfFirst { it.name == prefs.getString("palette", "Hitam Cyan") }.coerceAtLeast(0))
        }
        root.addView(spinner, LinearLayout.LayoutParams(-1, dp(52)))
        val preview = TextView(this).apply {
            text = "Aa   123   KEYBOARIZ X"
            textSize = 20f
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(16), dp(12), dp(16))
        }
        root.addView(preview, LinearLayout.LayoutParams(-1, dp(64)))
        fun updatePreview(index: Int) {
            val p = palettes[index]
            preview.setTextColor(p.text)
            preview.setBackgroundColor(p.key)
        }
        updatePreview(spinner.selectedItemPosition)
        spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                updatePreview(position)
                val p = palettes[position]
                prefs.edit().putString("palette", p.name)
                    .putInt("textColor", p.text).putInt("keyColor", p.key).putInt("backgroundColor", p.background).apply()
            }
        }

        section(root, "PENGATURAN")
        addAction(root, "Tentang KEYBOARIZ X") {
            android.app.AlertDialog.Builder(this)
                .setTitle("KEYBOARIZ X")
                .setMessage("Keyboard Android ringan dengan tema futuristik hitam dan cyan. Versi 1.0")
                .setPositiveButton("OK", null).show()
        }
        val note = TextView(this).apply {
            text = "Pilih KEYBOARIZ X di daftar keyboard Android setelah mengaktifkannya. Tema warna akan diterapkan pada keyboard."
            textSize = 12f
            setTextColor(Color.LTGRAY)
            setPadding(0, dp(18), 0, 0)
        }
        root.addView(note)
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun section(root: LinearLayout, title: String) {
        root.addView(TextView(this).apply {
            text = title
            textSize = 12f
            setTextColor(Color.rgb(0, 207, 255))
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(18), 0, dp(8))
        })
    }

    private fun addAction(root: LinearLayout, label: String, action: () -> Unit) {
        root.addView(Button(this).apply {
            text = label
            isAllCaps = false
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(29, 48, 64))
            setOnClickListener { action() }
        }, LinearLayout.LayoutParams(-1, dp(52)).apply { bottomMargin = dp(8) })
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private data class Palette(val name: String, val text: Int, val key: Int, val background: Int)
}
