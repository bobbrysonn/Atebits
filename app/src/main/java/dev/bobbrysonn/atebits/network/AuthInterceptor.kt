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
            builder.header("x-twitter-client-language", "en")
            builder.header("priority", "u=1, i")
            
            // Add Transaction ID
            val path = originalRequest.url.encodedPath
            val transactionId = TransactionIdHelper().getTransactionId(path)
            if (transactionId != null) {
                builder.header("x-client-transaction-id", transactionId)
            }
        }

        return chain.proceed(builder.build())
    }
}
