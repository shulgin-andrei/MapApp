package com.andrey.mapapp.utils

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import kotlin.math.cos
import kotlin.math.sin

//class WindRoseOverlay(
//    private val center: GeoPoint,
//    private val stats: List<WindStat>
//) : Overlay() {
//
//    private val paint = Paint().apply {
//        color = Color.parseColor("#80FF0000") //
//        style = Paint.Style.FILL_AND_STROKE
//        strokeWidth = 5f
//        isAntiAlias = true
//    }
//
//    private val linePaint = Paint().apply {
//        color = Color.BLACK
//        strokeWidth = 2f
//        style = Paint.Style.STROKE
//        alpha = 50
//    }
//
//    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
//        if (shadow) return
//
//        // Переводим гео-координаты центра в пиксели на экране
//        val projection = mapView.projection
//        val centerPoint = projection.toPixels(center, null)
//
//        val maxRadius = 300f // Максимальная длина лепестка в пикселях
//
//        // Рисуем 8 лепестков
//        stats.forEach { stat ->
//            // ИНВЕРСИЯ: прибавляем 180 градусов, чтобы показать КУДА летит ветер
//            // Угол в радианах. Сектор 0 (Север) в коде идет вверх (-90 градусов от востока)
//            val angleDeg = (stat.directionIndex * 45.0) + 180.0
//            val angleRad = Math.toRadians(angleDeg - 90.0)
//
//            // Длина лепестка зависит от частоты (0..100%)
//            val radius = (stat.frequency / 100.0) * maxRadius
//
//            if (radius > 0) {
//                val endX = centerPoint.x + (radius * cos(angleRad)).toFloat()
//                val endY = centerPoint.y + (radius * sin(angleRad)).toFloat()
//
//                // Рисуем линию (или можно заморочиться с треугольником)
//                canvas.drawLine(centerPoint.x.toFloat(), centerPoint.y.toFloat(), endX, endY, paint)
//
//                // Маленький кружок на конце для красоты
//                canvas.drawCircle(endX, endY, 8f, paint)
//            }
//        }
//    }
//}

//class WindRoseOverlay(
//    private val center: GeoPoint,
//    private val stats: List<WindStat>
//) : Overlay() {
//
//    private val fillPaint = Paint().apply {
//        color = Color.parseColor("#600000FF")
//        style = Paint.Style.FILL
//        isAntiAlias = true
//    }
//
//    private val outlinePaint = Paint().apply {
//        color = Color.BLUE
//        style = Paint.Style.STROKE
//        strokeWidth = 4f
//        isAntiAlias = true
//    }
//
//    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
//        if (shadow) return
//
//        val projection = mapView.projection
//        val centerPoint = projection.toPixels(center, null)
//
//        // scaling
//        val scaleFactor = 6f
//
//        val path = Path()
//        var firstPointSet = false
//
//        // going through 8 segments
//        stats.sortedBy { it.directionIndex }.forEach { stat ->
//            // inversion, to show not the rose, but the direction of wind
//            val angleDeg = (stat.directionIndex * 45.0) + 180.0
//            val angleRad = Math.toRadians(angleDeg - 90.0)
//
//            val radius = stat.frequency.toFloat() * scaleFactor
//            val x = centerPoint.x + (radius * cos(angleRad)).toFloat()
//            val y = centerPoint.y + (radius * sin(angleRad)).toFloat()
//
//            if (!firstPointSet) {
//                path.moveTo(x, y)
//                firstPointSet = true
//            } else {
//                path.lineTo(x, y)
//            }
//        }
//
//        if (firstPointSet) {
//            path.close()
//            canvas.drawPath(path, fillPaint)
//            canvas.drawPath(path, outlinePaint)
//        }
//
//        // Рисуем точку центра источника
//        canvas.drawCircle(centerPoint.x.toFloat(), centerPoint.y.toFloat(), 6f, outlinePaint)
//    }
//}


class WindRoseOverlay(
    private val center: GeoPoint,
    private val stats: List<WindStat>
) : Overlay() {

    private var cachedBitmap: android.graphics.Bitmap? = null
    private val scaleFactor = 6f
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
        val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val cx = size / 2f
        val cy = size / 2f

        // Настройка шрифта
        val textPaint = android.graphics.Paint().apply {
            color = Color.BLACK
            textSize = 24f
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
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
                val sectorPaint = android.graphics.Paint().apply {
                    color = getWindColor(stat.avgSpeed)
                    style = android.graphics.Paint.Style.FILL
                    isAntiAlias = true
                }

                val path = android.graphics.Path().apply {
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
                    val textDistance = radius + 25f // Смещение текста от кончика
                    val tx = cx + (textDistance * cos(angleRad)).toFloat()
                    val ty = cy + (textDistance * sin(angleRad)).toFloat()

                    // Добавляем "км/ч" или просто число
                    canvas.drawText("${stat.avgSpeed.toInt()} км/ч", tx, ty + 10f, textPaint)
                }
            }
        }

        // Центр
        canvas.drawCircle(cx, cy, 6f, outlinePaint.apply { style = android.graphics.Paint.Style.FILL })

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