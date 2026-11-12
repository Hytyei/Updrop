package app.updrop.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.updrop.data.Abi
import app.updrop.data.DeviceInfo
import app.updrop.data.Installed
import app.updrop.data.Recommender
import app.updrop.data.Signer
import kotlinx.coroutines.launch
import app.updrop.data.TrackedApp
import app.updrop.data.Variant
import app.updrop.data.Versions
import app.updrop.work.DlState
import app.updrop.work.Downloads

@Composable
fun DetailScreen(vm: MainViewModel, url: String, onBack: () -> Unit) {
    val apps by vm.apps.collectAsState()
    val installedMap by vm.installed.collectAsState()
    val checking by vm.checking.collectAsState()
    val app = apps.firstOrNull { it.pageUrl == url } ?: return
    val inst = installedMap[url]
    val rec = remember(app, inst) { Recommender.pick(app, inst) }
    val ctx = LocalContext.current
    var confirmRemove by remember { mutableStateOf(false) }
    val busy = url in checking
    val status = statusOf(app, inst)

    Box(Modifier.fillMaxSize()) {
        SparkField(count = 26, intensity = 0.7f)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().padding(bottom = 40.dp)) {
            Row(Modifier.padding(horizontal = 6.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = C.Text) }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { vm.check(url) }, enabled = !busy) { Icon(Icons.Rounded.Refresh, "Check now", tint = if (busy) C.Glow else C.Text) }
                IconButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }) { Icon(Icons.AutoMirrored.Rounded.OpenInNew, "Open page", tint = C.Text) }
                IconButton(onClick = { confirmRemove = true }) { Icon(Icons.Rounded.DeleteOutline, "Remove", tint = C.Dim) }
            }
            ScanBar(busy, Modifier.fillMaxWidth().height(2.dp))

            // hero
            Column(Modifier.fillMaxWidth().padding(top = 18.dp).enter(0), horizontalAlignment = Alignment.CenterHorizontally) {
                OrbitRing(132.dp, active = status == Status.UPDATE || status == Status.NEW_BUILD || busy) { AppIcon(app.iconUrl, 96.dp) }
                Spacer(Modifier.height(14.dp))
                Text(app.name, color = C.Text, fontSize = 24.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp))
                Spacer(Modifier.height(6.dp))
                StatusPill(status)
                Text(
                    "Checked ${ago(app.lastChecked)}" + (app.updatedAt?.take(10)?.let { " · site updated $it" } ?: ""),
                    color = C.Faint, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp),
                )
            }

            // versions
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 18.dp).enter(1), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(
                    "INSTALLED", inst?.versionName ?: "Not installed",
                    inst?.let { installedCpu(it) } ?: "Not found on this phone", Modifier.weight(1f), accent = false,
                )
                StatTile(
                    "ON FARSROID", app.latestVersion ?: "?",
                    app.variants.mapNotNull { it.abi }.distinct().joinToString(" / ") { Abi.pretty(it) }.ifBlank { "Universal" },
                    Modifier.weight(1f), accent = Versions.compare(app.latestVersion, inst?.versionName) > 0 || inst == null,
                )
            }

            if (rec != null) {
                SectionTitle("Recommended for you", Modifier.enter(2))
                VariantCard(app, rec.variant, recommended = true, reasons = rec.reasons, vm = vm, modifier = Modifier.enter(2))
            } else if (app.variants.isNotEmpty()) {
                Text(
                    "None of these files is built for this phone's CPU (${DeviceInfo.supportedAbis.joinToString()}).",
                    color = C.Warn, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 18.dp),
                )
            }

            val others = app.variants.filter { it.url != rec?.variant?.url }
            if (others.isNotEmpty()) {
                SectionTitle("Other files", Modifier.enter(3))
                others.forEachIndexed { i, v -> VariantCard(app, v, recommended = false, reasons = emptyList(), vm = vm, modifier = Modifier.enter(3 + i)) }
            }

            if (app.changelog.isNotEmpty()) {
                SectionTitle("What's new in ${app.latestVersion ?: "this build"}", Modifier.enter(5))
                Column(
                    Modifier.padding(horizontal = 18.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.Card)
                        .border(1.dp, C.Stroke, RoundedCornerShape(18.dp)).padding(14.dp).enter(5),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    app.changelog.forEach {
                        Text(it, color = C.Text, fontSize = 13.sp, modifier = Modifier.fillMaxWidth(),
                            style = TextStyle(textDirection = TextDirection.Content))
                    }
                }
            }
            app.lastError?.let {
                Text("Last check failed: $it", color = C.Warn, fontSize = 12.sp, modifier = Modifier.padding(18.dp))
            }
        }
    }

    if (confirmRemove) AlertDialog(
        onDismissRequest = { confirmRemove = false },
        containerColor = C.CardHi,
        title = { Text("Stop watching ${app.name}?", color = C.Text) },
        text = { Text("Updrop will forget this page. Downloaded files are cleaned up after a week.", color = C.Dim) },
        confirmButton = { TextButton(onClick = { confirmRemove = false; vm.remove(url); onBack() }) { Text("Remove", color = C.Bad) } },
        dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Keep", color = C.Glow) } },
    )
}

