package dev.fluidkey.touch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AdaptiveTouchTest {

    private val keyW = 100f
    private val keyH = 150f
    private lateinit var keys: List<HitKey>
    private lateinit var predictor: NextCharPredictor

    /** A plain QWERTY letter grid: 100x150 cells, rows 2 and 3 indented like a phone keyboard. */
    @Before
    fun setUp() {
        val rows = listOf("qwertyuiop" to 0f, "asdfghjkl" to 0.5f, "zxcvbnm" to 1.5f)
        val list = ArrayList<HitKey>()
        rows.forEachIndexed { r, (letters, indent) ->
            letters.forEachIndexed { i, c ->
                val left = (indent + i) * keyW
                val top = r * keyH
                list += HitKey(list.size, c.code, left, top, left + keyW, top + keyH, left + keyW / 2, top + keyH / 2, keyW, keyH, true)
            }
        }
        keys = list
        predictor = NextCharPredictor().apply {
            listOf(
                "the" to 5000L, "there" to 900L, "they" to 900L, "them" to 700L, "then" to 600L, "these" to 400L,
                "three" to 120L, "through" to 120L, "thanks" to 300L, "this" to 2500L, "that" to 2500L,
                "and" to 3000L, "are" to 800L, "rest" to 50L, "quick" to 60L, "quiet" to 40L,
            ).forEach { (w, c) -> addFrequency(w, c) }
            markReady()
        }
    }

    private fun key(c: Char) = keys.first { it.code == c.code }

    @Test
    fun distributionSumsToOne() {
        for (prefix in listOf("", "t", "th", "zzq", "quick")) {
            val sum = predictor.distribution(prefix).sum()
            assertEquals("prefix '$prefix'", 1f, sum, 1e-3f)
        }
    }

    @Test
    fun predictorPrefersDictionaryContinuations() {
        val p = predictor.distribution("th")
        assertTrue(p['e' - 'a'] > p['r' - 'a'])
        assertTrue(p['e' - 'a'] > 0.3f)
        val q = predictor.distribution("q")
        assertTrue(q['u' - 'a'] > 0.5f)
    }

    @Test
    fun likelyKeyStealsBorderTouch() {
        val r = AdaptiveTouchResolver()
        val e = key('e')
        val rKey = key('r')
        // 15% into 'r', outside r's anchor core.
        val x = rKey.left + keyW * 0.15f
        val y = rKey.cy
        val res = r.resolve(x, y, keys, predictor.distribution("th"))!!
        assertEquals('e'.code, res.key.code)
        assertTrue(res.stolen)
        // The same touch with an unhelpful context stays on 'r'.
        val plain = r.resolve(x, y, keys, null)!!
        assertEquals(rKey.code, plain.key.code)
        assertEquals(e.code, key('e').code)
    }

    @Test
    fun anchorCoreIsAlwaysRespected() {
        val r = AdaptiveTouchResolver().apply { strength = 2f }
        // Even when every probability favours a neighbour, the centre of each key types that key.
        for (prefix in listOf("", "th", "q", "an", "zzz")) {
            val probs = predictor.distribution(prefix)
            for (k in keys) {
                for ((fx, fy) in listOf(0f to 0f, 0.24f to 0.24f, -0.24f to -0.24f, 0.24f to -0.24f, -0.24f to 0.24f)) {
                    val res = r.resolve(k.cx + fx * keyW, k.cy + fy * keyH, keys, probs)!!
                    assertEquals("key ${k.code.toChar()} prefix '$prefix'", k.code, res.key.code)
                }
            }
        }
    }

    @Test
    fun zeroStrengthIsAPlainKeyboard() {
        val r = AdaptiveTouchResolver().apply { strength = 0f }
        val probs = predictor.distribution("th")
        var y = 1f
        while (y < 3 * keyH) {
            var x = 1f
            while (x < 10 * keyW) {
                val res = r.resolve(x, y, keys, probs)!!
                assertFalse(res.stolen)
                x += 7f
            }
            y += 11f
        }
    }

    @Test
    fun expansionIsBounded() {
        val r = AdaptiveTouchResolver().apply { strength = 2f }
        val probs = predictor.distribution("th") // strongly favours 'e'
        val e = key('e')
        // Two keys away from 'e' it must never win.
        val far = key('y')
        val res = r.resolve(far.left + 1f, far.cy, keys, probs)!!
        assertFalse(res.key.code == e.code)
    }

    @Test
    fun learnsAimOffsetOnlyForAcceptedWords() {
        val r = AdaptiveTouchResolver()
        val a = key('a')
        repeat(50) {
            r.recordTap(a, a.cx + 30f, a.cy, stolen = false)
            r.commitWord()
        }
        assertTrue(r.offsetOf('a').first > 0.1f)
        val r2 = AdaptiveTouchResolver()
        repeat(50) {
            r2.recordTap(a, a.cx + 30f, a.cy, stolen = false)
            r2.onBackspace()
            r2.commitWord()
        }
        assertEquals(0f, r2.offsetOf('a').first, 1e-6f)
        val restored = AdaptiveTouchResolver().apply { deserialize(r.serialize()) }
        assertEquals(r.offsetOf('a').first, restored.offsetOf('a').first, 1e-4f)
    }
}
