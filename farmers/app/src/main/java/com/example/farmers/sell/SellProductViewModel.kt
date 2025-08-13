package com.example.farmers.sell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.farmers.data.UserManager
import com.example.farmers.service.ApiProduct
import com.example.farmers.service.FirebaseApiService
import com.example.farmers.service.SellProductRequest
import com.example.farmers.service.StreamQueryRequest
import com.example.farmers.service.UserProductsRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// --- UI State ---
data class SellProductUiState(
    // Form state
    val selectedCategory: String = "crops",
    val selectedProduct: String = "",
    val price: String = "",
    val quantity: String = "",
    val expiryInDays: String = "",
    val isFetchingAnalysis: Boolean = false,
    val marketAnalysisResult: String? = null,
    val isPostingAd: Boolean = false,
    val postAdSuccess: Boolean = false,
    val postAdError: String? = null,

    // "Your Shop" state
    val sellingProducts: List<ApiProduct> = emptyList(),
    val isLoadingShop: Boolean = false,
    val shopError: String? = null
)


@HiltViewModel
class SellProductViewModel @Inject constructor(
    private val userManager: UserManager,
    private val apiService: FirebaseApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow(SellProductUiState())
    val uiState = _uiState.asStateFlow()

    val categories: List<String> = listOf("crops", "seeds", "fertilizer", "pesticides", "tools")

    val productMap: Map<String, List<String>> = mapOf(
        "crops" to listOf("Tomato", "Potato", "Lemon", "Paddy", "Wheat"),
        "seeds" to listOf("Paddy Seeds", "Wheat Seeds", "Corn Seeds"),
        "fertilizer" to listOf("Urea", "Potash", "DAP"),
        "pesticides" to listOf("Neem Oil", "Insecticidal Soap"),
        "tools" to listOf("Spade", "Hand Trowel", "Watering Can")
    )

    fun onCategoryChange(category: String) {
        _uiState.update {
            it.copy(
                selectedCategory = category,
                selectedProduct = "",
                marketAnalysisResult = null
            )
        }
    }

    fun onProductSelected(product: String) {
        _uiState.update {
            it.copy(
                selectedProduct = product,
                marketAnalysisResult = null
            )
        }
    }

    fun onPriceChange(price: String) {
        _uiState.update { it.copy(price = price) }
    }

    fun onQuantityChange(quantity: String) {
        _uiState.update { it.copy(quantity = quantity) }
    }

    fun onExpiryChange(days: String) {
        if (days.all { it.isDigit() }) {
            _uiState.update { it.copy(expiryInDays = days) }
        }
    }

    fun fetchMarketAnalysis() {
        val currentState = _uiState.value
        if (currentState.selectedProduct.isBlank() || currentState.isFetchingAnalysis) return

        viewModelScope.launch {
            _uiState.update { it.copy(isFetchingAnalysis = true, marketAnalysisResult = null) }

            val userId = userManager.getUserId()
            val sessionId = userManager.getSessionId()

            if (userId == null || sessionId == null) {
                _uiState.update {
                    it.copy(
                        isFetchingAnalysis = false,
                        marketAnalysisResult = "Error: Could not find user session. Please restart the app."
                    )
                }
                return@launch
            }

            try {
                val request = StreamQueryRequest(
                    user_id = userId,
                    session_id = sessionId,
                    message = "Can you give me the current market price in rupees/kg or rupees/unit for the product ${_uiState.value.selectedProduct}",
                    audio_url = null,
                    image_url = null,
                )
                val response = apiService.streamQueryAgent(request)
                _uiState.update {
                    it.copy(
                        isFetchingAnalysis = false,
                        marketAnalysisResult = response.response
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isFetchingAnalysis = false,
                        marketAnalysisResult = "Error: Failed to fetch analysis. ${e.message}"
                    )
                }
            }
        }
    }

    fun fetchYourProducts() {
        viewModelScope.launch {
            val userId = userManager.getUserId()
            if (userId == null) {
                _uiState.update { it.copy(shopError = "You must be logged in to view your shop.") }
                return@launch
            }

            _uiState.update { it.copy(isLoadingShop = true, shopError = null) }
            try {
                val request = UserProductsRequest(user_id = userId)
                val response = apiService.listUserProducts(request)
                _uiState.update {
                    it.copy(
                        sellingProducts = response.products,
                        isLoadingShop = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoadingShop = false,
                        shopError = "Failed to load your products: ${e.message}"
                    )
                }
            }
        }
    }

    fun postProductAd() {
        viewModelScope.launch {
            val userId = userManager.getUserId()
            if (userId == null) {
                _uiState.update { it.copy(postAdError = "You must be logged in to sell.") }
                return@launch
            }

            _uiState.update { it.copy(isPostingAd = true, postAdError = null) }

            try {
                val request = SellProductRequest(
                    user_id = userId,
                    product_name = _uiState.value.selectedProduct,
                    product_type = _uiState.value.selectedCategory,
                    price_per_kg = _uiState.value.price.toIntOrNull() ?: 0,
                    quantity_available = _uiState.value.quantity.toIntOrNull() ?: 0,
                    expiry_in_days = _uiState.value.expiryInDays.toIntOrNull() ?: 0
                )
                apiService.sellProduct(request)

                _uiState.update { it.copy(isPostingAd = false, postAdSuccess = true) }

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isPostingAd = false,
                        postAdError = "Failed to post ad: ${e.message}"
                    )
                }
            }
        }
    }

    fun onNavigationConsumed() {
        _uiState.update { it.copy(postAdSuccess = false, postAdError = null) }
    }
}
