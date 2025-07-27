package com.example.farmers.sell

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddBusiness
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.farmers.service.ApiProduct
import com.example.farmers.ui.theme.PrimaryGreen
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SellProductScreen(
    viewModel: SellProductViewModel = hiltViewModel(),
    onBackClick: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var isViewingShop by remember { mutableStateOf(false) }

    // Effect for handling the result of posting an ad
    LaunchedEffect(uiState.postAdSuccess) {
        if (uiState.postAdSuccess) {
            Toast.makeText(context, "Product posted successfully!", Toast.LENGTH_SHORT).show()
            // Switch to the shop view and refresh the product list
            isViewingShop = true
            viewModel.fetchYourProducts()
            viewModel.onNavigationConsumed()
        }
    }
    LaunchedEffect(uiState.postAdError) {
        uiState.postAdError?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.onNavigationConsumed()
        }
    }

    Scaffold(
        containerColor = Color(0xFFF7F9F9),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(if (isViewingShop) "Your Shop" else "Sell Your Product") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isViewingShop) {
                            isViewingShop = false // Go back to the form
                        } else {
                            onBackClick() // Exit the screen
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        isViewingShop = !isViewingShop
                        if (isViewingShop) {
                            viewModel.fetchYourProducts()
                        }
                    }) {
                        Icon(
                            imageVector = if (isViewingShop) Icons.Default.AddBusiness else Icons.Default.Storefront,
                            contentDescription = "Toggle Shop View",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = PrimaryGreen,
                    titleContentColor = Color.White,
                )
            )
        }
    ) { paddingValues ->
        if (isViewingShop) {
            YourShopContent(
                uiState = uiState,
                onRefresh = { viewModel.fetchYourProducts() },
                modifier = Modifier.padding(paddingValues)
            )
        } else {
            SellFormContent(
                viewModel = viewModel,
                uiState = uiState,
                modifier = Modifier.padding(paddingValues)
            )
        }
    }
}

@Composable
private fun SellFormContent(
    viewModel: SellProductViewModel,
    uiState: SellProductUiState,
    modifier: Modifier = Modifier
) {
    val categories = viewModel.categories
    val productList = viewModel.productMap[uiState.selectedCategory] ?: emptyList()

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            ProductDropdown(
                label = "Product Category",
                options = categories,
                selectedOption = uiState.selectedCategory,
                onOptionSelected = viewModel::onCategoryChange,
                capitalize = true
            )
            ProductDropdown(
                label = "Product Name",
                options = productList,
                selectedOption = uiState.selectedProduct,
                onOptionSelected = viewModel::onProductSelected,
                enabled = productList.isNotEmpty()
            )
            if (uiState.selectedProduct.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = uiState.price,
                        onValueChange = viewModel::onPriceChange,
                        modifier = Modifier.weight(1f),
                        label = { Text(if (uiState.selectedCategory == "tools") "Price per unit" else "Price per kg") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = professionalTextFieldColors()
                    )
                    Button(
                        onClick = { viewModel.fetchMarketAnalysis() },
                        enabled = !uiState.isFetchingAnalysis,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen.copy(alpha = 0.1f), contentColor = PrimaryGreen)
                    ) {
                        Text("Analyze")
                    }
                }
                MarketAnalysisResult(uiState)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = uiState.expiryInDays,
                    onValueChange = viewModel::onExpiryChange,
                    modifier = Modifier.weight(1f),
                    label = { Text("Expiry (days)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    colors = professionalTextFieldColors()
                )
                OutlinedTextField(
                    value = uiState.quantity,
                    onValueChange = viewModel::onQuantityChange,
                    modifier = Modifier.weight(1f),
                    label = { Text(if (uiState.selectedCategory == "tools") "Units" else "Total Kgs") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    colors = professionalTextFieldColors()
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(16.dp)
        ) {
            Button(
                onClick = viewModel::postProductAd,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                enabled = uiState.selectedProduct.isNotBlank() && uiState.price.isNotBlank() && uiState.quantity.isNotBlank() && uiState.expiryInDays.isNotBlank() && !uiState.isPostingAd,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryGreen,
                    contentColor = Color.White,
                    disabledContainerColor = Color.Gray.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (uiState.isPostingAd) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 3.dp
                    )
                } else {
                    Text("Post for Sale", fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun YourShopContent(
    uiState: SellProductUiState,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sellingProducts = uiState.sellingProducts
    val isLoading = uiState.isLoadingShop
    val error = uiState.shopError

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            isLoading -> CircularProgressIndicator(color = PrimaryGreen)
            error != null -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(error, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onRefresh, colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Retry")
                    }
                }
            }
            sellingProducts.isEmpty() -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Storefront,
                        contentDescription = "Empty Shop",
                        modifier = Modifier.size(64.dp),
                        tint = Color.Gray.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Your shop is empty.", style = MaterialTheme.typography.titleMedium, color = Color.Gray)
                    Text("Products you post for sale will appear here.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onRefresh, colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Refresh")
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(sellingProducts) { product ->
                        SellingProductCard(product = product)
                    }
                }
            }
        }
    }
}

