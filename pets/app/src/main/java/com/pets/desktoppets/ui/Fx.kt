package com.pets.desktoppets.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// ---------------------------------------------------------------------------------------------
// Backdrop: drifting aurora blobs + rising hearts, stars and bubbles, all in the palette colours.
// ---------------------------------------------------------------------------------------------
private class Particle(val x: Float, val speed: Float, val size: Float, val phase: Float, val kind: Int, val color: Int)

@Composable
fun AnimatedBackdrop(modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) withFrameNanos { n -> if (last != 0L) t += (n - last) / 1e9f; last = n }
    }
    val particles = remember { val r = Random(7); List(36) { Particle(r.nextFloat(), 0.012f + r.nextFloat() * 0.03f, 5f + r.nextFloat() * 11f, r.nextFloat(), r.nextInt(3), r.nextInt(4)) } }
    Canvas(modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        drawRect(Brush.linearGradient(listOf(p.bg1, p.bg2, p.bg3),
            start = Offset(w * (0.5f + 0.5f * sin(t * 0.12f)), 0f), end = Offset(w * (0.5f - 0.5f * sin(t * 0.12f)), h)))
        val blobs = listOf(p.primary to 0f, p.secondary to 2.1f, p.accent to 4.2f)
        blobs.forEachIndexed { i, (col, ph) ->
            val cx = w * (0.5f + 0.42f * sin(t * (0.10f + i * 0.037f) + ph))
            val cy = h * (0.5f + 0.40f * cos(t * (0.083f + i * 0.05f) + ph * 1.3f))
            drawRect(Brush.radialGradient(listOf(col.copy(alpha = if (p.dark) 0.30f else 0.26f), Color.Transparent), center = Offset(cx, cy), radius = w * 0.85f))
        }
        val cols = listOf(p.primary, p.primary2, p.secondary, p.accent)
        particles.forEach { q ->
            val prog = (q.phase + t * q.speed) % 1f
            val y = h * (1.05f - prog * 1.15f)
            val x = w * q.x + sin(t * 0.8f + q.phase * 12f) * 22f
            val tw = 0.45f + 0.55f * sin(t * 2.2f + q.phase * 20f).let { it * it }
            val a = tw * (if (prog < 0.1f) prog * 10f else if (prog > 0.85f) (1f - prog) * 6.6f else 1f) * (if (p.dark) 0.9f else 0.75f)
            val col = cols[q.color].copy(alpha = a.coerceIn(0f, 1f))
            val s = q.size * (1f + 0.3f * sin(t * 1.7f + q.phase * 9f))
            when (q.kind) {
                0 -> drawCircle(col, s * 0.55f, Offset(x, y), style = Stroke(2.2f))
                1 -> drawStar(Offset(x, y), s, col, t * 25f + q.phase * 90f)
                else -> drawHeart(Offset(x, y), s, col)
            }
        }
    }
}

fun DrawScope.drawStar(c: Offset, r: Float, color: Color, deg: Float) = rotate(deg, c) {
    val path = Path().apply {
        moveTo(c.x, c.y - r); lineTo(c.x + r * 0.22f, c.y - r * 0.22f); lineTo(c.x + r, c.y); lineTo(c.x + r * 0.22f, c.y + r * 0.22f)
        lineTo(c.x, c.y + r); lineTo(c.x - r * 0.22f, c.y + r * 0.22f); lineTo(c.x - r, c.y); lineTo(c.x - r * 0.22f, c.y - r * 0.22f); close()
    }
    drawPath(path, color)
}

fun DrawScope.drawHeart(c: Offset, r: Float, color: Color) {
    val path = Path().apply {
        moveTo(c.x, c.y + r * 0.9f)
        cubicTo(c.x - r * 1.5f, c.y - r * 0.1f, c.x - r * 0.7f, c.y - r * 1.2f, c.x, c.y - r * 0.4f)
        cubicTo(c.x + r * 0.7f, c.y - r * 1.2f, c.x + r * 1.5f, c.y - r * 0.1f, c.x, c.y + r * 0.9f)
        close()
    }
    drawPath(path, color)
}

// ---------------------------------------------------------------------------------------------
// Text, borders, entrances, press effects
// ---------------------------------------------------------------------------------------------
/** Title style whose gradient slides across the letters forever. */
@Composable
fun shimmerTitle(size: Int = 34): TextStyle {
    val p = LocalPalette.current
    val x by rememberInfiniteTransition(label = "shimmer").animateFloat(0f, 1f, infiniteRepeatable(tween(2800, easing = LinearEasing)), label = "x")
    val brush = Brush.linearGradient(listOf(p.primary, p.accent, p.primary2, p.secondary, p.primary),
        start = Offset(x * 600f, 0f), end = Offset(x * 600f + 600f, 120f), tileMode = TileMode.Repeated)
    return TextStyle(brush = brush, fontSize = size.sp, fontWeight = FontWeight.ExtraBold, fontFamily = PetsFont)
}

