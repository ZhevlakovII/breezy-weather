package wangdaye.com.geometricweather.weather.services;

import android.content.Context;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.TimeZone;

import javax.inject.Inject;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import wangdaye.com.geometricweather.BuildConfig;
import wangdaye.com.geometricweather.common.basic.models.Location;
import wangdaye.com.geometricweather.common.rxjava.BaseObserver;
import wangdaye.com.geometricweather.common.rxjava.ObserverContainer;
import wangdaye.com.geometricweather.common.rxjava.SchedulerTransformer;
import wangdaye.com.geometricweather.common.utils.DisplayUtils;
import wangdaye.com.geometricweather.settings.SettingsManager;
import wangdaye.com.geometricweather.weather.apis.MetNoApi;
import wangdaye.com.geometricweather.weather.apis.NominatimApi;
import wangdaye.com.geometricweather.weather.converters.MetNoResultConverterKt;
import wangdaye.com.geometricweather.weather.json.metno.MetNoForecastResult;
import wangdaye.com.geometricweather.weather.json.metno.MetNoEphemerisResult;
import wangdaye.com.geometricweather.weather.json.nominatim.NominatimLocationResult;

/**
 * MET Norway weather service.
 **/
public class MetNoWeatherService extends WeatherService {
    private final MetNoApi mApi;
    private final NominatimApi mNominatimApi;
    private final CompositeDisposable mCompositeDisposable;

    @Inject
    public MetNoWeatherService(MetNoApi api, NominatimApi nominatimApi, CompositeDisposable disposable) {
        mApi = api;
        mNominatimApi = nominatimApi;
        mCompositeDisposable = disposable;
    }

    protected String getUserAgent() {
        return "GeometricWeather/"+ BuildConfig.VERSION_NAME + " github.com/WangDaYeeeeee/GeometricWeather/issues";
    }

    protected static String getTimezoneOffset(TimeZone tz) {
        Calendar cal = GregorianCalendar.getInstance(tz);
        int offsetInMillis = tz.getOffset(cal.getTimeInMillis());

        String offset = String.format("%02d:%02d", Math.abs(offsetInMillis / 3600000), Math.abs((offsetInMillis / 60000) % 60));
        offset = (offsetInMillis >= 0 ? "+" : "-") + offset;

        return offset;
    }

    @Override
    public void requestWeather(Context context, Location location, @NonNull RequestWeatherCallback callback) {
        Observable<MetNoForecastResult> forecast = mApi.getForecast(
                getUserAgent(),
                location.getLatitude(),
                location.getLongitude()
        );

        String formattedDate = DisplayUtils.getFormattedDate(new Date(), location.getTimeZone(), "yyyy-MM-dd");
        Observable<MetNoEphemerisResult> ephemeris = mApi.getEphemeris(
                getUserAgent(),
                formattedDate,
                15,
                location.getLatitude(),
                location.getLongitude(),
                getTimezoneOffset(location.getTimeZone())
        );

        Observable.zip(forecast, ephemeris,
                (metNoForecast, metNoEphemeris) -> MetNoResultConverterKt.convert(
                         context,
                         location,
                         metNoForecast,
                         metNoEphemeris
                )
        ).compose(SchedulerTransformer.create())
                .subscribe(new ObserverContainer<>(mCompositeDisposable, new BaseObserver<WeatherResultWrapper>() {
                    @Override
                    public void onSucceed(WeatherResultWrapper wrapper) {
                        if (wrapper.result != null) {
                            callback.requestWeatherSuccess(
                                    Location.copy(location, wrapper.result)
                            );
                        } else {
                            onFailed();
                        }
                    }

                    @Override
                    public void onFailed() {
                        callback.requestWeatherFailed(location, this.isApiLimitReached(), this.isApiUnauthorized());
                    }
                }));
    }

    @Override
    @NonNull
    public List<Location> requestLocation(Context context, String query) {
        String languageCode = SettingsManager.getInstance(context).getLanguage().getCode();
        List<NominatimLocationResult> resultList = null;
        try {
            resultList = mNominatimApi.callWeatherLocation(getUserAgent(), query, "city", true, languageCode, "jsonv2").execute().body();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return MetNoResultConverterKt.convert(resultList);
    }

    @Override
    public void requestLocation(Context context, Location location,
                                @NonNull RequestLocationCallback callback) {

        String languageCode = SettingsManager.getInstance(context).getLanguage().getCode();

        mNominatimApi.getWeatherLocationByGeoPosition(
                getUserAgent(),
                location.getLatitude(),
                location.getLongitude(),
                "city",
                true,
                languageCode,
         "jsonv2"
            ).compose(SchedulerTransformer.create())
                .subscribe(new ObserverContainer<>(mCompositeDisposable, new BaseObserver<NominatimLocationResult>() {
                    @Override
                    public void onSucceed(NominatimLocationResult metnoLocationResult) {
                        if (metnoLocationResult != null) {
                            List<Location> locationList = new ArrayList<>();
                            locationList.add(MetNoResultConverterKt.convert(location, metnoLocationResult));
                            callback.requestLocationSuccess(
                                    location.getLatitude() + "," + location.getLongitude(),
                                    locationList
                            );
                        } else {
                            onFailed();
                        }
                    }

                    @Override
                    public void onFailed() {
                        callback.requestLocationFailed(
                                location.getLatitude() + "," + location.getLongitude()
                        );
                    }
                }));
    }

    public void requestLocation(Context context, String query,
                                @NonNull RequestLocationCallback callback) {
        String languageCode = SettingsManager.getInstance(context).getLanguage().getCode();

        mNominatimApi.getWeatherLocation(getUserAgent(), query, "city", true, languageCode, "jsonv2")
                .compose(SchedulerTransformer.create())
                .subscribe(new ObserverContainer<>(mCompositeDisposable, new BaseObserver<List<NominatimLocationResult>>() {
                    @Override
                    public void onSucceed(List<NominatimLocationResult> metnoLocationResults) {
                        if (metnoLocationResults != null && metnoLocationResults.size() != 0) {
                            List<Location> locationList = MetNoResultConverterKt.convert(metnoLocationResults);
                            callback.requestLocationSuccess(query, locationList);
                        } else {
                            callback.requestLocationFailed(query);
                        }
                    }

                    @Override
                    public void onFailed() {
                        callback.requestLocationFailed(query);
                    }
                }));
    }

    @Override
    public void cancel() {
        mCompositeDisposable.clear();
    }
}