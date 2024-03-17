package com.ibaevzz.bdev

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.net.wifi.WifiManager
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.Socket
import java.util.*
import kotlin.math.pow
import kotlin.math.roundToInt

@Suppress("DEPRECATION")
@SuppressLint("MissingPermission")
class PulsarService(private val context: Context, private val bAddress: String) {

    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null
    private var socket: Socket? = null
    private val isConnect get() = socket?.isConnected?:false
    private var macAddress = "0"

    suspend fun connect(): Boolean {
        if(!BluetoothAdapter.getDefaultAdapter().isEnabled) return false
        try {
            val device: BluetoothDevice? = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(bAddress)
            bluetoothSocket = device?.createInsecureRfcommSocketToServiceRecord(UUID.fromString(Constants.UUID))
            withContext(Dispatchers.IO) {
                bluetoothSocket?.connect()
                outputStream = bluetoothSocket?.outputStream
                inputStream = bluetoothSocket?.inputStream
            }
        }catch (ex: IOException) {
            return false
        }
        return true
    }

    suspend fun connectWifi(address: String, port: String){
        this.macAddress = address
        val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        if(!wifiManager.isWifiEnabled) throw Exception()
        if(wifiManager.dhcpInfo.gateway != macAddress.toInt()) throw Exception()
        if(!wifiManager.connectionInfo.ssid.contains(PMSK_PNR)) throw Exception()
        withContext(Dispatchers.IO) {
            if(isConnect) return@withContext
            closeWifiConnection()

            val ip = address.toInt()

            socket = Socket(
                InetAddress.getByAddress(byteArrayOf(
                    (ip).toByte(),
                    (ip ushr 8).toByte(),
                    (ip ushr 16).toByte(),
                    (ip ushr 24).toByte())), port.toInt())

            if (socket?.isConnected != true) throw Exception()
            inputStream = socket?.getInputStream()
            outputStream = socket?.getOutputStream()
        }
    }

    suspend fun closeWifiConnection(){
        withContext(Dispatchers.IO){
            if(!isConnect) return@withContext
            outputStream?.close()
            inputStream?.close()
            socket?.close()
        }
    }

    fun closeConnection() {
        try {
            outputStream?.close()
            inputStream?.close()
            bluetoothSocket?.close()
        } catch (e: IOException) {
            Toast.makeText(context, "Не удалось отключиться", Toast.LENGTH_SHORT).show()
        }
    }

    suspend fun pulsarAddress(): Pair<Int, Double>{
        val func = arrayOf(::oldAddr, ::newAddr, ::newType, ::oldType)
        val resultDict = mutableMapOf<String, Int>()

        for(i in Constants.ADDRESS_REQUEST.indices){
            val key = if(i<2) "address" else "devid"
            if(i < 2 && resultDict.containsKey("address") ||
                (i > 1 && resultDict.containsKey("devid")))
                continue
            if(!resultDict.containsKey("address") && i > 1)
                break
            val res = tryAttempts(injectCRC(if(i < 2)
                Constants.ADDRESS_REQUEST[i] else
                splitAddressPulsar(
                    (resultDict["address"]?:0).toString()) + Constants.ADDRESS_REQUEST[i]))
            if(res.first==1){
                if(res.second[4] == 0.toByte() && res.second[6] == 4.toByte()){
                    continue
                }else{
                    resultDict[key] = func[i](res.second)
                }
            }
        }
        val value = getValue(splitAddressPulsar(
            resultDict["address"].toString()),
            resultDict["devid"]?:0)
        return (resultDict["address"]?:0) to (value ?: 0.0)
    }

    suspend fun pulsarWrite(oldAddress: String, newValue: Double, newAddress: Int): Boolean{
        if(!isValidAddress(newAddress.toString())){
            return false
        }
        val pAddress = splitAddressPulsar(oldAddress)

        val devId = getDeviceID(pAddress)
        if(devId==-1) return false

        val resValue = writeNewValue(pAddress, newValue, devId)
        if(resValue.first==3) return false
        if(resValue.first==1){
            if(handlePulsarErrorCode(resValue.second)!=0){
                if(!enterPassword(pAddress, devId)){
                    return false
                }
            }
        }
        if(oldAddress.toInt() == newAddress){
            return true
        }
        val addressRes = writeAddress(pAddress, newAddress, devId)
        if(addressRes.first==1){
            if(handlePulsarErrorCode(addressRes.second)==0) {
                return true
            }
        }
        return false
    }

