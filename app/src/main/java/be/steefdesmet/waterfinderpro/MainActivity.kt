package be.steefdesmet.waterfinderpro

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.location.Location
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().userAgentValue = packageName
        setContent {
            MaterialTheme {
                WaterFinderScreen()
            }
        }
    }
}

data class WaterPlace(
    val id: Long,
    val name: String,
    val type: String,
    val latitude: Double,
    val longitude: Double
)

data class GeocodedPlace(
    val name: String,
    val latitude: Double,
    val longitude: Double
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WaterFinderScreen() {
    val context = LocalContext.current
    var currentLocation by remember { mutableStateOf<Location?>(null) }
    var searchCenter by remember { mutableStateOf<GeoPoint?>(null) }
    var searchCenterName by remember { mutableStateOf("Mijn locatie") }
    var locationText by remember { mutableStateOf("") }
    var radiusKm by remember { mutableStateOf(15f) }
    var isSearching by remember { mutableStateOf(false) }
    var isGeocoding by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("GPS-locatie wordt bepaald…") }
    var selectedPlace by remember { mutableStateOf<WaterPlace?>(null) }
    val waterPlaces = remember { mutableStateListOf<WaterPlace>() }

    fun useGpsLocation(location: Location?) {
        currentLocation = location
        if (location != null && searchCenter == null) {
            searchCenter = GeoPoint(location.latitude, location.longitude)
            searchCenterName = "Mijn locatie"
        }
        message = if (location == null) "Geen GPS-locatie gevonden." else "GPS gevonden"
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            loadLastLocation(context, ::useGpsLocation)
        } else {
            message = "Locatietoestemming is nodig om je huidige positie te tonen."
        }
    }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            loadLastLocation(context, ::useGpsLocation)
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("WaterFinder Pro") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                OsmMap(
                    modifier = Modifier.fillMaxSize(),
                    currentLocation = currentLocation,
                    searchCenter = searchCenter,
                    searchCenterName = searchCenterName,
                    waterPlaces = waterPlaces,
                    onWaterPlaceSelected = { selectedPlace = it }
                )
                Card(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                ) {
                    Text(message, modifier = Modifier.padding(10.dp))
                }
            }

            selectedPlace?.let { place ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Text(place.name, style = MaterialTheme.typography.titleMedium)
                        Text("Type: ${place.type}")
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { openNavigation(context, place) }
                        ) {
                            Text("Breng mij erheen")
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = locationText,
                    onValueChange = { locationText = it },
                    singleLine = true,
                    label = { Text("Plaatsnaam, bv. Mechelen of Lille") }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        modifier = Modifier.weight(1f),
                        enabled = locationText.isNotBlank() && !isGeocoding && !isSearching,
                        onClick = {
                            isGeocoding = true
                            selectedPlace = null
                            message = "Plaats zoeken…"
                            geocodeLocation(locationText.trim()) { place, error ->
                                isGeocoding = false
                                if (place == null) {
                                    message = error ?: "Plaats niet gevonden."
                                } else {
                                    searchCenter = GeoPoint(place.latitude, place.longitude)
                                    searchCenterName = place.name
                                    waterPlaces.clear()
                                    message = "Zoekcentrum: ${place.name}"
                                }
                            }
                        }
                    ) {
                        if (isGeocoding) CircularProgressIndicator() else Text("Gebruik deze plaats")
                    }

                    Button(
                        enabled = currentLocation != null && !isGeocoding && !isSearching,
                        onClick = {
                            currentLocation?.let {
                                searchCenter = GeoPoint(it.latitude, it.longitude)
                                searchCenterName = "Mijn locatie"
                                locationText = ""
                                waterPlaces.clear()
                                selectedPlace = null
                                message = "Zoekcentrum: mijn locatie"
                            }
                        }
                    ) {
                        Text("GPS")
                    }
                }

                Text("Zoekradius: ${radiusKm.roundToInt()} km — vanaf $searchCenterName")
                Slider(
                    value = radiusKm,
                    onValueChange = { radiusKm = it },
                    valueRange = 5f..25f,
                    steps = 19
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${waterPlaces.size} waterplaatsen")
                    Button(
                        enabled = searchCenter != null && !isSearching && !isGeocoding,
                        onClick = {
                            val center = searchCenter ?: return@Button
                            isSearching = true
                            selectedPlace = null
                            message = "Water zoeken rond $searchCenterName…"
                            searchWater(
                                latitude = center.latitude,
                                longitude = center.longitude,
                                radiusMeters = radiusKm.roundToInt() * 1000,
                                onResult = { results, error ->
                                    waterPlaces.clear()
                                    waterPlaces.addAll(results)
                                    isSearching = false
                                    message = error ?: "${results.size} waterplaatsen gevonden rond $searchCenterName"
                                }
                            )
                        }
                    ) {
                        if (isSearching) CircularProgressIndicator() else Text("Zoek water")
                    }
                }
            }
        }
    }
}

