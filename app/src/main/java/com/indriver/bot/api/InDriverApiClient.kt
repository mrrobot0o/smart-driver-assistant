package com.indriver.bot.api

import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import com.google.gson.GsonBuilder
import java.util.concurrent.TimeUnit

/**
 * API Client for inDriver backend
 * Based on reverse-engineered API endpoints
 */
object InDriverApiClient {

    private const val BASE_URL = "https://super-services.indriverapp.com/"
    private const val NEW_ORDER_URL = "https://new-order.eu-east-1.indriverapp.com/"
    
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Accept", "application/json")
                    .addHeader("User-Agent", "inDriver/5.8.1 Android")
                    .addHeader("X-Api-Key", "indriver_mobile_api")
                    .build()
                chain.proceed(request)
            }
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(
                GsonBuilder()
                    .setLenient()
                    .serializeNulls()
                    .create()
            ))
            .build()
    }

    val api: InDriverApi by lazy {
        retrofit.create(InDriverApi::class.java)
    }
}

/**
 * inDriver API Interface
 */
interface InDriverApi {

    @GET("api/v1/orders/available")
    suspend fun getAvailableOrders(
        @Query("lat") latitude: Double,
        @Query("lng") longitude: Double,
        @Query("radius") radiusKm: Double = 10.0
    ): OrdersResponse

    @POST("api/v1/orders/{orderId}/accept")
    suspend fun acceptOrder(
        @Path("orderId") orderId: String,
        @Body request: AcceptOrderRequest
    ): AcceptOrderResponse

    @POST("api/v1/orders/{orderId}/bid")
    suspend fun placeBid(
        @Path("orderId") orderId: String,
        @Body request: BidRequest
    ): BidResponse

    @GET("api/v1/driver/status")
    suspend fun getDriverStatus(): DriverStatusResponse

    @GET("api/v1/driver/earnings")
    suspend fun getEarnings(): EarningsResponse
}

// Data classes for API responses

data class OrdersResponse(
    val success: Boolean,
    val orders: List<Order>?,
    val error: String?
)

data class Order(
    val id: String,
    val pickup: Location,
    val dropoff: Location,
    val passenger: Passenger,
    val suggestedPrice: Double,
    val distance: Double,
    val duration: Int,
    val createdAt: Long,
    val expiresAt: Long
)

data class Location(
    val lat: Double,
    val lng: Double,
    val address: String
)

data class Passenger(
    val id: String,
    val name: String,
    val rating: Double,
    val totalRides: Int
)

data class AcceptOrderRequest(
    val driverId: String,
    val estimatedArrival: Int,
    val acceptedAt: Long = System.currentTimeMillis()
)

data class AcceptOrderResponse(
    val success: Boolean,
    val orderId: String?,
    val message: String?
)

data class BidRequest(
    val driverId: String,
    val price: Double,
    val estimatedArrival: Int
)

data class BidResponse(
    val success: Boolean,
    val bidId: String?,
    val status: String?
)

data class DriverStatusResponse(
    val online: Boolean,
    val currentLocation: Location?,
    val activeOrder: Order?
)

data class EarningsResponse(
    val today: Double,
    val week: Double,
    val month: Double,
    val totalRides: Int
)
