package wangdaye.com.geometricweather.weather.json.metno

import kotlinx.serialization.Serializable

/**
 * MET Norway location forecast.
 */
@Serializable
data class MetNoForecastResult(
    val properties: MetNoForecastProperties?
)
