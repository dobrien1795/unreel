package com.unreel.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.unreel.app.ui.DashboardScreen
import com.unreel.app.ui.MainViewModel
import com.unreel.app.ui.UnreelTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            UnreelTheme {
                val vm: MainViewModel = viewModel()
                DashboardScreen(viewModel = vm)
            }
        }
    }
}
