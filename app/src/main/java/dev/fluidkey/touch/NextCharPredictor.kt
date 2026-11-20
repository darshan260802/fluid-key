package dev.fluidkey.touch

import kotlin.math.sqrt

/**
 * Character-level language model: P(next letter | letters typed so far in the current word).
 *
 * Two sources are blended:
 *  1. A prefix trie over a frequency dictionary (+ words the user has typed). Given the
 *     current word prefix, the probability of letter c is the summed weight of all words that
 *     continue with c, divided by the weight of all words that continue at all.
 *  2. A letter trigram model with bigram/unigram interpolation, used as a smooth backoff for
 *     prefixes the dictionary does not know (names, slang, typos).
 *
 * Word weights are sqrt(corpus count) so very common words ("you", "the") do not drown
 * everything else. A small uniform floor keeps every letter possible.
 */
class NextCharPredictor {

    private class Node {
        var keys = CharArray(0)
        var kids = arrayOfNulls<Node>(0)
        var weight = 0.0 // weight of all words passing through this node
        var end = 0.0    // weight of words ending exactly here

        fun child(c: Char): Node? {
            for (i in keys.indices) if (keys[i] == c) return kids[i]
            return null
        }

        fun getOrAdd(c: Char): Node {
            child(c)?.let { return it }
            val n = Node()
            val size = keys.size
            keys = keys.copyOf(size + 1).also { it[size] = c }
            kids = kids.copyOf(size + 1).also { it[size] = n }
            return n
        }
    }

    private val root = Node()
    private val tri = DoubleArray(27 * 27 * 26)
    private val bi = DoubleArray(27 * 26)
    private val uni = DoubleArray(26)

    @Volatile
    var isReady = false
        private set

    private var cachedPrefix: String? = null
    private var cached = FloatArray(26)

    fun markReady() {
        isReady = true
    }

    /** Adds [word] (lowercase a-z only; anything else is ignored) with [weight]. */
    fun addWord(word: String, weight: Double) {
        if (word.isEmpty() || weight <= 0.0) return
        for (c in word) if (c !in 'a'..'z') return
        var n = root
        n.weight += weight
        for (c in word) {
            n = n.getOrAdd(c)
            n.weight += weight
        }
        n.end += weight
        var p2 = START
        var p1 = START
        for (c in word) {
            val ci = c - 'a'
            tri[(p2 * 27 + p1) * 26 + ci] += weight
            bi[p1 * 26 + ci] += weight
            uni[ci] += weight
            p2 = p1
            p1 = ci
        }
        cachedPrefix = null
    }

    /** Adds a dictionary word from its raw corpus frequency. */
    fun addFrequency(word: String, count: Long) = addWord(word, sqrt(count.toDouble()))

    /** Reinforces a word the user actually typed. */
    fun learn(word: String, times: Int = 1) = addWord(normalize(word), LEARN_WEIGHT * times)

    fun contains(word: String): Boolean {
        val n = find(normalize(word)) ?: return false
        return n.end > 0.0
    }

    /**
     * Returns P(letter | prefix) for letters a..z as a 26-element array that sums to 1.
     * The returned array is cached and must not be modified by callers.
     */
    fun distribution(prefixRaw: String): FloatArray {
        val prefix = normalize(prefixRaw)
        if (prefix == cachedPrefix) return cached

        // Letter n-gram backoff.
        val c1 = if (prefix.isNotEmpty()) prefix[prefix.length - 1] - 'a' else START
        val c2 = if (prefix.length >= 2) prefix[prefix.length - 2] - 'a' else START
        var uniTot = 0.0
        var biTot = 0.0
        var triTot = 0.0
        for (i in 0 until 26) {
            uniTot += uni[i]
            biTot += bi[c1 * 26 + i]
            triTot += tri[(c2 * 27 + c1) * 26 + i]
        }
        val mix = DoubleArray(26)
        for (i in 0 until 26) {
            val pu = if (uniTot > 0) uni[i] / uniTot else 1.0 / 26
            val pb = if (biTot > 0) bi[c1 * 26 + i] / biTot else pu
            val pt = if (triTot > 0) tri[(c2 * 27 + c1) * 26 + i] / triTot else pb
            mix[i] = 0.7 * pt + 0.2 * pb + 0.1 * pu
        }

        // Dictionary continuation, weighted by how much evidence the prefix has.
        val node = find(prefix)
        val cont = if (node != null) node.weight - node.end else 0.0
        if (node != null && cont > 1e-9) {
            val conf = cont / (cont + TRIE_CONFIDENCE)
            for (i in 0 until 26) {
                val pt = (node.child('a' + i)?.weight ?: 0.0) / cont
                mix[i] = conf * pt + (1 - conf) * mix[i]
            }
        }

        var sum = 0.0
        for (i in 0 until 26) {
            mix[i] = (1 - FLOOR) * mix[i] + FLOOR / 26
            sum += mix[i]
        }
        val out = FloatArray(26) { (mix[it] / sum).toFloat() }
        cachedPrefix = prefix
        cached = out
        return out
    }

    private fun find(prefix: String): Node? {
        var n = root
        for (c in prefix) n = n.child(c) ?: return null
        return n
    }

    companion object {
        private const val START = 26
        private const val FLOOR = 0.01
        private const val TRIE_CONFIDENCE = 20.0
        const val LEARN_WEIGHT = 40.0

        fun normalize(s: String): String {
            val sb = StringBuilder(s.length)
            for (ch in s) {
                val c = ch.lowercaseChar()
                if (c in 'a'..'z') sb.append(c)
            }
            return if (sb.length > 24) sb.substring(sb.length - 24) else sb.toString()
        }
    }
}
