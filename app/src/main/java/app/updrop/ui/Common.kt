package app.updrop.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage

/** App icon from Farsroid, with a quiet placeholder while it loads (or when the CDN is unreachable). */
@Composable
fun AppIcon(url: String?, size: Dp, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(size * 0.26f)
    Box(modifier.size(size).clip(shape).background(C.CardHi).border(1.dp, C.Stroke, shape), contentAlignment = Alignment.Center) {
        SubcomposeAsyncImage(
            model = url, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.size(size),
            loading = { Icon(Icons.Rounded.Android, null, tint = C.Faint, modifier = Modifier.size(size * 0.5f)) },
            error = { Icon(Icons.Rounded.Android, null, tint = C.Faint, modifier = Modifier.size(size * 0.5f)) },
        )
    }
}

@Composable
fun StatusPill(status: Status, modifier: Modifier = Modifier) {
    val (text, color) = when (status) {
        Status.UPDATE -> "UPDATE" to C.Lime
        Status.NEW_BUILD -> "NEW BUILD" to C.Glow
        Status.CURRENT -> "UP TO DATE" to C.Dim
        Status.NOT_INSTALLED -> "NOT INSTALLED" to C.Faint
        Status.ERROR -> "ERROR" to C.Bad
    }
    val c by animateColorAsState(color, tween(500), label = "pill")
    val hot = status == Status.UPDATE || status == Status.NEW_BUILD
    Row(
        modifier.clip(RoundedCornerShape(50)).background(c.copy(alpha = if (hot) 0.14f else 0.07f))
            .border(1.dp, c.copy(alpha = if (hot) 0.6f else 0.25f), RoundedCornerShape(50))
            .padding(start = if (hot) 2.dp else 10.dp, end = 10.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (hot) PulseDot(c, size = 5.dp)
        Text(text, color = c, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    }
}

@Composable
fun Chip(text: String, color: Color = C.Dim, modifier: Modifier = Modifier) {
    Text(
        text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Medium,
        modifier = modifier.clip(RoundedCornerShape(8.dp)).background(C.CardHi).border(1.dp, C.Stroke, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

fun ago(ms: Long): String {
    if (ms <= 0) return "never"
    val s = (System.currentTimeMillis() - ms) / 1000
    return when {
        s < 60 -> "just now"
        s < 3600 -> "${s / 60} min ago"
        s < 86400 -> "${s / 3600} h ago"
        else -> "${s / 86400} d ago"
    }
}
