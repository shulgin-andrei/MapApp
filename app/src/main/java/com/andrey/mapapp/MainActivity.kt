package com.andrey.mapapp

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.andrey.mapapp.data.local.AppDataBase
import com.andrey.mapapp.data.local.ExpeditionRepository
import com.andrey.mapapp.data.local.MarkerData
import com.andrey.mapapp.data.local.entities.ExpeditionEntity
import com.andrey.mapapp.data.local.entities.SampleEntity
import com.andrey.mapapp.data.local.entities.SourceEntity
import com.andrey.mapapp.data.local.enums.MarkerType
import com.andrey.mapapp.data.local.enums.SourceTypeEnum
import com.andrey.mapapp.data.network.RetrofitClient
import com.andrey.mapapp.ui.ExpeditionDrawerContent
import com.andrey.mapapp.ui.MarkerBottomSheet
import com.andrey.mapapp.ui.SourceBottomSheet
import com.andrey.mapapp.utils.WindAnalyzer
import com.andrey.mapapp.utils.WindRoseOverlay
import com.andrey.mapapp.utils.WindStat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.ScaleBarOverlay
import org.osmdroid.views.overlay.compass.CompassOverlay
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay
import java.time.Instant
import java.time.ZoneId.systemDefault
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private const val PREFS_NAME = "map_prefs"
private const val KEY_LAT = "last_lat"
private const val KEY_LON = "last_lon"
private const val KEY_ZOOM = "last_zoom"
class MainActivity : AppCompatActivity(), MapEventsReceiver  {

    // all view elements here
    private lateinit var composeView: ComposeView
    private lateinit var mapView: MapView
    private lateinit var drawingControls: LinearLayout

    // important stuff
    private lateinit var expRep: ExpeditionRepository // the thing, that's keeping track of last created expedition and of instance of rep itself
    private lateinit var db: AppDataBase    // database


    // variables for drawing sources
    private var isDrawingMode = false // boolean for checking the mode of drawing
    private var currentDrawingType: SourceTypeEnum? = null
    private val tempPoints = mutableListOf<GeoPoint>() // all the points created in process of drawing the source
    private var previewOverlay: Overlay? = null // for demo drawing preview, sets over mapView like an overlay
    private var currentWindOverlay: WindRoseOverlay? = null

    private val getSampleLocation = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            lifecycleScope.launch {
                val currentExp = expRep.getOrCreateActiveId()
                val defExp : Long = -1
                val lat = result.data?.getDoubleExtra("TARGET_LAT", 0.0) ?: 0.0
                val lon = result.data?.getDoubleExtra("TARGET_LON", 0.0) ?: 0.0
                val expId = result.data?.getLongExtra("TARGET_EXP", defExp)!!

                if (expId!= currentExp) {
                    expRep.setActiveExpedition(expId)
                }
                if (lat != 0.0 && lon != 0.0) {
                    val targetPoint = GeoPoint(lat, lon)
                    mapView.controller.animateTo(targetPoint)
                    mapView.controller.setZoom(18.0) // Приближаем, чтобы увидеть точку
                }
            }
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        db = AppDataBase.createDataBase(this)

        expRep = ExpeditionRepository.getInstance(
            db.expeditionDao(),
            getSharedPreferences("app_prefs", MODE_PRIVATE))

        Configuration.getInstance().load(this, getPreferences(MODE_PRIVATE))
        setContentView(R.layout.activity_main)

        composeView = findViewById<ComposeView>(R.id.compose_view)
        composeSetUp()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // reset, надо будет сделать его как функцию из главного меню
        lifecycleScope.launch {
        db.expeditionDao().clearTableAndResetIndex()
        db.sampleDao().clearTableAndResetIndex()
        db.sourceDao().clearTableAndResetIndex()}

//        lifecycleScope.launch {
//            db.sampleDao().clearTableAndResetIndex()
//        }

