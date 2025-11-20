package dev.bobbrysonn.atebits.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

interface TransactionIdApi {
    @GET("/generate-x-client-transaction-id")
    suspend fun generateTransactionId(@Query("path") path: String): TransactionIdResponse
}

@Serializable
data class TransactionIdResponse(
    @SerialName("x-client-transaction-id")
    val transactionId: String
)
