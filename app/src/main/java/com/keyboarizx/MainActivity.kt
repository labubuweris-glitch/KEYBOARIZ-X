package com.keyboarizx

import android.app.Activity
import android.os.Bundle
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.provider.Settings
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("keyboard_settings", Context.MODE_PRIVATE) }
    private val cyan = Color.rgb(0, 207, 255)
    private val dark = Color.rgb(8, 13, 20)
    private val palettes = listOf(
        Palette("Hitam Cyan", Color.WHITE, Color.rgb(37, 55, 70), Color.rgb(17, 28, 39)),
        Palette("Hitam Merah", Color.WHITE, Color.rgb(91, 35, 42), Color.rgb(28, 14, 18)),
        Palette("Hitam Ungu", Color.WHITE, Color.rgb(75, 48, 103), Color.rgb(25, 17, 35)),
        Palette("Hitam Hijau", Color.WHITE, Color.rgb(35, 83, 61), Color.rgb(12, 27, 20)),
        Palette("Hitam Emas", Color.rgb(255, 225, 157), Color.rgb(91, 70, 35), Color.rgb(29, 24, 15)),
        Palette("Hitam Biru", Color.WHITE, Color.rgb(39, 66, 112), Color.rgb(13, 21, 37)),
        Palette("Hitam Oranye", Color.WHITE, Color.rgb(112, 62, 29), Color.rgb(31, 20, 13)),
        Palette("Hitam Pink", Color.WHITE, Color.rgb(105, 43, 78), Color.rgb(31, 16, 26)),
        Palette("Hitam Putih", Color.WHITE, Color.rgb(65, 65, 65), Color.rgb(20, 20, 20)),
        Palette("Ocean", Color.WHITE, Color.rgb(29, 91, 108), Color.rgb(13, 37, 46)),
        Palette("Forest", Color.WHITE, Color.rgb(48, 91, 55), Color.rgb(17, 34, 20)),
        Palette("Wine", Color.WHITE, Color.rgb(112, 42, 61), Color.rgb(43, 17, 26)),
        Palette("Graphite", Color.WHITE, Color.rgb(75, 83, 92), Color.rgb(31, 35, 40)),
        Palette("Silver", Color.rgb(25, 31, 39), Color.rgb(194, 204, 214), Color.rgb(224, 230, 236)),
        Palette("Arctic", Color.rgb(19, 45, 58), Color.rgb(177, 225, 241), Color.rgb(224, 244, 250)),
        Palette("Cloud", Color.rgb(28, 42, 65), Color.rgb(202, 218, 242), Color.rgb(237, 243, 251)),
        Palette("Rose", Color.rgb(76, 34, 54), Color.rgb(244, 202, 220), Color.rgb(255, 237, 245)),
        Palette("Emerald", Color.rgb(20, 58, 47), Color.rgb(171, 224, 205), Color.rgb(225, 246, 237))
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
            textSize = 27f
            setTextColor(cyan)
            typeface = Typeface.DEFAULT_BOLD
        })
        root.addView(TextView(this).apply {
            text = "KEYBOARD FUTURISTIK • OFFLINE"
            textSize = 11f
            setTextColor(Color.LTGRAY)
            setPadding(0, dp(4), 0, dp(22))
        })

        section(root, "AKTIVASI KEYBOARD")
        addAction(root, "Aktifkan KEYBOARIZ X") {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
        addAction(root, "Pilih KEYBOARIZ X sebagai default") {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        }

        section(root, "TEMA DAN WARNA")
        root.addView(TextView(this).apply {
            text = "18 tema siap pakai + pilihan warna buatan sendiri"
            textSize = 13f
            setTextColor(Color.LTGRAY)
        })
        val customName = "Warna Kustom"
        val themeNames = palettes.map { it.name } + customName
        val customIndex = palettes.size
        val spinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, themeNames)
            setSelection(themeNames.indexOf(prefs.getString("palette", "Hitam Cyan")).coerceAtLeast(0))
        }
        root.addView(spinner, LinearLayout.LayoutParams(-1, dp(52)))

        val preview = TextView(this).apply {
            text = "Aa   123   KEYBOARIZ X"
            textSize = 19f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        root.addView(preview, LinearLayout.LayoutParams(-1, dp(62)))
        val contrastHint = TextView(this).apply {
            text = "Pratinjau warna • Warna tersimpan di perangkat"
            textSize = 11f
            setTextColor(Color.LTGRAY)
            setPadding(0, dp(7), 0, 0)
        }
        root.addView(contrastHint)

        fun refreshPreview() {
            val text = prefs.getInt("textColor", Color.WHITE)
            val key = prefs.getInt("keyColor", Color.rgb(37, 55, 70))
            preview.setTextColor(text)
            preview.setBackgroundColor(key)
        }
        fun applyPalette(index: Int) {
            if (index == customIndex) {
                prefs.edit().putString("palette", customName).apply()
                if (!prefs.contains("textColor")) prefs.edit().putInt("textColor", Color.WHITE).apply()
                if (!prefs.contains("keyColor")) prefs.edit().putInt("keyColor", Color.rgb(37, 55, 70)).apply()
                if (!prefs.contains("backgroundColor")) prefs.edit().putInt("backgroundColor", Color.rgb(17, 28, 39)).apply()
                refreshPreview()
            } else {
                val p = palettes[index]
                prefs.edit().putString("palette", p.name)
                    .putInt("textColor", p.text)
                    .putInt("keyColor", p.key)
                    .putInt("backgroundColor", p.background).apply()
                refreshPreview()
            }
        }
        applyPalette(spinner.selectedItemPosition)
        spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                applyPalette(position)
            }
        }

        fun editCustomColor(label: String, preferenceKey: String) {
            val current = prefs.getInt(preferenceKey, Color.WHITE)
            val hex = String.format("#%06X", 0xFFFFFF and current)
            val input = EditText(this).apply {
                hint = "#RRGGBB"
                setSingleLine(true)
                setText(hex)
                setSelection(text.length)
            }
            android.app.AlertDialog.Builder(this)
                .setTitle(label)
                .setMessage("Masukkan kode warna HEX, contoh: #00D9FF")
                .setView(input)
                .setPositiveButton("Simpan") { _, _ ->
                    try {
                        val color = Color.parseColor(input.text.toString().trim())
                        prefs.edit().putInt(preferenceKey, color).putString("palette", customName).apply()
                        spinner.setSelection(customIndex)
                        refreshPreview()
                    } catch (_: IllegalArgumentException) {
                        Toast.makeText(this, "Kode warna tidak valid. Gunakan format #RRGGBB", Toast.LENGTH_LONG).show()
                    }
                }
                .setNegativeButton("Batal", null)
                .show()
        }
        addAction(root, "Ubah warna tulisan") { editCustomColor("Warna tulisan", "textColor") }
        addAction(root, "Ubah warna tombol") { editCustomColor("Warna tombol", "keyColor") }
        addAction(root, "Ubah warna latar keyboard") { editCustomColor("Warna latar keyboard", "backgroundColor") }


        section(root, "BENTUK TOMBOL")
        root.addView(TextView(this).apply {
            text = "Pilih bentuk tombol. Love, Hexagon, dan Diamond memakai susunan selang-seling."
            textSize = 13f
            setTextColor(Color.LTGRAY)
        })
        val shapeOptions = listOf(
            "Rounded", "Pill", "Square", "Soft Square", "Circle",
            "Love", "Hexagon", "Oval", "Diamond", "Double Border"
        )
        val shapeValues = listOf(
            "ROUNDED", "PILL", "SQUARE", "SOFT_SQUARE", "CIRCLE",
            "LOVE", "HEXAGON", "OVAL", "DIAMOND", "DOUBLE_BORDER"
        )
        val shapeSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, shapeOptions)
            setSelection(shapeValues.indexOf(prefs.getString("keyShape", "ROUNDED")).coerceAtLeast(0))
        }
        root.addView(shapeSpinner, LinearLayout.LayoutParams(-1, dp(52)))
        shapeSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                prefs.edit().putString("keyShape", shapeValues[position]).apply()
            }
        }


        section(root, "RIZBOARD")
        root.addView(TextView(this).apply {
            text = "Kelola tombol custom yang muncul bersama tombol bawaan di halaman RIZBOARD."
            textSize = 13f
            setTextColor(Color.LTGRAY)
        })
        addAction(root, "Kelola Tombol RIZBOARD") { showRizboardManager() }

        section(root, "CLIPBOARD")
        addAction(root, "Kelola Kategori Sematan") {
            showCategoryManager()
        }

        section(root, "ANIMASI CUSTOM")
        root.addView(TextView(this).apply {
            text = "Pilih GIF dari galeri HP. GIF akan tampil kecil di atas tombol keyboard. Default: tanpa animasi."
            textSize = 13f
            setTextColor(Color.LTGRAY)
        })
        addAction(root, "Pilih GIF dari Galeri") { pickCustomGif() }
        addAction(root, "Nonaktifkan Animasi") {
            prefs.edit().putBoolean("custom_gif_enabled", false).apply()
            Toast.makeText(this, "Animasi dinonaktifkan", Toast.LENGTH_SHORT).show()
        }
        addAction(root, "Hapus GIF Tersimpan") {
            val saved = prefs.getString("custom_gif_uri", null)
            if (saved != null) {
                try {
                    contentResolver.releasePersistableUriPermission(
                        android.net.Uri.parse(saved), Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {}
            }
            prefs.edit().remove("custom_gif_uri").putBoolean("custom_gif_enabled", false).apply()
            Toast.makeText(this, "GIF tersimpan dihapus", Toast.LENGTH_SHORT).show()
        }

        section(root, "PENGATURAN LAINNYA")
        addAction(root, "Tentang KEYBOARIZ X") {
            android.app.AlertDialog.Builder(this)
                .setTitle("KEYBOARIZ X")
                .setMessage("Keyboard Android ringan, offline, dan tanpa iklan. Versi 1.0")
                .setPositiveButton("OK", null).show()
        }
        root.addView(TextView(this).apply {
            text = "Aktifkan keyboard terlebih dahulu, lalu pilih KEYBOARIZ X sebagai keyboard default. Pengaturan tema tersimpan di perangkat."
            textSize = 12f
            setTextColor(Color.LTGRAY)
            setPadding(0, dp(16), 0, 0)
        })
        setContentView(ScrollView(this).apply { addView(root) })
    }



    private fun pickCustomGif() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/gif"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        try {
            startActivityForResult(intent, 701)
        } catch (_: Exception) {
            Toast.makeText(this, "Galeri GIF tidak tersedia di perangkat ini", Toast.LENGTH_LONG).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 701 || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        val mime = contentResolver.getType(uri)
        if (mime != "image/gif" && !uri.toString().lowercase().endsWith(".gif")) {
            Toast.makeText(this, "Pilih file GIF, ya", Toast.LENGTH_LONG).show()
            return
        }
        try {
            contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            prefs.edit()
                .putString("custom_gif_uri", uri.toString())
                .putBoolean("custom_gif_enabled", true)
                .apply()
            Toast.makeText(this, "GIF tersimpan. Buka keyboard untuk melihat animasi.", Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
            Toast.makeText(this, "GIF tidak dapat diakses. Coba pilih file lain.", Toast.LENGTH_LONG).show()
        }
    }

    private fun loadRizboardCustom(): MutableList<String> {
        val raw = prefs.getString("rizboard_custom_buttons", null) ?: return mutableListOf()
        return try {
            val json = JSONArray(raw)
            (0 until json.length()).mapNotNull { i -> json.optString(i).takeIf { it.isNotEmpty() } }.distinct().toMutableList()
        } catch (_: Exception) { mutableListOf() }
    }

    private fun saveRizboardCustom(items: List<String>) {
        val json = JSONArray()
        items.distinct().forEach { json.put(it) }
        prefs.edit().putString("rizboard_custom_buttons", json.toString()).apply()
    }

    private fun showRizboardManager() {
        fun refresh() {
            val items = loadRizboardCustom()
            val labels = items.map { if (it.length > 32) it.take(29) + "…" else it }.toMutableList()
            labels.add("+ Tambah tombol custom")
            android.app.AlertDialog.Builder(this)
                .setTitle("Kelola Tombol RIZBOARD")
                .setItems(labels.toTypedArray()) { _, which ->
                    if (which == labels.lastIndex) showRizboardEditDialog(null) { refresh() }
                    else showRizboardItemActions(items[which]) { refresh() }
                }
                .setNegativeButton("Tutup", null)
                .show()
        }
        refresh()
    }

    private fun showRizboardItemActions(value: String, onDone: () -> Unit) {
        android.app.AlertDialog.Builder(this)
            .setTitle(if (value.length > 40) value.take(37) + "…" else value)
            .setItems(arrayOf("Ubah", "Hapus")) { _, which ->
                if (which == 0) showRizboardEditDialog(value, onDone)
                else android.app.AlertDialog.Builder(this)
                    .setMessage("Hapus tombol custom ini?")
                    .setPositiveButton("Hapus") { _, _ ->
                        saveRizboardCustom(loadRizboardCustom().filterNot { it == value })
                        onDone()
                    }
                    .setNegativeButton("Batal", null).show()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showRizboardEditDialog(oldValue: String?, onDone: () -> Unit) {
        val input = EditText(this).apply {
            hint = "Isi teks tombol"
            setText(oldValue ?: "")
            setSingleLine(false)
            minLines = 1
            maxLines = 4
        }
        android.app.AlertDialog.Builder(this)
            .setTitle(if (oldValue == null) "Tambah tombol custom" else "Ubah tombol custom")
            .setMessage("Teks akan dimasukkan persis saat tombol ditekan.")
            .setView(input)
            .setPositiveButton("Simpan") { _, _ ->
                val value = input.text.toString()
                if (value.isNotEmpty()) {
                    val items = loadRizboardCustom()
                    if (oldValue != null) items.remove(oldValue)
                    if (value !in items) {
                        items.add(value)
                        saveRizboardCustom(items)
                    } else Toast.makeText(this, "Tombol tersebut sudah ada", Toast.LENGTH_SHORT).show()
                    onDone()
                } else Toast.makeText(this, "Isi tombol tidak boleh kosong", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun loadPinCategories(): LinkedHashMap<String, List<String>> {
        val raw = prefs.getString("clipboard_pin_categories", null)
        if (raw != null) {
            return try {
                val json = JSONObject(raw)
                val result = linkedMapOf<String, List<String>>()
                val keys = json.keys()
                while (keys.hasNext()) {
                    val name = keys.next()
                    val array = json.optJSONArray(name) ?: JSONArray()
                    result[name] = (0 until array.length()).mapNotNull { i ->
                        array.optString(i).takeIf { it.isNotBlank() }
                    }.distinct()
                }
                result
            } catch (_: Exception) {
                linkedMapOf()
            }
        }
        val legacyRaw = prefs.getString("clipboard_pinned", null)
        val legacy = try {
            val array = if (legacyRaw == null) JSONArray() else JSONArray(legacyRaw)
            (0 until array.length()).mapNotNull { i ->
                array.optString(i).takeIf { it.isNotBlank() }
            }.distinct()
        } catch (_: Exception) { emptyList() }
        val migrated = linkedMapOf<String, List<String>>()
        if (legacy.isNotEmpty()) migrated["Belum Dikategorikan"] = legacy
        savePinCategories(migrated)
        prefs.edit().remove("clipboard_pinned").apply()
        return migrated
    }

    private fun savePinCategories(categories: Map<String, List<String>>) {
        val json = JSONObject()
        categories.forEach { (name, items) ->
            val array = JSONArray()
            items.distinct().forEach { array.put(it) }
            json.put(name, array)
        }
        prefs.edit().putString("clipboard_pin_categories", json.toString()).apply()
    }

    private fun showCategoryManager() {
        fun refresh() {
            val categories = loadPinCategories()
            val labels = categories.map { (name, entries) -> "📁  " + name + "  (" + entries.size + " teks)" }.toMutableList()
            labels.add("+ Buat kategori")
            android.app.AlertDialog.Builder(this)
                .setTitle("Kelola Kategori Sematan")
                .setItems(labels.toTypedArray()) { _, which ->
                    if (which == labels.lastIndex) showAddCategoryDialog { refresh() }
                    else showCategoryActions(categories.keys.elementAt(which)) { refresh() }
                }
                .setNegativeButton("Tutup", null)
                .show()
        }
        refresh()
    }

    private fun showAddCategoryDialog(onDone: () -> Unit) {
        val input = EditText(this).apply { hint = "Nama kategori" }
        android.app.AlertDialog.Builder(this)
            .setTitle("Buat kategori")
            .setView(input)
            .setPositiveButton("Simpan") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    val categories = loadPinCategories().toMutableMap()
                    if (categories.keys.any { it.equals(name, true) }) {
                        Toast.makeText(this, "Kategori sudah ada", Toast.LENGTH_SHORT).show()
                    } else {
                        categories[name] = emptyList()
                        savePinCategories(categories)
                    }
                    onDone()
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showCategoryActions(name: String, onDone: () -> Unit) {
        android.app.AlertDialog.Builder(this)
            .setTitle(name)
            .setItems(arrayOf("Ubah nama", "Hapus kategori")) { _, which ->
                if (which == 0) {
                    val input = EditText(this).apply { setText(name) }
                    android.app.AlertDialog.Builder(this)
                        .setTitle("Ubah nama kategori")
                        .setView(input)
                        .setPositiveButton("Simpan") { _, _ ->
                            val newName = input.text.toString().trim()
                            if (newName.isNotEmpty() && newName != name) {
                                val categories = loadPinCategories().toMutableMap()
                                if (categories.keys.none { it.equals(newName, true) }) {
                                    val entries = categories.remove(name).orEmpty()
                                    categories[newName] = entries
                                    savePinCategories(categories)
                                } else {
                                    Toast.makeText(this, "Nama kategori sudah ada", Toast.LENGTH_SHORT).show()
                                }
                            }
                            onDone()
                        }
                        .setNegativeButton("Batal", null)
                        .show()
                } else {
                    android.app.AlertDialog.Builder(this)
                        .setTitle("Hapus kategori?")
                        .setMessage("Teks di dalam kategori ini tidak akan dihapus. Teks akan dipindahkan ke Belum Dikategorikan.")
                        .setPositiveButton("Hapus") { _, _ ->
                            val categories = loadPinCategories().toMutableMap()
                            val removed = categories.remove(name).orEmpty()
                            if (removed.isNotEmpty()) {
                                categories["Belum Dikategorikan"] =
                                    (categories["Belum Dikategorikan"].orEmpty() + removed).distinct()
                            }
                            savePinCategories(categories)
                            onDone()
                        }
                        .setNegativeButton("Batal", null)
                        .show()
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun section(root: LinearLayout, title: String) {
        root.addView(TextView(this).apply {
            text = title
            textSize = 12f
            setTextColor(cyan)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(20), 0, dp(8))
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
