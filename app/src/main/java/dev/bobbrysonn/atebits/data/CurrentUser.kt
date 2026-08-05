package dev.bobbrysonn.atebits.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** The signed-in user's profile, fetched once per session. */
object CurrentUser {
    var profile by mutableStateOf<UserProfile?>(null)
}
