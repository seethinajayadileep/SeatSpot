package com.seatspot.library

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.seatspot.library.data.SeatSpotApi
import com.seatspot.library.ui.SeatSpotApp
import com.seatspot.library.ui.SeatSpotViewModel
import com.seatspot.library.ui.SeatSpotViewModelFactory
import com.seatspot.library.ui.theme.SeatSpotTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val api = SeatSpotApi(applicationContext)
        val model: SeatSpotViewModel by viewModels { SeatSpotViewModelFactory(api) }
        setContent {
            SeatSpotTheme {
                SeatSpotApp(model)
            }
        }
    }
}
