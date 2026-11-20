package dev.fluidkey.touch

import kotlin.math.abs
import kotlin.math.ln

/**
 * A key as the touch resolver sees it.
 *
 * [left]..[bottom] is the key's *touch cell* (cells tile the keyboard with no dead zones),
 * [cx]/[cy]/[w]/[h] describe the key's nominal cell used for the spatial model.
 * Only keys with [adaptive] = true (letters) take part in dynamic resizing.
 */
class HitKey(
    val id: Int,
    val code: Int,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val cx: Float,
    val cy: Float,
    val w: Float,
    val h: Float,
    val adaptive: Boolean,
) {
    fun contains(x: Float, y: Float) = x >= left && x < right && y >= top && y < bottom

    fun distanceSq(x: Float, y: Float): Float {
        val dx = when {
            x < left -> left - x
            x > right -> x - right
            else -> 0f
        }
        val dy = when {
            y < top -> top - y
            y > bottom -> y - bottom
            else -> 0f
        }
        return dx * dx + dy * dy
    }
}

/**
 * Invisible, prediction-driven key-target resizing (the technique behind the iOS keyboard,
 * US 8,232,973), made safe with *anchored* targets (Gunawardana, Paek & Meek, IUI 2010).
 *
 * For a touch at p:
 *   1. If p lies in the anchor core of the key under the finger, that key wins. Always.
 *      This guarantees every key (and so every word) stays typeable.
 *   2. Otherwise each nearby letter k is scored with a Bayesian decode
 *        score(k) = log N(p; centre_k + learnedOffset_k, sigma) + lambda * log P(k | prefix)
 *      and the best one wins, as long as p is within maxExpansion of k's cell.
 *
 * The result: likely next letters quietly grow into their neighbours' edges while the
 * visible keyboard never changes.
 */
class AdaptiveTouchResolver {

    class Result(val key: HitKey, val geometric: HitKey) {
        val stolen: Boolean get() = key !== geometric
    }

    var enabled = true
    /** lambda: weight of the language model against the spatial model. 0 = plain keyboard. */
    var strength = 1f
    /** Fraction of a key's width/height (centred) that always belongs to that key. */
    var anchorFraction = 0.5f
    /** How far (in key widths/heights) a key's effective target may extend past its cell. */
    var maxExpansion = 0.45f
    /** Spatial model standard deviation, in key widths/heights. */
    var sigmaFactor = 0.5f
    /** Minimum log-score advantage (nats) a neighbour needs to take over a touch. */
    var stealMargin = 0.5f
    /** Learn per-key aim offsets from words the user commits without correcting. */
    var learning = true

    private val offX = FloatArray(26)
    private val offY = FloatArray(26)

    private class Tap(val index: Int, val nx: Float, val ny: Float)

    private val pending = ArrayList<Tap>()
    private var dampTaps = 0
    private var lastWasStolen = false

    /** The key whose cell contains (x, y), or the nearest one for touches in gaps/edges. */
    fun geometric(x: Float, y: Float, keys: List<HitKey>): HitKey? {
        var best: HitKey? = null
        var bestD = Float.MAX_VALUE
        for (k in keys) {
            if (k.contains(x, y)) return k
            val d = k.distanceSq(x, y)
            if (d < bestD) {
                bestD = d
                best = k
            }
        }
        return best
    }

    /**
     * Resolves a touch. [probs] is P(letter) for a..z or null when no prediction is available.
     * Pass [consume] = false for visualisation so internal state is untouched.
     */
    fun resolve(x: Float, y: Float, keys: List<HitKey>, probs: FloatArray?, consume: Boolean = true): Result? {
        val g = geometric(x, y, keys) ?: return null
        val r = decide(x, y, g, keys, probs)
        if (consume) {
            lastWasStolen = r.stolen
            if (dampTaps > 0) dampTaps--
        }
        return r
    }

