package com.example.krishimitra

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import com.example.krishimitra.databinding.ActivityHomeSctivityBinding

class HomeActivity : AppCompatActivity() {
    private lateinit var binding: ActivityHomeSctivityBinding
    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        window.statusBarColor = ContextCompat.getColor(this, R.color.theme)
        binding = ActivityHomeSctivityBinding.inflate(layoutInflater)
        setContentView(binding.root)


        val botview = binding.bottomNavigationView


        val navController = findNavController(R.id.fragmentContainerView)
        botview.setupWithNavController(navController)
    }
}