        /*
        var firstmarker: SampleEntity? = null
        lifecycleScope.launch {
            firstmarker = db.sampleDao().findById(id = 3)!!
            db.sampleDao().deleteItem(firstmarker)
        }
        */

//        val startGeoPoint =  GeoPoint(55.015, 82.9346)
//        val _startGeoPoint =  GeoPoint(55.03, 82.9350)
//        addMarker(startGeoPoint, "first marker", "really first", db)
//        addMarker(_startGeoPoint, "second marker", "really second", db)

//        random generation of points
//        for (i in 0..200) {
//            var point = GeoPoint(Random.nextDouble(60.15, 100.977), Random.nextDouble(30.15, 60.977))
//            addMarker(point, "point + $i", "desc + $point", db)
//        }

//        lifecycleScope.launch {
//            val sourceDao = db.sourceDao()
//
//            sourceDao.clearTableAndResetIndex()
//
//            sourceDao.insertSource(SourceEntity(
//                type = SourceTypeEnum.POINT,
//                title = "Точечный источник",
//                description = "Выброс из трубы",
//                geometry = listOf(GeoPoint(55.015, 82.934))
//            ))
//
//            sourceDao.insertSource(SourceEntity(
//                type = SourceTypeEnum.LINE,
//                title = "Линейный источник",
//                description = "Сток воды",
//                geometry = listOf(
//                    GeoPoint(55.016, 82.935),
//                    GeoPoint(55.017, 82.937),
//                    GeoPoint(55.018, 82.936)
//                )
//            ))
//
//            sourceDao.insertSource(
//                SourceEntity(
//                    type = SourceTypeEnum.AREA,
//                    title = "Площадной источник",
//                    description = "Полигон отходов",
//                    geometry = listOf(
//                        GeoPoint(55.014, 82.932),
//                        GeoPoint(55.013, 82.933),
//                        GeoPoint(55.012, 82.931),
//                        GeoPoint(
//                            55.013,
//                            82.930
//                        )
//                    )
//                )
//            )
//        }


    }


    fun composeSetUp() {
        composeView.setContent {
            val scope = rememberCoroutineScope()
            val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

            // subscription for repository
            val expeditions by expRep.allExpeditions.collectAsState(initial = emptyList())
            val activeId by expRep.currentExpeditionId.collectAsState()


            var showAddDialog by remember { mutableStateOf(false) }
            var newExpName by remember { mutableStateOf("") }

            ModalNavigationDrawer(
                drawerState = drawerState,
                gesturesEnabled = drawerState.isOpen,
                drawerContent = {

                    ExpeditionDrawerContent(
                        expeditions = expeditions,
                        activeId = activeId,
                        onSelect = { id ->
                            scope.launch {
                                expRep.setActiveExpedition(id)
                                currentWindOverlay?.let { mapView.overlays.remove(it) }
                                currentWindOverlay = null
                                drawerState.close()
                            }
                        },
                        onAddClick = { showAddDialog = true },
                        onDeleteClick = { expedition ->
                            lifecycleScope.launch(Dispatchers.IO) {
                                expRep.deleteExpedition(expedition)
                            }
                        },
                        onDetailsClick = { exp ->
                            val intent =
                                Intent(this@MainActivity, ExpeditionSamplesActivity::class.java)
                            intent.putExtra(
                                "EXPEDITION_ID",
                                exp.id
                            ) // Передаем ID, чтобы знать, что загружать
                            intent.putExtra("EXPEDITION_NAME", exp.name) // Для заголовка окна
                            //startActivity(intent)
                            getSampleLocation.launch(intent)

                        }

                    )
                }
            ) {
                // here's the mainView, that before was the only
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { _ ->
                        // making it a root layout
                        val root = layoutInflater.inflate(R.layout.activity_main, null)

                        // Инициализируем mapView и кнопки из раздутого XML
                        mapView = root.findViewById(R.id.map_view)
                        drawingControls = root.findViewById(R.id.drawing_controls)
                        val buttonFinish = root.findViewById<Button>(R.id.button_finish_drawing)
                        val buttonCancel = root.findViewById<Button>(R.id.button_cancel_drawing)
                        buttonFinish.setOnClickListener {
                            finishDrawing()
                        }
                        buttonCancel.setOnClickListener {
                            cancelDrawing()
                        }

                        mapViewLoad()
                        restoreMapState()
                        loadDataFromDB(db, expRep)

                        root
                    }
                )

                // Кнопка гамбургер поверх карты (если её нет в XML)
                Box(modifier = Modifier.fillMaxSize()) {
                    SmallFloatingActionButton(
                        onClick = { scope.launch { drawerState.open() } },
                        modifier = Modifier.padding(16.dp).align(Alignment.TopStart)
                    ) {
                        Icon(Icons.Default.Menu, contentDescription = null)
                    }
                }
            }
            if (showAddDialog) {
                AlertDialog(
                    onDismissRequest = {
                        showAddDialog = false
                        newExpName = "" // сleaning up after closing
                    },
                    title = { Text("Новая экспедиция") },
                    text = {
                        OutlinedTextField(
                            value = newExpName,
                            onValueChange = { newExpName = it },
                            label = { Text("Название") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    confirmButton = {
                        Button(
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                // Явно указываем Compose-цвет через полный путь, чтобы избежать конфликтов
                                containerColor = androidx.compose.ui.graphics.Color(0xFF90EE90),
                                contentColor = androidx.compose.ui.graphics.Color.Black
                            ),
                            onClick = {
                                if (newExpName.isNotBlank()) {
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        val newExp = ExpeditionEntity(
                                            name = newExpName,
                                            dateCreated = System.currentTimeMillis()
                                        )
                                        val selectedExp = db.expeditionDao().insertExpedition(newExp)
                                        currentWindOverlay?.let { mapView.overlays.remove(it) }
                                        expRep.setActiveExpedition(selectedExp)

                                        // Закрываем и очищаем в главном потоке
                                        withContext(Dispatchers.Main) {
                                            showAddDialog = false
                                            newExpName = ""
                                            // Можно сразу закрыть Drawer, если нужно
                                            scope.launch { drawerState.close() }
                                        }
                                    }
                                }
                                else {
                                    Toast.makeText(this, "Дайте название экспедиции", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Text("Сохранить")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            showAddDialog = false
                            newExpName = ""
                        }) {
                            Text("Отмена", color = androidx.compose.ui.graphics.Color.Black)
                        }
                    }
                )
            }
        }
    }



    // whole default setup of mapView
    fun mapViewLoad() {
        val mapEventsOverlay = MapEventsOverlay(this, this)
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapView.setBuiltInZoomControls(false)   // -default zoom
        //val infoWindow = MarkerWindow(mapView)

        val rotationGestureOverlay = RotationGestureOverlay(this, mapView)
        rotationGestureOverlay.isEnabled
        mapView.overlays.add(rotationGestureOverlay) // rotation rules
        val dm: DisplayMetrics = resources.displayMetrics
        // compass with internal thing means the compass of DEVICE
        // usable in case of navigating to point
//        val compassOverlay = CompassOverlay(this,
//            InternalCompassOrientationProvider(this), mapView)
//        compassOverlay.enableCompass()

        // this just determines location of NORTH relatively to rotation of map
        val mapNorthCompassOverlay = object: CompassOverlay(this, mapView) {
            override fun draw(c: Canvas?, pProjection: Projection?) {
                drawCompass(c, -mapView.mapOrientation, pProjection?.screenRect)
            }
        }
        Log.d("лол", dm.heightPixels.toString())

        mapView.overlays.add(mapNorthCompassOverlay)

        val scaleBarOverlay = ScaleBarOverlay(mapView)
        scaleBarOverlay.setCentred(true)
        scaleBarOverlay.setAlignBottom(true)
        scaleBarOverlay.setMaxLength(2f)
        //scaleBarOverlay.setMinZoom(7.0) // shows up only when scale bar goes around 215km+-
        scaleBarOverlay.setScaleBarOffset(100, 100)
        mapView.overlays.add(scaleBarOverlay) // scale-bar, huh
        mapView.getOverlays().add(0, mapEventsOverlay)
        mapView.controller.setZoom(20.0)
        val startGeoPoint =  GeoPoint(55.015, 82.9346)

        mapView.getOverlays().add(0, mapEventsOverlay)

        mapView.controller.setZoom(20.0)
        mapView.controller.setCenter(startGeoPoint)

    }
    private fun saveMapState() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val center = mapView.mapCenter
        val zoom = mapView.zoomLevelDouble

        prefs.edit().apply {
            // SharedPreferences не умеют хранить Float/Double высокой точности напрямую,
            // поэтому переводим в String, чтобы не потерять точность координат
            putString(KEY_LAT, center.latitude.toString())
            putString(KEY_LON, center.longitude.toString())
            putFloat(KEY_ZOOM, zoom.toFloat())
            apply()
        }
    }

    private fun restoreMapState() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        val latStr = prefs.getString(KEY_LAT, null)
        val lonStr = prefs.getString(KEY_LON, null)
        val zoom = prefs.getFloat(KEY_ZOOM, 10f) // 10f — дефолтный зум, если запуск первый

        if (latStr != null && lonStr != null) {
            val lat = latStr.toDoubleOrNull() ?: 55.015 // дефолтная широта, если что-то пошло не так
            val lon = lonStr.toDoubleOrNull() ?: 82.9346 // дефолтная долгота

            val targetPoint = GeoPoint(lat, lon)
            mapView.controller.setCenter(targetPoint)
            mapView.controller.setZoom(zoom.toDouble())
        } else {
            // Если запускаемся вообще впервые и сохраненных координат нет,
            // ставим какую-то базовую точку по умолчанию
            mapView.controller.setCenter(GeoPoint(55.015, 82.9346))
            mapView.controller.setZoom(10.0)
        }
    }

    // loading all of data from db and making markers out of it
    fun loadDataFromDB(db: AppDataBase, repository: ExpeditionRepository) {
        // two separated lifecycles to delete and update samples and sources separately
        lifecycleScope.launch {
            repository.currentExpeditionId.flatMapLatest { expId ->
                if (expId == null) {
                    // Если экспедиции нет, возвращаем пустой список
                    flowOf(emptyList())
                } else {
                    // Если есть ID — берем пробы только этой экспедиции
                    db.sampleDao().getSamplesByExpedition(expId)
                }
            }.collect { samples ->
                val samplesToRemove = mapView.overlays.filter { overlay ->
                    (overlay as? Marker)?.relatedObject.let { obj ->
                        obj is MarkerData && obj.type == MarkerType.SAMPLE
                    }
                }
                mapView.overlays.removeAll(samplesToRemove)

                // drawing sample markers at the mapView
                samples.forEach { entity ->
                    val marker = Marker(mapView).apply {
                        position = GeoPoint(entity.lat, entity.lon)
                        title = entity.title
                        relatedObject = MarkerData(entity.id, MarkerType.SAMPLE)
                        icon = getDrawable(R.drawable.blue_circle_icon)
                        alpha = 0.85f
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        setOnMarkerClickListener { m, _ ->
                            openMarkerSheet(m, null, db)
                            true
                        }
                    }
                    mapView.overlays.add(marker)
                }
                mapView.invalidate()
            }

        }

        // sources
        lifecycleScope.launch {
            //db.sourceDao().getAllSources()

            repository.currentExpeditionId.flatMapLatest { expId ->
                if (expId == null) {
                    flowOf(emptyList())
                } else {
                    // Берем источники только текущей экспедиции
                    db.sourceDao().getSourcesByExpedition(expId)
                }
            }.collect { sources ->
                val sourcesToRemove = mapView.overlays.filter { overlay ->
                    overlay is Polyline || overlay is Polygon ||
                            (overlay as? Marker)?.relatedObject.let { obj ->
                                obj is MarkerData && obj.type == MarkerType.SOURCE
                            }
                }
                mapView.overlays.removeAll(sourcesToRemove)

                // drawing all of the sources at mapView
                sources.forEach { source ->
                    when (source.type) {
                        SourceTypeEnum.POINT -> {
                            val marker = Marker(mapView).apply {
                                position = source.geometry.first()
                                title = source.title
                                snippet = source.description
                                relatedObject = MarkerData(source.id, MarkerType.SOURCE)
                                icon = getDrawable(R.drawable.red_circle_icon)
                                alpha = 0.75f
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                setOnMarkerClickListener { m, _ ->
                                    val data = m.relatedObject as? MarkerData
                                    data?.let { openSourceSheet(it.id!!, db) }
                                    true
                                }
                            }
                            mapView.overlays.add(marker)
                        }

                        SourceTypeEnum.LINE -> {
                            val line = Polyline(mapView)
                            line.setPoints(source.geometry)
                            line.title = source.title
                            line.relatedObject = MarkerData(source.id, MarkerType.SOURCE)
                            line.setOnClickListener { p, _, _ ->
                                val data = line.relatedObject as? MarkerData
                                data?.let { openSourceSheet(it.id!!, db) }
                                true
                            }
                            // line style
                            line.outlinePaint.color = Color.RED
                            line.outlinePaint.strokeWidth = 10f
                            mapView.overlays.add(line)


                        }

                        SourceTypeEnum.AREA -> {
                            val polygon = Polygon(mapView)
                            polygon.points = source.geometry
                            polygon.title = source.title
                            polygon.relatedObject = MarkerData(source.id, MarkerType.SOURCE)
                            polygon.setOnClickListener { p, _, _ ->
                                val data = polygon.relatedObject as? MarkerData
                                data?.let { openSourceSheet(it.id!!, db) }
                                true

                            }
                            // polygon style
                            polygon.fillPaint.color = Color.argb(
                                70,
                                255,
                                0,
                                0
                            ) // semi-transparent red polygon
                            polygon.outlinePaint.color = Color.RED
                            polygon.outlinePaint.strokeWidth = 5f
                            mapView.overlays.add(polygon)
                        }
                    }
                }
                mapView.invalidate()
            }
        }

    }


    // this thing is keeping save value if parse to double failed
    fun EditText.toDoubleOrDefault(default: Double): Double {
        return this.text.toString().replace(',', '.').toDoubleOrNull() ?: default
    }

    // time for sample entity
    fun getCurrentTime(): String {
        val instant = Instant.ofEpochMilli(System.currentTimeMillis())

        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneOffset.UTC)

        return formatter.format(instant)
        // for example "2026-05-19T12:30:45Z"
    }

    // whole redact and delete thing from down side of the screen
    fun openMarkerSheet(marker: Marker?, point: GeoPoint?, db: AppDataBase) {

        // all view logic is in MarkerBottomSheet class
        // here is only executive part

        // if marker passed - update
        // if point passed - add

        val relatedObject = marker?.relatedObject as? MarkerData
        val id: Int? = relatedObject?.id
        val pos = marker?.position ?: point ?: return

        val sheet = MarkerBottomSheet.newInstance(id, pos.latitude, pos.longitude)

        // save button
        sheet.onSave = { title, desc, code, newLat, newLon ->
            lifecycleScope.launch {
                val expId = expRep.getOrCreateActiveId()
                Log.d("DB_CHECK", "Attempting to insert sample with ExpID: $expId")
                val entity = SampleEntity(
                    id = id ?: null,
                    expeditionId = expId,
                    lat = newLat,
                    lon = newLon,
                    title = title,
                    description = desc,
                    code = code,
                    createdAt = getCurrentTime()
                )
                db.sampleDao().insertItem(entity)
            }
        }

        // delete button
        sheet.onDelete = {
            id?.let { lifecycleScope.launch { db.sampleDao().deleteById(it) } }
        }
        sheet.show(supportFragmentManager, "MarkerSheet")
    }

    fun openSourceSheet(id: Int? = null, db: AppDataBase) {
        val sheet = SourceBottomSheet.newInstance(id)

        sheet.onSave = { title, desc ->
            lifecycleScope.launch {

                val expId = expRep.getOrCreateActiveId()

                var finalGeometry = ArrayList(tempPoints)
                var finalType = currentDrawingType ?: SourceTypeEnum.POINT

                // edit
                if (id != null) {
                    val existing = db.sourceDao().findById(id)
                    if (existing != null) {
                        // just editing simple props, not geometry
                        if (finalGeometry.isEmpty()) {
                            finalGeometry = ArrayList(existing.geometry)
                        }
                        // for not fucking up the type of source when saving
                        finalType = existing.type
                    }
                }

                val source = SourceEntity(
                    id = id ?: null,
                    expeditionId = expId,
                    type = finalType,
                    title = title.ifBlank { "Новый источник" },
                    description = desc,
                    geometry = finalGeometry
                )
                db.sourceDao().insertSource(source)

                if (isDrawingMode) {
                    cancelDrawing()
                }
                Toast.makeText(this@MainActivity, "Сохранено", Toast.LENGTH_SHORT).show()
            }
        }

        sheet.onWindRose = { sourceId ->
            lifecycleScope.launch {
                val source = db.sourceDao().findById(sourceId)
                source?.let {
                    // taking a centre point
                    val center = GeoPoint(
                        it.geometry.map { p -> p.latitude }.average(),
                        it.geometry.map { p -> p.longitude }.average()
                    )

                    if (it.windDataJson != null) {
                        // if there's a cash - we're taking it
                        val stats = WindAnalyzer.unpackStats(it.windDataJson)
                        showWindRose(center, stats)
                    } else {
                        // doing an api call if not
                        try {
                            // current time in Long
                            val millis = System.currentTimeMillis()
                            val localDateTime = Instant.ofEpochMilli(millis)
                                .atZone(systemDefault())
                                .toLocalDateTime()
                            // formated to string
                            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

                            val endDateString = localDateTime.format(formatter)
                            // start date
                            val monthsToSubtract = 12L
                            val startDate = localDateTime.minusMonths(monthsToSubtract)
                            val startDateString = startDate.format(formatter)

                            Log.d("Time for WIND", "start: ${startDateString}, end: ${endDateString}")

                            val response = RetrofitClient.apiService.getHistoryWeather(
                                lat = center.latitude,
                                lon = center.longitude,
                                startDate = startDateString, // Пример периода
                                endDate = endDateString
                            )
                            val stats = WindAnalyzer().process(response)

                            // saving
                            db.sourceDao().updateWindData(sourceId, WindAnalyzer.packStats(stats))

                            showWindRose(center, stats)
                        } catch (e: Exception) {
                            Toast.makeText(this@MainActivity, "Ошибка загрузки данных ветра", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }


        // basically same thing with openMarkerSheet
        sheet.onDelete = { sourceId ->
            lifecycleScope.launch {
                db.sourceDao().deleteById(sourceId)
                Toast.makeText(this@MainActivity, "Источник удален", Toast.LENGTH_SHORT).show()
            }
        }

        sheet.show(supportFragmentManager, "SourceSheet")
    }

    // start of drawing the source
    // type - POINT, LINE, AREA
    // firstPoint - c'mon, you got this
    fun startDrawing(type: SourceTypeEnum, firstPoint: GeoPoint) {
        isDrawingMode = true
        currentDrawingType = type
        tempPoints.clear()
        tempPoints.add(firstPoint)
        Log.d("бля", "зашло в функцию")
        drawingControls.visibility = View.VISIBLE
        updateDrawingPreview()
    }

    fun cancelDrawing() {
        isDrawingMode = false
        tempPoints.clear()
        mapView.overlays.remove(previewOverlay)
        previewOverlay = null
        drawingControls.visibility = View.GONE
        Log.d("бля", "вышло")
        mapView.invalidate()
        Toast.makeText(this, "Рисование отменено", Toast.LENGTH_SHORT).show()
    }

    fun updateDrawingPreview() {
        // removing old overlay
        mapView.overlays.remove(previewOverlay)

        // and updating
        when (currentDrawingType) {
            SourceTypeEnum.LINE -> {
                val line = Polyline(mapView)
                line.setPoints(tempPoints)
                line.outlinePaint.color = Color.BLUE
                previewOverlay = line
            }
            SourceTypeEnum.AREA -> {
                val polygon = Polygon(mapView)
                polygon.points = tempPoints
                polygon.fillPaint.color = Color.argb(50, 0, 0, 255)
                previewOverlay = polygon
            }
            SourceTypeEnum.POINT -> {
                // ending right ahead
                finishDrawing()
                return
            }
            else -> {}
        }
        // updating overlay in mapView context
        previewOverlay?.let { mapView.overlays.add(it) }
        mapView.invalidate()
    }

    fun finishDrawing() {
        val minPoints = when(currentDrawingType) {
            SourceTypeEnum.AREA -> 3
            SourceTypeEnum.LINE -> 2
            else -> 1
        }

        if (tempPoints.size < minPoints) {
            Toast.makeText(this, "Недостаточно точек!", Toast.LENGTH_SHORT).show()
            return
        }

        // creating through the sheet
        openSourceSheet(null, db = AppDataBase.createDataBase(this))
    }

    @SuppressLint("MissingPermission")
    private fun getCurrentLocationAndSave() {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Запрашиваем последнее известное местоположение (самый быстрый способ)
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
                val lat = location.latitude
                val lon = location.longitude

                // Открываем твой диалог/экран создания пробы, но уже со вставленными координатами!
                openMarkerSheet(null,GeoPoint(lat, lon), db)
            } else {
                // Если lastLocation пустой (такое бывает, если GPS долго спал),
                // запрашиваем свежую точку один раз
                val locationRequest = Priority.PRIORITY_HIGH_ACCURACY
                fusedLocationClient.getCurrentLocation(locationRequest, null)
                    .addOnSuccessListener { freshLocation ->
                        if (freshLocation != null) {
                            openMarkerSheet(null,GeoPoint(freshLocation.latitude, freshLocation.longitude), db)
                        } else {
                            Toast.makeText(this, "Не удалось поймать спутники. Включите GPS", Toast.LENGTH_SHORT).show()
                        }
                    }
            }
        }
    }

    private val requestLocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (fineLocationGranted || coarseLocationGranted) {
            // Разрешение получено, можно брать GPS
            getCurrentLocationAndSave()
        } else {
            // Пользователь отказал — выведи Toast или диалог
            Toast.makeText(this, "Без GPS нельзя определить точку пробы", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkPermissionAndGetLocation() {
        val fineLoc = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarseLoc = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)

        if (fineLoc == PackageManager.PERMISSION_GRANTED || coarseLoc == PackageManager.PERMISSION_GRANTED) {
            getCurrentLocationAndSave()
        } else {
            // Запрашиваем разрешения
            requestLocationPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    // closing all overlays infoWindows with touch
    // i guess its kinda bad idea if i would do sm more complicated
    override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
//        Toast.makeText(this, "Tapped", Toast.LENGTH_SHORT).show()
//        InfoWindow.closeAllInfoWindowsOn(mapView);
//        return true

        // adding points in drawing mode
        if (isDrawingMode && p != null) {
            tempPoints.add(p)
            updateDrawingPreview()
            return true
        }
        if(currentWindOverlay!=null) {
            currentWindOverlay?.let { mapView.overlays.remove(it) }
            currentWindOverlay = null
            mapView.invalidate()
            Toast.makeText(this, "Оверлэй вырублен", Toast.LENGTH_SHORT).show()
        }

        //testWindApi()
        return false
    }

    override fun longPressHelper(p: GeoPoint?): Boolean {
        // DOES SOMETHING NOW
        // just calls an ultimate fun for add/edit/delete of samples and sources
        // decision list, making a sample or source
        p?.let { point ->
            val options = arrayOf("Проба", "Проба по GPS", "Источник: Точка", "Источник: Линия", "Источник: Область")

            AlertDialog.Builder(this@MainActivity)
                .setTitle("Что добавить?")
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> openMarkerSheet(null, point, AppDataBase.createDataBase(this)) // sample
                        1 -> checkPermissionAndGetLocation()
                        2 -> startDrawing(SourceTypeEnum.POINT, point)
                        3 -> startDrawing(SourceTypeEnum.LINE, point)
                        4 -> startDrawing(SourceTypeEnum.AREA, point)

                    }
                }
                .show()
        }
        return true
    }

    private fun testWindApi() {
        lifecycleScope.launch {
            try {
                // Делаем запрос (координаты из твоего примера)
                val response = RetrofitClient.apiService.getHistoryWeather(
                    lat = 55.015,
                    lon = 82.934,
                    startDate = "2025-01-01",
                    endDate = "2025-01-05" // Возьмем пару дней для теста
                )


                // Если данные пришли, выведем в логи первые 5 записей
                val directions = response.hourly.windDirections
                val speeds = response.hourly.windSpeeds

                Log.d("WIND_TEST", "Данные получены! Всего записей: ${directions.size}")

                for (i in 0 until 5) {
                    Log.d("WIND_TEST", "Час $i: Направление=${directions[i]}°, Скорость=${speeds[i]} км/ч")
                }

                // А теперь прогоним через наш анализатор (если ты его уже создал)
                val analyzer = WindAnalyzer()
                val stats = analyzer.process(response)

                val windRoseOverlay = WindRoseOverlay(GeoPoint(55.015, 82.934),stats)
                mapView.overlays.add(windRoseOverlay)
                mapView.invalidate()
                stats.forEach { stat ->
                    Log.d("WIND_TEST", "Сектор ${stat.directionIndex}: Частота ${String.format("%.1f", stat.frequency)}%, Ср.скорость ${String.format("%.1f", stat.avgSpeed)}")
                }

            } catch (e: Exception) {
                Log.e("WIND_TEST", "Ошибка при запросе: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    private fun showWindRose(center: GeoPoint, stats: List<WindStat>) {
        // clearing current wind rose if it exists
        currentWindOverlay?.let { mapView.overlays.remove(it) }

        // new layer
        val overlay = WindRoseOverlay(center, stats)
        currentWindOverlay = overlay

        // add update
        mapView.overlays.add(overlay)
        mapView.invalidate()
    }

    override fun onPause() {
        super.onPause()
        if (::mapView.isInitialized) {
            mapView.onPause()
        }
        saveMapState()

    }

    override fun onResume() {
        super.onResume()
        if (::mapView.isInitialized) {
            mapView.onResume()
        }
    }

}