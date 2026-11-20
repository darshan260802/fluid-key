package dev.fluidkey.keyboard

object Codes {
    const val SHIFT = -1
    const val SYMBOLS = -2
    const val ALPHA = -3
    const val SYMBOLS2 = -4
    const val DELETE = -5
    const val EMOJI = -6
    const val ENTER = -7
    const val DELETE_WORD = -8
    const val SPACE = 32
}

enum class KeyStyle { NORMAL, MODIFIER, ACTION, SPACE }

class Key(
    val code: Int,
    val label: String,
    val width: Float = 1f,
    val popup: List<String> = emptyList(),
    val hint: String? = null,
    val style: KeyStyle = KeyStyle.NORMAL,
) {
    val isLetter: Boolean get() = code in 'a'.code..'z'.code

    fun output(shifted: Boolean): String = if (isLetter && shifted) label.uppercase() else label
}

class Row(val keys: List<Key>, val height: Float = 1f, val padStart: Float = 0f)

enum class LayoutKind { LETTERS, SYMBOLS, SYMBOLS2, NUMBER, PHONE }

class KeyboardLayout(val kind: LayoutKind, val rows: List<Row>)

object Layouts {
    private const val TOP = "qwertyuiop"
    private const val DIGITS = "1234567890"

    private val letterSymbols = mapOf(
        'a' to "@", 's' to "#", 'd' to "$", 'f' to "_", 'g' to "&", 'h' to "-", 'j' to "+", 'k' to "(", 'l' to ")",
        'z' to "*", 'x' to "\"", 'c' to "'", 'v' to ":", 'b' to ";", 'n' to "!", 'm' to "?",
    )

    private val accents = mapOf(
        'a' to "àáâäæãåā", 'c' to "çćč", 'd' to "ð", 'e' to "èéêëēęė", 'g' to "ğ", 'i' to "ìíîïīį",
        'l' to "ł", 'n' to "ñń", 'o' to "òóôöõøœ", 's' to "ßśš", 'u' to "ùúûüū", 'y' to "ýÿ", 'z' to "žźż",
    )

    private val digitPopups = mapOf(
        '1' to "¹½⅓¼⅛", '2' to "²⅔", '3' to "³¾⅜", '4' to "⁴", '5' to "⁵⅝", '6' to "⁶",
        '7' to "⁷⅞", '8' to "⁸", '9' to "⁹", '0' to "ⁿ∅⁰",
    )

    private val symbolPopups = mapOf(
        "$" to "₹€£¥¢₩₽", "-" to "–—·", "+" to "±", "(" to "[{<", ")" to "]}>", "/" to "\\|",
        "*" to "†‡★", "\"" to "“”„«»", "'" to "‘’‚‹›", "!" to "¡", "?" to "¿", "%" to "‰",
        "=" to "≠≈∞", "^" to "←↑↓→", "°" to "′″",
    )

    private fun chars(s: String) = s.map { it.toString() }

    private fun letter(c: Char, numberRow: Boolean): Key {
        val first: String? = when {
            c in TOP && !numberRow -> DIGITS[TOP.indexOf(c)].toString()
            c in TOP -> null
            else -> letterSymbols[c]
        }
        val popup = buildList {
            first?.let { add(it) }
            accents[c]?.forEach { add(it.toString()) }
        }
        return Key(c.code, c.toString(), popup = popup, hint = first)
    }

    private fun symbol(s: String, width: Float = 1f): Key {
        val popup = (digitPopups[s.first()] ?: symbolPopups[s])?.let { listOf(s) + chars(it) } ?: emptyList()
        return Key(s.codePointAt(0), s, width, popup = popup)
    }

    private fun delete(width: Float = 1.5f) = Key(Codes.DELETE, "", width, style = KeyStyle.MODIFIER)

    private fun bottomRow(modeKey: Key) = Row(
        listOf(
            modeKey,
            Key(Codes.EMOJI, "", 1f, style = KeyStyle.MODIFIER),
            Key(','.code, ",", 1f, style = KeyStyle.MODIFIER),
            Key(Codes.SPACE, "", 4f, style = KeyStyle.SPACE),
            Key('.'.code, ".", 1f, popup = chars(".,?!'\":;-"), style = KeyStyle.MODIFIER),
            Key(Codes.ENTER, "", 1.5f, style = KeyStyle.ACTION),
        )
    )

