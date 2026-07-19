package com.mapconductor.react.maplibre

import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.ComposeView
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReactContext
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.WritableMap
import com.facebook.react.uimanager.UIManagerHelper
import com.facebook.react.uimanager.events.Event
import com.mapconductor.compose.CollectAndRenderOverlays
import com.mapconductor.compose.MapViewScope
import com.mapconductor.compose.circle.LocalCircleCollector
import com.mapconductor.compose.groundimage.LocalGroundImageCollector
import com.mapconductor.compose.info.LocalInfoBubbleCollector
import com.mapconductor.compose.polygon.LocalPolygonCollector
import com.mapconductor.compose.polyline.LocalPolylineCollector
import com.mapconductor.compose.raster.LocalRasterLayerCollector
import com.mapconductor.core.ResourceProvider
import com.mapconductor.core.circle.CircleCapableInterface
import com.mapconductor.core.features.GeoPoint
import com.mapconductor.core.groundimage.GroundImageCapableInterface
import com.mapconductor.core.map.LocalMapOverlayRegistry
import com.mapconductor.core.map.LocalMapServiceRegistry
import com.mapconductor.core.map.LocalMapViewController
import com.mapconductor.core.map.MapCameraPosition
import com.mapconductor.core.map.MapOverlayRegistry
import com.mapconductor.core.map.MutableMapServiceRegistry
import com.mapconductor.core.marker.MarkerOverlay
import com.mapconductor.core.marker.MarkerIconInterface
import com.mapconductor.core.marker.MarkerState
import com.mapconductor.core.marker.MarkerTilingOptions
import com.mapconductor.core.polygon.PolygonCapableInterface
import com.mapconductor.core.polyline.PolylineCapableInterface
import com.mapconductor.core.raster.RasterLayerCapableInterface
import com.mapconductor.maplibre.MapLibreMapViewHolder
import com.mapconductor.maplibre.MapLibreMapViewHolderInterface
import com.mapconductor.maplibre.MapLibreMapViewScope
import com.mapconductor.maplibre.MapLibreViewController
import com.mapconductor.maplibre.createMapLibreViewController
import com.mapconductor.maplibre.toCameraPosition
import com.mapconductor.react.extensions.NativeMapExtensionHostState
import com.mapconductor.react.maplibre.circle.circleStateFromReadableMap
import com.mapconductor.react.maplibre.circle.circleStatesFromReadableArray
import com.mapconductor.react.maplibre.marker.ReactNativeMarkerState
import com.mapconductor.react.maplibre.marker.ReactNativeMarkerIcon
import com.mapconductor.react.maplibre.marker.fromReadableMap
import com.mapconductor.react.maplibre.marker.markerStatesFromBatchReadableMap
import com.mapconductor.react.maplibre.marker.toMarkerIcon
import com.mapconductor.react.maplibre.polyline.polylineStateFromReadableMap
import com.mapconductor.react.maplibre.polyline.polylineStatesFromReadableArray
import com.mapconductor.react.maplibre.polygon.polygonStateFromReadableMap
import com.mapconductor.react.maplibre.polygon.polygonStatesFromReadableArray
import com.mapconductor.react.marker.MarkerScaleBridge
import com.mapconductor.react.groundimage.groundImageStateFromReadableMap
import com.mapconductor.react.groundimage.groundImageStatesFromReadableArray
import com.mapconductor.react.raster.rasterLayerStateFromReadableMap
import com.mapconductor.react.raster.rasterLayerStatesFromReadableArray
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import com.mapconductor.maplibre.MapLibreDesign as ComposeMapLibreDesign

private data class MapLibreWrapperInfoBubblePosition(
    val id: String,
    val point: GeoPoint,
)

