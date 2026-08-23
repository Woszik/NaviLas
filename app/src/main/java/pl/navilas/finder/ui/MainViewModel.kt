package pl.navilas.finder.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.navilas.finder.data.bdl.BdlOfflineDownloader
import pl.navilas.finder.data.bdl.BdlOfflineStore
import pl.navilas.finder.data.bdl.BdlSearchContext
import pl.navilas.finder.data.bdl.BdlSearchSubsetFilter
import pl.navilas.finder.data.bdl.RestSiteRepository
import pl.navilas.finder.data.cache.BdlSearchSessionCache
import pl.navilas.finder.data.cache.OsmRoadTileCache
import pl.navilas.finder.data.cache.RoadAssessmentCache
import pl.navilas.finder.data.osm.CachingOverpassRoadClient
import pl.navilas.finder.data.osm.NominatimGeocoder
import pl.navilas.finder.data.osm.OverpassRoadClient
import pl.navilas.finder.data.osm.PersistentLocalityGeocodeStore
import pl.navilas.finder.data.osm.RoadProximityAnalyzer
import pl.navilas.finder.data.saved.SavedPointsStore
import pl.navilas.finder.domain.AppMessage
import pl.navilas.finder.domain.BdlDataScope
import pl.navilas.finder.domain.OfflineBdlConfig
import pl.navilas.finder.domain.estimatedSizeLabel
import pl.navilas.finder.domain.OfflineBdlState
import pl.navilas.finder.domain.OfflineBdlStatus
import pl.navilas.finder.domain.ZanocujPolygonQuality
import pl.navilas.finder.domain.LatLon
import pl.navilas.finder.domain.NavigationTargetKind
import pl.navilas.finder.domain.Poi
import pl.navilas.finder.domain.PoiCategory
import pl.navilas.finder.domain.PoiGeometryKind
import pl.navilas.finder.domain.RestSite
import pl.navilas.finder.domain.RestSiteResult
import pl.navilas.finder.domain.RoadAssessment
import pl.navilas.finder.domain.SearchConfig
import pl.navilas.finder.domain.SearchOriginMode
import pl.navilas.finder.domain.ListViewMode
import pl.navilas.finder.domain.SavedPoint
import pl.navilas.finder.domain.SavedPointCategory
import pl.navilas.finder.domain.TravelProfile
import pl.navilas.finder.domain.ZanocujFilterMode
import pl.navilas.finder.domain.ZanocujStatus
import pl.navilas.finder.location.AppLocationProvider
import pl.navilas.finder.location.LocationOutcome
import pl.navilas.finder.nav.NavigationTargets
import pl.navilas.finder.util.GeoUtils
import pl.navilas.finder.BuildConfig
import pl.navilas.finder.R
import pl.navilas.finder.update.AppUpdateChecker
import pl.navilas.finder.update.AppUpdateDownloader
import pl.navilas.finder.update.AppUpdateInstaller
import pl.navilas.finder.update.AppUpdateLogic
import pl.navilas.finder.update.AppUpdateOffer
import pl.navilas.finder.update.AppUpdatePreferences
import java.io.File
import java.io.IOException
import java.net.UnknownHostException
import java.util.concurrent.atomic.AtomicLong

data class UserPosition(
    val latitude: Double,
    val longitude: Double,
    val approximate: Boolean,
)

