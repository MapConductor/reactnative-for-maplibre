package com.mapconductor.react.maplibre

import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.WritableMap
import com.mapconductor.core.features.GeoPoint
import com.mapconductor.core.features.GeoPointInterface
import com.mapconductor.core.features.GeoRectBounds
import com.mapconductor.core.map.MapCameraPosition
import com.mapconductor.core.map.VisibleRegion
import com.mapconductor.maplibre.toGeoPoint
import com.mapconductor.maplibre.toLatLng
import org.maplibre.android.camera.CameraPosition

private const val MAPLIBRE_TO_GOOGLE_ZOOM_OFFSET = 1.0

private fun maplibreZoomToGoogleZoom(maplibreZoom: Double): Double =
    (maplibreZoom + MAPLIBRE_TO_GOOGLE_ZOOM_OFFSET).coerceIn(0.0, 22.0)

private fun googleZoomToMaplibreZoom(googleZoom: Double): Double =
    (googleZoom - MAPLIBRE_TO_GOOGLE_ZOOM_OFFSET).coerceIn(0.0, 22.0)

fun MapCameraPosition.toCameraPosition(): CameraPosition =
    CameraPosition.Builder()
        .target(position.toLatLng())
        .zoom(googleZoomToMaplibreZoom(zoom))
        .bearing(bearing)
        .tilt(tilt.coerceIn(0.0, 60.0))
        .build()

fun CameraPosition.toMapCameraPosition(): MapCameraPosition =
    MapCameraPosition(
        position = target?.toGeoPoint() ?: GeoPoint(0.0, 0.0),
        zoom = maplibreZoomToGoogleZoom(zoom),
        bearing = bearing ?: 0.0,
        tilt = tilt ?: 0.0,
    )

fun MapCameraPosition.toWritableMap(): WritableMap =
    Arguments.createMap().apply {
        putMap("position", position.toWritableMap())
        putMap("center", position.toWritableMap())
        putDouble("zoom", zoom)
        putDouble("bearing", bearing)
        putDouble("tilt", tilt)
        visibleRegion?.let { putMap("visibleRegion", it.toWritableMap()) }
    }

private fun GeoPointInterface.toWritableMap(): WritableMap =
    Arguments.createMap().apply {
        putDouble("latitude", latitude)
        putDouble("longitude", longitude)
        putDouble("altitude", altitude ?: 0.0)
    }

private fun GeoRectBounds.toWritableMap(): WritableMap =
    Arguments.createMap().apply {
        southWest?.let { putMap("southWest", it.toWritableMap()) }
        northEast?.let { putMap("northEast", it.toWritableMap()) }
    }

private fun VisibleRegion.toWritableMap(): WritableMap =
    Arguments.createMap().apply {
        putMap("bounds", bounds.toWritableMap())
        nearLeft?.let { putMap("nearLeft", it.toWritableMap()) }
        nearRight?.let { putMap("nearRight", it.toWritableMap()) }
        farLeft?.let { putMap("farLeft", it.toWritableMap()) }
        farRight?.let { putMap("farRight", it.toWritableMap()) }
    }

fun MapCameraPosition.Companion.fromReadableMap(map: ReadableMap?): MapCameraPosition {
    val positionMap =
        when {
            map == null -> null
            map.hasKey("position") -> map.getMap("position")
            map.hasKey("center") -> map.getMap("center")
            else -> null
        }

    return MapCameraPosition(
        position = GeoPoint.fromReadableMap(positionMap) ?: GeoPoint(0.0, 0.0),
        zoom = map?.getDoubleOrNull("zoom") ?: 0.0,
        bearing = map?.getDoubleOrNull("bearing") ?: 0.0,
        tilt = map?.getDoubleOrNull("tilt") ?: 0.0,
    )
}
