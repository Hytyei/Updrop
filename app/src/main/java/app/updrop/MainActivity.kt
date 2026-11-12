package app.updrop

import android.Manifest
import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.updrop.ui.AddSheet
import app.updrop.ui.C
import app.updrop.ui.DetailScreen
import app.updrop.ui.HomeScreen
import app.updrop.ui.MainViewModel
import app.updrop.ui.SettingsSheet
import app.updrop.ui.UpdropTheme
import app.updrop.work.Notifier

class UpdropApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifier.ensureChannel(this)
    }
}

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private var sharedText by mutableStateOf<String?>(null)
    private var showAdd by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {}.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        handle(intent)
        // re-read installed versions whenever the user comes back (e.g. after installing an update)
        lifecycle.addObserver(LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) vm.onResume(this) })

        setContent {
            UpdropTheme {
                val open by vm.open.collectAsState()
                var showSettings by androidx.compose.runtime.remember { mutableStateOf(false) }
                BackHandler(open != null) { vm.openApp(null) }
                Box(Modifier.fillMaxSize().background(C.Black)) {
                    AnimatedContent(
                        targetState = open,
                        transitionSpec = {
                            if (targetState != null) {
                                (slideInHorizontally(spring(dampingRatio = 0.85f, stiffness = 260f)) { it / 3 } + fadeIn(tween(250)) + scaleIn(initialScale = 0.96f))
                                    .togetherWith(fadeOut(tween(180)) + scaleOut(targetScale = 0.94f))
                            } else {
                                (fadeIn(tween(250)) + scaleIn(initialScale = 0.94f))
                                    .togetherWith(slideOutHorizontally(tween(220)) { it / 3 } + fadeOut(tween(180)))
                            }
                        },
                        label = "nav",
                    ) { url ->
                        if (url == null) HomeScreen(vm, onAdd = { showAdd = true }, onSettings = { showSettings = true })
                        else DetailScreen(vm, url, onBack = { vm.openApp(null) })
                    }
                    if (showAdd) AddSheet(vm, sharedText) { showAdd = false; sharedText = null }
                    if (showSettings) SettingsSheet(vm) { showSettings = false }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        intent ?: return
        intent.getStringExtra(EXTRA_PAGE)?.let { vm.openApp(it) }
        if (intent.action == Intent.ACTION_SEND) {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let { sharedText = it; showAdd = true }
        }
    }

    companion object { const val EXTRA_PAGE = "page" }
}