private fun installedCpu(i: Installed): String = when {
    i.runningAbi == null && i.apkAbis.isEmpty() -> "No native code (any CPU)"
    i.isUniversal -> "Runs ${Abi.pretty(i.runningAbi)} · APK has ${i.apkAbis.size} CPUs"
    else -> "Runs ${Abi.pretty(i.runningAbi ?: i.apkAbis.firstOrNull())}"
}

@Composable
private fun SectionTitle(t: String, modifier: Modifier = Modifier) {
    Text(t, color = C.Dim, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 8.dp))
}

@Composable
private fun StatTile(label: String, value: String, sub: String, modifier: Modifier, accent: Boolean) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier.clip(shape)
            .background(if (accent) Brush.verticalGradient(listOf(C.Green.copy(alpha = 0.2f), C.Card)) else Brush.verticalGradient(listOf(C.Card, C.Card)))
            .border(1.dp, if (accent) C.Glow.copy(alpha = 0.5f) else C.Stroke, shape).padding(14.dp),
    ) {
        Text(label, color = if (accent) C.Glow else C.Dim, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
        Spacer(Modifier.height(4.dp))
        Text(value, color = if (accent) C.Lime else C.Text, fontSize = 20.sp, fontWeight = FontWeight.Black, maxLines = 1)
        Text(sub, color = C.Dim, fontSize = 11.sp)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VariantCard(app: TrackedApp, v: Variant, recommended: Boolean, reasons: List<String>, vm: MainViewModel, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var dlId by remember(v.url) { mutableStateOf(Downloads.idFor(ctx, v.url)) }
    var state by remember(v.url) { mutableStateOf<DlState?>(null) }
    LaunchedEffect(dlId) {
        val id = dlId ?: return@LaunchedEffect
        Downloads.watch(ctx, id).collect { state = it; if (it == null) dlId = null }
    }
    val inst = vm.installed.collectAsState().value[app.pageUrl]
    var conflict by remember(v.url) { mutableStateOf<String?>(null) }   // why Android would refuse this file
    var inspecting by remember(v.url) { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // when a download finishes, read the file's signing key so the card (and the recommendation) can show it
    LaunchedEffect(state?.finished) {
        val st = state ?: return@LaunchedEffect
        if (!st.finished || v.isBundle || app.signers.containsKey(v.flavour)) return@LaunchedEffect
        inspect(ctx, st.id)?.signer?.let { vm.learnSigner(app.pageUrl, v.flavour, it) }
    }
    val sigMatch = Recommender.signatureMatch(app, v, inst)
    val pinned = app.preferredKind == v.kind
    val shape = RoundedCornerShape(20.dp)
    val progress by animateFloatAsState(state?.fraction ?: 0f, tween(350), label = "dl")

    Column(
        modifier.padding(horizontal = 18.dp, vertical = 5.dp).fillMaxWidth()
            .then(if (recommended) Modifier.glow(C.Glow, 0.12f, 1.1f) else Modifier)
            .clip(shape)
            .background(if (recommended) Brush.verticalGradient(listOf(C.Green.copy(alpha = 0.22f), C.Card)) else Brush.verticalGradient(listOf(C.Card, C.Card)))
            .border(if (recommended) 1.5.dp else 1.dp, if (recommended) C.Glow.copy(alpha = 0.7f) else C.Stroke, shape)
            .animateContentSize()
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (recommended) {
                Icon(Icons.Rounded.Bolt, null, tint = C.Lime, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(v.labelIn(app.name), color = C.Text, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = { vm.setPreferred(app.pageUrl, if (pinned) null else v.kind) }, modifier = Modifier.size(36.dp)) {
                Icon(if (pinned) Icons.Rounded.Star else Icons.Rounded.StarOutline, "Prefer this flavour", tint = if (pinned) C.Lime else C.Faint)
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
            Chip(Abi.pretty(v.abi), if (v.abi == null || v.abi in DeviceInfo.supportedAbis) C.Glow else C.Bad)
            v.size?.let { Chip(it) }
            if (v.fresh) Chip("just updated", C.Lime)
            if (v.isBundle) Chip("split .apks", C.Warn)
            when (sigMatch) {
                true -> Chip("same signing key", C.Glow)
                false -> Chip("different signing key", C.Bad)
                null -> {}
            }
        }
        if (v.title.isNotBlank()) Text(
            v.title, color = C.Faint, fontSize = 11.sp, modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            style = TextStyle(textDirection = TextDirection.Rtl),
        )
        reasons.forEach { raw ->
            val warn = raw.startsWith("!")   // Recommender marks warnings with a leading "!"
            Row(Modifier.padding(top = 5.dp), verticalAlignment = Alignment.Top) {
                Icon(
                    if (warn) Icons.Rounded.WarningAmber else Icons.Rounded.Check, null,
                    tint = if (warn) C.Warn else C.Glow, modifier = Modifier.size(14.dp).padding(top = 2.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(raw.removePrefix("!"), color = if (warn) C.Warn else C.Text.copy(alpha = 0.85f), fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val s = state
            val label = when {
                inspecting -> "Checking the file…"
                s?.finished == true -> if (v.isBundle) "Downloaded" else "Install"
                s?.failed == true -> "Retry download"
                s != null -> "${(s.fraction * 100).toInt()}%  ·  ${mb(s.done)} / ${if (s.total > 0) mb(s.total) else "?"}"
                else -> "Download"
            }
            DlButton(
                label = label, progress = if (s != null && !s.finished && !s.failed) progress else if (s?.finished == true) 1f else 0f,
                primary = recommended, icon = if (s?.finished == true) Icons.Rounded.InstallMobile else Icons.Rounded.Download,
                modifier = Modifier.weight(1f),
            ) {
                when {
                    s?.finished == true && v.isBundle -> Toast.makeText(ctx, "Saved to Android/data/app.updrop/files/Download. Open it with SAI (split APK installer).", Toast.LENGTH_LONG).show()
                    inspecting -> {}
                    s?.finished == true -> scope.launch {
                        inspecting = true
                        val a = inspect(ctx, s.id)
                        inspecting = false
                        a?.signer?.let { vm.learnSigner(app.pageUrl, v.flavour, it) }
                        val i = inst
                        conflict = when {
                            a == null || i == null || (app.packageName != null && a.packageName != app.packageName) -> null
                            a.signer != null && i.signer != null && a.signer != i.signer ->
                                "This file is signed with a different key (${Signer.short(a.signer)}) than the ${app.name} on your phone (${Signer.short(i.signer)}). Android only updates an app with a file signed by the same key, so it would stop with \"package conflicts with an existing package\"."
                            a.versionCode < i.versionCode ->
                                "This file (${a.versionName ?: a.versionCode}) is older than the installed ${i.versionName}. Android blocks downgrades."
                            else -> null
                        }
                        if (conflict == null) install(ctx, s.id)
                    }
                    s != null && !s.failed -> Downloads.cancel(ctx, v.url).also { dlId = null; state = null }
                    else -> {
                        dlId = Downloads.start(ctx, v.url)
                        vm.setPreferred(app.pageUrl, v.kind) // remember the flavour for next time
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = { copy(ctx, v.url) }) { Icon(Icons.Rounded.ContentCopy, "Copy link", tint = C.Dim) }
        }
        if (state != null && state?.finished == false && state?.failed == false) {
            Text("Tap the bar to cancel", color = C.Faint, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }

    conflict?.let { why ->
        val matching = app.variants.firstOrNull { it.url != v.url && Recommender.signatureMatch(app, it, inst) == true }
        AlertDialog(
            onDismissRequest = { conflict = null },
            containerColor = C.CardHi,
            title = { Text("Can't update in place", color = C.Text) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(why, color = C.Dim, fontSize = 14.sp)
                    if (matching != null) Text(
                        "\"${matching.labelIn(app.name)}\" is signed with the same key as your installed copy. Get that one instead to update without losing anything.",
                        color = C.Glow, fontSize = 14.sp,
                    )
                    Text(
                        "To install this file anyway, ${app.name} must be uninstalled first. Android deletes the app's data (saves, logins, settings) on uninstall, and without root no app can copy it out. Use the app's own backup, export or account sync before you continue.",
                        color = C.Warn, fontSize = 13.sp,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    conflict = null
                    val id = state?.id ?: return@TextButton
                    val pkg = app.packageName ?: return@TextButton
                    vm.uninstallThenInstall(ctx, pkg, id)
                }) { Text("Uninstall & install", color = C.Bad) }
            },
            dismissButton = { TextButton(onClick = { conflict = null }) { Text("Cancel", color = C.Glow) } },
        )
    }
}

/** Reads the downloaded APK's package, version and signing key (off the main thread). */
private suspend fun inspect(ctx: Context, id: Long): app.updrop.data.ArchiveInfo? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    Downloads.localPath(ctx, id)?.let { Signer.archive(ctx, it) }
}

@Composable
private fun DlButton(label: String, progress: Float, primary: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier.pressScale(src).height(46.dp).clip(shape)
            .background(if (primary) C.Green.copy(alpha = 0.35f) else C.CardHi)
            .border(1.dp, if (primary) C.Glow.copy(alpha = 0.6f) else C.Stroke, shape)
            .drawBehind {
                if (progress > 0f) drawRect(
                    Brush.horizontalGradient(listOf(C.Green, C.Glow)),
                    size = size.copy(width = size.width * progress),
                )
            }
            .clickable(src, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

private fun mb(b: Long) = "%.0f MB".format(b / 1048576.0)

private fun copy(ctx: Context, url: String) {
    ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("link", url))
    if (Build.VERSION.SDK_INT < 33) Toast.makeText(ctx, "Link copied", Toast.LENGTH_SHORT).show()
}

fun install(ctx: Context, id: Long) {
    if (Build.VERSION.SDK_INT >= 26 && !ctx.packageManager.canRequestPackageInstalls()) {
        Toast.makeText(ctx, "Allow Updrop to install apps, then tap Install again", Toast.LENGTH_LONG).show()
        ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")))
        return
    }
    if (!Downloads.install(ctx, id)) Toast.makeText(ctx, "The downloaded file is gone. Download it again.", Toast.LENGTH_LONG).show()
}
