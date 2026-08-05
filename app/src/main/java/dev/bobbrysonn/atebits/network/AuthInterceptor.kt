package dev.bobbrysonn.atebits.network

import dev.bobbrysonn.atebits.Constants
import dev.bobbrysonn.atebits.data.AuthRepository
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(private val authRepository: AuthRepository) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val builder = originalRequest.newBuilder()

        // Add User-Agent to all requests
        builder.header("User-Agent", Constants.USER_AGENT)

        // Add Auth headers if session exists
        val session = authRepository.getSession()
        if (session != null) {
            builder.header("Authorization", Constants.BEARER_TOKEN) // Changed to use Constants.BEARER_TOKEN
            builder.header("Cookie", session.cookieString)
            builder.header("x-csrf-token", session.csrfToken)
            builder.header("content-type", "application/json")
            builder.header("referer", "https://x.com/")
            builder.header("x-twitter-active-user", "yes")
            builder.header("x-twitter-auth-type", "OAuth2Session")
            builder.header("x-twitter-client-language", "en")
            builder.header("priority", "u=1, i")
            // No x-client-transaction-id: the third-party generator service
            // this app relied on is dead (parked domain), so the header was
            // never actually sent — and the blocking lookup added a wasted
            // round-trip to every single API call.
        }

        val response = chain.proceed(builder.build())

        // 401 = expired/revoked auth_token, 403 = rejected csrf/cookies. Either way
        // the stored session is dead — wipe it and signal the UI to show login.
        if (session != null && (response.code == 401 || response.code == 403)) {
            authRepository.onSessionExpired()
        }

        return response
    }
}
