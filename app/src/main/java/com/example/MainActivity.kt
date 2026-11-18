package com.example

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.domain.model.ThemeMode
import com.example.domain.model.UserPreferences
import com.example.ui.navigation.NovaSpaceNavHost
import com.example.ui.theme.NovaSpaceTheme
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    private val appContainer: AppContainer
        get() = (application as NovaSpaceApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val prefs by appContainer.settingsRepository.userPreferences
                .collectAsStateWithLifecycle(initialValue = UserPreferences())

            NovaSpaceTheme(themeMode = prefs.themeMode) {
                NovaSpaceNavHost(container = appContainer)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            lifecycleScope.launch {
                appContainer.secretZoneRepository.onAppMovedToBackground()
            }
        }
    }
}
