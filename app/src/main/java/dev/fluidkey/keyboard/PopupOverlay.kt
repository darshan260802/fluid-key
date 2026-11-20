package dev.fluidkey.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import dev.fluidkey.theme.KeyboardTheme
import dev.fluidkey.theme.Themes

/**
 * Transparent layer above the whole input view that draws key previews and long-press
 * popups. Living above the toolbar lets top-row popups extend past the keyboard's top edge.
 * It never consumes touches.
 */
class PopupOverlay(context: Context) : View(context) {
    var theme: KeyboardTheme = Themes.DARK
    var offsetX = 0f
    var offsetY = 0f

    private val dp = resources.displayMetrics.density
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    private var previewRect: RectF? = null
    private var previewText = ""

    private var panelRect: RectF? = null
    private var cells: List<RectF> = emptyList()
    private var items: List<String> = emptyList()
    private var selected = 0

    init {
        isClickable = false
        isFocusable = false
    }

    fun showPreview(r: RectF, label: String) {
        previewRect = r
        previewText = label
        invalidate()
    }

    fun hidePreview() {
        if (previewRect != null) {
            previewRect = null
            invalidate()
        }
    }

    fun showPanel(panel: RectF, cellRects: List<RectF>, labels: List<String>, sel: Int) {
        panelRect = panel
        cells = cellRects
        items = labels
        selected = sel
        previewRect = null
        invalidate()
    }

    fun setSelection(sel: Int) {
        if (sel != selected) {
            selected = sel
            invalidate()
        }
    }

    fun hidePanel() {
        if (panelRect != null) {
            panelRect = null
            invalidate()
        }
    }

    fun hideAll() {
        previewRect = null
        panelRect = null
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.save()
        canvas.translate(offsetX, offsetY)
        val radius = 10 * dp
        previewRect?.let { r ->
            bg.color = theme.popup
            bg.setShadowLayer(6 * dp, 0f, 2 * dp, 0x55000000)
            canvas.drawRoundRect(r, radius, radius, bg)
            bg.clearShadowLayer()
            text.color = theme.popupText
            text.textSize = r.height() * 0.55f
            canvas.drawText(previewText, r.centerX(), r.centerY() - (text.descent() + text.ascent()) / 2, text)
        }
        panelRect?.let { p ->
            bg.color = theme.popup
            bg.setShadowLayer(8 * dp, 0f, 3 * dp, 0x66000000)
            canvas.drawRoundRect(p, radius, radius, bg)
            bg.clearShadowLayer()
            for ((i, cell) in cells.withIndex()) {
                val isSel = i == selected
                if (isSel) {
                    bg.color = theme.accent
                    canvas.drawRoundRect(cell, radius * 0.8f, radius * 0.8f, bg)
                }
                text.color = if (isSel) theme.onAccent else theme.popupText
                text.textSize = cell.height() * 0.48f
                canvas.drawText(items[i], cell.centerX(), cell.centerY() - (text.descent() + text.ascent()) / 2, text)
            }
        }
        canvas.restore()
    }
}
