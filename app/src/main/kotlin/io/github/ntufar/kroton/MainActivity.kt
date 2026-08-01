package io.github.ntufar.kroton

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.ntufar.kroton.designsystem.KrotonTheme
import io.github.ntufar.kroton.navigation.KrotonApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KrotonTheme {
                KrotonApp()
            }
        }
    }
}
