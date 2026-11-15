package com.keyboarizx

import android.inputmethodservice.InputMethodService
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import java.util.Locale
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject

class KeyboarizImeService : InputMethodService() {
    // 0 = lowercase, 1 = one-shot shift, 2 = caps lock
    private var shiftState = 0
    private var lastSelectionForCase: String? = null
    private var selectionCaseMode = 0
    private var numberMode = false
    private var symbolPage = 1
    private var clipboardMode = false
    private var rizboardMode = false
    private var customMode = false
    private var clipboardPage = 0 // 0 = riwayat, 1 = sematan
    private var selectedPinCategory: String? = null
    private var pendingPinItem: String? = null
    private var pendingMoveFromCategory: String? = null
    private val prefs by lazy { getSharedPreferences("keyboard_settings", Context.MODE_PRIVATE) }
    private val handler = Handler(Looper.getMainLooper())
    private val clipboardManager by lazy {
        getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }
    private var deleting = false
    private var enterButton: Button? = null
    private var rizboardEnterButton: Button? = null
    private var lastRizboardInsertedText: String? = null

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
        val keyShape = prefs.getString("keyShape", "ROUNDED") ?: "ROUNDED"

        // Warna garis pembeda tiap kelompok tombol.
        val clipboardBorder = Color.rgb(57, 198, 232) // cyan
        val rizboardBorder = Color.rgb(167, 139, 250) // ungu
        val customBorder = Color.rgb(245, 163, 75) // oranye
        val letterBorder = Color.rgb(69, 184, 232) // biru muda
        val numberBorder = Color.rgb(231, 182, 83) // emas
        val functionBorder = Color.rgb(91, 205, 151) // hijau lembut

