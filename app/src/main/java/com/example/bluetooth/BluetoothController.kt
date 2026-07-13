package com.example.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

class BluetoothController(private val context: Context) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private var socket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _incomingMessages = MutableStateFlow<List<String>>(emptyList())
    val incomingMessages: StateFlow<List<String>> = _incomingMessages

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val sppUuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val scope = CoroutineScope(Dispatchers.IO)

    fun isBluetoothSupported(): Boolean = bluetoothAdapter != null

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled ?: false

    @SuppressLint("MissingPermission")
    fun getPairedDevices(): List<BluetoothDevice> {
        if (!isBluetoothSupported()) return emptyList()
        return try {
            bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun connect(device: BluetoothDevice): Boolean = withContext(Dispatchers.IO) {
        _connectionState.value = ConnectionState.CONNECTING
        _errorMessage.value = null
        closeConnection() // close any previous socket

        try {
            val socketRef = device.createRfcommSocketToServiceRecord(sppUuid)
            socket = socketRef
            
            // Cancel discovery because it slows down connections significantly
            try {
                bluetoothAdapter?.cancelDiscovery()
            } catch (_: SecurityException) {}

            socketRef.connect()
            inputStream = socketRef.inputStream
            outputStream = socketRef.outputStream

            _connectionState.value = ConnectionState.CONNECTED
            startListening()
            true
        } catch (e: Exception) {
            _errorMessage.value = e.localizedMessage ?: "Failed to connect"
            _connectionState.value = ConnectionState.ERROR
            closeConnection()
            false
        }
    }

    fun disconnect() {
        closeConnection()
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    private fun closeConnection() {
        try {
            inputStream?.close()
        } catch (_: Exception) {}
        try {
            outputStream?.close()
        } catch (_: Exception) {}
        try {
            socket?.close()
        } catch (_: Exception) {}
        inputStream = null
        outputStream = null
        socket = null
    }

    fun sendCommand(command: String): Boolean {
        if (_connectionState.value != ConnectionState.CONNECTED) return false
        val out = outputStream ?: return false
        return try {
            out.write(command.toByteArray())
            out.flush()
            true
        } catch (e: IOException) {
            _errorMessage.value = "Send failed: ${e.localizedMessage}"
            _connectionState.value = ConnectionState.ERROR
            closeConnection()
            false
        }
    }

    fun clearLogs() {
        _incomingMessages.value = emptyList()
    }

    private fun startListening() {
        scope.launch {
            val reader = inputStream ?: return@launch
            val buffer = ByteArray(1024)
            var bytes: Int
            val lineBuffer = StringBuilder()

            while (_connectionState.value == ConnectionState.CONNECTED) {
                try {
                    bytes = reader.read(buffer)
                    if (bytes > 0) {
                        val str = String(buffer, 0, bytes)
                        lineBuffer.append(str)
                        
                        // Process complete lines
                        while (lineBuffer.contains("\n")) {
                            val index = lineBuffer.indexOf("\n")
                            val line = lineBuffer.substring(0, index).trim()
                            lineBuffer.delete(0, index + 1)
                            if (line.isNotEmpty()) {
                                _incomingMessages.value = (_incomingMessages.value + line).takeLast(100)
                            }
                        }
                    }
                } catch (e: IOException) {
                    if (_connectionState.value == ConnectionState.CONNECTED) {
                        _errorMessage.value = "Connection lost"
                        _connectionState.value = ConnectionState.ERROR
                    }
                    closeConnection()
                    break
                }
            }
        }
    }
}
