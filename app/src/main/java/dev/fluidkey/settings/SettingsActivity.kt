package dev.fluidkey.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import dev.fluidkey.BuildConfig
import dev.fluidkey.clipboard.ClipboardStore
import dev.fluidkey.ime.Dictionary
import dev.fluidkey.ime.FluidKeyService

class SettingsActivity : ComponentActivity() {

    private val setup = mutableStateOf(SetupState.DISABLED)
    private lateinit var store: PrefsStore

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        store = PrefsStore(Dictionary.settings(this))
        setup.value = computeSetup()
        setContent {
            FluidKeyTheme {
                SettingsScreen(
                    store = store,
                    setup = setup.value,
                    version = BuildConfig.VERSION_NAME,
                    onEnable = { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) },
                    onSelect = { (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker() },
                    onResetLearning = {
                        // The running keyboard listens for this token and wipes its learned model.
                        store.sp.edit().putLong("reset_learning_token", System.currentTimeMillis()).apply()
                    },
                    onClearClipboard = {
                        ClipboardStore(getSharedPreferences(FluidKeyService.DATA_PREFS, Context.MODE_PRIVATE)).clearUnpinned()
                    },
                    onOpenDocs = {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(DOCS_URL)))
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        setup.value = computeSetup()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // The keyboard picker is a dialog over us; re-check when it closes.
        if (hasFocus) setup.value = computeSetup()
    }

    override fun onDestroy() {
        store.close()
        super.onDestroy()
    }

    private fun computeSetup(): SetupState {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val enabled = imm.enabledInputMethodList.any { it.packageName == packageName }
        val current = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) ?: ""
        return when {
            current.startsWith("$packageName/") -> SetupState.READY
            enabled -> SetupState.ENABLED
            else -> SetupState.DISABLED
        }
    }

    companion object {
        const val DOCS_URL = "https://github.com/darshan260802/fluid-key/blob/HEAD/docs/ADAPTIVE_TOUCH.md"
    }
}
