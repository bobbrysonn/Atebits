package dev.bobbrysonn.atebits.data

import android.content.Context
import android.webkit.CookieManager
import dev.bobbrysonn.atebits.Constants
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
}

@Serializable
data class Session(
    val cookieString: String,
    val csrfToken: String,
    val authorization: String
)
