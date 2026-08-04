package dev.bobbrysonn.atebits.data

import android.content.Context
import android.webkit.CookieManager
import dev.bobbrysonn.atebits.Constants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class AuthRepository(private val context: Context) {

    private val sharedPreferences = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun extractAndStoreSession() {
        val cookieManager = CookieManager.getInstance()
        val cookies = cookieManager.getCookie(Constants.LOGIN_URL)

        if (cookies != null) {
            val cookieMap = parseCookies(cookies)
            val csrfToken = cookieMap["ct0"]
            
            if (csrfToken != null) {
                val session = Session(
                    cookieString = cookies,
                    csrfToken = csrfToken,
                    authorization = Constants.BEARER_TOKEN
                )
                saveSession(session)
            }
        }
    }

    private fun parseCookies(cookieString: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val pairs = cookieString.split(";")
        for (pair in pairs) {
            val parts = pair.trim().split("=", limit = 2)
            if (parts.size == 2) {
                map[parts[0]] = parts[1]
            }
        }
        return map
    }

    private fun saveSession(session: Session) {
        val sessionJson = json.encodeToString(Session.serializer(), session)
        sharedPreferences.edit().putString("session", sessionJson).apply()
    }

    fun getSession(): Session? {
        val sessionJson = sharedPreferences.getString("session", null) ?: return null
        return try {
            json.decodeFromString(Session.serializer(), sessionJson)
        } catch (e: Exception) {
            null
        }
    }

    // Wipes the stored session and the WebView cookies, so the login screen
    // shows a fresh login form instead of re-extracting the stale cookies.
    fun clearSession() {
        sharedPreferences.edit().remove("session").apply()
        val cookieManager = CookieManager.getInstance()
        cookieManager.removeAllCookies(null)
        cookieManager.flush()
    }

    // Called when the API rejects our credentials (401/403).
    fun onSessionExpired() {
        clearSession()
        SessionEvents.notifySessionExpired()
    }
}

// Process-wide signal so the network layer can tell the UI the session died,
// regardless of which AuthRepository instance noticed it.
object SessionEvents {
    private val _sessionExpired = MutableStateFlow(false)
    val sessionExpired: StateFlow<Boolean> = _sessionExpired.asStateFlow()

    fun notifySessionExpired() {
        _sessionExpired.value = true
    }

    fun reset() {
        _sessionExpired.value = false
    }
}

@Serializable
data class Session(
    val cookieString: String,
    val csrfToken: String,
    val authorization: String
)
