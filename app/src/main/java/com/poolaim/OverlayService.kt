
package com.poolaim

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.graphics.*
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.Toast
import java.util.Locale
import kotlin.math.*

class OverlayService : Service() {

    companion object {
        const val ACTION_START = "com.poolaim.START"
        const val ACTION_TOGGLE_TOUCH = "com.poolaim.TOGGLE_TOUCH"

        const val CHANNEL = "pool_aim_channel"
        const val NOTIFICATION_ID = 101
    }

    private lateinit var windowManager: WindowManager
    private lateinit var overlay: PoolView

    private var touchThrough = false

    override fun onCreate() {
        super.onCreate()

        windowManager =
            getSystemService(WINDOW_SERVICE) as WindowManager

        createNotificationChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        ensureForeground()

        when (intent?.action) {

            ACTION_TOGGLE_TOUCH -> {
                touchThrough = !touchThrough
                updateOverlayFlags()
                updateNotification()
                return START_STICKY
            }

            ACTION_START -> {
                // Start overlay below
            }
        }

        if (!Settings.canDrawOverlays(this)) {
            stopForeground(true)
            stopSelf()
            return START_NOT_STICKY
        }

        if (!::overlay.isInitialized) {

            overlay = PoolView(this)

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                getOverlayWindowType(),

                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,

                PixelFormat.TRANSLUCENT
            )

            params.gravity = Gravity.TOP or Gravity.START

            windowManager.addView(overlay, params)
        }

