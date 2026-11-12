package app.updrop.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/** Slow green motes drifting upward. Drawn on pure black so most of the screen stays off. */
@Composable
fun SparkField(modifier: Modifier = Modifier, count: Int = 42, intensity: Float = 1f) {
    val seeds = remember {
        val r = Random(7)
        List(count) { floatArrayOf(r.nextFloat(), r.nextFloat(), 0.4f + r.nextFloat() * 1.6f, 0.02f + r.nextFloat() * 0.05f, r.nextFloat() * 6.28f) }
    }
    val time = remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { time.longValue = it - start }
    }
    Canvas(modifier.fillMaxSize()) {
        val t = time.longValue / 1_000_000_000f
        seeds.forEach { s ->
            val y = ((s[1] - t * s[3]) % 1f + 1f) % 1f
            val x = s[0] + sin(t * 0.4f + s[4]) * 0.015f
            val fadeEdge = (minOf(y, 1f - y) * 5f).coerceIn(0f, 1f)
            val twinkle = 0.55f + 0.45f * sin(t * 1.7f + s[4] * 3f)
            val a = 0.33f * fadeEdge * twinkle * intensity
            val c = Offset(x * size.width, y * size.height)
            val rad = s[2].dp.toPx()
            drawCircle(Brush.radialGradient(listOf(C.Glow.copy(alpha = a * 0.5f), Color.Transparent), c, rad * 6f), rad * 6f, c)
            drawCircle(C.Lime.copy(alpha = a), rad, c)
        }
    }
}

/** Soft radial glow behind a composable. */
fun Modifier.glow(color: Color = C.Glow, alpha: Float = 0.35f, radius: Float = 1.2f) = drawBehind {
    val r = size.maxDimension * radius / 2f
    drawCircle(Brush.radialGradient(listOf(color.copy(alpha = alpha), Color.Transparent), center, r), r, center)
}

/** Rotating sweep ring, Xbox-guide style. [active] speeds it up and brightens it. */
@Composable
fun OrbitRing(size: Dp, active: Boolean, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val inf = rememberInfiniteTransition(label = "orbit")
    val angle by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(if (active) 2200 else 9000, easing = LinearEasing)), label = "a")
    val breath by inf.animateFloat(0.55f, 1f, infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b")
    val strength by animateFloatAsState(if (active) 1f else 0.35f, tween(600), label = "s")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().glow(C.Glow, 0.28f * strength * breath, 1.05f)) {
            val w = 2.2.dp.toPx()
            rotate(angle) {
                drawArc(
                    Brush.sweepGradient(listOf(Color.Transparent, C.Green.copy(alpha = strength), C.Lime.copy(alpha = strength), Color.Transparent)),
                    0f, 300f, false, style = Stroke(w, cap = StrokeCap.Round),
                    topLeft = Offset(w, w), size = androidx.compose.ui.geometry.Size(this.size.width - 2 * w, this.size.height - 2 * w),
                )
            }
        }
        content()
    }
}

/** Little breathing status light. */
@Composable
fun PulseDot(color: Color, modifier: Modifier = Modifier, size: Dp = 8.dp) {
    val inf = rememberInfiniteTransition(label = "pulse")
    val p by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "p")
    Canvas(modifier.size(size * 2.6f)) {
        val r = size.toPx() / 2
        drawCircle(color.copy(alpha = (1 - p) * 0.5f), r * (1 + p * 1.6f))
        drawCircle(color, r)
    }
}

/** Moving light band across a bar while something loads. */
@Composable
fun ScanBar(active: Boolean, modifier: Modifier = Modifier) {
    val vis by animateFloatAsState(if (active) 1f else 0f, tween(400), label = "v")
    val inf = rememberInfiniteTransition(label = "scan")
    val x by inf.animateFloat(-0.3f, 1.3f, infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing)), label = "x")
    Canvas(modifier) {
        if (vis <= 0.01f) return@Canvas
        val cx = x * size.width
        drawRect(
            Brush.horizontalGradient(
                listOf(Color.Transparent, C.Glow.copy(alpha = 0.9f * vis), C.Lime.copy(alpha = vis), Color.Transparent),
                startX = cx - size.width * 0.25f, endX = cx + size.width * 0.25f,
            )
        )
    }
}

/** Springy press feedback for any clickable surface. */
@Composable
fun Modifier.pressScale(source: MutableInteractionSource): Modifier {
    val pressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.965f else 1f, spring(dampingRatio = 0.55f, stiffness = 600f), label = "press")
    return this.graphicsLayer { scaleX = s; scaleY = s }
}

/** Staggered entrance: fades and lifts in once, delayed by [index]. */
@Composable
fun Modifier.enter(index: Int): Modifier {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(60L * index.coerceAtMost(10))
        a.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 180f))
    }
    return this.graphicsLayer {
        alpha = a.value.coerceIn(0f, 1f)
        translationY = (1 - a.value) * 48.dp.toPx()
        val sc = 0.94f + 0.06f * a.value
        scaleX = sc; scaleY = sc
    }
}

/** A short burst of rays, played when a check finds a new build. */
@Composable
fun Burst(trigger: Int, modifier: Modifier = Modifier) {
    val p = remember { Animatable(1f) }
    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        p.snapTo(0f); p.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
    }
    Canvas(modifier) {
        val v = p.value
        if (v >= 1f) return@Canvas
        val rays = 14
        for (i in 0 until rays) {
            val ang = (i.toFloat() / rays) * 2 * PI.toFloat()
            val d0 = size.minDimension * (0.25f + v * 0.35f)
            val d1 = d0 + size.minDimension * 0.12f * (1 - v)
            val dir = Offset(kotlin.math.cos(ang), sin(ang))
            drawLine(C.Lime.copy(alpha = 1 - v), center + dir * d0, center + dir * d1, 2.dp.toPx(), StrokeCap.Round)
        }
        drawCircle(C.Glow.copy(alpha = (1 - v) * 0.6f), size.minDimension * 0.5f * v, style = Stroke(2.dp.toPx()))
    }
}
