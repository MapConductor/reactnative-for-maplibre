package com.mapconductor.react.maplibre

import com.mapconductor.react.wrapper.MapConductorMapViewCommands
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.uimanager.SimpleViewManager
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.annotations.ReactProp

class MapConductorMapLibreViewManager : SimpleViewManager<MapLibreMapViewWrapper>() {
    override fun getName(): String = REACT_CLASS

    override fun createViewInstance(reactContext: ThemedReactContext): MapLibreMapViewWrapper =
        MapLibreMapViewWrapper(reactContext)

    override fun onAfterUpdateTransaction(view: MapLibreMapViewWrapper) {
        super.onAfterUpdateTransaction(view)
        view.initializeMapIfNeeded()
    }

    @ReactProp(name = "cameraPosition")
    fun setCameraPosition(
        view: MapLibreMapViewWrapper,
        cameraPosition: ReadableMap?,
    ) {
        view.setCameraPosition(cameraPosition)
    }

    @ReactProp(name = "mapDesignType")
    fun setMapDesignType(
        view: MapLibreMapViewWrapper,
        mapDesignType: String?,
    ) {
        view.setMapDesignType(mapDesignType)
    }

    /**
     * 地図の見た目。JS 側は「記述」だけ送ってくる（コンパイラが wasm で
     * Hermes に wasm が無い）ので、組み立てはネイティブ側で行う。
     * 詳細は `ReactNativeVectorStyle`。
     */
    @ReactProp(name = "vectorStyle")
    fun setVectorStyle(
        view: MapLibreMapViewWrapper,
        vectorStyle: ReadableMap?,
    ) {
        view.setVectorStyle(vectorStyle)
    }

    @ReactProp(name = "infoBubblePositions")
    fun setInfoBubblePositions(
        view: MapLibreMapViewWrapper,
        positions: ReadableArray?,
    ) {
        view.setInfoBubblePositions(positions)
    }

    @ReactProp(name = "markerTilingOptions")
    fun setMarkerTilingOptions(
        view: MapLibreMapViewWrapper,
        options: ReadableMap?,
    ) {
        view.setMarkerTilingOptions(options)
    }

    override fun receiveCommand(
        root: MapLibreMapViewWrapper,
        commandId: String,
        args: ReadableArray?,
    ) {
        // コマンド名の対応は全プロバイダ共通。写経すると綴り違いが黙って無効化されるため
        // js-sdk-react に集約してある。
        MapConductorMapViewCommands.receive(root, commandId, args)
    }

    override fun onDropViewInstance(view: MapLibreMapViewWrapper) {
        view.onDropViewInstance()
        super.onDropViewInstance(view)
    }

    override fun getExportedCustomDirectEventTypeConstants(): MutableMap<String, Any> =
        MapConductorMapViewCommands.directEventTypeConstants()

    companion object {
        const val REACT_CLASS = "MapLibreMapView"
    }
}
