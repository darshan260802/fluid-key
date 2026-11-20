package dev.fluidkey.ime

import android.content.Context
import android.content.SharedPreferences
import dev.fluidkey.touch.NextCharPredictor
import java.io.File

/** Shared storage helpers for the keyboard service and the settings app. */
object Dictionary {
    const val USER_WORDS = "user_words.txt"

    /** The settings file (same name androidx.preference used, so v1.0 settings carry over). */
    fun settings(context: Context): SharedPreferences =
        context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)

    /** Loads the bundled frequency dictionary into [p]. Call off the main thread. */
    fun loadBase(context: Context, p: NextCharPredictor) {
        context.assets.open("words_en.txt").bufferedReader().useLines { lines ->
            lines.forEach { line ->
                val sp = line.indexOf(' ')
                if (sp > 0) p.addFrequency(line.substring(0, sp), line.substring(sp + 1).toLongOrNull() ?: 1L)
            }
        }
    }

    /** Words the user has typed, with use counts. */
    fun readUserWords(context: Context): Map<String, Int> {
        val f = File(context.filesDir, USER_WORDS)
        if (!f.exists()) return emptyMap()
        val loaded = HashMap<String, Int>()
        f.forEachLine { line ->
            val parts = line.split(' ')
            val n = parts.getOrNull(1)?.toIntOrNull()
            if (parts.size == 2 && n != null) loaded[parts[0]] = n
        }
        return loaded
    }

    /** Full predictor (dictionary + user words), loaded on a background thread. */
    fun loadAsync(context: Context, onReady: (NextCharPredictor, Map<String, Int>) -> Unit): NextCharPredictor {
        val p = NextCharPredictor()
        val app = context.applicationContext
        Thread {
            var user: Map<String, Int> = emptyMap()
            try {
                loadBase(app, p)
                user = readUserWords(app)
                user.forEach { (w, n) -> p.learn(w, n) }
            } catch (_: Exception) {
            }
            p.markReady()
            onReady(p, user)
        }.apply { name = "FluidKey-dict"; priority = Thread.NORM_PRIORITY - 1 }.start()
        return p
    }
}