/** Glowing multi-colour border; the colours (not the rectangle) travel around the rounded rectangle. */
fun Modifier.neonBorder(corner: Dp, width: Dp = 3.dp, active: Boolean = true, speedMs: Int = 3600): Modifier = composed {
    if (!active) return@composed this
    val p = LocalPalette.current
    val angle by rememberInfiniteTransition(label = "neon").animateFloat(0f, 360f, infiniteRepeatable(tween(speedMs, easing = LinearEasing)), label = "a")
    val colors = intArrayOf(p.primary.toArgb(), p.accent.toArgb(), p.secondary.toArgb(), p.primary2.toArgb(), p.primary.toArgb())
    drawBehind {
        val cr = CornerRadius(corner.toPx())
        val brush = object : ShaderBrush() {
            override fun createShader(size: Size): android.graphics.Shader =
                android.graphics.SweepGradient(size.width / 2f, size.height / 2f, colors, null).also {
                    it.setLocalMatrix(android.graphics.Matrix().apply { setRotate(angle, size.width / 2f, size.height / 2f) })
                }
        }
        drawRoundRect(brush, cornerRadius = cr, style = Stroke(width.toPx() * 3.2f), alpha = 0.22f)
        drawRoundRect(brush, cornerRadius = cr, style = Stroke(width.toPx()))
    }
}

/** Slide-up + fade + pop, staggered by [index]. */
fun Modifier.entrance(index: Int = 0, key: Any? = Unit): Modifier = composed {
    val a = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { delay((index.coerceAtMost(10) * 65).toLong()); a.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow)) }
    graphicsLayer {
        alpha = a.value.coerceIn(0f, 1f); translationY = (1f - a.value) * 90f
        val s = 0.9f + 0.1f * a.value; scaleX = s; scaleY = s
    }
}

/** Clickable that squishes while pressed and springs back; no ripple. */
fun Modifier.bouncy(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.93f else 1f, spring(dampingRatio = 0.35f, stiffness = 500f), label = "bounce")
    graphicsLayer { scaleX = s; scaleY = s }.clickable(enabled = enabled, interactionSource = src, indication = null, onClick = onClick)
}

/** Gentle floating motion, for hero elements. */
fun Modifier.floaty(amplitudeDp: Float = 5f, periodMs: Int = 3200): Modifier = composed {
    val y by rememberInfiniteTransition(label = "float").animateFloat(-amplitudeDp, amplitudeDp,
        infiniteRepeatable(tween(periodMs, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "y")
    graphicsLayer { translationY = y * density }
}

// ---------------------------------------------------------------------------------------------
// Confetti
// ---------------------------------------------------------------------------------------------
class Confetti {
    var burst by mutableIntStateOf(0)
    fun fire() { burst++ }
}

val LocalConfetti = staticCompositionLocalOf { Confetti() }

private class Bit(val ang: Float, val spd: Float, val size: Float, val spin: Float, val color: Int, val kind: Int)

@Composable
fun ConfettiLayer(state: Confetti) {
    val p = LocalPalette.current
    val prog = remember { Animatable(1f) }
    val bits = remember { mutableStateOf<List<Bit>>(emptyList()) }
    LaunchedEffect(state.burst) {
        if (state.burst == 0) return@LaunchedEffect
        val r = Random(state.burst)
        bits.value = List(90) { Bit(r.nextFloat() * 6.283f, 0.35f + r.nextFloat() * 0.9f, 6f + r.nextFloat() * 10f, r.nextFloat() * 720f - 360f, r.nextInt(4), r.nextInt(3)) }
        prog.snapTo(0f); prog.animateTo(1f, tween(1700, easing = LinearEasing))
    }
    if (prog.value < 1f) Canvas(Modifier.fillMaxSize()) {
        val cols = listOf(p.primary, p.primary2, p.secondary, p.accent)
        val o = Offset(size.width / 2f, size.height * 0.42f); val t = prog.value
        bits.value.forEach { b ->
            val dist = b.spd * size.width * 0.9f * (1f - (1f - t) * (1f - t))
            val x = o.x + cos(b.ang) * dist; val y = o.y + sin(b.ang) * dist + 520f * t * t
            val col = cols[b.color].copy(alpha = (1f - t).coerceIn(0f, 1f))
            when (b.kind) {
                0 -> drawStar(Offset(x, y), b.size * 1.3f, col, b.spin * t)
                1 -> drawHeart(Offset(x, y), b.size, col)
                else -> rotate(b.spin * t, Offset(x, y)) { drawRect(col, Offset(x - b.size / 2, y - b.size / 4), Size(b.size, b.size / 2)) }
            }
        }
    }
}
