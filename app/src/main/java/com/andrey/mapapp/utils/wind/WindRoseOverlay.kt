package com.andrey.mapapp.utils.wind

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import kotlin.math.cos
import kotlin.math.sin

class WindRoseOverlay(
    private val center: GeoPoint,
    private val stats: List<WindStat>
) : Overlay() {

    private var cachedBitmap: Bitmap? = null
    private val scaleFactor = 10f
    private val maxRadius = 200f // Ограничим размер для кэша

    // Краски выносим в init, чтобы не создавать в draw
    private val outlinePaint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
    }

    private fun getWindColor(speed: Double): Int {
        return when {
            speed < 5 -> Color.parseColor("#8000FF00")
            speed < 10 -> Color.parseColor("#80FFFF00")
            speed < 15 -> Color.parseColor("#80FFA500")
            else -> Color.parseColor("#80FF0000")
        }
    }

    private fun createRoseBitmap() {
        val size = (maxRadius * 3).toInt() // Увеличим запас под текст
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val cx = size / 2f
        val cy = size / 2f

        // Настройка шрифта
        val textPaint = Paint().apply {
            color = Color.BLACK
            textSize = 24f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            // Можно добавить небольшую тень или обводку, чтобы цифры читались на любом фоне
            setShadowLayer(3f, 0f, 0f, Color.WHITE)
        }

        stats.forEach { stat ->
            val angleDeg = (stat.directionIndex * 45.0) + 180.0
            val angleRad = Math.toRadians(angleDeg - 90.0)
            val radius = stat.frequency.toFloat() * scaleFactor

            if (radius > 0) {
                val targetX = cx + (radius * cos(angleRad)).toFloat()
                val targetY = cy + (radius * sin(angleRad)).toFloat()

                // 1. Рисуем лепесток
                val sectorPaint = Paint().apply {
                    color = getWindColor(stat.avgSpeed)
                    style = Paint.Style.FILL
                    isAntiAlias = true
                }

                val path = Path().apply {
                    moveTo(cx, cy)
                    val spread = Math.toRadians(20.0)
                    val leftX = cx + (radius * 0.8f * cos(angleRad - spread)).toFloat()
                    val leftY = cy + (radius * 0.8f * sin(angleRad - spread)).toFloat()
                    val rightX = cx + (radius * 0.8f * cos(angleRad + spread)).toFloat()
                    val rightY = cy + (radius * 0.8f * sin(angleRad + spread)).toFloat()

                    lineTo(leftX, leftY)
                    lineTo(targetX, targetY)
                    lineTo(rightX, rightY)
                    close()
                }

                canvas.drawPath(path, sectorPaint)
                canvas.drawPath(path, outlinePaint)

                // 2. Рисуем цифру скорости чуть дальше кончика лепестка
                if (stat.frequency > 5) { // не рисуем для совсем мелких, чтобы не кучковались в центре
                    val textDistance = radius + 29f // Смещение текста от кончика
                    val tx = cx + (textDistance * cos(angleRad)).toFloat()
                    val ty = cy + (textDistance * sin(angleRad)).toFloat()

                    // Добавляем "км/ч" или просто число
                    canvas.drawText("${stat.avgSpeed.toInt()} км/ч", tx, ty + 10f, textPaint)
                }
            }
        }

        // Центр
        canvas.drawCircle(cx, cy, 6f, outlinePaint.apply { style = Paint.Style.FILL })

        cachedBitmap = bitmap
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return

        // Если картинка еще не создана — создаем один раз
        if (cachedBitmap == null) {
            createRoseBitmap()
        }

        val projection = mapView.projection
        val centerPoint = projection.toPixels(center, null)

        // Рисуем готовую картинку. Это САМАЯ быстрая операция для Android.
        cachedBitmap?.let {
            val left = centerPoint.x.toFloat() - (it.width / 2f)
            val top = centerPoint.y.toFloat() - (it.height / 2f)
            canvas.drawBitmap(it, left, top, null)
        }
    }
}