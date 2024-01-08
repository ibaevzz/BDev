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
import kotlin.math.pow
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    var i = 0

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
        if(!BluetoothAdapter.getDefaultAdapter().isEnabled) return false
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
            return false
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
            Toast.makeText(this@MainActivity, "Ошибка", Toast.LENGTH_SHORT).show()
        }
        return recData
    }

    private suspend fun readMessage(): ByteArray? {
        val buffer = ByteArray(128)
        val bytes: Int
        try {
            delay(300)
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
            Toast.makeText(this@MainActivity, "Ошибка", Toast.LENGTH_SHORT).show()
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
        var result: Int
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

    private suspend fun writeNewValue(address: ByteArray, newValue: Double, devId: Int): Triple<Int, ByteArray, Double>{
        val multiplier = if(devId==98) 1 else 1000
        var value: Double = ((newValue*1000).roundToInt().toDouble()/1000.0) * multiplier
        val dataValue: ByteArray
        if(devId == 98){
            dataValue = floatToHex(value)
        }else{
            dataValue = toBytes(value.roundToInt(), 4)
            value /= 1000
        }
        val res = tryAttempts(PulsarFunc.injectCRC(address + Constants.WRITE_VALUE_REQUEST + dataValue + toBytes(1, 2)))
        return Triple(res.first, res.second, value)
    }

    private suspend fun writeAddress(address: ByteArray, newAddress: Int, devId: Int): Triple<Int, ByteArray, Int>{
        val index = if(devId in Constants.CURSED_IDS) 1 else 0
        var requestID = 1
        val dataValue: ByteArray
        if(PulsarFunc.splitAddressPulsar(newAddress.toString()).contentEquals(address)||
                newAddress == 0){
            return Triple(5, ByteArray(0), newAddress)
        }
        if(index==0){
            dataValue = toBytes(newAddress, 8)
        }else{
            tryAttempts(PulsarFunc.injectCRC(address +
                    Constants.WRITE_ADDRESS_REQUEST[2] +
                    toBytes(requestID, 2)))
            requestID += 1
            dataValue = PulsarFunc.cursedAddressToBytes(newAddress.toString())
        }
        val result = tryAttempts(PulsarFunc.injectCRC(address +
                Constants.WRITE_ADDRESS_REQUEST[index] + dataValue +
                toBytes(requestID, 2)))
        return Triple(result.first, result.second, newAddress)
    }

    private suspend fun getDeviceID(address: ByteArray): Int{
        var devID = -1
        for(i in 0 until Constants.GET_DEVICE_ID_REQUEST.size){
            val res = tryAttempts(PulsarFunc.injectCRC(address + Constants.GET_DEVICE_ID_REQUEST[i]))
            if(res.first == 1){
                if(PulsarFunc.handlePulsarErrorCode(res.second)!=0){
                    continue
                }
                devID = if(i == 0){
                    PulsarFunc.newType(res.second)
                }else{
                    PulsarFunc.oldType(res.second)
                }
                break
            }else if(res.first == 3){
                break
            }
        }
        return devID
    }

    private suspend fun getValue(address: ByteArray, devId: Int): Double{
        var value = 0.0
        val res = tryAttempts(PulsarFunc.injectCRC(address +
                Constants.GET_VALUE_REQUEST
                + toBytes(1, 2)))
        if(res.first == 1){
            if(res.second.size>=10){
                val payload = byteArrayOf(res.second[6],
                        res.second[7],
                        res.second[8],
                        res.second[9])
                value = if(devId == 98){
                    (hexToFloat(payload)*1000).roundToInt()/1000.0
                }else{
                    fromBytes(payload).toDouble() / 1000.0
                }
            }
        }
        return value
    }

    private suspend fun getAddress(address: ByteArray, devId: Int): Int{
        val index = if(devId in Constants.CURSED_IDS) 1 else 0
        val res = tryAttempts(PulsarFunc.injectCRC(address+
                Constants.GET_ADDRESS_REQUEST[index] +
                toBytes(1, 2)))
        var value = 0
        if(res.first==1){
            if(index==0){
                value = PulsarFunc.newAddr(res.second)
            }else{
                val recData = ByteArray(8)
                for(i in 0..7){
                    recData[i] = res.second[i+6]
                }
                value = PulsarFunc.cursedAddress(recData)
            }
        }
        return value
    }

    private suspend fun pulsarAddress(): Pair<Int, Double>{
        val func = arrayOf(PulsarFunc::oldAddr, PulsarFunc::newAddr,
            PulsarFunc::newType, PulsarFunc::oldType)
        val resultDict = mutableMapOf<String, Int>()

        for(i in Constants.ADDRESS_REQUEST.indices){
            val key = if(i<2) "address" else "devid"
            if(i < 2 && resultDict.containsKey("address") ||
                (i > 1 && resultDict.containsKey("devid")))
                continue
            if(!resultDict.containsKey("address") && i > 1)
                break
            val res = tryAttempts(PulsarFunc.injectCRC(if(i < 2)
                Constants.ADDRESS_REQUEST[i] else
                PulsarFunc.splitAddressPulsar(
                    resultDict["address"].toString()) + Constants.ADDRESS_REQUEST[i]))
            if(res.first==1){
                if(res.second[4] == 0.toByte() && res.second[6] == 4.toByte()){
                    continue
                }else{
                    resultDict[key] = func[i](res.second)
                }
            }
        }
        val value = getValue(PulsarFunc.splitAddressPulsar(
            resultDict["address"].toString()),
            resultDict["devid"]?:0)
        return (resultDict["address"]?:0) to value
    }

    private suspend fun pulsarWrite(oldAddress: String, newValue: Double, newAddress: Int): Boolean{
        if(!PulsarFunc.isValidAddress(newAddress.toString())){
            return false
        }
        val pAddress = PulsarFunc.splitAddressPulsar(oldAddress)

        val devId = getDeviceID(pAddress)
        if(devId==-1) return false

        val resValue = writeNewValue(pAddress, newValue, devId)
        if(resValue.first==3) return false
        if(resValue.first==1){
            if(PulsarFunc.handlePulsarErrorCode(resValue.second)!=0){
                if(!enterPassword(pAddress, devId)){
                    return false
                }
            }
        }
        val addressRes = writeAddress(pAddress, newAddress, devId)
        if(addressRes.first==1){
            if(PulsarFunc.handlePulsarErrorCode(addressRes.second)==0) {
                return true
            }
        }
        return false
    }

    private fun fromBytes(ch: ByteArray): Int{
        var s = ""
        for(i in ch.reversed()){
            s += i.toUByte().toString(16).padStart(2, '0')
        }
        return s.toInt(16)
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

    private fun hexToFloat(hex: ByteArray): Double{
        val s = hex[3].toUByte().toString(2).padStart(8, '0') +
                hex[2].toUByte().toString(2).padStart(8, '0') +
                hex[1].toUByte().toString(2).padStart(8, '0') +
                hex[0].toUByte().toString(2).padStart(8, '0')
        val z = s[0].digitToInt()
        val step = s.substring(1, 9).toInt(2) - 127
        var mant = "1${s.substring(9)}"
        mant = if(step>=0){
            mant.substring(0, step + 1)+'.'+mant.substring(step+1)
        }else{
            "0." + mant.padStart(23 + kotlin.math.abs(step), '0')
        }
        var ch = 0.0
        var t = 0
        var ii = 0
        for(i in mant){
            if(i=='.'){
                t = ii
                break
            }
            ii+=1
        }
        ii = t - 1
        var ss = 0
        while(ii >= 0){
            ch += mant[ii].digitToInt() * 2.0.pow(ss.toDouble())
            ss += 1
            ii -= 1
        }
        ii = t + 1
        ss = -1
        while(ii<mant.length){
            ch += mant[ii].digitToInt() * 2.0.pow(ss.toDouble())
            ss -= 1
            ii += 1
        }
        return if(z==1) -ch else ch
    }

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
        val mant = if(m==0){
            drToBin(ch).padEnd(23, '0')
        }else if(m>0){
            (cToBin(ch).substring(1) + drToBin(ch)).padEnd(23, '0')
        }else{
            if(drToBin(ch).length<=23)
                drToBin(ch).substring(kotlin.math.abs(m)).padEnd(23, '0')
            else
                drToBin(ch).substring(kotlin.math.abs(m), 23 + kotlin.math.abs(m))
        }
        var res = ""
        var i = 0
        val s = z.toString() + step + mant.substring(0, 23)
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
        for(i in 0..40){
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