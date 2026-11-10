package com.topentris

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.topentris.data.SettingsRepository
import com.topentris.ui.Ink
import com.topentris.ui.TopentrisApp
import com.topentris.ui.TopentrisTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = SettingsRepository(applicationContext)
        setContent {
            TopentrisTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Ink) {
                    TopentrisApp(repository)
                }
            }
        }
    }
}
