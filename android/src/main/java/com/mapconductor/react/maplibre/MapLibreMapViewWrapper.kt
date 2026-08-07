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
import com.mapconductor.react.wrapper.MapViewWrapperEventEmitter
import com.mapconductor.react.wrapper.MapViewWrapperScreenPositions
import com.mapconductor.react.wrapper.WrapperInfoBubblePosition
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
import com.mapconductor.react.maplibre.polyline.polylineStateFromReadableMap
import com.mapconductor.react.maplibre.polyline.polylineStatesFromReadableArray
import com.mapconductor.react.maplibre.polygon.polygonStateFromReadableMap
import com.mapconductor.react.maplibre.polygon.polygonStatesFromReadableArray
import com.mapconductor.react.marker.MarkerScaleBridge
import com.mapconductor.react.marker.applyNativeMarkerUpdate
import com.mapconductor.react.marker.decodeNativeMarkerBatch
import com.mapconductor.react.marker.decodeNativeMarkerIcon
import com.mapconductor.react.marker.decodeNativeMarkerState
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
    private var infoBubblePositions: List<WrapperInfoBubblePosition> = emptyList()

    private val events = MapViewWrapperEventEmitter(this)
    private val screenPositions = MapViewWrapperScreenPositions(events, mainCoroutine)

    private val nativeMapExtensionHost =
        NativeMapExtensionHostState(context) { extensionId, eventName, payload ->
            events.emit(
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
                events.emit("topMapLoaded", Arguments.createMap())
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
        infoBubblePositions = MapViewWrapperScreenPositions.parseInfoBubblePositions(positions)
        emitInfoBubbleScreenPositions()
    }

    fun setMarkerTilingOptions(options: ReadableMap?) {
        markerTilingOptions = markerTilingOptionsFromReadableMap(options, viewId = id)
        MarkerScaleBridge.invalidate(id)
    }

    private fun configureController(controller: MapLibreViewController) {
        controller.setCameraMoveStartListener { camera ->
            pendingCameraPosition = camera
            events.emitCameraEvent("topCameraMoveStart", camera.toWritableMap())
            emitMarkerScreenPositions()
            emitInfoBubbleScreenPositions()
        }
        controller.setCameraMoveListener { camera ->
            pendingCameraPosition = camera
            events.emitCameraEvent("topCameraMove", camera.toWritableMap())
            emitMarkerScreenPositions()
            emitInfoBubbleScreenPositions()
        }
        controller.setCameraMoveEndListener { camera ->
            pendingCameraPosition = camera
            events.emitCameraEvent("topCameraMoveEnd", camera.toWritableMap())
            emitMarkerScreenPositions()
            emitInfoBubbleScreenPositions()
        }
        controller.setMapClickListener {
            if (!nativeMapExtensionHost.dispatchMapClick(it, pendingCameraPosition.zoom)) {
                events.emitPointEvent("topMapClick", it)
            }
        }
        controller.setMapLongClickListener { events.emitPointEvent("topMapLongClick", it) }
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
                decodeNativeMarkerBatch(
                    payload = payload,
                    context = context,
                    previousStates = previousStates,
                    onMarkerEvent = events::handleMarkerEvent,
                ).associateBy { it.id }
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
                        decodeNativeMarkerIcon(iconDictionary.getMap(index), context)
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
            decodeNativeMarkerBatch(
                payload = payload,
                context = context,
                sharedIcons = markerCompositionIcons,
                onMarkerEvent = events::handleMarkerEvent,
            ).forEach { state ->
                markerCompositionBuffer[state.id] = state
            }
            markerTrace(
                "append decoded generation=$generation sequence=$sequence count=$count " +
                    "buffer=${markerCompositionBuffer.size} elapsedMs=${SystemClock.elapsedRealtime() - startedAt}",
            )
            withContext(Dispatchers.Main) {
                markerTrace("append ACK emit generation=$generation sequence=$sequence")
                events.emitMarkerCompositionBatchProcessed(generation, sequence)
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
        if (marker == null) return
        markerCoroutine.launch {
            val id = if (marker.hasKey("id") && !marker.isNull("id")) marker.getString("id") else null
            val previousStates = markerStates
            val next =
                (id?.let(previousStates::get)?.also { existing -> applyNativeMarkerUpdate(marker, context, existing) }
                    ?: decodeNativeMarkerState(marker, context, events::handleMarkerEvent))
                    ?: return@launch
            markerStates = markerStates + (next.id to next)
            runMarkerControllerCall { mapController?.updateMarker(next) }
            withContext(Dispatchers.Main) {
                emitMarkerScreenPositions()
                emitInfoBubbleScreenPositions()
            }
        }
    }

    fun compositionPolylines(polylines: ReadableArray?) {
        val states = polylineStatesFromReadableArray(polylines, events::emitPolylineClick)
        mainCoroutine.launch {
            mapController?.compositionPolylines(states)
        }
    }

    fun compositionCircles(circles: ReadableArray?) {
        val states = circleStatesFromReadableArray(circles, events::emitCircleClick)
        mainCoroutine.launch {
            mapController?.compositionCircles(states)
        }
    }

    fun updateCircle(circle: ReadableMap?) {
        val state = circleStateFromReadableMap(circle, events::emitCircleClick) ?: return
        mainCoroutine.launch {
            mapController?.updateCircle(state)
        }
    }

    fun compositionPolygons(polygons: ReadableArray?) {
        val states = polygonStatesFromReadableArray(polygons, events::emitPolygonClick)
        mainCoroutine.launch {
            mapController?.compositionPolygons(states)
        }
    }

    fun updatePolygon(polygon: ReadableMap?) {
        val state = polygonStateFromReadableMap(polygon, events::emitPolygonClick) ?: return
        mainCoroutine.launch {
            mapController?.updatePolygon(state)
        }
    }

    fun updatePolyline(polyline: ReadableMap?) {
        val state = polylineStateFromReadableMap(polyline, events::emitPolylineClick) ?: return
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
        val states = groundImageStatesFromReadableArray(images, context, events::emitGroundImageClick)
        val previousIds = groundImageStates.keys
        groundImageStates = states.associateBy { it.id }
        val extensionImages =
            extensionScope.groundImageCollector.flow.value.filterKeys { id -> id !in previousIds }
        extensionScope.groundImageCollector.flow.value =
            (extensionImages + groundImageStates).toMutableMap()
    }

    fun updateGroundImage(image: ReadableMap?) {
        val state = groundImageStateFromReadableMap(image, context, events::emitGroundImageClick) ?: return
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

    private fun emitMarkerScreenPositions() {
        screenPositions.emitMarkers(markerStates.values, markerTilingOptions, { mapHolder?.toScreenOffset(it) })
    }

    private fun emitInfoBubbleScreenPositions() {
        screenPositions.emitInfoBubbles(infoBubblePositions, { mapHolder?.toScreenOffset(it) })
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

}
