package com.ibaevzz.bdev

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.getSystemService
import com.ibaevzz.bdev.*
import com.ibaevzz.bdev.databinding.ActivityWifiBinding

class WifiActivity: AppCompatActivity() {

    companion object {
        private const val PERMISSION_REQUEST_CODE = 0
    }

    private lateinit var binding: ActivityWifiBinding
    private lateinit var wifiManager: WifiManager
    private lateinit var locationManager: LocationManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWifiBinding.inflate(layoutInflater)
        setContentView(binding.root)

        wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        locationManager = applicationContext.getSystemService(LOCATION_SERVICE) as LocationManager

        binding.connect.setOnClickListener{
            startActivity(Intent(WifiManager.ACTION_PICK_WIFI_NETWORK))
        }
        binding.update.setOnClickListener{
            if(!checkPermissions()){
                requestPermissions()
                return@setOnClickListener
            }
            if(!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)&&
                !locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)){
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                return@setOnClickListener
            }
            updateIpAndHost()
        }
        binding.contin.setOnClickListener{
            try {
                val intent = Intent(this, MainActivity::class.java)
                intent.putExtra(IP_EXTRA, binding.ip.text.toString().stringToIp().toString())
                intent.putExtra(PORT_EXTRA, binding.port.text.toString())
                intent.putExtra(IS_NETWORK_EXTRA, true)
                binding.ip.setText("")
                binding.port.setText("")
                startActivity(intent)
            }catch (_: Exception){
                Toast.makeText(this, "Невалидные значения", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if(requestCode == PERMISSION_REQUEST_CODE){
            if(checkPermissions()){
                updateIpAndHost()
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun updateIpAndHost(){
        val name = wifiManager.connectionInfo.ssid
        if(name!=null && name.contains(PMSK_PNR)){
            val ip = wifiManager.dhcpInfo.gateway
            if(ip!=0){
                binding.ip.setText(ip.ipToString())
                binding.port.setText(PORT.toString())
            }
        }else{
            Toast.makeText(this, "Подключена неподходящая сеть", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkPermissions() = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun requestPermissions(){
        requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION), PERMISSION_REQUEST_CODE
        )
    }
}