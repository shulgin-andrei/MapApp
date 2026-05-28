package com.andrey.mapapp.utils

import android.content.ContentResolver
import android.net.Uri
import com.andrey.mapapp.data.local.AppDataBase
import com.andrey.mapapp.data.local.entities.PlannedPointEntity
import com.andrey.mapapp.utils.wind.WindAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.osmdroid.util.GeoPoint

object PlanImporter {

    suspend fun importPlan(
        contentResolver: ContentResolver,
        db: AppDataBase,
        sourceId: Int,
        uri: Uri
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val inputStream = contentResolver.openInputStream(uri)
            val jsonString = inputStream?.bufferedReader().use { it?.readText() } ?: ""
            if (jsonString.isBlank()) throw Exception("Файл пуст")

            val source = db.sourceDao().findById(sourceId)
                ?: throw Exception("Источник не найден в БД")
            val windDataJson = source.windDataJson
                ?: throw Exception("Сначала рассчитайте розу ветров!")

            val center = GeoPoint(
                source.geometry.map { p -> p.latitude }.average(),
                source.geometry.map { p -> p.longitude }.average()
            )

            val stats = WindAnalyzer.unpackStats(windDataJson)
            val maxSector = stats.maxByOrNull { it.frequency } ?: throw Exception("Ошибка данных ветра")
            val bearing = ((maxSector.directionIndex * 45) + 180) % 360.0

            val jsonObject = JSONObject(jsonString)
            val pointsArray = jsonObject.getJSONObject("observationPlan").getJSONArray("points")
            val pointsToSave = ArrayList<PlannedPointEntity>()

            for (i in 0 until pointsArray.length()) {
                val p = pointsArray.getJSONObject(i)
                val distance = p.getDouble("distance")
                val weight = p.getDouble("weight")
                val crosswind = p.optDouble("crosswindOffset", 0.0)

                var targetPoint = GeometryUtils.calculateTargetPoint(center, distance, bearing)

                if (crosswind != 0.0) {
                    targetPoint = GeometryUtils.calculateTargetPoint(targetPoint, crosswind, (bearing + 90) % 360.0)
                }

                pointsToSave.add(
                    PlannedPointEntity(
                        sourceId = sourceId,
                        latitude = targetPoint.latitude,
                        longitude = targetPoint.longitude,
                        distance = distance,
                        weight = weight,
                        isVisited = false
                    )
                )
            }

            db.plannedPointsDao().deleteBySourceId(sourceId)
            db.plannedPointsDao().insertPoints(pointsToSave)
        }
    }
}