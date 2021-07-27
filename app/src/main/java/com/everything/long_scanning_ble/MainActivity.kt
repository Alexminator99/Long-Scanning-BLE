package com.everything.long_scanning_ble

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    override fun onResume() {
        super.onResume()

        startService(Intent(this, BleService::class.java))
    }

    override fun onPause() {
        super.onPause()

        BleService.startActionFoo(this, "low_scan")
    }
}