class MapLibreMapViewWrapper(context: Context) :
    FrameLayout(context) {

    companion object {
        // Shared across all wrapper instances, one background thread. ReadableArray/ReadableMap
        // parsing and marker-icon decoding (JNI + bitmap I/O) happen here instead of the UI
        // thread, so a large compositionMarkers() batch (e.g. 20k+ markers) doesn't freeze the
        // map screen while it loads. Single-threaded so that commits from overlapping
        // compositionMarkers/updateMarker/clearOverlays calls on the same view are applied to
        // `markerStates` in the order React Native issued them.
        private val markerIngestDispatcher: CoroutineDispatcher =
            Executors.newSingleThreadExecutor { r ->
                Thread(r, "MapLibreMarkerIngest").apply { isDaemon = true }
            }.asCoroutineDispatcher()
    }

    private val mainCoroutine: CoroutineScope = CoroutineScope(Dispatchers.Main)
    private val markerCoroutine: CoroutineScope = CoroutineScope(markerIngestDispatcher)
    private val extensionComposeView = ComposeView(context)
    private val extensionScope = MapLibreMapViewScope()
    private val extensionRegistry =
        MapOverlayRegistry().apply {
            extensionScope
                .buildRegistry()
                .getAll()
                .filterNot { it is MarkerOverlay }
                .forEach(::register)
        }
    private val extensionServiceRegistry = MutableMapServiceRegistry()
    private var mapView: MapView? = null
    private var mapHolder: MapLibreMapViewHolderInterface? = null

    // Read from the main thread (camera/lifecycle callbacks) and from markerCoroutine's
    // background thread (compositionMarkers/updateMarker). Plain `var` gives the JVM no
    // happens-before edge between onDropViewInstance()'s write and a marker-coroutine read,
    // so the background thread can observe a stale non-null reference to an already
    // torn-down controller under GC pressure; @Volatile forces the write to be visible
    // as soon as it happens instead of at some unspecified later point.
    @Volatile
    private var mapController: MapLibreViewController? = null
    private var initialized = false
    private var pendingCameraPosition = MapCameraPosition.Default
    private var pendingMapDesign = ComposeMapLibreDesign.DemoTiles
    private var markerStates: Map<String, MarkerState> = emptyMap()
    private var markerCompositionGeneration: Int? = null
    private val markerCompositionBuffer = mutableMapOf<String, MarkerState>()
    private var markerCompositionIcons: List<MarkerIconInterface?> = emptyList()
    private var rasterLayerStates: Map<String, com.mapconductor.core.raster.RasterLayerState> = emptyMap()
    private var groundImageStates: Map<String, com.mapconductor.core.groundimage.GroundImageState> = emptyMap()
    private var markerTilingOptions = MarkerTilingOptions.Default
    private var infoBubblePositions: List<MapLibreWrapperInfoBubblePosition> = emptyList()

    // Camera listeners fire on every frame during pan/zoom. When there is nothing to
    // report (marker tiling active, no markers, no open info bubbles), emitting an empty
    // positions payload every frame floods the bridge and forces a JS setState per frame,
    // so an empty payload is emitted once as a clearing event and then suppressed until
    // there is data again. Both flags are only touched on the main thread.
    private var emittedEmptyMarkerScreenPositions = false
    private var emittedEmptyInfoBubbleScreenPositions = false
    private val nativeMapExtensionHost =
        NativeMapExtensionHostState(context) { extensionId, eventName, payload ->
            emit(
                "topNativeMapExtensionEvent",
                Arguments.createMap().apply {
                    putString("extensionId", extensionId)
                    putString("eventName", eventName)
                    putMap("payload", payload)
                },
            )
        }

    init {
        markerTrace("wrapper init")
        ResourceProvider.init(context)

        extensionComposeView.isClickable = false
        extensionComposeView.isFocusable = false
        addView(
            extensionComposeView,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
            )
        )
    }

    fun initializeMapIfNeeded() {
        if (initialized) return
        initialized = true
        MapLibre.getInstance(context)

        val nativeMapView =
            MapView(
                context,
                MapLibreMapOptions
                    .createFromAttributes(context)
                    .camera(pendingCameraPosition.toCameraPosition())
                    .textureMode(true),
            )
        mapView = nativeMapView
        addView(
            nativeMapView,
            0,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )

        nativeMapView.getMapAsync { map ->
            map.setStyle(pendingMapDesign.styleJsonURL) {
                if (!initialized) return@setStyle
                val holder = MapLibreMapViewHolder(nativeMapView, map)
                val controller =
                    createMapLibreViewController(
                        holder = holder,
                        markerTiling = markerTilingOptions,
                        serviceRegistry = extensionServiceRegistry,
                    )
                mapHolder = holder
                mapController = controller
                configureController(controller)
                extensionComposeView.setContent {
                    RenderNativeExtensions(
                        scope = extensionScope,
                        registry = extensionRegistry,
                        controller = controller,
                        serviceRegistry = extensionServiceRegistry,
                        host = nativeMapExtensionHost,
                    )
                }
                markerCoroutine.launch {
                    runMarkerControllerCall { controller.compositionMarkers(markerStates.values.toList()) }
                }
                markerTrace("SDK onMapLoaded callback")
                emit("topMapLoaded", Arguments.createMap())
                emitMarkerScreenPositions()
                emitInfoBubbleScreenPositions()
                nativeMapView.post { controller.sendInitialCameraUpdate() }
            }
        }
    }

    fun setCameraPosition(cameraPosition: ReadableMap?) {
        pendingCameraPosition = MapCameraPosition.fromReadableMap(cameraPosition)
        mapController?.moveCamera(pendingCameraPosition)
    }

    fun setMapDesignType(mapDesignType: String?) {
        val styleUrl = MapLibreDesign.styleUrlFrom(mapDesignType)
        pendingMapDesign = ComposeMapLibreDesign(id = styleUrl, styleJsonURL = styleUrl)
        mapController?.setMapDesignType(pendingMapDesign)
    }

    fun moveCamera(cameraPosition: ReadableMap?) {
        setCameraPosition(cameraPosition)
    }

    fun animateCamera(
        cameraPosition: ReadableMap?,
        durationMillis: Int,
    ) {
        pendingCameraPosition = MapCameraPosition.fromReadableMap(cameraPosition)
        mapController?.animateCamera(pendingCameraPosition, durationMillis.toLong())
    }

    fun fitBounds(
        bounds: ReadableMap?,
        padding: Int,
    ) {
        mapController?.fitBounds(geoRectBoundsFromReadableMap(bounds), padding)
    }

    fun setInfoBubblePositions(positions: ReadableArray?) {
        infoBubblePositions =
            (0 until (positions?.size() ?: 0)).mapNotNull { index ->
                val position = positions?.getMap(index) ?: return@mapNotNull null
                val id =
                    if (position.hasKey("id") && !position.isNull("id")) {
                        position.getString("id")
                    } else {
                        null
                    } ?: return@mapNotNull null
                val point = GeoPoint.fromReadableMap(position) ?: return@mapNotNull null
                MapLibreWrapperInfoBubblePosition(id = id, point = point)
            }
        emitInfoBubbleScreenPositions()
    }

    fun setMarkerTilingOptions(options: ReadableMap?) {
        markerTilingOptions = markerTilingOptionsFromReadableMap(options, viewId = id)
        MarkerScaleBridge.invalidate(id)
    }

    private fun configureController(controller: MapLibreViewController) {
        controller.setCameraMoveStartListener { camera ->
            pendingCameraPosition = camera
            emitCameraEvent("topCameraMoveStart", camera)
            emitMarkerScreenPositions()
            emitInfoBubbleScreenPositions()
        }
        controller.setCameraMoveListener { camera ->
            pendingCameraPosition = camera
            emitCameraEvent("topCameraMove", camera)
            emitMarkerScreenPositions()
            emitInfoBubbleScreenPositions()
        }
        controller.setCameraMoveEndListener { camera ->
            pendingCameraPosition = camera
            emitCameraEvent("topCameraMoveEnd", camera)
            emitMarkerScreenPositions()
            emitInfoBubbleScreenPositions()
        }
        controller.setMapClickListener {
            if (!nativeMapExtensionHost.dispatchMapClick(it, pendingCameraPosition.zoom)) {
                emitPointEvent("topMapClick", it)
            }
        }
        controller.setMapLongClickListener { emitPointEvent("topMapLongClick", it) }
    }

    fun clearOverlays() {
        // Routed through markerCoroutine so it's ordered against any in-flight
        // compositionMarkers/updateMarker call on the same queue.
        markerCoroutine.launch {
            markerStates = emptyMap()
            runMarkerControllerCall { mapController?.compositionMarkers(emptyList()) }
            withContext(Dispatchers.Main) {
                mapController?.compositionPolygons(emptyList())
                mapController?.compositionPolylines(emptyList())
                mapController?.compositionCircles(emptyList())
                val groundImageIds = groundImageStates.keys
                groundImageStates = emptyMap()
                extensionScope.groundImageCollector.flow.value =
                    extensionScope.groundImageCollector.flow.value
                        .filterKeys { id -> id !in groundImageIds }
                        .toMutableMap()
                val rasterLayerIds = rasterLayerStates.keys
                rasterLayerStates = emptyMap()
                extensionScope.rasterLayerCollector.flow.value =
                    extensionScope.rasterLayerCollector.flow.value
                        .filterKeys { id -> id !in rasterLayerIds }
                        .toMutableMap()
                infoBubblePositions = emptyList()
                emitMarkerScreenPositions()
                emitInfoBubbleScreenPositions()
            }
        }
    }

    fun compositionMarkers(payload: ReadableMap?) {
        markerCoroutine.launch {
            val previousStates = markerStates
            val nextStates =
                markerStatesFromBatchReadableMap(payload)
                    .associate { it.id to it.toCoreMarkerState(previousStates[it.id]) }
            markerStates = nextStates
            runMarkerControllerCall { mapController?.compositionMarkers(nextStates.values.toList()) }
            withContext(Dispatchers.Main) {
                emitMarkerScreenPositions()
                emitInfoBubbleScreenPositions()
            }
        }
    }

    fun beginMarkerComposition(
        generation: Int,
        iconDictionary: ReadableArray?,
    ) {
        markerTrace("begin received generation=$generation icons=${iconDictionary?.size() ?: 0}")
        markerCoroutine.launch {
            markerTrace("begin executing generation=$generation")
            markerCompositionGeneration = generation
            markerCompositionBuffer.clear()
            markerCompositionIcons =
                if (iconDictionary == null) {
                    emptyList()
                } else {
                    (0 until iconDictionary.size()).map { index ->
                        ReactNativeMarkerIcon.fromReadableMap(iconDictionary.getMap(index))?.toMarkerIcon(context)
                    }
                }
        }
    }

    fun appendMarkerComposition(
        generation: Int,
        sequence: Int,
        payload: ReadableMap?,
    ) {
        val count = payload?.getArray("ids")?.size() ?: 0
        markerTrace("append received generation=$generation sequence=$sequence count=$count")
        markerCoroutine.launch {
            val startedAt = SystemClock.elapsedRealtime()
            if (markerCompositionGeneration != generation) {
                markerTrace("append ignored generation=$generation sequence=$sequence current=$markerCompositionGeneration")
                return@launch
            }
            markerStatesFromBatchReadableMap(payload, markerCompositionIcons).forEach { state ->
                markerCompositionBuffer[state.id] = state.toCoreMarkerState(null)
            }
            markerTrace(
                "append decoded generation=$generation sequence=$sequence count=$count " +
                    "buffer=${markerCompositionBuffer.size} elapsedMs=${SystemClock.elapsedRealtime() - startedAt}",
            )
            withContext(Dispatchers.Main) {
                markerTrace("append ACK emit generation=$generation sequence=$sequence")
                emitMarkerCompositionBatchProcessed(generation, sequence)
            }
        }
    }

    fun commitMarkerComposition(generation: Int) {
        markerTrace("commit received generation=$generation")
        markerCoroutine.launch {
            if (markerCompositionGeneration != generation) {
                markerTrace("commit ignored generation=$generation current=$markerCompositionGeneration")
                return@launch
            }
            val nextStates = markerCompositionBuffer.toMap()
            markerCompositionBuffer.clear()
            markerCompositionIcons = emptyList()
            markerCompositionGeneration = null
            val startedAt = SystemClock.elapsedRealtime()
            markerTrace("commit controller assignment start generation=$generation count=${nextStates.size}")
            markerStates = nextStates
            runMarkerControllerCall { mapController?.compositionMarkers(nextStates.values.toList()) }
            markerTrace(
                "commit controller assignment end generation=$generation count=${nextStates.size} " +
                    "elapsedMs=${SystemClock.elapsedRealtime() - startedAt}",
            )
            withContext(Dispatchers.Main) {
                emitMarkerScreenPositions()
                emitInfoBubbleScreenPositions()
            }
        }
    }

    fun updateMarker(marker: ReadableMap?) {
        markerCoroutine.launch {
            val previousStates = markerStates
            val state = ReactNativeMarkerState.fromReadableMap(marker) ?: return@launch
            val next = state.toCoreMarkerState(previousStates[state.id])
            markerStates = markerStates + (state.id to next)
            state.animation?.let(next::animate)
            runMarkerControllerCall { mapController?.updateMarker(next) }
            withContext(Dispatchers.Main) {
                emitMarkerScreenPositions()
                emitInfoBubbleScreenPositions()
            }
        }
    }

    fun compositionPolylines(polylines: ReadableArray?) {
        val states = polylineStatesFromReadableArray(polylines, ::emitPolylineClick)
        mainCoroutine.launch {
            mapController?.compositionPolylines(states)
        }
    }

    fun compositionCircles(circles: ReadableArray?) {
        val states = circleStatesFromReadableArray(circles, ::emitCircleClick)
        mainCoroutine.launch {
            mapController?.compositionCircles(states)
        }
    }

    fun updateCircle(circle: ReadableMap?) {
        val state = circleStateFromReadableMap(circle, ::emitCircleClick) ?: return
        mainCoroutine.launch {
            mapController?.updateCircle(state)
        }
    }

    fun compositionPolygons(polygons: ReadableArray?) {
        val states = polygonStatesFromReadableArray(polygons, ::emitPolygonClick)
        mainCoroutine.launch {
            mapController?.compositionPolygons(states)
        }
    }

    fun updatePolygon(polygon: ReadableMap?) {
        val state = polygonStateFromReadableMap(polygon, ::emitPolygonClick) ?: return
        mainCoroutine.launch {
            mapController?.updatePolygon(state)
        }
    }

    fun updatePolyline(polyline: ReadableMap?) {
        val state = polylineStateFromReadableMap(polyline, ::emitPolylineClick) ?: return
        mainCoroutine.launch {
            mapController?.updatePolyline(state)
        }
    }

    fun compositionRasterLayers(layers: ReadableArray?) {
        val states = rasterLayerStatesFromReadableArray(layers)
        val previousIds = rasterLayerStates.keys
        rasterLayerStates = states.associateBy { it.id }
        val extensionLayers =
            extensionScope.rasterLayerCollector.flow.value.filterKeys { id -> id !in previousIds }
        extensionScope.rasterLayerCollector.flow.value =
            (extensionLayers + rasterLayerStates).toMutableMap()
    }

    fun compositionGroundImages(images: ReadableArray?) {
        val states = groundImageStatesFromReadableArray(images, context, ::emitGroundImageClick)
        val previousIds = groundImageStates.keys
        groundImageStates = states.associateBy { it.id }
        val extensionImages =
            extensionScope.groundImageCollector.flow.value.filterKeys { id -> id !in previousIds }
        extensionScope.groundImageCollector.flow.value =
            (extensionImages + groundImageStates).toMutableMap()
    }

    fun updateGroundImage(image: ReadableMap?) {
        val state = groundImageStateFromReadableMap(image, context, ::emitGroundImageClick) ?: return
        groundImageStates = groundImageStates + (state.id to state)
        extensionScope.groundImageCollector.flow.value =
            extensionScope.groundImageCollector.flow.value
                .toMutableMap()
                .apply { put(state.id, state) }
    }

    fun updateRasterLayer(layer: ReadableMap?) {
        val state = rasterLayerStateFromReadableMap(layer) ?: return
        rasterLayerStates = rasterLayerStates + (state.id to state)
        extensionScope.rasterLayerCollector.flow.value =
            extensionScope.rasterLayerCollector.flow.value
                .toMutableMap()
                .apply { put(state.id, state) }
    }

    fun upsertNativeMapExtension(
        extensionId: String,
        type: String,
        payload: ReadableMap?,
    ) {
        nativeMapExtensionHost.upsert(extensionId, type, payload)
    }

    fun removeNativeMapExtension(extensionId: String) {
        nativeMapExtensionHost.remove(extensionId)
    }

    fun onDropViewInstance() {
        markerTrace("wrapper drop")
        initialized = false
        MarkerScaleBridge.invalidate(id)
        nativeMapExtensionHost.clear()
        extensionComposeView.disposeComposition()
        // Null the field before destroying: a marker-coroutine job that reads mapController
        // after this point sees null and no-ops, instead of getting a reference to a
        // controller whose MarkerManager is about to be (or just was) destroyed.
        val controller = mapController
        mapController = null
        mapHolder = null
        controller?.destroy()
        mapView?.onDestroy()
        mapView = null
        markerCoroutine.cancel()
        mainCoroutine.cancel()
    }

    override fun onLayout(
        changed: Boolean,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ) {
        super.onLayout(changed, left, top, right, bottom)
        mapView?.layout(0, 0, right - left, bottom - top)
        extensionComposeView.layout(0, 0, right - left, bottom - top)
        emitMarkerScreenPositions()
        emitInfoBubbleScreenPositions()
    }

    private fun emitCameraEvent(
        eventName: String,
        camera: MapCameraPosition,
    ) {
        emit(eventName, Arguments.createMap().apply { putMap("cameraPosition", camera.toWritableMap()) })
    }

    private fun emitPolylineClick(
        id: String,
        event: com.mapconductor.core.polyline.PolylineEvent,
    ) {
        emit(
            "topPolylineClick",
            Arguments.createMap().apply {
                putString("polylineId", id)
                putMap("point", GeoPoint.from(event.clicked).toWritableMap())
            },
        )
    }

    private fun emitCircleClick(
        id: String,
        event: com.mapconductor.core.circle.CircleEvent,
    ) {
        emit(
            "topCircleClick",
            Arguments.createMap().apply {
                putString("circleId", id)
                putMap("point", GeoPoint.from(event.clicked).toWritableMap())
            },
        )
    }

    private fun emitPolygonClick(
        id: String,
        event: com.mapconductor.core.polygon.PolygonEvent,
    ) {
        emit(
            "topPolygonClick",
            Arguments.createMap().apply {
                putString("polygonId", id)
                putMap("point", GeoPoint.from(event.clicked).toWritableMap())
            },
        )
    }

    private fun emitGroundImageClick(
        id: String,
        event: com.mapconductor.core.groundimage.GroundImageEvent,
    ) {
        val clicked = event.clicked ?: return
        emit(
            "topGroundImageClick",
            Arguments.createMap().apply {
                putString("groundImageId", id)
                putMap("point", GeoPoint.from(clicked).toWritableMap())
            },
        )
    }

    private fun emit(
        eventName: String,
        event: WritableMap,
    ) {
        val reactContext = context as? ReactContext ?: return
        val surfaceId = UIManagerHelper.getSurfaceId(this)
        UIManagerHelper.getEventDispatcher(reactContext)
            ?.dispatchEvent(MapLibreMapViewWrapperEvent(surfaceId, id, eventName, event))
    }

    private fun emitMarkerCompositionBatchProcessed(
        generation: Int,
        sequence: Int,
    ) {
        emit(
            "topMarkerCompositionBatchProcessed",
            Arguments.createMap().apply {
                putInt("generation", generation)
                putInt("sequence", sequence)
            },
        )
    }

    private fun emitPointEvent(
        eventName: String,
        point: GeoPoint,
    ) {
        emit(
            eventName,
            Arguments.createMap().apply {
                putMap(
                    "point",
                    Arguments.createMap().apply {
                        putDouble("latitude", point.latitude)
                        putDouble("longitude", point.longitude)
                    },
                )
            },
        )
    }

    private fun markerTrace(message: String) {
        Log.d(
            MARKER_TRACE_TAG,
            "[MapLibre][RN][t=${SystemClock.elapsedRealtime()}]" +
                "[thread=${Thread.currentThread().name}] $message",
        )
    }

    /**
     * Marker composition/update work runs on markerCoroutine's background thread, which can
     * still have a command in flight when onDropViewInstance() destroys the controller's
     * MarkerManager on the main thread (the view is gone, but a stale in-flight update isn't
     * an error worth crashing the app over). Swallow only that specific race; anything else,
     * including cancellation, propagates normally.
     */
    private suspend fun runMarkerControllerCall(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalStateException) {
            markerTrace("marker controller call skipped after teardown: ${e.message}")
        }
    }

    private fun emitMarkerScreenPositions() {
        val tilingActive = markerStates.size >= markerTilingOptions.minMarkerCount
        if (tilingActive || markerStates.isEmpty()) {
            if (emittedEmptyMarkerScreenPositions) return
            emittedEmptyMarkerScreenPositions = true
            mainCoroutine.launch {
                emit(
                    "topMarkerScreenPositions",
                    Arguments.createMap().apply { putArray("positions", Arguments.createArray()) },
                )
            }
            return
        }
        emittedEmptyMarkerScreenPositions = false
        mainCoroutine.launch {
            val density = ResourceProvider.getDensity()
            val holder = mapHolder ?: return@launch
            val array =
                Arguments.createArray().apply {
                    markerStates.values.forEach { marker ->
                        val offset = holder.toScreenOffset(marker.position) ?: return@forEach
                        pushMap(
                            Arguments.createMap().apply {
                                putString("markerId", marker.id)
                                putDouble("x", offset.x.toDouble() / density)
                                putDouble("y", offset.y.toDouble() / density)
                            },
                        )
                    }
                }
            emit("topMarkerScreenPositions", Arguments.createMap().apply { putArray("positions", array) })
        }
    }

    private fun emitInfoBubbleScreenPositions() {
        if (infoBubblePositions.isEmpty()) {
            if (emittedEmptyInfoBubbleScreenPositions) return
            emittedEmptyInfoBubbleScreenPositions = true
            mainCoroutine.launch {
                emit(
                    "topInfoBubbleScreenPositions",
                    Arguments.createMap().apply { putArray("positions", Arguments.createArray()) },
                )
            }
            return
        }
        emittedEmptyInfoBubbleScreenPositions = false
        mainCoroutine.launch {
            val density = ResourceProvider.getDensity()
            val holder = mapHolder ?: return@launch
            val array =
                Arguments.createArray().apply {
                    infoBubblePositions.forEach { position ->
                        val offset = holder.toScreenOffset(position.point) ?: return@forEach
                        pushMap(
                            Arguments.createMap().apply {
                                putString("id", position.id)
                                putDouble("x", offset.x.toDouble() / density)
                                putDouble("y", offset.y.toDouble() / density)
                            },
                        )
                    }
                }
            emit("topInfoBubbleScreenPositions", Arguments.createMap().apply { putArray("positions", array) })
        }
    }

    private fun ReactNativeMarkerState.toCoreMarkerState(previous: MarkerState?): MarkerState {
        val resolvedIcon = this.resolvedIcon ?: icon?.toMarkerIcon(context)
        val next =
            previous ?: MarkerState(
                id = id,
                position = position,
                clickable = clickable,
                draggable = draggable,
                zIndex = zIndex?.toInt(),
                icon = resolvedIcon,
                onClick = {
                    emit("topMarkerClick", Arguments.createMap().apply { putString("markerId", id) })
                },
            )

        next.position = position
        next.clickable = clickable
        next.draggable = draggable
        next.zIndex = zIndex?.toInt()
        next.icon = resolvedIcon
        next.onClick =
            if (clickable) {
                {
                    emit("topMarkerClick", Arguments.createMap().apply { putString("markerId", id) })
                }
            } else {
                null
            }
        next.onDragStart = { emitMarkerDrag("topMarkerDragStart", it) }
        next.onDrag = { emitMarkerDrag("topMarkerDrag", it) }
        next.onDragEnd = { emitMarkerDrag("topMarkerDragEnd", it) }
        next.onAnimateStart = { emitMarkerAnimate("topMarkerAnimateStart", it) }
        next.onAnimateEnd = { emitMarkerAnimate("topMarkerAnimateEnd", it) }
        return next
    }

    private fun emitMarkerDrag(
        eventName: String,
        state: MarkerState,
    ) {
        emit(
            eventName,
            Arguments.createMap().apply {
                putString("markerId", state.id)
                putMap("point", GeoPoint.from(state.position).toWritableMap())
            },
        )
    }

    private fun emitMarkerAnimate(
        eventName: String,
        state: MarkerState,
    ) {
        emit(
            eventName,
            Arguments.createMap().apply {
                putString("markerId", state.id)
            },
        )
    }
}