    private fun numberRow() = Row(chars(DIGITS).map { symbol(it) }, height = 0.8f)

    fun letters(numberRow: Boolean): KeyboardLayout {
        val rows = mutableListOf<Row>()
        if (numberRow) rows += numberRow()
        rows += Row(TOP.map { letter(it, numberRow) })
        rows += Row("asdfghjkl".map { letter(it, numberRow) }, padStart = 0.5f)
        rows += Row(
            listOf(Key(Codes.SHIFT, "", 1.5f, style = KeyStyle.MODIFIER)) +
                "zxcvbnm".map { letter(it, numberRow) } +
                delete()
        )
        rows += bottomRow(Key(Codes.SYMBOLS, "?123", 1.5f, style = KeyStyle.MODIFIER))
        return KeyboardLayout(LayoutKind.LETTERS, rows)
    }

    fun symbols(): KeyboardLayout = KeyboardLayout(
        LayoutKind.SYMBOLS,
        listOf(
            Row(chars(DIGITS).map { symbol(it) }),
            Row(chars("@#\$_&-+()/").map { symbol(it) }),
            Row(
                listOf(Key(Codes.SYMBOLS2, "=\\<", 1.5f, style = KeyStyle.MODIFIER)) +
                    chars("*\"':;!?").map { symbol(it) } + delete()
            ),
            bottomRow(Key(Codes.ALPHA, "ABC", 1.5f, style = KeyStyle.MODIFIER)),
        )
    )

    fun symbols2(): KeyboardLayout = KeyboardLayout(
        LayoutKind.SYMBOLS2,
        listOf(
            Row(chars("~`|•√π÷×¶∆").map { symbol(it) }),
            Row(chars("£¢€¥^°={}\\").map { symbol(it) }),
            Row(
                listOf(Key(Codes.SYMBOLS, "?123", 1.5f, style = KeyStyle.MODIFIER)) +
                    chars("%©®™✓[]").map { symbol(it) } + delete()
            ),
            bottomRow(Key(Codes.ALPHA, "ABC", 1.5f, style = KeyStyle.MODIFIER)),
        )
    )

    fun number(): KeyboardLayout = KeyboardLayout(
        LayoutKind.NUMBER,
        listOf(
            Row(listOf(symbol("1", 2.5f), symbol("2", 2.5f), symbol("3", 2.5f), Key('-'.code, "-", 2.5f, popup = chars("-+/*()"), style = KeyStyle.MODIFIER))),
            Row(listOf(symbol("4", 2.5f), symbol("5", 2.5f), symbol("6", 2.5f), Key(Codes.SPACE, "", 2.5f, style = KeyStyle.SPACE))),
            Row(listOf(symbol("7", 2.5f), symbol("8", 2.5f), symbol("9", 2.5f), delete(2.5f))),
            Row(
                listOf(
                    Key(','.code, ",", 2.5f, style = KeyStyle.MODIFIER), symbol("0", 2.5f),
                    Key('.'.code, ".", 2.5f, popup = chars(".:/"), style = KeyStyle.MODIFIER),
                    Key(Codes.ENTER, "", 2.5f, style = KeyStyle.ACTION),
                )
            ),
        )
    )

    fun phone(): KeyboardLayout = KeyboardLayout(
        LayoutKind.PHONE,
        listOf(
            Row(listOf(symbol("1", 2.5f), symbol("2", 2.5f), symbol("3", 2.5f), Key('+'.code, "+", 2.5f, popup = chars("+()-/N,;"), style = KeyStyle.MODIFIER))),
            Row(listOf(symbol("4", 2.5f), symbol("5", 2.5f), symbol("6", 2.5f), Key(Codes.SPACE, "", 2.5f, style = KeyStyle.SPACE))),
            Row(listOf(symbol("7", 2.5f), symbol("8", 2.5f), symbol("9", 2.5f), delete(2.5f))),
            Row(
                listOf(
                    Key('*'.code, "*", 2.5f, style = KeyStyle.MODIFIER), symbol("0", 2.5f),
                    Key('#'.code, "#", 2.5f, style = KeyStyle.MODIFIER),
                    Key(Codes.ENTER, "", 2.5f, style = KeyStyle.ACTION),
                )
            ),
        )
    )
}