data class UiState(
    val profile: TravelProfile = TravelProfile.CAR,
    val zanocujFilter: ZanocujFilterMode = ZanocujFilterMode.ALL,
    /** GPS / last known device location (blue marker). */
    val userPosition: UserPosition? = null,
    /**
     * Optional search centre from a map tap (orange pin).
     * Used when [searchOriginMode] is [SearchOriginMode.MAP] or [SearchOriginMode.LOCALITY].
     */
    val mapSearchPin: LatLon? = null,
    val searchOriginMode: SearchOriginMode = SearchOriginMode.GPS,
    val localityQuery: String = "",
    val localityDisplayName: String? = null,
    val allSites: List<RestSite> = emptyList(),
    val zanocujPolygons: List<pl.navilas.finder.data.bdl.ZanocujPolygon> = emptyList(),
    val roadBySiteId: Map<String, RoadAssessment> = emptyMap(),
    val results: List<RestSiteResult> = emptyList(),
    val selectedSiteId: String? = null,
    val currentPage: Int = AppPages.SEARCH,
    val mapCameraRequest: MapCameraRequest? = null,
    val isLocating: Boolean = false,
    val isSearching: Boolean = false,
    /** Moto profile: OSM road analysis running after BDL results are shown. */
    val isAnalyzingRoads: Boolean = false,
    val message: AppMessage? = null,
    val searchConfig: SearchConfig = SearchConfig.DEFAULT,
    val offlineBdl: OfflineBdlState = OfflineBdlState(),
    val listViewMode: ListViewMode = ListViewMode.SEARCH,
    val savedPoints: Map<String, SavedPoint> = emptyMap(),
    val savedCategories: List<SavedPointCategory> = emptyList(),
    /** null = all categories in saved list. */
    val savedCategoryFilterId: String? = null,
    val savedListResults: List<RestSiteResult> = emptyList(),
    val appUpdateOffer: AppUpdateOffer? = null,
    val appUpdateDownloading: Boolean = false,
    val appUpdateDownloadPercent: Int? = null,
    val appUpdateInstallFile: File? = null,
    val appUpdateError: String? = null,
) {
    fun activeListResults(): List<RestSiteResult> = when (listViewMode) {
        ListViewMode.SEARCH -> results
        ListViewMode.SAVED -> savedListResults
    }

    fun isSaved(siteId: String): Boolean = savedPoints.containsKey(siteId)

    fun savedPoint(siteId: String): SavedPoint? = savedPoints[siteId]
    /** Position used for BDL search radius and result distances. */
    fun searchOrigin(): UserPosition? = when (searchOriginMode) {
        SearchOriginMode.MAP, SearchOriginMode.LOCALITY ->
            mapSearchPin?.let { UserPosition(it.latitude, it.longitude, approximate = false) }
        SearchOriginMode.GPS -> userPosition
    }

    fun usesMapPinForSearch(): Boolean =
        searchOriginMode == SearchOriginMode.MAP && mapSearchPin != null

    fun usesLocalityForSearch(): Boolean =
        searchOriginMode == SearchOriginMode.LOCALITY && mapSearchPin != null
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val bdlSessionCache = BdlSearchSessionCache()
    private val roadAssessmentCache = RoadAssessmentCache()
    private val osmTileCache = OsmRoadTileCache()
    private val localityStore = PersistentLocalityGeocodeStore.fromAppFilesDir(application.filesDir)
    private val locationProvider = AppLocationProvider(application)
    private val offlineStore = BdlOfflineStore.fromAppFilesDir(application.filesDir)
    private val offlineDownloader = BdlOfflineDownloader(store = offlineStore)
    private val restRepository = RestSiteRepository(
        offlineStore = offlineStore,
        sessionCache = bdlSessionCache,
        config = SearchConfig.DEFAULT,
    )
    private val roadAnalyzer = RoadProximityAnalyzer(
        overpass = CachingOverpassRoadClient(tileCache = osmTileCache),
        assessmentCache = roadAssessmentCache,
    )
    private val localityGeocoder = NominatimGeocoder(localityStore = localityStore)
    private val savedPointsStore = SavedPointsStore.fromAppFilesDir(application.filesDir)
    private val appUpdatePrefs = AppUpdatePreferences(application)
    private val appUpdateChecker = AppUpdateChecker(BuildConfig.UPDATE_MANIFEST_URL)
    private val appUpdateDownloader = AppUpdateDownloader()
    private val cameraToken = AtomicLong(1L)
    private val searchGeneration = AtomicLong(0L)
    private var lastBdlSearchContext: BdlSearchContext? = null

    private val _state = MutableStateFlow(loadInitialState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private fun loadInitialState(): UiState {
        val saved = savedPointsStore.allPoints().associateBy { it.site.id }
        val categories = savedPointsStore.allCategories()
        val base = UiState(
            offlineBdl = loadOfflineState(),
            savedPoints = saved,
            savedCategories = categories,
        )
        return base.copy(savedListResults = buildSavedListResults(base))
    }

    init {
        refreshOfflineStateFromDisk()
        viewModelScope.launch {
            delay(2_000)
            checkForAppUpdate(force = false)
        }
    }

    private fun invalidateBdlSessionCache() {
        bdlSessionCache.clear()
        lastBdlSearchContext = null
    }

    private fun currentOfflineVersion(): Long =
        if (_state.value.offlineBdl.isReady) offlineStore.downloadedAt() ?: 0L else 0L

    private fun loadOfflineState(): OfflineBdlState {
        val stored = offlineStore.storedConfig()
        return if (stored != null) {
            OfflineBdlState(
                status = OfflineBdlStatus.READY,
                storedConfig = stored,
                pendingConfig = stored,
                storageBytes = offlineStore.storageBytes(),
                downloadedAt = offlineStore.downloadedAt(),
            )
        } else {
            OfflineBdlState()
        }
    }

    private fun refreshOfflineStateFromDisk() {
        _state.update { it.copy(offlineBdl = loadOfflineState()) }
    }

    fun setOfflineScope(scope: BdlDataScope) {
        updatePendingOfflineConfig { it.copy(scope = scope) }
    }

    fun setZanocujQuality(quality: ZanocujPolygonQuality) {
        updatePendingOfflineConfig { it.copy(zanocujQuality = quality) }
    }

    private fun updatePendingOfflineConfig(
        transform: (OfflineBdlConfig) -> OfflineBdlConfig,
    ) {
        _state.update { current ->
            val newPending = transform(current.offlineBdl.pendingConfig)
            val stored = current.offlineBdl.storedConfig
            val shouldDelete = stored != null && !newPending.matches(stored)
            if (shouldDelete) {
                offlineStore.deleteAll()
                invalidateBdlSessionCache()
            }
            val offline = if (shouldDelete) {
                current.offlineBdl.copy(
                    pendingConfig = newPending,
                    storedConfig = null,
                    status = OfflineBdlStatus.NOT_DOWNLOADED,
                    storageBytes = 0L,
                    downloadedAt = null,
                    progress = 0f,
                    progressLabel = null,
                    errorMessage = null,
                )
            } else {
                current.offlineBdl.copy(pendingConfig = newPending)
            }
            current.copy(
                offlineBdl = offline,
                message = if (shouldDelete) {
                    AppMessage.Info("Zmieniono wybór danych — poprzedni pakiet offline został usunięty.")
                } else {
                    current.message
                },
            )
        }
    }

    fun downloadOfflineData() {
        if (_state.value.offlineBdl.status == OfflineBdlStatus.DOWNLOADING) return
        val config = _state.value.offlineBdl.pendingConfig
        viewModelScope.launch {
            _state.update {
                it.copy(
                    offlineBdl = it.offlineBdl.copy(
                        status = OfflineBdlStatus.DOWNLOADING,
                        progress = 0f,
                        progressLabel = "Przygotowanie…",
                        errorMessage = null,
                    ),
                    message = null,
                )
            }
            try {
                offlineDownloader.download(config) { completed, total, label ->
                    _state.update {
                        it.copy(
                            offlineBdl = it.offlineBdl.copy(
                                progress = if (total > 0) completed.toFloat() / total else 0f,
                                progressLabel = label,
                            ),
                        )
                    }
                }
                _state.update {
                    it.copy(
                        offlineBdl = loadOfflineState().copy(pendingConfig = config),
                        message = AppMessage.Info(
                            "Dane BDL pobrane (${config.estimatedSizeLabel()}). Wyszukiwanie działa offline.",
                        ),
                    )
                }
                invalidateBdlSessionCache()
            } catch (e: UnknownHostException) {
                _state.update {
                    it.copy(
                        offlineBdl = it.offlineBdl.copy(
                            status = OfflineBdlStatus.ERROR,
                            errorMessage = "Brak internetu.",
                        ),
                        message = AppMessage.Error("Pobieranie BDL: brak internetu."),
                    )
                }
            } catch (e: Exception) {
                offlineStore.deleteAll()
                _state.update {
                    it.copy(
                        offlineBdl = OfflineBdlState(
                            pendingConfig = config,
                            status = OfflineBdlStatus.ERROR,
                            errorMessage = e.message ?: "błąd pobierania",
                        ),
                        message = AppMessage.Error("Pobieranie BDL: ${e.message ?: "błąd"}"),
                    )
                }
            }
        }
    }

    fun deleteOfflineData() {
        if (_state.value.offlineBdl.status == OfflineBdlStatus.DOWNLOADING) return
        offlineStore.deleteAll()
        invalidateBdlSessionCache()
        val pending = _state.value.offlineBdl.pendingConfig
        _state.update {
            it.copy(
                offlineBdl = OfflineBdlState(pendingConfig = pending),
                message = AppMessage.Info("Dane BDL offline usunięte z telefonu."),
            )
        }
    }

    fun setCurrentPage(page: Int) {
        val clamped = page.coerceIn(AppPages.SEARCH, AppPages.LIST)
        _state.update { current ->
            if (current.currentPage == clamped) current
            else current.copy(currentPage = clamped)
        }
    }

    fun setSearchRadiusKm(radiusKm: Double) {
        val clamped = radiusKm.coerceIn(5.0, SearchConfig.MAX_SEARCH_RADIUS_KM)
        require(clamped in SearchConfig.SEARCH_RADIUS_PRESETS_KM) {
            "Unsupported radius $radiusKm"
        }
        _state.update { current ->
            // Radius is a search parameter only — does not mutate results until ZNAJDŹ.
            current.copy(
                searchConfig = current.searchConfig.copy(searchRadiusKm = clamped),
            )
        }
    }

    fun setSearchOriginMode(mode: SearchOriginMode) {
        _state.update { current ->
            current.copy(
                searchOriginMode = mode,
                results = buildResults(
                    current.allSites,
                    when (mode) {
                        SearchOriginMode.GPS -> current.userPosition
                        SearchOriginMode.MAP, SearchOriginMode.LOCALITY -> current.mapSearchPin?.let {
                            UserPosition(it.latitude, it.longitude, approximate = false)
                        }
                    },
                    current.profile,
                    current.zanocujFilter,
                    current.roadBySiteId,
                ),
            )
        }
    }

    fun setLocalityQuery(query: String) {
        _state.update { it.copy(localityQuery = query) }
    }

    fun setProfile(profile: TravelProfile) {
        _state.update { current ->
            val next = current.copy(
                profile = profile,
                results = buildResults(
                    current.allSites,
                    current.searchOrigin(),
                    profile,
                    current.zanocujFilter,
                    current.roadBySiteId,
                ),
            )
            next.copy(savedListResults = buildSavedListResults(next))
        }
    }

    fun setZanocujFilter(mode: ZanocujFilterMode) {
        _state.update { current ->
            current.copy(
                zanocujFilter = mode,
                results = buildResults(
                    current.allSites,
                    current.searchOrigin(),
                    current.profile,
                    mode,
                    current.roadBySiteId,
                ),
            )
        }
    }

    fun setListViewMode(mode: ListViewMode) {
        _state.update { current ->
            current.copy(listViewMode = mode)
        }
    }

    fun setSavedCategoryFilter(categoryId: String?) {
        _state.update { current ->
            val updated = current.copy(savedCategoryFilterId = categoryId)
            updated.copy(savedListResults = buildSavedListResults(updated))
        }
    }

    fun toggleSave(site: RestSite) {
        val existing = savedPointsStore.getPoint(site.id)
        if (existing != null) {
            savedPointsStore.removePoint(site.id)
            _state.update { current ->
                val next = current.copy(
                    savedPoints = current.savedPoints.filterKeys { it != site.id },
                    message = AppMessage.Info("Usunięto „${site.name}” z zapisanych."),
                )
                next.copy(savedListResults = buildSavedListResults(next))
            }
            return
        }
        val point = SavedPoint(
            site = site,
            savedAtMs = System.currentTimeMillis(),
            categoryIds = emptySet(),
            userComment = null,
        )
        savedPointsStore.savePoint(point)
        _state.update { current ->
            val next = current.copy(
                savedPoints = current.savedPoints + (site.id to point),
                message = AppMessage.Info("Zapisano „${site.name}”."),
            )
            next.copy(savedListResults = buildSavedListResults(next))
        }
    }

    fun updateSavedPoint(
        siteId: String,
        categoryIds: Set<String>,
        userComment: String?,
    ) {
        val existing = savedPointsStore.getPoint(siteId) ?: return
        val validIds = categoryIds.intersect(savedPointsStore.allCategories().map { it.id }.toSet())
        val updated = existing.copy(
            categoryIds = validIds,
            userComment = userComment?.trim()?.takeIf { it.isNotEmpty() },
        )
        savedPointsStore.savePoint(updated)
        _state.update { current ->
            val next = current.copy(
                savedPoints = current.savedPoints + (siteId to updated),
                message = AppMessage.Info("Zaktualizowano zapisane miejsce."),
            )
            next.copy(savedListResults = buildSavedListResults(next))
        }
    }

    fun addSavedCategory(name: String) {
        val category = savedPointsStore.addCategory(name)
        _state.update { current ->
            current.copy(
                savedCategories = savedPointsStore.allCategories(),
                message = AppMessage.Info("Dodano kategorię „${category.name}”."),
            )
        }
    }

    fun renameSavedCategory(categoryId: String, newName: String) {
        savedPointsStore.renameCategory(categoryId, newName)
        _state.update { current ->
            current.copy(
                savedCategories = savedPointsStore.allCategories(),
                savedListResults = buildSavedListResults(current),
                message = AppMessage.Info("Zmieniono nazwę kategorii."),
            )
        }
    }

    fun deleteSavedCategory(categoryId: String) {
        savedPointsStore.deleteCategory(categoryId)
        _state.update { current ->
            val filter = if (current.savedCategoryFilterId == categoryId) null else current.savedCategoryFilterId
            val saved = savedPointsStore.allPoints().associateBy { it.site.id }
            val next = current.copy(
                savedPoints = saved,
                savedCategories = savedPointsStore.allCategories(),
                savedCategoryFilterId = filter,
            )
            next.copy(savedListResults = buildSavedListResults(next))
        }
    }

    private fun buildSavedListResults(state: UiState): List<RestSiteResult> {
        val origin = state.searchOrigin()
        val categoryOrder = state.savedCategories.associate { it.id to it.sortOrder }
        val categoryName = state.savedCategories.associate { it.id to it.name }
        return state.savedPoints.values
            .filter { point ->
                state.savedCategoryFilterId == null ||
                    state.savedCategoryFilterId in point.categoryIds
            }
            .sortedWith(
                compareBy<SavedPoint>(
                    { point -> primaryCategorySortKey(point.categoryIds, categoryOrder) },
                    { point -> primaryCategoryLabel(point.categoryIds, categoryName) },
                    { it.site.name },
                ),
            )
            .map { buildResultFromSaved(it, origin, state.profile, state.roadBySiteId) }
    }

    private fun primaryCategorySortKey(
        categoryIds: Set<String>,
        categoryOrder: Map<String, Int>,
    ): Int = categoryIds.minOfOrNull { categoryOrder[it] ?: Int.MAX_VALUE } ?: Int.MAX_VALUE

    private fun primaryCategoryLabel(
        categoryIds: Set<String>,
        categoryName: Map<String, String>,
    ): String = if (categoryIds.isEmpty()) {
        "Bez kategorii"
    } else {
        categoryIds.mapNotNull { categoryName[it] }.sorted().joinToString(", ")
    }

    private fun buildResultFromSaved(
        saved: SavedPoint,
        origin: UserPosition?,
        profile: TravelProfile,
        roadBySiteId: Map<String, RoadAssessment>,
    ): RestSiteResult {
        val site = saved.site
        val distanceKm = origin?.let {
            GeoUtils.distanceKm(it.latitude, it.longitude, site.latitude, site.longitude)
        } ?: 0.0
        val assessment = if (profile == TravelProfile.MOTORCYCLE) roadBySiteId[site.id] else null
        val (target, kind) = when (profile) {
            TravelProfile.CAR -> NavigationTargets.forCar(site)
            TravelProfile.MOTORCYCLE -> {
                NavigationTargets.forMotorcycle(assessment)
                    ?: (LatLon(site.latitude, site.longitude) to NavigationTargetKind.REST_SITE)
            }
        }
        return RestSiteResult(
            site = site,
            distanceKm = distanceKm,
            roadAssessment = assessment,
            navigationTarget = target,
            navigationTargetKind = kind,
        )
    }

    /** Empty-map tap: set search centre (does not run search until ZNAJDŹ). */
    fun setMapSearchPin(latitude: Double, longitude: Double) {
        val pin = LatLon(latitude, longitude)
        _state.update { current ->
            current.copy(
                searchOriginMode = SearchOriginMode.MAP,
                mapSearchPin = pin,
                localityDisplayName = null,
                results = buildResults(
                    current.allSites,
                    UserPosition(latitude, longitude, approximate = false),
                    current.profile,
                    current.zanocujFilter,
                    current.roadBySiteId,
                ),
                message = AppMessage.Info(
                    "Punkt wyszukiwania na mapie ustawiony. Naciśnij ZNAJDŹ, by pobrać miejsca.",
                ),
            )
        }
    }

    fun clearMapSearchPin() {
        _state.update { current ->
            current.copy(
                searchOriginMode = SearchOriginMode.GPS,
                mapSearchPin = null,
                localityDisplayName = null,
                results = buildResults(
                    current.allSites,
                    current.userPosition,
                    current.profile,
                    current.zanocujFilter,
                    current.roadBySiteId,
                ),
                message = AppMessage.Info("Punkt z mapy wyczyszczony — wyszukiwanie od GPS."),
            )
        }
    }

    fun closeSelectedSite() {
        selectSite(null)
    }

    fun selectSite(siteId: String?) {
        _state.update { it.copy(selectedSiteId = siteId) }
    }

    /** Marker tap on map: select + show POI camera (no page change, no fitBounds). */
    fun onMarkerSelected(siteId: String) {
        val token = cameraToken.getAndIncrement()
        _state.update {
            it.copy(
                selectedSiteId = siteId,
                mapCameraRequest = PagerNavigation.cameraForMarkerClick(siteId, token),
            )
        }
    }

    /** List row tap: select + go to map + show POI camera (no fitBounds). */
    fun onListItemSelected(siteId: String) {
        val token = cameraToken.getAndIncrement()
        _state.update {
            it.copy(
                selectedSiteId = siteId,
                currentPage = AppPages.MAP,
                mapCameraRequest = PagerNavigation.cameraForListSelect(siteId, token),
            )
        }
    }

    fun consumeMapCameraRequest() {
        _state.update { it.copy(mapCameraRequest = null) }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    fun checkForAppUpdate(force: Boolean) {
        viewModelScope.launch {
            appUpdatePrefs.lastCheckAtMs = System.currentTimeMillis()
            try {
                val manifest = withContext(Dispatchers.IO) { appUpdateChecker.fetchManifest() }
                val offer = AppUpdateLogic.evaluateOffer(
                    manifest = manifest,
                    currentVersionCode = BuildConfig.VERSION_CODE,
                    dismissedVersionCode = appUpdatePrefs.dismissedVersionCode,
                )
                if (offer != null) {
                    _state.update { it.copy(appUpdateOffer = offer, appUpdateError = null) }
                } else if (force) {
                    _state.update {
                        it.copy(message = AppMessage.Info(getApplication<Application>().getString(
                            R.string.app_update_up_to_date,
                        )))
                    }
                }
            } catch (_: IOException) {
                if (force) {
                    _state.update {
                        it.copy(message = AppMessage.Error(getApplication<Application>().getString(
                            R.string.app_update_network_error,
                        )))
                    }
                }
            } catch (e: Exception) {
                if (force) {
                    _state.update {
                        it.copy(message = AppMessage.Error("Aktualizacja: ${e.message ?: "błąd"}"))
                    }
                }
            }
        }
    }

    fun dismissAppUpdate() {
        val offer = _state.value.appUpdateOffer ?: return
        if (offer.mandatory) return
        appUpdatePrefs.dismissedVersionCode = offer.versionCode
        _state.update { it.copy(appUpdateOffer = null) }
    }

    fun startAppUpdateDownload() {
        val offer = _state.value.appUpdateOffer ?: return
        viewModelScope.launch {
            _state.update {
                it.copy(
                    appUpdateDownloading = true,
                    appUpdateDownloadPercent = null,
                    appUpdateError = null,
                    appUpdateInstallFile = null,
                )
            }
            val apkFile = AppUpdateInstaller.apkFile(getApplication())
            try {
                withContext(Dispatchers.IO) {
                    appUpdateDownloader.download(offer.apkUrl, apkFile) { downloaded, total ->
                        val percent = total?.takeIf { it > 0L }?.let {
                            ((downloaded * 100) / it).toInt().coerceIn(0, 100)
                        }
                        _state.update { state -> state.copy(appUpdateDownloadPercent = percent) }
                    }
                }
                val valid = withContext(Dispatchers.IO) {
                    appUpdateDownloader.verifySha256(apkFile, offer.sha256)
                }
                if (!valid) {
                    apkFile.delete()
                    throw IOException("Niezgodna suma kontrolna pliku APK")
                }
                _state.update {
                    it.copy(
                        appUpdateDownloading = false,
                        appUpdateDownloadPercent = null,
                        appUpdateOffer = null,
                        appUpdateInstallFile = apkFile,
                    )
                }
            } catch (e: Exception) {
                apkFile.delete()
                _state.update {
                    it.copy(
                        appUpdateDownloading = false,
                        appUpdateDownloadPercent = null,
                        appUpdateError = e.message ?: "Błąd pobierania",
                    )
                }
            }
        }
    }

    fun consumeAppUpdateInstall() {
        _state.update { it.copy(appUpdateInstallFile = null) }
    }

    fun consumeAppUpdateError() {
        _state.update { it.copy(appUpdateError = null) }
    }

    fun onBackPressed(): Boolean {
        val next = PagerNavigation.pageAfterBack(_state.value.currentPage) ?: return false
        setCurrentPage(next)
        return true
    }

    fun refreshLocation(showApproximateHint: Boolean = true) {
        viewModelScope.launch {
            _state.update { it.copy(isLocating = true, message = null, searchOriginMode = SearchOriginMode.GPS, mapSearchPin = null, localityDisplayName = null) }
            when (val outcome = locationProvider.currentLocation()) {
                is LocationOutcome.Exact -> applyLocation(
                    UserPosition(outcome.location.latitude, outcome.location.longitude, false),
                    AppMessage.Info("Lokalizacja GPS zaktualizowana. Wyszukiwanie od GPS."),
                )
                is LocationOutcome.Approximate -> applyLocation(
                    UserPosition(outcome.location.latitude, outcome.location.longitude, true),
                    if (showApproximateHint) {
                        AppMessage.Info("Dostępna tylko lokalizacja przybliżona. Wyszukiwanie od GPS.")
                    } else {
                        null
                    },
                )
                is LocationOutcome.Failure -> {
                    _state.update {
                        it.copy(isLocating = false, message = AppMessage.Error(outcome.reason))
                    }
                }
            }
        }
    }

    private fun applyLocation(pos: UserPosition, message: AppMessage?) {
        _state.update {
            it.copy(
                isLocating = false,
                userPosition = pos,
                searchOriginMode = SearchOriginMode.GPS,
                mapSearchPin = null,
                localityDisplayName = null,
                results = buildResults(
                    it.allSites,
                    pos,
                    it.profile,
                    it.zanocujFilter,
                    it.roadBySiteId,
                ),
                message = message,
            )
        }
    }

    fun searchNearby() {
        viewModelScope.launch {
            when (_state.value.searchOriginMode) {
                SearchOriginMode.LOCALITY -> searchFromLocality()
                SearchOriginMode.MAP, SearchOriginMode.GPS -> {
                    val origin = _state.value.searchOrigin()
                    if (origin == null) {
                        _state.update {
                            it.copy(
                                message = AppMessage.Error(
                                    when (it.searchOriginMode) {
                                        SearchOriginMode.MAP ->
                                            "Brak punktu na mapie. Dotknij mapy albo wybierz GPS / miejscowość."
                                        else ->
                                            "Brak punktu wyszukiwania. Włącz GPS albo wybierz mapę / miejscowość."
                                    },
                                ),
                            )
                        }
                        if (_state.value.searchOriginMode == SearchOriginMode.GPS) {
                            refreshLocationThenSearch()
                        }
                        return@launch
                    }
                    performSearch(origin)
                }
            }
        }
    }

    private suspend fun searchFromLocality() {
        val query = _state.value.localityQuery.trim()
        if (query.length < 2) {
            _state.update {
                it.copy(message = AppMessage.Error("Wpisz nazwę miejscowości (min. 2 znaki)."))
            }
            return
        }
        _state.update { it.copy(isSearching = true, message = null) }
        try {
            val place = localityGeocoder.geocodeLocality(query)
            if (place == null) {
                _state.update {
                    it.copy(
                        isSearching = false,
                        message = AppMessage.Error("Nie znaleziono miejscowości „$query”. Spróbuj innej nazwy."),
                    )
                }
                return
            }
            val origin = UserPosition(place.latitude, place.longitude, approximate = false)
            _state.update {
                it.copy(
                    mapSearchPin = place.toLatLon(),
                    localityDisplayName = place.displayName,
                )
            }
            performSearch(origin)
        } catch (e: UnknownHostException) {
            _state.update {
                it.copy(
                    isSearching = false,
                    message = AppMessage.Error("Brak internetu — geokodowanie miejscowości wymaga sieci."),
                )
            }
        } catch (e: IOException) {
            _state.update {
                it.copy(
                    isSearching = false,
                    message = AppMessage.Error("Geokodowanie: ${e.message ?: "błąd sieci"}"),
                )
            }
        } catch (e: Exception) {
            _state.update {
                it.copy(
                    isSearching = false,
                    message = AppMessage.Error("Miejscowość: ${e.message ?: "błąd"}"),
                )
            }
        }
    }

    private fun refreshLocationThenSearch() {
        viewModelScope.launch {
            _state.update { it.copy(isLocating = true, message = null) }
            when (val outcome = locationProvider.currentLocation(preferFastPath = true)) {
                is LocationOutcome.Exact -> {
                    val pos = UserPosition(outcome.location.latitude, outcome.location.longitude, false)
                    _state.update { it.copy(isLocating = false, userPosition = pos) }
                    performSearch(pos)
                }
                is LocationOutcome.Approximate -> {
                    val pos = UserPosition(outcome.location.latitude, outcome.location.longitude, true)
                    _state.update { it.copy(isLocating = false, userPosition = pos) }
                    performSearch(pos)
                }
                is LocationOutcome.Failure -> {
                    _state.update {
                        it.copy(isLocating = false, message = AppMessage.Error(outcome.reason))
                    }
                }
            }
        }
    }

    private suspend fun performSearch(position: UserPosition) {
        val generation = searchGeneration.incrementAndGet()
        _state.update { it.copy(isSearching = true, isAnalyzingRoads = false, message = null) }
        try {
            val radiusKm = _state.value.searchConfig.searchRadiusKm
            val profile = _state.value.profile
            val offlineVersion = currentOfflineVersion()
            if (BdlSearchSubsetFilter.canReuse(
                    lastBdlSearchContext,
                    position.latitude,
                    position.longitude,
                    radiusKm,
                    offlineVersion,
                )
            ) {
                val subset = BdlSearchSubsetFilter.subset(
                    context = lastBdlSearchContext!!,
                    latitude = position.latitude,
                    longitude = position.longitude,
                    radiusKm = radiusKm,
                    config = _state.value.searchConfig,
                )
                val retainedRoads = _state.value.roadBySiteId.filterKeys { id ->
                    subset.sites.any { it.id == id }
                }
                val token = cameraToken.getAndIncrement()
                publishBdlSearchResults(
                    generation = generation,
                    position = position,
                    sites = subset.sites,
                    zanocujPolygons = subset.zanocujPolygons,
                    radiusKm = radiusKm,
                    cameraToken = token,
                    startRoadAnalysis = profile == TravelProfile.MOTORCYCLE && subset.sites.isNotEmpty(),
                    fromRadiusSubset = true,
                    retainedRoadAssessments = retainedRoads,
                )
                if (profile == TravelProfile.MOTORCYCLE && subset.sites.isNotEmpty()) {
                    analyzeRoadsInBackground(generation, subset.sites, position)
                }
                return
            }
            val outcome = restRepository.findRestSitesWithMeta(
                latitude = position.latitude,
                longitude = position.longitude,
                radiusKm = radiusKm,
            )
            val bundle = outcome.bundle
            lastBdlSearchContext = BdlSearchContext(
                originLat = position.latitude,
                originLon = position.longitude,
                radiusKm = radiusKm,
                offlineVersion = offlineVersion,
                bundle = bundle,
            )
            val token = cameraToken.getAndIncrement()
            publishBdlSearchResults(
                generation = generation,
                position = position,
                sites = bundle.sites,
                zanocujPolygons = bundle.zanocujPolygons,
                radiusKm = radiusKm,
                cameraToken = token,
                startRoadAnalysis = profile == TravelProfile.MOTORCYCLE && bundle.sites.isNotEmpty(),
                fromSessionCache = outcome.fromSessionCache,
            )
            if (profile == TravelProfile.MOTORCYCLE && bundle.sites.isNotEmpty()) {
                analyzeRoadsInBackground(generation, bundle.sites, position)
            }
        } catch (e: UnknownHostException) {
            val offlineReady = _state.value.offlineBdl.isReady
            _state.update {
                it.copy(
                    isSearching = false,
                    isAnalyzingRoads = false,
                    message = AppMessage.Error(
                        if (offlineReady) {
                            "Brak internetu. Analiza dróg OSM wymaga sieci — miejsca BDL z pamięci urządzenia."
                        } else {
                            "Brak internetu. Pobierz dane BDL offline albo połącz się z siecią."
                        },
                    ),
                )
            }
        } catch (e: IOException) {
            _state.update {
                it.copy(
                    isSearching = false,
                    isAnalyzingRoads = false,
                    message = AppMessage.Error("Błąd BDL: ${e.message ?: "sieć"}"),
                )
            }
        } catch (e: Exception) {
            _state.update {
                it.copy(
                    isSearching = false,
                    isAnalyzingRoads = false,
                    message = AppMessage.Error("Wyszukiwanie: ${e.message ?: "błąd"}"),
                )
            }
        }
    }

    private fun publishBdlSearchResults(
        generation: Long,
        position: UserPosition,
        sites: List<RestSite>,
        zanocujPolygons: List<pl.navilas.finder.data.bdl.ZanocujPolygon>,
        radiusKm: Double,
        cameraToken: Long,
        startRoadAnalysis: Boolean,
        fromSessionCache: Boolean = false,
        fromRadiusSubset: Boolean = false,
        retainedRoadAssessments: Map<String, RoadAssessment> = emptyMap(),
    ) {
        if (searchGeneration.get() != generation) return
        _state.update { current ->
            if (searchGeneration.get() != generation) return@update current
            val roadBySiteId = if (retainedRoadAssessments.isNotEmpty()) {
                retainedRoadAssessments
            } else {
                emptyMap()
            }
            val results = buildResults(
                sites,
                position,
                current.profile,
                current.zanocujFilter,
                roadBySiteId = roadBySiteId,
            )
            val originLabel = when {
                current.searchOriginMode == SearchOriginMode.LOCALITY ->
                    "miejscowości ${current.localityQuery.trim().ifBlank { "?" }}"
                current.searchOriginMode == SearchOriginMode.MAP -> "punktu na mapie"
                else -> "GPS"
            }
            val offlineSuffix = if (current.offlineBdl.isReady) " · offline BDL" else ""
            val cacheSuffix = if (fromSessionCache) " · cache" else ""
            val subsetSuffix = if (fromRadiusSubset) " · filtr promienia" else ""
            val roadsPending = startRoadAnalysis && roadBySiteId.size < sites.size
            val motoSuffix = if (roadsPending) " · drogi OSM w tle" else ""
            current.copy(
                isSearching = false,
                isAnalyzingRoads = roadsPending,
                allSites = sites,
                zanocujPolygons = zanocujPolygons,
                roadBySiteId = roadBySiteId,
                results = results,
                selectedSiteId = null,
                currentPage = AppPages.MAP,
                mapCameraRequest = PagerNavigation.cameraForNewSearch(cameraToken),
                message = AppMessage.Info(
                    "Znaleziono ${sites.size} miejsc w promieniu ${radiusKm.toInt()} km od $originLabel$offlineSuffix$cacheSuffix$subsetSuffix$motoSuffix.",
                ),
            )
        }
    }

    private fun analyzeRoadsInBackground(
        generation: Long,
        sites: List<RestSite>,
        position: UserPosition,
    ) {
        viewModelScope.launch {
            try {
                val roadBySiteId = withContext(Dispatchers.IO) {
                    roadAnalyzer.assessAll(sites.map { it.toPointPoi() })
                }
                if (searchGeneration.get() != generation) return@launch
                _state.update { current ->
                    if (searchGeneration.get() != generation) return@update current
                    val next = current.copy(
                        isAnalyzingRoads = false,
                        roadBySiteId = roadBySiteId,
                        results = buildResults(
                            current.allSites,
                            position,
                            current.profile,
                            current.zanocujFilter,
                            roadBySiteId,
                        ),
                    )
                    next.copy(savedListResults = buildSavedListResults(next))
                }
            } catch (e: Exception) {
                if (searchGeneration.get() != generation) return@launch
                _state.update {
                    it.copy(
                        isAnalyzingRoads = false,
                        message = AppMessage.Error(
                            "Miejsca BDL OK, analiza dróg OSM: ${e.message ?: "błąd"}",
                        ),
                    )
                }
            }
        }
    }

    private fun buildResults(
        sites: List<RestSite>,
        position: UserPosition?,
        profile: TravelProfile,
        zanocujFilter: ZanocujFilterMode,
        roadBySiteId: Map<String, RoadAssessment>,
    ): List<RestSiteResult> {
        if (position == null) return emptyList()
        return sites
            .asSequence()
            .filter { site ->
                when (zanocujFilter) {
                    ZanocujFilterMode.ALL -> true
                    ZanocujFilterMode.ONLY_IN_ZONE -> site.zanocujStatus == ZanocujStatus.IN_ZONE
                }
            }
            .map { site ->
                val assessment = if (profile == TravelProfile.MOTORCYCLE) {
                    roadBySiteId[site.id]
                } else {
                    null
                }
                val (target, kind) = when (profile) {
                    TravelProfile.CAR -> NavigationTargets.forCar(site)
                    TravelProfile.MOTORCYCLE -> {
                        NavigationTargets.forMotorcycle(assessment)
                            ?: (LatLon(site.latitude, site.longitude) to NavigationTargetKind.REST_SITE)
                    }
                }
                RestSiteResult(
                    site = site,
                    distanceKm = GeoUtils.distanceKm(
                        position.latitude,
                        position.longitude,
                        site.latitude,
                        site.longitude,
                    ),
                    roadAssessment = assessment,
                    navigationTarget = target,
                    navigationTargetKind = kind,
                )
            }
            .sortedBy { it.distanceKm }
            .toList()
    }
}

private fun RestSite.toPointPoi(): Poi = Poi(
    id = id,
    categories = setOf(PoiCategory.REST),
    name = name,
    latitude = latitude,
    longitude = longitude,
    description = description,
    source = sourceLayerName,
    geometryKind = PoiGeometryKind.POINT,
)

fun RestSiteResult.canNavigateMotorcycle(): Boolean =
    navigationTargetKind == NavigationTargetKind.OSM_ROAD &&
        roadAssessment?.nearestRoad != null &&
        roadAssessment.roadSuitability != null &&
        roadAssessment.roadSuitability != pl.navilas.finder.domain.RoadSuitability.REJECTED
