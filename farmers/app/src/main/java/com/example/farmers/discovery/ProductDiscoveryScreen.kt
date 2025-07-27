package com.example.farmers.discovery

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.farmers.R
import com.example.farmers.ui.theme.PrimaryGreen
import com.example.farmers.ui.theme.white
import kotlinx.coroutines.launch

@Composable
fun getProductImage(productType: String): Int {
    // A placeholder logic for getting product images.
    return when (productType.lowercase().split(" ").first()) {
        "crops" -> R.drawable.farmer_products
        "pesticides" -> R.drawable.pesticide_image
        "fertilizer" -> R.drawable.fertilizer_image
        "tools" -> R.drawable.tool_image
        "seeds" -> R.drawable.seed_image
        else -> R.drawable.farmer_products
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDiscoveryScreen(
    viewModel: ProductDiscoveryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    // Effects for showing Toasts
    LaunchedEffect(uiState.ratingSuccessMessage) {
        uiState.ratingSuccessMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.onRatingMessageConsumed()
        }
    }
    LaunchedEffect(uiState.ratingErrorMessage) {
        uiState.ratingErrorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.onRatingMessageConsumed()
        }
    }

    Scaffold(
        containerColor = Color(0xFFF7F9F9),
        topBar = {
            val title = when (uiState.currentView) {
                DiscoveryView.PRODUCTS -> "Discover Products"
                DiscoveryView.CART -> "Your Cart"
                DiscoveryView.ORDERS -> "Your Orders"
            }
            CenterAlignedTopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = PrimaryGreen,
                    titleContentColor = white
                ),
                navigationIcon = {
                    if (uiState.currentView != DiscoveryView.PRODUCTS) {
                        IconButton(onClick = { viewModel.showView(DiscoveryView.PRODUCTS) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = white)
                        }
                    }
                },
                actions = {
                    if (uiState.currentView == DiscoveryView.PRODUCTS) {
                        TextButton(onClick = { viewModel.showView(DiscoveryView.ORDERS) }) {
                            Text("Your Orders", color = white)
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (uiState.currentView == DiscoveryView.PRODUCTS) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.showView(DiscoveryView.CART) },
                    containerColor = PrimaryGreen,
                    contentColor = white,
                    icon = {
                        BadgedBox(badge = {
                            if (uiState.cartItems.isNotEmpty()) {
                                Badge { Text("${uiState.cartItems.size}") }
                            }
                        }) {
                            Icon(Icons.Default.ShoppingCart, "Cart")
                        }
                    },
                    text = { Text("View Cart") }
                )
            }
        }
    ) { paddingValues ->
        when (uiState.currentView) {
            DiscoveryView.PRODUCTS -> {
                ProductListContent(
                    modifier = Modifier.padding(paddingValues),
                    uiState = uiState,
                    onCategorySelected = viewModel::onCategorySelected,
                    onQuantityChanged = viewModel::onQuantityChanged
                )
            }
            DiscoveryView.CART -> {
                CartScreenContent(
                    modifier = Modifier.padding(paddingValues),
                    uiState = uiState,
                    onQuantityChanged = viewModel::onQuantityChanged,
                    onPurchase = {
                        coroutineScope.launch {
                            val status = viewModel.purchaseCartItems()
                            val message = when (status) {
                                "success" -> "Purchase successful!"
                                "partial_success" -> "Some items could not be purchased."
                                "failed" -> "Purchase failed. Please try again."
                                else -> "An error occurred."
                            }
                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        }
                    },
                    onBack = { viewModel.showView(DiscoveryView.PRODUCTS) }
                )
            }
            DiscoveryView.ORDERS -> {
                OrdersScreenContent(
                    modifier = Modifier.padding(paddingValues),
                    uiState = uiState,
                    onBack = { viewModel.showView(DiscoveryView.PRODUCTS) },
                    onRateOrder = { orderId, productId, rating ->
                        viewModel.rateProduct(orderId, productId, rating)
                    }
                )
            }
        }
    }
}

