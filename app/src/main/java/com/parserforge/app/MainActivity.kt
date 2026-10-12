package com.parserforge.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.parserforge.app.ui.ParserForgeNav
import com.parserforge.app.ui.theme.EightSpineTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.BLACK),
            navigationBarStyle = SystemBarStyle.dark(Color.BLACK),
        )
        val repository = (application as ParserForgeApp).repository
        setContent {
            EightSpineTheme {
                ParserForgeNav(repository)
            }
        }
    }
}
