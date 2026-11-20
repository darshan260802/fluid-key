package dev.fluidkey.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import dev.fluidkey.BuildConfig
import dev.fluidkey.R
import dev.fluidkey.clipboard.ClipboardStore
import dev.fluidkey.ime.FluidKeyService

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        findViewById<Button>(R.id.btn_enable).setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
        findViewById<Button>(R.id.btn_select).setOnClickListener {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        }
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings_container, SettingsFragment())
                .commit()
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) updateStatus()
    }

    private fun updateStatus() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val enabled = imm.enabledInputMethodList.any { it.packageName == packageName }
        val current = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) ?: ""
        val selected = current.startsWith("$packageName/")
        findViewById<TextView>(R.id.status).text = getString(
            when {
                selected -> R.string.status_ready
                enabled -> R.string.status_enabled
                else -> R.string.status_disabled
            }
        )
        findViewById<Button>(R.id.btn_enable).isEnabled = !enabled
        findViewById<Button>(R.id.btn_select).isEnabled = enabled && !selected
    }
}

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
        findPreference<Preference>("reset_learning")?.setOnPreferenceClickListener {
            val sp = preferenceManager.sharedPreferences ?: return@setOnPreferenceClickListener true
            // The running keyboard listens for this token and wipes its learned model.
            sp.edit().putLong("reset_learning_token", System.currentTimeMillis()).apply()
            Toast.makeText(requireContext(), R.string.reset_done, Toast.LENGTH_SHORT).show()
            true
        }
        findPreference<Preference>("clear_clipboard")?.setOnPreferenceClickListener {
            val data = requireContext().getSharedPreferences(FluidKeyService.DATA_PREFS, Context.MODE_PRIVATE)
            ClipboardStore(data).clearUnpinned()
            Toast.makeText(requireContext(), R.string.clipboard_cleared, Toast.LENGTH_SHORT).show()
            true
        }
        findPreference<Preference>("version")?.summary = BuildConfig.VERSION_NAME
    }
}
