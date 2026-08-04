package dev.bobbrysonn.atebits

import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import dev.bobbrysonn.atebits.data.AuthRepository
import dev.bobbrysonn.atebits.data.SessionEvents
import dev.bobbrysonn.atebits.ui.screens.LoginScreen
import dev.bobbrysonn.atebits.ui.screens.MainScreen
import dev.bobbrysonn.atebits.ui.theme.AtebitsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Allow chrome://inspect debugging of the login WebView in debug builds
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        val authRepository = AuthRepository(this)

        setContent {
            AtebitsTheme {
                var session by remember { mutableStateOf(authRepository.getSession()) }

                // When the API rejects our credentials, drop back to the login screen.
                val sessionExpired by SessionEvents.sessionExpired.collectAsState()
                LaunchedEffect(sessionExpired) {
                    if (sessionExpired) {
                        session = null
                        SessionEvents.reset()
                    }
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    if (session != null) {
                        MainScreen()
                    } else {
                        LoginScreen(
                            onLoginSuccess = {
                                authRepository.extractAndStoreSession()
                                session = authRepository.getSession()
                            }
                        )
                    }
                }
            }
        }
    }
}
