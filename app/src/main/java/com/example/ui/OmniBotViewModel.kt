package com.example.ui

import android.app.Application
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bluetooth.BluetoothController
import com.example.bluetooth.ConnectionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class OmniBotViewModel(application: Application) : AndroidViewModel(application) {

    val bluetoothController = BluetoothController(application.applicationContext)

    private val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = application.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        application.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    val connectionState = bluetoothController.connectionState
    val incomingMessages = bluetoothController.incomingMessages
    val errorMessage = bluetoothController.errorMessage

    private val _connectedDevice = MutableStateFlow<BluetoothDevice?>(null)
    val connectedDevice: StateFlow<BluetoothDevice?> = _connectedDevice

    private val _terminalLines = MutableStateFlow<List<TerminalLine>>(emptyList())
    val terminalLines: StateFlow<List<TerminalLine>> = _terminalLines

    private val _speedLevel = MutableStateFlow(6) // Default speed level
    val speedLevel: StateFlow<Int> = _speedLevel

    private val _launcherRunning = MutableStateFlow(false)
    val launcherRunning: StateFlow<Boolean> = _launcherRunning

    private val _launcherSpeed = MutableStateFlow(5) // Default launcher speed level
    val launcherSpeed: StateFlow<Int> = _launcherSpeed

    private val _targetAngle = MutableStateFlow(20.0f) // default 20 degrees (mid of 0 to 43)
    val targetAngle: StateFlow<Float> = _targetAngle

    private val _angleHomed = MutableStateFlow(false)
    val angleHomed: StateFlow<Boolean> = _angleHomed

    private val _limitSwitchActive = MutableStateFlow(false)
    val limitSwitchActive: StateFlow<Boolean> = _limitSwitchActive

    private val _currentAngle = MutableStateFlow(0.0f)
    val currentAngle: StateFlow<Float> = _currentAngle

    private val _activeCommand = MutableStateFlow('S')
    val activeCommand: StateFlow<Char> = _activeCommand

    val hapticEnabled = MutableStateFlow(true)

    init {
        // Collect incoming messages from BluetoothController and add to chronological log
        viewModelScope.launch {
            incomingMessages.collect { list ->
                val lastIncoming = list.lastOrNull() ?: return@collect
                // Check if this line is already added to avoid duplication on StateFlow emissions
                val exists = _terminalLines.value.any { it.isIncoming && it.text == lastIncoming && System.currentTimeMillis() - it.timestamp < 1000 }
                if (!exists) {
                    addTerminalLine("RX: $lastIncoming", isIncoming = true)
                    parseIncomingMessage(lastIncoming)
                }
            }
        }

        // Monitor connection state to auto-reset connectedDevice if disconnected
        viewModelScope.launch {
            connectionState.collect { state ->
                if (state == ConnectionState.DISCONNECTED || state == ConnectionState.ERROR) {
                    _connectedDevice.value = null
                }
            }
        }
    }

    fun addTerminalLine(text: String, isIncoming: Boolean) {
        val newLine = TerminalLine(text, isIncoming)
        _terminalLines.value = (_terminalLines.value + newLine).takeLast(100)
    }

    fun triggerVibration() {
        if (!hapticEnabled.value) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(40)
            }
        } catch (_: Exception) {}
    }

    fun sendCommand(command: String) {
        val success = bluetoothController.sendCommand(command)
        addTerminalLine("TX: $command ${if (success) "✓" else "✗"}", isIncoming = false)
    }

    fun setSpeed(level: Int) {
        if (level in 0..9) {
            _speedLevel.value = level
            sendCommand(level.toString())
            triggerVibration()
        }
    }

    fun adjustSpeed(increment: Boolean) {
        val current = _speedLevel.value
        if (increment && current < 9) {
            _speedLevel.value = current + 1
            sendCommand("+")
            triggerVibration()
        } else if (!increment && current > 1) {
            _speedLevel.value = current - 1
            sendCommand("-")
            triggerVibration()
        }
    }

    fun handleMovementPress(command: Char) {
        if (_activeCommand.value == command) return // Prevent repeated signals on simple hold
        _activeCommand.value = command
        
        // Inversi maju-mundur karena arah motor fisik pada robot terbalik
        val translatedCommand = when (command) {
            'F' -> 'B' // Maju mengirim B (Mundur)
            'B' -> 'F' // Mundur mengirim F (Maju)
            'G' -> 'H' // Diagonal Maju-Kiri mengirim Diagonal Mundur-Kiri
            'I' -> 'J' // Diagonal Maju-Kanan mengirim Diagonal Mundur-Kanan
            'H' -> 'G' // Diagonal Mundur-Kiri mengirim Diagonal Maju-Kiri
            'J' -> 'I' // Diagonal Mundur-Kanan mengirim Diagonal Maju-Kanan
            else -> command
        }
        
        sendCommand(translatedCommand.toString())
        triggerVibration()
    }

    fun handleMovementRelease() {
        _activeCommand.value = 'S'
        sendCommand("S")
        triggerVibration()
    }

    fun toggleLauncher(enable: Boolean) {
        _launcherRunning.value = enable
        sendCommand(if (enable) "K" else "M")
        triggerVibration()
    }

    fun setLauncherSpeed(level: Int) {
        if (level in 0..9) {
            _launcherSpeed.value = level
            if (level == 0) {
                _launcherRunning.value = false
            } else {
                _launcherRunning.value = true
            }
            sendCommand("P$level")
            triggerVibration()
        }
    }

    fun adjustLauncherSpeed(increment: Boolean) {
        val current = _launcherSpeed.value
        if (increment && current < 9) {
            _launcherSpeed.value = current + 1
            sendCommand("]")
            triggerVibration()
        } else if (!increment && current > 0) {
            val next = current - 1
            _launcherSpeed.value = next
            if (next == 0) {
                _launcherRunning.value = false
            }
            sendCommand("[")
            triggerVibration()
        }
    }

    fun setAngle(angle: Float) {
        val constrained = angle.coerceIn(0.0f, 43.0f)
        _targetAngle.value = constrained
        val cmd = "A" + String.format(java.util.Locale.US, "%.1f", constrained) + "#"
        sendCommand(cmd)
        triggerVibration()
    }

    fun jogAngle(increment: Boolean) {
        val current = _targetAngle.value
        if (increment) {
            if (current < 43.0f) {
                _targetAngle.value = (current + 1.0f).coerceAtMost(43.0f)
            }
            sendCommand("U")
        } else {
            if (current > 0.0f) {
                _targetAngle.value = (current - 1.0f).coerceAtLeast(0.0f)
            }
            sendCommand("D")
        }
        triggerVibration()
    }

    fun startHoming() {
        sendCommand("Z")
        triggerVibration()
    }

    fun stopAngleMotion() {
        sendCommand("C")
        triggerVibration()
    }

    fun requestAngleStatus() {
        sendCommand("V")
        triggerVibration()
    }

    fun executeEmergencyStop() {
        _activeCommand.value = 'S'
        _launcherRunning.value = false
        sendCommand("X")
        triggerVibration()
    }

    fun connectDevice(device: BluetoothDevice) {
        viewModelScope.launch {
            _connectedDevice.value = device
            addTerminalLine("Connecting to ${device.name ?: "Device"}...", isIncoming = false)
            val success = bluetoothController.connect(device)
            if (success) {
                addTerminalLine("Connected to ${device.name ?: "Unknown"}", isIncoming = false)
                // Synchronize speed after connection
                sendCommand(_speedLevel.value.toString())
            } else {
                _connectedDevice.value = null
                val error = errorMessage.value ?: "Connection failed"
                addTerminalLine("Error: $error", isIncoming = false)
            }
        }
    }

    fun disconnectDevice() {
        bluetoothController.disconnect()
        _connectedDevice.value = null
        addTerminalLine("Disconnected.", isIncoming = false)
    }

    fun clearTerminal() {
        _terminalLines.value = emptyList()
        bluetoothController.clearLogs()
    }

    private fun parseIncomingMessage(msg: String) {
        val lower = msg.lowercase()
        
        // Match: Sudut=12.34 deg, target=30.00 deg, homed=YA, minLimit=OFF
        if (lower.contains("sudut=")) {
            // Parse angle value
            """(?i)sudut=([-\d.]+)""".toRegex().find(msg)?.let { match ->
                match.groupValues.getOrNull(1)?.toFloatOrNull()?.let { valAngle ->
                    _currentAngle.value = valAngle
                }
            }
            
            // Parse homed
            """(?i)homed=(ya|tidak)""".toRegex().find(msg)?.let { match ->
                val homedStr = match.groupValues.getOrNull(1)?.uppercase()
                _angleHomed.value = (homedStr == "YA")
            }
            
            // Parse minLimit
            """(?i)minlimit=(aktif|off)""".toRegex().find(msg)?.let { match ->
                val limitStr = match.groupValues.getOrNull(1)?.uppercase()
                _limitSwitchActive.value = (limitStr == "AKTIF")
            }
        }
        
        if (lower.contains("limit bawah aktif") || lower.contains("limit minimum aktif")) {
            _limitSwitchActive.value = true
            _angleHomed.value = true
            _currentAngle.value = 0.0f
        }
        
        if (lower.contains("homing selesai")) {
            _angleHomed.value = true
            _currentAngle.value = 0.0f
            _limitSwitchActive.value = false
        }
        
        if (lower.contains("homing gagal")) {
            _angleHomed.value = false
        }
        
        // Parse "Sudut tercapai=12.34 derajat" or "Sudut tercapai=12.34"
        if (lower.contains("sudut tercapai=")) {
            """(?i)sudut tercapai=([-\d.]+)""".toRegex().find(msg)?.let { match ->
                match.groupValues.getOrNull(1)?.toFloatOrNull()?.let { valAngle ->
                    _currentAngle.value = valAngle
                    _targetAngle.value = valAngle
                }
            }
        }
        
        // Parse "Target sudut=12.34"
        if (lower.contains("target sudut=")) {
            """(?i)target sudut=([-\d.]+)""".toRegex().find(msg)?.let { match ->
                match.groupValues.getOrNull(1)?.toFloatOrNull()?.let { valAngle ->
                    _targetAngle.value = valAngle
                }
            }
        }
    }
}

data class TerminalLine(
    val text: String,
    val isIncoming: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
