package dev.fluidkey.emoji

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Paint
import android.view.Gravity
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dev.fluidkey.R
import dev.fluidkey.ime.Ui
import dev.fluidkey.theme.KeyboardTheme

/** Emoji categories loaded from assets/emoji.txt (generated from Unicode emoji data). */
object EmojiData {
    class Category(val name: String, val tab: String, val emojis: List<String>)

    @Volatile
    private var cache: List<Category>? = null

    private val tabs = mapOf(
        "Smileys & Emotion" to "😀", "People & Body" to "👋", "Animals & Nature" to "🐻",
        "Food & Drink" to "🍔", "Travel & Places" to "🚗", "Activities" to "⚽",
        "Objects" to "💡", "Symbols" to "💕", "Flags" to "🏁",
    )

    fun load(context: Context): List<Category> {
        cache?.let { return it }
        val paint = Paint()
        val result = context.assets.open("emoji.txt").bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.mapNotNull { line ->
                val tab = line.indexOf('\t')
                if (tab < 0) return@mapNotNull null
                val name = line.substring(0, tab)
                // Drop emoji the device's font cannot draw (newer Unicode on older Android).
                val emojis = line.substring(tab + 1).split(' ').filter { it.isNotEmpty() && paint.hasGlyph(it) }
                Category(name, tabs[name] ?: emojis.firstOrNull() ?: "?", emojis)
            }.toList()
        }
        cache = result
        return result
    }
}

@SuppressLint("ViewConstructor")
class EmojiPanel(
    context: Context,
    private val theme: KeyboardTheme,
    private val data: SharedPreferences,
    private val onEmoji: (String) -> Unit,
    private val onDelete: () -> Unit,
    private val onClose: () -> Unit,
) : LinearLayout(context) {

    private class Entry(val text: String, val header: Boolean)

    private val entries = ArrayList<Entry>()
    private val sectionStarts = ArrayList<Int>()
    private val tabViews = ArrayList<TextView>()
    private val grid = RecyclerView(context)
    private val layoutManager = GridLayoutManager(context, COLUMNS)
    private var selectedTab = -1

    init {
        orientation = VERTICAL
        setBackgroundColor(theme.background)
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int) = if (entries[position].header) COLUMNS else 1
        }
        grid.layoutManager = layoutManager
        grid.adapter = Adapter()
        grid.isVerticalScrollBarEnabled = false
        grid.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                val first = layoutManager.findFirstVisibleItemPosition()
                val idx = sectionStarts.indexOfLast { it <= first }
                if (idx >= 0) selectTab(idx)
            }
        })
        addView(grid, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        val bar = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        bar.addView(Ui.textButton(context, theme, "ABC") { onClose() }, LayoutParams(LayoutParams.WRAP_CONTENT, Ui.dp(context, 44f)))
        val tabRow = LinearLayout(context).apply { orientation = HORIZONTAL }
        val tabScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(tabRow)
        }
        bar.addView(tabScroll, LayoutParams(0, Ui.dp(context, 44f), 1f))
        bar.addView(
            Ui.iconButton(context, theme, R.drawable.ic_backspace, context.getString(R.string.delete)) { onDelete() },
            LayoutParams(Ui.dp(context, 52f), Ui.dp(context, 44f)),
        )
        addView(bar, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        build(tabRow)
    }

    private fun recents(): List<String> =
        data.getString(KEY_RECENTS, "")!!.split('\n').filter { it.isNotEmpty() }

    private fun build(tabRow: LinearLayout) {
        val cats = ArrayList<EmojiData.Category>()
        cats += EmojiData.Category(context.getString(R.string.recent), "🕘", recents())
        cats += EmojiData.load(context)
        for ((i, cat) in cats.withIndex()) {
            sectionStarts += entries.size
            entries += Entry(cat.name, true)
            if (cat.emojis.isEmpty() && i == 0) entries += Entry("", false)
            cat.emojis.forEach { entries += Entry(it, false) }
            val tab = TextView(context).apply {
                text = cat.tab
                textSize = 20f
                gravity = Gravity.CENTER
                background = Ui.ripple(theme)
                setOnClickListener { layoutManager.scrollToPositionWithOffset(sectionStarts[i], 0); selectTab(i) }
            }
            tabViews += tab
            tabRow.addView(tab, LayoutParams(Ui.dp(context, 46f), Ui.dp(context, 44f)))
        }
        // Start on smileys if there are no recents yet.
        val start = if (recents().isEmpty()) 1 else 0
        layoutManager.scrollToPositionWithOffset(sectionStarts[start], 0)
        selectTab(start)
    }

    private fun selectTab(i: Int) {
        if (i == selectedTab) return
        selectedTab = i
        tabViews.forEachIndexed { idx, v ->
            v.alpha = if (idx == i) 1f else 0.45f
            v.background = if (idx == i) Ui.rounded(theme.modifier, Ui.dp(context, 10f).toFloat()) else Ui.ripple(theme)
        }
    }

    private fun pick(e: String) {
        if (e.isEmpty()) return
        val list = recents().toMutableList()
        list.remove(e)
        list.add(0, e)
        data.edit().putString(KEY_RECENTS, list.take(MAX_RECENTS).joinToString("\n")).apply()
        onEmoji(e)
    }

    private inner class Holder(val view: TextView) : RecyclerView.ViewHolder(view)

    private inner class Adapter : RecyclerView.Adapter<Holder>() {
        override fun getItemCount() = entries.size
        override fun getItemViewType(position: Int) = if (entries[position].header) 0 else 1

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val tv = TextView(parent.context)
            if (viewType == 0) {
                tv.textSize = 12f
                tv.setTextColor(theme.hint)
                val p = Ui.dp(context, 10f)
                tv.setPadding(p, p, p, p / 2)
                tv.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            } else {
                tv.textSize = 26f
                tv.gravity = Gravity.CENTER
                tv.background = Ui.ripple(theme)
                tv.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, 48f))
            }
            return Holder(tv)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val e = entries[position]
            holder.view.text = if (e.header) e.text.uppercase() else e.text
            if (!e.header) holder.view.setOnClickListener { pick(e.text) }
        }
    }

    companion object {
        private const val COLUMNS = 8
        private const val MAX_RECENTS = 32
        private const val KEY_RECENTS = "emoji_recents"
    }
}
