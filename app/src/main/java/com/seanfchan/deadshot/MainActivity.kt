package com.seanfchan.deadshot

import android.content.Intent
import android.os.Bundle
import android.view.View

class MainActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val mapButton: View = findViewById(R.id.map_button)
        val aimerButton: View = findViewById(R.id.aimer_button)

        mapButton.setOnClickListener {
            val intent = Intent(this, MapActivity::class.java)
            startActivity(intent)
        }

        aimerButton.setOnClickListener {
            val intent = Intent(this, TrackerActivity::class.java)
            startActivity(intent)
        }
    }
}
