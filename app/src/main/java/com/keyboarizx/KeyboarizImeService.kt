package com.keyboarizx

import android.inputmethodservice.InputMethodService
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.LinearLayout

class KeyboarizImeService : InputMethodService() {
    private var caps = false
    private val prefs by lazy { getSharedPreferences("keyboard_settings", Context.MODE_PRIVATE) }

    override fun onCreateInputView(): View {
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
                val button = Button(this).apply {
                    text = if (label == "⇧" || label == "⌫" || label == "?123" || label == "↵") label else if (caps) label.uppercase() else label
                    textSize = if (label == "SPACE") 12f else 18f
                    setTextColor(if (label == "⇧" || label == "↵") Color.rgb(4, 20, 29) else textColor)
                    setBackgroundColor(if (label == "⇧" || label == "↵") Color.rgb(0, 207, 255) else keyColor)
                    typeface = Typeface.DEFAULT
                    isAllCaps = false
                    minWidth = 0
                    minimumWidth = 0
                    setPadding(0, 0, 0, 0)
                    setOnClickListener { handleKey(label) }
                }
                val params = LinearLayout.LayoutParams(0, dp(48), weights[index])
                params.setMargins(dp(2), dp(2), dp(2), dp(2))
                row.addView(button, params)
            }
            root.addView(row)
        }

        addRow(listOf("q","w","e","r","t","y","u","i","o","p"))
        addRow(listOf("a","s","d","f","g","h","j","k","l"))
        addRow(listOf("⇧","z","x","c","v","b","n","m","⌫"), listOf(1.3f,1f,1f,1f,1f,1f,1f,1f,1.3f))
        addRow(listOf("?123", ",", "SPACE", ".", "↵"), listOf(1.4f,0.8f,3.8f,0.8f,1.4f))
        return root
    }

    private fun handleKey(key: String) {
        val connection = currentInputConnection ?: return
        when (key) {
            "⇧" -> { caps = !caps; setInputView(onCreateInputView()) }
            "⌫" -> connection.deleteSurroundingText(1, 0)
            "↵" -> {
                val action = currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE
                if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) connection.performEditorAction(action)
                else connection.commitText("\n", 1)
            }
            "SPACE" -> connection.commitText(" ", 1)
            "?123" -> connection.commitText("", 1)
            else -> connection.commitText(if (caps) key.uppercase() else key, 1)
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
