package com.ibaevzz.bdev

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ibaevzz.bdev.databinding.ActivityBtactivityBinding
import com.ibaevzz.bdev.databinding.DeviceBinding

class BTActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBtactivityBinding
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private val devList: MutableList<BluetoothDevice> = mutableListOf()
    private val devSet: MutableSet<String> = mutableSetOf()

    private val register = registerForActivityResult(ActivityResultContracts.StartActivityForResult()){
        if(it.resultCode == RESULT_OK){
            makeRec()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBtactivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.enabled.setOnClickListener{
            startApp()
        }
        startApp()
    }

    private fun startApp(){
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
            != PackageManager.PERMISSION_GRANTED)  {
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ActivityCompat.requestPermissions(this, arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN), 1)
            }
        }else if(ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
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

    private fun makeRec(){
        binding.enabled.visibility = View.GONE
        binding.devices.visibility = View.VISIBLE
        binding.devices.layoutManager = LinearLayoutManager(this)
        bluetoothAdapter.startDiscovery()
        registerReceiver(BLERec(), IntentFilter(BluetoothDevice.ACTION_FOUND))
    }

    inner class BLERec: BroadcastReceiver(){
        override fun onReceive(context: Context, intent: Intent) {
            val action: String? = intent.action
            if(BluetoothDevice.ACTION_FOUND == action){
                val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                if (device != null) {
                    if(!devSet.contains(device.address)){
                        devSet.add(device.address)
                        devList.add(device)
                    }
                }
                binding.devices.adapter = DevAdapter(devList)
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

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.binding.name.text = list[position].name
            holder.binding.root.setOnClickListener{
                val intent = Intent(this@BTActivity, MainActivity::class.java)
                intent.putExtra("MAC", list[position].address)
                startActivity(intent)
            }
        }
    }
}