        return START_STICKY
    }

    private fun ensureForeground() {

        val notification = buildNotification()

        if (Build.VERSION.SDK_INT >= 34) {

            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )

        } else {

            startForeground(
                NOTIFICATION_ID,
                notification
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun getOverlayWindowType(): Int {

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }

    // Adjustment B:
    // Update touch-through flags safely.

    private fun updateOverlayFlags() {

        if (!::overlay.isInitialized) return

        val params =
            overlay.layoutParams as WindowManager.LayoutParams

        params.flags =
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                (if (touchThrough) {
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                } else {
                    0
                })

        windowManager.updateViewLayout(
            overlay,
            params
        )

        overlay.invalidate()
    }

    private fun createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val channel = NotificationChannel(
                CHANNEL,
                "Pool Aim Overlay",
                NotificationManager.IMPORTANCE_LOW
            )

            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {

        val toggleIntent = Intent(
            this,
            OverlayService::class.java
        ).apply {
            action = ACTION_TOGGLE_TOUCH
        }

        val pendingIntent = PendingIntent.getService(
            this,
            1,
            toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE
        )

        val builder =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(this, CHANNEL)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(this)
                    .setPriority(Notification.PRIORITY_LOW)
            }

        return builder
            .setContentTitle("🎱 Pool Aim Running")
            .setContentText(
                if (touchThrough) {
                    "Touch Through: ON"
                } else {
                    "Edit Mode: ON"
                }
            )
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_menu_view,
                if (touchThrough) {
                    "Enable Editing"
                } else {
                    "Touch Through"
                },
                pendingIntent
            )
            .build()
    }

    private fun updateNotification() {

        getSystemService(NotificationManager::class.java)
            .notify(
                NOTIFICATION_ID,
                buildNotification()
            )
    }

    override fun onDestroy() {

        if (::overlay.isInitialized) {
            windowManager.removeView(overlay)
        }

        stopForeground(true)

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

/* =========================================
   PURE GEOMETRY HELPERS
   ========================================= */

private typealias Point = Pair<Float, Float>

private fun distance(a: Point, b: Point): Float {

    return hypot(
        a.first - b.first,
        a.second - b.second
    )
}

private fun unit(a: Point, b: Point): Point {

    val d = distance(a, b)

    if (d < 0.000001f) {
        return Point(0f, 0f)
    }

    return Point(
        (b.first - a.first) / d,
        (b.second - a.second) / d
    )
}

private fun pockets(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float
): List<Point> {

    val mid = (left + right) / 2f

    return listOf(
        Point(left, top),
        Point(mid, top),
        Point(right, top),
        Point(left, bottom),
        Point(mid, bottom),
        Point(right, bottom)
    )
}

private fun nearestPocket(
    point: Point,
    pocketList: List<Point>
): Pair<Point, Float> {

    val best = pocketList.minByOrNull {
        distance(point, it)
    } ?: return Point(0f, 0f) to Float.MAX_VALUE

    return best to distance(point, best)
}

private fun clampToRect(
    point: Point,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float
): Point {

    return Point(
        point.first.coerceIn(left, right),
        point.second.coerceIn(top, bottom)
    )
}

private fun ghostBall(
    obj: Point,
    pocket: Point,
    radius: Float
): Point {

    val u = unit(obj, pocket)

    return Point(
        obj.first - u.first * 2f * radius,
        obj.second - u.second * 2f * radius
    )
}

private fun angleBetween(
    a: Point,
    b: Point
): Float {

    val na = hypot(a.first, a.second)
    val nb = hypot(b.first, b.second)

    if (na < 1e-9f || nb < 1e-9f) {
        return 0f
    }

    val cosine = (
        (a.first * b.first + a.second * b.second) /
            (na * nb)
        ).coerceIn(-1f, 1f)

    return Math.toDegrees(
        acos(cosine).toDouble()
    ).toFloat()
}

private fun cutAngle(
    cue: Point,
    obj: Point,
    pocket: Point
): Float {

    return angleBetween(
        Point(
            obj.first - cue.first,
            obj.second - cue.second
        ),
        Point(
            pocket.first - obj.first,
            pocket.second - obj.second
        )
    )
}

private fun bestPocket(
    cue: Point,
    obj: Point,
    pocketList: List<Point>,
    radius: Float,
    maxCut: Float = 80f
): Pair<Int, Float>? {

    var best: Pair<Int, Float>? = null

    pocketList.forEachIndexed { index, pocket ->

        if (distance(obj, pocket) <= 2f * radius) {
            return@forEachIndexed
        }

        val angle = cutAngle(cue, obj, pocket)

        if (angle > maxCut) {
            return@forEachIndexed
        }

        if (best == null || angle < best!!.second) {
            best = index to angle
        }
    }

    return best
}

private fun bankSegments(
    start: Point,
    direction: Point,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    maxBanks: Int = 2
): List<Pair<Point, Point>> {

    var vx = direction.first
    var vy = direction.second

    var cx = start.first
    var cy = start.second

    if (!vx.isFinite() || !vy.isFinite() ||
        (vx == 0f && vy == 0f)
    ) {
        return emptyList()
    }

    val segments = mutableListOf<Pair<Point, Point>>()

    repeat(maxBanks.coerceAtLeast(0)) {

        val candidates =
            mutableListOf<Pair<Float, String>>()

        if (vx > 0f) {
            candidates.add((right - cx) / vx to "x")
        } else if (vx < 0f) {
            candidates.add((left - cx) / vx to "x")
        }

        if (vy > 0f) {
            candidates.add((bottom - cy) / vy to "y")
        } else if (vy < 0f) {
            candidates.add((top - cy) / vy to "y")
        }

        val valid = candidates.filter {
            it.first > 0.001f && it.first.isFinite()
        }

        val hitInfo = valid.minByOrNull {
            it.first
        } ?: return segments

        val t = hitInfo.first
        val axis = hitInfo.second

        val hit = Point(
            cx + vx * t,
            cy + vy * t
        )

        segments.add(
            Point(cx, cy) to hit
        )

        if (axis == "x") {
            vx = -vx
        } else {
            vy = -vy
        }

        cx = hit.first
        cy = hit.second
    }

    return segments
}

private fun difficulty(
    angle: Float,
    length: Float,
    tableWidth: Float
): String {

    val score =
        angle / 90f * 0.7f +
            min(
                length / max(tableWidth, 1f),
                1.5f
            ) / 1.5f * 0.3f

    return when {
        score < 0.22f -> "Easy"
        score < 0.45f -> "Medium"
        score < 0.70f -> "Hard"
        else -> "Very Hard"
    }
}

/* =========================================
   FLOATING OVERLAY VIEW
   ========================================= */

private class PoolView(
    context: Context
) : View(context) {

    private val paint = Paint(
        Paint.ANTI_ALIAS_FLAG
    )

    private var table = RectF()

    private var cue = Point(0f, 0f)
    private var obj = Point(0f, 0f)

    private var ballRadius = 12f

    private var dragging = 0

    private var selectedPocket = -1
    private var currentCut = 0f

    private val mainColor =
        Color.rgb(80, 220, 140)

    init {
        setBackgroundColor(Color.TRANSPARENT)
        isClickable = true
    }

    private fun initializeTable() {

        val w = width.toFloat()
        val h = height.toFloat()

        if (w <= 0f || h <= 0f) return

        table = RectF(
            w * 0.04f,
            h * 0.19f,
            w * 0.96f,
            h * 0.88f
        )

        ballRadius = table.width() * 0.0125f

        cue = Point(
            table.left + table.width() * 0.25f,
            table.centerY()
        )

        obj = Point(
            table.right - table.width() * 0.25f,
            table.centerY()
        )
    }

    override fun onSizeChanged(
        w: Int,
        h: Int,
        oldw: Int,
        oldh: Int
    ) {
        super.onSizeChanged(w, h, oldw, oldh)

        initializeTable()
    }

    private fun drawLine(
        canvas: Canvas,
        a: Point,
        b: Point,
        color: Int,
        thickness: Float = 3f
    ) {

        paint.color = color
        paint.strokeWidth = thickness
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND

        canvas.drawLine(
            a.first,
            a.second,
            b.first,
            b.second,
            paint
        )
    }

    private fun drawBall(
        canvas: Canvas,
        point: Point,
        color: Int,
        label: String
    ) {

        paint.style = Paint.Style.FILL
        paint.color = color

        canvas.drawCircle(
            point.first,
            point.second,
            ballRadius,
            paint
        )

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f
        paint.color = Color.WHITE

        canvas.drawCircle(
            point.first,
            point.second,
            ballRadius,
            paint
        )

        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = ballRadius * 0.85f

        canvas.drawText(
            label,
            point.first,
            point.second + ballRadius * 0.3f,
            paint
        )
    }

    override fun onDraw(canvas: Canvas) {

        super.onDraw(canvas)

        if (width <= 0 || height <= 0) return

        if (table.isEmpty) {
            initializeTable()
        }

        val pocketList = pockets(
            table.left,
            table.top,
            table.right,
            table.bottom
        )

        selectedPocket = -1
        currentCut = 0f

        // TABLE BORDER

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = Color.WHITE

        canvas.drawRoundRect(
            table,
            20f,
            20f,
            paint
        )

        // SIX POCKETS

        pocketList.forEach { pocket ->

            paint.color = mainColor
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f

            canvas.drawCircle(
                pocket.first,
                pocket.second,
                14f,
                paint
            )
        }

        // POCKET GUIDE LINES

        pocketList.forEach { pocket ->

            drawLine(
                canvas,
                obj,
                pocket,
                Color.argb(90, 255, 255, 255),
                1.5f
            )
        }

        // BEST POCKET

        val best = bestPocket(
            cue,
            obj,
            pocketList,
            ballRadius
        )

        if (best != null) {

            selectedPocket = best.first
            currentCut = best.second

            val targetPocket =
                pocketList[selectedPocket]

            // OBJECT TO POCKET

            drawLine(
                canvas,
                obj,
                targetPocket,
                Color.GREEN,
                3f
            )

            // GHOST BALL

            val ghost = ghostBall(
                obj,
                targetPocket,
                ballRadius
            )

            drawLine(
                canvas,
                cue,
                ghost,
                Color.CYAN,
                3f
            )

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            paint.color = Color.CYAN

            canvas.drawCircle(
                ghost.first,
                ghost.second,
                ballRadius,
                paint
            )

            // BANK GUIDES

            val direction = unit(cue, obj)

            val banks = bankSegments(
                cue,
                direction,
                table.left,
                table.top,
                table.right,
                table.bottom,
                2
            )

            banks.forEachIndexed { index, segment ->

                drawLine(
                    canvas,
                    segment.first,
                    segment.second,
                    if (index == 0) {
                        Color.YELLOW
                    } else {
                        Color.MAGENTA
                    },
                    2f
                )
            }
        }

        // BALLS

        drawBall(
            canvas,
            cue,
            Color.WHITE,
            "C"
        )

        drawBall(
            canvas,
            obj,
            Color.RED,
            "O"
        )

        // HUD

        drawHUD(canvas)
    }

    private fun drawHUD(canvas: Canvas) {

        val length = distance(cue, obj)

        val level = difficulty(
            currentCut,
            length,
            table.width()
        )

        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.textSize = 16f
        paint.textAlign = Paint.Align.LEFT

        val pocketNumber =
            if (selectedPocket >= 0) {
                selectedPocket + 1
            } else {
                0
            }

        canvas.drawText(
            "POOL AIM | Cut: ${
                String.format(Locale.US, "%.1f", currentCut)
            }°",
            25f,
            45f,
            paint
        )

        canvas.drawText(
            "Pocket: $pocketNumber | Difficulty: $level",
            25f,
            70f,
            paint
        )

        canvas.drawText(
            "Drag balls to adjust",
            25f,
            95f,
            paint
        )
    }

    override fun onTouchEvent(
        event: MotionEvent
    ): Boolean {

        val x = event.x
        val y = event.y

        when (event.action) {

            MotionEvent.ACTION_DOWN -> {

                val point = Point(x, y)

                dragging = when {

                    distance(point, cue) <
                        ballRadius * 2.5f -> 1

                    distance(point, obj) <
                        ballRadius * 2.5f -> 2

                    else -> 0
                }

                return dragging != 0
            }

            MotionEvent.ACTION_MOVE -> {

                if (dragging == 0) {
                    return false
                }

                val point = clampToRect(
                    Point(x, y),
                    table.left + ballRadius,
                    table.top + ballRadius,
                    table.right - ballRadius,
                    table.bottom - ballRadius
                )

                if (dragging == 1) {
                    cue = point
                } else {
                    obj = point
                }

                invalidate()

                return true
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {

                dragging = 0

                performClick()

                return true
            }
        }

        return true
    }

    override fun performClick(): Boolean {

        super.performClick()

        return true
    }
}
