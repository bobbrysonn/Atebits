package dev.bobbrysonn.atebits.network

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit

class TransactionIdHelper {

    private val json = Json { ignoreUnknownKeys = true }
    private val domain = "https://x-client-transaction-id-generator.xyz"

    private val api: TransactionIdApi

    init {
        val contentType = "application/json".toMediaType()
        val retrofit = Retrofit.Builder()
            .baseUrl(domain)
            .client(OkHttpClient())
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()

        api = retrofit.create(TransactionIdApi::class.java)
    }

    fun getTransactionId(path: String): String? {
        return try {
            runBlocking {
                api.generateTransactionId(path).transactionId
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
