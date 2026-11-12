package com.keyboarizx

import android.inputmethodservice.InputMethodService
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.text.InputType
import android.widget.Button
import android.widget.LinearLayout

class KeyboarizImeService : InputMethodService() {
    // 0 = lowercase, 1 = one-shot shift, 2 = caps lock
    private var shiftState = 0
    private val prefs by lazy { getSharedPreferences("keyboard_settings", Context.MODE_PRIVATE) }
    private val handler = Handler(Looper.getMainLooper())
    private var deleting = false
    private var enterButton: Button? = null
    private val deleteRepeat = object : Runnable {
        override fun run() {
            if (!deleting) return
            deleteOne()
            handler.postDelayed(this, 55L)
        }
    }

    override fun onCreateInputView(): View = createKeyboardView()

    private fun createKeyboardView(): View {
        val textColor = prefs.getInt("textColor", Color.WHITE)
        val keyColor = prefs.getInt("keyColor", Color.rgb(37, 55, 70))
        val backgroundColor = prefs.getInt("backgroundColor", Color.rgb(17, 28, 39))
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(6), dp(4), dp(6))
            setBackgroundColor(backgroundColor)
        }

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
                        else -> if (shiftState > 0) label.uppercase() else label
                    }
                    textSize = if (label == "SPACE") 12f else 18f
                    val isAccent = (label == "⇧" && shiftState > 0) || label == "↵"
                    setTextColor(if (isAccent) Color.rgb(4, 20, 29) else textColor)
                    setBackgroundColor(if (isAccent) Color.rgb(0, 207, 255) else keyColor)
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

        addRow(listOf("q","w","e","r","t","y","u","i","o","p"))
        addRow(listOf("a","s","d","f","g","h","j","k","l"))
        addRow(listOf("⇧","z","x","c","v","b","n","m","⌫"),
            listOf(1.3f,1f,1f,1f,1f,1f,1f,1f,1.3f))
        addRow(listOf("?123", ",", "SPACE", ".", "↵"),
            listOf(1.4f,0.8f,3.8f,0.8f,1.4f))
        return root
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
            "?123" -> { /* Number/symbol layout will be added in the next stage. */ }
            else -> {
                connection.commitText(if (shiftState > 0) key.uppercase() else key, 1)
                if (shiftState == 1) {
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
        super.onDestroy()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