@Composable
private fun OsmMap(
    modifier: Modifier,
    currentLocation: Location?,
    searchCenter: GeoPoint?,
    searchCenterName: String,
    waterPlaces: List<WaterPlace>,
    onWaterPlaceSelected: (WaterPlace) -> Unit
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            MapView(context).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                controller.setZoom(12.0)
                controller.setCenter(GeoPoint(51.1167, 2.6833))
            }
        },
        update = { map ->
            map.overlays.clear()

            currentLocation?.let { location ->
                val point = GeoPoint(location.latitude, location.longitude)
                Marker(map).apply {
                    position = point
                    title = "Mijn huidige locatie"
                    icon = createCircleMarker(map.context, Color.RED, 42)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    map.overlays.add(this)
                }
            }

            searchCenter?.let { center ->
                map.controller.setCenter(center)
                if (searchCenterName != "Mijn locatie") {
                    Marker(map).apply {
                        position = center
                        title = "Zoekcentrum: $searchCenterName"
                        icon = createCircleMarker(map.context, Color.rgb(255, 140, 0), 38)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        map.overlays.add(this)
                    }
                }
            }

            waterPlaces.forEach { place ->
                Marker(map).apply {
                    position = GeoPoint(place.latitude, place.longitude)
                    title = place.name
                    snippet = "${place.type} — tik voor navigatie"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    setOnMarkerClickListener { marker, _ ->
                        marker.showInfoWindow()
                        onWaterPlaceSelected(place)
                        true
                    }
                    map.overlays.add(this)
                }
            }
            map.invalidate()
        }
    )
}

private fun createCircleMarker(context: Context, color: Int, sizePx: Int): BitmapDrawable {
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.FILL
    }
    canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx * 0.36f, paint)
    paint.color = Color.WHITE
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = sizePx * 0.10f
    canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx * 0.36f, paint)
    return BitmapDrawable(context.resources, bitmap)
}

private fun openNavigation(context: Context, place: WaterPlace) {
    val googleNavigation = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("google.navigation:q=${place.latitude},${place.longitude}")
    ).apply {
        setPackage("com.google.android.apps.maps")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    try {
        context.startActivity(googleNavigation)
    } catch (_: ActivityNotFoundException) {
        val fallback = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("geo:0,0?q=${place.latitude},${place.longitude}(${Uri.encode(place.name)})")
        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        context.startActivity(fallback)
    }
}

private fun loadLastLocation(context: Context, onResult: (Location?) -> Unit) {
    val fineGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    val coarseGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    if (!fineGranted && !coarseGranted) {
        onResult(null)
        return
    }
    LocationServices.getFusedLocationProviderClient(context)
        .lastLocation
        .addOnSuccessListener(onResult)
        .addOnFailureListener { onResult(null) }
}

private fun geocodeLocation(
    query: String,
    onResult: (GeocodedPlace?, String?) -> Unit
) {
    CoroutineScope(Dispatchers.Main).launch {
        try {
            val place = withContext(Dispatchers.IO) { fetchGeocodedPlace(query) }
            if (place == null) onResult(null, "Plaats niet gevonden. Probeer bijvoorbeeld 'Lille, Frankrijk'.")
            else onResult(place, null)
        } catch (_: Exception) {
            onResult(null, "Plaats zoeken mislukt. Controleer de internetverbinding.")
        }
    }
}

