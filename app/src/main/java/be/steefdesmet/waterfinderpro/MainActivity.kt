package be.steefdesmet.waterfinderpro

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WaterFinderScreen() {
    val context = LocalContext.current
    var currentLocation by remember { mutableStateOf<Location?>(null) }
    var radiusKm by remember { mutableStateOf(25f) }
    var isSearching by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("GPS-locatie wordt bepaald…") }
    val waterPlaces = remember { mutableStateListOf<WaterPlace>() }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            loadLastLocation(context) { location ->
                currentLocation = location
                message = if (location == null) "Geen GPS-locatie gevonden." else "GPS gevonden"
            }
        } else {
            message = "Locatietoestemming is nodig om water in de omgeving te zoeken."
        }
    }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            loadLastLocation(context) { location ->
                currentLocation = location
                message = if (location == null) "Geen GPS-locatie gevonden." else "GPS gevonden"
            }
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
                    waterPlaces = waterPlaces
                )
                Card(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                ) {
                    Text(message, modifier = Modifier.padding(10.dp))
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Zoekradius: ${radiusKm.roundToInt()} km")
                Slider(
                    value = radiusKm,
                    onValueChange = { radiusKm = it },
                    valueRange = 5f..100f,
                    steps = 18
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${waterPlaces.size} waterplaatsen")
                    Button(
                        enabled = currentLocation != null && !isSearching,
                        onClick = {
                            val location = currentLocation ?: return@Button
                            isSearching = true
                            message = "Water zoeken…"
                            searchWater(
                                latitude = location.latitude,
                                longitude = location.longitude,
                                radiusMeters = radiusKm.roundToInt() * 1000,
                                onResult = { results, error ->
                                    waterPlaces.clear()
                                    waterPlaces.addAll(results)
                                    isSearching = false
                                    message = error ?: "${results.size} waterplaatsen gevonden"
                                }
                            )
                        }
                    ) {
                        if (isSearching) {
                            CircularProgressIndicator()
                        } else {
                            Text("Zoek water")
                        }
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
    waterPlaces: List<WaterPlace>
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
                map.controller.setCenter(point)
                Marker(map).apply {
                    position = point
                    title = "Mijn locatie"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    map.overlays.add(this)
                }
            }
            waterPlaces.forEach { place ->
                Marker(map).apply {
                    position = GeoPoint(place.latitude, place.longitude)
                    title = place.name
                    snippet = place.type
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    map.overlays.add(this)
                }
            }
            map.invalidate()
        }
    )
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

private fun searchWater(
    latitude: Double,
    longitude: Double,
    radiusMeters: Int,
    onResult: (List<WaterPlace>, String?) -> Unit
) {
    kotlinx.coroutines.CoroutineScope(Dispatchers.Main).run {
        kotlinx.coroutines.launch {
            try {
                val results = withContext(Dispatchers.IO) {
                    fetchWaterPlaces(latitude, longitude, radiusMeters)
                }
                onResult(results, null)
            } catch (exception: Exception) {
                onResult(emptyList(), "Zoeken mislukt: ${exception.message ?: "onbekende fout"}")
            }
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
          nwr(around:$radiusMeters,$latitude,$longitude)[waterway~\"river|canal\"];
          nwr(around:$radiusMeters,$latitude,$longitude)[natural=bay];
          nwr(around:$radiusMeters,$latitude,$longitude)[place=sea];
        );
        out center tags 150;
    """.trimIndent()

    val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
    val connection = URL("https://overpass-api.de/api/interpreter?data=$encoded")
        .openConnection() as HttpURLConnection
    connection.connectTimeout = 40_000
    connection.readTimeout = 40_000
    connection.setRequestProperty("User-Agent", "WaterFinderPro/0.1")

    val body = connection.inputStream.bufferedReader().use { it.readText() }
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
}