    suspend fun pulsarRead(address: String): Triple<Int?, Int?, Double?>{
        val pAddress = splitAddressPulsar(address)
        val devId = getDeviceID(pAddress)
        if(devId == -1)
            return Triple(null, null, null)
        val mAddress = getAddress(pAddress, devId)
        if(mAddress == -1){
            return Triple(devId, null, null)
        }
        val value = getValue(pAddress, devId) ?: return Triple(devId, mAddress, null)
        return Triple(devId, mAddress, value)
    }

    private suspend fun sendMessage(message: ByteArray): ByteArray? {
        var recData: ByteArray? = null
        try {
            withContext(Dispatchers.IO) {
                outputStream?.write(message)
            }
            recData = readMessage()
        } catch (e: IOException) {
            Toast.makeText(context, "Ошибка", Toast.LENGTH_SHORT).show()
        }
        return recData
    }

    private suspend fun readMessage(): ByteArray? {
        val buffer = ByteArray(128)
        var reqResult: ByteArray? = null
        var bytes: Int
        try {
            return withContext(Dispatchers.IO) {
                while(true) {
                    delay(1000)
                    if ((inputStream?.available() ?: 0) > 0) {
                        bytes = inputStream?.read(buffer) ?: -1
                        if (bytes != -1) {
                            val result = ByteArray(bytes)
                            for (i in 0 until bytes) {
                                result[i] = buffer[i]
                            }
                            reqResult = if(reqResult!=null){
                                reqResult!! + result
                            }else{
                                result
                            }
                        }
                    }else{
                        break
                    }
                }
                return@withContext reqResult
            }
        } catch (e: IOException) {
            Toast.makeText(context, "Ошибка", Toast.LENGTH_SHORT).show()
        }
        return null
    }