private fun fetchGeocodedPlace(query: String): GeocodedPlace? {
    val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
    val connection = URL("https://nominatim.openstreetmap.org/search?format=jsonv2&limit=1&q=$encoded")
        .openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = 20_000
        connection.readTimeout = 20_000
        connection.setRequestProperty("User-Agent", "WaterFinderPro/0.1.3")
        connection.setRequestProperty("Accept-Language", "nl,en;q=0.8")
        val status = connection.responseCode
        if (status !in 200..299) throw IOException("Nominatim HTTP $status")
        val body = connection.inputStream.bufferedReader().use { it.readText() }
        val results = JSONArray(body)
        if (results.length() == 0) return null
        val item = results.getJSONObject(0)
        return GeocodedPlace(
            name = item.optString("display_name").ifBlank { query },
            latitude = item.getString("lat").toDouble(),
            longitude = item.getString("lon").toDouble()
        )
    } finally {
        connection.disconnect()
    }
}

private fun searchWater(
    latitude: Double,
    longitude: Double,
    radiusMeters: Int,
    onResult: (List<WaterPlace>, String?) -> Unit
) {
    CoroutineScope(Dispatchers.Main).launch {
        try {
            val results = withContext(Dispatchers.IO) {
                fetchWaterPlaces(latitude, longitude, radiusMeters)
            }
            onResult(results, null)
        } catch (exception: Exception) {
            onResult(
                emptyList(),
                if (exception is IOException) {
                    "Zoeken mislukt. Controleer de internetverbinding en probeer opnieuw."
                } else {
                    "Zoeken mislukt. Probeer het later opnieuw."
                }
            )
        }
    }
}

private fun fetchWaterPlaces(
    latitude: Double,
    longitude: Double,
    radiusMeters: Int
): List<WaterPlace> {
    val query = """
        [out:json][timeout:35];
        (
          nwr(around:$radiusMeters,$latitude,$longitude)[natural=water];
          nwr(around:$radiusMeters,$latitude,$longitude)[waterway~"river|canal"];
          nwr(around:$radiusMeters,$latitude,$longitude)[natural=bay];
          nwr(around:$radiusMeters,$latitude,$longitude)[place=sea];
        );
        out center tags 150;
    """.trimIndent()

    val postBody = "data=" + URLEncoder.encode(query, Charsets.UTF_8.name())
    val connection = URL("https://overpass-api.de/api/interpreter")
        .openConnection() as HttpURLConnection

    try {
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.connectTimeout = 40_000
        connection.readTimeout = 40_000
        connection.setRequestProperty("User-Agent", "WaterFinderPro/0.1.3")
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
        connection.outputStream.use { stream ->
            stream.write(postBody.toByteArray(Charsets.UTF_8))
        }

        val status = connection.responseCode
        val responseStream = if (status in 200..299) connection.inputStream else connection.errorStream
        val body = responseStream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (status !in 200..299) throw IOException("Overpass HTTP $status")

        val elements = JSONObject(body).getJSONArray("elements")
        val results = mutableListOf<WaterPlace>()
        for (index in 0 until elements.length()) {
            val element = elements.getJSONObject(index)
            val tags = element.optJSONObject("tags") ?: JSONObject()
            val center = element.optJSONObject("center")
            val lat = if (element.has("lat")) element.optDouble("lat") else center?.optDouble("lat")
            val lon = if (element.has("lon")) element.optDouble("lon") else center?.optDouble("lon")
            if (lat == null || lon == null || lat.isNaN() || lon.isNaN()) continue

            val type = when {
                tags.optString("waterway").isNotBlank() -> tags.optString("waterway")
                tags.optString("water").isNotBlank() -> tags.optString("water")
                tags.optString("natural") == "bay" -> "baai"
                tags.optString("place") == "sea" -> "zee"
                else -> "water"
            }
            results += WaterPlace(
                id = element.optLong("id"),
                name = tags.optString("name").ifBlank { "Naamloze waterpartij" },
                type = type,
                latitude = lat,
                longitude = lon
            )
        }
        return results.distinctBy { it.id }
    } finally {
        connection.disconnect()
    }
}
