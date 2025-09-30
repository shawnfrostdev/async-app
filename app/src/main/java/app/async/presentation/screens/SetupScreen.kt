package app.async.presentation.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import app.async.R
import app.async.data.model.DirectoryItem
import app.async.presentation.viewmodel.SetupUiState
import app.async.presentation.viewmodel.SetupViewModel
import app.async.ui.theme.InterFamily
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun SetupScreen(
    setupViewModel: SetupViewModel = hiltViewModel(),
    onSetupComplete: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by setupViewModel.uiState.collectAsState()

    // Re-check permissions when the screen is resumed
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                setupViewModel.checkPermissions(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        setupViewModel.loadMusicDirectories()
    }

    // Build permission items list
    val permissionItems = buildList {
        // Media Permission
        add(
            PermissionItem(
                id = "media",
                title = "Media Access",
                description = "Access to your audio files and music library",
                icon = R.drawable.rounded_library_music_24,
                isGranted = uiState.mediaPermissionGranted,
                isRequired = true
            )
        )

        // Notifications (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(
                PermissionItem(
                    id = "notifications",
                    title = "Notifications",
                    description = "Control music from lock screen and notification shade",
                    icon = R.drawable.rounded_circle_notifications_24,
                    isGranted = uiState.notificationsPermissionGranted,
                    isRequired = true
                )
            )
        }

        // All Files Access (Android 11+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            add(
                PermissionItem(
                    id = "all_files",
                    title = "All Files Access",
                    description = "Broader file access to find all your music",
                    icon = R.drawable.rounded_audio_file_24,
                    isGranted = uiState.allFilesAccessGranted,
                    isRequired = true
                )
            )
        }
        
        // Directory Selection (Optional - Always last)
        add(
            PermissionItem(
                id = "directories",
                title = "Music Folders",
                description = "Select folders where your music is stored (Optional)",
                icon = R.drawable.rounded_folder_24,
                isGranted = uiState.directoryItems.any { it.isAllowed },
                isRequired = false
            )
        )
    }

    // Find first permission that needs granting
    val nextPermissionToGrant = permissionItems.firstOrNull { !it.isGranted }
    
    // Check if all REQUIRED permissions are granted
    val requiredPermissionsGranted = permissionItems.filter { it.isRequired }.all { it.isGranted }
    
    var showDirectoryPicker by remember { mutableStateOf(false) }

    Scaffold(
        bottomBar = {
            SetupBottomBar(
                allPermissionsGranted = uiState.allPermissionsGranted,
                requiredPermissionsGranted = requiredPermissionsGranted,
                nextPermission = nextPermissionToGrant,
                onActionClick = { permission ->
                    when (permission.id) {
                        "media" -> {
                            // Will be handled by permission state in the composable
                        }
                        "directories" -> {
                            showDirectoryPicker = true
                        }
                        "notifications" -> {
                            // Will be handled by permission state in the composable
                        }
                        "all_files" -> {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                                intent.data = "package:${context.packageName}".toUri()
                                context.startActivity(intent)
                            }
                        }
                    }
                },
                onFinishClick = {
                    setupViewModel.checkPermissions(context)
                    if (requiredPermissionsGranted) {
                        setupViewModel.setSetupComplete()
                        onSetupComplete()
                    } else {
                        Toast.makeText(
                            context,
                            "Please grant required permissions to continue",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 32.dp)
        ) {
            // Header
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = "Welcome to Async",
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = InterFamily
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "To get started, we need a few permissions to access your music library and provide the best experience.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 24.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // Permission Cards grouped together
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(color = Color.Transparent, shape = RoundedCornerShape(24.dp))
                        .clip(shape = RoundedCornerShape(24.dp))
                ) {
                    permissionItems.forEachIndexed { index, item ->
                        PermissionItemCard(item = item)
                        if (index < permissionItems.size - 1) {
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
            }
        }

        // Directory Picker Bottom Sheet
    if (showDirectoryPicker) {
        DirectoryPickerBottomSheet(
            onDismiss = { showDirectoryPicker = false }
            )
        }
    }
}

data class PermissionItem(
    val id: String,
    val title: String,
    val description: String,
    val icon: Int,
    val isGranted: Boolean,
    val isRequired: Boolean
)

@Composable
private fun PermissionItemCard(
    item: PermissionItem
) {
    val checkmarkColor by animateColorAsState(
        targetValue = if (item.isGranted) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        animationSpec = tween(300),
        label = "checkmark_color"
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(item.icon),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Content
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Checkmark
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(checkmarkColor),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = item.isGranted,
                    enter = fadeIn(spring(stiffness = Spring.StiffnessHigh)) + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = "Granted",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun SetupBottomBar(
    allPermissionsGranted: Boolean,
    requiredPermissionsGranted: Boolean,
    nextPermission: PermissionItem?,
    onActionClick: (PermissionItem) -> Unit,
    onFinishClick: () -> Unit
) {
    val context = LocalContext.current
    
    // Permission states
    val mediaPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        listOf(Manifest.permission.READ_MEDIA_AUDIO)
    } else {
        listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    val mediaPermissionState = rememberMultiplePermissionsState(permissions = mediaPermissions)
    val notificationPermissionState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberMultiplePermissionsState(permissions = listOf(Manifest.permission.POST_NOTIFICATIONS))
    } else null

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // If all required permissions granted, show Get Started
            if (requiredPermissionsGranted) {
                // Show optional permission buttons with skip if there are any not granted
                if (nextPermission != null && !nextPermission.isRequired) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Main action button (larger)
                        FilledTonalButton(
                            onClick = {
                                when (nextPermission.id) {
                                    "directories" -> onActionClick(nextPermission)
                                    "notifications" -> notificationPermissionState?.launchMultiplePermissionRequest()
                                    "all_files" -> onActionClick(nextPermission)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(vertical = 16.dp)
                        ) {
                            Icon(
                                painter = painterResource(nextPermission.icon),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = when (nextPermission.id) {
                                    "directories" -> "Select Folders"
                                    "notifications" -> "Enable"
                                    else -> "Grant"
                                },
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        
                        // Skip button (smaller)
                        TextButton(
                            onClick = onFinishClick,
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp)
                        ) {
                            Text(
                                text = "Skip",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                } else {
                    // No optional permissions left, show Get Started
                    Button(
                        onClick = onFinishClick,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(vertical = 16.dp)
                    ) {
                        Text(
                            text = "Get Started",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            } else if (nextPermission != null) {
                // Show required permission button
                when (nextPermission.id) {
                    "media" -> {
                        FilledTonalButton(
                            onClick = { mediaPermissionState.launchMultiplePermissionRequest() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(vertical = 16.dp)
                        ) {
                            Icon(
                                painter = painterResource(nextPermission.icon),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Grant ${nextPermission.title}",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                    "directories" -> {
                        FilledTonalButton(
                            onClick = { onActionClick(nextPermission) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(vertical = 16.dp)
                        ) {
                            Icon(
                                painter = painterResource(nextPermission.icon),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Select ${nextPermission.title}",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                    "notifications" -> {
                        FilledTonalButton(
                            onClick = { notificationPermissionState?.launchMultiplePermissionRequest() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(vertical = 16.dp)
                        ) {
                            Icon(
                                painter = painterResource(nextPermission.icon),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Enable ${nextPermission.title}",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                    "all_files" -> {
                        FilledTonalButton(
                            onClick = { onActionClick(nextPermission) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(vertical = 16.dp)
                        ) {
                            Icon(
                                painter = painterResource(nextPermission.icon),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Grant ${nextPermission.title}",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DirectoryPickerBottomSheet(
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    var selectedFolders by remember { mutableStateOf<List<Uri>>(emptyList()) }
    
    // Folder picker launcher
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            // Take persistent permission
            context.contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            selectedFolders = selectedFolders + it
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        sheetState = sheetState,
        modifier = Modifier.fillMaxHeight(),
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Header
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 24.dp, bottom = 16.dp, top = 10.dp)
                ) {
                    Text(
                        text = "Music Folders",
                        fontFamily = InterFamily,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Browse and select folders where you want to store your music. The app will only scan these folders.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Content
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (selectedFolders.isEmpty()) {
                        // Empty state
    Column(
        modifier = Modifier
            .fillMaxSize()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No folders selected",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Tap the button below to browse your device and select music folders",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        // List of selected folders
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 160.dp)
                        ) {
                            items(selectedFolders.size) { index ->
                                val folder = selectedFolders[index]
                                SelectedFolderCard(
                                    folderUri = folder,
                                    onRemove = {
                                        selectedFolders = selectedFolders.filterIndexed { i, _ -> i != index }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Bottom buttons
    Column(
        modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(start = 20.dp, end = 20.dp, bottom = 26.dp, top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Add Folder button
        Button(
                    onClick = { folderPickerLauncher.launch(null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Browse Folders",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                
                // Done/Skip button
                FilledTonalButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    Text(
                        text = if (selectedFolders.isNotEmpty()) "Done" else "Skip",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectedFolderCard(
    folderUri: Uri,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                        Text(
                        text = folderUri.lastPathSegment?.split(":")?.lastOrNull() ?: "Unknown folder",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                        Text(
                        text = folderUri.toString(),
                        style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Remove folder",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}