    private suspend fun tryAttempts(nData: ByteArray): Pair<Int, ByteArray>{
        var result = 3
        var recData: ByteArray? = null
        for(i in 0..2) {
            recData = sendMessage(nData)
            if (recData != null && recData.isNotEmpty()) {
                if (checkCRC(recData)){
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
            val r = tryAttempts(injectCRC(address +
                    Constants.PASSWORD_REQUEST[pIndex] + pPass +
                    pPass + toBytes(i+1, 2)))
            result = r.first
            recData = r.second
            if(result==1){
                if(handlePulsarErrorCode(recData)==0){
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
        val res = tryAttempts(injectCRC(address + Constants.WRITE_VALUE_REQUEST + dataValue + toBytes(1, 2)))
        return Triple(res.first, res.second, value)
    }

    private suspend fun writeAddress(address: ByteArray, newAddress: Int, devId: Int): Triple<Int, ByteArray, Int>{
        val index = if(devId in Constants.CURSED_IDS) 1 else 0
        var requestID = 1
        val dataValue: ByteArray
        if(splitAddressPulsar(newAddress.toString()).contentEquals(address)||
            newAddress == 0){
            return Triple(5, ByteArray(0), newAddress)
        }
        if(index==0){
            dataValue = toBytes(newAddress, 8)
        }else{
            tryAttempts(injectCRC(address +
                    Constants.WRITE_ADDRESS_REQUEST[2] +
                    toBytes(requestID, 2)))
            requestID += 1
            dataValue = cursedAddressToBytes(newAddress.toString())
        }
        val result = tryAttempts(injectCRC(address +
                Constants.WRITE_ADDRESS_REQUEST[index] + dataValue +
                toBytes(requestID, 2)))
        return Triple(result.first, result.second, newAddress)
    }

    private suspend fun getDeviceID(address: ByteArray): Int{
        var devID = -1
        for(i in 0 until Constants.GET_DEVICE_ID_REQUEST.size){
            val res = tryAttempts(injectCRC(address + Constants.GET_DEVICE_ID_REQUEST[i]))
            if(res.first == 1){
                if(handlePulsarErrorCode(res.second)!=0){
                    continue
                }
                devID = if(i == 0){
                    newType(res.second)
                }else{
                    oldType(res.second)
                }
                break
            }else if(res.first == 3){
                break
            }
        }
        return devID
    }

    private suspend fun getValue(address: ByteArray, devId: Int): Double?{
        var value: Double? = null
        val res = tryAttempts(injectCRC(address +
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
        val res = tryAttempts(injectCRC(address+
                Constants.GET_ADDRESS_REQUEST[index] +
                toBytes(1, 2)))
        var value = -1
        if(res.first==1){
            if(index==0){
                value = newAddr(res.second)
            }else{
                val recData = ByteArray(8)
                for(i in 0..7){
                    recData[i] = res.second[i+6]
                }
                value = cursedAddress(recData)
            }
        }
        return value
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

    private fun handlePulsarErrorCode(nRecData: ByteArray): Int{
        if(nRecData.size<=7) return -1
        if(nRecData[4] == 0.toByte()){
            return nRecData[6].toInt()
        }else if(nRecData[6] == 0xff.toByte() && nRecData[7] == 0xff.toByte()){
            return 9
        }
        return 0
    }

    private fun ibmCRC16(data: ByteArray): ByteArray{
        var result: UShort = 0xFFFFu
        for(i in data.indices){
            val dataI = data[i].toUByte().toUShort()
            result = result xor dataI
            for(a in 0 until 8){
                result = if(result%2u!=0u){
                    (result.toInt() shr 1).toUShort() xor 0xA001u
                }else{
                    (result.toInt() shr 1).toUShort()
                }
            }
        }
        val dataResult = ByteArray(2)
        dataResult[0] = result.toString(16).padStart(4, '0').slice(2..3).toUByte(16).toByte()
        dataResult[1] = result.toString(16).padStart(4, '0').slice(0..1).toUByte(16).toByte()
        return dataResult
    }

    private fun injectCRC(data: ByteArray): ByteArray{
        return data + ibmCRC16(data)
    }

    private fun checkCRC(nData: ByteArray): Boolean{
        return ibmCRC16(nData
            .toList()
            .subList(0, nData.size-2)
            .toByteArray())
            .contentEquals(nData
                .toList()
                .subList(nData.size-2, nData.size)
                .toByteArray())
    }

    private fun oldAddr(recData: ByteArray): Int{
        val hex = recData[4].toUByte().toString(16)+
                recData[5].toUByte().toString(16).let {
                    if(it.length==1){
                        "0$it"
                    }else{
                        it
                    }
                }+
                recData[6].toUByte().toString(16).let {
                    if(it.length==1){
                        "0$it"
                    }else{
                        it
                    }
                }+
                recData[7].toUByte().toString(16).let {
                    if(it.length==1){
                        "0$it"
                    }else{
                        it
                    }
                }
        return hex.toInt()
    }

    private fun newAddr(recData: ByteArray): Int{
        val range = 6 until recData.size - 8
        var result = 0
        var step = 0
        for(i in range){
            for(ch in recData[i].toUByte().toString(16).let {
                println(it)
                if(it.length==1){
                    "0$it".reversed()
                }else{
                    it.reversed()
                }
            }){
                result += ch.digitToInt(16)*16.0.pow(step.toDouble()).toInt()
                step+=1
            }
        }
        return result
    }

    private fun oldType(recData: ByteArray): Int{
        return devidFromBytes(recData.asList().subList(6, recData.size-2).toByteArray())
    }

    private fun newType(recData: ByteArray): Int{
        return devidFromBytes(recData.asList().subList(6, recData.size-8).toByteArray())
    }

    private fun devidFromBytes(nData: ByteArray): Int{
        var result = 0
        var step = 0
        for(i in nData){
            for(ch in i.toUByte().toString(16).let {
                println(it)
                if(it.length==1){
                    "0$it".reversed()
                }else{
                    it.reversed()
                }
            }){
                result += ch.digitToInt(16)*16.0.pow(step.toDouble()).toInt()
                step+=1
            }
        }
        return result
    }

    private fun isValidAddress(nAddress: String): Boolean{
        return try {nAddress.toInt()}
        catch (_: NumberFormatException){-1} in 0..99999999
    }

    private fun cursedAddress(input: ByteArray): Int{
        var result = ""
        for(i in input){
            result = i.toString(16).let {
                if(it.length==1){
                    "0$it"
                }else {
                    it
                }
            } + result
        }
        println(result)
        return try {
            result.toInt()
        }catch (_: NumberFormatException){ -1 }
    }

    private fun cursedAddressToBytes(input: String): ByteArray{
        return addressToBytes(input.toInt(16))
    }

    private fun addressToBytes(input: Int): ByteArray{
        val s = input.toString(16)
            .padStart(16, '0')
        val result = ByteArray(8)
        var i = s.length - 1
        var ii = 0
        while(i > 0){
            result[ii] = (s[i-1].toString() + s[i].toString()).toUByte(16).toByte()
            i-=2
            ii+=1
        }
        return result
    }

    private fun splitAddressPulsar(address: String): ByteArray{
        val s = address.padStart(8, '0')
        val b = ByteArray(4)
        for(i in 0..3){
            b[i] = (s[i*2].toString() + s[i*2+1].toString()).toUByte(16).toByte()
        }
        return b
    }


    @Suppress("unused")
    object Constants {

        const val UUID = "00001101-0000-1000-8000-00805F9B34FB"

        val RESULT_TYPE = arrayOf("INVALID SERIAL PORT",
            "CRC PASSED",
            "CRC FAILED",
            "TIMEOUT",
            "INVALID INPUT",
            "POINTLESS",
            "NOT SUPPORTED")

        val TVH_DEVICE = setOf(297 to "Пульсар модуль счетчика воды v6",
            319 to "Пульсар модуль счетчика воды v1.1",
            338 to "Пульсар модуль счетчика воды v1.9",
            385 to "Пульсар модуль счетчика воды Mini v1",
            408 to "Пульсар модуль счетчика воды v1.20",
            98  to "Пульсар водосчётчик RS485")

        val CURSED_IDS = arrayOf(297, 98)

        val PULSAR_ERROR = arrayOf("Успех",
            "Отсутствует запрашиваемый код функции",
            "Ошибка в битовой маске запроса",
            "Ошибочная длинна запроса",
            "Отсутствует параметр",
            "Запись заблокирована, требуется авторизация",
            "Записываемое значение (параметр) находится вне заданного диапазона",
            "Отсутствует запрашиваемый тип архива",
            "Превышение максимального количества архивных значений за один пакет")

        val ADDRESS_REQUEST = arrayOf(
            byteArrayOf(0xf0.toByte(), 0x0f, 0x0f, 0xf0.toByte(), 0x00,
                0x00, 0x00, 0x00, 0x00),
            byteArrayOf(0x00, 0x00, 0x00, 0x00,
                0x0a, 0x0c, 0x01, 0x00, 0x01, 0x00),
            byteArrayOf(0x0a, 0x0c, 0x00, 0x00, 0x01, 0x00),
            byteArrayOf(0x03, 0x02, 0x46, 0x00, 0x01)
        )

        val PASSWORD_REQUEST = arrayOf(
            byteArrayOf(0x0b, 0x14, 0x00, 0xe0.toByte()),
            byteArrayOf(0x0b, 0x14, 0x99.toByte(), 0x00)
        )

        val WRITE_VALUE_REQUEST = byteArrayOf(0x03, 0x12,
            0x01, 0x00, 0x00, 0x00)

        val WRITE_ADDRESS_REQUEST = arrayOf(
            byteArrayOf(0x0b, 0x14, 0x01, 0x00),
            byteArrayOf(0x0b, 0x14, 0x02, 0x00),
            byteArrayOf(0x0b, 0x14, 0xf2.toByte(),
                0xe7.toByte(), 0xd2.toByte(), 0x0a,
                0x1f, 0xeb.toByte(), 0x8c.toByte(),
                0xa9.toByte(), 0x54, 0xab.toByte())
        )

        val GET_DEVICE_ID_REQUEST = arrayOf(
            byteArrayOf(0x0a, 0x0c, 0x00, 0x00, 0x01, 0x00),
            byteArrayOf(0x03, 0x02, 0x46, 0x00, 0x01)
        )

        val GET_VALUE_REQUEST = byteArrayOf(0x01, 0x0e, 0x01, 0x00, 0x00, 0x00)

        val GET_ADDRESS_REQUEST = arrayOf(
            byteArrayOf(0x0a, 0x0c, 0x01, 0x00),
            byteArrayOf(0x0a, 0x0c, 0x02, 0x00)
        )

        val GET_VERSION_REQUEST = arrayOf(
            byteArrayOf(0x0a, 0x0c, 0x02, 0x00),
            byteArrayOf(0x0a, 0x0c, 0x05, 0x00)
        )

        val PULSAR_PASSWORDS = arrayOf(31285, 3791, 48053, 45182)
    }

}