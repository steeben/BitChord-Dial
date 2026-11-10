package com.music.bitchord.data.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Counts taps made one after another: [tap] answers true on the one that
 * completes a run of [needed], each within [gapMs] of the last. A pause
 * starts the count again.
 */
class TapRun(private val needed: Int, private val gapMs: Long = 1_500L) {
    private var count = 0
    private var last = 0L

    fun tap(nowMs: Long): Boolean {
        count = if (count > 0 && nowMs - last > gapMs) 1 else count + 1
        last = nowMs
        if (count < needed) return false
        count = 0
        return true
    }
}

/**
 * Developer options: switched on by tapping the Dial logo at the foot of
 * Settings [TAPS_TO_ENABLE] times in a row, which adds a few rows to
 * Advanced & experimental options for looking at the app as someone new to
 * it would.
 *
 * Kept in a file of their own, apart from the settings: the stock settings
 * test empties the settings file, and a backup import replaces it, and
 * neither may take these with it.
 */
object DeveloperOptions {

    const val TAPS_TO_ENABLE = 10

    /** The placeholder account in the account switcher: a listener who is not signed in. */
    const val PLACEHOLDER_ACCOUNT_ID = "dial:placeholder"
    const val PLACEHOLDER_PROFILE_ID = "dial:placeholder:guest"

    private const val PREFS = "dial_developer"
    private const val SNAPSHOT_PREFS = "dial_stock_settings_snapshot"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_PLACEHOLDER_ACCOUNT = "placeholder_account"
    private const val KEY_PLACEHOLDER_SELECTED = "placeholder_selected"
    private const val KEY_STOCK_SETTINGS = "stock_settings_test"

    private var prefs: SharedPreferences? = null

    val enabled = MutableStateFlow(false)

    /** Whether the account switcher offers the placeholder account. */
    val placeholderAccount = MutableStateFlow(false)

    /**
     * Whether the placeholder account is the one in use. While it is, the app
     * makes its requests with no session and reads as signed out, and the
     * real accounts stay saved, untouched, to switch back to.
     */
    @Volatile
    var placeholderSelected: Boolean = false
        private set

    /** Whether every setting is at its default for now, with the listener's own put aside. */
    val stockSettingsTest = MutableStateFlow(false)

    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        enabled.value = p.getBoolean(KEY_ENABLED, false)
        placeholderAccount.value = enabled.value && p.getBoolean(KEY_PLACEHOLDER_ACCOUNT, false)
        placeholderSelected = placeholderAccount.value && p.getBoolean(KEY_PLACEHOLDER_SELECTED, false)
        stockSettingsTest.value = p.getBoolean(KEY_STOCK_SETTINGS, false)
    }

    /**
     * Off takes the rows away and undoes what they did: the placeholder
     * account goes, and settings put aside by the stock settings test return.
     */
    fun setEnabled(context: Context, value: Boolean) {
        enabled.value = value
        prefs?.edit()?.putBoolean(KEY_ENABLED, value)?.apply()
        if (!value) {
            setPlaceholderAccount(false)
            setStockSettingsTest(context, false)
        }
    }

    /** Off also leaves the account, which the view model sees and acts on. */
    fun setPlaceholderAccount(value: Boolean) {
        if (!value) setPlaceholderSelected(false)
        placeholderAccount.value = value
        prefs?.edit()?.putBoolean(KEY_PLACEHOLDER_ACCOUNT, value)?.apply()
    }

    fun setPlaceholderSelected(value: Boolean) {
        placeholderSelected = value
        prefs?.edit()?.putBoolean(KEY_PLACEHOLDER_SELECTED, value)?.apply()
    }

    /**
     * On: the listener's settings are put aside and every setting goes back
     * to its default. Off: the settings put aside come back, replacing
     * whatever was changed in between. Sign-ins and the record of downloads
     * are not settings and stay as they are throughout.
     */
    fun setStockSettingsTest(context: Context, on: Boolean) {
        if (on == stockSettingsTest.value) return
        val snapshot = context.applicationContext.getSharedPreferences(SNAPSHOT_PREFS, Context.MODE_PRIVATE)
        if (on) {
            // Written before anything is cleared, so a stop in between loses nothing.
            val saved = snapshot.edit().clear().putAll(AppSettings.exportPrefs()).commit()
            if (!saved) return
            prefs?.edit()?.putBoolean(KEY_STOCK_SETTINGS, true)?.commit()
            AppSettings.importPrefs(emptyMap())
        } else {
            AppSettings.importPrefs(snapshot.all)
            prefs?.edit()?.putBoolean(KEY_STOCK_SETTINGS, false)?.commit()
            snapshot.edit().clear().apply()
        }
        // Shares the settings' file, so it has just been replaced underneath it.
        SearchHistory.reload()
        stockSettingsTest.value = on
    }

    private fun SharedPreferences.Editor.putAll(values: Map<String, Any?>): SharedPreferences.Editor {
        values.forEach { (key, value) ->
            when (value) {
                is Boolean -> putBoolean(key, value)
                is Int -> putInt(key, value)
                is Long -> putLong(key, value)
                is Float -> putFloat(key, value)
                is String -> putString(key, value)
                is Set<*> -> putStringSet(key, value.filterIsInstance<String>().toSet())
                else -> Unit
            }
        }
        return this
    }
}
