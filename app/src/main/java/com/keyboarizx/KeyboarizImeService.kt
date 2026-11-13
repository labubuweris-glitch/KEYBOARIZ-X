package com.keyboarizx

import android.inputmethodservice.InputMethodService
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.app.AlertDialog
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject

class KeyboarizImeService : InputMethodService() {
    // 0 = lowercase, 1 = one-shot shift, 2 = caps lock
    private var shiftState = 0
    private var numberMode = false
    private var clipboardMode = false
    private var clipboardPage = 0 // 0 = riwayat, 1 = sematan
    private var selectedPinCategory: String? = null
    private val prefs by lazy { getSharedPreferences("keyboard_settings", Context.MODE_PRIVATE) }
    private val handler = Handler(Looper.getMainLooper())
    private val clipboardManager by lazy {
        getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }
    private var deleting = false
    private var enterButton: Button? = null

    // Rekam salinan selama layanan IME masih hidup, termasuk saat tampilan keyboard tertutup.
    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        captureCurrentClipboard()
    }

    private val deleteRepeat = object : Runnable {
        override fun run() {
            if (!deleting) return
            deleteOne()
            handler.postDelayed(this, 55L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        clipboardManager.addPrimaryClipChangedListener(clipboardListener)
    }

    override fun onCreateInputView(): View = createKeyboardView()

    private fun createKeyboardView(): View {
        val textColor = prefs.getInt("textColor", Color.WHITE)
        val keyColor = prefs.getInt("keyColor", Color.rgb(37, 55, 70))
        val backgroundColor = prefs.getInt("backgroundColor", Color.rgb(17, 28, 39))
        val accentColor = Color.rgb(0, 207, 255)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(6), dp(4), dp(6))
            setBackgroundColor(backgroundColor)
        }

        val toolRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        fun makeToolButton(label: String, weight: Float, onClick: () -> Unit) {
            val button = Button(this).apply {
                text = label
                textSize = 13f
                setTextColor(textColor)
                setBackgroundColor(keyColor)
                typeface = Typeface.DEFAULT_BOLD
                isAllCaps = false
                minWidth = 0
                minimumWidth = 0
                setPadding(dp(2), 0, dp(2), 0)
                setOnClickListener { onClick() }
            }
            val params = LinearLayout.LayoutParams(0, dp(36), weight)
            params.setMargins(dp(2), dp(1), dp(2), dp(3))
            toolRow.addView(button, params)
        }

        if (clipboardMode) {
            makeToolButton("CLIPBOARD", 2.2f) { }
            makeToolButton("✕", 0.7f) {
                clipboardMode = false
                selectedPinCategory = null
                setInputView(createKeyboardView())
            }
            root.addView(toolRow)

            val tabRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            fun addTab(label: String, page: Int) {
                val tab = Button(this).apply {
                    text = label
                    textSize = 12f
                    isAllCaps = false
                    minWidth = 0
                    minimumWidth = 0
                    setTextColor(if (clipboardPage == page) Color.rgb(4, 20, 29) else textColor)
                    setBackgroundColor(if (clipboardPage == page) accentColor else keyColor)
                    setOnClickListener {
                        clipboardPage = page
                        selectedPinCategory = null
                        setInputView(createKeyboardView())
                    }
                }
                val lp = LinearLayout.LayoutParams(0, dp(38), 1f)
                lp.setMargins(dp(2), dp(1), dp(2), dp(3))
                tabRow.addView(tab, lp)
            }
            addTab("RIWAYAT", 0)
            addTab("📌 SEMATAN", 1)
            root.addView(tabRow)

            val scrollView = ScrollView(this)
            val itemsLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(2), 0, dp(2))
            }

            fun addEmpty(message: String) {
                val emptyText = TextView(this).apply {
                    text = message
                    textSize = 14f
                    setTextColor(textColor)
                    gravity = Gravity.CENTER
                    setPadding(dp(16), dp(24), dp(16), dp(24))
                }
                itemsLayout.addView(emptyText, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ))
            }

            fun addClipboardItem(item: String, pinnedItem: Boolean, category: String?) {
                val itemButton = Button(this).apply {
                    text = (if (pinnedItem) "📌  " else "") +
                        if (item.length > 180) item.take(180) + "…" else item
                    textSize = 14f
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    setTextColor(textColor)
                    setBackgroundColor(keyColor)
                    isAllCaps = false
                    minWidth = 0
                    minimumWidth = 0
                    setPadding(dp(12), dp(6), dp(12), dp(6))
                    setOnClickListener {
                        currentInputConnection?.commitText(item, 1)
                        clipboardMode = false
                        selectedPinCategory = null
                        setInputView(createKeyboardView())
                    }
                    setOnLongClickListener {
                        val menu = PopupMenu(this@KeyboarizImeService, this)
                        if (!pinnedItem) {
                            menu.menu.add("Sematkan").setOnMenuItemClickListener {
                                showPinCategoryDialog(item)
                                true
                            }
                            menu.menu.add("Hapus").setOnMenuItemClickListener {
                                val updated = loadClipboardHistory().toMutableList()
                                updated.remove(item)
                                saveClipboardHistory(updated)
                                setInputView(createKeyboardView())
                                true
                            }
                        } else {
                            menu.menu.add("Pindahkan kategori").setOnMenuItemClickListener {
                                showMoveCategoryDialog(item, category)
                                true
                            }
                            menu.menu.add("Lepas sematan").setOnMenuItemClickListener {
                                removePinnedItem(item)
                                setInputView(createKeyboardView())
                                true
                            }
                            menu.menu.add("Hapus").setOnMenuItemClickListener {
                                removePinnedItem(item)
                                val updated = loadClipboardHistory().toMutableList()
                                updated.remove(item)
                                saveClipboardHistory(updated)
                                setInputView(createKeyboardView())
                                true
                            }
                        }
                        menu.show()
                        true
                    }
                }
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.setMargins(dp(2), dp(2), dp(2), dp(2))
                itemsLayout.addView(itemButton, params)
            }

            if (clipboardPage == 0) {
                val history = loadClipboardHistory().filterNot { isPinned(it) }
                if (history.isEmpty()) {
                    addEmpty("Belum ada riwayat tersimpan.\nSalin teks untuk menambahkannya ke Clipboard.")
                } else {
                    history.forEach { addClipboardItem(it, false, null) }
                }
            } else {
                val categories = loadPinCategories()
                val category = selectedPinCategory
                if (category == null) {
                    if (categories.isEmpty()) {
                        addEmpty("Belum ada teks yang disematkan.\nSematkan teks dari halaman Riwayat.")
                    } else {
                        categories.forEach { (name, categoryItems) ->
                            val count = categoryItems.size
                            val folderButton = Button(this).apply {
                                text = "📁  $name     ($count)"
                                textSize = 14f
                                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                                setTextColor(textColor)
                                setBackgroundColor(keyColor)
                                isAllCaps = false
                                minWidth = 0
                                minimumWidth = 0
                                setPadding(dp(12), dp(8), dp(12), dp(8))
                                setOnClickListener {
                                    selectedPinCategory = name
                                    setInputView(createKeyboardView())
                                }
                                setOnLongClickListener {
                                    val menu = PopupMenu(this@KeyboarizImeService, this)
                                    menu.menu.add("Ubah nama").setOnMenuItemClickListener {
                                        showRenameCategoryDialog(name)
                                        true
                                    }
                                    menu.menu.add("Hapus kategori").setOnMenuItemClickListener {
                                        deletePinCategory(name)
                                        setInputView(createKeyboardView())
                                        true
                                    }
                                    menu.show()
                                    true
                                }
                            }
                            val lp = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                            )
                            lp.setMargins(dp(2), dp(2), dp(2), dp(2))
                            itemsLayout.addView(folderButton, lp)
                        }
                    }
                    val addCategory = Button(this).apply {
                        text = "+ Buat kategori"
                        textSize = 14f
                        setTextColor(if (true) Color.rgb(4, 20, 29) else textColor)
                        setBackgroundColor(accentColor)
                        isAllCaps = false
                        setOnClickListener { showCreateCategoryDialog() }
                    }
                    val lp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dp(44)
                    )
                    lp.setMargins(dp(2), dp(4), dp(2), dp(2))
                    itemsLayout.addView(addCategory, lp)
                } else {
                    val back = Button(this).apply {
                        text = "‹  KATEGORI"
                        textSize = 13f
                        setTextColor(textColor)
                        setBackgroundColor(keyColor)
                        isAllCaps = false
                        setOnClickListener {
                            selectedPinCategory = null
                            setInputView(createKeyboardView())
                        }
                    }
                    itemsLayout.addView(back, LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dp(40)
                    ))
                    val entries = categories[category].orEmpty()
                    if (entries.isEmpty()) {
                        addEmpty("Kategori ini masih kosong.")
                    } else {
                        entries.forEach { addClipboardItem(it, true, category) }
                    }
                }
            }

            scrollView.addView(itemsLayout)
            scrollView.setOnTouchListener(object : View.OnTouchListener {
                private var downX = 0f
                private var downY = 0f
                override fun onTouch(v: View, event: MotionEvent): Boolean {
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            downX = event.x
                            downY = event.y
                        }
                        MotionEvent.ACTION_UP -> {
                            val dx = event.x - downX
                            val dy = event.y - downY
                            if (kotlin.math.abs(dx) > dp(70) && kotlin.math.abs(dx) > kotlin.math.abs(dy) * 1.4f) {
                                clipboardPage = if (dx < 0) 1 else 0
                                selectedPinCategory = null
                                setInputView(createKeyboardView())
                                return true
                            }
                        }
                    }
                    return false
                }
            })
            root.addView(scrollView, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            ))
            return root
        }

        makeToolButton("▤  Clipboard", 1f) {
            captureCurrentClipboard()
            clipboardMode = true
            setInputView(createKeyboardView())
        }
        root.addView(toolRow)

        fun addRow(keys: List<String>, weights: List<Float> = List(keys.size) { 1f }) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            keys.forEachIndexed { index, label ->
                val displayLabel = if (label == "↵") enterKeyLabel() else label
                val button = Button(this).apply {
                    text = when {
                        label == "⇧" && shiftState == 2 -> "⬆"
                        label == "⇧" -> "⇧"
                        label == "⌫" || label == "?123" -> label
                        label == "↵" -> displayLabel
                        label == "SPACE" -> "SPACE"
                        else -> if (shiftState > 0 && !numberMode) label.uppercase() else label
                    }
                    textSize = if (label == "SPACE") 12f else 18f
                    val isAccent = (label == "⇧" && shiftState > 0) || label == "↵"
                    setTextColor(if (isAccent) Color.rgb(4, 20, 29) else textColor)
                    setBackgroundColor(if (isAccent) accentColor else keyColor)
                    typeface = Typeface.DEFAULT
                    isAllCaps = false
                    minWidth = 0
                    minimumWidth = 0
                    setPadding(0, 0, 0, 0)
                    if (label == "↵") enterButton = this

                    if (label == "⌫") {
                        setOnClickListener { deleteOne() }
                        setOnLongClickListener {
                            if (!deleting) {
                                deleting = true
                                deleteOne()
                                handler.postDelayed(deleteRepeat, 350L)
                            }
                            true
                        }
                        setOnTouchListener { _, event ->
                            if (event.action == MotionEvent.ACTION_UP ||
                                event.action == MotionEvent.ACTION_CANCEL) stopDeleting()
                            false
                        }
                    } else {
                        setOnClickListener { handleKey(label) }
                    }
                }
                val params = LinearLayout.LayoutParams(0, dp(48), weights[index])
                params.setMargins(dp(2), dp(2), dp(2), dp(2))
                row.addView(button, params)
            }
            root.addView(row)
        }

        if (numberMode) {
            // Gboard-inspired number and symbol page.
            addRow(listOf("1","2","3","4","5","6","7","8","9","0"))
            addRow(listOf("@","#","$","%","&","-","+","(",")","/"))
            addRow(listOf("*","\"","'",":",";","!","?","_","⌫"),
                listOf(1f,1f,1f,1f,1f,1f,1f,1f,1.4f))
            addRow(listOf("ABC", ",", "SPACE", ".", "↵"),
                listOf(1.4f,0.8f,3.8f,0.8f,1.4f))
        } else {
            addRow(listOf("q","w","e","r","t","y","u","i","o","p"))
            addRow(listOf("a","s","d","f","g","h","j","k","l"))
            addRow(listOf("⇧","z","x","c","v","b","n","m","⌫"),
                listOf(1.3f,1f,1f,1f,1f,1f,1f,1f,1.3f))
            addRow(listOf("?123", ",", "SPACE", ".", "↵"),
                listOf(1.4f,0.8f,3.8f,0.8f,1.4f))
        }
        return root
    }

    private fun loadClipboardHistory(): List<String> {
        val raw = prefs.getString("clipboard_history", null) ?: return emptyList()
        return try {
            val json = JSONArray(raw)
            (0 until json.length()).mapNotNull { index ->
                json.optString(index).takeIf { it.isNotBlank() }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveClipboardHistory(items: List<String>) {
        val json = JSONArray()
        items.distinct().take(20).forEach { json.put(it) }
        prefs.edit().putString("clipboard_history", json.toString()).apply()
    }

    // Migrasi otomatis: sematan versi lama masuk ke kategori Belum Dikategorikan.
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
                    val values = (0 until array.length()).mapNotNull { i ->
                        array.optString(i).takeIf { it.isNotBlank() }
                    }.distinct()
                    result[name] = values
                }
                result
            } catch (_: Exception) {
                linkedMapOf()
            }
        }
        val legacy = loadLegacyPinnedClipboard()
        val migrated = linkedMapOf<String, List<String>>()
        if (legacy.isNotEmpty()) migrated["Belum Dikategorikan"] = legacy
        savePinCategories(migrated)
        prefs.edit().remove("clipboard_pinned").apply()
        return migrated
    }

    private fun loadLegacyPinnedClipboard(): List<String> {
        val raw = prefs.getString("clipboard_pinned", null) ?: return emptyList()
        return try {
            val json = JSONArray(raw)
            (0 until json.length()).mapNotNull { i ->
                json.optString(i).takeIf { it.isNotBlank() }
            }.distinct()
        } catch (_: Exception) {
            emptyList()
        }
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

    private fun allPinnedItems(): List<String> =
        loadPinCategories().values.flatten().distinct()

    private fun isPinned(item: String): Boolean = item in allPinnedItems()

    private fun addPinnedItem(item: String, category: String) {
        val categories = loadPinCategories()
        val updated = categories.mapValues { (_, values) -> values.filterNot { it == item } }.toMutableMap()
        updated[category] = (updated[category].orEmpty() + item).distinct()
        savePinCategories(updated)
        val history = loadClipboardHistory().toMutableList()
        history.remove(item)
        saveClipboardHistory(history)
    }

    private fun removePinnedItem(item: String) {
        val categories = loadPinCategories()
        val updated = categories.mapValues { (_, values) -> values.filterNot { it == item } }
            .filterValues { it.isNotEmpty() }
        savePinCategories(updated)
        val history = loadClipboardHistory().toMutableList()
        history.remove(item)
        history.add(0, item)
        saveClipboardHistory(history)
    }

    private fun showPinCategoryDialog(item: String) {
        val names = loadPinCategories().keys.toMutableList()
        val options = names + "＋ Buat kategori baru"
        AlertDialog.Builder(this)
            .setTitle("Pilih kategori")
            .setItems(options.toTypedArray()) { _, which ->
                if (which == options.lastIndex) {
                    showCreateCategoryDialog(item)
                } else {
                    addPinnedItem(item, names[which])
                    setInputView(createKeyboardView())
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showCreateCategoryDialog(itemToPin: String? = null) {
        val input = EditText(this).apply { hint = "Nama kategori" }
        AlertDialog.Builder(this)
            .setTitle("Buat kategori")
            .setView(input)
            .setPositiveButton("Simpan") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    val categories = loadPinCategories().toMutableMap()
                    val existingName = categories.keys.firstOrNull { it.equals(name, true) }
                    val targetName = existingName ?: name
                    if (existingName == null) {
                        categories[targetName] = emptyList()
                        savePinCategories(categories)
                    }
                    if (itemToPin != null) addPinnedItem(itemToPin, targetName)
                    setInputView(createKeyboardView())
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showMoveCategoryDialog(item: String, currentCategory: String?) {
        val names = loadPinCategories().keys.filter { it != currentCategory }
        if (names.isEmpty()) {
            showCreateCategoryDialog(item)
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Pindahkan ke kategori")
            .setItems(names.toTypedArray()) { _, which ->
                addPinnedItem(item, names[which])
                setInputView(createKeyboardView())
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showRenameCategoryDialog(oldName: String) {
        val input = EditText(this).apply { setText(oldName) }
        AlertDialog.Builder(this)
            .setTitle("Ubah nama kategori")
            .setView(input)
            .setPositiveButton("Simpan") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty() && newName != oldName) {
                    val categories = loadPinCategories().toMutableMap()
                    if (categories.keys.none { it.equals(newName, true) }) {
                        val items = categories.remove(oldName).orEmpty()
                        categories[newName] = items
                        savePinCategories(categories)
                    }
                    setInputView(createKeyboardView())
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun deletePinCategory(name: String) {
        val categories = loadPinCategories().toMutableMap()
        val removed = categories.remove(name).orEmpty()
        if (removed.isNotEmpty()) {
            categories["Belum Dikategorikan"] =
                (categories["Belum Dikategorikan"].orEmpty() + removed).distinct()
        }
        savePinCategories(categories)
        selectedPinCategory = null
    }

    private fun captureCurrentClipboard() {
        val clip = clipboardManager.primaryClip ?: return
        val description = clip.description
        if (description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN).not() &&
            description.hasMimeType("text/html").not()) return
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            description.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE, false) == true) return

        val text = clip.getItemAt(0).coerceToText(this)?.toString()?.trim()
            ?.takeIf { it.isNotEmpty() } ?: return
        if (isPinned(text)) return
        val history = loadClipboardHistory().toMutableList()
        history.remove(text)
        history.add(0, text)
        saveClipboardHistory(history)
    }

    private fun enterKeyLabel(): String {
        val action = currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
        return when (action) {
            EditorInfo.IME_ACTION_SEARCH -> "Cari"
            EditorInfo.IME_ACTION_GO -> "Go"
            EditorInfo.IME_ACTION_SEND -> "Kirim"
            // NEXT, DONE and ordinary Enter are all treated as newline.
            else -> "↵"
        }
    }

    private fun refreshEnterKey() {
        enterButton?.text = enterKeyLabel()
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        refreshEnterKey()
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        refreshEnterKey()
        // Ambil salinan terbaru saat keyboard dibuka kembali.
        captureCurrentClipboard()
    }

    private fun handleKey(key: String) {
        val connection = currentInputConnection ?: return
        when (key) {
            "⇧" -> {
                shiftState = when (shiftState) {
                    0 -> 1
                    1 -> 2
                    else -> 0
                }
                setInputView(createKeyboardView())
            }
            "↵" -> {
                val action = currentInputEditorInfo?.imeOptions
                    ?.and(EditorInfo.IME_MASK_ACTION)
                    ?: EditorInfo.IME_ACTION_NONE

                // Keep only explicit Search, Go and Send actions.
                // NEXT, DONE and all other actions insert a newline.
                when (action) {
                    EditorInfo.IME_ACTION_SEARCH,
                    EditorInfo.IME_ACTION_GO,
                    EditorInfo.IME_ACTION_SEND -> connection.performEditorAction(action)
                    else -> connection.commitText("\n", 1)
                }
            }
            "SPACE" -> connection.commitText(" ", 1)
            "?123" -> {
                numberMode = true
                setInputView(createKeyboardView())
            }
            "ABC" -> {
                numberMode = false
                setInputView(createKeyboardView())
            }
            else -> {
                connection.commitText(if (shiftState > 0 && !numberMode) key.uppercase() else key, 1)
                if (shiftState == 1 && !numberMode) {
                    shiftState = 0
                    setInputView(createKeyboardView())
                }
            }
        }
    }

    private fun deleteOne() {
        currentInputConnection?.deleteSurroundingText(1, 0)
    }

    private fun stopDeleting() {
        deleting = false
        handler.removeCallbacks(deleteRepeat)
    }

    override fun onFinishInput() {
        stopDeleting()
        super.onFinishInput()
    }

    override fun onDestroy() {
        stopDeleting()
        clipboardManager.removePrimaryClipChangedListener(clipboardListener)
        super.onDestroy()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
