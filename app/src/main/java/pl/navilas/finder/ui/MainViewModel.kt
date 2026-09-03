package pl.navilas.finder.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import pl.navilas.finder.data.bdl.BrowseCarFilterMatcher
import pl.navilas.finder.data.bdl.BdlOfflineDownloader
import pl.navilas.finder.data.bdl.BdlOfflineStore
import pl.navilas.finder.data.bdl.BdlSearchContext
import pl.navilas.finder.data.bdl.BdlSearchSubsetFilter
import pl.navilas.finder.data.bdl.BdlOverlayLoader
import pl.navilas.finder.data.bdl.NearbyOverlayObjects
import pl.navilas.finder.data.bdl.OfflineMapBrowseLoader
import pl.navilas.finder.data.bdl.RestSiteRepository
import pl.navilas.finder.data.cache.BdlSearchSessionCache
import pl.navilas.finder.data.cache.PersistentOsmRoadTileStore
import pl.navilas.finder.data.cache.RoadAssessmentCache
import pl.navilas.finder.data.preferences.StartupMode
import pl.navilas.finder.data.preferences.UiPreferences
import pl.navilas.finder.data.osm.CachingOverpassRoadClient
import pl.navilas.finder.data.osm.MotorcycleAccessHint
import pl.navilas.finder.data.osm.NominatimGeocoder
import pl.navilas.finder.data.osm.OfficialApproachEntry
import pl.navilas.finder.data.osm.OverpassRoadClient
import pl.navilas.finder.data.osm.PersistentLocalityGeocodeStore
import pl.navilas.finder.data.osm.RoadProximityAnalyzer
import pl.navilas.finder.data.saved.SavedPointsStore
import pl.navilas.finder.data.saved.SavedPointsBackupCodec
import pl.navilas.finder.data.saved.SavedPointsBackupParseResult
import pl.navilas.finder.data.saved.SavedPointsBackupSnapshot
import pl.navilas.finder.data.saved.SavedPointsImportMode
import pl.navilas.finder.data.saved.SavedPointsImportResult
import pl.navilas.finder.domain.BdlOverlayFilter
import pl.navilas.finder.domain.BdlOverlayPoint
import pl.navilas.finder.domain.SiteSelection
import pl.navilas.finder.domain.BrowseCarFilter
import pl.navilas.finder.domain.BrowseMapCluster
import pl.navilas.finder.domain.isUsableBrowseViewport
import pl.navilas.finder.domain.selectBrowseContent
import pl.navilas.finder.domain.BrowseParkingProximityMode
import pl.navilas.finder.data.bdl.ForestEntryBanBounds
import pl.navilas.finder.data.bdl.ForestEntryBanClassifier
import pl.navilas.finder.data.bdl.ForestEntryBanLoader
import pl.navilas.finder.data.bdl.ForestEntryBanStore
import pl.navilas.finder.domain.EntryBanRefreshOffer
import pl.navilas.finder.domain.ENTRY_BAN_REFRESH_SNOOZE_MS
import pl.navilas.finder.domain.ENTRY_BAN_REFRESH_STALE_MS
import pl.navilas.finder.domain.ForestEntryBan
import pl.navilas.finder.domain.MapTrackingMode
import pl.navilas.finder.domain.AppExploreMode
import pl.navilas.finder.domain.AppMessage
import pl.navilas.finder.domain.BdlDataScope
import pl.navilas.finder.domain.CorridorVertexAction
import pl.navilas.finder.domain.OfflineBdlConfig
import pl.navilas.finder.domain.estimatedSizeLabel
import pl.navilas.finder.domain.OfflineBdlState
import pl.navilas.finder.domain.BdlRefreshOffer
import pl.navilas.finder.domain.BDL_REFRESH_SNOOZE_MS
import pl.navilas.finder.domain.OfflineBdlStatus
import pl.navilas.finder.domain.shouldOfferBdlRefresh
import pl.navilas.finder.domain.ZanocujPolygonQuality
import pl.navilas.finder.domain.LatLon
import pl.navilas.finder.domain.LocalityPickPurpose
import pl.navilas.finder.domain.NavigationTargetKind
import pl.navilas.finder.data.osm.GeocodedPlace
import pl.navilas.finder.location.LastGpsPreferences
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
import pl.navilas.finder.domain.ZanocujStatus
import pl.navilas.finder.location.AppLocationProvider
import pl.navilas.finder.location.LocationOutcome
import pl.navilas.finder.nav.NavigationTargets
import pl.navilas.finder.util.CorridorGeometry
import pl.navilas.finder.util.GeoUtils
import pl.navilas.finder.BuildConfig
import pl.navilas.finder.R
import pl.navilas.finder.update.AppUpdateChecker
import pl.navilas.finder.update.UpdateTrack
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

internal fun savedExploreMode(value: String?): AppExploreMode =
    value?.let { saved ->
        AppExploreMode.entries.firstOrNull { it.name == saved }
    } ?: AppExploreMode.SEARCH

internal fun startupExploreMode(mode: StartupMode, savedValue: String?): AppExploreMode =
    when (mode) {
        StartupMode.SEARCH -> AppExploreMode.SEARCH
        StartupMode.MAP_BROWSE -> AppExploreMode.MAP_BROWSE
        StartupMode.REMEMBER_LAST -> savedExploreMode(savedValue)
    }

internal fun searchCriteriaSummaryPl(
    origin: SearchOriginMode,
    radiusKm: Double,
    localityQuery: String,
    corridorPointCount: Int,
    corridorLeftKm: Double,
    corridorRightKm: Double,
): String = when (origin) {
    SearchOriginMode.GPS -> "${radiusKm.toInt()} km · od GPS"
    SearchOriginMode.MAP -> "${radiusKm.toInt()} km · od mapa"
    SearchOriginMode.LOCALITY -> {
        val name = localityQuery.trim().ifBlank { "?" }
        "${radiusKm.toInt()} km · od $name"
    }
    SearchOriginMode.LINE ->
        "linia: $corridorPointCount pkt L${corridorLeftKm.toInt()}/P${corridorRightKm.toInt()}"
}

internal fun exploreModeTargetPage(
    mode: AppExploreMode,
    stayOnPage: Boolean,
    currentPage: Int,
): Int = if (stayOnPage) {
    currentPage
} else {
    when (mode) {
        AppExploreMode.SEARCH -> AppPages.SEARCH
        AppExploreMode.MAP_BROWSE -> AppPages.MAP
    }
}

