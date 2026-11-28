package com.terra.browser

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.kyant.backdrop.components.enableLiquidEdgeToEdge
import com.terra.browser.state.TerraBrowserState
import com.terra.browser.ui.TerraBrowserApp
import com.terra.browser.webview.TerraWebViewPool

class MainActivity : ComponentActivity() {
    private lateinit var browserState: TerraBrowserState
    private lateinit var webViewPool: TerraWebViewPool

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableLiquidEdgeToEdge()

        val prefs = getSharedPreferences("terra_browser_prefs", Context.MODE_PRIVATE)
        browserState = TerraBrowserState(prefs)
        webViewPool = TerraWebViewPool(applicationContext, browserState)

        setContent {
            TerraBrowserApp(
                state = browserState,
                pool = webViewPool,
            )
        }
    }

    override fun onPause() {
        super.onPause()
        if (::webViewPool.isInitialized) {
            webViewPool.saveAllActiveStatesAndSnapshots()
        }
        if (::browserState.isInitialized) {
            browserState.persistNow()
        }
    }

    override fun onStop() {
        super.onStop()
        if (::browserState.isInitialized) {
            browserState.persistNow()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::webViewPool.isInitialized) {
            webViewPool.destroyAll()
        }
    }
}
