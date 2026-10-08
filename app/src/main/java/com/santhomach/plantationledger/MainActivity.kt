package com.santhomach.plantationledger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.santhomach.plantationledger.ui.calculator.CalculatorViewModel
import com.santhomach.plantationledger.ui.calculator.FloatingCalculator
import com.santhomach.plantationledger.ui.navigation.PlantationLedgerNavigation
import com.santhomach.plantationledger.ui.theme.PlantationLedgerTheme
import com.santhomach.plantationledger.ui.viewmodel.SettingsViewModel
import com.santhomach.plantationledger.ui.viewmodel.ThemeMode
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val settingsViewModel: SettingsViewModel by viewModels()

    /** Activity-scoped: the calculator keeps its history while the app runs, and resets when it is closed. */
    private val calculatorViewModel: CalculatorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by settingsViewModel.themeMode.collectAsState()
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> systemDark
            }
            PlantationLedgerTheme(darkTheme = darkTheme, dynamicColor = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        PlantationLedgerNavigation()
                        // Floats above every main screen; dialogs open on top of it.
                        FloatingCalculator(viewModel = calculatorViewModel)
                    }
                }
            }
        }
    }
}
