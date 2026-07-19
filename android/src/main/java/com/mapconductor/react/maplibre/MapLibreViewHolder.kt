package com.mapconductor.react.maplibre

import android.graphics.PointF
import com.mapconductor.core.features.GeoPoint
import com.mapconductor.maplibre.toLatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

class MapLibreViewHolder(
    val mapView: MapView,
    val map: MapLibreMap,
) {
    fun toScreenOffset(
        latitude: Double,
        longitude: Double,
    ): PointF = map.projection.toScreenLocation(GeoPoint(latitude, longitude).toLatLng())

    fun fromScreenOffset(offset: PointF) = map.projection.fromScreenLocation(offset)
}
