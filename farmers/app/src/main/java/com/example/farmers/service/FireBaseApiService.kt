package com.example.farmers.service

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.Response

interface FirebaseApiService {
    @POST("get_or_create_session")
    suspend fun createSession(@Body request: CreateSessionRequest): CreateSessionResponse

    @POST("stream_query_agent")
    suspend fun streamQueryAgent(@Body request: StreamQueryRequest): StreamQueryResponse

    @POST("list_products")
    suspend fun listProducts(@Body request: ListProductRequest): ListProductsResponse

    @POST("purchase_product")
    suspend fun purchaseProducts(@Body request: PurchaseRequest): PurchaseResponse

    @POST("list_orders")
    suspend fun listOrders(@Body request: ListOrderRequest): ListOrdersResponse

    @POST("get_agent_dashboard_orders")
    suspend fun listDeliveryOrders(@Body request: DeliveryOrderRequest): ListDeliveryOrdersResponse

    @POST("delivery_update")
    suspend fun updateOrderStatus(@Body request: UpdateOrderStatusRequest)

    @POST("sell_product")
    suspend fun sellProduct(@Body request: SellProductRequest)

    @POST("list_user_products")
    suspend fun listUserProducts(@Body request: UserProductsRequest): ListUserProductsResponse

    @POST("rate_product")
    suspend fun rateProduct(@Body request: RateProductRequest)

    // community related

    @GET("getCommunityMessages")
    suspend fun getCommunityMessages(): CommunityMessagesResponse

    @POST("sendCommunityMessage")
    suspend fun sendCommunityMessage(@Body request: SendCommunityMessageRequest): Response<Unit>
}

// session creation
data class CreateSessionRequest(val user_id: String?, val state: RequestState?)
data class RequestState(
    val experience: Int? = null,
    val name: String? = null,
    val user_id: String?,
    val state: String? = null,
    val acres: Int? = null,
    val district: String? = null,
    val weather_last_updated: String? = null,
    val language: String? = null,
    val weather_forecast: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val timeZone: String? = null,
    val crops: List<String>? = emptyList()
)
data class CreateSessionResponse(val session_id: String, val state: UserState)

data class UserState(
    val longitude: Double,
    val latitude: Double,
    val weather_last_updated: String,
    val timeZone: String,
    val name: String,
    val experience: Double,
    val district: String,
    val state: String,
    val crops: List<String>,
    val language: String,
    val user_id: String
)

//  chatbot
data class StreamQueryRequest(
    val user_id: String,
    val session_id: String,
    val message: String?,
    val audio_url: String?,
    val image_url: String?
)
data class StreamQueryResponse(val response: String)

// product listing
data class ListProductRequest(val user_id: String)
data class ListProductsResponse(
    val products: List<ApiProduct>
)

data class ApiProduct(
    val product_id: String,
    val product_name: String,
    val product_type: String,
    val seller_name: String,
    val price_per_kg: Int,
    val quantity_available: Int,
    val state: String,
    val district: String,
    val rating: Float?,
    val rating_count: Int
)

// Product Buying
data class PurchaseItem(
    val product_id: String,
    val quantity: Int
)

data class PurchaseRequest(
    val user_id: String,
    val product_list: List<PurchaseItem>
)

data class PurchaseResponse(
    val status: String
)

// list orders
data class ListOrderRequest(val user_id: String)

data class ListOrdersResponse(
    val orders: List<ApiOrder>
)

data class ApiOrder(
    val order_id: String,
    val order_time: String,
    val product_name: String,
    val product_id: String,
    val status: String,
    val quantity: Int,
    val seller_id: String,
    val agent_assigned: String?,
    val buyer_id: String
)

// delivery orders
data class DeliveryOrderRequest(
    val agent_id: String
)

data class ListDeliveryOrdersResponse(
    val orders: List<ApiOrder>
)

data class UpdateOrderStatusRequest(
    val order_id: String,
    val agent_id: String,
    val status: String
)

// sell product
data class SellProductRequest(
    val user_id: String,
    val product_name: String,
    val product_type: String,
    val price_per_kg: Int,
    val quantity_available: Int,
    val expiry_in_days: Int,
)

data class UserProductsRequest(
    val user_id: String
)

data class ListUserProductsResponse(
    val products: List<ApiProduct>
)

// rating
data class RateProductRequest(
    val user_id: String,
    val rating: Int,
    val order_id: String,
    val product_id: String
)


// community

data class CommunityMessagesResponse(
    @SerializedName("messages")
    val messages: List<CommunityMessage>
)

data class CommunityMessage(
    @SerializedName("senderId")
    val senderId: String,

    @SerializedName("senderName")
    val senderName: String,

    @SerializedName("text")
    val text: String,

    @SerializedName("text_ta")
    val text_ta: String?,

    @SerializedName("text_hi")
    val text_hi: String?,

    @SerializedName("audio_url")
    val audio_url: String?,

    @SerializedName("timestamp")
    val timestamp: String?
)

data class SendCommunityMessageRequest(
    @SerializedName("senderId")
    val senderId: String,

    @SerializedName("senderName")
    val senderName: String,

    @SerializedName("text")
    val text: String,

    @SerializedName("audio_url")
    val audio_url: String?
)

