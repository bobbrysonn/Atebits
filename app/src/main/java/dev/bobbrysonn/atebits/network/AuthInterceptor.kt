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
            builder.header("Authorization", session.authorization)
            builder.header("Cookie", session.cookieString)
            builder.header("x-csrf-token", session.csrfToken)
            
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
