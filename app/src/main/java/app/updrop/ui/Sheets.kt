package app.updrop.ui

import android.content.ClipboardManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.updrop.data.Repo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSheet(vm: MainViewModel, initial: String?, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var text by remember { mutableStateOf(initial ?: "") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // prefill from the clipboard when it holds a Farsroid link
    LaunchedEffect(Unit) {
        if (text.isBlank()) {
            val clip = ctx.getSystemService(ClipboardManager::class.java).primaryClip?.getItemAt(0)?.text?.toString()
            Repo.normalize(clip ?: "")?.let { text = it }
        }
    }
    fun submit() {
        if (busy || text.isBlank()) return
        busy = true; error = null
        vm.add(text) { err -> busy = false; if (err == null) onDismiss() else error = err }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = C.Card, scrimColor = Color.Black.copy(alpha = 0.7f)) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding().imePadding().padding(bottom = 20.dp)) {
            Text("Watch a Farsroid page", color = C.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("Paste the app's page link, for example farsroid.com/adobe-photoshop-lightroom-cc/", color = C.Dim, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 14.dp))
            OutlinedTextField(
                value = text, onValueChange = { text = it; error = null },
                modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !busy,
                placeholder = { Text("https://www.farsroid.com/…", color = C.Faint) },
                trailingIcon = {
                    IconButton(onClick = {
                        ctx.getSystemService(ClipboardManager::class.java).primaryClip?.getItemAt(0)?.text?.toString()?.let { text = it }
                    }) { Icon(Icons.Rounded.ContentPaste, "Paste", tint = C.Glow) }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { submit() }),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = C.Glow, unfocusedBorderColor = C.Stroke, cursorColor = C.Glow,
                    focusedTextColor = C.Text, unfocusedTextColor = C.Text,
                ),
            )
            AnimatedVisibility(error != null) {
                Text(error ?: "", color = C.Bad, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            }
            ScanBar(busy, Modifier.fillMaxWidth().padding(top = 12.dp).height(2.dp))
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(16.dp))
                    .background(Brush.horizontalGradient(listOf(C.Green, Color(0xFF1A9A1A))))
                    .clickable(enabled = !busy) { submit() },
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                if (busy) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Reading the page…", color = Color.White, fontWeight = FontWeight.Bold)
                } else Text("Start watching", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(vm: MainViewModel, onDismiss: () -> Unit) {
    val s by vm.settings.collectAsState()
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = C.Card, scrimColor = Color.Black.copy(alpha = 0.7f)) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 24.dp)) {
            Text("Background checks", color = C.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("How often Updrop looks at your pages while closed. Android may delay a check to save battery.", color = C.Dim, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to "Off", 1 to "1h", 3 to "3h", 6 to "6h", 12 to "12h", 24 to "24h").forEach { (h, l) ->
                    val on = s.intervalHours == h
                    Text(
                        l, color = if (on) Color.Black else C.Text, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                        modifier = Modifier.clip(RoundedCornerShape(12.dp))
                            .background(if (on) C.Glow else C.CardHi)
                            .border(1.dp, if (on) C.Glow else C.Stroke, RoundedCornerShape(12.dp))
                            .clickable { vm.saveSettings(s.copy(intervalHours = h)) }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Toggle("Only on Wi-Fi", "Skip checks on mobile data", s.wifiOnly) { vm.saveSettings(s.copy(wifiOnly = it)) }
            Toggle("Notify me", "A notification when a new build appears", s.notify) { vm.saveSettings(s.copy(notify = it)) }
            Spacer(Modifier.height(10.dp))
            Text("Split bundles (.apks) are saved to Download/Updrop. Plain APKs stay in Updrop's own folder for a week.", color = C.Faint, fontSize = 12.sp)
        }
    }
}

@Composable
private fun Toggle(title: String, sub: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!value) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(sub, color = C.Dim, fontSize = 12.sp)
        }
        Switch(
            checked = value, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = C.Glow, uncheckedTrackColor = C.CardHi, uncheckedBorderColor = C.Stroke),
        )
    }
}
