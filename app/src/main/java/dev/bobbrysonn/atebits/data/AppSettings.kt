package dev.bobbrysonn.atebits.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Compose-observable app settings backed by SharedPreferences.
 * init() is idempotent; called once from MainActivity before composition.
 */
object AppSettings {
    private const val KEY_AUTOPLAY = "autoplay_videos"

    private var prefs: SharedPreferences? = null

    var autoplayVideos by mutableStateOf(true)
        private set

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE).also {
            autoplayVideos = it.getBoolean(KEY_AUTOPLAY, true)
        }
    }

    fun setAutoplay(enabled: Boolean) {
        autoplayVideos = enabled
        prefs?.edit()?.putBoolean(KEY_AUTOPLAY, enabled)?.apply()
    }
}
