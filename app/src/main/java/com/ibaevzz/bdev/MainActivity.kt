package com.ibaevzz.bdev

import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ibaevzz.bdev.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

@Suppress("DEPRECATION")
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var pulsarService: PulsarService
    private var oldAddress = -1

    @SuppressLint("ResourceAsColor")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        pulsarService = PulsarService(this, intent.getStringExtra(MAC)?:"")

        val isNetwork = intent.getBooleanExtra(IS_NETWORK_EXTRA, false)

        binding.connect.setOnClickListener{
            makeButtonsClick(false)
            binding.connect.isEnabled = false
            lifecycleScope.launch {
                if(!isNetwork) {
                    if (pulsarService.connect()) {
                        withContext(Dispatchers.Main) {
                            binding.find.isEnabled = true
                            binding.close.isEnabled = true
                        }
                        Toast.makeText(this@MainActivity, "Успешно подключено", Toast.LENGTH_SHORT)
                            .show()
                    } else {
                        binding.connect.isEnabled = true
                        Toast.makeText(
                            this@MainActivity,
                            "Не удалось подключиться",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }else{
                    try {
                        pulsarService.connectWifi(intent.getStringExtra(IP_EXTRA)?:"", intent.getStringExtra(
                            PORT_EXTRA)?:"")
                        withContext(Dispatchers.Main) {
                            binding.find.isEnabled = true
                            binding.close.isEnabled = true
                            Toast.makeText(this@MainActivity, "Успешно подключено", Toast.LENGTH_SHORT)
                                .show()
                        }
                    }catch (e: Exception){
                        binding.connect.isEnabled = true
                        Toast.makeText(this@MainActivity, "Не удалось подключиться", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        binding.find.setOnClickListener{
            makeButtonsClick(false)
            lifecycleScope.launch {
                try {
                    val res = pulsarService.pulsarAddress()
                    withContext(Dispatchers.Main){
                        binding.address.text = res.first.toString()
                        binding.value.text = res.second.toString()
                        oldAddress = res.first
                        makeButtonsClick(true)
                    }
                }catch (e: Exception){
                    Toast.makeText(this@MainActivity, "Ошибка", Toast.LENGTH_SHORT).show()
                    binding.find.isEnabled = true
                    binding.close.isEnabled = true
                }
            }
        }

        binding.write.setOnClickListener{
            makeButtonsClick(false)
            lifecycleScope.launch {
                val add = binding.newAddress.text.toString()
                val value = binding.newValue.text.toString()
                if(add == "" || value == "" || value[0] == '.'){
                    Toast.makeText(this@MainActivity, "Ошибка", Toast.LENGTH_SHORT).show()
                    makeButtonsClick(true)
                    return@launch
                }
                if(pulsarService.pulsarWrite(oldAddress.toString(), value.toDouble(), add.toInt())){
                    Toast.makeText(this@MainActivity, "Успешная запись", Toast.LENGTH_SHORT).show()
                    oldAddress = add.toInt()
                }else{
                    Toast.makeText(this@MainActivity, "Ошибка", Toast.LENGTH_SHORT).show()
                }
                makeButtonsClick(true)
            }
        }

        binding.check.setOnClickListener{
            makeButtonsClick(false)
            lifecycleScope.launch {
                val add = binding.newAddress.text.toString()
                val value = binding.newValue.text.toString()
                if(add == "" || value == "" || value[0] == '.'){
                    Toast.makeText(this@MainActivity, "Ошибка", Toast.LENGTH_SHORT).show()
                    makeButtonsClick(true)
                    return@launch
                }

                val trip = pulsarService.pulsarRead(oldAddress.toString())
                binding.address.text = trip.second.toString()
                binding.value.text = trip.third.toString()

                if(trip.second != null && trip.third != null){
                    if(trip.second == add.toInt() && trip.third == value.toDouble()){
                        Toast.makeText(this@MainActivity, "Успешно", Toast.LENGTH_SHORT).show()
                        lifecycleScope.launch(Dispatchers.IO) {
                            withContext(Dispatchers.Main){
                                binding.root.setBackgroundColor(resources.getColor(android.R.color.holo_green_light))
                            }
                            delay(1000)
                            withContext(Dispatchers.Main) {
                                binding.root.setBackgroundColor(resources.getColor(R.color.white))
                            }
                        }
                    }else{
                        Toast.makeText(this@MainActivity, "Неуспешная запись", Toast.LENGTH_SHORT).show()
                        lifecycleScope.launch(Dispatchers.IO) {
                            withContext(Dispatchers.Main){
                                binding.root.setBackgroundColor(resources.getColor(android.R.color.holo_red_light))
                            }
                            delay(1000)
                            withContext(Dispatchers.Main) {
                                binding.root.setBackgroundColor(resources.getColor(R.color.white))
                            }
                        }
                    }
                }else{
                    Toast.makeText(this@MainActivity, "Неуспешная запись", Toast.LENGTH_SHORT).show()
                    lifecycleScope.launch(Dispatchers.IO) {
                        withContext(Dispatchers.Main){
                            binding.root.setBackgroundColor(resources.getColor(android.R.color.holo_red_light))
                        }
                        delay(1000)
                        withContext(Dispatchers.Main) {
                            binding.root.setBackgroundColor(resources.getColor(R.color.white))
                        }
                    }
                }
                makeButtonsClick(true)
            }
        }

        binding.close.setOnClickListener{
            makeButtonsClick(false)
            lifecycleScope.launch {
                if(isNetwork){
                    pulsarService.closeWifiConnection()
                }else {
                    pulsarService.closeConnection()
                }
                pulsarService = PulsarService(this@MainActivity, intent.getStringExtra(MAC)?:"")
                binding.connect.isEnabled = true
                binding.value.text = ""
                binding.address.text = ""
            }
        }
    }

    private fun makeButtonsClick(isClick: Boolean){
        binding.find.isEnabled = isClick
        binding.write.isEnabled = isClick
        binding.check.isEnabled = isClick
        binding.close.isEnabled = isClick
    }

    override fun onDestroy() {
        super.onDestroy()
        if(intent.getBooleanExtra(IS_NETWORK_EXTRA, false)){
            lifecycleScope.launch {
                pulsarService.closeWifiConnection()
            }
        }else{
            pulsarService.closeConnection()
        }
    }
}