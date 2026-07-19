package com.mapconductor.react.maplibre

object MapLibreDesign {
    const val DEFAULT_STYLE_URL = "https://demotiles.maplibre.org/style.json"

    fun styleUrlFrom(value: String?): String {
        if (value.isNullOrBlank()) return DEFAULT_STYLE_URL
        val style = value.substringAfter("style=", missingDelimiterValue = value)
        return style.takeIf { it.isNotBlank() } ?: DEFAULT_STYLE_URL
    }
}
