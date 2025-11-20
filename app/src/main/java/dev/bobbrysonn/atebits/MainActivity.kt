package dev.bobbrysonn.atebits

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import dev.bobbrysonn.atebits.data.AuthRepository
import dev.bobbrysonn.atebits.ui.screens.LoginScreen
import dev.bobbrysonn.atebits.ui.screens.MainScreen
import dev.bobbrysonn.atebits.ui.theme.AtebitsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val authRepository = AuthRepository(this)

        setContent {
            AtebitsTheme {
                var isLoggedIn by remember { mutableStateOf(authRepository.getSession() != null) }
                // Changed isLoggedIn to session and made it a mutableStateOf to trigger recomposition
                var session by remember { mutableStateOf(authRepository.getSession()) }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    if (session != null) {
                        MainScreen() // Display MainScreen when a session exists
                    } else {
                        LoginScreen(
                            onLoginSuccess = {
                                authRepository.extractAndStoreSession()
                                // Re-read session to trigger UI update
                                // In a real app, use a ViewModel with StateFlow
                                // For now, we can just recreate the activity or rely on state change if we had one
                                // But since `session` is a local var read once, we need to force recomposition or navigation
                                // Simplest for now:
                                session = authRepository.getSession() // Update the session state
                            }
                        )
                    }
                }
            }
        }
    }
}