package com.kingzcheung.ximecgen

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.kingzcheung.ximecgen.ui.XimecgenApp
import com.kingzcheung.ximecgen.ui.theme.XimecgenTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            XimecgenTheme {
                XimecgenApp()
            }
        }
    }
}
