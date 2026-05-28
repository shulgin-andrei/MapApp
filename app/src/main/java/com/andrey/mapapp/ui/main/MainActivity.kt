package com.andrey.mapapp.ui.main

import android.content.Intent
import android.graphics.Canvas
import android.net.Uri
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.widget.Button
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
import androidx.compose.material.icons.filled.GpsFixed
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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.andrey.mapapp.R
import com.andrey.mapapp.data.local.AppDataBase
import com.andrey.mapapp.data.local.AppSettings
import com.andrey.mapapp.data.local.ExpeditionRepository
import com.andrey.mapapp.data.local.MarkerData
import com.andrey.mapapp.data.local.entities.ExpeditionEntity
import com.andrey.mapapp.data.local.entities.SampleEntity
import com.andrey.mapapp.data.local.entities.SourceEntity
import com.andrey.mapapp.data.local.enums.MarkerType
import com.andrey.mapapp.data.local.enums.SourceTypeEnum
import com.andrey.mapapp.data.network.RetrofitClient
import com.andrey.mapapp.ui.ExpeditionSamplesActivity
import com.andrey.mapapp.ui.bottom_sheets.MarkerBottomSheet
import com.andrey.mapapp.ui.bottom_sheets.SourceBottomSheet
import com.andrey.mapapp.ui.settings.SettingsActivity
import com.andrey.mapapp.utils.PlanImporter
import com.andrey.mapapp.utils.wind.DominantWindOverlay
import com.andrey.mapapp.utils.wind.WindAnalyzer
import com.andrey.mapapp.utils.wind.WindRoseOverlay
import com.andrey.mapapp.utils.wind.WindStat
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
    private lateinit var settings: AppSettings
    private var currentPeriod: Int = 1
    private lateinit var expRep: ExpeditionRepository // the thing, that's keeping track of last created expedition and of instance of rep itself
    private lateinit var db: AppDataBase    // database

    private var currentWindOverlay: WindRoseOverlay? = null
    private var currentDominantWindOverlay: DominantWindOverlay? = null
    private lateinit var mapNorthCompassOverlay: CompassOverlay
    // delegated helpers
    private lateinit var markerFactory: MapMarkerFactory
    private lateinit var drawingManager: SourceDrawingManager
    private lateinit var  locationHelper: MapLocationHelper

    private var targetSourceIdForImport: Int? = null

    // file picker for import
    private val pickJsonFileLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { fileUri ->
            val sourceId = targetSourceIdForImport ?: return@registerForActivityResult
            lifecycleScope.launch {
                PlanImporter.importPlan(contentResolver, db, sourceId, fileUri)
                    .onSuccess { Toast.makeText(this@MainActivity, "План успешно импортирован!", Toast.LENGTH_SHORT).show() }
                    .onFailure { e -> Toast.makeText(this@MainActivity, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }
    }

    // FOR ON CLICK IN EXPEDITION-SAMPLE ACTIVITY, GETS OK AND GOES TO SAMPLE ON MAP
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
                    mapView.controller.setZoom(18.0)
                }
            }
        }
    }
    private fun initHelpers() {
        markerFactory = MapMarkerFactory(this, mapView)

        drawingManager = SourceDrawingManager(mapView, drawingControls) { finalType, points ->
            // callback triggers when drawing is finished, so we only have to open the sheet
            openSourceSheet(id = null, db = db, drawnGeometry = points, drawnType = finalType)
        }

    }

    // MAIN
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        locationHelper = MapLocationHelper(this){ geoPoint ->
            // Коллбек срабатывает, когда была успешно поймана точка по GPS для замера пробы
            openMarkerSheet(null, geoPoint, db)
        }
        settings = AppSettings(this)
        currentPeriod = settings.getWindPeriod()

        db = AppDataBase.createDataBase(this)

        expRep = ExpeditionRepository.getInstance(
            db.expeditionDao(),
            getSharedPreferences("app_prefs", MODE_PRIVATE))

        Configuration.getInstance().load(this, getPreferences(MODE_PRIVATE))
        setContentView(R.layout.activity_main)
        // main form and drawer
        composeView = findViewById<ComposeView>(R.id.compose_view)
        composeSetUp()


        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    // WHOLE LEFT SIDE DRAWER UI SET UP ============================================================
    fun composeSetUp() {
        composeView.setContent {
            val scope = rememberCoroutineScope()
            val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

            // subscription for repository
            val expeditions by expRep.allExpeditions.collectAsState(initial = emptyList())
            val activeId by expRep.currentExpeditionId.collectAsState()


            var showAddDialog by remember { mutableStateOf(false) }
            var showDeleteWarningDialog by remember { mutableStateOf(false) }
            var newExpName by remember { mutableStateOf("") }

            ModalNavigationDrawer(
                drawerState = drawerState,
                gesturesEnabled = drawerState.isOpen,
                drawerContent = {
                    // drawer with connecting callbacks
                    ExpeditionDrawerContent(
                        expeditions = expeditions,
                        activeId = activeId,
                        onSelect = { id ->
                            scope.launch {
                                expRep.setActiveExpedition(id)
                                currentDominantWindOverlay?.let { mapView.overlays.remove(it) }
                                currentDominantWindOverlay = null
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
                        onSettingsClick = {
                            clearWindRose()
                            startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
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
                        },
                        onClearAllDataClick = { showDeleteWarningDialog = true }
                    )
                }
            ) {
                // here's the mainView, that before was the only
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { _ ->
                        // making it a root layout
                        val root = layoutInflater.inflate(R.layout.layout_map_content, null)

                        // main xml
                        mapView = root.findViewById(R.id.map_view)
                        drawingControls = root.findViewById(R.id.drawing_controls)
                        // only after mapView initializing we can finally do this stuff
                        locationHelper.attachMapView(mapView)
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
                        initHelpers()
                        loadDataFromDB(db, expRep)


                        root
                    }
                )

                // burger button and gps button
                Box(modifier = Modifier.fillMaxSize()) {
                    // bg
                    SmallFloatingActionButton(
                        onClick = { scope.launch { drawerState.open() } },
                        modifier = Modifier.padding(16.dp).align(Alignment.TopStart)
                    ) {
                        Icon(Icons.Default.Menu, contentDescription = null)
                    }
                    //pgs
                    SmallFloatingActionButton(
                        onClick = {
                            locationHelper.checkLocationPermissionAndEnableGps()
                        },
                        containerColor = androidx.compose.ui.graphics.Color(0xFF90EE90), // Твой зеленый цвет
                        contentColor = androidx.compose.ui.graphics.Color.Black,
                        modifier = Modifier
                            .padding(16.dp)
                            .align(Alignment.BottomEnd) // bottom right
                    ) {
                        Icon(
                            imageVector = Icons.Default.GpsFixed,
                            contentDescription = "Показать меня"
                        )
                    }

                }
            }
            // add dialog pop for new expedition
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
                                        currentDominantWindOverlay?.let { mapView.overlays.remove(it) }
                                        currentWindOverlay?.let { mapView.overlays.remove(it) }
                                        expRep.setActiveExpedition(selectedExp)

                                        // cleaning up
                                        withContext(Dispatchers.Main) {
                                            showAddDialog = false
                                            newExpName = ""
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
            // clearing all data
            if (showDeleteWarningDialog) {
                AlertDialog(
                    onDismissRequest = { showDeleteWarningDialog = false },
                    title = { Text("Удалить всё?") },
                    text = { Text("Вы уверены, что хотите полностью очистить базу данных? Все экспедиции, точки, пробы и планы удалятся навсегда.") },
                    confirmButton = {
                        Button(
                            colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFFFF6B6B),
                                contentColor = androidx.compose.ui.graphics.Color.White),
                            onClick = {
                                showDeleteWarningDialog = false
                                lifecycleScope.launch {
                                    try {
                                        // clearing all the data
                                        withContext(Dispatchers.IO) {
                                            db.expeditionDao().clearTableAndResetIndex()
                                            db.sampleDao().clearTableAndResetIndex()
                                            db.plannedPointsDao().clearTableAndResetIndex()
                                            db.sourceDao().clearTableAndResetIndex()
                                        }

                                        // cleaning overlays
                                        currentDominantWindOverlay?.let { mapView.overlays.remove(it) }
                                        currentWindOverlay?.let { mapView.overlays.remove(it) }
                                        currentDominantWindOverlay = null
                                        currentWindOverlay = null

                                        Toast.makeText(
                                            this@MainActivity,
                                            "Все данные успешно удалены!",
                                            Toast.LENGTH_SHORT
                                        ).show()

                                        // resetting activity
                                        this@MainActivity.recreate()

                                    } catch (e: Exception) {
                                        Log.d("CLEARING E", e.message.toString())
                                        Toast.makeText(
                                            this@MainActivity,
                                            "Ошибка очистки: ${e.message}",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            }
                        ) {
                            Text("Да, удалить")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteWarningDialog = false }) {
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
        val screenWidth = dm.widthPixels / dm.density // pure dp
        val screenHeight = dm.heightPixels / dm.density
        // this just determines location of NORTH relatively to rotation of map
        mapNorthCompassOverlay = object: CompassOverlay(this, mapView) {
            override fun draw(c: Canvas?, pProjection: Projection?) {
                drawCompass(c, -mapView.mapOrientation, pProjection?.screenRect)
            }
        }
        val compassIconWidth = 45f
        val marginX = screenWidth - compassIconWidth * 0.9f
        val marginY = screenHeight - compassIconWidth * 2f
        Log.d("лол", "x = ${dm.widthPixels/dm.density} y = ${dm.heightPixels}")

        mapNorthCompassOverlay.setCompassCenter(marginX, marginY)
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

        mapView.controller.setCenter(startGeoPoint)

    }
    private fun saveMapState() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val center = mapView.mapCenter
        val zoom = mapView.zoomLevelDouble

        prefs.edit().apply {
            // SharedPreferences cannot save Float/Double with high efficiancy directly
            // so we parsing it to string
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
        val zoom = prefs.getFloat(KEY_ZOOM, 10f) // default zoom

        if (latStr != null && lonStr != null) {
            val lat = latStr.toDoubleOrNull() ?: 55.015 // default coords
            val lon = lonStr.toDoubleOrNull() ?: 82.9346

            val targetPoint = GeoPoint(lat, lon)
            mapView.controller.setCenter(targetPoint)
            mapView.controller.setZoom(zoom.toDouble())
        } else {
            // default
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
                    flowOf(emptyList())
                } else {
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
                    val marker = markerFactory.createSampleMarker(entity) { m -> openMarkerSheet(m, null, db) }
                    mapView.overlays.add(marker)
                }
                mapView.invalidate()
            }

        }
        // planned points
        lifecycleScope.launch {
            repository.currentExpeditionId.flatMapLatest { expId ->
                if (expId == null) {
                    flowOf(emptyList())
                } else {
                    db.plannedPointsDao().getPointsByExpedition(expId)
                }
            }.collect { plannedPoints ->
                val plannedToRemove = mapView.overlays.filter { overlay ->
                    (overlay as? Marker)?.relatedObject.let { obj ->
                        obj is MarkerData && obj.type == MarkerType.PLANNED
                    }
                }
                mapView.overlays.removeAll(plannedToRemove)

                plannedPoints.forEach { entity ->
                    val marker = markerFactory.createPlannedMarker(entity) { m -> openMarkerSheet(null, m.position, db) }
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
                    // all stuff from before now in SourcDrawingManager
                    val overlay = markerFactory.createSourceOverlay(source) { sourceId -> openSourceSheet(sourceId, db) }
                    if (overlay != null) {
                        mapView.overlays.add(overlay)
                    }
                }
                mapView.invalidate()
            }
        }

    }

    // fun for calculating points based on distance from source
    // now in GeometryUtils

    // import
    // in PlanImporter

    // time for sample entity
    fun getCurrentTime(): String {
        val instant = Instant.ofEpochMilli(System.currentTimeMillis())

        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneOffset.UTC)

        return formatter.format(instant)
        // for example "2026-05-19T12:30:45Z"
    }

    // BOTTOM SHEET'S FUNCs ========================================================================
    // whole redact and delete thing from down side of the screen
    fun openMarkerSheet(marker: Marker?, point: GeoPoint?, db: AppDataBase) {
        // all view logic is in MarkerBottomSheet class
        // here is only executive part

        // if marker passed - update
        // if point passed - add

        val relatedObject = marker?.relatedObject as? MarkerData
        val id: Int? = relatedObject?.id
        val pos = marker?.position ?: point ?: return
        // checking if it is planned point
        val isPlannedPointClick = relatedObject?.type == MarkerType.PLANNED
        val sampleId = if(isPlannedPointClick) null else id

        val sheet = MarkerBottomSheet.newInstance(id, pos.latitude, pos.longitude)

        // save button
        sheet.onSave = { title, desc, smpId, code, newLat, newLon ->
            lifecycleScope.launch {
                val expId = expRep.getOrCreateActiveId()
                Log.d("DB_CHECK", "Attempting to insert sample with ExpID: $expId")
                val entity = SampleEntity(
                    id = id ?: null,
                    expeditionId = expId,
                    samplingId = smpId,
                    lat = newLat,
                    lon = newLon,
                    title = title,
                    description = desc,
                    code = code,
                    createdAt = getCurrentTime()
                )
                db.sampleDao().insertItem(entity)
                // if it was planned point - turning it of on map
                if (isPlannedPointClick && id != null) {
                    db.plannedPointsDao().markAsVisited(id)
                    Log.d("DB_CHECK", "Плановая точка ID: $id успешно отмечена как посещенная")
                }
            }
        }

        // delete button
        sheet.onDelete = {
            if (!isPlannedPointClick) {
                id?.let { lifecycleScope.launch { db.sampleDao().deleteById(it) } }
            }
            else {
                Toast.makeText(this, "Вы не можете удалить невзятую пробу", Toast.LENGTH_SHORT).show()
            }
        }
        sheet.show(supportFragmentManager, "MarkerSheet")
    }

    fun openSourceSheet(id: Int? = null,
                        db: AppDataBase,
                        drawnGeometry: List<GeoPoint>? = null,
                        drawnType: SourceTypeEnum? = null)
    {
        val sheet = SourceBottomSheet.newInstance(id)

        sheet.onSave = { title, desc ->
            lifecycleScope.launch {

                val expId = expRep.getOrCreateActiveId()

                var finalGeometry = if (drawnGeometry != null) ArrayList(drawnGeometry) else ArrayList<GeoPoint>()
                var finalType = drawnType ?: SourceTypeEnum.POINT

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

                if (drawingManager.isDrawingMode) {
                    cancelDrawing()
                }
                Toast.makeText(this@MainActivity, "Сохранено", Toast.LENGTH_SHORT).show()
            }
        }

        sheet.onWindRose = { sourceId, update ->
            lifecycleScope.launch {
                val source = db.sourceDao().findById(sourceId)
                source?.let {
                    // taking a centre point
                    val center = GeoPoint(
                        it.geometry.map { p -> p.latitude }.average(),
                        it.geometry.map { p -> p.longitude }.average()
                    )
                    // we null this shi if we need to update
                    // bcs of that we can use this one call-back for two buttons in sourceBottomSheet
                    if(update) {
                        it.windDataJson = null
                    }

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
                            val monthsToSubtract = settings.getWindPeriod().toLong()
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
        sheet.onImportPlan = { sourceId ->
            targetSourceIdForImport = sourceId
            // Открываем проводник, фильтруя только JSON файлы (или любые текстовые)
            pickJsonFileLauncher.launch("application/json")
        }
        sheet.show(supportFragmentManager, "SourceSheet")
    }

    // DRAWING FUNCs =============================================================================
    // start of drawing the source
    // type - POINT, LINE, AREA
    // firstPoint - c'mon, you got this
    // now in SourceDrawingManager
    private fun finishDrawing() = drawingManager.finishDrawing()
    private fun cancelDrawing() = drawingManager.cancelDrawing()

    // SAMPLE BY LOCATION ==========================================================================
    // now in MapLocationHelper

    // FUNCs FOR USER LOCATION ===============================================================
    // now in MapLocationHelper too

    // =========================================================================================
    // closing all overlays infoWindows with touch
    // i guess its kinda bad idea if i would do sm more complicated
    // UPDATED: in result, i do kinda same mentioned thing
    // it seems okay
    override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
        // adding points in drawing mode
        if (drawingManager.isDrawingMode && p != null) {
            drawingManager.addPoint(p)
            return true
        }
        clearWindRose()
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
                        1 -> locationHelper.checkPermissionAndGetLocation()
                        2 -> drawingManager.startDrawing(SourceTypeEnum.POINT, point)
                        3 -> drawingManager.startDrawing(SourceTypeEnum.LINE, point)
                        4 -> drawingManager.startDrawing(SourceTypeEnum.AREA, point)

                    }
                }
                .show()
        }
        return true
    }


    // wind rose stuff ============================================================================
    private fun showWindRose(center: GeoPoint, stats: List<WindStat>) {
        // clearing current wind rose if it exists
        currentWindOverlay?.let { mapView.overlays.remove(it) }
        currentDominantWindOverlay?.let { mapView.overlays.remove(it) }

        // new layer
        // depends on settings
        // it's either a rose overlay, or just dominant wind arrow
        if(settings.isFullRoseEnabled()) {
            val overlay = WindRoseOverlay(center, stats)
            currentWindOverlay = overlay
            mapView.overlays.add(overlay)
        }
        else {
            val overlay = DominantWindOverlay(center, stats)
            currentDominantWindOverlay = overlay
            mapView.overlays.add(overlay)
        }
        // add update
        mapView.invalidate()
    }

    private fun clearWindRose() {
        if(currentWindOverlay!=null) {
            currentWindOverlay?.let { mapView.overlays.remove(it) }
            currentWindOverlay = null
            //Toast.makeText(this, "Оверлэй вырублен", Toast.LENGTH_SHORT).show()
        }
        if (currentDominantWindOverlay!= null) {
            currentDominantWindOverlay?.let { mapView.overlays.remove(it) }
            currentDominantWindOverlay = null
        }
        mapView.invalidate()
    }

    // defaults =================================================================================

    override fun onPause() {
        super.onPause()
        if (::mapView.isInitialized) {
            mapView.onPause()
        }
        // battery save-wise feature
        if (::locationHelper.isInitialized) {
            locationHelper.disableMyLocation()
        }
        saveMapState()

    }

    override fun onResume() {
        super.onResume()
        if (::mapView.isInitialized) {
            mapView.onResume()
        }
        val newPeriod = settings.getWindPeriod()
        if (newPeriod != currentPeriod) {
            currentPeriod = newPeriod
        }
        // enabling after onPause
        if (::locationHelper.isInitialized) {
            locationHelper.enableMyLocationIfInitialized()
        }
    }
}