@Composable
private fun RenderNativeExtensions(
    scope: MapViewScope,
    registry: MapOverlayRegistry,
    controller: MapLibreViewController,
    serviceRegistry: MutableMapServiceRegistry,
    host: NativeMapExtensionHostState,
) {
    DisposableEffect(controller) {
        scope.groundImageCollector.setUpdateHandler { state ->
            (controller as GroundImageCapableInterface).let { capable ->
                if (capable.hasGroundImage(state)) capable.updateGroundImage(state)
            }
        }
        scope.rasterLayerCollector.setUpdateHandler { state ->
            (controller as RasterLayerCapableInterface).let { capable ->
                if (capable.hasRasterLayer(state)) capable.updateRasterLayer(state)
            }
        }
        scope.polygonCollector.setUpdateHandler { state ->
            (controller as PolygonCapableInterface).let { capable ->
                if (capable.hasPolygon(state)) capable.updatePolygon(state)
            }
        }
        scope.polylineCollector.setUpdateHandler { state ->
            (controller as PolylineCapableInterface).let { capable ->
                if (capable.hasPolyline(state)) capable.updatePolyline(state)
            }
        }
        scope.circleCollector.setUpdateHandler { state ->
            (controller as CircleCapableInterface).let { capable ->
                if (capable.hasCircle(state)) capable.updateCircle(state)
            }
        }
        onDispose {
            scope.groundImageCollector.setUpdateHandler(null)
            scope.rasterLayerCollector.setUpdateHandler(null)
            scope.polygonCollector.setUpdateHandler(null)
            scope.polylineCollector.setUpdateHandler(null)
            scope.circleCollector.setUpdateHandler(null)
        }
    }

    CollectAndRenderOverlays(
        registry = registry,
        controller = controller,
    )
    CompositionLocalProvider(
        LocalMapOverlayRegistry provides registry,
        LocalMapServiceRegistry provides serviceRegistry,
        LocalMapViewController provides controller,
        LocalInfoBubbleCollector provides scope.bubbleFlow,
        LocalCircleCollector provides scope.circleCollector,
        LocalPolylineCollector provides scope.polylineCollector,
        LocalPolygonCollector provides scope.polygonCollector,
        LocalGroundImageCollector provides scope.groundImageCollector,
        LocalRasterLayerCollector provides scope.rasterLayerCollector,
    ) {
        with(scope) {
            with(host) { RenderExtensions() }
        }
    }
}

