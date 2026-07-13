package com.example.ui

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.bluetooth.ConnectionState
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

// Elegant Apple-inspired Silicon/Sleek Theme
val SlateBg = Color(0xFF0F1014)        // Rich Dark Space Gray-Black
val CardDark = Color(0xFF1C1C1E)       // iOS Dark Mode Secondary System Background
val CyanNeon = Color(0xFF0A84FF)       // Apple Blue (SF Blue)
val CyanGlow = Color(0x1F0A84FF)       // Muted SF Blue glow
val AmberNeon = Color(0xFFFFD60A)      // Apple Yellow/Gold (SF Yellow)
val RedNeon = Color(0xFFFF453A)        // Apple Red (SF Red)
val GreenNeon = Color(0xFF30D158)      // Apple Green (SF Green)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: OmniBotViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val connectionState by viewModel.connectionState.collectAsState()
    val terminalLines by viewModel.terminalLines.collectAsState()
    val speedLevel by viewModel.speedLevel.collectAsState()
    val activeCommand by viewModel.activeCommand.collectAsState()
    val hapticEnabled by viewModel.hapticEnabled.collectAsState()
    val connectedDevice by viewModel.connectedDevice.collectAsState()
    val launcherRunning by viewModel.launcherRunning.collectAsState()
    val launcherSpeed by viewModel.launcherSpeed.collectAsState()
    val targetAngle by viewModel.targetAngle.collectAsState()
    val angleHomed by viewModel.angleHomed.collectAsState()
    val limitSwitchActive by viewModel.limitSwitchActive.collectAsState()
    val currentAngle by viewModel.currentAngle.collectAsState()

    var showInfoDialog by remember { mutableStateOf(false) }
    var selectedDeviceForConnection by remember { mutableStateOf<BluetoothDevice?>(null) }
    var isDeviceMenuExpanded by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) }

    // Dynamic Bluetooth Permissions list based on SDK
    val requiredPermissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(android.Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            listOf(
                android.Manifest.permission.BLUETOOTH,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
    }

    var permissionsGranted by remember {
        mutableStateOf(
            requiredPermissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionsGranted = results.values.all { it }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "OmniBot Control",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                        Text(
                            text = "ESP32 Holonomic X-Drive",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color.Gray
                            )
                        )
                    }
                },
                actions = {
                    BluetoothStatusBadge(connectionState = connectionState, deviceName = connectedDevice?.name)
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = { viewModel.hapticEnabled.value = !hapticEnabled },
                        modifier = Modifier.testTag("toggle_haptic_button")
                    ) {
                        Icon(
                            imageVector = if (hapticEnabled) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Toggle Haptics",
                            tint = if (hapticEnabled) RedNeon else Color.Gray
                        )
                    }
                    IconButton(
                        onClick = { showInfoDialog = true },
                        modifier = Modifier.testTag("info_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "About commands",
                            tint = CyanNeon
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SlateBg,
                    titleContentColor = Color.White
                )
            )
        },
        bottomBar = {
            if (permissionsGranted) {
                NavigationBar(
                    containerColor = CardDark,
                    tonalElevation = 8.dp,
                    windowInsets = WindowInsets.navigationBars
                ) {
                    val haptic = LocalHapticFeedback.current
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = {
                            if (hapticEnabled) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            selectedTab = 0
                        },
                        icon = { Icon(Icons.Default.Build, contentDescription = "Flight Deck") },
                        label = { Text("Flight Deck") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = SlateBg,
                            selectedTextColor = AmberNeon,
                            indicatorColor = AmberNeon,
                            unselectedIconColor = Color.Gray,
                            unselectedTextColor = Color.Gray
                        )
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = {
                            if (hapticEnabled) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            selectedTab = 1
                        },
                        icon = { Icon(Icons.Default.List, contentDescription = "Serial Monitor") },
                        label = { Text("Serial Monitor") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = SlateBg,
                            selectedTextColor = AmberNeon,
                            indicatorColor = AmberNeon,
                            unselectedIconColor = Color.Gray,
                            unselectedTextColor = Color.Gray
                        )
                    )
                }
            }
        },
        containerColor = SlateBg
    ) { innerPadding ->
        if (!permissionsGranted) {
            // Permission Request Card
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CardDark),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Permission Warning",
                            tint = AmberNeon,
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            text = "Bluetooth Permission Required",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "This app requires Bluetooth permissions to scan, pair, and send control signals to the ESP32 OmniBot.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.LightGray,
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = {
                                permissionLauncher.launch(requiredPermissions.toTypedArray())
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CyanNeon),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("grant_permissions_button")
                        ) {
                            Text("Grant Permissions", color = SlateBg, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            // Main Control Dashboard
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Real-time status banner for quick feedback
                AnimatedVisibility(visible = connectionState != ConnectionState.CONNECTED) {
                    val bannerBg = CardDark
                    val bannerText = when (connectionState) {
                        ConnectionState.CONNECTING -> "Menyambungkan ke perangkat ESP32... Mohon tunggu."
                        ConnectionState.ERROR -> "Gagal menyambung. Pastikan ESP32 menyala & terpasang Bluetooth."
                        else -> "Status: Terputus. Pilih perangkat ESP32 di bawah untuk mulai mengendalikan."
                    }
                    val bannerIcon = when (connectionState) {
                        ConnectionState.CONNECTING -> Icons.Default.Refresh
                        ConnectionState.ERROR -> Icons.Default.Warning
                        else -> Icons.Default.Info
                    }
                    val bannerTextColor = when (connectionState) {
                        ConnectionState.CONNECTING -> AmberNeon
                        ConnectionState.ERROR -> RedNeon
                        else -> Color.Gray
                    }
                    val bannerBorder = when (connectionState) {
                        ConnectionState.CONNECTING -> AmberNeon.copy(alpha = 0.5f)
                        ConnectionState.ERROR -> RedNeon.copy(alpha = 0.5f)
                        else -> Color.Gray.copy(alpha = 0.3f)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(bannerBg)
                            .border(1.dp, bannerBorder, RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = bannerIcon,
                            contentDescription = "Connection Status",
                            tint = bannerTextColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = bannerText,
                            color = Color.LightGray,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Connection Status Card
                ConnectionCard(
                    connectionState = connectionState,
                    pairedDevices = viewModel.bluetoothController.getPairedDevices(),
                    onConnect = { viewModel.connectDevice(it) },
                    onDisconnect = { viewModel.disconnectDevice() },
                    errorMessage = viewModel.errorMessage.collectAsState().value,
                    isMenuExpanded = isDeviceMenuExpanded,
                    onMenuToggle = { isDeviceMenuExpanded = it }
                )

                // Navigation is now handled by the bottom navigation bar

                if (selectedTab == 0) {
                    // FLIGHT DECK (Scrollable Control center)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(androidx.compose.foundation.rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Tactical Telemetry Robot 3D Visualizer
                        TacticalRobot3DVisualizer(
                            activeCommand = activeCommand,
                            speedLevel = speedLevel
                        )

                        // D-Pad and Control Panel
                        ControlPanel(
                            activeCommand = activeCommand,
                            hapticEnabled = hapticEnabled,
                            onPress = { viewModel.handleMovementPress(it) },
                            onRelease = { viewModel.handleMovementRelease() }
                        )

                        // Speed Adjuster Slider Card
                        SpeedCard(
                            speedLevel = speedLevel,
                            hapticEnabled = hapticEnabled,
                            onSpeedChange = { viewModel.setSpeed(it) },
                            onIncrement = { viewModel.adjustSpeed(true) },
                            onDecrement = { viewModel.adjustSpeed(false) }
                        )

                        // Launcher Control Card (Pelontar)
                        LauncherCard(
                            launcherRunning = launcherRunning,
                            launcherSpeed = launcherSpeed,
                            hapticEnabled = hapticEnabled,
                            onSpeedChange = { viewModel.setLauncherSpeed(it) },
                            onIncrement = { viewModel.adjustLauncherSpeed(true) },
                            onDecrement = { viewModel.adjustLauncherSpeed(false) }
                        )

                        // Angle Control Card (Pengatur Sudut)
                        AngleControlCard(
                            targetAngle = targetAngle,
                            currentAngle = currentAngle,
                            angleHomed = angleHomed,
                            limitSwitchActive = limitSwitchActive,
                            hapticEnabled = hapticEnabled,
                            onAngleChange = { viewModel.setAngle(it) },
                            onJogIncrement = { viewModel.jogAngle(true) },
                            onJogDecrement = { viewModel.jogAngle(false) },
                            onHoming = { viewModel.startHoming() },
                            onStopMotion = { viewModel.stopAngleMotion() },
                            onCheckStatus = { viewModel.requestAngleStatus() }
                        )

                        // Emergency Stop Button
                        EmergencyStopButton(
                            onClick = { viewModel.executeEmergencyStop() }
                        )
                    }
                } else {
                    // SERIAL MONITOR (Full screen height monitor)
                    TerminalCard(
                        terminalLines = terminalLines,
                        hapticEnabled = hapticEnabled,
                        onClearLogs = { viewModel.clearTerminal() },
                        onSendRaw = { viewModel.sendCommand(it) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    // Info/Help Dialog
    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = { Text("Command Protocol Info") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Movement:", fontWeight = FontWeight.Bold)
                    Text("• F: Forward | B: Backward\n• L: Left Slide | R: Right Slide\n• G/I/H/J: Diagonals\n• Q: CCW Rotate | E: CW Rotate\n• S: Hard Stop (sent when released)")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Speed Controls:", fontWeight = FontWeight.Bold)
                    Text("• 0 - 9: Absolute Speed Sets\n• + / -: Speed Increment / Decrement")
                }
            },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) {
                    Text("Understood", color = CyanNeon)
                }
            }
        )
    }
}

@SuppressLint("MissingPermission")
@Composable
fun ConnectionCard(
    connectionState: ConnectionState,
    pairedDevices: List<BluetoothDevice>,
    onConnect: (BluetoothDevice) -> Unit,
    onDisconnect: () -> Unit,
    errorMessage: String?,
    isMenuExpanded: Boolean,
    onMenuToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val statusColor by animateColorAsState(
        targetValue = when (connectionState) {
            ConnectionState.CONNECTED -> GreenNeon
            ConnectionState.CONNECTING -> AmberNeon
            ConnectionState.ERROR -> RedNeon
            ConnectionState.DISCONNECTED -> Color.Gray
        },
        label = "status_color"
    )

    val statusText = when (connectionState) {
        ConnectionState.CONNECTED -> "Connected"
        ConnectionState.CONNECTING -> "Connecting..."
        ConnectionState.ERROR -> "Error"
        ConnectionState.DISCONNECTED -> "Disconnected"
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Glowing indicator dot
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(statusColor, CircleShape)
                            .border(2.dp, statusColor.copy(alpha = 0.3f), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Bluetooth: $statusText",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                if (connectionState == ConnectionState.CONNECTED) {
                    Button(
                        onClick = onDisconnect,
                        colors = ButtonDefaults.buttonColors(containerColor = RedNeon),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("disconnect_button")
                    ) {
                        Text("Disconnect", color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            if (connectionState != ConnectionState.CONNECTED) {
                // Device selection
                Box(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { onMenuToggle(true) },
                        colors = ButtonDefaults.buttonColors(containerColor = CyanNeon),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("select_device_button")
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Devices", tint = Color.White)
                            Text("Select Paired Device", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }

                    DropdownMenu(
                        expanded = isMenuExpanded,
                        onDismissRequest = { onMenuToggle(false) },
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(CardDark)
                            .border(0.5.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                    ) {
                        if (pairedDevices.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("No paired devices found", color = Color.Gray) },
                                onClick = { onMenuToggle(false) }
                            )
                        } else {
                            pairedDevices.forEach { device ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(
                                                text = device.name ?: "Unknown Device",
                                                color = Color.White,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = device.address,
                                                color = Color.Gray,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    },
                                    onClick = {
                                        onConnect(device)
                                        onMenuToggle(false)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            errorMessage?.let {
                Text(
                    text = "Error: $it",
                    color = RedNeon,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
fun ControlPanel(
    activeCommand: Char,
    hapticEnabled: Boolean,
    onPress: (Char) -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(if (activeCommand != 'S') GreenNeon else RedNeon, CircleShape)
                    )
                    Text(
                        text = "FLIGHT DECK SYSTEM",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF8E8E93),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                }
                Text(
                    text = "X-DRIVE ENGINE",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF8E8E93),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.06f), thickness = 0.5.dp)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // LEFT ROTATION WING
                RotationButton(
                    icon = Icons.Default.Refresh,
                    label = "Rotate L",
                    command = 'Q',
                    activeCommand = activeCommand,
                    hapticEnabled = hapticEnabled,
                    onPress = { onPress('Q') },
                    onRelease = onRelease,
                    modifier = Modifier.testTag("btn_rotate_ccw")
                )

                // CENTER 8-WAY DIRECTIONAL GRID (3x3)
                Box(
                    modifier = Modifier
                        .size(244.dp)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(Color(0xFF2C2C2E), Color(0xFF121214)),
                                radius = 350f
                            )
                        )
                        .border(0.5.dp, Color.White.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Row 1 (G, F, I)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            DirectionalButton(
                                icon = Icons.Default.KeyboardArrowUp,
                                angle = -45f,
                                label = "F-Left",
                                command = 'G',
                                activeCommand = activeCommand,
                                hapticEnabled = hapticEnabled,
                                isDiagonal = true,
                                onPress = { onPress('G') },
                                onRelease = onRelease,
                                modifier = Modifier.testTag("btn_g")
                            )
                            DirectionalButton(
                                icon = Icons.Default.KeyboardArrowUp,
                                angle = 0f,
                                label = "Forward",
                                command = 'F',
                                activeCommand = activeCommand,
                                hapticEnabled = hapticEnabled,
                                isDiagonal = false,
                                onPress = { onPress('F') },
                                onRelease = onRelease,
                                modifier = Modifier.testTag("btn_f")
                            )
                            DirectionalButton(
                                icon = Icons.Default.KeyboardArrowUp,
                                angle = 45f,
                                label = "F-Right",
                                command = 'I',
                                activeCommand = activeCommand,
                                hapticEnabled = hapticEnabled,
                                isDiagonal = true,
                                onPress = { onPress('I') },
                                onRelease = onRelease,
                                modifier = Modifier.testTag("btn_i")
                            )
                        }

                        // Row 2 (L, STOP_STATUS, R)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            DirectionalButton(
                                icon = Icons.Default.KeyboardArrowLeft,
                                angle = 0f,
                                label = "Left",
                                command = 'L',
                                activeCommand = activeCommand,
                                hapticEnabled = hapticEnabled,
                                isDiagonal = false,
                                onPress = { onPress('L') },
                                onRelease = onRelease,
                                modifier = Modifier.testTag("btn_l")
                            )
                            
                            // Stop Status Visual Indicator in the Center
                            StopIndicator(
                                activeCommand = activeCommand,
                                hapticEnabled = hapticEnabled,
                                onStop = onRelease
                            )

                            DirectionalButton(
                                icon = Icons.Default.KeyboardArrowRight,
                                angle = 0f,
                                label = "Right",
                                command = 'R',
                                activeCommand = activeCommand,
                                hapticEnabled = hapticEnabled,
                                isDiagonal = false,
                                onPress = { onPress('R') },
                                onRelease = onRelease,
                                modifier = Modifier.testTag("btn_r")
                            )
                        }

                        // Row 3 (H, B, J)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            DirectionalButton(
                                icon = Icons.Default.KeyboardArrowDown,
                                angle = 45f,
                                label = "B-Left",
                                command = 'H',
                                activeCommand = activeCommand,
                                hapticEnabled = hapticEnabled,
                                isDiagonal = true,
                                onPress = { onPress('H') },
                                onRelease = onRelease,
                                modifier = Modifier.testTag("btn_h")
                            )
                            DirectionalButton(
                                icon = Icons.Default.KeyboardArrowDown,
                                angle = 0f,
                                label = "Backward",
                                command = 'B',
                                activeCommand = activeCommand,
                                hapticEnabled = hapticEnabled,
                                isDiagonal = false,
                                onPress = { onPress('B') },
                                onRelease = onRelease,
                                modifier = Modifier.testTag("btn_b")
                            )
                            DirectionalButton(
                                icon = Icons.Default.KeyboardArrowDown,
                                angle = -45f,
                                label = "B-Right",
                                command = 'J',
                                activeCommand = activeCommand,
                                hapticEnabled = hapticEnabled,
                                isDiagonal = true,
                                onPress = { onPress('J') },
                                onRelease = onRelease,
                                modifier = Modifier.testTag("btn_j")
                            )
                        }
                    }
                }

                // RIGHT ROTATION WING
                RotationButton(
                    icon = Icons.Default.Refresh,
                    label = "Rotate R",
                    command = 'E',
                    activeCommand = activeCommand,
                    hapticEnabled = hapticEnabled,
                    onPress = { onPress('E') },
                    onRelease = onRelease,
                    isClockwise = true,
                    modifier = Modifier.testTag("btn_rotate_cw")
                )
            }
        }
    }
}

