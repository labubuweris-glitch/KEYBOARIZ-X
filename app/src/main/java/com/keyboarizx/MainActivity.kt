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
    private val defaultTheme = KeyboardTheme(
        "Default",
        Color.parseColor("#F1C40F"),
        Color.BLACK,
        Color.BLACK,
        Color.parseColor("#39C6E8"),
        Color.parseColor("#45B8E8"),
        Color.parseColor("#5BCD97")
    )
    private val themePrefsKey = "custom_keyboard_themes"
    private val selectedThemeKey = "selected_keyboard_theme"

    private data class KeyboardTheme(
        val name: String, val text: Int, val key: Int, val background: Int,
        val shortcutBorder: Int, val characterBorder: Int, val functionBorder: Int
    )

    private fun colorHex(color: Int) = String.format("#%06X", 0xFFFFFF and color)

    private fun loadCustomThemes(): MutableList<KeyboardTheme> {
        val raw = prefs.getString(themePrefsKey, null) ?: return mutableListOf()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                KeyboardTheme(
                    item.optString("name"),
                    item.optInt("text"), item.optInt("key"), item.optInt("background"),
                    item.optInt("shortcutBorder"), item.optInt("characterBorder"), item.optInt("functionBorder")
                ).takeIf { it.name.isNotBlank() }
            }.toMutableList()
        } catch (_: Exception) { mutableListOf() }
    }

    private fun saveCustomThemes(themes: List<KeyboardTheme>) {
        val array = JSONArray()
        themes.forEach { theme ->
            array.put(JSONObject().apply {
                put("name", theme.name); put("text", theme.text); put("key", theme.key)
                put("background", theme.background); put("shortcutBorder", theme.shortcutBorder)
                put("characterBorder", theme.characterBorder); put("functionBorder", theme.functionBorder)
            })
        }
        prefs.edit().putString(themePrefsKey, array.toString()).apply()
    }

    private fun applyTheme(theme: KeyboardTheme) {
        prefs.edit()
            .putString(selectedThemeKey, theme.name)
            .putInt("textColor", theme.text)
            .putInt("keyColor", theme.key)
            .putInt("backgroundColor", theme.background)
            .putInt("shortcutBorderColor", theme.shortcutBorder)
            .putInt("characterBorderColor", theme.characterBorder)
            .putInt("functionBorderColor", theme.functionBorder)
            .apply()
    }

    private fun showThemeManager() {
        fun refresh() {
            val themes = loadCustomThemes()
            val labels = listOf("Default  •  tema bawaan") + themes.map { it.name } + listOf("+ Tambah warna kustom")
            android.app.AlertDialog.Builder(this)
                .setTitle("Tema Warna Keyboard")
                .setItems(labels.toTypedArray()) { _, which ->
                    when {
                        which == 0 -> {
                            applyTheme(defaultTheme)
                            Toast.makeText(this, "Tema Default diterapkan", Toast.LENGTH_SHORT).show()
                        }
                        which == labels.lastIndex -> showThemeEditor(null) { refresh() }
                        else -> {
                            val selected = themes[which - 1]
                            android.app.AlertDialog.Builder(this)
                                .setTitle(selected.name)
                                .setItems(arrayOf("Terapkan", "Edit", "Hapus")) { _, action ->
                                    when (action) {
                                        0 -> {
                                            applyTheme(selected)
                                            Toast.makeText(this, "Tema ${selected.name} diterapkan", Toast.LENGTH_SHORT).show()
                                        }
                                        1 -> showThemeEditor(selected) { refresh() }
                                        2 -> android.app.AlertDialog.Builder(this)
                                            .setMessage("Hapus tema warna ${selected.name}?")
                                            .setPositiveButton("Hapus") { _, _ ->
                                                saveCustomThemes(loadCustomThemes().filterNot { it.name == selected.name })
                                                if (prefs.getString(selectedThemeKey, "Default") == selected.name) applyTheme(defaultTheme)
                                                refresh()
                                            }
                                            .setNegativeButton("Batal", null).show()
                                    }
                                }
                                .setNegativeButton("Batal", null).show()
                        }
                    }
                }
                .setNegativeButton("Tutup", null)
                .show()
        }
        refresh()
    }

    private fun showThemeEditor(existing: KeyboardTheme?, onDone: () -> Unit) {
        val labels = listOf(
            "Nama tema warna", "Warna tulisan", "Warna tombol", "Warna latar keyboard",
            "Warna garis Tombol Pintasan", "Warna garis Tombol Karakter", "Warna garis Tombol Fungsi"
        )
        val values = listOf(
            existing?.name ?: "",
            colorHex(existing?.text ?: defaultTheme.text),
            colorHex(existing?.key ?: defaultTheme.key),
            colorHex(existing?.background ?: defaultTheme.background),
            colorHex(existing?.shortcutBorder ?: defaultTheme.shortcutBorder),
            colorHex(existing?.characterBorder ?: defaultTheme.characterBorder),
            colorHex(existing?.functionBorder ?: defaultTheme.functionBorder)
        )
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(4), dp(20), 0)
        }
        val fields = labels.mapIndexed { index, label ->
            val input = EditText(this).apply {
                hint = if (index == 0) label else "$label  (#RRGGBB)"
                setSingleLine(true)
                setText(values[index])
                textSize = 14f
                if (index == 0) inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            }
            form.addView(input, LinearLayout.LayoutParams(-1, dp(48)))
            input
        }
        android.app.AlertDialog.Builder(this)
            .setTitle(if (existing == null) "Tambah tema warna" else "Edit tema warna")
            .setView(ScrollView(this).apply { addView(form) })
            .setPositiveButton("Simpan", null)
            .setNegativeButton("Batal", null)
            .create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val name = fields[0].text.toString().trim()
                        if (name.isBlank() || name.equals("Default", true)) {
                            Toast.makeText(this, "Nama tema tidak boleh kosong atau Default", Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }
                        try {
                            val colors = fields.drop(1).map { Color.parseColor(it.text.toString().trim()) }
                            val themes = loadCustomThemes()
                            if (themes.any { it.name.equals(name, true) && it.name != existing?.name }) {
                                Toast.makeText(this, "Nama tema sudah digunakan", Toast.LENGTH_SHORT).show()
                                return@setOnClickListener
                            }
                            val theme = KeyboardTheme(name, colors[0], colors[1], colors[2], colors[3], colors[4], colors[5])
                            if (existing != null) themes.removeAll { it.name == existing.name }
                            themes.add(theme)
                            saveCustomThemes(themes)
                            applyTheme(theme)
                            dialog.dismiss()
                            onDone()
                            Toast.makeText(this, "Tema $name disimpan dan diterapkan", Toast.LENGTH_SHORT).show()
                        } catch (_: IllegalArgumentException) {
                            Toast.makeText(this, "Kode warna tidak valid. Gunakan format #RRGGBB", Toast.LENGTH_LONG).show()
                        }
                    }
                }
                dialog.show()
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Migrasi sekali ke sistem tema baru: Default hitam-emas, tanpa mengubah pilihan karakter.
        if (!prefs.getBoolean("keyboard_theme_v2_migrated", false)) {
            applyTheme(defaultTheme)
            prefs.edit().putBoolean("keyboard_theme_v2_migrated", true).apply()
        }
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

        section(root, "TEMA WARNA")
        root.addView(TextView(this).apply {
            text = "Gunakan tema Default atau buat tema warna sendiri. Tema kustom dapat ditambah, diedit, dan dihapus."
            textSize = 13f
            setTextColor(Color.LTGRAY)
        })
        addAction(root, "Kelola Tema Warna") { showThemeManager() }

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


        section(root, "KARAKTER ANIMASI")
        root.addView(TextView(this).apply {
            text = "Pilih karakter yang bergerak di atas tombol keyboard."
            textSize = 13f
            setTextColor(Color.LTGRAY)
        })
        val characterNames = listOf("Tanpa karakter", "Semut", "Capung (tampak atas)", "Mustang (tampak atas)", "Motor sport + pengendara (samping)", "Ayam berjalan (samping)")
        val characterValues = listOf("NONE", "ANT", "DRAGONFLY", "MUSTANG", "MOTORCYCLE", "CHICKEN")
        val characterSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, characterNames)
            setSelection(characterValues.indexOf(prefs.getString("keyboard_character", "NONE")).coerceAtLeast(0))
        }
        root.addView(characterSpinner, LinearLayout.LayoutParams(-1, dp(52)))
        characterSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                prefs.edit().putString("keyboard_character", characterValues[position]).apply()
                Toast.makeText(this@MainActivity, "Pilihan karakter disimpan. Buka ulang keyboard untuk menerapkan.", Toast.LENGTH_SHORT).show()
            }
        }

        section(root, "JALUR ANIMASI")
        root.addView(TextView(this).apply {
            text = "Pilih pola gerakan karakter di seluruh area keyboard."
            textSize = 13f
            setTextColor(Color.LTGRAY)
        })
        val routeNames = listOf("Ular (tembus dinding)", "Lurus", "Zig-zag", "Gelombang", "Melingkar")
        val routeValues = listOf("SNAKE", "STRAIGHT", "ZIGZAG", "WAVE", "CIRCLE")
        val routeSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, routeNames)
            setSelection(routeValues.indexOf(prefs.getString("keyboard_animation_route", "SNAKE")).coerceAtLeast(0))
        }
        root.addView(routeSpinner, LinearLayout.LayoutParams(-1, dp(52)))
        routeSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                prefs.edit().putString("keyboard_animation_route", routeValues[position]).apply()
                Toast.makeText(this@MainActivity, "Jalur animasi disimpan. Buka ulang keyboard untuk menerapkan.", Toast.LENGTH_SHORT).show()
            }
        }

        section(root, "KECEPATAN ANIMASI")
        root.addView(TextView(this).apply {
            text = "Atur kecepatan gerak semua karakter animasi."
            textSize = 13f
            setTextColor(Color.LTGRAY)
        })
        val speedNames = listOf("Lambat", "Sedang (bawaan)", "Cepat")
        val speedValues = listOf("SLOW", "NORMAL", "FAST")
        val speedSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, speedNames)
            setSelection(speedValues.indexOf(prefs.getString("keyboard_animation_speed", "NORMAL")).coerceAtLeast(0))
        }
        root.addView(speedSpinner, LinearLayout.LayoutParams(-1, dp(52)))
        speedSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                prefs.edit().putString("keyboard_animation_speed", speedValues[position]).apply()
                Toast.makeText(this@MainActivity, "Kecepatan animasi disimpan. Buka ulang keyboard untuk menerapkan.", Toast.LENGTH_SHORT).show()
            }
        }

        section(root, "WARNA KARAKTER")
        root.addView(TextView(this).apply {
            text = "Atur warna tampilan semua karakter animasi. Original mempertahankan warna asli setiap karakter."
            textSize = 13f
            setTextColor(Color.LTGRAY)
        })
        val characterColorNames = listOf("Original", "Hitam pekat", "Putih", "Merah", "Kuning", "Hijau neon", "Biru neon", "Ungu")
        val characterColorValues = listOf("ORIGINAL", "#171717", "#FFFFFF", "#FF3B30", "#FFD60A", "#39FF14", "#00BFFF", "#BF5AF2")
        val characterColorSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, characterColorNames)
            setSelection(characterColorValues.indexOf(prefs.getString("keyboard_character_color", "ORIGINAL")).coerceAtLeast(0))
        }
        root.addView(characterColorSpinner, LinearLayout.LayoutParams(-1, dp(52)))
        characterColorSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                prefs.edit().putString("keyboard_character_color", characterColorValues[position]).apply()
                Toast.makeText(this@MainActivity, "Warna karakter disimpan. Buka ulang keyboard untuk menerapkan.", Toast.LENGTH_SHORT).show()
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
}
