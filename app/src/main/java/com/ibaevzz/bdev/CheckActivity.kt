package com.ibaevzz.bdev

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.ibaevzz.bdev.databinding.ActivityCheckBinding

class CheckActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCheckBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCheckBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.bluetooth.setOnClickListener{
            startActivity(Intent(this, BTActivity::class.java))
        }
        binding.wifi.setOnClickListener{
            startActivity(Intent(this, WifiActivity::class.java))
        }
    }
}