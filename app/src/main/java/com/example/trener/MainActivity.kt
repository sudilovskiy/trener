package com.example.trener

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

const val logTag = "TrenerApp"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TrenerApp() }
    }
}
