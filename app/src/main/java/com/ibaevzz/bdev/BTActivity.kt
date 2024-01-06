package com.ibaevzz.bdev

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ibaevzz.bdev.databinding.ActivityBtactivityBinding
import com.ibaevzz.bdev.databinding.DeviceBinding

class BTActivity : AppCompatActivity() {

    private lateinit var bluetoothAdapter: BluetoothAdapter

    private val register = registerForActivityResult(ActivityResultContracts.StartActivityForResult()){
        if(it.resultCode == RESULT_OK){
            binding.enabled.visibility = View.GONE
            binding.devices.visibility = View.VISIBLE
            binding.devices.layoutManager = LinearLayoutManager(this)
            binding.devices.adapter = DevAdapter(bluetoothAdapter.bondedDevices.toList())
        }
    }
    private lateinit var binding: ActivityBtactivityBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBtactivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED) {
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 1
                )
            }
        }

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if(!bluetoothAdapter.isEnabled){
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            binding.enabled.setOnClickListener{
                register.launch(enableBtIntent)
            }
            register.launch(enableBtIntent)
        }else{
            binding.enabled.visibility = View.GONE
            binding.devices.visibility = View.VISIBLE
            binding.devices.layoutManager = LinearLayoutManager(this)
            binding.devices.adapter = DevAdapter(bluetoothAdapter.bondedDevices.toList())
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