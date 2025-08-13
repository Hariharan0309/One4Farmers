package com.example.farmers.discovery

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.farmers.data.UserManager
import com.example.farmers.service.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

// Enum to manage which view is visible on the screen
enum class DiscoveryView {
    PRODUCTS,
    CART,
    ORDERS
}

// UI Models
data class Product(
    val id: String,
    val productName: String,
    val productType: String,
    val sellerName: String,
    val pricePerKg: Int,
    val quantityAvailable: Int,
    val state: String,
    val district: String,
    val rating: Float?,
    val ratingCount: Int
)

data class CartItem(
    val product: Product,
    val quantity: Int
)

data class Order(
    val orderId: String,
    val orderDate: String,
    val productId: String,
    val productName: String,
    val status: String,
    val quantity: Int
)

data class ProductDiscoveryUiState(
    val products: List<Product> = emptyList(),
    val cartItems: Map<String, CartItem> = emptyMap(),
    val orders: List<Order> = emptyList(),
    val isLoading: Boolean = false,
    val isPurchasing: Boolean = false,
    val isFetchingOrders: Boolean = false,
    val categories: List<String> = listOf("All", "crops", "seeds", "fertilizer", "pesticides", "pesticides"),
    val selectedCategory: String = "All",
    val currentView: DiscoveryView = DiscoveryView.PRODUCTS,
    val ratingSuccessMessage: String? = null,
    val ratingErrorMessage: String? = null
)

@HiltViewModel
class ProductDiscoveryViewModel @Inject constructor(
    private val firebaseApiService: FirebaseApiService,
    private val userManager: UserManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProductDiscoveryUiState())
    val uiState = _uiState.asStateFlow()

    init {
        fetchProducts()
    }

    fun fetchProducts() {
        val userId = userManager.getUserId() ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val response = firebaseApiService.listProducts(ListProductRequest(user_id = userId))
                val products = response.products.map { it.toProduct() }
                _uiState.update { it.copy(products = products, isLoading = false) }
            } catch (e: Exception) {
                Log.e("ProductDiscoveryVM", "API call failed: ${e.message}")
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun fetchOrders() {
        val userId = userManager.getUserId() ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isFetchingOrders = true) }
            try {
                val response = firebaseApiService.listOrders(ListOrderRequest(user_id = userId))
                val orders = response.orders.map { it.toOrder() }
                _uiState.update { it.copy(orders = orders, isFetchingOrders = false) }
            } catch (e: Exception) {
                Log.e("ProductDiscoveryVM", "Failed to fetch orders: ${e.message}")
                _uiState.update { it.copy(isFetchingOrders = false) }
            }
        }
    }

    suspend fun purchaseCartItems(): String {
        _uiState.update { it.copy(isPurchasing = true) }
        val userId = userManager.getUserId()
        val cart = _uiState.value.cartItems

        if (userId == null || cart.isEmpty()) {
            _uiState.update { it.copy(isPurchasing = false) }
            return "failed"
        }

        val itemsToPurchase = cart.values.map { cartItem ->
            PurchaseItem(product_id = cartItem.product.id, quantity = cartItem.quantity)
        }

        val request = PurchaseRequest(user_id = userId, product_list = itemsToPurchase)

        return try {
            val response = firebaseApiService.purchaseProducts(request)
            if (response.status == "success" || response.status == "partial_success") {
                _uiState.update { it.copy(cartItems = emptyMap(), currentView = DiscoveryView.PRODUCTS) }
                fetchProducts()
            }
            response.status
        } catch (e: Exception) {
            Log.e("PurchaseVM", "Purchase API call failed: ${e.message}")
            "error"
        } finally {
            _uiState.update { it.copy(isPurchasing = false) }
        }
    }

    fun onCategorySelected(category: String) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    fun onQuantityChanged(product: Product, quantity: Int) {
        viewModelScope.launch {
            val currentCart = _uiState.value.cartItems.toMutableMap()
            val newQuantity = quantity.coerceIn(0, product.quantityAvailable)

            if (newQuantity > 0) {
                currentCart[product.id] = CartItem(product = product, quantity = newQuantity)
            } else {
                currentCart.remove(product.id)
            }
            _uiState.update { it.copy(cartItems = currentCart) }
        }
    }

    fun rateProduct(orderId: String, productId: String, rating: Int) {
        val userId = userManager.getUserId()
        if (userId == null) {
            _uiState.update { it.copy(ratingErrorMessage = "You must be logged in to rate.") }
            return
        }

        viewModelScope.launch {
            try {
                val request = RateProductRequest(
                    user_id = userId,
                    rating = rating,
                    order_id = orderId,
                    product_id = productId
                )
                firebaseApiService.rateProduct(request)

                fetchOrders()
                fetchProducts()

                _uiState.update { it.copy(ratingSuccessMessage = "Thank you for your feedback!") }
            } catch (e: Exception) {
                Log.e("ProductDiscoveryVM", "Failed to rate product: ${e.message}")
                _uiState.update { it.copy(ratingErrorMessage = "Failed to submit rating.") }
            }
        }
    }

    fun onRatingMessageConsumed() {
        _uiState.update { it.copy(ratingSuccessMessage = null, ratingErrorMessage = null) }
    }

    fun showView(view: DiscoveryView) {
        _uiState.update { it.copy(currentView = view) }
        if (view == DiscoveryView.ORDERS) {
            fetchOrders()
        }
    }
}

// Mapper functions
private fun ApiProduct.toProduct(): Product {
    return Product(
        id = this.product_id,
        productName = this.product_name,
        productType = this.product_type,
        sellerName = this.seller_name,
        pricePerKg = this.price_per_kg,
        quantityAvailable = this.quantity_available,
        state = this.state,
        district = this.district,
        rating = this.rating,
        ratingCount = this.rating_count
    )
}

private fun ApiOrder.toOrder(): Order {
    val formattedDate = try {
        val odt = OffsetDateTime.parse(this.order_time)
        val formatter = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a")
        odt.format(formatter)
    } catch (e: Exception) {
        this.order_time
    }

    return Order(
        orderId = this.order_id,
        orderDate = formattedDate,
        productName = this.product_name,
        status = this.status,
        quantity = this.quantity,
        productId = this.product_id
    )
}