@Composable
fun ProductListContent(
    modifier: Modifier = Modifier,
    uiState: ProductDiscoveryUiState,
    onCategorySelected: (String) -> Unit,
    onQuantityChanged: (Product, Int) -> Unit
) {
    val filteredProducts = uiState.products.filter {
        uiState.selectedCategory.equals("All", ignoreCase = true) || it.productType.equals(uiState.selectedCategory, ignoreCase = true)
    }

    Column(modifier = modifier) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(uiState.categories) { category ->
                val isSelected = category.equals(uiState.selectedCategory, ignoreCase = true)
                FilterChip(
                    selected = isSelected,
                    onClick = { onCategorySelected(category) },
                    label = { Text(category.replaceFirstChar { it.uppercaseChar() }) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PrimaryGreen,
                        selectedLabelColor = white
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = Color.Gray.copy(alpha = 0.5f),
                        selectedBorderColor = Color.Transparent
                    )
                )
            }
        }

        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (filteredProducts.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No products available.", style = MaterialTheme.typography.bodyLarge, color = Color.Gray)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(filteredProducts, key = { it.id }) { product ->
                    val quantityInCart = uiState.cartItems[product.id]?.quantity ?: 0
                    ProductCard(
                        product = product,
                        quantityInCart = quantityInCart,
                        onQuantityChanged = { newQuantity -> onQuantityChanged(product, newQuantity) }
                    )
                }
            }
        }
    }
}

