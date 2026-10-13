package com.mindfortress.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.mindfortress.app.MainActivity
import com.mindfortress.app.ai.DeviceActionExecutor
import com.mindfortress.app.theme.*

class JarvisFloatingOverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createFloatingHud()
    }

    private fun createFloatingHud() {
        if (!Settings.canDrawOverlays(this)) return

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 30
            y = 200
        }

        val actionExecutor = DeviceActionExecutor(this)

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@JarvisFloatingOverlayService)
            setViewTreeSavedStateRegistryOwner(this@JarvisFloatingOverlayService)
            setContent {
                var isExpanded by remember { mutableStateOf(false) }

                Box(
                    modifier = Modifier.wrapContentSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (isExpanded) {
                        // Expanded Quick-Command Radial Hub
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = DeepSpaceObsidian.copy(alpha = 0.94f),
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, ArcReactorCyanGlow),
                            modifier = Modifier.padding(8.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.width(210.dp)
                                ) {
                                    Text(
                                        "⚡ JARVIS HUD",
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = ArcReactorCyan
                                    )
                                    Text(
                                        "✕",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        modifier = Modifier.clickable { isExpanded = false }
                                    )
                                }

                                FloatingHudActionPill(
                                    label = "🎙️ Open Console",
                                    color = ArcReactorCyan
                                ) {
                                    isExpanded = false
                                    val intent = Intent(this@JarvisFloatingOverlayService, MainActivity::class.java).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                    }
                                    startActivity(intent)
                                }

                                FloatingHudActionPill(
                                    label = "🫁 Physiological Sigh",
                                    color = HologramTeal
                                ) {
                                    isExpanded = false
                                    actionExecutor.speak("Physiological sigh protocol active. Two sharp inhales through your nose, then a long exhale through your mouth.")
                                }

                                FloatingHudActionPill(
                                    label = "🔒 90m Ultradian Lock",
                                    color = QuantumAmber
                                ) {
                                    isExpanded = false
                                    actionExecutor.activateDeepFocusLock(90)
                                    actionExecutor.speak("90-minute ultradian lock engaged. Entering peak flow.")
                                }
                            }
                        }
                    } else {
                        // Compact Draggable Arc Reactor Orb
                        MiniArcReactorOrb(
                            onClick = { isExpanded = true }
                        )
                    }
                }
            }
        }

        // Draggable touch listener
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        composeView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    layoutParams.x = initialX + (event.rawX - initialTouchX).toInt()
                    layoutParams.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(composeView, layoutParams)
                    true
                }
                else -> false
            }
        }

        overlayView = composeView
        windowManager.addView(composeView, layoutParams)
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        if (overlayView != null) {
            windowManager.removeView(overlayView)
            overlayView = null
        }
    }

    companion object {
        fun start(context: Context) {
            if (Settings.canDrawOverlays(context)) {
                val intent = Intent(context, JarvisFloatingOverlayService::class.java)
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, JarvisFloatingOverlayService::class.java)
            context.stopService(intent)
        }
    }
}

@Composable
fun MiniArcReactorOrb(onClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "MiniOrbTransition")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing)),
        label = "miniRot"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(DeepSpaceObsidian.copy(alpha = 0.88f))
            .clickable(onClick = onClick)
    ) {
        Canvas(modifier = Modifier.size(48.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension / 2f

            // Outer cyan rotating ring
            rotate(rotation, pivot = center) {
                drawCircle(
                    color = ArcReactorCyan.copy(alpha = 0.7f),
                    radius = radius * 0.9f,
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )
                // 3 Energy Nodes
                for (i in 0 until 3) {
                    val angle = Math.toRadians((i * 120.0)).toFloat()
                    val p = Offset(
                        center.x + radius * 0.9f * kotlin.math.cos(angle),
                        center.y + radius * 0.9f * kotlin.math.sin(angle)
                    )
                    drawCircle(color = ArcReactorCyan, radius = 2.5.dp.toPx(), center = p)
                }
            }

            // Inner glowing core
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White, ArcReactorCyan, Color.Transparent),
                    center = center,
                    radius = radius * 0.55f
                ),
                radius = radius * 0.55f,
                center = center
            )
        }
    }
}

@Composable
fun FloatingHudActionPill(
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = CyberCardSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }
    }
}
