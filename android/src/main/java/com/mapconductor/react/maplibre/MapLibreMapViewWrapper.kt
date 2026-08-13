package com.mapconductor.react.maplibre

import android.content.Context
import android.view.View
import androidx.compose.ui.geometry.Offset
import com.mapconductor.core.features.GeoPointInterface
import com.mapconductor.core.map.MapCameraPosition
import com.mapconductor.core.map.MutableMapServiceRegistry
import com.mapconductor.core.marker.MarkerTilingOptions
import com.mapconductor.maplibre.MapLibreMapViewHolder
import com.mapconductor.maplibre.MapLibreMapViewHolderInterface
import com.mapconductor.maplibre.MapLibreMapViewScope
import com.mapconductor.maplibre.MapLibreViewController
import com.mapconductor.maplibre.createMapLibreViewController
import com.mapconductor.maplibre.toCameraPosition
import com.mapconductor.react.wrapper.MapConductorMapViewWrapperBase
import com.mapconductor.react.wrapper.MapConductorReactNativeHost
import com.mapconductor.react.wrapper.MapConductorReactNativeHostDelegate
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import com.mapconductor.maplibre.MapLibreDesign as ComposeMapLibreDesign

/**
 * RN の MapLibre ビュー。
 *
 * コマンドの受け口・マーカー取り込み・スクリーン座標の通知・拡張の Compose レイヤは
 * [MapConductorMapViewWrapperBase]（js-sdk-react/android）が全部持っているので、
 * ここはプロバイダ固有のアダプタを差すだけ。
 */
class MapLibreMapViewWrapper(context: Context) : MapConductorMapViewWrapperBase(context) {
    override val host: MapConductorReactNativeHost = MapLibreReactNativeHost()
}

/** MapLibre の地図一式を RN のラッパー基底が扱える形へ翻訳する。 */
private class MapLibreReactNativeHost : MapConductorReactNativeHost {
    override val providerName = "MapLibre"
    override val extensionScope = MapLibreMapViewScope()
    override val serviceRegistry = MutableMapServiceRegistry()

    private var mapView: MapView? = null
    private var holder: MapLibreMapViewHolderInterface? = null
    private var controller: MapLibreViewController? = null
    private var mapDesign = ComposeMapLibreDesign.DemoTiles

    override fun createMapView(
        context: Context,
        initialCamera: MapCameraPosition,
        markerTiling: MarkerTilingOptions,
        delegate: MapConductorReactNativeHostDelegate,
    ): View {
        MapLibre.getInstance(context)

        val nativeMapView =
            MapView(
                context,
                MapLibreMapOptions
                    .createFromAttributes(context)
                    .camera(initialCamera.toCameraPosition())
                    .textureMode(true),
            )
        mapView = nativeMapView

        nativeMapView.getMapAsync { map ->
            map.setStyle(mapDesign.styleJsonURL) {
                if (!delegate.isAttached) return@setStyle
                val mapHolder = MapLibreMapViewHolder(nativeMapView, map)
                holder = mapHolder
                val viewController =
                    createMapLibreViewController(
                        holder = mapHolder,
                        markerTiling = markerTiling,
                        serviceRegistry = serviceRegistry,
                    )
                controller = viewController
                delegate.onControllerReady(viewController)
                delegate.onMapLoaded()
                nativeMapView.post { viewController.sendInitialCameraUpdate() }
            }
        }
        return nativeMapView
    }

    override fun setMapDesign(id: String?) {
        val styleUrl = MapLibreDesign.styleUrlFrom(id)
        mapDesign = ComposeMapLibreDesign(id = styleUrl, styleJsonURL = styleUrl)
        controller?.setMapDesignType(mapDesign)
    }

    override fun toScreenOffset(position: GeoPointInterface): Offset? = holder?.toScreenOffset(position)

    override fun destroy() {
        controller?.destroy()
        controller = null
        holder = null
        mapView?.onDestroy()
        mapView = null
    }
}
