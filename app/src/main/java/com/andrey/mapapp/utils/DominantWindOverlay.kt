package com.andrey.mapapp.utils

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import kotlin.math.cos
import kotlin.math.sin

class DominantWindOverlay(
    private val center: GeoPoint,
    private val stats: List<WindStat>
) : Overlay() {

    // Фиксированная длина линии на экране в пикселях
    private val lineLength = 500f

    // Настраиваем краску для красивой пунктирной линии
    private val linePaint = Paint().apply {
        color = Color.parseColor("#0000FF") // Наш синий цвет
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
        // Эффект пунктира: 15 пикселей линия, 10 пикселей пробел
        pathEffect = DashPathEffect(floatArrayOf(15f, 10f), 0f)
    }

    // Настраиваем краску для стрелочки (наконечника)
    private val arrowPaint = Paint().apply {
        color = Color.parseColor("#0000FF")
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow || stats.isEmpty()) return

        // 1. Находим сектор с максимальной частотой
        val dominantStat = stats.maxByOrNull { it.frequency } ?: return

        // Если ветра вообще не было, ничего не рисуем
        if (dominantStat.frequency <= 0) return

        // 2. Высчитываем угол (логика как в твоей розе)
        val angleDeg = (dominantStat.directionIndex * 45.0) + 180.0
        val angleRad = Math.toRadians(angleDeg - 90.0)

        // 3. Получаем координаты центра метеостанции на экране
        val projection = mapView.projection
        val centerPoint = projection.toPixels(center, null)
        val cx = centerPoint.x.toFloat()
        val cy = centerPoint.y.toFloat()

        // 4. Считаем, где закончится линия
        val targetX = cx + (lineLength * cos(angleRad)).toFloat()
        val targetY = cy + (lineLength * sin(angleRad)).toFloat()

        // 5. Рисуем пунктирный луч
        canvas.drawLine(cx, cy, targetX, targetY, linePaint)

        // 6. Рисуем аккуратную стрелочку на конце, чтобы было понятно, куда дует
        drawArrowHead(canvas, targetX, targetY, angleRad)
    }

    private fun drawArrowHead(canvas: Canvas, x: Float, y: Float, angleRad: Double) {
        val arrowSize = 15f
        val arrowPath = Path()

        // Углы для крыльев стрелки относительно основного вектора
        val leftWingAngle = angleRad - Math.toRadians(145.0)
        val rightWingAngle = angleRad + Math.toRadians(145.0)

        val leftX = x + (arrowSize * cos(leftWingAngle)).toFloat()
        val leftY = y + (arrowSize * sin(leftWingAngle)).toFloat()
        val rightX = x + (arrowSize * cos(rightWingAngle)).toFloat()
        val rightY = y + (arrowSize * sin(rightWingAngle)).toFloat()

        arrowPath.moveTo(x, y)
        arrowPath.lineTo(leftX, leftY)
        arrowPath.lineTo(rightX, rightY)
        arrowPath.close()

        canvas.drawPath(arrowPath, arrowPaint)
    }
}