data class UiState(
    val profile: TravelProfile = TravelProfile.CAR,
    val exploreMode: AppExploreMode = AppExploreMode.SEARCH,
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
    /** Polyline vertices for LINE corridor search (2+ required to search). */
    val corridorLine: List<LatLon> = emptyList(),
    val corridorLeftKm: Double = DEFAULT_CORRIDOR_LEFT_KM,
    val corridorRightKm: Double = DEFAULT_CORRIDOR_RIGHT_KM,
    val corridorVertexAction: CorridorVertexAction? = null,
    /** Waiting for second map tap to finish GPS→map line shortcut. */
    val corridorAwaitMapEnd: Boolean = false,
    /** Nominatim candidates for locality picker (null = no picker). */
    val localityCandidates: List<pl.navilas.finder.data.osm.GeocodedPlace>? = null,
    /** When set, locality pick seeds corridor instead of radial search. */
    val localityPickPurpose: LocalityPickPurpose = LocalityPickPurpose.SEARCH_ORIGIN,
    val allSites: List<RestSite> = emptyList(),
    val zanocujPolygons: List<pl.navilas.finder.data.bdl.ZanocujPolygon> = emptyList(),
    val roadBySiteId: Map<String, RoadAssessment> = emptyMap(),
    val results: List<RestSiteResult> = emptyList(),
    val selectedSiteIds: List<String> = emptyList(),
    val currentPage: Int = AppPages.SEARCH,
    val mapCameraRequest: MapCameraRequest? = null,
    /**
     * Live GPS follow while driving. [MapTrackingMode.TRACKING] keeps the puck at a screen
     * focal point; gestures and the track FAB move to [MapTrackingMode.PAUSED].
     */
    val mapTrackingMode: MapTrackingMode = MapTrackingMode.OFF,
    /** Monotonic counter: Activity applies follow camera when this changes in TRACKING. */
    val mapFollowRevision: Long = 0L,
    val isLocating: Boolean = false,
    val isSearching: Boolean = false,
    /** Moto profile: OSM road analysis running after BDL results are shown. */
    val isAnalyzingRoads: Boolean = false,
    val roadAnalysisCompleted: Int = 0,
    val roadAnalysisTotal: Int = 0,
    /** Amenity filtering/sorting runs off the UI thread. */
    val isFilteringPlaces: Boolean = false,
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
    val bdlRefreshOffer: BdlRefreshOffer? = null,
    val appUpdateDownloading: Boolean = false,
    val appUpdateDownloadPercent: Int? = null,
    val appUpdateInstallFile: File? = null,
    val appUpdateError: String? = null,
    /** MapBrowse: permanent offline layer loaded (revision bumps when GeoJSON must reload). */
    val mapBrowseRevision: Long = 0L,
    val isMapBrowseLoading: Boolean = false,
    /** Rest-site points currently sent to MapLibre in Browse mode. */
    val browseViewportSites: List<RestSite> = emptyList(),
    val browseMapClusters: List<BrowseMapCluster> = emptyList(),
    /** Amenity / Zanocuj filters (browse + search, car + moto). */
    val browseCarFilter: BrowseCarFilter = BrowseCarFilter.DEFAULT,
    /** Precomputed IDs for Browse marker filtering; null means no active filter. */
    val browseFilterMatchingIds: Set<String>? = null,
    /** Extra BDL objects overlay (Search and Browse). Off by default. */
    val bdlOverlayFilter: BdlOverlayFilter = BdlOverlayFilter(),
    val bdlOverlayViewport: List<BdlOverlayPoint> = emptyList(),
    val bdlOverlayFullAvailable: Boolean = false,
    /** Periodic BDL forest-entry bans drawn like Zanocuj; filter hides sites inside them. */
    val entryBanViewport: List<ForestEntryBan> = emptyList(),
    val entryBanStatus: String? = null,
    val entryBanPackDownloadedAt: Long? = null,
    val entryBanPackCount: Int = 0,
    val entryBanDownloading: Boolean = false,
    val entryBanRefreshOffer: EntryBanRefreshOffer? = null,
) {
    fun activeListResults(): List<RestSiteResult> = when (listViewMode) {
        ListViewMode.SEARCH -> results
        ListViewMode.SAVED -> savedListResults
    }

    val selectedSiteId: String? get() = selectedSiteIds.lastOrNull()

    fun isSaved(siteId: String): Boolean = savedPoints.containsKey(siteId)

    fun savedPoint(siteId: String): SavedPoint? = savedPoints[siteId]
    /** Position used for BDL search radius and result distances. */
    fun searchOrigin(): UserPosition? = when (searchOriginMode) {
        SearchOriginMode.MAP, SearchOriginMode.LOCALITY ->
            mapSearchPin?.let { UserPosition(it.latitude, it.longitude, approximate = false) }
        SearchOriginMode.LINE ->
            corridorLine.firstOrNull()?.let {
                UserPosition(it.latitude, it.longitude, approximate = false)
            }
        SearchOriginMode.GPS -> userPosition
    }

    fun usesMapPinForSearch(): Boolean =
        searchOriginMode == SearchOriginMode.MAP && mapSearchPin != null

    fun usesLocalityForSearch(): Boolean =
        searchOriginMode == SearchOriginMode.LOCALITY && mapSearchPin != null

    fun usesCorridorForSearch(): Boolean =
        searchOriginMode == SearchOriginMode.LINE && corridorLine.size >= 2

    fun isMapBrowse(): Boolean = exploreMode == AppExploreMode.MAP_BROWSE

    fun entryBanAt(latitude: Double, longitude: Double): ForestEntryBan? =
        ForestEntryBanClassifier.containing(latitude, longitude, entryBanViewport)

    companion object {
        const val DEFAULT_CORRIDOR_LEFT_KM = 5.0
        const val DEFAULT_CORRIDOR_RIGHT_KM = 10.0
        const val MOTORCYCLE_ROAD_ANALYZE_LIMIT = 50
        const val MAX_CORRIDOR_SIDE_KM = 50.0
    }
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val bdlSessionCache = BdlSearchSessionCache()
    private val roadAssessmentCache = RoadAssessmentCache()
    private val osmTileStore = PersistentOsmRoadTileStore.fromAppFilesDir(application.filesDir)
    private val localityStore = PersistentLocalityGeocodeStore.fromAppFilesDir(application.filesDir)
    private val lastGpsPreferences = LastGpsPreferences(application)
    private val locationProvider = AppLocationProvider(
        context = application,
        lastGpsPreferences = lastGpsPreferences,
    )
    private val offlineStore = BdlOfflineStore.fromAppFilesDir(application.filesDir)
    private val offlineDownloader = BdlOfflineDownloader(filesDir = application.filesDir)
    private val restRepository = RestSiteRepository(
        offlineStore = offlineStore,
        sessionCache = bdlSessionCache,
        config = SearchConfig.DEFAULT,
    )
    private val forestEntryBanLoader = ForestEntryBanLoader()
    private val forestEntryBanStore = ForestEntryBanStore.fromAppFilesDir(application.filesDir)
    private val roadAnalyzer = RoadProximityAnalyzer(
        overpass = CachingOverpassRoadClient(tileCache = osmTileStore),
        assessmentCache = roadAssessmentCache,
    )
    private val localityGeocoder = NominatimGeocoder(
        localityStore = localityStore,
        voivodeshipCacheFile = File(application.filesDir, "county_voivodeship_cache.json"),
    )
    private val savedPointsStore = SavedPointsStore.fromAppFilesDir(application.filesDir)
    private val appUpdatePrefs = AppUpdatePreferences(application)
    private val appUpdateChecker = AppUpdateChecker(
        manifestsByTrack = mapOf(
            UpdateTrack.NIGHTLY to BuildConfig.UPDATE_MANIFEST_NIGHTLY_URL,
            UpdateTrack.BETA to BuildConfig.UPDATE_MANIFEST_URL,
            UpdateTrack.FINAL to BuildConfig.UPDATE_MANIFEST_FINAL_URL,
        ),
    )
    private val appUpdateDownloader = AppUpdateDownloader()
    private val uiPreferences = UiPreferences(application)
    private val exploreModePrefs =
        application.getSharedPreferences(EXPLORE_MODE_PREFS, Application.MODE_PRIVATE)
    private val cameraToken = AtomicLong(1L)
    private val searchGeneration = AtomicLong(0L)
    private val mapBrowseGeneration = AtomicLong(0L)
    private val filterGeneration = AtomicLong(0L)
    private var lastBdlSearchContext: BdlSearchContext? = null
    private var pendingCorridorLocalityStart: LatLon? = null
    private var liveGpsCentered = false
    /** Browse-only: full Zanocuj geometries for viewport clips (not drawn nationwide). */
    @Volatile
    private var browseZanocujIndex: List<pl.navilas.finder.data.bdl.ZanocujBoundsPolygon> = emptyList()
    @Volatile
    private var browseOverlayIndex: List<BdlOverlayPoint> = emptyList()
    private var browseOverlayById: Map<String, BdlOverlayPoint> = emptyMap()
    private var lastBrowseViewportKey: String? = null
    private var lastBrowseBounds: GeoUtils.Envelope? = null
    private var lastBrowseZoom: Double = 0.0
    private var lastBrowseCenterLat: Double = 0.0
    private var lastBrowseCenterLon: Double = 0.0
    private var mapBrowseLoadJob: Job? = null
    private var browseViewportJob: Job? = null
    @Volatile
    private var entryBanIndex: List<ForestEntryBanBounds> = emptyList()
    private var overlayLoadJob: Job? = null
    private val overlayIndexMutex = Mutex()
    private var entryBanDownloadJob: Job? = null
    private var selectedRoadsJob: Job? = null
    private val selectedRoadsGeneration = AtomicLong(0L)
    private var ignoreEmptyMapClickUntilElapsedMs = 0L
    private var filterResultsJob: Job? = null
    private var mapTrackingJob: Job? = null
    private var bdlRefreshCheckedThisSession = false
    private var lastFollowAppliedLat: Double? = null
    private var lastFollowAppliedLon: Double? = null
    /** True while user pans/zooms/rotates — camera follow suspended until idle. */
    @Volatile
    private var mapTrackingGestureHold = false
    private val mapFollowRevision = AtomicLong(0L)

    /** Screen focal for follow (Activity sets when starting/resuming). */
    @Volatile
    var followFocalScreenX: Float = 0f
    @Volatile
    var followFocalScreenY: Float = 0f
    @Volatile
    var followBearing: Double = 0.0
    @Volatile
    var followZoom: Double = 14.0

    private val _state = MutableStateFlow(loadInitialState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private fun loadInitialState(): UiState {
        val initialExploreMode = loadSavedExploreMode()
        val saved = savedPointsStore.allPoints().associateBy { it.site.id }
        val categories = savedPointsStore.allCategories()
        val lastGps = lastGpsPreferences.load()
        val user = lastGps?.let {
            UserPosition(it.latitude, it.longitude, it.approximate)
        }
        val camera = if (lastGps != null) {
            PagerNavigation.cameraForUserLocation(
                latitude = lastGps.latitude,
                longitude = lastGps.longitude,
                token = cameraToken.getAndIncrement(),
                zoom = 11.5,
            )
        } else {
            null
        }
        val base = UiState(
            exploreMode = initialExploreMode,
            currentPage = if (initialExploreMode == AppExploreMode.MAP_BROWSE) {
                AppPages.MAP
            } else {
                AppPages.SEARCH
            },
            offlineBdl = loadOfflineState(),
            savedPoints = saved,
            savedCategories = categories,
            userPosition = user,
            mapCameraRequest = camera,
            entryBanPackDownloadedAt = forestEntryBanStore.downloadedAt(),
            entryBanPackCount = forestEntryBanStore.count(),
        )
        loadEntryBanIndexFromDisk()
        return base.copy(savedListResults = buildSavedListResults(base))
    }

    private fun loadSavedExploreMode(): AppExploreMode {
        val saved = exploreModePrefs.getString(EXPLORE_MODE_KEY, null)
        return startupExploreMode(uiPreferences.startupMode, saved)
    }

    private fun saveExploreMode(mode: AppExploreMode) {
        exploreModePrefs.edit().putString(EXPLORE_MODE_KEY, mode.name).apply()
    }

    init {
        refreshOfflineStateFromDisk()
        if (_state.value.isMapBrowse()) {
            loadMapBrowseLayer(force = false)
        }
        if (BuildConfig.APP_UPDATE_ENABLED) {
            viewModelScope.launch {
                delay(2_000)
                checkForAppUpdate(force = false)
            }
        }
        viewModelScope.launch {
            delay(2_000)
            maybeOfferBdlRefresh()
            maybeOfferEntryBanRefresh()
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
            val configChanged = stored != null && !newPending.matches(stored)
            // Keep the live offline DB until a successful re-download of the new config.
            current.copy(
                offlineBdl = current.offlineBdl.copy(pendingConfig = newPending),
                message = if (configChanged) {
                    AppMessage.Info(
                        "Zmieniono ustawienia offline — pobierz ponownie, aby je zastosować. " +
                            "Dotychczasowa baza działa do skutku aktualizacji.",
                    )
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
                uiPreferences.bdlRefreshSnoozeUntilMs = 0L
                _state.update {
                    it.copy(
                        offlineBdl = loadOfflineState().copy(pendingConfig = config),
                        bdlRefreshOffer = null,
                        message = AppMessage.Info(
                            if (_state.value.isMapBrowse()) {
                                "Dane BDL pobrane (${config.estimatedSizeLabel()}). Ładuję punkty na mapę…"
                            } else {
                                "Dane BDL pobrane (${config.estimatedSizeLabel()}). Wyszukiwanie działa offline."
                            },
                        ),
                    )
                }
                invalidateBdlSessionCache()
                if (_state.value.isMapBrowse()) {
                    loadMapBrowseLayer(force = true)
                }
            } catch (e: UnknownHostException) {
                _state.update {
                    it.copy(
                        offlineBdl = loadOfflineState().copy(
                            pendingConfig = config,
                            status = if (offlineStore.isReady()) {
                                OfflineBdlStatus.READY
                            } else {
                                OfflineBdlStatus.ERROR
                            },
                            errorMessage = "Brak internetu.",
                        ),
                        message = AppMessage.Error(
                            if (offlineStore.isReady()) {
                                "Pobieranie BDL: brak internetu. Dotychczasowa baza bez zmian."
                            } else {
                                "Pobieranie BDL: brak internetu."
                            },
                        ),
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        offlineBdl = loadOfflineState().copy(
                            pendingConfig = config,
                            status = if (offlineStore.isReady()) {
                                OfflineBdlStatus.READY
                            } else {
                                OfflineBdlStatus.ERROR
                            },
                            errorMessage = e.message ?: "błąd pobierania",
                        ),
                        message = AppMessage.Error(
                            if (offlineStore.isReady()) {
                                "Pobieranie BDL nieudane (${e.message ?: "błąd"}). Dotychczasowa baza bez zmian."
                            } else {
                                "Pobieranie BDL: ${e.message ?: "błąd"}"
                            },
                        ),
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
        uiPreferences.bdlRefreshSnoozeUntilMs = 0L
        _state.update {
            it.copy(
                offlineBdl = OfflineBdlState(pendingConfig = pending),
                bdlRefreshOffer = null,
                allSites = if (it.isMapBrowse()) emptyList() else it.allSites,
                results = if (it.isMapBrowse()) emptyList() else it.results,
                zanocujPolygons = if (it.isMapBrowse()) emptyList() else it.zanocujPolygons,
                mapBrowseRevision = if (it.isMapBrowse()) it.mapBrowseRevision + 1 else it.mapBrowseRevision,
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
        val clamped = radiusKm.coerceIn(SearchConfig.MIN_SEARCH_RADIUS_KM, SearchConfig.MAX_SEARCH_RADIUS_KM)
        _state.update { current ->
            current.copy(
                searchConfig = current.searchConfig.copy(searchRadiusKm = clamped),
            )
        }
    }

    fun setCorridorLeftKm(km: Double) {
        val clamped = km.coerceIn(0.0, UiState.MAX_CORRIDOR_SIDE_KM)
        _state.update { it.copy(corridorLeftKm = clamped) }
    }

    fun setCorridorRightKm(km: Double) {
        val clamped = km.coerceIn(0.0, UiState.MAX_CORRIDOR_SIDE_KM)
        _state.update { it.copy(corridorRightKm = clamped) }
    }

    fun appendCorridorPoint(latitude: Double, longitude: Double) {
        val point = LatLon(latitude, longitude)
        _state.update { current ->
            val action = current.corridorVertexAction
            when (action) {
                is CorridorVertexAction.Move -> {
                    if (action.index !in current.corridorLine.indices) {
                        return@update current.copy(
                            corridorVertexAction = null,
                            message = AppMessage.Error("Nieprawidłowy punkt linii."),
                        )
                    }
                    val next = current.corridorLine.toMutableList().also { it[action.index] = point }
                    current.copy(
                        corridorLine = next,
                        corridorVertexAction = null,
                        message = AppMessage.Info("Przesunięto punkt ${action.index + 1}."),
                    )
                }
                is CorridorVertexAction.InsertAfter -> {
                    val insertAt = (action.index + 1).coerceIn(0, current.corridorLine.size)
                    val next = current.corridorLine.toMutableList().also { it.add(insertAt, point) }
                    current.copy(
                        corridorLine = next,
                        corridorVertexAction = null,
                        message = AppMessage.Info("Dodano punkt między wierzchołkami (${next.size} pkt)."),
                    )
                }
                null -> {
                    if (current.corridorAwaitMapEnd) {
                        val start = current.userPosition?.let { LatLon(it.latitude, it.longitude) }
                            ?: current.corridorLine.firstOrNull()
                        if (start == null) {
                            return@update current.copy(
                                corridorAwaitMapEnd = false,
                                message = AppMessage.Error("Brak punktu GPS — włącz lokalizację."),
                            )
                        }
                        current.copy(
                            searchOriginMode = SearchOriginMode.LINE,
                            corridorLine = listOf(start, point),
                            corridorAwaitMapEnd = false,
                            mapSearchPin = null,
                            message = AppMessage.Info("Linia GPS → mapa gotowa (2 pkt)."),
                            currentPage = AppPages.MAP,
                        )
                    } else {
                        current.copy(
                            searchOriginMode = SearchOriginMode.LINE,
                            corridorLine = current.corridorLine + point,
                            mapSearchPin = null,
                            localityDisplayName = null,
                            message = AppMessage.Info(
                                "Linia: ${current.corridorLine.size + 1} pkt. " +
                                    if (current.corridorLine.size + 1 >= 2) {
                                        "Możesz wyszukać lub edytować punkty na mapie."
                                    } else {
                                        "Dodaj kolejny punkt."
                                    },
                            ),
                        )
                    }
                }
            }
        }
    }

    fun beginMoveCorridorPoint(index: Int) {
        _state.update {
            it.copy(
                corridorVertexAction = CorridorVertexAction.Move(index),
                message = AppMessage.Info("Dotknij mapy, aby przesunąć punkt ${index + 1}."),
                currentPage = AppPages.MAP,
            )
        }
    }

    fun beginInsertCorridorPoint(index: Int) {
        _state.update {
            it.copy(
                corridorVertexAction = CorridorVertexAction.InsertAfter(index),
                message = AppMessage.Info("Dotknij mapy, aby wstawić punkt za nr ${index + 1}."),
                currentPage = AppPages.MAP,
            )
        }
    }

    fun deleteCorridorPoint(index: Int) {
        _state.update { current ->
            if (index !in current.corridorLine.indices) current
            else current.copy(
                corridorLine = current.corridorLine.filterIndexed { i, _ -> i != index },
                corridorVertexAction = null,
                message = AppMessage.Info("Usunięto punkt ${index + 1}."),
            )
        }
    }

    fun cancelCorridorVertexAction() {
        _state.update { it.copy(corridorVertexAction = null, corridorAwaitMapEnd = false) }
    }

    fun clearCorridorLine() {
        _state.update {
            it.copy(
                corridorLine = emptyList(),
                corridorVertexAction = null,
                corridorAwaitMapEnd = false,
                message = AppMessage.Info("Wyczyszczono linię wyszukiwania."),
            )
        }
    }

    fun startCorridorGpsToMap() {
        viewModelScope.launch {
            var gps = _state.value.userPosition
            if (gps == null) {
                when (val outcome = locationProvider.currentLocation(preferFastPath = true)) {
                    is LocationOutcome.Exact ->
                        gps = UserPosition(outcome.location.latitude, outcome.location.longitude, false)
                    is LocationOutcome.Approximate ->
                        gps = UserPosition(outcome.location.latitude, outcome.location.longitude, true)
                    is LocationOutcome.Failure -> {
                        _state.update { it.copy(message = AppMessage.Error(outcome.reason)) }
                        return@launch
                    }
                }
            }
            val start = LatLon(gps!!.latitude, gps.longitude)
            _state.update {
                it.copy(
                    userPosition = gps,
                    searchOriginMode = SearchOriginMode.LINE,
                    corridorLine = listOf(start),
                    corridorAwaitMapEnd = true,
                    corridorVertexAction = null,
                    currentPage = AppPages.MAP,
                    message = AppMessage.Info("Dotknij mapy, aby ustawić koniec linii (GPS → mapa)."),
                )
            }
        }
    }

    fun startCorridorGpsToLocality() {
        _state.update {
            it.copy(
                searchOriginMode = SearchOriginMode.LINE,
                localityPickPurpose = LocalityPickPurpose.CORRIDOR_END_FROM_GPS,
                localityCandidates = null,
                message = AppMessage.Info("Wpisz miejscowość końca linii i naciśnij Znajdź."),
                currentPage = AppPages.SEARCH,
            )
        }
    }

    fun startCorridorLocalityToLocality() {
        pendingCorridorLocalityStart = null
        _state.update {
            it.copy(
                searchOriginMode = SearchOriginMode.LINE,
                localityPickPurpose = LocalityPickPurpose.CORRIDOR_START,
                localityCandidates = null,
                localityQuery = "",
                message = AppMessage.Info("Wpisz miejscowość START i naciśnij Znajdź."),
                currentPage = AppPages.SEARCH,
            )
        }
    }

    fun dismissLocalityCandidates() {
        _state.update { it.copy(localityCandidates = null) }
    }

    fun applyLocalityChoice(place: GeocodedPlace) {
        val query = _state.value.localityQuery.trim()
        localityGeocoder.rememberChoice(query, place)
        when (_state.value.localityPickPurpose) {
            LocalityPickPurpose.SEARCH_ORIGIN -> {
                val origin = UserPosition(place.latitude, place.longitude, approximate = false)
                _state.update {
                    it.copy(
                        mapSearchPin = place.toLatLon(),
                        localityDisplayName = place.displayName,
                        localityCandidates = null,
                        searchOriginMode = SearchOriginMode.LOCALITY,
                    )
                }
                viewModelScope.launch { performSearch(origin) }
            }
            LocalityPickPurpose.CORRIDOR_END_FROM_GPS -> {
                viewModelScope.launch {
                    var gps = _state.value.userPosition
                    if (gps == null) {
                        when (val outcome = locationProvider.currentLocation(preferFastPath = true)) {
                            is LocationOutcome.Exact ->
                                gps = UserPosition(outcome.location.latitude, outcome.location.longitude, false)
                            is LocationOutcome.Approximate ->
                                gps = UserPosition(outcome.location.latitude, outcome.location.longitude, true)
                            is LocationOutcome.Failure -> {
                                _state.update {
                                    it.copy(
                                        localityCandidates = null,
                                        message = AppMessage.Error(outcome.reason),
                                    )
                                }
                                return@launch
                            }
                        }
                    }
                    val start = LatLon(gps!!.latitude, gps.longitude)
                    val end = place.toLatLon()
                    _state.update {
                        it.copy(
                            userPosition = gps,
                            searchOriginMode = SearchOriginMode.LINE,
                            corridorLine = listOf(start, end),
                            localityCandidates = null,
                            localityPickPurpose = LocalityPickPurpose.SEARCH_ORIGIN,
                            localityDisplayName = place.displayName,
                            currentPage = AppPages.MAP,
                            message = AppMessage.Info("Linia GPS → ${place.displayName.substringBefore(',')}."),
                        )
                    }
                }
            }
            LocalityPickPurpose.CORRIDOR_START -> {
                pendingCorridorLocalityStart = place.toLatLon()
                _state.update {
                    it.copy(
                        localityCandidates = null,
                        localityPickPurpose = LocalityPickPurpose.CORRIDOR_END_AFTER_START,
                        localityQuery = "",
                        message = AppMessage.Info(
                            "Start: ${place.displayName.substringBefore(',')}. Wpisz miejscowość KONIEC i Znajdź.",
                        ),
                    )
                }
            }
            LocalityPickPurpose.CORRIDOR_END_AFTER_START -> {
                val start = pendingCorridorLocalityStart
                pendingCorridorLocalityStart = null
                if (start == null) {
                    _state.update {
                        it.copy(
                            localityCandidates = null,
                            localityPickPurpose = LocalityPickPurpose.SEARCH_ORIGIN,
                            message = AppMessage.Error("Brak punktu start — spróbuj ponownie."),
                        )
                    }
                    return
                }
                _state.update {
                    it.copy(
                        searchOriginMode = SearchOriginMode.LINE,
                        corridorLine = listOf(start, place.toLatLon()),
                        localityCandidates = null,
                        localityPickPurpose = LocalityPickPurpose.SEARCH_ORIGIN,
                        localityDisplayName = place.displayName,
                        currentPage = AppPages.MAP,
                        message = AppMessage.Info("Linia między miejscowościami gotowa."),
                    )
                }
            }
        }
    }

    fun setSearchOriginMode(mode: SearchOriginMode) {
        _state.update { current ->
            current.copy(
                searchOriginMode = mode,
                localityCandidates = null,
                localityPickPurpose = if (mode == SearchOriginMode.LINE) {
                    current.localityPickPurpose
                } else {
                    LocalityPickPurpose.SEARCH_ORIGIN
                },
                results = buildResults(
                    current.allSites,
                    when (mode) {
                        SearchOriginMode.GPS -> current.userPosition
                        SearchOriginMode.MAP, SearchOriginMode.LOCALITY -> current.mapSearchPin?.let {
                            UserPosition(it.latitude, it.longitude, approximate = false)
                        }
                        SearchOriginMode.LINE -> current.corridorLine.firstOrNull()?.let {
                            UserPosition(it.latitude, it.longitude, approximate = false)
                        }
                    },
                    current.profile,
                    current.browseCarFilter,
                    current.roadBySiteId,
                    corridorLine = if (mode == SearchOriginMode.LINE) current.corridorLine else emptyList(),
                ),
            )
        }
    }

    fun setLocalityQuery(query: String) {
        _state.update { current ->
            if (current.localityQuery == query) {
                current
            } else {
                current.copy(localityQuery = query, localityCandidates = null)
            }
        }
    }

    fun setProfile(profile: TravelProfile) {
        _state.update { current ->
            val motoBrowseTip = current.isMapBrowse() &&
                profile == TravelProfile.MOTORCYCLE &&
                current.profile != TravelProfile.MOTORCYCLE
            val next = current.copy(
                profile = profile,
                message = if (motoBrowseTip) {
                    AppMessage.Info(
                        getApplication<Application>().getString(R.string.map_browse_moto_hint),
                    )
                } else {
                    current.message
                },
                results = if (current.isMapBrowse()) {
                    browseSelectionResults(current.copy(profile = profile), current.selectedSiteIds)
                } else {
                    buildResults(
                        current.allSites,
                        current.searchOrigin(),
                        profile,
                        current.browseCarFilter,
                        current.roadBySiteId,
                        corridorLine = if (current.searchOriginMode == SearchOriginMode.LINE) {
                            current.corridorLine
                        } else {
                            emptyList()
                        },
                    )
                },
            )
            next.copy(savedListResults = buildSavedListResults(next))
        }
    }

    fun setBrowseCarFilter(filter: BrowseCarFilter) {
        val generation = filterGeneration.incrementAndGet()
        filterResultsJob?.cancel()
        _state.update { current ->
            current.copy(
                browseCarFilter = filter,
                isFilteringPlaces = current.allSites.isNotEmpty(),
                browseFilterMatchingIds = null,
                message = if (
                    filter.requireParking &&
                    filter.parkingMode == BrowseParkingProximityMode.MAX_DISTANCE &&
                    filter.parkingMaxMeters >= LARGE_PARKING_FILTER_WARNING_METERS
                ) {
                    AppMessage.Info(
                        "Duży promień parkingu (${filter.parkingMaxMeters} m) może wydłużyć filtrowanie.",
                    )
                } else {
                    current.message
                },
            )
        }
        val snapshot = _state.value
        if (snapshot.allSites.isEmpty()) {
            _state.update { it.copy(isFilteringPlaces = false) }
            return
        }
        filterResultsJob = viewModelScope.launch {
            if (snapshot.isMapBrowse()) {
                val matchingIds = withContext(Dispatchers.Default) {
                    matchingSiteIds(snapshot.allSites, filter)
                }
                if (filterGeneration.get() != generation) return@launch
                _state.update { current ->
                    if (!current.isMapBrowse() || current.browseCarFilter != filter) {
                        return@update current
                    }
                    current.copy(
                        browseFilterMatchingIds = matchingIds,
                        isFilteringPlaces = false,
                    )
                }
                val user = _state.value.userPosition
                val bounds = lastBrowseBounds ?: if (user != null) {
                    GeoUtils.envelopeAround(
                        user.latitude,
                        user.longitude,
                        BROWSE_INITIAL_VIEWPORT_RADIUS_KM,
                    )
                } else {
                    POLAND_BROWSE_ENVELOPE
                }
                scheduleBrowseViewportRefresh(
                    bounds,
                    user?.latitude ?: 52.1,
                    user?.longitude ?: 19.4,
                    if (lastBrowseBounds != null) {
                        lastBrowseZoom
                    } else if (user != null) {
                        BROWSE_INITIAL_USER_ZOOM
                    } else {
                        5.5
                    },
                )
            } else {
                val results = withContext(Dispatchers.Default) {
                    rebuildResults(snapshot)
                }
                if (filterGeneration.get() != generation) return@launch
                _state.update { current ->
                    if (current.isMapBrowse() || current.browseCarFilter != filter) {
                        return@update current
                    }
                    current.copy(results = results, isFilteringPlaces = false)
                }
            }
        }
    }

    /** null = show all markers; otherwise visible site ids for browse amenity filter. */
    fun browseCarMatchingIds(state: UiState = _state.value): Set<String>? {
        if (!state.isMapBrowse()) return null
        return state.browseFilterMatchingIds
    }

    private fun rebuildResults(state: UiState): List<RestSiteResult> {
        if (state.isMapBrowse()) {
            return browseSelectionResults(state, state.selectedSiteIds)
        }
        if (state.allSites.isEmpty()) return emptyList()
        return buildResults(
            state.allSites,
            state.searchOrigin(),
            state.profile,
            state.browseCarFilter,
            state.roadBySiteId,
            corridorLine = if (state.searchOriginMode == SearchOriginMode.LINE) {
                state.corridorLine
            } else {
                emptyList()
            },
        )
    }

    fun setExploreMode(mode: AppExploreMode, stayOnPage: Boolean = false) {
        if (_state.value.exploreMode == mode) return
        saveExploreMode(mode)
        when (mode) {
            AppExploreMode.SEARCH -> {
                mapBrowseGeneration.incrementAndGet()
                mapBrowseLoadJob?.cancel()
                mapBrowseLoadJob = null
                filterGeneration.incrementAndGet()
                filterResultsJob?.cancel()
                filterResultsJob = null
                browseZanocujIndex = emptyList()
                lastBrowseViewportKey = null
                browseViewportJob?.cancel()
                _state.update { current ->
                    current.copy(
                        exploreMode = AppExploreMode.SEARCH,
                        mapBrowseRevision = 0L,
                        isMapBrowseLoading = false,
                        allSites = emptyList(),
                        browseViewportSites = emptyList(),
                        browseMapClusters = emptyList(),
                        results = emptyList(),
                        browseFilterMatchingIds = null,
                        isFilteringPlaces = false,
                        isSearching = false,
                        isAnalyzingRoads = false,
                        roadAnalysisCompleted = 0,
                        roadAnalysisTotal = 0,
                        zanocujPolygons = emptyList(),
                        selectedSiteIds = emptyList(),
                        message = AppMessage.Info("Tryb wyszukiwania — ustaw źródło i naciśnij Znajdź."),
                        currentPage = exploreModeTargetPage(
                            mode,
                            stayOnPage,
                            current.currentPage,
                        ),
                    )
                }
            }
            AppExploreMode.MAP_BROWSE -> {
                searchGeneration.incrementAndGet()
                filterGeneration.incrementAndGet()
                filterResultsJob?.cancel()
                filterResultsJob = null
                _state.update {
                    it.copy(
                        exploreMode = AppExploreMode.MAP_BROWSE,
                        allSites = emptyList(),
                        browseViewportSites = emptyList(),
                        browseMapClusters = emptyList(),
                        results = emptyList(),
                        selectedSiteIds = emptyList(),
                        browseFilterMatchingIds = null,
                        isFilteringPlaces = false,
                        isSearching = false,
                        isAnalyzingRoads = false,
                        roadAnalysisCompleted = 0,
                        roadAnalysisTotal = 0,
                        message = null,
                        currentPage = exploreModeTargetPage(
                            mode,
                            stayOnPage,
                            it.currentPage,
                        ),
                    )
                }
                loadMapBrowseLayer(force = true)
            }
        }
    }

    fun loadMapBrowseLayer(force: Boolean = false) {
        if (!_state.value.isMapBrowse()) return
        if (_state.value.isMapBrowseLoading) return
        if (!force && _state.value.mapBrowseRevision > 0 && _state.value.allSites.isNotEmpty()) return
        val generation = mapBrowseGeneration.incrementAndGet()
        mapBrowseLoadJob?.cancel()
        mapBrowseLoadJob = viewModelScope.launch {
            if (!offlineStore.isReady()) {
                _state.update {
                    if (!it.isMapBrowse() || mapBrowseGeneration.get() != generation) {
                        return@update it
                    }
                    it.copy(
                        message = AppMessage.Info(
                            getApplication<Application>().getString(R.string.map_browse_need_offline),
                        ),
                        currentPage = AppPages.SEARCH,
                    )
                }
                return@launch
            }
            _state.update {
                if (!it.isMapBrowse() || mapBrowseGeneration.get() != generation) {
                    return@update it
                }
                it.copy(
                    isMapBrowseLoading = true,
                    message = null,
                )
            }
            try {
                val bundle = OfflineMapBrowseLoader(offlineStore).loadAll()
                val fullAvailable =
                    _state.value.offlineBdl.storedConfig?.scope == BdlDataScope.FULL_BDL
                val overlay = withContext(Dispatchers.IO) {
                    BdlOverlayLoader.loadAll(offlineStore, fullAvailable)
                }
                if (!isActiveMapBrowseLoad(generation)) return@launch
                val initialUser = _state.value.userPosition
                val initialBounds = if (initialUser != null) {
                    GeoUtils.envelopeAround(
                        initialUser.latitude,
                        initialUser.longitude,
                        BROWSE_INITIAL_VIEWPORT_RADIUS_KM,
                    )
                } else {
                    POLAND_BROWSE_ENVELOPE
                }
                val initialCenterLat = initialUser?.latitude ?: 52.1
                val initialCenterLon = initialUser?.longitude ?: 19.4
                val initialZoom = if (initialUser != null) {
                    BROWSE_INITIAL_USER_ZOOM
                } else {
                    5.5
                }
                val initialContent = withContext(Dispatchers.Default) {
                    selectBrowseContent(
                        bundle.sites,
                        initialBounds,
                        initialCenterLat,
                        initialCenterLon,
                        initialZoom,
                        matchingIds = null,
                    )
                }
                browseZanocujIndex = bundle.zanocujIndex
                browseOverlayIndex = overlay
                browseOverlayById = overlay.associateBy { it.id }
                lastBrowseViewportKey = null
                // Nationwide MapLibre overlays OOMs — keep index private; draw viewport subset only.
                _state.update { current ->
                    if (!current.isMapBrowse() || mapBrowseGeneration.get() != generation) {
                        return@update current
                    }
                    current.copy(
                        allSites = bundle.sites,
                        browseViewportSites = initialContent.first,
                        browseMapClusters = initialContent.second,
                        zanocujPolygons = emptyList(),
                        bdlOverlayViewport = emptyList(),
                        bdlOverlayFullAvailable = fullAvailable,
                        roadBySiteId = emptyMap(),
                        results = emptyList(),
                        selectedSiteIds = emptyList(),
                        isMapBrowseLoading = false,
                        mapBrowseRevision = current.mapBrowseRevision + 1,
                        mapCameraRequest = MapCameraRequest.CenterOn(
                            latitude = current.userPosition?.latitude ?: 52.1,
                            longitude = current.userPosition?.longitude ?: 19.4,
                            zoom = if (current.userPosition != null) BROWSE_INITIAL_USER_ZOOM else 5.5,
                            token = cameraToken.getAndIncrement(),
                        ),
                        message = AppMessage.Info(
                            if (bundle.skippedInvalidGeometry > 0) {
                                getApplication<Application>().getString(
                                    R.string.map_browse_loaded_skipped,
                                    bundle.sites.size,
                                    bundle.skippedInvalidGeometry,
                                ) + " · ${bundle.loadMs} ms"
                            } else {
                                getApplication<Application>().getString(
                                    R.string.map_browse_loaded,
                                    bundle.sites.size,
                                ) + " · ${bundle.loadMs} ms"
                            },
                        ),
                    )
                }
                if (isActiveMapBrowseLoad(generation)) {
                    scheduleBrowseViewportRefresh(
                        initialBounds,
                        initialCenterLat,
                        initialCenterLon,
                        initialZoom,
                    )
                }
                if (_state.value.browseCarFilter.isActive && isActiveMapBrowseLoad(generation)) {
                    setBrowseCarFilter(_state.value.browseCarFilter)
                }
            } catch (e: Exception) {
                if (!isActiveMapBrowseLoad(generation)) return@launch
                browseZanocujIndex = emptyList()
                browseOverlayIndex = emptyList()
                browseOverlayById = emptyMap()
                _state.update {
                    if (!it.isMapBrowse() || mapBrowseGeneration.get() != generation) {
                        return@update it
                    }
                    it.copy(
                        isMapBrowseLoading = false,
                        message = AppMessage.Error(
                            "Przeglądanie mapy: ${e.message ?: "błąd ładowania"}",
                        ),
                    )
                }
            } finally {
                if (mapBrowseGeneration.get() == generation) {
                    mapBrowseLoadJob = null
                }
            }
        }
    }

    private fun isActiveMapBrowseLoad(generation: Long): Boolean =
        _state.value.isMapBrowse() && mapBrowseGeneration.get() == generation

    /**
     * Browse: draw Zanocuj fills only for the visible map bbox (capped).
     * Called on camera idle — keeps MapLibre native heap bounded.
     */
    fun onBrowseMapViewport(
        west: Double,
        south: Double,
        east: Double,
        north: Double,
        zoom: Double,
    ) {
        if (!isUsableBrowseViewport(west, south, east, north)) return
        val padLon = (east - west).coerceAtLeast(0.01) * 0.15
        val padLat = (north - south).coerceAtLeast(0.01) * 0.15
        val envelope = GeoUtils.Envelope(
            xmin = west - padLon,
            ymin = south - padLat,
            xmax = east + padLon,
            ymax = north + padLat,
        )
        val centerLat = (south + north) / 2.0
        val centerLon = (west + east) / 2.0
        lastBrowseBounds = envelope
        lastBrowseZoom = zoom
        lastBrowseCenterLat = centerLat
        lastBrowseCenterLon = centerLon
        val key = listOf(
            (west * 100).toInt(),
            (south * 100).toInt(),
            (east * 100).toInt(),
            (north * 100).toInt(),
            zoom.toInt(),
            _state.value.bdlOverlayFilter.hashCode(),
        ).joinToString(",")
        if (key == lastBrowseViewportKey) return
        lastBrowseViewportKey = key
        scheduleBrowseViewportRefresh(envelope, centerLat, centerLon, zoom)
    }

    fun setBdlOverlayFilter(filter: BdlOverlayFilter) {
        lastBrowseViewportKey = null
        _state.update { it.copy(bdlOverlayFilter = filter) }
        if (filter.isActive) {
            ensureOverlayIndexLoaded()
        }
        val bounds = lastBrowseBounds
        if (bounds != null) {
            scheduleBrowseViewportRefresh(
                bounds,
                lastBrowseCenterLat,
                lastBrowseCenterLon,
                lastBrowseZoom,
            )
        } else if (!filter.isActive) {
            _state.update { it.copy(bdlOverlayViewport = emptyList()) }
        }
    }

    private fun scheduleBrowseViewportRefresh(
        envelope: GeoUtils.Envelope,
        centerLat: Double,
        centerLon: Double,
        zoom: Double,
    ) {
        browseViewportJob?.cancel()
        browseViewportJob = viewModelScope.launch {
            val allSites = _state.value.allSites
            val matchingIds = _state.value.browseFilterMatchingIds
            val drawZanocuj = zoom >= BROWSE_ZANOCUJ_MIN_ZOOM && browseZanocujIndex.isNotEmpty()
            val overlayGroups = _state.value.bdlOverlayFilter.effectiveGroups(
                _state.value.bdlOverlayFullAvailable,
            )
            val drawOverlay = overlayGroups.isNotEmpty() &&
                zoom >= BROWSE_OVERLAY_MIN_ZOOM &&
                browseOverlayIndex.isNotEmpty()
            val zanocujSubset = if (drawZanocuj) {
                withContext(Dispatchers.Default) {
                    browseZanocujIndex
                        .asSequence()
                        .filter { it.intersects(envelope) }
                        .sortedBy {
                            val dLat = it.centerLat() - centerLat
                            val dLon = it.centerLon() - centerLon
                            dLat * dLat + dLon * dLon
                        }
                        .take(BROWSE_ZANOCUJ_MAX_POLYGONS)
                        .map { it.polygon }
                        .toList()
                }
            } else {
                emptyList()
            }
            val overlaySubset = if (drawOverlay) {
                withContext(Dispatchers.Default) {
                    BdlOverlayLoader.inEnvelope(
                        points = browseOverlayIndex,
                        envelope = envelope,
                        groups = overlayGroups,
                        centerLat = centerLat,
                        centerLon = centerLon,
                        limit = BROWSE_OVERLAY_MAX_POINTS,
                    )
                }
            } else {
                emptyList()
            }
            val (banSubset, banStatus) = loadEntryBansForViewport(
                envelope,
                centerLat,
                centerLon,
                zoom,
            )
            val browseContent = withContext(Dispatchers.Default) {
                selectBrowseContent(
                    allSites,
                    envelope,
                    centerLat,
                    centerLon,
                    zoom,
                    matchingIds,
                )
            }
            if (!_state.value.isMapBrowse()) {
                _state.update {
                    it.copy(
                        bdlOverlayViewport = overlaySubset,
                        entryBanViewport = banSubset,
                        entryBanStatus = banStatus,
                    )
                }
                return@launch
            }
            _state.update {
                val nextSites = if (
                    browseContent.first.isEmpty() &&
                    browseContent.second.isEmpty() &&
                    (it.browseViewportSites.isNotEmpty() || it.browseMapClusters.isNotEmpty())
                ) {
                    it.browseViewportSites
                } else {
                    browseContent.first
                }
                val nextClusters = if (
                    browseContent.first.isEmpty() &&
                    browseContent.second.isEmpty() &&
                    (it.browseViewportSites.isNotEmpty() || it.browseMapClusters.isNotEmpty())
                ) {
                    it.browseMapClusters
                } else {
                    browseContent.second
                }
                val viewportChanged = it.browseViewportSites.map(RestSite::id) !=
                    nextSites.map(RestSite::id) || it.browseMapClusters != nextClusters
                it.copy(
                    zanocujPolygons = zanocujSubset,
                    bdlOverlayViewport = overlaySubset,
                    entryBanViewport = banSubset,
                    entryBanStatus = banStatus,
                    browseViewportSites = nextSites,
                    browseMapClusters = nextClusters,
                    mapBrowseRevision = if (viewportChanged) {
                        it.mapBrowseRevision + 1
                    } else {
                        it.mapBrowseRevision
                    },
                )
            }
        }
    }

    private suspend fun loadEntryBansForViewport(
        envelope: GeoUtils.Envelope,
        centerLat: Double,
        centerLon: Double,
        zoom: Double,
    ): Pair<List<ForestEntryBan>, String?> {
        val app = getApplication<Application>()
        if (zoom < ENTRY_BAN_MIN_ZOOM) {
            return emptyList<ForestEntryBan>() to app.getString(R.string.entry_ban_zoom_hint)
        }
        val packIndex = entryBanIndex
        if (_state.value.entryBanPackDownloadedAt != null) {
            val bans = withContext(Dispatchers.Default) {
                ForestEntryBanClassifier.inEnvelope(
                    index = packIndex,
                    envelope = envelope,
                    centerLat = centerLat,
                    centerLon = centerLon,
                    limit = ENTRY_BAN_MAX_POLYGONS,
                )
            }
            val downloadedAt = _state.value.entryBanPackDownloadedAt
            val dateLabel = downloadedAt?.let { formatEntryBanPackDate(it) }
            val stale = downloadedAt != null &&
                System.currentTimeMillis() - downloadedAt >= ENTRY_BAN_REFRESH_STALE_MS
            val status = when {
                bans.isEmpty() && stale ->
                    app.getString(R.string.entry_ban_empty_pack_stale, dateLabel.orEmpty())
                bans.isEmpty() ->
                    app.getString(R.string.entry_ban_empty_pack, dateLabel.orEmpty())
                stale ->
                    app.getString(R.string.entry_ban_count_pack_stale, bans.size, dateLabel.orEmpty())
                else ->
                    app.getString(R.string.entry_ban_count_pack, bans.size, dateLabel.orEmpty())
            }
            return bans to status
        }
        return try {
            val bans = withContext(Dispatchers.IO) {
                forestEntryBanLoader.queryViewport(
                    envelope = envelope,
                    centerLat = centerLat,
                    centerLon = centerLon,
                    limit = ENTRY_BAN_MAX_POLYGONS,
                )
            }
            val status = if (bans.isEmpty()) {
                app.getString(R.string.entry_ban_empty)
            } else {
                app.getString(R.string.entry_ban_count, bans.size)
            }
            bans to status
        } catch (_: Exception) {
            emptyList<ForestEntryBan>() to app.getString(R.string.entry_ban_offline)
        }
    }

    private fun formatEntryBanPackDate(epochMs: Long): String {
        val formatter = java.text.DateFormat.getDateInstance(
            java.text.DateFormat.MEDIUM,
            java.util.Locale.forLanguageTag("pl-PL"),
        )
        return formatter.format(java.util.Date(epochMs))
    }

    private fun browseListOrigin(state: UiState): UserPosition? =
        state.userPosition ?: state.searchOrigin()

    /** Browse keeps all points on the map; list/card hold the current multi-selection. */
    private fun browseSelectionResults(state: UiState, siteIds: List<String>): List<RestSiteResult> {
        if (siteIds.isEmpty()) return emptyList()
        val sites = siteIds.mapNotNull { siteId ->
            state.allSites.firstOrNull { it.id == siteId }
                ?: browseOverlayById[siteId]?.toRestSite()
                ?: state.results.firstOrNull { it.site.id == siteId }?.site
        }
        if (sites.isEmpty()) return emptyList()
        return buildResults(
            sites,
            browseListOrigin(state),
            state.profile,
            BrowseCarFilter(),
            state.roadBySiteId,
            corridorSites = state.allSites,
        )
    }

    private fun resolveSelectedSite(state: UiState, siteId: String): RestSite? =
        state.allSites.firstOrNull { it.id == siteId }
            ?: browseOverlayById[siteId]?.toRestSite()
            ?: state.results.firstOrNull { it.site.id == siteId }?.site

    private fun resultForSelectedSite(state: UiState, site: RestSite): RestSiteResult {
        val origin = state.searchOrigin() ?: state.userPosition
        if (origin != null) {
            return buildResults(
                listOf(site),
                origin,
                state.profile,
                BrowseCarFilter(),
                state.roadBySiteId,
                corridorSites = state.allSites,
            ).first()
        }
        val assessment = if (state.profile == TravelProfile.MOTORCYCLE) {
            state.roadBySiteId[site.id]
        } else {
            null
        }
        val (target, kind) = when (state.profile) {
            TravelProfile.CAR -> NavigationTargets.forCar(site)
            TravelProfile.MOTORCYCLE -> {
                NavigationTargets.forMotorcycle(assessment)
                    ?: (pl.navilas.finder.domain.LatLon(site.latitude, site.longitude) to
                        NavigationTargetKind.REST_SITE)
            }
        }
        return markOfficialApproach(
            listOf(
                RestSiteResult(
                    site = site,
                    distanceKm = 0.0,
                    roadAssessment = assessment,
                    navigationTarget = target,
                    navigationTargetKind = kind,
                ),
            ),
            state.roadBySiteId,
            state.allSites,
            state.results.map { it.site },
            state.savedPoints.values.map { it.site },
        ).first()
    }

    fun displayedListResults(state: UiState): List<RestSiteResult> {
        if (state.listViewMode == ListViewMode.SAVED) return state.savedListResults
        if (state.isMapBrowse()) return state.results
        if (state.selectedSiteIds.isEmpty()) return state.results
        val inResults = state.results.associateBy { it.site.id }
        val extras = state.selectedSiteIds.mapNotNull { id ->
            if (id in inResults) return@mapNotNull null
            val site = resolveSelectedSite(state, id) ?: return@mapNotNull null
            resultForSelectedSite(state, site)
        }
        val pinned = state.selectedSiteIds.mapNotNull { inResults[it] }
        val rest = state.results.filter { it.site.id !in state.selectedSiteIds.toSet() }
        return extras + pinned + rest
    }

    private fun ensureOverlayIndexLoaded() {
        if (browseOverlayIndex.isNotEmpty()) return
        if (overlayLoadJob?.isActive == true) return
        overlayLoadJob = viewModelScope.launch {
            overlayIndexForDetails()
            lastBrowseViewportKey = null
            val bounds = lastBrowseBounds
            if (bounds != null && _state.value.bdlOverlayFilter.isActive) {
                scheduleBrowseViewportRefresh(
                    bounds,
                    lastBrowseCenterLat,
                    lastBrowseCenterLon,
                    lastBrowseZoom,
                )
            }
        }
    }

    /**
     * Offline overlay points within 200 m of [site] for the details dialog.
     * Does not draw on the map.
     */
    suspend fun nearbyOverlayGroups(site: RestSite): List<NearbyOverlayObjects.Group> {
        val index = overlayIndexForDetails()
        return NearbyOverlayObjects.groupedWithin(
            points = index,
            latitude = site.latitude,
            longitude = site.longitude,
            excludeId = site.id,
            radiusMeters = NearbyOverlayObjects.DETAILS_RADIUS_METERS,
        )
    }

    private suspend fun overlayIndexForDetails(): List<BdlOverlayPoint> {
        if (browseOverlayIndex.isNotEmpty()) return browseOverlayIndex
        if (!offlineStore.isReady()) return emptyList()
        return overlayIndexMutex.withLock {
            if (browseOverlayIndex.isNotEmpty()) return@withLock browseOverlayIndex
            val fullAvailable = _state.value.offlineBdl.storedConfig?.scope == BdlDataScope.FULL_BDL
            val overlay = withContext(Dispatchers.IO) {
                BdlOverlayLoader.loadAll(offlineStore, fullAvailable)
            }
            browseOverlayIndex = overlay
            browseOverlayById = overlay.associateBy { it.id }
            _state.update { it.copy(bdlOverlayFullAvailable = fullAvailable) }
            overlay
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

    fun savedPointsCount(): Int = savedPointsStore.allPoints().size

    fun buildSavedPointsExportJson(): String =
        SavedPointsBackupCodec.encodeExport(
            store = savedPointsStore,
            appVersion = BuildConfig.VERSION_NAME,
            exportedAtMs = System.currentTimeMillis(),
        )

    fun parseSavedPointsImport(text: String): SavedPointsBackupParseResult =
        SavedPointsBackupCodec.parseImport(text) { root ->
            savedPointsStore.parseSnapshot(root)
        }

    fun importSavedPoints(
        snapshot: SavedPointsBackupSnapshot,
        mode: SavedPointsImportMode,
    ): SavedPointsImportResult {
        val result = savedPointsStore.importSnapshot(snapshot, mode)
        refreshSavedPointsState()
        return result
    }

    private fun refreshSavedPointsState() {
        _state.update { current ->
            val saved = savedPointsStore.allPoints().associateBy { it.site.id }
            val next = current.copy(
                savedPoints = saved,
                savedCategories = savedPointsStore.allCategories(),
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
            .let { built ->
                markOfficialApproach(
                    built,
                    state.roadBySiteId,
                    state.allSites,
                    state.savedPoints.values.map { it.site },
                )
            }
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

    /** Empty-map tap: set search centre, or append corridor vertex in LINE mode. */
    fun setMapSearchPin(latitude: Double, longitude: Double) {
        if (_state.value.isMapBrowse()) return
        if (_state.value.searchOriginMode == SearchOriginMode.LINE) {
            appendCorridorPoint(latitude, longitude)
            return
        }
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
                    current.browseCarFilter,
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
                    current.browseCarFilter,
                    current.roadBySiteId,
                ),
                message = AppMessage.Info("Punkt z mapy wyczyszczony — wyszukiwanie od GPS."),
            )
        }
    }

    fun closeSelectedSite() {
        applySelection(SiteSelection.removeLast(_state.value.selectedSiteIds))
    }

    fun selectSite(siteId: String?) {
        applySelection(if (siteId == null) emptyList() else listOf(siteId))
    }

    /** Empty-map tap: clear browse selection, or drop a search pin. Ignores ghost taps after a marker. */
    fun onEmptyMapClicked(latitude: Double, longitude: Double) {
        if (System.currentTimeMillis() < ignoreEmptyMapClickUntilElapsedMs) return
        if (_state.value.isMapBrowse()) {
            selectSite(null)
        } else {
            setMapSearchPin(latitude, longitude)
        }
    }

    private fun applySelection(
        nextIds: List<String>,
        cameraSiteId: String? = null,
        goToMap: Boolean = false,
        cameraTokenValue: Long? = null,
    ) {
        _state.update { current ->
            current.copy(
                selectedSiteIds = nextIds,
                results = if (current.isMapBrowse()) {
                    browseSelectionResults(current, nextIds)
                } else {
                    current.results
                },
                currentPage = if (goToMap) AppPages.MAP else current.currentPage,
                mapCameraRequest = if (cameraSiteId != null && cameraTokenValue != null) {
                    if (goToMap) {
                        PagerNavigation.cameraForListSelect(cameraSiteId, cameraTokenValue)
                    } else {
                        PagerNavigation.cameraForMarkerClick(cameraSiteId, cameraTokenValue)
                    }
                } else {
                    current.mapCameraRequest
                },
            )
        }
        analyzeSelectedRoadsIfNeeded()
    }

    /** Marker tap: always open/focus the card. Browse does not move the camera. */
    fun onMarkerSelected(siteId: String) {
        pauseMapTrackingForPoiInteraction()
        ignoreEmptyMapClickUntilElapsedMs =
            System.currentTimeMillis() + EMPTY_MAP_CLICK_DEBOUNCE_MS
        val nextIds = SiteSelection.add(_state.value.selectedSiteIds, siteId)
        val moveCamera = !_state.value.isMapBrowse()
        val token = if (moveCamera) cameraToken.getAndIncrement() else null
        applySelection(
            nextIds,
            cameraSiteId = if (moveCamera) siteId else null,
            cameraTokenValue = token,
        )
    }

    /** List row tap: focus site + go to map + show POI camera (no fitBounds). */
    fun onListItemSelected(siteId: String) {
        pauseMapTrackingForPoiInteraction()
        val token = cameraToken.getAndIncrement()
        val nextIds = SiteSelection.add(_state.value.selectedSiteIds, siteId)
        applySelection(nextIds, cameraSiteId = siteId, goToMap = true, cameraTokenValue = token)
    }

    private fun cancelSelectedRoadsJob() {
        selectedRoadsGeneration.incrementAndGet()
        selectedRoadsJob?.cancel()
        selectedRoadsJob = null
        _state.update { current ->
            if (current.isAnalyzingRoads) current.copy(isAnalyzingRoads = false) else current
        }
    }

    private fun analyzeSelectedRoadsIfNeeded() {
        val snapshot = _state.value
        if (snapshot.profile != TravelProfile.MOTORCYCLE) {
            cancelSelectedRoadsJob()
            return
        }
        val missing = snapshot.selectedSiteIds.filter { it !in snapshot.roadBySiteId }
        if (missing.isEmpty()) {
            if (selectedRoadsJob?.isActive == true) cancelSelectedRoadsJob()
            return
        }
        val sites = missing.mapNotNull { resolveSelectedSite(snapshot, it) }
        if (sites.isEmpty()) {
            cancelSelectedRoadsJob()
            return
        }
        selectedRoadsJob?.cancel()
        val generation = selectedRoadsGeneration.incrementAndGet()
        selectedRoadsJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    isAnalyzingRoads = true,
                    roadAnalysisCompleted = 0,
                    roadAnalysisTotal = sites.size,
                )
            }
            try {
                val assessed = withContext(Dispatchers.IO) {
                    roadAnalyzer.assessAll(sites.map { it.toPointPoi() }) { completed, total ->
                        if (selectedRoadsGeneration.get() == generation) {
                            _state.update { current ->
                                current.copy(
                                    roadAnalysisCompleted = completed,
                                    roadAnalysisTotal = total,
                                )
                            }
                        }
                    }
                }
                if (selectedRoadsGeneration.get() != generation) return@launch
                _state.update { current ->
                    if (selectedRoadsGeneration.get() != generation) return@update current
                    val merged = current.roadBySiteId + assessed
                    val next = current.copy(
                        isAnalyzingRoads = false,
                        roadBySiteId = merged,
                        roadAnalysisCompleted = sites.size,
                        roadAnalysisTotal = sites.size,
                        results = if (current.isMapBrowse()) {
                            browseSelectionResults(
                                current.copy(roadBySiteId = merged),
                                current.selectedSiteIds,
                            )
                        } else {
                            markOfficialApproach(
                                current.results.map { item ->
                                    val assessment = merged[item.site.id]
                                    if (assessment == null) {
                                        item
                                    } else {
                                        val (target, kind) = NavigationTargets.forMotorcycle(assessment)
                                            ?: (item.navigationTarget to item.navigationTargetKind)
                                        item.copy(
                                            roadAssessment = assessment,
                                            navigationTarget = target,
                                            navigationTargetKind = kind,
                                        )
                                    }
                                },
                                merged,
                                current.allSites,
                            )
                        },
                    )
                    next.copy(savedListResults = buildSavedListResults(next))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (selectedRoadsGeneration.get() != generation) return@launch
                _state.update {
                    it.copy(
                        isAnalyzingRoads = false,
                        message = AppMessage.Error(
                            "Analiza dróg OSM: ${e.message ?: "błąd"}",
                        ),
                    )
                }
            }
        }
    }

    fun consumeMapCameraRequest() {
        _state.update { it.copy(mapCameraRequest = null) }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    fun checkForAppUpdate(force: Boolean) {
        if (!BuildConfig.APP_UPDATE_ENABLED) return
        viewModelScope.launch {
            appUpdatePrefs.lastCheckAtMs = System.currentTimeMillis()
            try {
                val manifests = withContext(Dispatchers.IO) {
                    appUpdateChecker.fetchEligibleManifests(uiPreferences.updateChannel)
                }
                val offer = AppUpdateLogic.evaluateBestOffer(
                    manifests = manifests,
                    currentVersionCode = BuildConfig.VERSION_CODE,
                    dismissedVersionCode = if (force) null else appUpdatePrefs.dismissedVersionCode,
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

    private fun maybeOfferBdlRefresh() {
        if (bdlRefreshCheckedThisSession) return
        bdlRefreshCheckedThisSession = true
        val offline = _state.value.offlineBdl
        val stored = offline.storedConfig ?: return
        val downloadedAt = offline.downloadedAt ?: return
        if (!shouldOfferBdlRefresh(
                isReady = offline.isReady,
                downloadedAt = downloadedAt,
                nowMs = System.currentTimeMillis(),
                snoozeUntilMs = uiPreferences.bdlRefreshSnoozeUntilMs,
            )
        ) {
            return
        }
        _state.update {
            it.copy(bdlRefreshOffer = BdlRefreshOffer(downloadedAt = downloadedAt, config = stored))
        }
    }

    fun acceptBdlRefresh() {
        val offer = _state.value.bdlRefreshOffer ?: return
        _state.update {
            it.copy(
                bdlRefreshOffer = null,
                offlineBdl = it.offlineBdl.copy(pendingConfig = offer.config),
            )
        }
        downloadOfflineData()
        maybeOfferEntryBanRefresh()
    }

    fun snoozeBdlRefresh() {
        uiPreferences.bdlRefreshSnoozeUntilMs = System.currentTimeMillis() + BDL_REFRESH_SNOOZE_MS
        _state.update { it.copy(bdlRefreshOffer = null) }
        maybeOfferEntryBanRefresh()
    }

    private fun loadEntryBanIndexFromDisk() {
        if (!forestEntryBanStore.isReady()) {
            entryBanIndex = emptyList()
            return
        }
        entryBanIndex = forestEntryBanStore.loadAll().mapNotNull { ForestEntryBanBounds.from(it) }
    }

    fun downloadEntryBans() {
        if (entryBanDownloadJob?.isActive == true) return
        entryBanDownloadJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    entryBanDownloading = true,
                    entryBanRefreshOffer = null,
                    message = AppMessage.Info(
                        getApplication<Application>().getString(R.string.entry_ban_downloading),
                    ),
                )
            }
            try {
                val bans = withContext(Dispatchers.IO) {
                    val downloaded = forestEntryBanLoader.downloadAll()
                    forestEntryBanStore.saveAll(downloaded, System.currentTimeMillis())
                    downloaded
                }
                entryBanIndex = bans.mapNotNull { ForestEntryBanBounds.from(it) }
                uiPreferences.entryBanRefreshSnoozeUntilMs = 0L
                val downloadedAt = forestEntryBanStore.downloadedAt()
                _state.update {
                    it.copy(
                        entryBanDownloading = false,
                        entryBanPackDownloadedAt = downloadedAt,
                        entryBanPackCount = bans.size,
                        message = AppMessage.Info(
                            getApplication<Application>().getString(
                                R.string.entry_ban_download_ok,
                                bans.size,
                            ),
                        ),
                    )
                }
                lastBrowseViewportKey = null
                refreshEntryBanDependentUi()
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        entryBanDownloading = false,
                        message = AppMessage.Error(
                            getApplication<Application>().getString(
                                R.string.entry_ban_download_error,
                                e.message ?: "błąd",
                            ),
                        ),
                    )
                }
            } finally {
                entryBanDownloadJob = null
            }
        }
    }

    fun acceptEntryBanRefresh() {
        _state.update { it.copy(entryBanRefreshOffer = null) }
        downloadEntryBans()
    }

    fun snoozeEntryBanRefresh() {
        uiPreferences.entryBanRefreshSnoozeUntilMs =
            System.currentTimeMillis() + ENTRY_BAN_REFRESH_SNOOZE_MS
        _state.update { it.copy(entryBanRefreshOffer = null) }
    }

    fun deleteEntryBans() {
        if (entryBanDownloadJob?.isActive == true) return
        forestEntryBanStore.deleteAll()
        entryBanIndex = emptyList()
        uiPreferences.entryBanRefreshSnoozeUntilMs = 0L
        _state.update {
            it.copy(
                entryBanPackDownloadedAt = null,
                entryBanPackCount = 0,
                entryBanRefreshOffer = null,
                message = AppMessage.Info(
                    getApplication<Application>().getString(R.string.entry_ban_deleted),
                ),
            )
        }
        refreshEntryBanDependentUi()
    }

    private fun refreshEntryBanDependentUi() {
        lastBrowseViewportKey = null
        val bounds = lastBrowseBounds
        if (bounds != null) {
            scheduleBrowseViewportRefresh(
                bounds,
                lastBrowseCenterLat,
                lastBrowseCenterLon,
                lastBrowseZoom,
            )
        }
        if (_state.value.browseCarFilter.excludeSitesInEntryBan) {
            setBrowseCarFilter(_state.value.browseCarFilter)
        }
    }

    private fun matchingSiteIds(sites: List<RestSite>, filter: BrowseCarFilter): Set<String>? {
        val banned = if (filter.excludeSitesInEntryBan) {
            siteIdsInsideEntryBans(sites)
        } else {
            emptySet()
        }
        return BrowseCarFilterMatcher.matchingIds(sites, filter, banned)
    }

    private fun siteIdsInsideEntryBans(sites: List<RestSite>): Set<String> {
        val index = entryBanIndex
        if (index.isNotEmpty()) {
            return sites.asSequence()
                .filter {
                    ForestEntryBanClassifier.containingInIndex(it.latitude, it.longitude, index) != null
                }
                .map { it.id }
                .toHashSet()
        }
        val viewport = _state.value.entryBanViewport
        if (viewport.isEmpty()) return emptySet()
        return sites.asSequence()
            .filter {
                ForestEntryBanClassifier.containing(it.latitude, it.longitude, viewport) != null
            }
            .map { it.id }
            .toHashSet()
    }

    private fun maybeOfferEntryBanRefresh() {
        if (_state.value.entryBanRefreshOffer != null) return
        if (_state.value.bdlRefreshOffer != null) return
        if (_state.value.appUpdateOffer != null) return
        if (_state.value.entryBanDownloading) return
        val downloadedAt = forestEntryBanStore.downloadedAt() ?: return
        if (!shouldOfferBdlRefresh(
                isReady = forestEntryBanStore.isReady(),
                downloadedAt = downloadedAt,
                nowMs = System.currentTimeMillis(),
                snoozeUntilMs = uiPreferences.entryBanRefreshSnoozeUntilMs,
                staleAfterMs = ENTRY_BAN_REFRESH_STALE_MS,
            )
        ) {
            return
        }
        _state.update {
            it.copy(
                entryBanRefreshOffer = EntryBanRefreshOffer(
                    downloadedAt = downloadedAt,
                    count = forestEntryBanStore.count(),
                ),
            )
        }
    }

    fun dismissAppUpdate() {
        val offer = _state.value.appUpdateOffer ?: return
        if (offer.mandatory) return
        appUpdatePrefs.dismissedVersionCode = offer.versionCode
        _state.update { it.copy(appUpdateOffer = null) }
        maybeOfferEntryBanRefresh()
    }

    fun startAppUpdateDownload() {
        val offer = _state.value.appUpdateOffer ?: return
        viewModelScope.launch {
            _state.update {
                it.copy(
                    appUpdateOffer = null,
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

    fun refreshLocation(showApproximateHint: Boolean = true, forceCenter: Boolean = false) {
        viewModelScope.launch {
            val browse = _state.value.isMapBrowse()
            _state.update {
                it.copy(
                    isLocating = true,
                    message = null,
                    searchOriginMode = if (browse) it.searchOriginMode else SearchOriginMode.GPS,
                    mapSearchPin = if (browse) it.mapSearchPin else null,
                    localityDisplayName = if (browse) it.localityDisplayName else null,
                )
            }
            when (val outcome = locationProvider.currentLocation()) {
                is LocationOutcome.Exact -> applyLocation(
                    UserPosition(outcome.location.latitude, outcome.location.longitude, false),
                    AppMessage.Info(
                        getApplication<Application>().getString(
                            if (browse) R.string.location_updated else R.string.location_updated_search,
                        ),
                    ),
                    forceCenter = forceCenter,
                )
                is LocationOutcome.Approximate -> applyLocation(
                    UserPosition(outcome.location.latitude, outcome.location.longitude, true),
                    if (showApproximateHint) {
                        AppMessage.Info(
                            getApplication<Application>().getString(
                                if (browse) R.string.location_approx else R.string.location_approx_search,
                            ),
                        )
                    } else {
                        null
                    },
                    forceCenter = forceCenter,
                )
                is LocationOutcome.Failure -> {
                    _state.update {
                        it.copy(isLocating = false, message = AppMessage.Error(outcome.reason))
                    }
                }
            }
        }
    }

    /**
     * Track FAB: TRACKING → PAUSED; PAUSED → TRACKING with the stored frame;
     * OFF → TRACKING with the supplied camera frame.
     */
    fun toggleMapTracking(
        focalScreenX: Float,
        focalScreenY: Float,
        bearing: Double,
        zoom: Double,
    ) {
        when (_state.value.mapTrackingMode) {
            MapTrackingMode.TRACKING -> pauseMapTracking()
            MapTrackingMode.PAUSED -> resumeMapTracking()
            MapTrackingMode.OFF -> startOrResumeMapTracking(
                focalScreenX = focalScreenX,
                focalScreenY = focalScreenY,
                bearing = bearing,
                zoom = zoom,
            )
        }
    }

    /**
     * User gesture on the map while tracking → suspend follow until [onMapGestureEnded].
     * Manual [MapTrackingMode.PAUSED] (POI / FAB) is unchanged.
     */
    fun onMapTrackingGestureStarted() {
        if (_state.value.mapTrackingMode != MapTrackingMode.TRACKING) return
        mapTrackingGestureHold = true
    }

    /** After user finishes a map gesture — adopt bearing/zoom; keep map frame (no recenter). */
    fun onMapGestureEnded(
        bearing: Double,
        zoom: Double,
        focalScreenX: Float?,
        focalScreenY: Float?,
    ) {
        if (_state.value.mapTrackingMode != MapTrackingMode.TRACKING || !mapTrackingGestureHold) return
        mapTrackingGestureHold = false
        followBearing = bearing
        followZoom = zoom.coerceIn(MIN_FOLLOW_ZOOM, MAX_FOLLOW_ZOOM)
        if (focalScreenX != null && focalScreenY != null &&
            focalScreenX.isFinite() && focalScreenY.isFinite()
        ) {
            followFocalScreenX = focalScreenX
            followFocalScreenY = focalScreenY
        }
        // Do not forceFollow here — that would snap GPS back to the old focal and undo the pan.
    }

    /** POI / fit-bounds camera while tracking → manual pause until FAB resume. */
    fun pauseMapTrackingForPoiInteraction() {
        if (_state.value.mapTrackingMode != MapTrackingMode.TRACKING) return
        mapTrackingGestureHold = false
        pauseMapTracking()
    }

    private fun pauseMapTracking() {
        mapTrackingGestureHold = false
        _state.update {
            it.copy(
                mapTrackingMode = MapTrackingMode.PAUSED,
                message = AppMessage.Info(
                    getApplication<Application>().getString(R.string.map_tracking_paused),
                ),
            )
        }
    }

    private fun resumeMapTracking() {
        mapTrackingGestureHold = false
        _state.update {
            it.copy(
                mapTrackingMode = MapTrackingMode.TRACKING,
                message = AppMessage.Info(
                    getApplication<Application>().getString(R.string.map_tracking_active),
                ),
            )
        }
        ensureMapTrackingUpdates()
        _state.value.userPosition?.let { pos ->
            applyTrackingLocation(pos, forceFollow = lastFollowAppliedLat == null)
        }
    }

    private fun startOrResumeMapTracking(
        focalScreenX: Float,
        focalScreenY: Float,
        bearing: Double,
        zoom: Double,
    ) {
        mapTrackingGestureHold = false
        followFocalScreenX = focalScreenX
        followFocalScreenY = focalScreenY
        followBearing = bearing
        followZoom = zoom
        lastFollowAppliedLat = null
        lastFollowAppliedLon = null
        _state.update {
            it.copy(
                mapTrackingMode = MapTrackingMode.TRACKING,
                message = AppMessage.Info(
                    getApplication<Application>().getString(R.string.map_tracking_active),
                ),
            )
        }
        ensureMapTrackingUpdates()
        // Immediate frame on current fix if we have one.
        _state.value.userPosition?.let { pos ->
            applyTrackingLocation(pos, forceFollow = true)
        }
    }

    private fun ensureMapTrackingUpdates() {
        if (mapTrackingJob?.isActive == true) return
        mapTrackingJob = viewModelScope.launch {
            locationProvider.locationUpdates().collect { outcome ->
                when (outcome) {
                    is LocationOutcome.Exact -> applyTrackingLocation(
                        UserPosition(outcome.location.latitude, outcome.location.longitude, false),
                    )
                    is LocationOutcome.Approximate -> applyTrackingLocation(
                        UserPosition(outcome.location.latitude, outcome.location.longitude, true),
                    )
                    is LocationOutcome.Failure -> {
                        mapTrackingJob?.cancel()
                        mapTrackingJob = null
                        _state.update {
                            it.copy(
                                mapTrackingMode = MapTrackingMode.OFF,
                                message = AppMessage.Error(outcome.reason),
                            )
                        }
                    }
                }
            }
        }
    }

    private fun applyTrackingLocation(pos: UserPosition, forceFollow: Boolean = false) {
        val mode = _state.value.mapTrackingMode
        if (mode != MapTrackingMode.TRACKING && mode != MapTrackingMode.PAUSED) return

        val movedEnough = forceFollow || lastFollowAppliedLat == null ||
            GeoUtils.distanceMeters(
                lastFollowAppliedLat!!,
                lastFollowAppliedLon!!,
                pos.latitude,
                pos.longitude,
            ) >= FOLLOW_MOVE_THRESHOLD_M

        _state.update { current ->
            val next = current.copy(userPosition = pos, isLocating = false)
            if (current.isMapBrowse()) {
                next.copy(
                    results = browseSelectionResults(
                        next.copy(userPosition = pos),
                        current.selectedSiteIds,
                    ),
                )
            } else {
                next
            }
        }

        if (mode == MapTrackingMode.TRACKING && movedEnough && !mapTrackingGestureHold) {
            lastFollowAppliedLat = pos.latitude
            lastFollowAppliedLon = pos.longitude
            _state.update {
                it.copy(mapFollowRevision = mapFollowRevision.incrementAndGet())
            }
        }
    }

    private fun applyLocation(pos: UserPosition, message: AppMessage?, forceCenter: Boolean = false) {
        val shouldCenter = forceCenter ||
            !liveGpsCentered ||
            _state.value.userPosition == null ||
            (_state.value.mapCameraRequest == null && _state.value.results.isEmpty())
        val camera = if (shouldCenter) {
            liveGpsCentered = true
            PagerNavigation.cameraForUserLocation(
                latitude = pos.latitude,
                longitude = pos.longitude,
                token = cameraToken.getAndIncrement(),
                zoom = 13.0,
            )
        } else {
            _state.value.mapCameraRequest
        }
        _state.update {
            it.copy(
                isLocating = false,
                userPosition = pos,
                searchOriginMode = if (
                    it.isMapBrowse() ||
                    it.searchOriginMode == SearchOriginMode.LINE ||
                    it.corridorAwaitMapEnd
                ) {
                    it.searchOriginMode
                } else {
                    SearchOriginMode.GPS
                },
                mapSearchPin = if (
                    it.isMapBrowse() || it.searchOriginMode == SearchOriginMode.LINE
                ) {
                    it.mapSearchPin
                } else {
                    null
                },
                localityDisplayName = if (
                    it.isMapBrowse() || it.searchOriginMode == SearchOriginMode.LINE
                ) {
                    it.localityDisplayName
                } else {
                    null
                },
                mapCameraRequest = camera,
                results = if (it.isMapBrowse()) {
                    browseSelectionResults(it.copy(userPosition = pos), it.selectedSiteIds)
                } else {
                    buildResults(
                        it.allSites,
                        when (it.searchOriginMode) {
                            SearchOriginMode.LINE -> it.searchOrigin() ?: pos
                            else -> pos
                        },
                        it.profile,
                        it.browseCarFilter,
                        it.roadBySiteId,
                        corridorLine = if (it.searchOriginMode == SearchOriginMode.LINE) {
                            it.corridorLine
                        } else {
                            emptyList()
                        },
                    )
                },
                message = message,
            )
        }
    }

    fun searchNearby() {
        if (_state.value.isMapBrowse()) {
            loadMapBrowseLayer(force = true)
            return
        }
        viewModelScope.launch {
            val purpose = _state.value.localityPickPurpose
            val lineNeedsLocality = _state.value.searchOriginMode == SearchOriginMode.LINE &&
                purpose != LocalityPickPurpose.SEARCH_ORIGIN
            when {
                lineNeedsLocality || _state.value.searchOriginMode == SearchOriginMode.LOCALITY ->
                    searchFromLocality()
                _state.value.searchOriginMode == SearchOriginMode.LINE -> performCorridorSearch()
                else -> {
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
        _state.update { it.copy(isSearching = true, message = null, localityCandidates = null) }
        try {
            val places = localityGeocoder.searchLocalities(query)
            if (places.isEmpty()) {
                _state.update {
                    it.copy(
                        isSearching = false,
                        localityCandidates = null,
                        message = AppMessage.Error("Nie znaleziono miejscowości „$query”. Spróbuj innej nazwy."),
                    )
                }
                return
            }
            // Always show the picker (even for one hit) so the user confirms the place.
            _state.update {
                it.copy(
                    isSearching = false,
                    localityCandidates = places,
                    message = AppMessage.Info(
                        if (places.size == 1) {
                            "Potwierdź miejscowość z listy."
                        } else {
                            "Wybierz miejscowość z listy (${places.size} wyników)."
                        },
                    ),
                    currentPage = AppPages.SEARCH,
                )
            }
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

    private suspend fun performCorridorSearch() {
        val state = _state.value
        val line = state.corridorLine
        if (line.size < 2) {
            _state.update {
                it.copy(
                    message = AppMessage.Error(
                        "Linia wymaga min. 2 punktów. Wybierz tryb „Wzdłuż linii” i klikaj mapę.",
                    ),
                )
            }
            return
        }
        if (state.corridorLeftKm + state.corridorRightKm <= 0.0) {
            _state.update {
                it.copy(message = AppMessage.Error("Ustaw lewą lub prawą szerokość korytarza (> 0 km)."))
            }
            return
        }
        val generation = searchGeneration.incrementAndGet()
        val origin = UserPosition(line.first().latitude, line.first().longitude, approximate = false)
        _state.update { it.copy(isSearching = true, isAnalyzingRoads = false, message = null) }
        try {
            val leftKm = state.corridorLeftKm
            val rightKm = state.corridorRightKm
            val profile = state.profile
            val startedAt = System.currentTimeMillis()
            val outcome = restRepository.findRestSitesAlongCorridor(line, leftKm, rightKm)
            val sorted = outcome.bundle.sites.sortedBy { site ->
                CorridorGeometry.project(site.latitude, site.longitude, line)?.distanceAlongKm
                    ?: Double.MAX_VALUE
            }
            lastBdlSearchContext = null
            val elapsedMs = System.currentTimeMillis() - startedAt
            val token = cameraToken.getAndIncrement()
            publishBdlSearchResults(
                generation = generation,
                position = origin,
                sites = sorted,
                zanocujPolygons = outcome.bundle.zanocujPolygons,
                radiusKm = maxOf(leftKm, rightKm),
                cameraToken = token,
                startRoadAnalysis = profile == TravelProfile.MOTORCYCLE && sorted.isNotEmpty(),
                corridorSummary = "korytarz L${leftKm.toInt()}/P${rightKm.toInt()} km · ${line.size} pkt · ${elapsedMs} ms",
                corridorLine = line,
            )
            if (profile == TravelProfile.MOTORCYCLE && sorted.isNotEmpty()) {
                analyzeRoadsInBackground(generation, sorted, origin)
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
                    message = AppMessage.Error("Wyszukiwanie wzdłuż linii: ${e.message ?: "błąd"}"),
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

    private suspend fun publishBdlSearchResults(
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
        corridorSummary: String? = null,
        corridorLine: List<LatLon> = emptyList(),
    ) {
        if (searchGeneration.get() != generation) return
        val snapshot = _state.value
        val roadBySiteId = if (retainedRoadAssessments.isNotEmpty()) {
            retainedRoadAssessments
        } else {
            emptyMap()
        }
        val lineForSort = corridorLine.ifEmpty {
            if (snapshot.searchOriginMode == SearchOriginMode.LINE) snapshot.corridorLine else emptyList()
        }
        val results = withContext(Dispatchers.Default) {
            buildResults(
                sites,
                position,
                snapshot.profile,
                snapshot.browseCarFilter,
                roadBySiteId = roadBySiteId,
                corridorLine = lineForSort,
            )
        }
        if (searchGeneration.get() != generation) return
        _state.update { current ->
            if (searchGeneration.get() != generation) return@update current
            val originLabel = when {
                corridorSummary != null -> corridorSummary
                current.searchOriginMode == SearchOriginMode.LOCALITY ->
                    "miejscowości ${current.localityQuery.trim().ifBlank { "?" }}"
                current.searchOriginMode == SearchOriginMode.MAP -> "punktu na mapie"
                current.searchOriginMode == SearchOriginMode.LINE -> "linii"
                else -> "GPS"
            }
            val offlineSuffix = if (current.offlineBdl.isReady) " · offline BDL" else ""
            val cacheSuffix = if (fromSessionCache) " · cache" else ""
            val subsetSuffix = if (fromRadiusSubset) " · filtr promienia" else ""
            val roadsPending = startRoadAnalysis && roadBySiteId.size < sites.size
            val motoSuffix = if (roadsPending) {
                " · drogi OSM w tle (top ${UiState.MOTORCYCLE_ROAD_ANALYZE_LIMIT})"
            } else {
                ""
            }
            val messageText = if (corridorSummary != null) {
                "Znaleziono ${sites.size} miejsc wzdłuż $originLabel$offlineSuffix$motoSuffix."
            } else {
                "Znaleziono ${sites.size} miejsc w promieniu ${radiusKm.toInt()} km od $originLabel$offlineSuffix$cacheSuffix$subsetSuffix$motoSuffix."
            }
            current.copy(
                isSearching = false,
                isAnalyzingRoads = roadsPending,
                roadAnalysisCompleted = 0,
                roadAnalysisTotal = if (roadsPending) {
                    minOf(sites.size, UiState.MOTORCYCLE_ROAD_ANALYZE_LIMIT)
                } else {
                    0
                },
                isFilteringPlaces = false,
                allSites = sites,
                zanocujPolygons = zanocujPolygons,
                roadBySiteId = roadBySiteId,
                results = results,
                selectedSiteIds = emptyList(),
                currentPage = AppPages.MAP,
                mapCameraRequest = PagerNavigation.cameraForNewSearch(cameraToken),
                message = AppMessage.Info(messageText),
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
                val toAssess = sites.take(UiState.MOTORCYCLE_ROAD_ANALYZE_LIMIT)
                val roadBySiteId = withContext(Dispatchers.IO) {
                    roadAnalyzer.assessAll(toAssess.map { it.toPointPoi() }) { completed, total ->
                        if (searchGeneration.get() == generation) {
                            _state.update { current ->
                                if (searchGeneration.get() != generation) {
                                    current
                                } else {
                                    current.copy(
                                        roadAnalysisCompleted = completed,
                                        roadAnalysisTotal = total,
                                    )
                                }
                            }
                        }
                    }
                }
                if (searchGeneration.get() != generation) return@launch
                val snapshot = _state.value
                val rebuiltResults = withContext(Dispatchers.Default) {
                    buildResults(
                        snapshot.allSites,
                        position,
                        snapshot.profile,
                        snapshot.browseCarFilter,
                        roadBySiteId,
                        corridorLine = if (snapshot.searchOriginMode == SearchOriginMode.LINE) {
                            snapshot.corridorLine
                        } else {
                            emptyList()
                        },
                    )
                }
                if (searchGeneration.get() != generation) return@launch
                _state.update { current ->
                    if (searchGeneration.get() != generation) return@update current
                    val next = current.copy(
                        isAnalyzingRoads = false,
                        roadAnalysisCompleted = toAssess.size,
                        roadAnalysisTotal = toAssess.size,
                        roadBySiteId = roadBySiteId,
                        results = rebuiltResults,
                        message = if (sites.size > toAssess.size) {
                            AppMessage.Info(
                                "Analiza dróg OSM: ${toAssess.size}/${sites.size} punktów (limit szybkości).",
                            )
                        } else {
                            current.message
                        },
                    )
                    next.copy(savedListResults = buildSavedListResults(next))
                }
            } catch (e: Exception) {
                if (searchGeneration.get() != generation) return@launch
                _state.update {
                    it.copy(
                        isAnalyzingRoads = false,
                        roadAnalysisCompleted = 0,
                        roadAnalysisTotal = 0,
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
        amenityFilter: BrowseCarFilter,
        roadBySiteId: Map<String, RoadAssessment>,
        corridorLine: List<LatLon> = emptyList(),
        corridorSites: List<RestSite> = emptyList(),
    ): List<RestSiteResult> {
        if (position == null && !_state.value.isMapBrowse()) return emptyList()
        val matchingIds = matchingSiteIds(sites, amenityFilter)
        val filtered = if (matchingIds == null) {
            sites
        } else {
            sites.filter { it.id in matchingIds }
        }
        return filtered
            .asSequence()
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
                val distanceKm = when {
                    position == null -> 0.0
                    corridorLine.size >= 2 -> {
                        CorridorGeometry.project(site.latitude, site.longitude, corridorLine)?.distanceAlongKm
                            ?: GeoUtils.distanceKm(
                                position.latitude,
                                position.longitude,
                                site.latitude,
                                site.longitude,
                            )
                    }
                    else -> GeoUtils.distanceKm(
                        position.latitude,
                        position.longitude,
                        site.latitude,
                        site.longitude,
                    )
                }
                RestSiteResult(
                    site = site,
                    distanceKm = distanceKm,
                    roadAssessment = assessment,
                    navigationTarget = target,
                    navigationTargetKind = kind,
                )
            }
            .sortedBy { it.distanceKm }
            .toList()
            .let { built -> markOfficialApproach(built, roadBySiteId, sites, corridorSites) }
    }

    private fun officialCorridor(
        roadBySiteId: Map<String, RoadAssessment>,
        vararg siteGroups: List<RestSite>,
    ) = MotorcycleAccessHint.collectCorridor(
        roadBySiteId.map { (id, assessment) ->
            val layerId = siteGroups.firstNotNullOfOrNull { group ->
                group.firstOrNull { it.id == id }?.sourceLayerId
            } ?: -1
            OfficialApproachEntry(
                sourceLayerId = layerId,
                road = assessment.nearestRoad,
            )
        },
    )

    private fun markOfficialApproach(
        results: List<RestSiteResult>,
        roadBySiteId: Map<String, RoadAssessment>,
        vararg extraSites: List<RestSite>,
    ): List<RestSiteResult> {
        if (results.isEmpty()) return results
        val corridor = officialCorridor(roadBySiteId, results.map { it.site }, *extraSites)
        return results.map { item ->
            item.copy(
                officialLpApproach = MotorcycleAccessHint.isOfficialLpApproach(
                    sourceLayerId = item.site.sourceLayerId,
                    latitude = item.site.latitude,
                    longitude = item.site.longitude,
                    assessment = item.roadAssessment,
                    corridor = corridor,
                ),
            )
        }
    }

    companion object {
        private const val EXPLORE_MODE_PREFS = "navilas_explore_mode"
        private const val EXPLORE_MODE_KEY = "last_mode"
        private const val LARGE_PARKING_FILTER_WARNING_METERS = 2_000
        /** Below this zoom, browse skips Zanocuj fills (too many polygons). */
        private const val BROWSE_ZANOCUJ_MIN_ZOOM = 7.5
        private const val BROWSE_ZANOCUJ_MAX_POLYGONS = 50
        private const val BROWSE_INITIAL_VIEWPORT_RADIUS_KM = 75.0
        private const val BROWSE_INITIAL_USER_ZOOM = 8.5
        private val POLAND_BROWSE_ENVELOPE = GeoUtils.Envelope(
            xmin = 14.0,
            ymin = 49.0,
            xmax = 24.5,
            ymax = 55.0,
        )
        private const val BROWSE_OVERLAY_MIN_ZOOM = 8.5
        private const val BROWSE_OVERLAY_MAX_POINTS = 400
        /** Same floor as Zanocuj fills so both zone layers appear together. */
        private const val ENTRY_BAN_MIN_ZOOM = BROWSE_ZANOCUJ_MIN_ZOOM
        private const val ENTRY_BAN_MAX_POLYGONS = 80
        /** Camera follow only after this GPS movement (metres). */
        private const val FOLLOW_MOVE_THRESHOLD_M = 5.0
        private const val MIN_FOLLOW_ZOOM = 3.0
        private const val MAX_FOLLOW_ZOOM = 20.0
        private const val EMPTY_MAP_CLICK_DEBOUNCE_MS = 400L
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
