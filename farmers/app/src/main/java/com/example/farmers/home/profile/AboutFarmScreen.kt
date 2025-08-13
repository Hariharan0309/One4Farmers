package com.example.farmers.home.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.farmers.ui.theme.PrimaryGreen
import com.example.farmers.ui.theme.white
import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.ContextCompat
import com.example.farmers.ui.theme.SecondaryGreen
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutFarmScreen(
    viewModel: AboutFarmViewModel = hiltViewModel(),
    navigateToHome: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    var isFetchingLocation by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val isFineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val isCoarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        if (isFineLocationGranted || isCoarseLocationGranted) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                val cancellationTokenSource = CancellationTokenSource()
                fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    cancellationTokenSource.token
                ).addOnSuccessListener { location ->
                    location?.let {
                        viewModel.updateGpsLocation(it.latitude, it.longitude)
                    }
                    isFetchingLocation = false
                }.addOnFailureListener {
                    isFetchingLocation = false
                }
            }
        } else {
            isFetchingLocation = false
            println("Location permission was denied.")
        }
    }

    Scaffold(
        containerColor = white,
        topBar = {
            TopAppBar(
                title = {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("About Your Farm")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PrimaryGreen, titleContentColor = white)
            )
        },
        content = { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    SectionTitle("General Details")

                    ExposedDropdownMenuBox(
                        expanded = viewModel.isLanguageMenuExpanded,
                        onExpandedChange = viewModel::onLanguageMenuToggle
                    ) {
                        OutlinedTextField(
                            value = viewModel.selectedLanguage,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Preferred Language") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = viewModel.isLanguageMenuExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            colors = formTextFieldColors()
                        )
                        ExposedDropdownMenu(
                            expanded = viewModel.isLanguageMenuExpanded,
                            onDismissRequest = { viewModel.onLanguageMenuToggle(false) },
                            modifier = Modifier.background(SecondaryGreen)
                        ) {
                            viewModel.languages.forEach { language ->
                                DropdownMenuItem(
                                    text = { Text(language) },
                                    onClick = { viewModel.onLanguageSelected(language) },
                                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // State Dropdown
                    ExposedDropdownMenuBox(
                        expanded = viewModel.isStateMenuExpanded,
                        onExpandedChange = viewModel::onStateMenuToggle
                    ) {
                        OutlinedTextField(
                            value = viewModel.selectedState,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("State") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = viewModel.isStateMenuExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            colors = formTextFieldColors(),
                            isError = viewModel.locationError != null
                        )
                        ExposedDropdownMenu(
                            expanded = viewModel.isStateMenuExpanded,
                            onDismissRequest = { viewModel.onStateMenuToggle(false) },
                            modifier = Modifier.background(SecondaryGreen)
                        ) {
                            viewModel.states.forEach { state ->
                                DropdownMenuItem(
                                    text = { Text(state) },
                                    onClick = { viewModel.onStateSelected(state) },
                                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // District Dropdown
                    ExposedDropdownMenuBox(
                        expanded = viewModel.isDistrictMenuExpanded,
                        onExpandedChange = viewModel::onDistrictMenuToggle
                    ) {
                        OutlinedTextField(
                            value = viewModel.selectedDistrict,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("District") },
                            enabled = viewModel.selectedState.isNotEmpty(),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = viewModel.isDistrictMenuExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            colors = formTextFieldColors(),
                            isError = viewModel.locationError != null,
                            supportingText = { viewModel.locationError?.let { Text(it) } }
                        )
                        ExposedDropdownMenu(
                            expanded = viewModel.isDistrictMenuExpanded,
                            onDismissRequest = { viewModel.onDistrictMenuToggle(false) },
                            modifier = Modifier.background(SecondaryGreen)
                        ) {
                            viewModel.availableDistricts.forEach { district ->
                                DropdownMenuItem(
                                    text = { Text(district) },
                                    onClick = { viewModel.onDistrictSelected(district) },
                                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = viewModel.totalLand,
                        onValueChange = viewModel::onTotalLandChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Total Land Area") },
                        trailingIcon = { Text("Acres", style = MaterialTheme.typography.bodySmall) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        colors = formTextFieldColors(),
                        isError = viewModel.totalLandError != null,
                        supportingText = { viewModel.totalLandError?.let { Text(it) } }
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = viewModel.experience,
                        onValueChange = viewModel::onExperienceChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Experience in Farming") },
                        trailingIcon = { Text("Years", style = MaterialTheme.typography.bodySmall) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = formTextFieldColors(),
                        isError = viewModel.experienceError != null,
                        supportingText = { viewModel.experienceError?.let { Text(it) } }
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "Go to your farm, turn on GPS, and tap the button to record the precise location.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            isFetchingLocation = true
                            permissionLauncher.launch(arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            ))
                        },
                        enabled = !isFetchingLocation,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SecondaryGreen)
                    ) {
                        // --- UPDATE: Show indicator or text based on state ---
                        if (isFetchingLocation) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = PrimaryGreen,
                                strokeWidth = 3.dp
                            )
                        } else {
                            Icon(Icons.Default.GpsFixed, contentDescription = "Get Location")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Fetch Farm's GPS Location",
                                color = PrimaryGreen
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = viewModel.latitude,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Latitude") },
                            modifier = Modifier.weight(1f),
                            colors = formTextFieldColors()
                        )
                        OutlinedTextField(
                            value = viewModel.longitude,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Longitude") },
                            modifier = Modifier.weight(1f),
                            colors = formTextFieldColors()
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = viewModel.timezone,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Timezone") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = formTextFieldColors()
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    SectionTitle("Cultivated Crops")

                    viewModel.cultivatedCrops.forEachIndexed { index, cropState ->
                        CropInputCard(
                            index = index,
                            cropState = cropState,
                            onCropNameChange = { newName -> viewModel.onCropNameChange(index, newName) },
                            onCropAreaChange = { newArea -> viewModel.onCropAreaChange(index, newArea) },
                            onCropMonthsChange = { newMonths -> viewModel.onCropMonthsChange(index, newMonths) },
                            onRemove = { viewModel.removeCrop(index) },
                            canBeRemoved = viewModel.cultivatedCrops.size > 1
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    TextButton(
                        onClick = viewModel::addCrop,
                        modifier = Modifier.align(Alignment.End),
                        colors = ButtonDefaults.textButtonColors(contentColor = PrimaryGreen)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Crop")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add another crop")
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    viewModel.areaSumError?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                if (viewModel.saveFarmDetails()) {
                                    navigateToHome()
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Failed to save details. Please check your connection and try again.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    navigateToHome()
                                }
                            }
                        },
                        enabled = !viewModel.isLoading,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp).height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, contentColor = white)
                    ) {
                        if (viewModel.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = white)
                        } else {
                            Text("Save Details", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 12.dp),
        color = PrimaryGreen
    )
}

@Composable
private fun CropInputCard(
    index: Int,
    cropState: CropUiState,
    onCropNameChange: (String) -> Unit,
    onCropAreaChange: (String) -> Unit,
    onCropMonthsChange: (String) -> Unit,
    onRemove: () -> Unit,
    canBeRemoved: Boolean
) {
    val cardBackgroundColor = Color(0xFFE8F5E9)
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = cardBackgroundColor
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Crop #${index + 1}",
                    style = MaterialTheme.typography.titleMedium,
                    color = PrimaryGreen
                )
                if (canBeRemoved) {
                    IconButton(onClick = onRemove, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Delete, "Remove Crop", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = cropState.name,
                onValueChange = onCropNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Crop Name (e.g., Rice, Wheat)") },
                singleLine = true,
                colors = formTextFieldColors(),
                isError = cropState.nameError != null,
                supportingText = { cropState.nameError?.let { Text(it) } }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = cropState.area,
                    onValueChange = onCropAreaChange,
                    modifier = Modifier.weight(1f),
                    label = { Text("Area") },
                    trailingIcon = { Text("Acres", style = MaterialTheme.typography.bodySmall) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    colors = formTextFieldColors(),
                    isError = cropState.areaError != null,
                    supportingText = { cropState.areaError?.let { Text(it) } }
                )
                OutlinedTextField(
                    value = cropState.months,
                    onValueChange = onCropMonthsChange,
                    modifier = Modifier.weight(1f),
                    label = { Text("Months") },
                    trailingIcon = { Text("Mos.", style = MaterialTheme.typography.bodySmall) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    colors = formTextFieldColors(),
                    isError = cropState.monthsError != null,
                    supportingText = { cropState.monthsError?.let { Text(it) } }
                )
            }
        }
    }
}

@Composable
private fun formTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = PrimaryGreen,
    unfocusedBorderColor = Color.LightGray,
    focusedLabelColor = PrimaryGreen,
    unfocusedLabelColor = Color.Gray,
    cursorColor = PrimaryGreen,
    focusedTextColor = Color.Black,
    unfocusedTextColor = Color.DarkGray,
    errorBorderColor = MaterialTheme.colorScheme.error,
    errorLabelColor = MaterialTheme.colorScheme.error,
    errorSupportingTextColor = MaterialTheme.colorScheme.error
)