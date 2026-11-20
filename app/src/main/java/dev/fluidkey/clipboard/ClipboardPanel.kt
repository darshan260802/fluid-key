package dev.fluidkey.clipboard

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.text.TextUtils
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import dev.fluidkey.R
import dev.fluidkey.ime.Ui
import dev.fluidkey.theme.KeyboardTheme

@SuppressLint("ViewConstructor")
class ClipboardPanel(
    context: Context,
    private val theme: KeyboardTheme,
    private val store: ClipboardStore,
    private val onPaste: (String) -> Unit,
    private val onClose: () -> Unit,
) : LinearLayout(context) {

    private val list = LinearLayout(context).apply { orientation = VERTICAL }

    init {
        orientation = VERTICAL
        setBackgroundColor(theme.background)
        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(Ui.textButton(context, theme, "ABC") { onClose() }, LayoutParams(LayoutParams.WRAP_CONTENT, Ui.dp(context, 40f)))
        header.addView(TextView(context).apply {
            text = context.getString(R.string.clipboard)
            setTextColor(theme.text)
            textSize = 15f
            gravity = Gravity.CENTER_VERTICAL
        }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        header.addView(
            Ui.iconButton(context, theme, R.drawable.ic_delete_sweep, context.getString(R.string.clear_clipboard)) {
                store.clearUnpinned()
                refresh()
            },
            LayoutParams(Ui.dp(context, 48f), Ui.dp(context, 40f)),
        )
        addView(header, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        val scroll = ScrollView(context).apply { isVerticalScrollBarEnabled = false }
        scroll.addView(list, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        refresh()
    }

    fun refresh() {
        list.removeAllViews()
        val items = store.items()
        val pad = Ui.dp(context, 8f)
        if (items.isEmpty()) {
            list.addView(TextView(context).apply {
                text = context.getString(R.string.clipboard_empty)
                setTextColor(theme.hint)
                gravity = Gravity.CENTER
                setPadding(pad * 2, pad * 4, pad * 2, pad * 4)
            }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            return
        }
        for (item in items) {
            val row = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = Ui.rounded(theme.key, Ui.dp(context, 10f).toFloat())
            }
            row.addView(TextView(context).apply {
                text = item.text
                maxLines = 3
                ellipsize = TextUtils.TruncateAt.END
                setTextColor(theme.text)
                textSize = 14f
                setPadding(pad + pad / 2, pad, pad, pad)
                background = Ui.ripple(theme, 10f, context)
                setOnClickListener { onPaste(item.text) }
            }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            row.addView(Ui.iconButton(context, theme, R.drawable.ic_pin, context.getString(R.string.pin)) {
                store.togglePin(item.text)
                refresh()
            }.apply {
                if (item.pinned) imageTintList = ColorStateList.valueOf(theme.accent)
            }, LayoutParams(Ui.dp(context, 40f), Ui.dp(context, 40f)))
            row.addView(Ui.iconButton(context, theme, R.drawable.ic_delete, context.getString(R.string.delete)) {
                store.remove(item.text)
                refresh()
            }, LayoutParams(Ui.dp(context, 40f), Ui.dp(context, 40f)))
            list.addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                setMargins(pad, pad / 2, pad, pad / 2)
            })
        }
    }
}