        // Aksen dibuat dari warna tombol + warna tulisan agar selalu selaras dengan tema aktif.
        val accentColor = blendThemeColors(keyColor, textColor, 0.28f)
        val accentTextColor = if (Color.luminance(accentColor) > 0.52f) {
            Color.rgb(20, 24, 30)
        } else {
            Color.WHITE
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(6), dp(4), dp(6))
            setBackgroundColor(backgroundColor)
        }

        val toolRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        fun makeToolButton(label: String, weight: Float, borderColor: Int = Color.rgb(90, 112, 130), onClick: () -> Unit): Button {
            val button = Button(this).apply {
                text = label
                textSize = 13f
                setTextColor(textColor)
                background = KeyShapeDrawable(keyColor, keyShape, resources.displayMetrics.density, borderColor = borderColor)
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
            return button
        }

        if (rizboardMode || customMode) {
            makeToolButton("HAPUS", 1.1f, Color.rgb(230, 105, 105)) { deleteLastRizboardText() }
            makeToolButton("SPASI", 1.1f, functionBorder) {
                currentInputConnection?.commitText(" ", 1)
            }
            rizboardEnterButton = makeToolButton(enterKeyLabel(), 1.25f, functionBorder) {
                performEnterAction()
            }
            makeToolButton("✕", 0.55f) {
                rizboardMode = false
                customMode = false
                setInputView(createKeyboardView())
            }
            root.addView(toolRow)

            val values = if (customMode) loadRizboardButtons() else listOf(
                "Brush212", "110188", "081094", "000", "11011988",
                "083819473139", "Brush1933", "==========================",
                ". can bisa login", ". kr aya otp", "@gmail.com", "1988"
            )
            val scroll = ScrollView(this)
            val content = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(2), 0, dp(2))
            }
            if (values.isEmpty()) {
                content.addView(TextView(this).apply {
                    text = "Belum ada tombol custom. Tambahkan melalui aplikasi KEYBOARIZ X."
                    textSize = 12f
                    setTextColor(textColor)
                    gravity = Gravity.CENTER
                    setPadding(dp(12), dp(18), dp(12), dp(18))
                })
            } else {
                values.chunked(3).forEach { group ->
                    val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                    group.forEach { value ->
                        val key = Button(this).apply {
                            text = if (value.length > 16) value.take(15) + "…" else value
                            textSize = 13f
                            isAllCaps = false
                            minWidth = 0
                            minimumWidth = 0
                            maxLines = 1
                            ellipsize = android.text.TextUtils.TruncateAt.END
                            setSingleLine(true)
                            setTextColor(textColor)
                            background = KeyShapeDrawable(
                                keyColor, keyShape, resources.displayMetrics.density,
                                borderColor = if (customMode) customBorder else rizboardBorder
                            )
                            setPadding(dp(3), 0, dp(3), 0)
                            setOnClickListener {
                                currentInputConnection?.commitText(value, 1)
                                lastRizboardInsertedText = value
                            }
                        }
                        row.addView(key, LinearLayout.LayoutParams(0, dp(46), 1f).apply {
                            setMargins(dp(2), dp(2), dp(2), dp(2))
                        })
                    }
                    repeat(3 - group.size) {
                        row.addView(View(this), LinearLayout.LayoutParams(0, dp(46), 1f))
                    }
                    content.addView(row)
                }
            }
            scroll.addView(content)
            // Isi 208dp + toolbar 40dp + padding root 12dp = total 260dp.
            root.addView(scroll, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(208)
            ))
            return root
        }

        if (clipboardMode) {
            makeToolButton("CLIPBOARD", 2.2f, clipboardBorder) { }
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
                    setTextColor(if (clipboardPage == page) accentTextColor else textColor)
                    background = KeyShapeDrawable(
                        if (clipboardPage == page) accentColor else keyColor,
                        keyShape, resources.displayMetrics.density, borderColor = clipboardBorder
                    )
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
                val itemButton = Button(this).apply {                    text = (if (pinnedItem) "📌  " else "") +
                        if (item.length > 180) item.take(180) + "…" else item
                    textSize = 14f
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    setTextColor(textColor)
                    background = KeyShapeDrawable(keyColor, keyShape, resources.displayMetrics.density, borderColor = clipboardBorder)
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
                                pendingPinItem = item
                                pendingMoveFromCategory = null
                                selectedPinCategory = null
                                clipboardPage = 1
                                setInputView(createKeyboardView())
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
                                pendingPinItem = item
                                pendingMoveFromCategory = category
                                selectedPinCategory = null
                                clipboardPage = 1
                                setInputView(createKeyboardView())
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
                val itemToPin = pendingPinItem
                if (itemToPin != null) {
                    addEmpty("PILIH KATEGORI")
                    categories.filterKeys { it != pendingMoveFromCategory }.forEach { (name, entries) ->
                        val choice = Button(this).apply {
                            text = "📁  $name  (${entries.size})"
                            textSize = 14f
                            gravity = Gravity.START or Gravity.CENTER_VERTICAL
                            setTextColor(textColor)
                            setBackgroundColor(keyColor)
                            isAllCaps = false
                            setOnClickListener {
                                addPinnedItem(itemToPin, name)
                                pendingPinItem = null
                                pendingMoveFromCategory = null
                                selectedPinCategory = null
                                setInputView(createKeyboardView())
                            }
                        }
                        itemsLayout.addView(choice, LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) })
                    }
                    val cancel = Button(this).apply {
                        text = "BATAL"
                        setTextColor(textColor)
                        setBackgroundColor(keyColor)
                        setOnClickListener {
                            pendingPinItem = null
                            pendingMoveFromCategory = null
                            setInputView(createKeyboardView())
                        }
                    }
                    itemsLayout.addView(cancel)
                    if (categories.keys.none { it != pendingMoveFromCategory }) {
                        addEmpty("Belum ada kategori tujuan. Buat kategori melalui aplikasi KEYBOARIZ X.")
                    }
                } else if (category == null) {
                    if (categories.isEmpty()) {
                        addEmpty("Belum ada teks yang disematkan.\\nSematkan teks dari halaman Riwayat.")
                    } else {
                        categories.forEach { (name, categoryItems) ->
                            val folderButton = Button(this).apply {
                                text = "📁  $name     (${categoryItems.size})"
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
                                    Toast.makeText(this@KeyboarizImeService,
                                        "Kelola kategori melalui aplikasi KEYBOARIZ X",
                                        Toast.LENGTH_SHORT).show()
                                    true
                                }
                            }
                            itemsLayout.addView(folderButton, LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) })
                        }
                    }
                    addEmpty("Buat dan atur kategori melalui aplikasi KEYBOARIZ X.\\nBuka aplikasi lalu pilih Kelola Kategori Sematan.")
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
                    if (entries.isEmpty()) addEmpty("Kategori ini masih kosong.")
                    else entries.forEach { addClipboardItem(it, true, category) }
                }
            }

            scrollView.addView(itemsLayout)
            // Tinggi daftar dibuat tetap agar ScrollView tidak mengisi seluruh tinggi layar IME.
            // 166dp + toolbar 40dp + tab 42dp + padding root 12dp = total 260dp.
            root.addView(scrollView, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(166)
            ))
            return root
        }

        // Number Pad halaman 3 tidak menampilkan shortcut Clipboard, RIZBOARD, dan CUSTOM.
        if (!(numberMode && symbolPage == 3)) {
            makeToolButton("▤ Clipboard", 1f, clipboardBorder) {
                captureCurrentClipboard()
                clipboardMode = true
                rizboardMode = false
                setInputView(createKeyboardView())
            }
            makeToolButton("RIZBOARD", 1f, rizboardBorder) {
                clipboardMode = false
                rizboardMode = true
                customMode = false
                setInputView(createKeyboardView())
            }
            makeToolButton("CUSTOM", 1f, customBorder) {
                clipboardMode = false
                rizboardMode = false
                customMode = true
                setInputView(createKeyboardView())
            }
            makeToolButton("1+2", 1f, numberBorder) {
                clipboardMode = false
                rizboardMode = false
                customMode = false
                numberMode = true
                symbolPage = 3
                setInputView(createKeyboardView())
            }
            root.addView(toolRow)
        }

        var keyboardRowIndex = 0
        fun addRow(keys: List<String>, weights: List<Float> = List(keys.size) { 1f }) {
            val staggeredRow = keyShape in listOf("LOVE", "HEXAGON", "DIAMOND") && keyboardRowIndex % 2 == 1
            val invertedLoveRow = keyShape == "LOVE" && staggeredRow
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                if (staggeredRow) setPadding(dp(12), 0, 0, 0)
            }
            keyboardRowIndex++
            keys.forEachIndexed { index, label ->
                val displayLabel = if (label == "↵") enterKeyLabel() else label
                val borderColor = when {
                    label == "SPACE" || label == "SPACE_ICON" -> letterBorder
                    label in listOf("⇧", "⌫", "↵", "BARIS_BARU", "?123", "ABC", "1+2", "=\\<") -> functionBorder
                    numberMode || label.length == 1 && label[0].isDigit() -> numberBorder
                    else -> letterBorder
                }
                val button = Button(this).apply {
                    text = when {
                        label == "⇧" && shiftState == 2 -> "⬆"
                        label == "⇧" -> "⇧"
                        label == "⌫" || label == "?123" -> label
                        label == "↵" -> displayLabel
                        label == "SPACE" && numberMode && symbolPage == 2 -> "⌨ KEYBOARIZ"
                        label == "SPACE_ICON" -> "␣"
                        label == "BARIS_BARU" -> "↵"
                        label == "SPACE" -> "SPACE"
                        else -> if (shiftState > 0 && !numberMode) label.uppercase() else label
                    }
                    textSize = when {
                        label == "SPACE" -> 10f
                        numberMode && symbolPage == 3 && label.length == 1 && label[0].isDigit() -> 26f
                        numberMode && symbolPage == 3 && label in listOf("+", "-", "*", "/", "%", "SPACE_ICON", "BARIS_BARU") -> 14f
                        numberMode && symbolPage == 3 -> 12f
                        else -> 18f
                    }
                    val isAccent = (label == "⇧" && shiftState > 0) || label == "↵" || label == "BARIS_BARU"
                    setTextColor(if (isAccent) accentTextColor else textColor)
                    background = KeyShapeDrawable(
                        if (isAccent) accentColor else keyColor,
                        keyShape,
                        resources.displayMetrics.density,
                        invertedLoveRow,
                        borderColor
                    )
                    typeface = Typeface.DEFAULT
                    isAllCaps = false
                    minWidth = 0
                    minimumWidth = 0
                    setPadding(0, 0, 0, 0)
                    if (label == "↵") enterButton = this

                    if (label == "⌫") {
                        // Hapus satu karakter saat dilepas; jika ditahan, mulai hapus berulang.
                        var backspacePointerDown = false
                        var backspaceLongPressed = false
                        val startLongDelete = Runnable {
                            if (backspacePointerDown && !backspaceLongPressed) {
                                backspaceLongPressed = true
                                deleting = true
                                deleteOne()
                                handler.postDelayed(deleteRepeat, 55L)
                            }
                        }
                        setOnTouchListener { _, event ->
                            when (event.actionMasked) {
                                MotionEvent.ACTION_DOWN -> {
                                    backspacePointerDown = true
                                    backspaceLongPressed = false
                                    handler.removeCallbacks(startLongDelete)
                                    handler.postDelayed(startLongDelete, 350L)
                                    true
                                }
                                MotionEvent.ACTION_UP -> {
                                    backspacePointerDown = false
                                    handler.removeCallbacks(startLongDelete)
                                    if (backspaceLongPressed) {
                                        stopDeleting()
                                    } else {
                                        deleteOne()
                                    }
                                    true
                                }
                                MotionEvent.ACTION_CANCEL -> {
                                    backspacePointerDown = false
                                    handler.removeCallbacks(startLongDelete)
                                    stopDeleting()
                                    true
                                }
                                else -> true
                            }
                        }
                    } else if (!numberMode && alternateCharacters.containsKey(label)) {
                        // Huruf beraksen: ketuk untuk huruf biasa, tahan untuk membuka pilihan.
                        var pointerDown = false
                        var alternatePopup: PopupWindow? = null
                        val showAlternates = Runnable {
                            if (pointerDown && alternatePopup == null) {
                                alternatePopup = showAlternateCharacters(this, label)
                                alternatePopup?.setOnDismissListener {
                                    alternatePopup = null
                                }
                            }
                        }
                        setOnTouchListener { _, event ->
                            when (event.actionMasked) {
                                MotionEvent.ACTION_DOWN -> {
                                    pointerDown = true
                                    handler.removeCallbacks(showAlternates)
                                    handler.postDelayed(showAlternates, 350L)
                                    true
                                }
                                MotionEvent.ACTION_UP -> {
                                    pointerDown = false
                                    handler.removeCallbacks(showAlternates)
                                    if (alternatePopup == null) handleKey(label)
                                    true
                                }
                                MotionEvent.ACTION_CANCEL -> {
                                    pointerDown = false
                                    handler.removeCallbacks(showAlternates)
                                    true
                                }
                                MotionEvent.ACTION_MOVE -> true
                                else -> true
                            }
                        }
                    } else {
                        // Proses tombol pada ACTION_DOWN agar input tidak menunggu jari diangkat.
                        setOnTouchListener { _, event ->
                            when (event.actionMasked) {
                                MotionEvent.ACTION_DOWN -> {
                                    if ((label == "1+2" || label == "1 2 3 4") && numberMode) {
                                        symbolPage = if (symbolPage == 3) 2 else 3
                                        setInputView(createKeyboardView())
                                    } else {
                                        handleKey(label)
                                    }
                                    true
                                }
                                MotionEvent.ACTION_MOVE,
                                MotionEvent.ACTION_UP,
                                MotionEvent.ACTION_CANCEL -> true
                                else -> true
                            }
                        }
                    }
                }
                val keyHeight = if (numberMode && symbolPage == 3) 58 else 48
                val params = LinearLayout.LayoutParams(0, dp(keyHeight), weights[index])
                // Hilangkan celah mati pada area sentuh: batas tombol saling berdempetan,
                // sementara inset drawable tetap menjaga jarak visual antar bentuk tombol.
                params.setMargins(0, dp(2), 0, dp(2))
                if (!numberMode && alternateCharacters.containsKey(label)) {
                    // Titik kecil menandai tombol yang memiliki karakter alternatif saat ditekan lama.
                    val keyContainer = FrameLayout(this)
                    keyContainer.addView(button, FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    ))
                    val indicator = View(this).apply {
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(Color.rgb(125, 190, 220))
                        }
                        // Tombol Android memiliki elevation bawaan; naikkan titik agar tampil di atas tombol.
                        elevation = dp(8).toFloat()
                        isClickable = false
                        isFocusable = false
                    }
                    keyContainer.addView(indicator, FrameLayout.LayoutParams(
                        dp(4), dp(4), Gravity.TOP or Gravity.END
                    ).apply {
                        setMargins(0, dp(6), dp(6), 0)
                    })
                    row.addView(keyContainer, params)
                } else {
                    row.addView(button, params)
                }
            }
            root.addView(row)
        }

        if (numberMode) {
            if (symbolPage == 1) {
                // Halaman simbol 1: pertahankan susunan yang sudah cocok dengan Gboard.
                addRow(listOf("1","2","3","4","5","6","7","8","9","0"))
                addRow(listOf("@","#","$","_","&","-","+","(",")","/"))
                addRow(listOf("=\\<","*","\"","'",":",";","!","?","⌫"),
                    listOf(1.25f,1f,1f,1f,1f,1f,1f,1f,1.4f))
                addRow(listOf("ABC", ",", "⚙", "SPACE", ".", "↵"),
                    listOf(1.3f,0.7f,1.2f,2.8f,0.7f,1.3f))
            } else if (symbolPage == 2) {
                // Halaman simbol 2: jangan ubah, ini susunan Gboard yang sudah disetujui.
                addRow(listOf("~","\u0060","|","•","√","π","÷","×","§","Δ"))
                addRow(listOf("£","¢","€","¥","^","°","=","{","}","\\"))
                addRow(listOf("=\\<","%","©","®","™","✓","[","]","⌫"),
                    listOf(1.25f,1f,1f,1f,1f,1f,1f,1f,1.4f))
                addRow(listOf("ABC", ",", "⚙", "SPACE", ".", "↵"),
                    listOf(1.3f,0.7f,1.2f,2.8f,0.7f,1.3f))
            } else {
                // Halaman 3: Number Pad ala Gboard.
                // Tombol angka dibuat besar; operator dan tombol fungsi lebih kecil.
                // Angka 0 diposisikan tepat di bawah angka 8.
                addRow(listOf("+","1","2","3","%"), listOf(0.6f,1.2f,1.2f,1.2f,0.6f))
                addRow(listOf("-","4","5","6","SPACE_ICON"), listOf(0.6f,1.2f,1.2f,1.2f,0.6f))
                addRow(listOf("*","7","8","9","⌫"), listOf(0.6f,1.2f,1.2f,1.2f,0.6f))
                addRow(listOf("/","ABC",",","1+2","0","=",".","BARIS_BARU"),
                    listOf(0.45f,0.8f,0.45f,0.8f,1.2f,0.9f,0.45f,1.15f))
            }
        } else {
            addRow(listOf("q","w","e","r","t","y","u","i","o","p"))
            addRow(listOf("a","s","d","f","g","h","j","k","l"))
            addRow(listOf("⇧","z","x","c","v","b","n","m","⌫"),
                listOf(1.3f,1f,1f,1f,1f,1f,1f,1f,1.3f))
            addRow(listOf("?123", ",", "SPACE", ".", "↵"),
                listOf(1.4f,0.8f,3.8f,0.8f,1.4f))
        }
        // Kolibri hanya tampil pada keyboard huruf utama; mode khusus tetap bersih.
        return if (!numberMode) withHummingbirdOverlay(root) else root
    }

    private fun withHummingbirdOverlay(keyboard: View): View {
        return FrameLayout(this).apply {
            clipChildren = false
            clipToPadding = false
            addView(keyboard, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ))
            addView(HummingbirdOverlayView(this@KeyboarizImeService),
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
        }
    }

    private fun loadRizboardButtons(): List<String> {
        val raw = prefs.getString("rizboard_custom_buttons", null) ?: return emptyList()
        return try {
            val json = JSONArray(raw)
            (0 until json.length()).mapNotNull { i -> json.optString(i).takeIf { it.isNotEmpty() } }.distinct()
        } catch (_: Exception) { emptyList() }
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
        savePinCategories(updated)
        val history = loadClipboardHistory().toMutableList()
        history.remove(item)
        history.add(0, item)
        saveClipboardHistory(history)
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
        val label = enterKeyLabel()
        enterButton?.text = label
        rizboardEnterButton?.text = label
    }

    private fun performEnterAction() {
        val connection = currentInputConnection ?: return
        val action = currentInputEditorInfo?.imeOptions
            ?.and(EditorInfo.IME_MASK_ACTION)
            ?: EditorInfo.IME_ACTION_NONE

        // Samakan perilaku tombol Enter utama dan tombol Enter di RIZBOARD.
        when (action) {
            EditorInfo.IME_ACTION_SEARCH,
            EditorInfo.IME_ACTION_GO,
            EditorInfo.IME_ACTION_SEND -> connection.performEditorAction(action)
            else -> connection.commitText("\n", 1)
        }
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

    private val alternateCharacters = mapOf(
        "e" to listOf("é", "è", "ê", "ë", "ĕ", "ę"),
        "t" to listOf("ţ", "ț"),
        "y" to listOf("ÿ"),
        "u" to listOf("ű", "ú", "ü", "û", "ù"),
        "i" to listOf("ł", "í", "ì", "î", "ï"),
        "o" to listOf("ő", "õ", "ó", "ò", "ö", "ô"),
        "a" to listOf("à", "â", "ä", "á", "ă", "ą"),
        "s" to listOf("š", "ś", "ş", "ș", "ß"),
        "d" to listOf("đ"),
        "z" to listOf("ž", "ź", "ż"),
        "c" to listOf("č", "ć", "ç"),
        "n" to listOf("ń", "ñ")
    )

    private fun showAlternateCharacters(anchor: View, baseKey: String): PopupWindow? {
        val options = alternateCharacters[baseKey] ?: return null
        val textColor = prefs.getInt("textColor", Color.WHITE)
        val keyColor = prefs.getInt("keyColor", Color.rgb(37, 55, 70))
        val keyShape = prefs.getString("keyShape", "ROUNDED") ?: "ROUNDED"

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
            background = KeyShapeDrawable(
                keyColor, keyShape, resources.displayMetrics.density,
                borderColor = Color.rgb(69, 184, 232)
            )
        }
        lateinit var popup: PopupWindow
        options.forEach { character ->
            val key = TextView(this).apply {
                text = if (shiftState > 0) character.uppercase(Locale.getDefault()) else character
                textSize = 18f
                gravity = Gravity.CENTER
                setTextColor(textColor)
                minWidth = dp(38)
                setPadding(dp(5), 0, dp(5), 0)
                setOnClickListener {
                    val output = if (shiftState > 0) character.uppercase(Locale.getDefault()) else character
                    currentInputConnection?.commitText(output, 1)
                    if (shiftState == 1) {
                        shiftState = 0
                        setInputView(createKeyboardView())
                    }
                    popup.dismiss()
                }
            }
            row.addView(key, LinearLayout.LayoutParams(dp(40), dp(42)))
        }

        popup = PopupWindow(
            row,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            elevation = dp(8).toFloat()
            isOutsideTouchable = true
        }
        row.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val xOffset = (anchor.width - row.measuredWidth) / 2
        popup.showAsDropDown(anchor, xOffset, -dp(98))
        return popup
    }

    private fun handleKey(key: String) {
        val connection = currentInputConnection ?: return
        when (key) {
            "⇧" -> {
                val selected = connection.getSelectedText(0)?.toString()
                if (!selected.isNullOrEmpty()) {
                    if (selected != lastSelectionForCase) {
                        selectionCaseMode = 0
                    }
                    selectionCaseMode = (selectionCaseMode % 3) + 1
                    val transformed = when (selectionCaseMode) {
                        1 -> titleCaseWords(selected)
                        2 -> selected.uppercase(Locale.getDefault())
                        else -> selected.lowercase(Locale.getDefault())
                    }
                    replaceSelectionKeepingIt(transformed)
                    lastSelectionForCase = transformed
                    shiftState = 0
                } else {
                    lastSelectionForCase = null
                    selectionCaseMode = 0
                    shiftState = when (shiftState) {
                        0 -> 1
                        1 -> 2
                        else -> 0
                    }
                }
                setInputView(createKeyboardView())
            }
            "↵" -> performEnterAction()
            "SPACE", "SPACE_ICON" -> connection.commitText(" ", 1)
            "BARIS_BARU" -> connection.commitText("\n", 1)
            "?123" -> {
                if (numberMode && symbolPage == 2) {
                    symbolPage = 1
                } else {
                    numberMode = true
                    symbolPage = 1
                }
                setInputView(createKeyboardView())
            }
            "=\\<" -> {
                if (numberMode) {
                    symbolPage = when (symbolPage) {
                        1 -> 2
                        2 -> 1
                        else -> 2
                    }
                    setInputView(createKeyboardView())
                } else {
                    connection.commitText(key, 1)
                }
            }
            "⚙" -> {
                val settingsIntent = packageManager.getLaunchIntentForPackage(packageName)
                settingsIntent?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                if (settingsIntent != null) {
                    startActivity(settingsIntent)
                } else {
                    Toast.makeText(this, "Pengaturan KEYBOARIZ X tidak ditemukan", Toast.LENGTH_SHORT).show()
                }
            }
            "1+2" -> {                if (numberMode && symbolPage == 2) {
                    symbolPage = 3
                    setInputView(createKeyboardView())
                }
            }
            "!?#" -> {
                numberMode = true
                symbolPage = 2
                setInputView(createKeyboardView())
            }
            "ABC" -> {
                numberMode = false
                symbolPage = 1
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

    private fun deleteLastRizboardText() {
        val connection = currentInputConnection ?: return
        val lastText = lastRizboardInsertedText
        if (lastText.isNullOrEmpty()) {
            Toast.makeText(this, "Belum ada teks RIZBOARD/CUSTOM yang dimasukkan.", Toast.LENGTH_SHORT).show()
            return
        }

        val beforeCursor = connection.getTextBeforeCursor(lastText.length, 0)?.toString().orEmpty()
        if (beforeCursor.endsWith(lastText)) {
            connection.deleteSurroundingText(lastText.length, 0)
            lastRizboardInsertedText = null
        } else {
            Toast.makeText(this, "Teks terakhir tidak tepat di belakang kursor.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun deleteOne() {
        val connection = currentInputConnection ?: return
        val selected = connection.getSelectedText(0)
        if (!selected.isNullOrEmpty()) {
            connection.commitText("", 1)
            lastSelectionForCase = null
            selectionCaseMode = 0
        } else {
            connection.deleteSurroundingText(1, 0)
        }
    }

    private fun titleCaseWords(value: String): String {
        val locale = Locale.getDefault()
        return Regex("\\S+").replace(value) { match ->
            val word = match.value
            val firstLetter = word.indexOfFirst { it.isLetter() }
            if (firstLetter < 0) word
            else word.substring(0, firstLetter) +
                word[firstLetter].toString().uppercase(locale) +
                word.substring(firstLetter + 1).lowercase(locale)
        }
    }

    private fun replaceSelectionKeepingIt(replacement: String) {
        val connection = currentInputConnection ?: return
        val extracted = connection.getExtractedText(ExtractedTextRequest(), 0)
        val selectionStart = extracted?.selectionStart ?: -1
        val selectionEnd = extracted?.selectionEnd ?: -1
        connection.beginBatchEdit()
        try {
            connection.commitText(replacement, 1)
            if (selectionStart >= 0 && selectionEnd >= 0) {
                val selectionAnchor = minOf(selectionStart, selectionEnd)
                connection.setSelection(selectionAnchor, selectionAnchor + replacement.length)
            }
        } finally {
            connection.endBatchEdit()
        }
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

    private fun blendThemeColors(base: Int, tint: Int, tintAmount: Float): Int {
        val amount = tintAmount.coerceIn(0f, 1f)
        val inverse = 1f - amount
        return Color.rgb(
            (Color.red(base) * inverse + Color.red(tint) * amount).toInt(),
            (Color.green(base) * inverse + Color.green(tint) * amount).toInt(),
            (Color.blue(base) * inverse + Color.blue(tint) * amount).toInt()
        )
    }
}

/**
 * A lightweight, touch-through hummingbird animation drawn directly on Canvas.
 * It uses no image assets, network access, or continuous background work when detached.
 */
private class HummingbirdOverlayView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val wingPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val darkPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shinePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val throatPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val beakPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val body = RectF()
    private val wing = Path()
    private val tail = Path()
    private val startTime = android.os.SystemClock.uptimeMillis()
    private val frame = object : Runnable {
        override fun run() {
            if (!isAttachedToWindow) return
            invalidate()
            postDelayed(this, 34L)
        }
    }

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        setWillNotDraw(false)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        removeCallbacks(frame)
        post(frame)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(frame)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return

        val elapsed = (android.os.SystemClock.uptimeMillis() - startTime) % 12800L
        val birdWidth = 35f * density
        val birdHeight = 25f * density
        val firstFlight = 3400L
        val firstHoverEnd = 5000L
        val secondFlightEnd = 8400L
        val secondHoverEnd = 9900L

        val x: Float
        val direction: Float
        when {
            elapsed < firstFlight -> {
                val p = elapsed / firstFlight.toFloat()
                x = -birdWidth + (width * 0.34f + birdWidth) * p
                direction = 1f
            }
            elapsed < firstHoverEnd -> {
                x = width * 0.34f
                direction = 1f
            }
            elapsed < secondFlightEnd -> {
                val p = (elapsed - firstHoverEnd) / (secondFlightEnd - firstHoverEnd).toFloat()
                x = width * 0.34f + (width * 0.72f) * p
                direction = 1f
            }
            elapsed < secondHoverEnd -> {
                x = width * 1.06f
                direction = 1f
            }
            else -> {
                val p = (elapsed - secondHoverEnd) / (12800L - secondHoverEnd).toFloat()
                x = width * 1.06f - (width + birdWidth * 0.2f) * p
                direction = -1f
            }
        }

        val flap = kotlin.math.sin(elapsed / 42.0).toFloat()
        val hover = kotlin.math.sin(elapsed / 175.0).toFloat() * 2.0f * density
        // Jalur terbang berada di area huruf, sedikit di atas pusat baris tombol.
        val y = height * 0.57f + hover

        canvas.save()
        canvas.translate(x, y)
        if (direction < 0f) canvas.scale(-1f, 1f)

        // Badan mungil hijau zamrud dengan gradasi sederhana.
        bodyPaint.shader = android.graphics.LinearGradient(
            0f, -birdHeight * 0.35f, 0f, birdHeight * 0.55f,
            intArrayOf(Color.rgb(80, 190, 133), Color.rgb(20, 105, 78), Color.rgb(8, 57, 52)),
            null, android.graphics.Shader.TileMode.CLAMP
        )
        body.set(-birdWidth * 0.31f, -birdHeight * 0.24f,
            birdWidth * 0.28f, birdHeight * 0.25f)
        canvas.drawOval(body, bodyPaint)
        bodyPaint.shader = null

        // Dada berkilau dan kepala.
        throatPaint.color = Color.rgb(70, 220, 170)
        body.set(-birdWidth * 0.12f, -birdHeight * 0.02f,
            birdWidth * 0.17f, birdHeight * 0.20f)
        canvas.drawOval(body, throatPaint)

        bodyPaint.color = Color.rgb(24, 142, 99)
        canvas.drawCircle(birdWidth * 0.18f, -birdHeight * 0.24f, birdHeight * 0.23f, bodyPaint)

        // Sayap mengepak cepat; sudut berubah tiap frame.
        wing.reset()
        val wingLift = flap * birdHeight * 0.42f
        wing.moveTo(-birdWidth * 0.02f, -birdHeight * 0.08f)
        wing.cubicTo(-birdWidth * 0.23f, -birdHeight * 0.52f - wingLift,
            -birdWidth * 0.48f, -birdHeight * 0.55f - wingLift,
            -birdWidth * 0.34f, -birdHeight * 0.03f)
        wing.cubicTo(-birdWidth * 0.24f, birdHeight * 0.04f,
            -birdWidth * 0.10f, birdHeight * 0.02f,
            -birdWidth * 0.02f, -birdHeight * 0.08f)
        wing.close()
        wingPaint.color = Color.argb(210, 102, 194, 164)
        canvas.drawPath(wing, wingPaint)

        // Ekor runcing.
        tail.reset()
        tail.moveTo(-birdWidth * 0.25f, birdHeight * 0.03f)
        tail.lineTo(-birdWidth * 0.55f, birdHeight * 0.27f)
        tail.lineTo(-birdWidth * 0.30f, birdHeight * 0.17f)
        tail.lineTo(-birdWidth * 0.18f, birdHeight * 0.12f)
        tail.close()
        darkPaint.color = Color.rgb(18, 91, 72)
        canvas.drawPath(tail, darkPaint)

        // Paruh panjang khas kolibri.
        beakPaint.color = Color.rgb(35, 38, 34)
        beakPaint.strokeWidth = 1.7f * density
        beakPaint.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(birdWidth * 0.32f, -birdHeight * 0.22f,
            birdWidth * 0.67f, -birdHeight * 0.17f, beakPaint)

        // Mata kecil dan kilau bulu.
        darkPaint.color = Color.rgb(12, 25, 22)
        canvas.drawCircle(birdWidth * 0.23f, -birdHeight * 0.29f, 1.35f * density, darkPaint)
        shinePaint.color = Color.argb(190, 220, 255, 235)
        canvas.drawCircle(birdWidth * 0.24f, -birdHeight * 0.31f, 0.48f * density, shinePaint)
        canvas.drawLine(-birdWidth * 0.18f, -birdHeight * 0.12f,
            birdWidth * 0.02f, -birdHeight * 0.16f, shinePaint)

        canvas.restore()
    }
}

/**
 * Draws keyboard key shapes while keeping the actual Android Button view as the touch target.
 * For staggered styles the whole row (and therefore its touch targets) is shifted together.
 */
private class KeyShapeDrawable(
    private val fillColor: Int,
    private val shape: String,
    private val density: Float,
    private val inverted: Boolean = false,
    private val borderColor: Int? = null
) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    override fun draw(canvas: Canvas) {
        val b = bounds
        val inset = 1.5f * density
        rect.set(
            b.left + inset,
            b.top + inset,
            b.right - inset,
            b.bottom - inset
        )
        if (rect.width() <= 0f || rect.height() <= 0f) return

        paint.color = fillColor
        paint.style = Paint.Style.FILL
        val radius = when (shape) {
            "PILL" -> rect.height() / 2f
            "SOFT_SQUARE" -> 9f * density
            "ROUNDED" -> 6f * density
            "DOUBLE_BORDER" -> 7f * density
            else -> 3f * density
        }

        when (shape) {
            "CIRCLE", "OVAL" -> canvas.drawOval(rect, paint)
            "HEXAGON" -> canvas.drawPath(polygonPath(
                listOf(
                    0.25f to 0f, 0.75f to 0f, 1f to 0.5f,
                    0.75f to 1f, 0.25f to 1f, 0f to 0.5f
                )
            ), paint)
            "DIAMOND" -> canvas.drawPath(polygonPath(
                listOf(0.5f to 0f, 1f to 0.5f, 0.5f to 1f, 0f to 0.5f)
            ), paint)
            "LOVE" -> {
                val path = Path()
                val w = rect.width()
                val h = rect.height()
                path.moveTo(rect.left + w * 0.5f, rect.top + h * 0.96f)
                path.cubicTo(rect.left + w * 0.39f, rect.top + h * 0.82f,
                    rect.left + w * 0.02f, rect.top + h * 0.56f,
                    rect.left + w * 0.02f, rect.top + h * 0.29f)
                path.cubicTo(rect.left + w * 0.02f, rect.top + h * 0.02f,
                    rect.left + w * 0.34f, rect.top - h * 0.04f,
                    rect.left + w * 0.5f, rect.top + h * 0.25f)
                path.cubicTo(rect.left + w * 0.66f, rect.top - h * 0.04f,
                    rect.left + w * 0.98f, rect.top + h * 0.02f,
                    rect.left + w * 0.98f, rect.top + h * 0.29f)
                path.cubicTo(rect.left + w * 0.98f, rect.top + h * 0.56f,
                    rect.left + w * 0.61f, rect.top + h * 0.82f,
                    rect.left + w * 0.5f, rect.top + h * 0.96f)
                path.close()
                if (inverted) {
                    val matrix = android.graphics.Matrix().apply {
                        setRotate(180f, rect.centerX(), rect.centerY())
                    }
                    path.transform(matrix)
                }
                canvas.drawPath(path, paint)
            }
            "DOUBLE_BORDER" -> {
                canvas.drawRoundRect(rect, radius, radius, paint)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.2f * density
                paint.color = Color.argb(190, 255, 255, 255)
                val inner = RectF(rect).apply { inset(3f * density, 3f * density) }
                canvas.drawRoundRect(inner, radius * 0.65f, radius * 0.65f, paint)
            }
            "PILL", "ROUNDED", "SOFT_SQUARE" -> canvas.drawRoundRect(rect, radius, radius, paint)
            else -> canvas.drawRoundRect(rect, radius, radius, paint)
        }

        // Garis luar tipis berwarna; isi tombol tetap memakai warna tema semula.
        borderColor?.let { color ->
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.25f * density
            paint.color = color
            val outline = RectF(rect).apply { inset(0.8f * density, 0.8f * density) }
            canvas.drawRoundRect(outline, radius, radius, paint)
            paint.style = Paint.Style.FILL
        }
    }

    private fun polygonPath(points: List<Pair<Float, Float>>): Path {
        return Path().apply {
            points.forEachIndexed { index, point ->
                val x = rect.left + rect.width() * point.first
                val y = rect.top + rect.height() * point.second
                if (index == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()        }
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