@Composable
private fun SellingProductCard(product: ApiProduct) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(product.product_name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            Spacer(modifier = Modifier.height(8.dp))

            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Price: ₹${product.price_per_kg}/${if (product.product_type == "tools") "unit" else "kg"}", color = PrimaryGreen, fontWeight = FontWeight.SemiBold)
                Text("Available: ${product.quantity_available} ${if (product.product_type == "tools") "units" else "kgs"}", color = Color.DarkGray)
            }
        }
    }
}


@Composable
private fun MarketAnalysisResult(uiState: SellProductUiState) {
    if (uiState.isFetchingAnalysis) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = PrimaryGreen,
                strokeWidth = 2.dp
            )
            Spacer(Modifier.width(8.dp))
            Text("Fetching market analysis...", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
    } else if (uiState.marketAnalysisResult != null) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = PrimaryGreen.copy(alpha = 0.05f)),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Text(
                text = uiState.marketAnalysisResult.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.DarkGray,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductDropdown(
    label: String,
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit,
    enabled: Boolean = true,
    capitalize: Boolean = false
) {
    var isExpanded by remember { mutableStateOf(false) }

    val selectedText = if (capitalize && selectedOption.isNotEmpty()) {
        selectedOption.replaceFirstChar { it.uppercase() }
    } else {
        selectedOption
    }

    ExposedDropdownMenuBox(
        expanded = isExpanded && enabled,
        onExpandedChange = { if (enabled) isExpanded = it }
    ) {
        OutlinedTextField(
            value = selectedText,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isExpanded) },
            colors = professionalTextFieldColors(),
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )

        ExposedDropdownMenu(
            expanded = isExpanded && enabled,
            onDismissRequest = { isExpanded = false },
            modifier = Modifier.background(Color.White)
        ) {
            options.forEach { option ->
                val displayText = if (capitalize) option.replaceFirstChar { it.uppercase() } else option
                DropdownMenuItem(
                    text = { Text(text = displayText) },
                    onClick = {
                        onOptionSelected(option)
                        isExpanded = false
                    },
                    colors = MenuDefaults.itemColors(textColor = Color.Black)
                )
            }
        }
    }
}

@Composable
private fun professionalTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.Black,
    unfocusedTextColor = Color.Black,
    disabledTextColor = Color.Gray,
    focusedBorderColor = PrimaryGreen,
    unfocusedBorderColor = Color.LightGray,
    focusedLabelColor = PrimaryGreen,
    cursorColor = PrimaryGreen,
    focusedContainerColor = Color.White,
    unfocusedContainerColor = Color.White,
    disabledContainerColor = Color.LightGray.copy(alpha = 0.2f)
)

// Helper function to format the date string
fun formatListedDate(dateTimeString: String?): String {
    if (dateTimeString == null) return "N/A"
    return try {
        val odt = OffsetDateTime.parse(dateTimeString)
        val formatter = DateTimeFormatter.ofPattern("dd MMM yyyy")
        odt.format(formatter)
    } catch (e: Exception) {
        "N/A"
    }
}
