package dev.fluidkey.clipboard

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** Clipboard history kept in app-private storage. Pinned items are never trimmed or cleared. */
class ClipboardStore(private val sp: SharedPreferences) {

    data class Item(val text: String, val time: Long, val pinned: Boolean)

    fun items(): List<Item> {
        val raw = sp.getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Item(o.getString("t"), o.optLong("d"), o.optBoolean("p"))
            }.sortedWith(compareByDescending<Item> { it.pinned }.thenByDescending { it.time })
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun latest(): Item? = items().filter { !it.pinned }.maxByOrNull { it.time }

    fun add(text: String) {
        val now = System.currentTimeMillis()
        val list = items().toMutableList()
        val existing = list.find { it.text == text }
        list.removeAll { it.text == text }
        list.add(0, Item(text, now, existing?.pinned ?: false))
        val pinned = list.filter { it.pinned }
        val recent = list.filter { !it.pinned }.sortedByDescending { it.time }.take(MAX_UNPINNED)
        save(pinned + recent)
    }

    fun togglePin(text: String) = save(items().map { if (it.text == text) it.copy(pinned = !it.pinned) else it })

    fun remove(text: String) = save(items().filter { it.text != text })

    fun clearUnpinned() = save(items().filter { it.pinned })

    private fun save(list: List<Item>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("t", it.text).put("d", it.time).put("p", it.pinned)) }
        sp.edit().putString(KEY, arr.toString()).apply()
    }

    companion object {
        private const val KEY = "clipboard_items"
        private const val MAX_UNPINNED = 30
    }
}
