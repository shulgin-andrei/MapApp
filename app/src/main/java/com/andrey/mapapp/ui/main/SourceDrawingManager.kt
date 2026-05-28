package com.andrey.mapapp.ui.main

import android.graphics.Color
import android.view.View
import com.andrey.mapapp.data.local.enums.SourceTypeEnum
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline

class SourceDrawingManager(
    private val mapView: MapView,
    private val drawingControls: View,
    private val onDrawingFinished: (SourceTypeEnum, List<GeoPoint>) -> Unit
) {
    var isDrawingMode = false
        private set

    private var currentDrawingType: SourceTypeEnum? = null
    val tempPoints = mutableListOf<GeoPoint>()
    private var previewOverlay: Overlay? = null

    // DRAWING FUNCs =============================================================================
    // start of drawing the source
    // type - POINT, LINE, AREA
    // firstPoint - c'mon, you got this
    fun startDrawing(type: SourceTypeEnum, firstPoint: GeoPoint) {
        isDrawingMode = true
        currentDrawingType = type
        tempPoints.clear()
        tempPoints.add(firstPoint)
        drawingControls.visibility = View.VISIBLE
        updateDrawingPreview()
    }

    fun addPoint(p: GeoPoint) {
        if (!isDrawingMode) return
        tempPoints.add(p)
        updateDrawingPreview()
    }

    fun cancelDrawing() {
        isDrawingMode = false
        tempPoints.clear()
        mapView.overlays.remove(previewOverlay)
        previewOverlay = null
        drawingControls.visibility = View.GONE
        mapView.invalidate()
    }

    fun finishDrawing() {
        val minPoints = when (currentDrawingType) {
            SourceTypeEnum.AREA -> 3
            SourceTypeEnum.LINE -> 2
            else -> 1
        }

        if (tempPoints.size < minPoints) {
            cancelDrawing()
            return
        }

        val finalType = currentDrawingType ?: SourceTypeEnum.POINT
        val finalPoints = ArrayList(tempPoints)

        cancelDrawing()
        onDrawingFinished(finalType, finalPoints) // reporting to an activity
    }

    private fun updateDrawingPreview() {
        mapView.overlays.remove(previewOverlay)

        when (currentDrawingType) {
            SourceTypeEnum.LINE -> {
                previewOverlay = Polyline(mapView).apply {
                    setPoints(tempPoints)
                    outlinePaint.color = Color.BLUE
                }
            }
            SourceTypeEnum.AREA -> {
                previewOverlay = Polygon(mapView).apply {
                    points = tempPoints
                    fillPaint.color = Color.argb(50, 0, 0, 255)
                }
            }
            SourceTypeEnum.POINT -> {
                finishDrawing()
                return
            }
            else -> {}
        }

        previewOverlay?.let { mapView.overlays.add(it) }
        mapView.invalidate()
    }
}