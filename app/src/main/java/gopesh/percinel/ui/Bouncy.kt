package gopesh.percinel.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/**
 * Rubber-band overscroll: pull past the top or bottom of any scrollable and the whole page
 * stretches with progressive resistance, then springs back on release. Flings that hit an
 * edge bounce. The built-in Android stretch is so subtle it reads as "nothing happened" —
 * this replaces it (platform overscroll is disabled app-wide in [App]).
 *
 * Usage: chain [rememberBouncy] BEFORE the scrolling modifier / on the LazyColumn:
 *   `Modifier.then(rememberBouncy()).verticalScroll(state)` or `LazyColumn(Modifier.then(rememberBouncy()))`
 * It also works when the content fits the screen — every page answers the finger.
 */
/**
 * Fade-through between screens: the old page fades out fast, the new one fades in with a
 * whisper of scale. Replaces the hard snap that navigation used to be.
 */
@Composable
fun <T> FadeThrough(target: T, label: String, content: @Composable (T) -> Unit) {
    AnimatedContent(
        targetState = target,
        transitionSpec = {
            (fadeIn(tween(210, delayMillis = 50)) +
                scaleIn(initialScale = 0.975f, animationSpec = tween(230, delayMillis = 50)))
                .togetherWith(fadeOut(tween(90)))
        },
        label = label,
    ) { content(it) }
}

@Composable
fun rememberBouncy(): Modifier {
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val connection = remember {
        object : NestedScrollConnection {
            // Progressive resistance: the further stretched, the harder to pull.
            private fun damped(delta: Float) = delta * (0.55f / (1f + abs(offset.value) / 220f))

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val off = offset.value
                // Dragging back while stretched unwinds the stretch before the list scrolls.
                if (source == NestedScrollSource.UserInput && off != 0f && sign(available.y) != sign(off)) {
                    val target = off + available.y
                    val clamped = if (sign(target) != sign(off)) 0f else target
                    scope.launch { offset.snapTo(clamped) }
                    return Offset(0f, clamped - off)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                // Whatever the list couldn't consume becomes stretch. Only claim the
                // vertical axis — horizontal gestures (swipe-to-delete) are not ours.
                if (source == NestedScrollSource.UserInput && available.y != 0f) {
                    scope.launch {
                        offset.snapTo((offset.value + damped(available.y)).coerceIn(-420f, 420f))
                    }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (offset.value != 0f) {
                    offset.animateTo(
                        0f,
                        spring(dampingRatio = 0.62f, stiffness = 380f),
                        initialVelocity = available.y,
                    )
                    return available
                }
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                // A fling that runs into the edge with leftover speed: quick bounce and settle.
                val v = available.y
                if (abs(v) > 150f) {
                    offset.animateTo(
                        0f,
                        spring(dampingRatio = 0.58f, stiffness = 340f),
                        initialVelocity = v.coerceIn(-6000f, 6000f) / 6f,
                    )
                }
                return available
            }
        }
    }
    return Modifier
        .nestedScroll(connection)
        .graphicsLayer { translationY = offset.value }
}
