package pl.navilas.finder.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed class LocationOutcome {
    data class Exact(val location: Location) : LocationOutcome()
    data class Approximate(val location: Location) : LocationOutcome()
    data class Failure(val reason: String) : LocationOutcome()
}

/**
 * Foreground-only location via the platform LocationManager (no background permission).
 */
class AppLocationProvider(
    private val context: Context,
    private val lastGoodStore: LastGoodLocationStore = LastGoodLocationStore(),
    private val lastGpsPreferences: LastGpsPreferences? = null,
) {
    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    fun permissionState(): PermissionState {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        return when {
            fine -> PermissionState.FINE
            coarse -> PermissionState.COARSE_ONLY
            else -> PermissionState.DENIED
        }
    }

    fun isLocationEnabled(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            locationManager.isLocationEnabled
        } else {
            @Suppress("DEPRECATION")
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }
    }

    /**
     * When [preferFastPath] is true, returns last good fix if younger than [LastGoodLocationStore.DEFAULT_MAX_AGE_MS].
     */
    @SuppressLint("MissingPermission")
    suspend fun currentLocation(preferFastPath: Boolean = false): LocationOutcome {
        when (permissionState()) {
            PermissionState.DENIED -> {
                return LocationOutcome.Failure(
                    "Brak uprawnień do lokalizacji. Nadaj dostęp, aby wyszukać punkty wokół Ciebie.",
                )
            }
            PermissionState.FINE, PermissionState.COARSE_ONLY -> Unit
        }

        if (!isLocationEnabled()) {
            return LocationOutcome.Failure(
                "Lokalizacja systemu jest wyłączona. Włącz GPS / lokalizację w ustawieniach telefonu.",
            )
        }

        if (preferFastPath) {
            lastGoodStore.getIfFresh()?.let { stored ->
                return stored.toOutcome()
            }
        }

        val cached = lastKnown()
        return try {
            val fresh = requestSingleUpdate()
            rememberGood(fresh)
            if (permissionState() == PermissionState.COARSE_ONLY) {
                LocationOutcome.Approximate(fresh)
            } else {
                LocationOutcome.Exact(fresh)
            }
        } catch (e: Exception) {
            if (cached != null) {
                rememberGood(cached)
                if (permissionState() == PermissionState.COARSE_ONLY) {
                    LocationOutcome.Approximate(cached)
                } else {
                    LocationOutcome.Exact(cached)
                }
            } else {
                LocationOutcome.Failure(
                    e.message ?: "Nie udało się pobrać lokalizacji.",
                )
            }
        }
    }

    private fun rememberGood(location: Location) {
        val approximate = permissionState() == PermissionState.COARSE_ONLY
        lastGoodStore.record(location, approximate)
        lastGpsPreferences?.save(
            latitude = location.latitude,
            longitude = location.longitude,
            approximate = approximate,
            recordedAtMs = System.currentTimeMillis(),
        )
    }

    fun lastPersistedFix(): LastGoodLocationStore.StoredLocation? = lastGpsPreferences?.load()

    private fun LastGoodLocationStore.StoredLocation.toOutcome(): LocationOutcome {
        val location = Location("last-good").apply {
            latitude = this@toOutcome.latitude
            longitude = this@toOutcome.longitude
            time = recordedAtMs
        }
        return if (approximate) {
            LocationOutcome.Approximate(location)
        } else {
            LocationOutcome.Exact(location)
        }
    }

    @SuppressLint("MissingPermission")
    private fun lastKnown(): Location? {
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        )
        return providers
            .mapNotNull { provider ->
                runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
            }
            .maxByOrNull { it.time }
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestSingleUpdate(): Location =
        suspendCancellableCoroutine { cont ->
            val provider = when {
                locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) &&
                    permissionState() == PermissionState.FINE -> LocationManager.GPS_PROVIDER
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ->
                    LocationManager.NETWORK_PROVIDER
                else -> LocationManager.PASSIVE_PROVIDER
            }

            val listener = object : android.location.LocationListener {
                override fun onLocationChanged(location: Location) {
                    locationManager.removeUpdates(this)
                    if (cont.isActive) cont.resume(location)
                }

                override fun onProviderDisabled(provider: String) = Unit

                override fun onProviderEnabled(provider: String) = Unit

                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
            }

            cont.invokeOnCancellation {
                locationManager.removeUpdates(listener)
            }

            try {
                locationManager.requestLocationUpdates(
                    provider,
                    0L,
                    0f,
                    listener,
                    Looper.getMainLooper(),
                )
            } catch (e: SecurityException) {
                cont.resumeWithException(e)
            } catch (e: IllegalArgumentException) {
                cont.resumeWithException(
                    IllegalStateException("Brak dostawcy lokalizacji: ${e.message}"),
                )
            }
        }

    enum class PermissionState {
        FINE,
        COARSE_ONLY,
        DENIED,
    }
}
