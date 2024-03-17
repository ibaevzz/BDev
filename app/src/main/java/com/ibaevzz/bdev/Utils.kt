package com.ibaevzz.bdev

const val PMSK_PNR = "PMSK_PNR"
const val PORT = 4001
const val IP_EXTRA = "IP"
const val PORT_EXTRA = "PORT"
const val IS_NETWORK_EXTRA = "IS_NETWORK"
const val MAC = "MAC"

fun Int.ipToString(): String{
    var result = ""
    for(i in 0 .. 24 step 8){
        result += (this ushr i).toUByte().toString()
        if(i != 24){
            result+="."
        }
    }
    return result
}

fun String.stringToIp(): Int{
    var i = 24
    var result = 0
    split(".").reversed().forEach {
        result += it.toUByte().toInt() shl i
        i-=8
    }
    return result
}