private const val MARKER_TRACE_TAG = "MCMarkerTrace"

private fun markerTilingOptionsFromReadableMap(map: ReadableMap?, viewId: Int): MarkerTilingOptions {
    if (map == null) return MarkerTilingOptions.Default
    val hasIconScaleCallback = map.getBooleanOrNull("hasIconScaleCallback") ?: false
    android.util.Log.d(
        "MarkerScaleBridge",
        "MapLibre markerTilingOptionsFromReadableMap viewId=$viewId hasIconScaleCallback=$hasIconScaleCallback map=$map",
    )
    return MarkerTilingOptions.Default.copy(
        enabled = map.getBooleanOrNull("enabled") ?: MarkerTilingOptions.Default.enabled,
        debugTileOverlay = map.getBooleanOrNull("debugTileOverlay")
            ?: MarkerTilingOptions.Default.debugTileOverlay,
        minMarkerCount = map.getIntOrNull("minMarkerCount") ?: MarkerTilingOptions.Default.minMarkerCount,
        cacheSize = map.getIntOrNull("cacheSize") ?: MarkerTilingOptions.Default.cacheSize,
        iconScaleCallback =
            if (hasIconScaleCallback) {
                { state: MarkerState, zoom: Int -> MarkerScaleBridge.requestScale(viewId, state.id, zoom) }
            } else {
                null
            },
    )
}

private class MapLibreMapViewWrapperEvent(
    surfaceId: Int,
    viewTag: Int,
    private val name: String,
    private val payload: WritableMap,
) : Event<MapLibreMapViewWrapperEvent>(surfaceId, viewTag) {
    override fun getEventName(): String = name

    override fun canCoalesce(): Boolean = false

    override fun getEventData(): WritableMap = payload
}
