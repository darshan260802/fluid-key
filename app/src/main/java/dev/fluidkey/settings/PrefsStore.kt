package dev.fluidkey.settings

import android.content.SharedPreferences
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf

/**
 * Compose state over the keyboard's SharedPreferences. Writes go straight to the same keys the
 * keyboard service reads (it reacts through its change listener), and outside changes (e.g. the
 * keyboard's one-handed toggle) flow back into the UI.
 */
class PrefsStore(val sp: SharedPreferences) {

    private class PrefState<T>(initial: T, private val write: (T) -> Unit) : MutableState<T> {
        val inner = mutableStateOf(initial)
        override var value: T
            get() = inner.value
            set(v) {
                if (v != inner.value) {
                    inner.value = v
                    write(v)
                }
            }

        override fun component1(): T = value
        override fun component2(): (T) -> Unit = { value = it }
    }

    private val states = HashMap<String, PrefState<*>>()
    private val reloaders = HashMap<String, () -> Unit>()

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        key?.let { reloaders[it]?.invoke() }
    }

    init {
        sp.registerOnSharedPreferenceChangeListener(listener)
    }

    fun close() = sp.unregisterOnSharedPreferenceChangeListener(listener)

    @Suppress("UNCHECKED_CAST")
    fun bool(key: String, def: Boolean): MutableState<Boolean> = states.getOrPut(key) {
        PrefState(sp.getBoolean(key, def)) { sp.edit().putBoolean(key, it).apply() }.also { st ->
            reloaders[key] = { st.inner.value = sp.getBoolean(key, def) }
        }
    } as MutableState<Boolean>

    @Suppress("UNCHECKED_CAST")
    fun int(key: String, def: Int): MutableState<Int> = states.getOrPut(key) {
        PrefState(sp.getInt(key, def)) { sp.edit().putInt(key, it).apply() }.also { st ->
            reloaders[key] = { st.inner.value = sp.getInt(key, def) }
        }
    } as MutableState<Int>

    @Suppress("UNCHECKED_CAST")
    fun string(key: String, def: String): MutableState<String> = states.getOrPut(key) {
        PrefState(sp.getString(key, def) ?: def) { sp.edit().putString(key, it).apply() }.also { st ->
            reloaders[key] = { st.inner.value = sp.getString(key, def) ?: def }
        }
    } as MutableState<String>
}
