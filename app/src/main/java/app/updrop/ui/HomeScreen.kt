package app.updrop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.ui.draw.drawBehind
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.updrop.data.Abi
import app.updrop.data.DeviceInfo
import app.updrop.data.Installed
import app.updrop.data.TrackedApp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: MainViewModel, onAdd: () -> Unit, onSettings: () -> Unit) {
    val apps by vm.apps.collectAsState()
    val installed by vm.installed.collectAsState()
    val checking by vm.checking.collectAsState()
    val burst by vm.burst.collectAsState()
    val busy = checking.isNotEmpty()
    val selfUpdate by vm.selfUpdate.collectAsState()
    val selfProgress by vm.selfProgress.collectAsState()
    val selfError by vm.selfError.collectAsState()
    // updates first, then new builds; otherwise keep the user's order (newest added first)
    val sorted = remember(apps, installed) {
        apps.sortedBy {
            when (statusOf(it, installed[it.pageUrl])) {
                Status.UPDATE -> 0; Status.NEW_BUILD -> 1; Status.ERROR -> 2; Status.CURRENT -> 3; Status.NOT_INSTALLED -> 4
            }
        }
    }

    val updates = apps.count { statusOf(it, installed[it.pageUrl]) == Status.UPDATE }
    val fresh = apps.count { statusOf(it, installed[it.pageUrl]) == Status.NEW_BUILD }

    val ctx = androidx.compose.ui.platform.LocalContext.current
    Box(Modifier.fillMaxSize()) {
        SparkField(intensity = if (busy) 1.6f else 1f)
        PullToRefreshBox(isRefreshing = false, onRefresh = vm::checkAll, modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Header(busy, burst, vm::checkAll, onSettings)
                }
                selfUpdate?.let { u ->
                    item(key = "self-update") {
                        SelfUpdateCard(u, selfProgress, selfError, Modifier.animateItem()) { vm.installSelf(ctx) }
                    }
                }
                item { DeviceCard() }
                if (apps.isNotEmpty()) item {
                    Summary(updates, fresh, apps.size, apps.maxOfOrNull { it.lastChecked } ?: 0)
                }
                if (apps.isEmpty()) item { Empty(onAdd) }
                itemsIndexed(sorted, key = { _, a -> a.pageUrl }) { i, app ->
                    AppCard(
                        app, installed[app.pageUrl], app.pageUrl in checking,
                        Modifier.animateItem().enter(i),
                    ) { vm.openApp(app.pageUrl) }
                }
            }
        }
        AddButton(onAdd, Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(22.dp))
    }
}

@Composable
private fun Header(busy: Boolean, burst: Int, onRefresh: () -> Unit, onSettings: () -> Unit) {
    val inf = rememberInfiniteTransition(label = "spin")
    val spin by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "r")
    Column(Modifier.statusBarsPadding().padding(top = 18.dp, bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                OrbitRing(46.dp, active = busy) {
                    Text("U", color = C.Glow, fontWeight = FontWeight.Black, fontSize = 20.sp)
                }
                Burst(burst, Modifier.size(96.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Updrop", color = C.Text, fontSize = 26.sp, fontWeight = FontWeight.Black)
                Text(if (busy) "Scanning Farsroid…" else "Farsroid build watcher", color = if (busy) C.Glow else C.Dim, fontSize = 13.sp)
            }
            IconButton(onClick = onRefresh, enabled = !busy) {
                Icon(Icons.Rounded.Refresh, "Check all", tint = if (busy) C.Glow else C.Text,
                    modifier = Modifier.graphicsLayer { rotationZ = if (busy) spin else 0f })
            }
            IconButton(onClick = onSettings) { Icon(Icons.Rounded.Tune, "Settings", tint = C.Text) }
        }
        ScanBar(busy, Modifier.fillMaxWidth().padding(top = 14.dp).height(2.dp))
    }
}

