package com.example.farmers.home.delivery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.farmers.data.UserManager
import com.example.farmers.service.ApiOrder
import com.example.farmers.service.DeliveryOrderRequest
import com.example.farmers.service.FirebaseApiService
import com.example.farmers.service.UpdateOrderStatusRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// --- UI State ---
data class DeliveryHomeUiState(
    val orders: List<ApiOrder> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val updatingOrderId: String? = null // To track which order is being updated
)

// --- ViewModel ---
@HiltViewModel
class DeliveryHomeViewModel @Inject constructor(
    private val userManager: UserManager,
    private val apiService: FirebaseApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeliveryHomeUiState())
    val uiState = _uiState.asStateFlow()

    init {
        fetchOrders()
    }

    fun fetchOrders() {
        viewModelScope.launch {
            val userId = userManager.getUserId()
            if (userId == null) {
                _uiState.update { it.copy(error = "User not logged in.", isLoading = false) }
                return@launch
            }

            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val response = apiService.listDeliveryOrders(DeliveryOrderRequest(agent_id = userId))
                _uiState.update {
                    it.copy(
                        orders = response.orders,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        error = "Failed to load orders: ${e.message}",
                        isLoading = false
                    )
                }
            }
        }
    }

    /**
     * Marks a specific order as "delivered".
     * It calls an API to update the status and then refreshes the order list.
     */
    fun markAsDelivered(orderId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(updatingOrderId = orderId) }

            val userId = userManager.getUserId()
            if (userId == null) {
                _uiState.update { it.copy(error = "User not logged in.", updatingOrderId = null) }
                return@launch
            }

            try {
                val request = UpdateOrderStatusRequest(order_id = orderId, status = "delivered", agent_id = userId)
                apiService.updateOrderStatus(request)

                fetchOrders()

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = "Failed to update order status: ${e.message}")
                }
            } finally {
                _uiState.update { it.copy(updatingOrderId = null) }
            }
        }
    }

    fun logout(onLogoutComplete: () -> Unit) {
        viewModelScope.launch {
            userManager.setDeliveryLoggedIn(false)
            userManager.setSessionId(null)
            userManager.setUserId(null)
            onLogoutComplete()
        }
    }
}
