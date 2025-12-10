package com.example.krishimitra

import android.os.Bundle
import android.widget.ImageView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import coil.load

class fullscreen_image : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_fullscreen_image)
        window.statusBarColor = ContextCompat.getColor(this, R.color.theme)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val fullscreenimage = findViewById<ImageView>(R.id.ivfullscreen)
        // Get the image resource ID from the Intent
        val imageResId = intent.getStringExtra("imageResId")
        // Set the image to the ImageView
        fullscreenimage.load(imageResId)

        // Close the activity when the image is clicked
        fullscreenimage.setOnClickListener {
            finish()
        }

    }
}