@Composable
private fun SelfUpdateCard(u: app.updrop.work.AppUpdate, progress: Float?, error: String?, modifier: Modifier, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(18.dp)
    val p by androidx.compose.animation.core.animateFloatAsState(progress ?: 0f, tween(300), label = "self")
    Column(
        modifier.fillMaxWidth().pressScale(src).glow(C.Lime, 0.12f, 1.1f).clip(shape)
            .background(Brush.horizontalGradient(listOf(C.Green.copy(alpha = 0.35f), C.Card)))
            .border(1.5.dp, C.Lime.copy(alpha = 0.7f), shape)
            .drawBehind { if (progress != null) drawRect(C.Glow.copy(alpha = 0.25f), size = size.copy(width = size.width * p)) }
            .clickable(src, indication = null, onClick = onClick)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PulseDot(C.Lime, size = 7.dp)
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                Text("Updrop ${u.version} is out", color = C.Text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(
                    when {
                        progress != null -> "Downloading… ${(progress * 100).toInt()}%"
                        error != null -> "$error · tap to retry"
                        else -> "You have ${app.updrop.BuildConfig.VERSION_NAME} · tap to update"
                    },
                    color = if (error != null && progress == null) C.Warn else C.Dim, fontSize = 12.sp,
                )
            }
            Icon(Icons.Rounded.SystemUpdate, null, tint = C.Lime)
        }
        if (u.notes.isNotBlank() && progress == null) Text(
            u.notes.lines().filter { it.isNotBlank() }.take(4).joinToString("\n"),
            color = C.Dim, fontSize = 12.sp, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun DeviceCard() {
    val abis = remember { DeviceInfo.supportedAbis }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(Brush.horizontalGradient(listOf(C.Green.copy(alpha = 0.16f), C.Card)))
            .border(1.dp, C.Green.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).glow(C.Glow, 0.25f), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Memory, null, tint = C.Glow, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("This phone runs ${Abi.pretty(abis.firstOrNull())}", color = C.Text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text("${DeviceInfo.model} · ${DeviceInfo.android}", color = C.Dim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (abis.size > 1) Text("Also runs: ${abis.drop(1).joinToString(", ")}", color = C.Faint, fontSize = 11.sp)
        }
    }
}

@Composable
private fun Summary(updates: Int, fresh: Int, total: Int, last: Long) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Text(
                when {
                    updates > 0 -> "$updates update${if (updates > 1) "s" else ""} ready"
                    fresh > 0 -> "$fresh new build${if (fresh > 1) "s" else ""}"
                    else -> "All caught up"
                },
                color = if (updates + fresh > 0) C.Lime else C.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold,
            )
            Text("$total app${if (total != 1) "s" else ""} watched · checked ${ago(last)}", color = C.Dim, fontSize = 12.sp)
        }
    }
}

@Composable
private fun AppCard(app: TrackedApp, inst: Installed?, checking: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val status = statusOf(app, inst)
    val hot = status == Status.UPDATE || status == Status.NEW_BUILD
    val src = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier.fillMaxWidth().pressScale(src).clip(shape)
            .background(if (hot) Brush.verticalGradient(listOf(C.Green.copy(alpha = 0.12f), C.Card)) else Brush.verticalGradient(listOf(C.Card, C.Card)))
            .border(1.dp, if (hot) C.Glow.copy(alpha = 0.45f) else C.Stroke, shape)
            .clickable(src, indication = null, onClick = onClick)
            .animateContentSize(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (hot) OrbitRing(60.dp, active = true) { AppIcon(app.iconUrl, 48.dp) }
            else AppIcon(app.iconUrl, 52.dp, Modifier.padding(4.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(app.name, color = C.Text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(inst?.versionName ?: "—", color = C.Dim, fontSize = 13.sp, maxLines = 1)
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = if (hot) C.Glow else C.Faint, modifier = Modifier.padding(horizontal = 4.dp).size(14.dp))
                    Text(app.latestVersion ?: "?", color = if (status == Status.UPDATE) C.Lime else C.Text, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusPill(status)
                    if (inst?.runningAbi != null) Chip(Abi.pretty(inst.runningAbi))
                }
            }
        }
        AnimatedVisibility(checking, enter = fadeIn(), exit = fadeOut()) {
            ScanBar(true, Modifier.fillMaxWidth().height(2.dp))
        }
        if (app.lastError != null) {
            Text("Last check failed: ${app.lastError}", color = C.Warn, fontSize = 11.sp, modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 10.dp))
        }
    }
}

@Composable
private fun Empty(onAdd: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        OrbitRing(120.dp, active = true) {
            Icon(Icons.Rounded.Add, null, tint = C.Glow, modifier = Modifier.size(44.dp).clickable(onClick = onAdd))
        }
        Spacer(Modifier.height(22.dp))
        Text("Nothing watched yet", color = C.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(
            "Add a Farsroid app page. Updrop checks it in the background and tells you when a new build lands.",
            color = C.Dim, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 30.dp, vertical = 6.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Text("Tip: in your browser, Share › Updrop works too.", color = C.Faint, fontSize = 12.sp)
    }
}

@Composable
private fun AddButton(onClick: () -> Unit, modifier: Modifier) {
    val src = remember { MutableInteractionSource() }
    Row(
        modifier.pressScale(src).glow(C.Glow, 0.3f, 1.6f).clip(RoundedCornerShape(50))
            .background(Brush.horizontalGradient(listOf(C.Green, Color(0xFF1A9A1A))))
            .clickable(src, indication = null, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Add, null, tint = Color.White)
        Spacer(Modifier.width(6.dp))
        Text("Add link", color = Color.White, fontWeight = FontWeight.Bold)
    }
}
