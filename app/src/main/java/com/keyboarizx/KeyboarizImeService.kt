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
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray

class KeyboarizImeService : InputMethodService() {
    // 0 = lowercase, 1 = one-shot shift, 2 = caps lock
    private var shiftState = 0
    private var numberMode = false
    private var clipboardMode = false
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
            makeToolButton("CLIPBOARD", 3.2f) { }
            makeToolButton("✕", 0.7f) {
                clipboardMode = false
                setInputView(createKeyboardView())
            }

            root.addView(toolRow)
            val scrollView = ScrollView(this)
            val itemsLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(2), 0, dp(2))
            }
            val pinned = loadPinnedClipboard()
            val history = pinned + loadClipboardHistory().filterNot { it in pinned }
            if (history.isEmpty()) {
                val emptyText = TextView(this).apply {
                    text = "Belum ada teks tersimpan.\nSalin teks untuk menambahkannya ke Clipboard."
                    textSize = 14f
                    setTextColor(textColor)
                    gravity = Gravity.CENTER
                    setPadding(dp(16), dp(24), dp(16), dp(24))
                }
                itemsLayout.addView(emptyText, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ))
            } else {
                history.forEach { item ->
                    val itemButton = Button(this).apply {
                        text = (if (item in pinned) "📌  " else "") +
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
                            setInputView(createKeyboardView())
                        }
                        setOnLongClickListener {
                            val menu = PopupMenu(this@KeyboarizImeService, this)
                            if (item in loadPinnedClipboard()) {
                                menu.menu.add("Lepas sematan").setOnMenuItemClickListener {
                                    val updatedPinned = loadPinnedClipboard().toMutableList()
                                    updatedPinned.remove(item)
                                    savePinnedClipboard(updatedPinned)
                                    val updatedHistory = loadClipboardHistory().toMutableList()
                                    updatedHistory.remove(item)
                                    updatedHistory.add(0, item)
                                    saveClipboardHistory(updatedHistory)
                                    setInputView(createKeyboardView())
                                    true
                                }
                            } else {
                                menu.menu.add("Sematkan").setOnMenuItemClickListener {
                                    val updatedPinned = loadPinnedClipboard().toMutableList()
                                    updatedPinned.remove(item)
                                    updatedPinned.add(0, item)
                                    savePinnedClipboard(updatedPinned)
                                    val updatedHistory = loadClipboardHistory().toMutableList()
                                    updatedHistory.remove(item)
                                    saveClipboardHistory(updatedHistory)
                                    setInputView(createKeyboardView())
                                    true
                                }
                            }
                            menu.menu.add("Hapus").setOnMenuItemClickListener {
                                val updatedPinned = loadPinnedClipboard().toMutableList()
                                updatedPinned.remove(item)
                                savePinnedClipboard(updatedPinned)
                                val updatedHistory = loadClipboardHistory().toMutableList()
                                updatedHistory.remove(item)
                                saveClipboardHistory(updatedHistory)
                                setInputView(createKeyboardView())
                                true
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
            }
            scrollView.addView(itemsLayout)
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

    private fun loadPinnedClipboard(): List<String> {
        val raw = prefs.getString("clipboard_pinned", null) ?: return emptyList()
        return try {
            val json = JSONArray(raw)
            (0 until json.length()).mapNotNull { index ->
                json.optString(index).takeIf { it.isNotBlank() }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun savePinnedClipboard(items: List<String>) {
        val json = JSONArray()
        items.distinct().forEach { json.put(it) }
        prefs.edit().putString("clipboard_pinned", json.toString()).apply()
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
        if (text in loadPinnedClipboard()) return
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
