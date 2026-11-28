package com.terra.browser

import android.content.Context
import android.content.Intent
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
        handleIncomingIntent(intent)

        setContent {
            TerraBrowserApp(
                state = browserState,
                pool = webViewPool,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (::browserState.isInitialized) {
            handleIncomingIntent(intent)
        }
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val url = intent.dataString?.trim() ?: return
        if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
            return
        }
        val targetGroupId = browserState.tabGroups.firstOrNull { it.isDefault }?.id ?: browserState.activeGroup.id
        browserState.isOverviewOpen = false
        browserState.isAddressEditing = false
        browserState.selectGroup(targetGroupId)
        val groupTabs = browserState.tabsInGroup(targetGroupId)
        if (groupTabs.size == 1 && groupTabs.first().isStartPage) {
            browserState.navigateActiveTab(url)
        } else {
            browserState.createTab(groupId = targetGroupId, url = url, select = true)
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