@Composable
fun ProductCard(
    product: Product,
    quantityInCart: Int,
    onQuantityChanged: (Int) -> Unit
) {
    val isAvailable = product.quantityAvailable > 0
    val context = LocalContext.current

    Card(
        modifier = Modifier.alpha(if (isAvailable) 1f else 0.7f),
        colors = CardDefaults.cardColors(containerColor = white),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column {
            Image(
                painter = painterResource(id = getProductImage(product.productType)),
                contentDescription = product.productName,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                contentScale = ContentScale.Crop
            )
            Column(
                modifier = Modifier
                    .padding(12.dp)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(product.productName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("by ${product.sellerName}", style = MaterialTheme.typography.bodySmall, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("₹${product.pricePerKg}/kg", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = PrimaryGreen)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = "Rating",
                            tint = Color(0xFFFFC107),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        // Updated Text to show rating and rating count
                        Text(
                            text = if (product.rating != null && product.rating > 0f) {
                                "${product.rating} (${product.ratingCount ?: 0})"
                            } else {
                                "No Rating"
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Text(
                        text = if (isAvailable) "Available: ${product.quantityAvailable} kg" else "Out of Stock",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isAvailable) Color.DarkGray else MaterialTheme.colorScheme.error
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (quantityInCart == 0) {
                    Button(
                        onClick = { onQuantityChanged(1) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = isAvailable,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryGreen,
                            contentColor = white
                        )
                    ) {
                        Text("Add to Cart")
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        OutlinedButton(onClick = { onQuantityChanged(quantityInCart - 1) }, shape = CircleShape, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Remove, "Remove one")
                        }
                        Text(quantityInCart.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        OutlinedButton(
                            onClick = {
                                if (quantityInCart < product.quantityAvailable) onQuantityChanged(quantityInCart + 1)
                                else Toast.makeText(context, "Stock limit reached", Toast.LENGTH_SHORT).show()
                            },
                            shape = CircleShape, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Add, "Add one")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CartScreenContent(
    modifier: Modifier = Modifier,
    uiState: ProductDiscoveryUiState,
    onQuantityChanged: (Product, Int) -> Unit,
    onPurchase: () -> Unit,
    onBack: () -> Unit
) {
    val cartItems = uiState.cartItems.values.toList()
    BackHandler(enabled = true) { onBack() }
    val totalCost = cartItems.sumOf { (it.product.pricePerKg * it.quantity).toDouble() }

    if (cartItems.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Your cart is empty.", style = MaterialTheme.typography.bodyLarge, color = Color.Gray)
        }
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items(cartItems, key = { it.product.id }) { item ->
                CartItemRow(item = item, onQuantityChanged = { onQuantityChanged(item.product, it) })
            }
        }
        Surface(shadowElevation = 8.dp, color = white) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total Cost:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("₹${"%.2f".format(totalCost)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = PrimaryGreen)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onPurchase,
                    enabled = cartItems.isNotEmpty() && !uiState.isPurchasing,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
                ) {
                    if (uiState.isPurchasing) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.Gray, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Purchasing...", color = Color.Gray)
                        }
                    } else {
                        Text("Purchase", fontSize = 16.sp, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun CartItemRow(item: CartItem, onQuantityChanged: (Int) -> Unit) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Image(
            painter = painterResource(id = getProductImage(item.product.productType)),
            contentDescription = item.product.productName,
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(item.product.productName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "₹${"%.2f".format((item.product.pricePerKg * item.quantity).toDouble())}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = PrimaryGreen
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(onClick = { onQuantityChanged(item.quantity - 1) }) {
                Icon(Icons.Default.Remove, "Remove one")
            }
            Text(item.quantity.toString(), style = MaterialTheme.typography.bodyLarge)
            IconButton(
                onClick = {
                    if (item.quantity < item.product.quantityAvailable) {
                        onQuantityChanged(item.quantity + 1)
                    } else {
                        Toast.makeText(context, "Stock limit reached for ${item.product.productName}", Toast.LENGTH_SHORT).show()
                    }
                }
            ) {
                Icon(Icons.Default.Add, "Add one")
            }
        }
    }
}


@Composable
fun OrdersScreenContent(
    modifier: Modifier = Modifier,
    uiState: ProductDiscoveryUiState,
    onBack: () -> Unit,
    onRateOrder: (String, String, Int) -> Unit
) {
    BackHandler(enabled = true) { onBack() }

    if (uiState.isFetchingOrders) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (uiState.orders.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("You have no past orders.", style = MaterialTheme.typography.bodyLarge, color = Color.Gray)
        }
        return
    }

    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        items(uiState.orders, key = { it.orderId }) { order ->
            OrderCard(order = order, onRateOrder = { rating -> onRateOrder(order.orderId, order.productId, rating) })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderCard(order: Order, onRateOrder: (Int) -> Unit) {
    var isRatingVisible by remember { mutableStateOf(false) }
    var currentRating by remember { mutableStateOf(0) }

    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = white), shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = getProductImage(order.productName)),
                    contentDescription = order.productName,
                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(order.productName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Quantity: ${order.quantity} kg", style = MaterialTheme.typography.bodyMedium)
                    Text(order.orderDate, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
                StatusChip(status = order.status)
            }
            if (order.status.lowercase() == "delivered") {
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                AnimatedVisibility(visible = isRatingVisible) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Tap a star to rate", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        Row(modifier = Modifier.padding(vertical = 8.dp)) {
                            (1..5).forEach { rating ->
                                IconButton(onClick = { currentRating = rating }) {
                                    Icon(
                                        imageVector = if (rating <= currentRating) Icons.Default.Star else Icons.Default.StarBorder,
                                        contentDescription = "Rate $rating",
                                        tint = if (rating <= currentRating) Color(0xFFFFC107) else Color.Gray
                                    )
                                }
                            }
                        }
                        Button(
                            onClick = {
                                onRateOrder(currentRating)
                                isRatingVisible = false
                            },
                            enabled = currentRating > 0,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Submit Rating")
                        }
                    }
                }
                if (!isRatingVisible) {
                    Button(
                        onClick = { isRatingVisible = true },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Rate Order")
                    }
                }
            }
        }
    }
}

@Composable
fun StatusChip(status: String) {
    val backgroundColor = when (status.lowercase()) {
        "delivered" -> PrimaryGreen.copy(alpha = 0.1f)
        "dispatched", "shipped" -> Color(0xFFFFF8E1)
        else -> Color.Gray.copy(alpha = 0.1f)
    }
    val contentColor = when (status.lowercase()) {
        "delivered" -> PrimaryGreen
        "dispatched", "shipped" -> Color(0xFFFFA000)
        else -> Color.DarkGray
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(backgroundColor)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(text = status.replaceFirstChar { it.uppercase() }, color = contentColor, fontWeight = FontWeight.Medium)
    }
}