    private fun decide(x: Float, y: Float, g: HitKey, keys: List<HitKey>, probs: FloatArray?): Result {
        if (!enabled || probs == null || !g.adaptive || strength <= 0f) return Result(g, g)
        // 1. Anchor: the core of every key is inviolable.
        if (abs(x - g.cx) <= g.w * anchorFraction / 2 && abs(y - g.cy) <= g.h * anchorFraction / 2) {
            return Result(g, g)
        }
        // 2. Bayesian decode over nearby letters.
        val lambda = if (dampTaps > 0) strength * 0.25f else strength
        // A neighbour must beat the touched key by a clear margin: two unlikely keys never
        // trade edges over noise, only a confident prediction moves a boundary.
        var best = g
        var bestScore = score(g, x, y, probs, lambda) + stealMargin
        for (k in keys) {
            if (k === g || !k.adaptive) continue
            if (abs(x - k.cx) > k.w * (0.5f + maxExpansion) || abs(y - k.cy) > k.h * (0.5f + maxExpansion)) continue
            val s = score(k, x, y, probs, lambda)
            if (s > bestScore) {
                best = k
                bestScore = s
            }
        }
        return Result(best, g)
    }

    private fun score(k: HitKey, x: Float, y: Float, probs: FloatArray, lambda: Float): Float {
        val i = k.code - 'a'.code
        val inRange = i in 0..25
        val ox = if (inRange) offX[i] else 0f
        val oy = if (inRange) offY[i] else 0f
        val dx = (x - (k.cx + ox * k.w)) / (sigmaFactor * k.w)
        val dy = (y - (k.cy + oy * k.h)) / (sigmaFactor * k.h)
        val p = if (inRange) probs[i] else 1f / 26
        return -0.5f * (dx * dx + dy * dy) + lambda * ln(p.coerceAtLeast(1e-6f))
    }

    /** Remember where the user actually touched for [key]; applied once the word is accepted. */
    fun recordTap(key: HitKey, x: Float, y: Float, stolen: Boolean) {
        if (!learning || !key.adaptive || stolen) return
        val i = key.code - 'a'.code
        if (i !in 0..25) return
        pending.add(Tap(i, (x - key.cx) / key.w, (y - key.cy) / key.h))
        if (pending.size > 64) pending.removeAt(0)
    }

    /** Backspace: the user disagreed with something. Drop the word's taps, back off if we stole. */
    fun onBackspace() {
        pending.clear()
        if (lastWasStolen) dampTaps = 3
        lastWasStolen = false
    }

    /** Word committed without correction: fold its taps into the per-key aim model. */
    fun commitWord() {
        for (t in pending) {
            offX[t.index] = (offX[t.index] * (1 - LEARN_RATE) + t.nx * LEARN_RATE).coerceIn(-MAX_OFFSET, MAX_OFFSET)
            offY[t.index] = (offY[t.index] * (1 - LEARN_RATE) + t.ny * LEARN_RATE).coerceIn(-MAX_OFFSET, MAX_OFFSET)
        }
        pending.clear()
    }

    fun offsetOf(letter: Char): Pair<Float, Float> {
        val i = letter - 'a'
        return offX[i] to offY[i]
    }

    fun reset() {
        offX.fill(0f)
        offY.fill(0f)
        pending.clear()
        dampTaps = 0
    }

    fun serialize(): String = (0 until 26).joinToString(";") { "${offX[it]},${offY[it]}" }

    fun deserialize(s: String?) {
        if (s.isNullOrEmpty()) return
        val parts = s.split(';')
        if (parts.size != 26) return
        for (i in 0 until 26) {
            val xy = parts[i].split(',')
            offX[i] = xy.getOrNull(0)?.toFloatOrNull()?.coerceIn(-MAX_OFFSET, MAX_OFFSET) ?: 0f
            offY[i] = xy.getOrNull(1)?.toFloatOrNull()?.coerceIn(-MAX_OFFSET, MAX_OFFSET) ?: 0f
        }
    }

    companion object {
        private const val LEARN_RATE = 0.03f
        private const val MAX_OFFSET = 0.2f
    }
}
