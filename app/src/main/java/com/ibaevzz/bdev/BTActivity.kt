package com.ibaevzz.bdev

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ibaevzz.bdev.databinding.ActivityBtactivityBinding
import com.ibaevzz.bdev.databinding.DeviceBinding
import java.text.SimpleDateFormat
import java.util.Calendar

@Suppress("DEPRECATION")
class BTActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBtactivityBinding
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private val devSet: MutableSet<BluetoothDevice> = mutableSetOf()
    private val devList: MutableList<BluetoothDevice> = mutableListOf()

    private val register = registerForActivityResult(ActivityResultContracts.StartActivityForResult()){
        if(it.resultCode == RESULT_OK){
            makeRec()
        }
    }

    @SuppressLint("SimpleDateFormat")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBtactivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val currentDate = Calendar.getInstance().time
        val lastDate = SimpleDateFormat("dd:MM:yyyy").parse("01:06:2024")!!
        val date = SimpleDateFormat("dd:MM:yyyy").parse("01:05:2024")

        if(currentDate.after(lastDate)){
            finish()
        }else if(currentDate.after(date)){
            val days = ((lastDate.time - currentDate.time)/86400000).toString()
            Toast.makeText(this, "До завершения работы программы осталось дней: $days", Toast.LENGTH_SHORT).show()
        }

        binding.enabled.setOnClickListener{
            startApp()
        }
        startApp()
    }

    private fun startApp(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED ||
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                    != PackageManager.PERMISSION_GRANTED))  {
            ActivityCompat.requestPermissions(this, arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN), 1)
        }else if(Build.VERSION.SDK_INT < Build.VERSION_CODES.S &&
            (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH)
                    != PackageManager.PERMISSION_GRANTED ||
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN)
                    != PackageManager.PERMISSION_GRANTED)){
            ActivityCompat.requestPermissions(this, arrayOf(
                Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN), 1)
        }
        else if(ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this, arrayOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION), 2)
        }
        else {
            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            if (!bluetoothAdapter.isEnabled) {
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                binding.enabled.setOnClickListener {
                    register.launch(enableBtIntent)
                }
                register.launch(enableBtIntent)
            } else {
                makeRec()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun makeRec(){
        binding.enabled.visibility = View.GONE
        binding.devices.visibility = View.VISIBLE
        binding.devices.layoutManager = LinearLayoutManager(this)
        bluetoothAdapter.startDiscovery()
        bluetoothAdapter.bluetoothLeScanner.startScan(object: ScanCallback(){
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                val device: BluetoothDevice? = result?.device
                addDevice(device)
            }
        })
        registerReceiver(BLERec(), IntentFilter(BluetoothDevice.ACTION_FOUND))
    }

    @SuppressLint("MissingPermission")
    fun addDevice(device: BluetoothDevice?){
        if (device != null) {
            if(!devSet.contains(device)){
                var isAdd = true
                for(i in devSet){
                    if(i.address == device.address){
                        isAdd = false
                        break
                    }
                }
                if(isAdd) {
                    devSet.add(device)
                    devList.add(device)
                }
            }
            for(i in devSet){
                if(i.name == null || i.name == ""){
                    devSet.remove(i)
                    devList.remove(i)
                }
            }
            binding.devices.adapter = DevAdapter(devList)
        }
    }

    inner class BLERec: BroadcastReceiver(){
        override fun onReceive(context: Context, intent: Intent) {
            val action: String? = intent.action
            if(BluetoothDevice.ACTION_FOUND == action){
                val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                addDevice(device)
            }
        }
    }

    inner class DevAdapter(private val list: List<BluetoothDevice>): RecyclerView.Adapter<DevAdapter.VH>(){
        inner class VH(val binding: DeviceBinding): RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val holder = DeviceBinding.inflate(layoutInflater, parent, false)
            return VH(holder)
        }

        override fun getItemCount(): Int {
            return list.size
        }

        @SuppressLint("MissingPermission")
        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.binding.name.text = list.toList()[position].name
            holder.binding.root.setOnClickListener{
                val intent = Intent(this@BTActivity, MainActivity::class.java)
                intent.putExtra(MAC, list.toList()[position].address)
                intent.putExtra(IS_NETWORK_EXTRA, false)
                startActivity(intent)
            }
        }
    }
}