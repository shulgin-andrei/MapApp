package com.andrey.mapapp.ui.main

import android.content.Context
import android.graphics.Color
import com.andrey.mapapp.R
import com.andrey.mapapp.data.local.MarkerData
import com.andrey.mapapp.data.local.entities.PlannedPointEntity
import com.andrey.mapapp.data.local.entities.SampleEntity
import com.andrey.mapapp.data.local.entities.SourceEntity
import com.andrey.mapapp.data.local.enums.MarkerType
import com.andrey.mapapp.data.local.enums.SourceTypeEnum
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline

class MapMarkerFactory(private val context: Context, private val mapView: MapView) {

    fun createSampleMarker(entity: SampleEntity, onClick: (Marker) -> Unit): Marker {
        return Marker(mapView).apply {
            position = GeoPoint(entity.lat, entity.lon)
            title = entity.title
            relatedObject = MarkerData(entity.id, MarkerType.SAMPLE)
            icon = context.getDrawable(R.drawable.blue_circle_icon)
            alpha = 0.85f
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            setOnMarkerClickListener { m, _ ->
                onClick(m)
                true
            }
        }
    }

    fun createPlannedMarker(entity: PlannedPointEntity, onClick: (Marker) -> Unit): Marker {
        return Marker(mapView).apply {
            position = GeoPoint(entity.latitude, entity.longitude)
            title = "Рекомендуемая проба: ${entity.distance}м"
            snippet = "Вес замера: ${(entity.weight * 100).toInt()}%"
            relatedObject = MarkerData(entity.id, MarkerType.PLANNED)
            icon = context.getDrawable(R.drawable.orange_circle_icon)
            alpha = 0.6f
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            setOnMarkerClickListener { m, _ ->
                onClick(m)
                true
            }
        }
    }

    fun createSourceOverlay(source: SourceEntity, onSourceClick: (Int) -> Unit): Overlay? {
        if (source.geometry.isEmpty()) return null

        return when (source.type) {
            SourceTypeEnum.POINT -> {
                Marker(mapView).apply {
                    position = source.geometry.first()
                    title = source.title
                    snippet = source.description
                    relatedObject = MarkerData(source.id, MarkerType.SOURCE)
                    icon = context.getDrawable(R.drawable.red_circle_icon)
                    alpha = 0.75f
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    setOnMarkerClickListener { m, _ ->
                        (m.relatedObject as? MarkerData)?.id?.let { onSourceClick(it) }
                        true
                    }
                }
            }

            SourceTypeEnum.LINE -> {
                Polyline(mapView).apply {
                    setPoints(source.geometry)
                    title = source.title
                    relatedObject = MarkerData(source.id, MarkerType.SOURCE)
                    outlinePaint.color = Color.RED
                    outlinePaint.strokeWidth = 10f
                    setOnClickListener { _, _, _ ->
                        (relatedObject as? MarkerData)?.id?.let { onSourceClick(it) }
                        true
                    }
                }
            }

            SourceTypeEnum.AREA -> {
                Polygon(mapView).apply {
                    points = source.geometry
                    title = source.title
                    relatedObject = MarkerData(source.id, MarkerType.SOURCE)
                    fillPaint.color = Color.argb(70, 255, 0, 0)
                    outlinePaint.color = Color.RED
                    outlinePaint.strokeWidth = 5f
                    setOnClickListener { _, _, _ ->
                        (relatedObject as? MarkerData)?.id?.let { onSourceClick(it) }
                        true
                    }
                }
            }
        }
    }
}