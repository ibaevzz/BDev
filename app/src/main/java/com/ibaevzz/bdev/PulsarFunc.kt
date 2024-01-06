package com.ibaevzz.bdev

import kotlin.math.pow

object PulsarFunc {
    fun handlePulsarErrorCode(nRecData: ByteArray): Int{
        if(nRecData.size<=7) return -1
        if(nRecData[4] == 0.toByte()){
            return nRecData[6].toInt()
        }else if(nRecData[6] == 0xff.toByte() && nRecData[7] == 0xff.toByte()){
            return 9
        }
        return 0
    }

    fun ibmCRC16(data: ByteArray): ByteArray{
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
        dataResult[0] = result.toString(16).slice(2..3).toUByte(16).toByte()
        dataResult[1] = result.toString(16).slice(0..1).toUByte(16).toByte()
        return dataResult
    }

    fun injectCRC(data: ByteArray): ByteArray{
        return data + ibmCRC16(data)
    }

    fun checkCRC(nData: ByteArray): Boolean{
        return ibmCRC16(nData
            .toList()
            .subList(0, nData.size-2)
            .toByteArray())
            .contentEquals(nData
                .toList()
                .subList(nData.size-2, nData.size)
                .toByteArray())
    }

    fun oldAddr(recData: ByteArray): Int{
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

    fun newAddr(recData: ByteArray): Int{
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

    fun oldType(recData: ByteArray): Int{
        return devidFromBytes(recData.asList().subList(6, recData.size-2).toByteArray())
    }

    fun newType(recData: ByteArray): Int{
        return devidFromBytes(recData.asList().subList(6, recData.size-8).toByteArray())
    }

    fun devidFromBytes(nData: ByteArray): Int{
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

    fun isValidAddress(nAddress: String): Boolean{
        return try {nAddress.toInt()}
        catch (_: NumberFormatException){-1} in 0..99999999
    }

    fun cursedAddress(input: ByteArray): Int{
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

    fun cursedAddressToBytes(input: String): ByteArray{
        return addressToBytes(input.toInt(16))
    }

    fun addressToBytes(input: Int): ByteArray{
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

    fun splitAddressPulsar(address: String): ByteArray{
        val s = address.padStart(8, '0')
        val b = ByteArray(4)
        for(i in 0..3){
            b[i] = (s[i*2].toString() + s[i*2+1].toString()).toUByte(16).toByte()
        }
        return b
    }

}