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
import java.lang.Math.abs
import java.lang.Math.round
import java.util.*
import kotlin.math.roundToInt


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

    private suspend fun enterPassword(address: ByteArray, nDevId: Int): Boolean{
        val pIndex = if(nDevId in Constants.CURSED_IDS) 1 else 0
        var result = 3
        var recData: ByteArray
        for(i in 0 until Constants.PULSAR_PASSWORDS.size){
            val pass = Constants.PULSAR_PASSWORDS[i]
            val pPass = toBytes(pass, 4)
            val r = tryAttempts(PulsarFunc.injectCRC(address +
                    Constants.PASSWORD_REQUEST[pIndex] + pPass +
                    pPass + toBytes(i+1, 2)))
            result = r.first
            recData = r.second
            if(result==1){
                if(PulsarFunc.handlePulsarErrorCode(recData)==0){
                    return true
                }
            }
        }
        return false
    }

    private suspend fun writeNewValue(address: ByteArray, newValue: Double, newAddress: Double, devId: Int): Triple<Int, ByteArray, Double>{
        val multiplier = if(devId==98) 1 else 1000
        var value: Double = ((newValue*1000).roundToInt().toDouble()/1000.0) * multiplier
        var dataValue = ByteArray(4)
        if(devId == 98){
            dataValue = floatToHex(value)
        }else{
            dataValue = toBytes(value.toInt(), 4)
            value /= 1000
        }
        val res = tryAttempts(PulsarFunc.injectCRC(address + Constants.WRITE_VALUE_REQUEST + dataValue + toBytes(1, 2)))
        return Triple(res.first, res.second, value)
    }

    private fun toBytes(ch: Int, size: Int): ByteArray{
        var s = ch.toString(16)
        if(s.length%2==1){
            s = "0$s"
        }
        val bytes = if (s.length/2<=size){
            ByteArray(size)
        }else{
            ByteArray(s.length/2)
        }
        var i = s.length-1
        var ii = 0
        while(i>0){
            bytes[ii] = (s[i-1].toString() + s[i].toString()).toUByte(16).toByte()
            i-=2
            ii+=1
        }
        while(ii<bytes.size){
            bytes[ii] = 0
            ii+=1
        }
        return bytes
    }


    //TODO неточные числа
    private fun floatToHex(_ch: Double): ByteArray{
        val ch = kotlin.math.abs(_ch)
        var m = 0
        if(ch==0.0){
            return byteArrayOf(0, 0, 0, 0)
        }else if(ch.toInt() == 0){
            var mm = -1
            for(i in drToBin(ch)){
                if(i == '1'){
                    m = mm
                    break
                }
                mm -= 1
            }
        }else{
            m = cToBin(ch).length - 1
        }
        val z = if(_ch<0) 1 else 0
        val step = (m + 127).toString(2).padStart(8, '0')
        var mant = ""
        mant = if(m==0){
            drToBin(ch).padEnd(23, '0')
        }else if(m>0){
            (cToBin(ch).substring(1) + drToBin(ch)).padEnd(23, '0')
        }else{
            drToBin(ch).substring(abs(m)).padEnd(23, '0')
        }
        println(cToBin(ch))
        println(drToBin(ch))
        var res = ""
        var i = 0
        val s = z.toString() + step + mant
        while(i < s.length){
            res += s.substring(i..i+3).toInt(2).toString(16)
            i += 4
        }
        i = res.length-1
        var ii = 0
        val resB = ByteArray(4)
        while(i>0){
            resB[ii] = (res[i-1].toString() + res[i].toString()).toUByte(16).toByte()
            ii += 1
            i -= 2
        }
        return resB
    }

    private fun cToBin(a: Double): String{
        val ch: Int = a.toInt()
        return ch.toString(2)
    }

    private fun drToBin(a: Double): String{
        var ch = a % 1
        var s = ""
        for(i in 0..22){
            ch *= 2
            if (ch>=1){
                s+="1"
                ch-=1
            }else{
                s+="0"
            }
            if(ch==0.0){
                break
            }
        }
        return s
    }
}