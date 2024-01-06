package com.ibaevzz.bdev

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*


class MainActivity : AppCompatActivity() {

    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null
    private var address = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        address = intent.getStringExtra("MAC").toString()
        lifecycleScope.launch {
            connect(address)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        closeConnection()
    }

    private suspend fun connect(deviceAddress: String): Boolean {
        val device: BluetoothDevice? = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(deviceAddress)
        if (ActivityCompat.checkSelfPermission(this@MainActivity, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED) { return false }

        bluetoothSocket = device?.createInsecureRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"))
        try {
            withContext(Dispatchers.IO) {
                bluetoothSocket?.connect()
                outputStream = bluetoothSocket?.outputStream
                inputStream = bluetoothSocket?.inputStream
            }
            Toast.makeText(this@MainActivity, "Успешно подключено", Toast.LENGTH_SHORT).show()
        }catch (_: IOException){
            Toast.makeText(this@MainActivity, "Не удалось подключиться", Toast.LENGTH_SHORT).show()
        }
        return true
    }

    private suspend fun sendMessage(message: ByteArray): ByteArray? {
        var recData: ByteArray? = null
        try {
            withContext(Dispatchers.IO) {
                outputStream?.write(message)
            }
            recData = readMessage()
        } catch (e: IOException) {
            Toast.makeText(this@MainActivity, "Не удалось отправить сообщение", Toast.LENGTH_SHORT).show()
        }
        return recData
    }

    private suspend fun readMessage(): ByteArray? {
        val buffer = ByteArray(128)
        val bytes: Int
        try {
            delay(500)
            return withContext(Dispatchers.IO) {
                if ((inputStream?.available() ?: 0) > 0) {
                    bytes = inputStream?.read(buffer) ?: -1
                    if (bytes != -1) {
                        val result = ByteArray(bytes)
                        for (i in 0 until bytes) {
                            result[i] = buffer[i]
                        }
                        return@withContext result
                    }
                }
                return@withContext null
            }
        } catch (e: IOException) {
            Toast.makeText(this@MainActivity, "Ошибка чтение", Toast.LENGTH_SHORT).show()
        }
        return null
    }

    private fun closeConnection() {
        try {
            outputStream?.close()
            inputStream?.close()
            bluetoothSocket?.close()
        } catch (e: IOException) {
            Toast.makeText(this@MainActivity, "Не удалось отключиться", Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun tryAttempts(nData: ByteArray): Pair<Int, ByteArray>{
        var result = 3
        var recData: ByteArray? = null
        for(i in 0..2) {
            recData = sendMessage(nData)
            if (recData != null && recData.isNotEmpty()) {
                if (PulsarFunc.checkCRC(recData)){
                    result = 1
                    break
                }else{
                    result = 2
                }
            }
        }
        return result to (recData?:ByteArray(0))
    }
}