@Composable
fun DirectionalButton(
    icon: ImageVector,
    angle: Float,
    label: String,
    command: Char,
    activeCommand: Char,
    hapticEnabled: Boolean,
    isDiagonal: Boolean = false,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val isActive = activeCommand == command
    val scale by animateFloatAsState(if (isActive) 0.88f else 1.0f, label = "button_scale")
    
    // Diagonal buttons are slightly smaller and styled with Gold/Amber to create a beautiful layout
    val buttonSize = if (isDiagonal) 50.dp else 62.dp
    val buttonShape = if (isDiagonal) CircleShape else RoundedCornerShape(14.dp)
    
    val baseColor = if (isDiagonal) AmberNeon else CyanNeon
    val buttonColor by animateColorAsState(
        if (isActive) baseColor else CardDark.copy(alpha = 0.6f),
        label = "btn_color"
    )
    val contentColor by animateColorAsState(
        if (isActive) SlateBg else baseColor,
        label = "content_color"
    )
    val borderStrokeColor by animateColorAsState(
        if (isActive) baseColor else Color.White.copy(alpha = 0.08f),
        label = "btn_border_color"
    )

    Box(
        modifier = modifier
            .size(62.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(buttonSize)
                .scale(scale)
                .clip(buttonShape)
                .background(buttonColor)
                .border(1.dp, borderStrokeColor, buttonShape)
                .pointerInput(onPress, onRelease, hapticEnabled) {
                    detectTapGestures(
                        onPress = {
                            if (hapticEnabled) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            onPress()
                            try {
                                tryAwaitRelease()
                            } catch (_: Exception) {}
                            if (hapticEnabled) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            onRelease()
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier
                    .size(if (isDiagonal) 22.dp else 28.dp)
                    .rotate(angle)
            )
        }
    }
}

@Composable
fun RotationButton(
    icon: ImageVector,
    label: String,
    command: Char,
    activeCommand: Char,
    hapticEnabled: Boolean,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    isClockwise: Boolean = false,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val isActive = activeCommand == command
    val scale by animateFloatAsState(if (isActive) 0.88f else 1.0f, label = "rotation_scale")
    val buttonColor by animateColorAsState(
        if (isActive) AmberNeon else CardDark.copy(alpha = 0.6f),
        label = "rot_color"
    )
    val contentColor by animateColorAsState(
        if (isActive) SlateBg else AmberNeon,
        label = "rot_content_color"
    )
    val borderStrokeColor by animateColorAsState(
        if (isActive) AmberNeon else Color.White.copy(alpha = 0.08f),
        label = "rot_border_color"
    )

    Box(
        modifier = modifier
            .size(64.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(buttonColor)
            .border(1.dp, borderStrokeColor, CircleShape)
            .pointerInput(onPress, onRelease, hapticEnabled) {
                detectTapGestures(
                    onPress = {
                        if (hapticEnabled) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        onPress()
                        try {
                            tryAwaitRelease()
                        } catch (_: Exception) {}
                        if (hapticEnabled) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        onRelease()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier
                .size(30.dp)
                .scale(scaleX = if (isClockwise) -1f else 1f, scaleY = 1f)
        )
    }
}

@Composable
fun StopIndicator(
    activeCommand: Char,
    hapticEnabled: Boolean,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val isStopped = activeCommand == 'S'
    val indicatorColor by animateColorAsState(
        if (isStopped) RedNeon else CyanNeon,
        label = "stop_indicator_color"
    )

    Box(
        modifier = modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(CardDark)
            .border(2.dp, indicatorColor, CircleShape)
            .clickable {
                if (hapticEnabled) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                onStop()
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = activeCommand.toString(),
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = indicatorColor,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = if (isStopped) "STOP" else "GO",
                fontSize = 8.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.Gray,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun SpeedCard(
    speedLevel: Int,
    hapticEnabled: Boolean,
    onSpeedChange: (Int) -> Unit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(CyanNeon, CircleShape)
                    )
                    Text(
                        text = "PROPULSION SPEED DRIVE",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF8E8E93),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                }
                Text(
                    text = "DUTY CYCLE: ${speedLevel * 11}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = AmberNeon,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.06f), thickness = 0.5.dp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Speed Ratio: $speedLevel / 9",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(
                        onClick = {
                            if (hapticEnabled) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            onDecrement()
                        },
                        modifier = Modifier
                            .testTag("btn_speed_down")
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.05f))
                            .border(0.5.dp, Color.White.copy(alpha = 0.08f), CircleShape)
                    ) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Decrease", tint = CyanNeon)
                    }
                    IconButton(
                        onClick = {
                            if (hapticEnabled) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            onIncrement()
                        },
                        modifier = Modifier
                            .testTag("btn_speed_up")
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.05f))
                            .border(0.5.dp, Color.White.copy(alpha = 0.08f), CircleShape)
                    ) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Increase", tint = CyanNeon)
                    }
                }
            }

            // Beautiful speed grid selectors (1 to 9)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for (i in 1..9) {
                    val isSelected = speedLevel == i
                    val speedColor by animateColorAsState(
                        if (isSelected) CyanNeon else Color.White.copy(alpha = 0.05f),
                        label = "speed_color_grid"
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(speedColor)
                            .border(
                                width = if (isSelected) 0.dp else 0.5.dp,
                                color = if (isSelected) Color.Transparent else Color.White.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable {
                                if (hapticEnabled) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                                onSpeedChange(i)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = i.toString(),
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else Color(0xFF8E8E93),
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // Presets
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SpeedPresetButton("Slow (3)", 3, speedLevel, hapticEnabled, onSpeedChange, Modifier.weight(1f))
                SpeedPresetButton("Medium (6)", 6, speedLevel, hapticEnabled, onSpeedChange, Modifier.weight(1f))
                SpeedPresetButton("Max Turbo (9)", 9, speedLevel, hapticEnabled, onSpeedChange, Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun SpeedPresetButton(
    text: String,
    level: Int,
    currentLevel: Int,
    hapticEnabled: Boolean,
    onClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val isSelected = currentLevel == level
    OutlinedButton(
        onClick = {
            if (hapticEnabled) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            onClick(level)
        },
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (isSelected) CyanNeon.copy(alpha = 0.15f) else Color.Transparent,
            contentColor = if (isSelected) CyanNeon else Color.Gray
        ),
        border = BorderStroke(
            1.dp,
            if (isSelected) CyanNeon else Color.Gray.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier.height(38.dp),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun LauncherCard(
    launcherRunning: Boolean,
    launcherSpeed: Int,
    hapticEnabled: Boolean,
    onSpeedChange: (Int) -> Unit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(if (launcherRunning) AmberNeon else Color.Gray, CircleShape)
                    )
                    Text(
                        text = "LAUNCHER MOTOR PELONTAR",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF8E8E93),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                }
                Text(
                    text = if (launcherRunning) "ACTIVE (${launcherSpeed * 11}%)" else "STANDBY",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (launcherRunning) AmberNeon else Color.Gray,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.06f), thickness = 0.5.dp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Launcher Speed: $launcherSpeed / 9",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (launcherRunning) "Commands: K (Start), M (Stop)" else "Tap grid to start launcher",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = {
                            if (hapticEnabled) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            onDecrement()
                        },
                        modifier = Modifier
                            .testTag("btn_launcher_down")
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.05f))
                            .border(0.5.dp, Color.White.copy(alpha = 0.08f), CircleShape)
                    ) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Decrease Launcher", tint = AmberNeon)
                    }
                    IconButton(
                        onClick = {
                            if (hapticEnabled) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            onIncrement()
                        },
                        modifier = Modifier
                            .testTag("btn_launcher_up")
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.05f))
                            .border(0.5.dp, Color.White.copy(alpha = 0.08f), CircleShape)
                    ) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Increase Launcher", tint = AmberNeon)
                    }
                }
            }

            // Beautiful speed grid selectors (0 to 9) - 0 is stop, 1-9 is speeds
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                for (i in 0..9) {
                    val isSelected = launcherSpeed == i
                    val speedColor by animateColorAsState(
                        if (isSelected) AmberNeon else Color.White.copy(alpha = 0.05f),
                        label = "launcher_speed_grid"
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(speedColor)
                            .border(
                                width = if (isSelected) 0.dp else 0.5.dp,
                                color = if (isSelected) Color.Transparent else Color.White.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable {
                                if (hapticEnabled) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                                onSpeedChange(i)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = i.toString(),
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.Black else Color(0xFF8E8E93),
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Presets
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LauncherPresetButton("STOP Launcher (0)", 0, launcherSpeed, hapticEnabled, onSpeedChange, Modifier.weight(1.2f))
                LauncherPresetButton("Launch Level 5", 5, launcherSpeed, hapticEnabled, onSpeedChange, Modifier.weight(1f))
                LauncherPresetButton("Max Launch (9)", 9, launcherSpeed, hapticEnabled, onSpeedChange, Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun LauncherPresetButton(
    text: String,
    level: Int,
    currentLevel: Int,
    hapticEnabled: Boolean,
    onClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val isSelected = currentLevel == level
    OutlinedButton(
        onClick = {
            if (hapticEnabled) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            onClick(level)
        },
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (isSelected) AmberNeon.copy(alpha = 0.15f) else Color.Transparent,
            contentColor = if (isSelected) AmberNeon else Color.Gray
        ),
        border = BorderStroke(
            1.dp,
            if (isSelected) AmberNeon else Color.Gray.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier.height(38.dp),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun AngleControlCard(
    targetAngle: Float,
    currentAngle: Float,
    angleHomed: Boolean,
    limitSwitchActive: Boolean,
    hapticEnabled: Boolean,
    onAngleChange: (Float) -> Unit,
    onJogIncrement: () -> Unit,
    onJogDecrement: () -> Unit,
    onHoming: () -> Unit,
    onStopMotion: () -> Unit,
    onCheckStatus: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(AmberNeon, CircleShape)
                    )
                    Text(
                        text = "PENGATUR SUDUT LAUNCHER (STEPPER)",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF8E8E93),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                }
                Text(
                    text = "LIVE: ${String.format(java.util.Locale.US, "%.1f", currentAngle)}°",
                    style = MaterialTheme.typography.labelMedium,
                    color = AmberNeon,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.06f), thickness = 0.5.dp)

            // Status bar for homed and limit switch status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Homed status chip
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (angleHomed) GreenNeon.copy(alpha = 0.12f) else RedNeon.copy(alpha = 0.12f))
                        .border(0.5.dp, if (angleHomed) GreenNeon.copy(alpha = 0.4f) else RedNeon.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(if (angleHomed) GreenNeon else RedNeon, CircleShape)
                        )
                        Text(
                            text = if (angleHomed) "HOMED: YES" else "NOT HOMED",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (angleHomed) GreenNeon else RedNeon
                        )
                    }
                }

                // Limit Switch status chip
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (limitSwitchActive) AmberNeon.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f))
                        .border(
                            0.5.dp,
                            if (limitSwitchActive) AmberNeon else Color.White.copy(alpha = 0.08f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(if (limitSwitchActive) AmberNeon else Color.Gray, CircleShape)
                        )
                        Text(
                            text = if (limitSwitchActive) "LIMIT: ACTIVE" else "LIMIT: INACTIVE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (limitSwitchActive) AmberNeon else Color.Gray
                        )
                    }
                }
            }

            // Value Display and Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Sudut Target: ${String.format(java.util.Locale.US, "%.1f", targetAngle)}°",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Command: A<nilai># (0.0° to 43.0°)",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Jog Down (-)
                    IconButton(
                        onClick = {
                            if (hapticEnabled) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            onJogDecrement()
                        },
                        modifier = Modifier
                            .testTag("btn_angle_jog_down")
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.05f))
                            .border(0.5.dp, Color.White.copy(alpha = 0.08f), CircleShape)
                    ) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Decrease Angle 1 Deg", tint = AmberNeon)
                    }

                    // Jog Up (+)
                    IconButton(
                        onClick = {
                            if (hapticEnabled) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            onJogIncrement()
                        },
                        modifier = Modifier
                            .testTag("btn_angle_jog_up")
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.05f))
                            .border(0.5.dp, Color.White.copy(alpha = 0.08f), CircleShape)
                    ) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Increase Angle 1 Deg", tint = AmberNeon)
                    }
                }
            }

            // Slider for smooth tuning
            Slider(
                value = targetAngle,
                onValueChange = {
                    val formatted = (Math.round(it * 10) / 10.0).toFloat()
                    onAngleChange(formatted)
                },
                valueRange = 0f..43f,
                colors = SliderDefaults.colors(
                    thumbColor = AmberNeon,
                    activeTrackColor = AmberNeon,
                    inactiveTrackColor = Color.White.copy(alpha = 0.08f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("slider_angle")
            )

            // Preset Quick Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val presets = listOf(0f, 15f, 30f, 43f)
                presets.forEach { preset ->
                    val isSelected = Math.abs(targetAngle - preset) < 0.5f
                    val label = if (preset == 0f) "0° (MIN)" else if (preset == 43f) "43° (MAX)" else "${preset.toInt()}°"
                    
                    OutlinedButton(
                        onClick = {
                            if (hapticEnabled) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            onAngleChange(preset)
                        },
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isSelected) AmberNeon.copy(alpha = 0.15f) else Color.Transparent,
                            contentColor = if (isSelected) AmberNeon else Color.Gray
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) AmberNeon else Color.Gray.copy(alpha = 0.4f)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            text = label,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // Command Button Bar (Homing Z, Stop Motion C, View Status V)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Button Homing (Z)
                Button(
                    onClick = {
                        if (hapticEnabled) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        onHoming()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GreenNeon.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, GreenNeon),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("btn_homing"),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Home, contentDescription = "Home", tint = GreenNeon, modifier = Modifier.size(16.dp))
                        Text("HOMING (Z)", color = GreenNeon, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Button Stop Motion (C)
                Button(
                    onClick = {
                        if (hapticEnabled) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        onStopMotion()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RedNeon.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, RedNeon),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("btn_stop_angle"),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Stop", tint = RedNeon, modifier = Modifier.size(16.dp))
                        Text("STOP (C)", color = RedNeon, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Button View Status (V)
                Button(
                    onClick = {
                        if (hapticEnabled) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        onCheckStatus()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanNeon.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, CyanNeon.copy(alpha = 0.8f)),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("btn_angle_status"),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Info, contentDescription = "Info", tint = CyanNeon, modifier = Modifier.size(16.dp))
                        Text("STATUS (V)", color = CyanNeon, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun EmergencyStopButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = RedNeon),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .testTag("btn_emergency_stop"),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "EMERGENCY STOP",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = "EMERGENCY SHUTDOWN (X)",
                color = Color.White,
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.titleMedium,
                letterSpacing = 1.2.sp
            )
        }
    }
}

@Composable
fun TerminalCard(
    terminalLines: List<TerminalLine>,
    hapticEnabled: Boolean,
    onClearLogs: () -> Unit,
    onSendRaw: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var rawInputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Automatic scroll-to-bottom on new messages
    LaunchedEffect(terminalLines.size) {
        if (terminalLines.isNotEmpty()) {
            listState.animateScrollToItem(terminalLines.lastIndex)
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(GreenNeon, CircleShape)
                    )
                    Text(
                        text = "TELEMETRY MONITOR",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF8E8E93),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                }
                TextButton(
                    onClick = {
                        if (hapticEnabled) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        onClearLogs()
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "Clear Logs",
                        color = RedNeon,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.06f), thickness = 0.5.dp)

            // Monospaced text box
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black.copy(alpha = 0.3f))
                    .border(0.5.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(14.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (terminalLines.isEmpty()) {
                    item {
                        Text(
                            text = "[System Idle] Awaiting connection...",
                            color = Color.Gray,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                    }
                } else {
                    items(terminalLines) { line ->
                        val textColor = if (line.isIncoming) GreenNeon else CyanNeon
                        Text(
                            text = line.text,
                            color = textColor,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Quick Command Macros
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "PRESETS",
                    fontSize = 10.sp,
                    color = Color(0xFF8E8E93),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    modifier = Modifier.padding(end = 4.dp)
                )
                val macros = listOf(
                    "PING" to "PING",
                    "GET SPEED" to "V",
                    "STATUS" to "?",
                    "STOP (S)" to "S",
                    "FORWARD (F)" to "F",
                    "RESET" to "RST"
                )
                macros.forEach { (label, cmd) ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                            .clickable {
                                if (hapticEnabled) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                                onSendRaw(cmd)
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = label,
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Quick command input
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.04f))
                    .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = rawInputText,
                    onValueChange = { rawInputText = it },
                    placeholder = { Text("Send raw command...", fontSize = 12.sp, color = Color.Gray, fontFamily = FontFamily.Monospace) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = CyanNeon,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("raw_command_input")
                )
                Button(
                    onClick = {
                        if (rawInputText.isNotEmpty()) {
                            if (hapticEnabled) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            onSendRaw(rawInputText)
                            rawInputText = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanNeon),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .height(36.dp)
                        .padding(end = 4.dp)
                        .testTag("raw_send_button")
                ) {
                    Text(
                        text = "Send",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun TacticalRobot3DVisualizer(
    activeCommand: Char,
    speedLevel: Int,
    modifier: Modifier = Modifier
) {
    // 1. Continuous rotation state for rotation commands
    var yawState by remember { mutableStateOf(45f) } // default angle for premium isometric view

    LaunchedEffect(activeCommand) {
        while (activeCommand == 'Q' || activeCommand == 'E') {
            val step = if (activeCommand == 'Q') -4f else 4f
            yawState = (yawState + step + 360f) % 360f
            kotlinx.coroutines.delay(16)
        }
    }

    // 2. Map command to physical chassis tilt angles
    val targetPitch = when (activeCommand) {
        'F' -> -15f
        'B' -> 15f
        'G' -> -10f
        'I' -> -10f
        'H' -> 10f
        'J' -> 10f
        else -> 0f
    }

    val targetRoll = when (activeCommand) {
        'L' -> -15f
        'R' -> 15f
        'G' -> -10f
        'I' -> 10f
        'H' -> -10f
        'J' -> 10f
        else -> 0f
    }

    // Smooth chassis response animation
    val animatedPitch by animateFloatAsState(
        targetValue = targetPitch,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "chassis_pitch"
    )
    val animatedRoll by animateFloatAsState(
        targetValue = targetRoll,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "chassis_roll"
    )

    // Animated vector pulse for live telemetry
    val transition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    val marchPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 30f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "march"
    )

    // Compute dynamic motor duty cycles for display
    val (flPower, frPower, blPower, brPower) = remember(activeCommand, speedLevel) {
        val base = speedLevel * 10
        when (activeCommand) {
            'F' -> listOf(base, base, base, base)
            'B' -> listOf(-base, -base, -base, -base)
            'L' -> listOf(-base, base, base, -base)
            'R' -> listOf(base, -base, -base, base)
            'G' -> listOf(0, base, base, 0)
            'I' -> listOf(base, 0, 0, base)
            'H' -> listOf(-base, 0, 0, -base)
            'J' -> listOf(0, -base, -base, 0)
            'Q' -> listOf(-base, base, -base, base)
            'E' -> listOf(base, -base, base, -base)
            else -> listOf(0, 0, 0, 0)
        }
    }

    // 3D Isometric math functions
    fun project3D(
        x: Float, y: Float, z: Float,
        cx: Float, cy: Float, scale: Float
    ): androidx.compose.ui.geometry.Offset {
        val yawRad = Math.toRadians(yawState.toDouble())
        val pitchRad = Math.toRadians(animatedPitch.toDouble())
        val rollRad = Math.toRadians(animatedRoll.toDouble())

        // 1. Rotate around Z (Yaw)
        val x1 = x * cos(yawRad) - y * sin(yawRad)
        val y1 = x * sin(yawRad) + y * cos(yawRad)
        val z1 = z

        // 2. Rotate around X (Pitch)
        val x2 = x1
        val y2 = y1 * cos(pitchRad) - z1 * sin(pitchRad)
        val z2 = y1 * sin(pitchRad) + z1 * cos(pitchRad)

        // 3. Rotate around Y (Roll)
        val x3 = x2 * cos(rollRad) + z2 * sin(rollRad)
        val y3 = y2
        val z3 = -x2 * sin(rollRad) + z2 * cos(rollRad)

        // Axonometric isometric projection:
        // 120 deg angles between axes. Z goes straight up.
        val screenX = cx + (x3 - y3) * 0.866f * scale
        val screenY = cy + (x3 + y3) * 0.5f * scale - z3 * scale

        return androidx.compose.ui.geometry.Offset(screenX.toFloat(), screenY.toFloat())
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Build,
                        contentDescription = "Chassis Mode",
                        tint = CyanNeon,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Real-Time 3D Telemetry Model",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Small Status Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (activeCommand != 'S') CyanNeon.copy(alpha = 0.15f)
                            else Color.Gray.copy(alpha = 0.15f)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (activeCommand != 'S') "MOVING ($activeCommand)" else "STDBY",
                        color = if (activeCommand != 'S') CyanNeon else Color.Gray,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Canvas Frame
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.4f))
                    .border(0.5.dp, Color(0xFF2E3440), RoundedCornerShape(10.dp))
            ) {
                // Interactive 3D Wireframe Canvas
                Canvas(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f + 10f // center with offset for height
                    val scale = size.minDimension / 140f

                    // 1. Draw Ground Grid/Circles for spatial reference
                    drawCircle(
                        color = Color(0xFF2E3440).copy(alpha = 0.5f),
                        radius = 45f * scale,
                        center = androidx.compose.ui.geometry.Offset(cx, cy + 12f * scale),
                        style = Stroke(width = 1f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f))
                    )
                    drawCircle(
                        color = Color(0xFF2E3440).copy(alpha = 0.3f),
                        radius = 25f * scale,
                        center = androidx.compose.ui.geometry.Offset(cx, cy + 12f * scale),
                        style = Stroke(width = 1f)
                    )

                    // Coordinate axis reference lines (tactical compass grid)
                    drawLine(
                        color = Color(0xFF2E3440).copy(alpha = 0.4f),
                        start = androidx.compose.ui.geometry.Offset(cx - 50f * scale, cy + 12f * scale),
                        end = androidx.compose.ui.geometry.Offset(cx + 50f * scale, cy + 12f * scale),
                        strokeWidth = 1f
                    )
                    drawLine(
                        color = Color(0xFF2E3440).copy(alpha = 0.4f),
                        start = androidx.compose.ui.geometry.Offset(cx, cy - 38f * scale),
                        end = androidx.compose.ui.geometry.Offset(cx, cy + 62f * scale),
                        strokeWidth = 1f
                    )

                    // 2. Define 3D wireframe points matching the actual robot CAD
                    // Bottom of the pyramidal base (z = -20)
                    val rBot = 25f
                    val zBot = -20f
                    val ld1 = project3D(-rBot, -rBot, zBot, cx, cy, scale)
                    val ld2 = project3D(rBot, -rBot, zBot, cx, cy, scale)
                    val ld3 = project3D(rBot, rBot, zBot, cx, cy, scale)
                    val ld4 = project3D(-rBot, rBot, zBot, cx, cy, scale)

                    // Top of the tapered pyramidal base (z = 4)
                    val rTop = 12f
                    val zTop = 4f
                    val ud1 = project3D(-rTop, -rTop, zTop, cx, cy, scale)
                    val ud2 = project3D(rTop, -rTop, zTop, cx, cy, scale)
                    val ud3 = project3D(rTop, rTop, zTop, cx, cy, scale)
                    val ud4 = project3D(-rTop, rTop, zTop, cx, cy, scale)

                    // Dome / Turret assembly on top of the base (z = 4 to 18)
                    val rDome = 9f
                    val zDomeTop = 18f
                    val td1 = project3D(-rDome, -rDome, zTop, cx, cy, scale)
                    val td2 = project3D(rDome, -rDome, zTop, cx, cy, scale)
                    val td3 = project3D(rDome, rDome, zTop, cx, cy, scale)
                    val td4 = project3D(-rDome, rDome, zTop, cx, cy, scale)

                    val dt1 = project3D(-rDome, -rDome, zDomeTop, cx, cy, scale)
                    val dt2 = project3D(rDome, -rDome, zDomeTop, cx, cy, scale)
                    val dt3 = project3D(rDome, rDome, zDomeTop, cx, cy, scale)
                    val dt4 = project3D(-rDome, rDome, zDomeTop, cx, cy, scale)
                    val domeCenter = project3D(0f, 0f, zDomeTop + 4f, cx, cy, scale)

                    // Vertical Launch Tube sticking out of the dome top (z = 18 to 44)
                    val rTube = 3.5f
                    val zTubeTop = 44f
                    val tb1 = project3D(-rTube, -rTube, zDomeTop, cx, cy, scale)
                    val tb2 = project3D(rTube, -rTube, zDomeTop, cx, cy, scale)
                    val tb3 = project3D(rTube, rTube, zDomeTop, cx, cy, scale)
                    val tb4 = project3D(-rTube, rTube, zDomeTop, cx, cy, scale)

                    val tt1 = project3D(-rTube, -rTube, zTubeTop, cx, cy, scale)
                    val tt2 = project3D(rTube, -rTube, zTubeTop, cx, cy, scale)
                    val tt3 = project3D(rTube, rTube, zTubeTop, cx, cy, scale)
                    val tt4 = project3D(-rTube, rTube, zTubeTop, cx, cy, scale)

                    // Mid-sections of the tube for ribbed/bellows texture
                    val tm1 = project3D(-rTube, -rTube, zDomeTop + 8f, cx, cy, scale)
                    val tm2 = project3D(rTube, -rTube, zDomeTop + 8f, cx, cy, scale)
                    val tm3 = project3D(rTube, rTube, zDomeTop + 8f, cx, cy, scale)
                    val tm4 = project3D(-rTube, rTube, zDomeTop + 8f, cx, cy, scale)

                    val tn1 = project3D(-rTube, -rTube, zDomeTop + 16f, cx, cy, scale)
                    val tn2 = project3D(rTube, -rTube, zDomeTop + 16f, cx, cy, scale)
                    val tn3 = project3D(rTube, rTube, zDomeTop + 16f, cx, cy, scale)
                    val tn4 = project3D(-rTube, rTube, zDomeTop + 16f, cx, cy, scale)

                    // 3. Draw Solid Panels & Outlines to represent the White/Blue/Dark Gray CAD design

                    // Colors matching the user's robot image:
                    val botWhite = Color(0xFFECEFF1)       // Premium off-white fiberglass
                    val botBlue = Color(0xFF2E659A)        // Royal blue geometric insert
                    val steelGray = Color(0xFF505A69)      // Structural gray steel
                    val darkConsole = Color(0xFF2A2D35)    // Matte dark gray parts

                    // --- DRAW PYRAMIDAL BASE PANELS ---
                    // Front Face Panel (ld1 -> ld2 -> ud2 -> ud1)
                    val frontFacePath = Path().apply {
                        moveTo(ld1.x, ld1.y)
                        lineTo(ld2.x, ld2.y)
                        lineTo(ud2.x, ud2.y)
                        lineTo(ud1.x, ud1.y)
                        close()
                    }
                    drawPath(path = frontFacePath, color = botWhite.copy(alpha = 0.85f))
                    drawPath(path = frontFacePath, color = steelGray, style = Stroke(width = 1.5f))

                    // Left Face Panel (ld4 -> ld1 -> ud1 -> ud4)
                    val leftFacePath = Path().apply {
                        moveTo(ld4.x, ld4.y)
                        lineTo(ld1.x, ld1.y)
                        lineTo(ud1.x, ud1.y)
                        lineTo(ud4.x, ud4.y)
                        close()
                    }
                    drawPath(path = leftFacePath, color = botWhite.copy(alpha = 0.75f))
                    drawPath(path = leftFacePath, color = steelGray, style = Stroke(width = 1f))

                    // Right Face Panel (ld2 -> ld3 -> ud3 -> ud2)
                    val rightFacePath = Path().apply {
                        moveTo(ld2.x, ld2.y)
                        lineTo(ld3.x, ld3.y)
                        lineTo(ud3.x, ud3.y)
                        lineTo(ud2.x, ud2.y)
                        close()
                    }
                    drawPath(path = rightFacePath, color = botWhite.copy(alpha = 0.65f))
                    drawPath(path = rightFacePath, color = steelGray, style = Stroke(width = 1f))

                    // Draw Blue Triangular/Chevron Geometric details on the Front Panel
                    // Let's find centers for chevron lines
                    val fbc = (ld1 + ld2) / 2f
                    val ftc = (ud1 + ud2) / 2f
                    val fml = (ld1 + ud1) / 2f
                    val fmr = (ld2 + ud2) / 2f

                    // Triangle Accent pointing down
                    val triDetailFront = Path().apply {
                        moveTo(ud1.x + (ud2.x - ud1.x) * 0.15f, ud1.y + (ud2.y - ud1.y) * 0.15f)
                        lineTo(ud2.x - (ud2.x - ud1.x) * 0.15f, ud2.y - (ud2.y - ud1.y) * 0.15f)
                        lineTo(fbc.x, fbc.y - 12f * scale)
                        close()
                    }
                    drawPath(path = triDetailFront, color = botBlue)

                    // Left Face blue triangles
                    val lbc = (ld4 + ld1) / 2f
                    val ltc = (ud4 + ud1) / 2f
                    val triDetailLeft = Path().apply {
                        moveTo(ud4.x + (ud1.x - ud4.x) * 0.15f, ud4.y + (ud1.y - ud4.y) * 0.15f)
                        lineTo(ud1.x - (ud1.x - ud4.x) * 0.15f, ud1.y - (ud1.y - ud4.y) * 0.15f)
                        lineTo(lbc.x, lbc.y - 12f * scale)
                        close()
                    }
                    drawPath(path = triDetailLeft, color = botBlue.copy(alpha = 0.85f))

                    // --- DRAW DOME / TURRET ASSEMBLY ---
                    // Cylinder part of the turret dome
                    val turretPath = Path().apply {
                        moveTo(td1.x, td1.y)
                        lineTo(td2.x, td2.y)
                        lineTo(dt2.x, dt2.y)
                        lineTo(dt1.x, dt1.y)
                        close()
                    }
                    drawPath(path = turretPath, color = darkConsole)
                    drawPath(path = turretPath, color = CyanNeon, style = Stroke(width = 1.5f))

                    // Dome round top (connecting DT to the center peak domeCenter)
                    val domePath = Path().apply {
                        moveTo(dt1.x, dt1.y)
                        lineTo(dt2.x, dt2.y)
                        lineTo(domeCenter.x, domeCenter.y)
                        close()
                    }
                    drawPath(path = domePath, color = darkConsole.copy(alpha = 0.9f))
                    drawLine(color = CyanNeon, start = dt1, end = domeCenter, strokeWidth = 1.5f)
                    drawLine(color = CyanNeon, start = dt2, end = domeCenter, strokeWidth = 1.5f)
                    drawLine(color = CyanNeon, start = dt3, end = domeCenter, strokeWidth = 1f)
                    drawLine(color = CyanNeon, start = dt4, end = domeCenter, strokeWidth = 1f)

                    // --- DRAW TALL RIUBBED LAUNCH TUBE ---
                    // Draw Launch Tube Body
                    val tubePath = Path().apply {
                        moveTo(tb1.x, tb1.y)
                        lineTo(tb2.x, tb2.y)
                        lineTo(tt2.x, tt2.y)
                        lineTo(tt1.x, tt1.y)
                        close()
                    }
                    drawPath(path = tubePath, color = botBlue.copy(alpha = 0.9f))
                    drawPath(path = tubePath, color = Color.White, style = Stroke(width = 1f))

                    // Ribbed details / rings along the tube to match the bellows in image
                    drawLine(color = AmberNeon, start = tm1, end = tm2, strokeWidth = 2.5f)
                    drawLine(color = AmberNeon, start = tm2, end = tm3, strokeWidth = 1.5f)
                    drawLine(color = AmberNeon, start = tn1, end = tn2, strokeWidth = 2.5f)
                    drawLine(color = AmberNeon, start = tn2, end = tn3, strokeWidth = 1.5f)
                    drawLine(color = AmberNeon, start = tt1, end = tt2, strokeWidth = 3f) // top nozzle rim

                    // Nozzle hole indicator
                    val nozzleCenter = (tt1 + tt2 + tt3 + tt4) / 4f
                    drawCircle(color = CyanNeon, radius = 2.5f * scale, center = nozzleCenter)

                    // 4. Draw 4 Angled Omni Wheels on lower corners
                    // We render them as cylinders oriented at 45 degree angles (X-Drive Configuration)
                    val wheelRadius = 8f
                    val wheelWidth = 4f

                    val corners = listOf(
                        Pair(ld1, -45f), // Front-Left
                        Pair(ld2, 45f),  // Front-Right
                        Pair(ld3, -45f), // Rear-Right
                        Pair(ld4, 45f)   // Rear-Left
                    )

                    corners.forEachIndexed { index, (corner, angleOffset) ->
                        // Dynamically compute the projection of wheel endpoints
                        // Since wheels are at 45deg, let's draw beautiful angled bars representing Omni rollers
                        val angleRad = Math.toRadians((yawState + angleOffset).toDouble())
                        
                        val wdx = (wheelRadius * cos(angleRad)).toFloat()
                        val wdy = (wheelRadius * sin(angleRad)).toFloat()

                        val wheelP1 = corner + androidx.compose.ui.geometry.Offset(wdx * scale, wdy * scale * 0.5f)
                        val wheelP2 = corner - androidx.compose.ui.geometry.Offset(wdx * scale, wdy * scale * 0.5f)

                        // Draw main rubber contact wheel rim
                        drawLine(
                            color = AmberNeon,
                            start = wheelP1,
                            end = wheelP2,
                            strokeWidth = 6f * scale
                        )

                        // Draw inner metal wheel center axle
                        drawCircle(
                            color = Color.White,
                            radius = 2f * scale,
                            center = corner
                        )

                        // If active command drives this motor, draw motion indicators (arrows / thrust particles)
                        val mPower = when (index) {
                            0 -> flPower
                            1 -> frPower
                            2 -> brPower
                            3 -> blPower
                            else -> 0
                        }

                        if (mPower != 0) {
                            // Compute vector arrows based on rotation and slide vectors
                            val thrustDirectionRad = when (activeCommand) {
                                'F' -> Math.toRadians((yawState + 90f).toDouble()) // straight ahead relative to chassis
                                'B' -> Math.toRadians((yawState - 90f).toDouble())
                                'L' -> Math.toRadians((yawState + 180f).toDouble())
                                'R' -> Math.toRadians(yawState.toDouble())
                                'Q' -> Math.toRadians((yawState + angleOffset + 90f).toDouble()) // rotational tangential force
                                'E' -> Math.toRadians((yawState + angleOffset - 90f).toDouble())
                                else -> 0.0
                            }

                            val tdx = (14f * cos(thrustDirectionRad)).toFloat()
                            val tdy = (14f * sin(thrustDirectionRad)).toFloat()

                            val vectorEnd = corner + androidx.compose.ui.geometry.Offset(tdx * scale * pulseScale, tdy * scale * 0.5f * pulseScale)

                            // Vector Arrow Line
                            drawLine(
                                color = if (mPower > 0) CyanNeon else RedNeon,
                                start = corner,
                                end = vectorEnd,
                                strokeWidth = 2.5f * scale
                            )
                            // Vector point head
                            drawCircle(
                                color = if (mPower > 0) CyanNeon else RedNeon,
                                radius = 3.5f * scale,
                                center = vectorEnd
                            )
                        }
                    }

                    // 5. Direction indicator arrow on top deck (points North/Front of Chassis)
                    val centerTop = project3D(0f, 0f, zTop, cx, cy, scale)
                    val frontIndicator = project3D(0f, -rTop * 0.8f, zTop, cx, cy, scale)

                    drawLine(
                        color = CyanNeon,
                        start = centerTop,
                        end = frontIndicator,
                        strokeWidth = 3f * scale
                    )
                    drawCircle(
                        color = CyanNeon,
                        radius = 4f * scale,
                        center = frontIndicator
                    )
                }

                // Numeric telemetry readout layer on top of Canvas
                // Top-Left corner: System variables
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text("SYS: CONNECTED", color = GreenNeon, fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    Text("X-DRIVE: ACTIVE", color = Color.White, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                    Text("CTRL_HZ: 60 FPS", color = Color.Gray, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                }

                // Top-Right corner: Isometric angles
                Column(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "YAW: ${String.format("%.1f", yawState)}°",
                        color = AmberNeon,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "PIT: ${String.format("%.1f", animatedPitch)}°",
                        color = if (animatedPitch != 0f) CyanNeon else Color.Gray,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "ROL: ${String.format("%.1f", animatedRoll)}°",
                        color = if (animatedRoll != 0f) CyanNeon else Color.Gray,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Bottom-Left corner: Duty cycle values
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text("M1(FL): $flPower%", color = if (flPower != 0) CyanNeon else Color.Gray, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                        Text("M2(FR): $frPower%", color = if (frPower != 0) CyanNeon else Color.Gray, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text("M3(RL): $blPower%", color = if (blPower != 0) CyanNeon else Color.Gray, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                        Text("M4(RR): $brPower%", color = if (brPower != 0) CyanNeon else Color.Gray, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                    }
                }

                // Bottom-Right corner: Interactive rotation hints
                Text(
                    text = "ROT CMD: PRESS Q/E",
                    color = Color.Gray,
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                )
            }
        }
    }
}

@Composable
fun BluetoothStatusBadge(
    connectionState: ConnectionState,
    deviceName: String?,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "pulse_state")
    val alphaPulse by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha_pulse"
    )

    val (color, text, isPulse) = when (connectionState) {
        ConnectionState.CONNECTED -> {
            val nameText = if (!deviceName.isNullOrBlank()) " ($deviceName)" else ""
            Triple(GreenNeon, "Connected$nameText", false)
        }
        ConnectionState.CONNECTING -> Triple(AmberNeon, "Connecting", true)
        ConnectionState.ERROR -> Triple(RedNeon, "BT Error", true)
        ConnectionState.DISCONNECTED -> Triple(Color(0xFF8E8E93), "Disconnected", false)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.12f))
            .border(0.5.dp, color.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        // Glowing Pulse Dot
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    color = if (isPulse) color.copy(alpha = alphaPulse) else color,
                    shape = CircleShape
                )
                .border(
                    width = 1.dp,
                    color = color.copy(alpha = if (isPulse) alphaPulse * 0.5f else 0.5f),
                    shape = CircleShape
                )
        )
        Text(
            text = text,
            color = if (connectionState == ConnectionState.DISCONNECTED) Color.White.copy(alpha = 0.6f) else color,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun DashboardTabs(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    hapticEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF161618))
            .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val tabs = listOf("Flight Deck", "Serial Monitor")
        tabs.forEachIndexed { index, title ->
            val isSelected = selectedTab == index
            val bgColor by animateColorAsState(
                targetValue = if (isSelected) Color(0xFF2C2C2E) else Color.Transparent,
                label = "tab_bg"
            )
            val textColor by animateColorAsState(
                targetValue = if (isSelected) Color.White else Color(0xFF8E8E93),
                label = "tab_text"
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(bgColor)
                    .clickable {
                        if (hapticEnabled) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        onTabSelected(index)
                    }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = textColor,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}


