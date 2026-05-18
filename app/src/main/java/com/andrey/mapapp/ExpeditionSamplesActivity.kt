package com.andrey.mapapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.coroutineScope
import com.andrey.mapapp.data.local.AppDataBase
import com.andrey.mapapp.data.local.entities.SampleEntity
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class ExpeditionSamplesActivity : AppCompatActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val expId = intent.getLongExtra("EXPEDITION_ID", -1L)
        val expName = intent.getStringExtra("EXPEDITION_NAME") ?: "Экспедиция"
        val arialStyle = TextStyle(fontFamily = FontFamily.SansSerif)

        val db = AppDataBase.createDataBase(this)

        setContent {
            // Получаем список проб именно для этой экспедиции
            val samples by db.sampleDao().getSamplesByExpedition(expId)
                .collectAsState(initial = emptyList())

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(expName + " (Пробы)") },
                        navigationIcon = {
                            IconButton(onClick = { finish() }) {
                                Icon(Icons.Default.ArrowBack, "Назад")
                            }
                        },
                        actions = {
                            if (samples.isNotEmpty()) {
                                IconButton(onClick = { exportToGeoJson(samples, expName, expId) }) {
                                    Icon(Icons.Default.Share, contentDescription = "Экспорт")
                                }
                            }
                        }

                    )
                }
            ) { padding ->
                if (samples.isEmpty()) {
                    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                        Text("В этой экспедиции пока нет проб", style = arialStyle)
                    }
                } else {
                    LazyColumn(modifier = Modifier.padding(padding)) {
                        items(samples) { sample ->
                            SampleItem(sample ,
                                onClick = {
                                    val resultIntent = Intent()
                                    resultIntent.putExtra("TARGET_LAT", sample.lat)
                                    resultIntent.putExtra("TARGET_LON", sample.lon)
                                    resultIntent.putExtra("TARGET_EXP", sample.expeditionId)
                                    setResult(RESULT_OK, resultIntent)
                                    finish() // Закрываем список и возвращаемся к карте
                                },
                                onDeleteClick = {
                                    lifecycle.coroutineScope.launch {
                                        db.sampleDao().deleteItem(sample)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun exportToGeoJson(samples: List<SampleEntity>, expeditionName: String, expeditionId: Long) {
        try {
            // 1. Создаем корень GeoJSON
            val geoJson = JSONObject()
            geoJson.put("type", "FeatureCollection")

            val features = JSONArray()

            // 2. Наполняем массив точками
            samples.forEach { sample ->
                val feature = JSONObject()
                feature.put("type", "Feature")

                // Геометрия (координаты: сначала долгота!, потом широта)
                val geometry = JSONObject()
                geometry.put("type", "Point")
                geometry.put("coordinates", JSONArray().apply {
                    put(sample.lon) // В GeoJSON принято Longitude, Latitude
                    put(sample.lat)
                })
                feature.put("geometry", geometry)

                // Данные пробы
                val properties = JSONObject()
                properties.put("name", sample.title)
                properties.put("description", sample.description)
                properties.put("expeditionId", expeditionId)
                feature.put("properties", properties)

                features.put(feature)
            }

            geoJson.put("features", features)

            // 3. Сохраняем файл или делимся им
            shareFile(geoJson.toString(), "$expeditionName.geojson")

        } catch (e: Exception) {
            e.printStackTrace()
            // Тут можно вывести Toast об ошибке
        }
    }

    private fun shareFile(content: String, fileName: String) {
        val file = java.io.File(cacheDir, fileName)
        file.writeText(content)

        val uri = androidx.core.content.FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider", // Убедись, что это совпадает с манифестом
            file
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/geo+json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, "Экспорт экспедиции")
        startActivity(chooser)
    }

}

@Composable
fun SampleItem(sample: SampleEntity,
               onClick: () -> Unit,
               onDeleteClick: () -> Unit) {
    val arialStyle = TextStyle(fontFamily = FontFamily.SansSerif)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clickable {onClick() },
        elevation = CardDefaults.cardElevation(4.dp)


    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically // Центрируем кнопку по вертикали относительно текста
        ) {


            Column(modifier = Modifier.padding(16.dp).weight(1f)) {
                Text(text = sample.title!!, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Координаты: ${sample.lat}, ${sample.lon}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                if (!sample.description.isNullOrBlank()) {
                    Text(
                        text = sample.description!!,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            IconButton(onClick = { onDeleteClick() }) {
                Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFFF6B6B))
            }
        }
    }
}