package wangdaye.com.geometricweather.weather.json.metno

import kotlinx.serialization.Serializable

@Serializable
data class MetNoForecastDataNextHours(
    val summary: MetNoForecastDataSummary?,
    val details: MetNoForecastDataDetails?
)
