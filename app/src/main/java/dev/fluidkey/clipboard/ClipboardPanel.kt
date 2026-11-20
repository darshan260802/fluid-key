package dev.fluidkey.clipboard

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import dev.fluidkey.R
import dev.fluidkey.ime.Ui
import dev.fluidkey.theme.GlassRenderer

/** Clipboard history as glass cards in a two-column staggered grid. */
@SuppressLint("ViewConstructor")
class ClipboardPanel(
    context: Context,
    private val renderer: GlassRenderer,
    private val store: ClipboardStore,
    private val onPaste: (String) -> Unit,
    private val onClose: () -> Unit,
) : LinearLayout(context) {

    private val theme get() = renderer.theme
    private var items: List<ClipboardStore.Item> = emptyList()
    private val grid = RecyclerView(context)
    private val empty = LinearLayout(context)
    private val count = TextView(context)
    private val adapter = Adapter()

    init {
        orientation = VERTICAL
        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(context, 4f), 0, Ui.dp(context, 4f), 0)
        }
        header.addView(Ui.textButton(context, renderer, "ABC") { onClose() }, LayoutParams(LayoutParams.WRAP_CONTENT, Ui.dp(context, 44f)))
        val titles = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(Ui.dp(context, 8f), 0, 0, 0)
        }
        titles.addView(TextView(context).apply {
            text = context.getString(R.string.clipboard)
            setTextColor(theme.text)
            typeface = Ui.medium
            textSize = 16f
        })
        count.setTextColor(theme.hint)
        count.textSize = 11f
        titles.addView(count)
        header.addView(titles, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        header.addView(
            Ui.iconButton(context, renderer, R.drawable.ic_delete_sweep, context.getString(R.string.clear_clipboard)) {
                store.clearUnpinned()
                refresh()
            },
            LayoutParams(Ui.dp(context, 46f), Ui.dp(context, 46f)),
        )
        addView(header, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        val content = FrameLayout(context)
        grid.layoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
        grid.adapter = adapter
        grid.clipToPadding = false
        val pad = Ui.dp(context, 6f)
        grid.setPadding(pad, 0, pad, pad)
        grid.isVerticalScrollBarEnabled = false
        content.addView(grid, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        empty.orientation = VERTICAL
        empty.gravity = Gravity.CENTER
        empty.addView(ImageView(context).apply {
            setImageResource(R.drawable.ic_clipboard)
            imageTintList = ColorStateList.valueOf(theme.accent)
            alpha = 0.85f
            scaleX = 1.8f
            scaleY = 1.8f
            background = Ui.glass(context, renderer, 999f)
        }, LayoutParams(Ui.dp(context, 72f), Ui.dp(context, 72f)))
        empty.addView(TextView(context).apply {
            text = context.getString(R.string.clipboard_empty)
            setTextColor(theme.hint)
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(Ui.dp(context, 32f), Ui.dp(context, 14f), Ui.dp(context, 32f), 0)
        })
        content.addView(empty, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        addView(content, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        refresh()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun refresh() {
        items = store.items()
        adapter.notifyDataSetChanged()
        empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        val pinned = items.count { it.pinned }
        count.text = when {
            items.isEmpty() -> "Nothing copied yet"
            pinned > 0 -> "${plural(items.size)} · $pinned pinned"
            else -> plural(items.size)
        }
    }

    private fun plural(n: Int) = if (n == 1) "1 item" else "$n items"

    private class Holder(val card: LinearLayout, val text: TextView, val pin: ImageView, val delete: ImageView) : RecyclerView.ViewHolder(card)

    private inner class Adapter : RecyclerView.Adapter<Holder>() {
        override fun getItemCount() = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val ctx = parent.context
            val card = LinearLayout(ctx).apply {
                orientation = VERTICAL
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    val m = Ui.dp(ctx, 4f)
                    setMargins(m, m, m, m)
                }
                isClickable = true
            }
            val text = TextView(ctx).apply {
                maxLines = 5
                ellipsize = TextUtils.TruncateAt.END
                setTextColor(theme.text)
                textSize = 13.5f
                setLineSpacing(0f, 1.1f)
                val p = Ui.dp(ctx, 12f)
                setPadding(p, p, p, Ui.dp(ctx, 2f))
            }
            card.addView(text)
            val actions = LinearLayout(ctx).apply {
                orientation = HORIZONTAL
                gravity = Gravity.END
            }
            val pin = Ui.iconButton(ctx, renderer, R.drawable.ic_pin, ctx.getString(R.string.pin), chip = false) {}
            val delete = Ui.iconButton(ctx, renderer, R.drawable.ic_delete, ctx.getString(R.string.delete), chip = false) {}
            listOf(pin, delete).forEach {
                it.scaleX = 0.8f
                it.scaleY = 0.8f
                actions.addView(it, LayoutParams(Ui.dp(ctx, 36f), Ui.dp(ctx, 34f)))
            }
            card.addView(actions)
            return Holder(card, text, pin, delete)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val item = items[position]
            val ctx = holder.card.context
            holder.text.text = item.text
            val glass = Ui.glass(ctx, renderer, 16f)
            holder.card.background = if (item.pinned) {
                // Pinned cards get an accent hairline.
                LayerDrawable(arrayOf(glass, GradientDrawable().apply {
                    cornerRadius = Ui.dp(ctx, 16f).toFloat()
                    setStroke(Ui.dp(ctx, 1.5f), theme.accent)
                }))
            } else glass
            holder.pin.imageTintList = ColorStateList.valueOf(if (item.pinned) theme.accent else theme.hint)
            holder.delete.imageTintList = ColorStateList.valueOf(theme.hint)
            holder.card.setOnClickListener { onPaste(item.text) }
            holder.pin.setOnClickListener {
                store.togglePin(item.text)
                refresh()
            }
            holder.delete.setOnClickListener {
                store.remove(item.text)
                refresh()
            }
        }
    }
}
