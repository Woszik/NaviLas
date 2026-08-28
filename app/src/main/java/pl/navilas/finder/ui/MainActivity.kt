package pl.navilas.finder.ui

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.inputmethod.InputMethodManager
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.color.MaterialColors
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapView
import pl.navilas.finder.BuildConfig
import pl.navilas.finder.R
import pl.navilas.finder.update.AppUpdateInstaller
import pl.navilas.finder.update.AppUpdateOffer
import pl.navilas.finder.data.osm.RoadClassifier
import pl.navilas.finder.databinding.ActivityMainBinding
import pl.navilas.finder.databinding.BottomSheetMapFiltersBinding
import pl.navilas.finder.databinding.BrowseCarFilterControlsBinding
import pl.navilas.finder.databinding.PageListBinding
import pl.navilas.finder.databinding.PageMapBinding
import pl.navilas.finder.databinding.PageSearchBinding
import pl.navilas.finder.domain.BrowseCarFilter
import pl.navilas.finder.domain.BrowseParkingProximityMode
import pl.navilas.finder.domain.AppExploreMode
import pl.navilas.finder.domain.AppMessage
import pl.navilas.finder.domain.MapTrackingMode
import pl.navilas.finder.domain.NavigationTargetKind
import pl.navilas.finder.domain.RestSite
import pl.navilas.finder.domain.RestSiteResult
import pl.navilas.finder.domain.SearchConfig
import pl.navilas.finder.domain.SearchOriginMode
import pl.navilas.finder.domain.TravelProfile
import pl.navilas.finder.domain.ZanocujStatus
import pl.navilas.finder.domain.BdlDataScope
import pl.navilas.finder.domain.ListViewMode
import pl.navilas.finder.domain.OfflineBdlStatus
import pl.navilas.finder.domain.SavedPointCategory
import pl.navilas.finder.domain.ZanocujPolygonQuality
import pl.navilas.finder.domain.estimatedSizeLabel
import pl.navilas.finder.domain.featureSummaryPl
import pl.navilas.finder.domain.labelPl
import pl.navilas.finder.domain.toStars
import pl.navilas.finder.data.saved.SavedPointsBackupCodec
import pl.navilas.finder.data.saved.SavedPointsBackupParseResult
import pl.navilas.finder.data.saved.SavedPointsBackupSnapshot
import pl.navilas.finder.data.saved.SavedPointsImportMode
import pl.navilas.finder.nav.NavigationLinks
import java.io.File
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var searchBinding: PageSearchBinding
    private lateinit var mapBinding: PageMapBinding
    private lateinit var listBinding: PageListBinding
    private lateinit var mapView: MapView
    private val viewModel: MainViewModel by viewModels()
    private val mapController = MapController()
    private lateinit var resultsAdapter: ResultsAdapter
    private var mapReady = false
    private var currentProfile: TravelProfile = TravelProfile.CAR
    private var syncingPager = false
    private var syncingOfflineUi = false
    private var syncingSearchOriginUi = false
    private var syncingBrowseCarFilterUi = false
    private var browseCarFilterExpanded = false
    private var syncingExploreModeUi = false
    private var mapFilterBottomSheet: BottomSheetDialog? = null
    private var lastBrowseCarFilterToken: Int = 0
    private var syncingCorridorUi = false
    private var syncingListUi = false
    private var offlineSectionExpanded = false
    private var lastRenderedResultsToken: Int = -1
    private var lastAppliedCameraToken: Long = -1L
    private var lastAppliedFollowRevision: Long = -1L
    private var updateOfferDialog: AlertDialog? = null
    private var updateProgressDialog: AlertDialog? = null
    private var updateProgressBar: ProgressBar? = null
    private var updateProgressText: TextView? = null
    private var shownUpdateVersionCode: Int? = null
    private var pendingInstallApk: File? = null
    /** Map "my location" FAB should always re-center; search locate may not. */
    private var pendingForceCenterOnLocate = false
    /** After permission grant: toggle map tracking instead of one-shot locate. */
    private var pendingTrackingToggle = false
    /** After returning from unknown-sources settings: start download or install once. */
    private var pendingAfterInstallPermission: PendingAfterInstallPermission? = null
    private var installSessionStarted = false
    private var installReceiverRegistered = false
    private var waitingForSystemInstallerUi = false
    private var pendingImportSnapshot: SavedPointsBackupSnapshot? = null

    private enum class PendingAfterInstallPermission {
        START_DOWNLOAD,
        INSTALL_APK,
    }

    private val installStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, Int.MIN_VALUE)) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    val confirm = if (Build.VERSION.SDK_INT >= 33) {
                        intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(Intent.EXTRA_INTENT)
                    }
                    if (confirm != null) {
                        waitingForSystemInstallerUi = true
                        setUpdateProgressPhase(getString(R.string.app_update_preparing_installer), indeterminate = true)
                        startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }
                PackageInstaller.STATUS_SUCCESS -> {
                    waitingForSystemInstallerUi = false
                    dismissUpdateProgressDialog()
                    unregisterInstallReceiver()
                }
                PackageInstaller.STATUS_FAILURE,
                PackageInstaller.STATUS_FAILURE_ABORTED,
                PackageInstaller.STATUS_FAILURE_BLOCKED,
                PackageInstaller.STATUS_FAILURE_CONFLICT,
                PackageInstaller.STATUS_FAILURE_INCOMPATIBLE,
                PackageInstaller.STATUS_FAILURE_INVALID,
                PackageInstaller.STATUS_FAILURE_STORAGE,
                -> {
                    waitingForSystemInstallerUi = false
                    dismissUpdateProgressDialog()
                    unregisterInstallReceiver()
                    val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                        ?: getString(R.string.app_update_permission_denied)
                    Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
                }
                else -> {
                    if (status != Int.MIN_VALUE && status < 0) {
                        waitingForSystemInstallerUi = false
                        dismissUpdateProgressDialog()
                        unregisterInstallReceiver()
                        Snackbar.make(
                            binding.root,
                            intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                                ?: "Instalacja nie powiodła się ($status)",
                            Snackbar.LENGTH_LONG,
                        ).show()
                    }
                }
            }
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val fine = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarse = grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        val forTracking = pendingTrackingToggle
        pendingTrackingToggle = false
        when {
            fine || coarse -> {
                if (forTracking) {
                    toggleMapTrackingFromUi()
                } else {
                    viewModel.refreshLocation(forceCenter = pendingForceCenterOnLocate)
                }
            }
            else -> Snackbar.make(
                binding.root,
                "Odmówiono lokalizacji. Mapa działa, ale wyszukiwanie wymaga pozycji.",
                Snackbar.LENGTH_LONG,
            ).show()
        }
        pendingForceCenterOnLocate = false
    }

    private val unknownSourcesLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        resumePendingAfterInstallPermission()
    }

    private val exportSavedPointsLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument(SavedPointsBackupCodec.MIME_TYPE),
    ) { uri ->
        if (uri != null) writeSavedPointsExport(uri)
    }

    private val importSavedPointsLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) readSavedPointsImport(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val inflater = layoutInflater
        searchBinding = PageSearchBinding.inflate(inflater)
        mapBinding = PageMapBinding.inflate(inflater)
        listBinding = PageListBinding.inflate(inflater)

        resultsAdapter = ResultsAdapter(
            onDetails = { item -> showDetails(item) },
            onNavigate = { item -> showNavigateChooser(item) },
            onSelect = { item -> viewModel.onListItemSelected(item.site.id) },
            onSave = { item -> onSaveClicked(item) },
            profileProvider = { currentProfile },
            isSavedProvider = { siteId -> viewModel.state.value.isSaved(siteId) },
            savedMetaProvider = { siteId -> savedMetaFor(siteId) },
            distanceLabelProvider = { item ->
                formatPoiDistance(viewModel.state.value, item.distanceKm)
            },
        )
        listBinding.resultsList.layoutManager = LinearLayoutManager(this)
        listBinding.resultsList.adapter = resultsAdapter

        mapView = mapBinding.mapView
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync { map ->
            map.uiSettings.isAttributionEnabled = true
            mapController.attach(map) {
                mapReady = true
                mapController.setOnSiteClickListener { siteId ->
                    viewModel.onMarkerSelected(siteId)
                }
                mapController.setOnEmptyMapClickListener { lat, lon ->
                    if (viewModel.state.value.isMapBrowse()) {
                        viewModel.selectSite(null)
                    } else {
                        viewModel.setMapSearchPin(lat, lon)
                    }
                }
                mapController.setOnCorridorVertexClickListener { index ->
                    showCorridorVertexMenu(index)
                }
                mapController.setOnCameraIdleListener { west, south, east, north, zoom, bearing ->
                    mapBinding.mapZoomScale.text = getString(R.string.map_zoom_scale, zoom)
                    viewModel.onBrowseMapViewport(west, south, east, north, zoom)
                    val pos = viewModel.state.value.userPosition
                    val screen = pos?.let {
                        mapController.screenLocationOf(it.latitude, it.longitude)
                    }
                    viewModel.onMapGestureEnded(
                        bearing = bearing,
                        zoom = zoom,
                        focalScreenX = screen?.x,
                        focalScreenY = screen?.y,
                    )
                }
                mapController.setOnGestureCameraMoveStartedListener {
                    viewModel.onMapTrackingGestureStarted()
                }
                applyUi(viewModel.state.value, forceMarkers = true)
                viewModel.refreshLocation(showApproximateHint = false)
            }
        }

        binding.pager.adapter = MainPagerAdapter(searchBinding.root, mapBinding.root, listBinding.root)
        binding.pager.offscreenPageLimit = AppPages.COUNT - 1
        binding.pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                if (syncingPager) return
                viewModel.setCurrentPage(position)
                updatePageIndicator(position)
            }
        })

        binding.pageLabels.text = getString(R.string.page_labels)
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_about -> {
                    showAboutDialog()
                    true
                }
                else -> false
            }
        }

        setupPageChromeNavigation()
        setupSearchPage()
        setupListPage()
        setupMapCard()
        setupBackHandler()
        observeState()
        requestLocationPermissions()
    }

    private fun setupPageChromeNavigation() {
        val goNext = {
            val next = PagerNavigation.pageAfterSwipeLeft(viewModel.state.value.currentPage)
            viewModel.setCurrentPage(next)
        }
        val goPrev = {
            val prev = PagerNavigation.pageAfterSwipeRight(viewModel.state.value.currentPage)
            viewModel.setCurrentPage(prev)
        }
        binding.toolbarSwipeZone.onSwipeToNext = goNext
        binding.toolbarSwipeZone.onSwipeToPrevious = goPrev
        binding.pageIndicatorBar.onSwipeToNext = goNext
        binding.pageIndicatorBar.onSwipeToPrevious = goPrev
        binding.btnPageNext.setOnClickListener { goNext() }
        binding.btnPagePrev.setOnClickListener { goPrev() }
    }

    private fun showAboutDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_about, null)
        view.findViewById<TextView>(R.id.aboutVersion).text = getString(
            R.string.about_version_format,
            BuildConfig.VERSION_NAME,
            BuildConfig.VERSION_CODE,
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.about_title)
            .setView(view)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun setupSearchPage() {
        setupRadiusSpinner()
        searchBinding.profileToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val profile = when (checkedId) {
                R.id.btnProfileCar -> TravelProfile.CAR
                R.id.btnProfileMoto -> TravelProfile.MOTORCYCLE
                else -> return@addOnButtonCheckedListener
            }
            viewModel.setProfile(profile)
        }
        searchBinding.exploreModeToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked || syncingExploreModeUi) return@addOnButtonCheckedListener
            val mode = when (checkedId) {
                R.id.btnExploreBrowse -> AppExploreMode.MAP_BROWSE
                else -> AppExploreMode.SEARCH
            }
            viewModel.setExploreMode(mode)
        }
        searchBinding.zanocujFilter.setOnCheckedChangeListener(null)
        setupBrowseCarFilterSection()
        searchBinding.btnSearch.setOnClickListener { viewModel.searchNearby() }
        searchBinding.btnLocate.setOnClickListener { requestLocationPermissions() }
        searchBinding.btnClearMapPin.setOnClickListener { viewModel.clearMapSearchPin() }
        searchBinding.appVersionLabel.text = getString(R.string.app_version_label, BuildConfig.VERSION_NAME)
        searchBinding.btnCheckUpdate.isVisible = BuildConfig.APP_UPDATE_ENABLED
        if (BuildConfig.APP_UPDATE_ENABLED) {
            searchBinding.btnCheckUpdate.setOnClickListener { viewModel.checkForAppUpdate(force = true) }
        }
        setupSearchOriginSection()
        setupOfflineSection()
    }

    private fun applyMapBrowseSearchChrome(state: UiState) {
        searchBinding.mapBrowseHint.isVisible = true
        searchBinding.zanocujLabel.isVisible = false
        searchBinding.zanocujFilter.isVisible = false
        searchBinding.browseParkingOnly.isVisible = false
        searchBinding.browseCarFilterSection.isVisible = true
        bindBrowseCarFilterUi(state)
        searchBinding.searchOriginLabel.isVisible = false
        searchBinding.searchOriginGroup.isVisible = false
        searchBinding.corridorPanel.isVisible = false
        searchBinding.localityInputLayout.isVisible = false
        searchBinding.localityCandidatesPanel.isVisible = false
        searchBinding.radiusLabel.isVisible = false
        searchBinding.radiusSpinner.isVisible = false
        setCustomRadiusUiVisible(false)
        searchBinding.btnClearMapPin.isVisible = false
        searchBinding.btnSearch.text = getString(R.string.map_browse_reload)
        searchBinding.searchHint.setText(R.string.search_page_hint_browse)
    }

    private fun applySearchModeChrome(state: UiState) {
        searchBinding.mapBrowseHint.isVisible = false
        searchBinding.zanocujLabel.isVisible = false
        searchBinding.zanocujFilter.isVisible = false
        searchBinding.browseParkingOnly.isVisible = false
        searchBinding.browseCarFilterSection.isVisible = true
        bindBrowseCarFilterUi(state)
        searchBinding.searchOriginLabel.isVisible = true
        searchBinding.searchOriginGroup.isVisible = true
        bindSearchOriginUi(state)
        updateSearchButtonLabel(
            state.searchConfig.searchRadiusKm,
            lineMode = state.searchOriginMode == SearchOriginMode.LINE,
        )
        searchBinding.searchHint.setText(R.string.search_page_hint)
    }

    private fun setupBrowseCarFilterSection() {
        setupFilterControlsListeners(searchBinding.browseCarFilterControls)
        searchBinding.btnToggleBrowseCarFilter.setOnClickListener {
            if (browseCarFilterExpanded) {
                applyBrowseCarFilterFromUi()
                browseCarFilterExpanded = false
            } else {
                syncBrowseCarFilterDraftTo(
                    searchBinding.browseCarFilterControls,
                    viewModel.state.value.browseCarFilter,
                )
                browseCarFilterExpanded = true
            }
            updateBrowseCarFilterChrome(viewModel.state.value)
        }
        searchBinding.btnApplyBrowseCarFilter.setOnClickListener {
            applyBrowseCarFilterFromUi()
            browseCarFilterExpanded = false
            updateBrowseCarFilterChrome(viewModel.state.value)
            bindMapFilterFab(viewModel.state.value)
        }
    }

    private fun setupFilterControlsListeners(
        controls: BrowseCarFilterControlsBinding,
        onDraftChanged: (() -> Unit)? = null,
    ) {
        fun notifyDraftChanged() {
            if (!syncingBrowseCarFilterUi) onDraftChanged?.invoke()
        }

        val checkboxListener = CompoundButton.OnCheckedChangeListener { _, _ ->
            notifyDraftChanged()
        }
        controls.browseFilterLawostoly.setOnCheckedChangeListener(checkboxListener)
        controls.browseFilterWiata.setOnCheckedChangeListener(checkboxListener)
        controls.browseFilterPalenisko.setOnCheckedChangeListener(checkboxListener)
        controls.browseFilterWodaPitna.setOnCheckedChangeListener(checkboxListener)
        controls.browseFilterZrodlo.setOnCheckedChangeListener(checkboxListener)
        controls.browseFilterZanocuj.setOnCheckedChangeListener(checkboxListener)

        controls.browseFilterParking.setOnCheckedChangeListener { _, checked ->
            if (!syncingBrowseCarFilterUi) {
                updateBrowseParkingSubControlsEnabled(controls, checked)
                notifyDraftChanged()
            }
        }
        controls.browseParkingProximityToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked || syncingBrowseCarFilterUi) return@addOnButtonCheckedListener
            updateBrowseParkingMetersEnabled(
                controls,
                controls.browseFilterParking.isChecked &&
                    checkedId == R.id.btnParkingMaxDistance,
            )
            notifyDraftChanged()
        }
        controls.browseParkingMaxMeters.setOnFocusChangeListener { _, hasFocus ->
            if (syncingBrowseCarFilterUi) return@setOnFocusChangeListener
            if (hasFocus) {
                controls.browseParkingMaxMeters.setText("")
                controls.browseParkingMaxMeters.post {
                    val imm = getSystemService(InputMethodManager::class.java)
                    imm?.showSoftInput(controls.browseParkingMaxMeters, InputMethodManager.SHOW_IMPLICIT)
                }
            } else {
                val text = controls.browseParkingMaxMeters.text?.toString()?.trim()
                if (text.isNullOrEmpty()) {
                    controls.browseParkingMaxMeters.setText(
                        BrowseCarFilter.DEFAULT_PARKING_MAX_METERS.toString(),
                    )
                }
                notifyDraftChanged()
            }
        }
        if (onDraftChanged != null) {
            controls.browseParkingMaxMeters.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    notifyDraftChanged()
                }
            })
        }
    }

    private fun applyBrowseCarFilterFromUi() {
        applyBrowseCarFilterFrom(searchBinding.browseCarFilterControls)
    }

    private fun applyBrowseCarFilterFrom(controls: BrowseCarFilterControlsBinding) {
        viewModel.setBrowseCarFilter(readBrowseCarFilterFrom(controls))
    }

    private fun updateBrowseParkingSubControlsEnabled(
        controls: BrowseCarFilterControlsBinding,
        parkingChecked: Boolean,
    ) {
        controls.browseParkingProximityToggle.isEnabled = parkingChecked
        val maxDistance = controls.btnParkingMaxDistance.isChecked
        updateBrowseParkingMetersEnabled(controls, parkingChecked && maxDistance)
    }

    private fun updateBrowseParkingMetersEnabled(
        controls: BrowseCarFilterControlsBinding,
        enabled: Boolean,
    ) {
        controls.browseParkingMaxMetersLayout.isEnabled = enabled
        controls.browseParkingMaxMeters.isEnabled = enabled
    }

    private fun readBrowseCarFilterFrom(controls: BrowseCarFilterControlsBinding): BrowseCarFilter {
        val metersText = controls.browseParkingMaxMeters.text?.toString()?.trim().orEmpty()
        val meters = metersText.toIntOrNull()?.coerceIn(1, BrowseCarFilter.MAX_PARKING_METERS)
            ?: BrowseCarFilter.DEFAULT_PARKING_MAX_METERS
        val parkingMode = if (controls.btnParkingMaxDistance.isChecked) {
            BrowseParkingProximityMode.MAX_DISTANCE
        } else {
            BrowseParkingProximityMode.NEAR_POINT
        }
        return BrowseCarFilter(
            requireLawostoly = controls.browseFilterLawostoly.isChecked,
            requireWiata = controls.browseFilterWiata.isChecked,
            requirePalenisko = controls.browseFilterPalenisko.isChecked,
            requireWodaPitna = controls.browseFilterWodaPitna.isChecked,
            requireZrodlo = controls.browseFilterZrodlo.isChecked,
            requireParking = controls.browseFilterParking.isChecked,
            parkingMode = parkingMode,
            parkingMaxMeters = meters,
            requireZanocujInZone = controls.browseFilterZanocuj.isChecked,
        )
    }

    private fun syncBrowseCarFilterDraftTo(
        controls: BrowseCarFilterControlsBinding,
        filter: BrowseCarFilter,
    ) {
        syncingBrowseCarFilterUi = true
        controls.browseFilterLawostoly.isChecked = filter.requireLawostoly
        controls.browseFilterWiata.isChecked = filter.requireWiata
        controls.browseFilterPalenisko.isChecked = filter.requirePalenisko
        controls.browseFilterWodaPitna.isChecked = filter.requireWodaPitna
        controls.browseFilterZrodlo.isChecked = filter.requireZrodlo
        controls.browseFilterParking.isChecked = filter.requireParking
        controls.browseFilterZanocuj.isChecked = filter.requireZanocujInZone
        when (filter.parkingMode) {
            BrowseParkingProximityMode.NEAR_POINT ->
                controls.browseParkingProximityToggle.check(R.id.btnParkingNearPoint)
            BrowseParkingProximityMode.MAX_DISTANCE ->
                controls.browseParkingProximityToggle.check(R.id.btnParkingMaxDistance)
        }
        val metersText = filter.parkingMaxMeters.toString()
        if (controls.browseParkingMaxMeters.text?.toString() != metersText) {
            controls.browseParkingMaxMeters.setText(metersText)
        }
        updateBrowseParkingSubControlsEnabled(controls, filter.requireParking)
        syncingBrowseCarFilterUi = false
    }

    private fun updateBrowseCarFilterChrome(state: UiState) {
        searchBinding.browseCarFilterPanel.isVisible = browseCarFilterExpanded
        searchBinding.browseCarFilterSummary.isVisible = !browseCarFilterExpanded
        searchBinding.browseCarFilterSummary.text = state.browseCarFilter.summaryPl()
        val chevron = if (browseCarFilterExpanded) " ▲" else " ▼"
        searchBinding.btnToggleBrowseCarFilter.text =
            getString(R.string.browse_car_filter_toggle) + chevron
    }

    private fun bindBrowseCarFilterUi(state: UiState) {
        if (browseCarFilterExpanded) {
            syncBrowseCarFilterDraftTo(searchBinding.browseCarFilterControls, state.browseCarFilter)
        }
        updateBrowseCarFilterChrome(state)
        bindMapFilterFab(state)
    }

    private fun bindMapFilterFab(state: UiState) {
        val summary = state.browseCarFilter.summaryPl()
        mapBinding.btnMapPlaceFilters.contentDescription =
            getString(R.string.map_filter_fab) + ": " + summary
        val tintAttr = if (state.browseCarFilter.isActive) {
            com.google.android.material.R.attr.colorPrimaryContainer
        } else {
            com.google.android.material.R.attr.colorSecondaryContainer
        }
        val onTintAttr = if (state.browseCarFilter.isActive) {
            com.google.android.material.R.attr.colorOnPrimaryContainer
        } else {
            com.google.android.material.R.attr.colorOnSecondaryContainer
        }
        mapBinding.btnMapPlaceFilters.backgroundTintList =
            android.content.res.ColorStateList.valueOf(
                MaterialColors.getColor(mapBinding.btnMapPlaceFilters, tintAttr),
            )
        mapBinding.btnMapPlaceFilters.iconTint =
            android.content.res.ColorStateList.valueOf(
                MaterialColors.getColor(mapBinding.btnMapPlaceFilters, onTintAttr),
            )
        mapBinding.btnMapPlaceFilters.setTextColor(
            MaterialColors.getColor(mapBinding.btnMapPlaceFilters, onTintAttr),
        )
    }

    private fun showMapFilterBottomSheet() {
        mapFilterBottomSheet?.dismiss()
        val sheetBinding = BottomSheetMapFiltersBinding.inflate(layoutInflater)
        val filter = viewModel.state.value.browseCarFilter
        fun refreshSheetSummary() {
            sheetBinding.mapFilterSheetSummary.text =
                readBrowseCarFilterFrom(sheetBinding.mapFilterControls).summaryPl()
        }
        syncBrowseCarFilterDraftTo(sheetBinding.mapFilterControls, filter)
        refreshSheetSummary()
        setupFilterControlsListeners(
            sheetBinding.mapFilterControls,
            onDraftChanged = { refreshSheetSummary() },
        )
        sheetBinding.btnApplyMapFilter.setOnClickListener {
            applyBrowseCarFilterFrom(sheetBinding.mapFilterControls)
            browseCarFilterExpanded = false
            updateBrowseCarFilterChrome(viewModel.state.value)
            bindMapFilterFab(viewModel.state.value)
            mapFilterBottomSheet?.dismiss()
        }
        val dialog = BottomSheetDialog(this)
        dialog.setContentView(sheetBinding.root)
        mapFilterBottomSheet = dialog
        dialog.show()
    }

    private fun applyBrowseMapFilters(state: UiState) {
        if (!state.isMapBrowse() || state.mapBrowseRevision <= 0) return
        val match = viewModel.browseCarMatchingIds(state)
        val token = (state.browseCarFilter.hashCode()) xor
            (match?.size ?: -1) xor
            state.mapBrowseRevision.hashCode() xor
            state.allSites.size
        if (token == lastBrowseCarFilterToken) return
        lastBrowseCarFilterToken = token
        mapController.setBrowseLayerMatchFlags(state.allSites, match)
    }

    private fun setupSearchOriginSection() {
        searchBinding.searchOriginGroup.setOnCheckedChangeListener { _, checkedId ->
            if (syncingSearchOriginUi) return@setOnCheckedChangeListener
            val mode = when (checkedId) {
                R.id.radioSearchMap -> SearchOriginMode.MAP
                R.id.radioSearchLocality -> SearchOriginMode.LOCALITY
                R.id.radioSearchLine -> SearchOriginMode.LINE
                else -> SearchOriginMode.GPS
            }
            viewModel.setSearchOriginMode(mode)
            if (mode == SearchOriginMode.LINE) {
                viewModel.setCurrentPage(AppPages.MAP)
            }
        }
        searchBinding.localityInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                viewModel.searchNearby()
                true
            } else {
                false
            }
        }
        searchBinding.localityInput.addTextChangedListener(
            object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: android.text.Editable?) {
                    if (syncingSearchOriginUi) return
                    viewModel.setLocalityQuery(s?.toString().orEmpty())
                }
            },
        )
        searchBinding.btnClearCorridor.setOnClickListener { viewModel.clearCorridorLine() }
        searchBinding.btnLineGpsMap.setOnClickListener { viewModel.startCorridorGpsToMap() }
        searchBinding.btnLineGpsLocality.setOnClickListener { viewModel.startCorridorGpsToLocality() }
        searchBinding.btnLineLocalityLocality.setOnClickListener { viewModel.startCorridorLocalityToLocality() }
        searchBinding.btnDismissLocalityCandidates.setOnClickListener {
            viewModel.dismissLocalityCandidates()
        }
        searchBinding.corridorLeftInput.addTextChangedListener(
            object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: android.text.Editable?) {
                    if (syncingCorridorUi) return
                    s?.toString()?.replace(',', '.')?.toDoubleOrNull()?.let { viewModel.setCorridorLeftKm(it) }
                }
            },
        )
        searchBinding.corridorRightInput.addTextChangedListener(
            object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: android.text.Editable?) {
                    if (syncingCorridorUi) return
                    s?.toString()?.replace(',', '.')?.toDoubleOrNull()?.let { viewModel.setCorridorRightKm(it) }
                }
            },
        )
    }

    private fun setupOfflineSection() {
        searchBinding.btnToggleOffline.setOnClickListener {
            offlineSectionExpanded = !offlineSectionExpanded
            updateOfflinePanelVisibility()
        }
        searchBinding.offlineScopeGroup.setOnCheckedChangeListener { _, checkedId ->
            if (syncingOfflineUi) return@setOnCheckedChangeListener
            val scope = when (checkedId) {
                R.id.radioOfflineFull -> BdlDataScope.FULL_BDL
                else -> BdlDataScope.NAVILAS_CORE
            }
            viewModel.setOfflineScope(scope)
        }
        searchBinding.offlineZanocujGroup.setOnCheckedChangeListener { _, checkedId ->
            if (syncingOfflineUi) return@setOnCheckedChangeListener
            val quality = when (checkedId) {
                R.id.radioZanocujPrecise -> ZanocujPolygonQuality.PRECISE
                else -> ZanocujPolygonQuality.SIMPLIFIED
            }
            viewModel.setZanocujQuality(quality)
        }
        searchBinding.btnDownloadOffline.setOnClickListener { viewModel.downloadOfflineData() }
        searchBinding.btnDeleteOffline.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.offline_delete)
                .setMessage(R.string.offline_delete_confirm)
                .setPositiveButton(android.R.string.ok) { _, _ -> viewModel.deleteOfflineData() }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun setupListPage() {
        listBinding.listModeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked || syncingListUi) return@addOnButtonCheckedListener
            val mode = when (checkedId) {
                R.id.btnListSaved -> ListViewMode.SAVED
                else -> ListViewMode.SEARCH
            }
            viewModel.setListViewMode(mode)
        }
        listBinding.btnManageCategories.setOnClickListener { showManageCategoriesDialog() }
        listBinding.btnSavedBackup.setOnClickListener { showSavedBackupMenu(it) }
        listBinding.savedCategoryFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long,
            ) {
                if (syncingListUi) return
                val state = viewModel.state.value
                if (state.listViewMode != ListViewMode.SAVED) return
                val filterId = savedFilterIdAtPosition(state.savedCategories, position)
                if (filterId != state.savedCategoryFilterId) {
                    viewModel.setSavedCategoryFilter(filterId)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun showSavedBackupMenu(anchor: View) {
        PopupMenu(this, anchor).apply {
            menuInflater.inflate(R.menu.menu_saved_backup, menu)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_saved_export -> {
                        startSavedPointsExport()
                        true
                    }
                    R.id.action_saved_import -> {
                        importSavedPointsLauncher.launch(arrayOf(SavedPointsBackupCodec.MIME_TYPE, "*/*"))
                        true
                    }
                    else -> false
                }
            }
            show()
        }
    }

    private fun startSavedPointsExport() {
        if (viewModel.savedPointsCount() == 0) {
            Snackbar.make(binding.root, R.string.saved_export_empty, Snackbar.LENGTH_LONG).show()
            return
        }
        exportSavedPointsLauncher.launch(SavedPointsBackupCodec.suggestedExportFilename())
    }

    private fun writeSavedPointsExport(uri: Uri) {
        lifecycleScope.launch {
            val json = withContext(Dispatchers.Default) {
                viewModel.buildSavedPointsExportJson()
            }
            val count = viewModel.savedPointsCount()
            val written = withContext(Dispatchers.IO) {
                runCatching {
                    contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(json.toByteArray(Charsets.UTF_8))
                    } ?: error("no stream")
                }.isSuccess
            }
            if (written) {
                Snackbar.make(
                    binding.root,
                    getString(R.string.saved_export_success, count),
                    Snackbar.LENGTH_LONG,
                ).show()
            } else {
                Snackbar.make(binding.root, R.string.saved_export_error, Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun readSavedPointsImportText(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            contentResolver.openInputStream(uri)?.use { stream ->
                val bytes = stream.readBytes()
                if (bytes.size > SavedPointsBackupCodec.MAX_FILE_BYTES) return@runCatching null
                bytes.toString(Charsets.UTF_8)
            }
        }.getOrNull()
    }

    private fun readSavedPointsImport(uri: Uri) {
        lifecycleScope.launch {
            val text = readSavedPointsImportText(uri)
            if (text == null) {
                Snackbar.make(binding.root, R.string.saved_import_read_error, Snackbar.LENGTH_LONG).show()
                return@launch
            }
            showSavedPointsImportDialog(text)
        }
    }

    private fun showSavedPointsImportDialog(jsonText: String) {
        lifecycleScope.launch {
            val parsed = withContext(Dispatchers.Default) {
                viewModel.parseSavedPointsImport(jsonText)
            }
            when (parsed) {
                is SavedPointsBackupParseResult.Failure -> {
                    Snackbar.make(
                        binding.root,
                        getString(R.string.saved_import_error, parsed.message),
                        Snackbar.LENGTH_LONG,
                    ).show()
                }
                is SavedPointsBackupParseResult.Success -> {
                    pendingImportSnapshot = parsed.snapshot
                    val skippedSuffix = if (parsed.skippedPoints > 0) {
                        getString(R.string.saved_import_skipped_suffix, parsed.skippedPoints)
                    } else {
                        ""
                    }
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle(R.string.saved_import)
                        .setMessage(
                            getString(
                                R.string.saved_import_preview,
                                parsed.snapshot.points.size,
                                parsed.snapshot.categories.size,
                                skippedSuffix,
                            ),
                        )
                        .setPositiveButton(R.string.saved_import_merge) { _, _ ->
                            applySavedPointsImport(SavedPointsImportMode.MERGE)
                        }
                        .setNeutralButton(R.string.saved_import_replace) { _, _ ->
                            confirmReplaceSavedPointsImport()
                        }
                        .setNegativeButton(android.R.string.cancel) { _, _ ->
                            pendingImportSnapshot = null
                        }
                        .show()
                }
            }
        }
    }

    private fun confirmReplaceSavedPointsImport() {
        val count = viewModel.savedPointsCount()
        if (count == 0) {
            applySavedPointsImport(SavedPointsImportMode.REPLACE)
            return
        }
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.saved_import_replace_confirm, count))
            .setPositiveButton(R.string.saved_import_replace) { _, _ ->
                applySavedPointsImport(SavedPointsImportMode.REPLACE)
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                pendingImportSnapshot = null
            }
            .show()
    }

    private fun applySavedPointsImport(mode: SavedPointsImportMode) {
        val snapshot = pendingImportSnapshot ?: return
        pendingImportSnapshot = null
        val result = viewModel.importSavedPoints(snapshot, mode)
        val skippedSuffix = if (result.skippedPoints > 0) {
            getString(R.string.saved_import_skipped_points_suffix, result.skippedPoints)
        } else {
            ""
        }
        Snackbar.make(
            binding.root,
            getString(
                R.string.saved_import_success,
                result.addedPoints + result.updatedPoints,
                result.addedPoints,
                result.updatedPoints,
                skippedSuffix,
            ),
            Snackbar.LENGTH_LONG,
        ).show()
    }

    private fun setupMapCard() {
        mapBinding.btnMapMyLocation.setOnClickListener { requestLocationPermissions(forceCenter = true) }
        mapBinding.btnMapTrackLocation.setOnClickListener {
            requestLocationPermissions(forceCenter = false, forTrackingToggle = true)
        }
        mapBinding.btnMapPlaceFilters.setOnClickListener { showMapFilterBottomSheet() }
        mapBinding.btnCloseCard.setOnClickListener { viewModel.closeSelectedSite() }
        mapBinding.btnSaveCard.setOnClickListener {
            selectedResult()?.let { onSaveClicked(it) }
        }
        mapBinding.cardEditSaved.setOnClickListener {
            selectedResult()?.let { showEditSavedDialog(it.site.id) }
        }
        mapBinding.cardDetails.setOnClickListener {
            selectedResult()?.let { showDetails(it) }
        }
        mapBinding.cardNavigate.setOnClickListener {
            selectedResult()?.let { showNavigateChooser(it) }
        }
    }

    private fun setupBackHandler() {
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (!viewModel.onBackPressed()) {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                    }
                }
            },
        )
    }

    private fun setupRadiusSpinner() {
        val presets = SearchConfig.SEARCH_RADIUS_PRESETS_KM
        val labels = presets.map { "${it.toInt()} km" } + getString(R.string.search_radius_custom_option)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
        searchBinding.radiusSpinner.adapter = adapter
        val defaultIndex = presets.indexOf(SearchConfig.DEFAULT_SEARCH_RADIUS_KM).coerceAtLeast(0)
        searchBinding.radiusSpinner.setSelection(defaultIndex, false)
        setCustomRadiusUiVisible(false)
        updateSearchButtonLabel(presets[defaultIndex], lineMode = false)
        searchBinding.radiusSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position < presets.size) {
                    setCustomRadiusUiVisible(false)
                    val km = presets[position]
                    viewModel.setSearchRadiusKm(km)
                    updateSearchButtonLabel(km, lineMode = viewModel.state.value.searchOriginMode == SearchOriginMode.LINE)
                } else {
                    setCustomRadiusUiVisible(true)
                    searchBinding.customRadiusInput.setText(
                        viewModel.state.value.searchConfig.searchRadiusKm.toInt().toString(),
                    )
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        searchBinding.btnApplyCustomRadius.setOnClickListener {
            val raw = searchBinding.customRadiusInput.text?.toString()?.trim().orEmpty()
            val km = raw.replace(',', '.').toDoubleOrNull()
            if (km == null) {
                Snackbar.make(binding.root, "Podaj liczbę km (1–100).", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.setSearchRadiusKm(km)
            val applied = viewModel.state.value.searchConfig.searchRadiusKm
            val presetIndex = presets.indexOf(applied)
            if (presetIndex >= 0) {
                searchBinding.radiusSpinner.setSelection(presetIndex, false)
                setCustomRadiusUiVisible(false)
            }
            updateSearchButtonLabel(applied, lineMode = viewModel.state.value.searchOriginMode == SearchOriginMode.LINE)
        }
    }

    private fun setCustomRadiusUiVisible(visible: Boolean) {
        searchBinding.customRadiusLayout.isVisible = visible
        searchBinding.btnApplyCustomRadius.isVisible = visible
    }

    private fun updateSearchButtonLabel(radiusKm: Double, lineMode: Boolean) {
        searchBinding.btnSearch.text = if (lineMode) {
            getString(R.string.search_along_line)
        } else {
            getString(R.string.search_with_radius, radiusKm.toInt())
        }
    }

    private fun requestLocationPermissions(
        forceCenter: Boolean = false,
        forTrackingToggle: Boolean = false,
    ) {
        pendingForceCenterOnLocate = forceCenter
        pendingTrackingToggle = forTrackingToggle
        val fine = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (fine || coarse) {
            if (forTrackingToggle) {
                pendingTrackingToggle = false
                toggleMapTrackingFromUi()
            } else {
                viewModel.refreshLocation(forceCenter = forceCenter)
                pendingForceCenterOnLocate = false
            }
            return
        }
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ),
        )
    }

    private fun toggleMapTrackingFromUi() {
        if (!mapReady) return
        val mode = viewModel.state.value.mapTrackingMode
        if (mode == MapTrackingMode.TRACKING) {
            viewModel.toggleMapTracking(0f, 0f, 0.0, 0.0)
            return
        }
        val width = mapController.mapWidthPx().takeIf { it > 0 } ?: return
        val height = mapController.mapHeightPx().takeIf { it > 0 } ?: return
        val pos = viewModel.state.value.userPosition
        val screen = pos?.let { mapController.screenLocationOf(it.latitude, it.longitude) }
        val onScreen = screen != null &&
            screen.x in 0f..width.toFloat() &&
            screen.y in 0f..height.toFloat()
        val focalX = if (onScreen) screen!!.x else width / 2f
        val focalY = if (onScreen) {
            screen!!.y
        } else {
            height * MapController.DEFAULT_FOLLOW_FOCAL_Y_FROM_TOP
        }
        viewModel.toggleMapTracking(
            focalScreenX = focalX,
            focalScreenY = focalY,
            bearing = mapController.currentBearing(),
            zoom = mapController.currentZoom().coerceAtLeast(13.0),
        )
    }

    private fun bindMapTrackingFab(mode: MapTrackingMode) {
        val tracking = mode == MapTrackingMode.TRACKING
        mapBinding.btnMapTrackLocation.setImageResource(
            if (tracking) {
                android.R.drawable.ic_media_pause
            } else {
                android.R.drawable.ic_media_play
            },
        )
        mapBinding.btnMapTrackLocation.contentDescription = getString(
            if (tracking) R.string.map_tracking_paused else R.string.map_tracking_cd,
        )
    }

    private fun applyFollowCameraIfNeeded(state: UiState) {
        if (state.mapTrackingMode != MapTrackingMode.TRACKING) return
        if (state.mapFollowRevision == lastAppliedFollowRevision) return
        val pos = state.userPosition ?: return
        lastAppliedFollowRevision = state.mapFollowRevision
        mapController.followUserAtScreenPoint(
            latitude = pos.latitude,
            longitude = pos.longitude,
            focalScreenX = viewModel.followFocalScreenX,
            focalScreenY = viewModel.followFocalScreenY,
            bearing = viewModel.followBearing,
            zoom = viewModel.followZoom,
        )
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    applyUi(state)
                }
            }
        }
    }

    private fun applyUi(state: UiState, forceMarkers: Boolean = false) {
        currentProfile = state.profile
        binding.progress.isVisible = state.isLocating || state.isSearching ||
            state.isAnalyzingRoads ||
            state.isMapBrowseLoading ||
            state.offlineBdl.status == OfflineBdlStatus.DOWNLOADING
        searchBinding.btnSearch.isEnabled = !state.isSearching && !state.isLocating &&
            !state.isAnalyzingRoads && !state.isMapBrowseLoading
        searchBinding.btnClearMapPin.isVisible = !state.isMapBrowse() &&
            state.searchOriginMode == SearchOriginMode.MAP &&
            state.usesMapPinForSearch()
        searchBinding.statusText.text = buildStatus(state)
        syncingExploreModeUi = true
        searchBinding.exploreModeToggle.check(
            if (state.isMapBrowse()) R.id.btnExploreBrowse else R.id.btnExploreSearch,
        )
        syncingExploreModeUi = false
        if (state.isMapBrowse()) {
            applyMapBrowseSearchChrome(state)
        } else {
            applySearchModeChrome(state)
        }
        bindOfflineUi(state)
        val mapHintRes = when {
            state.isMapBrowse() -> R.string.map_browse_tap_hint
            state.searchOriginMode == SearchOriginMode.LINE -> R.string.map_corridor_hint
            state.searchOriginMode == SearchOriginMode.MAP -> R.string.map_search_pin_hint
            else -> null
        }
        mapBinding.mapSearchHint.isVisible = mapHintRes != null
        if (mapHintRes != null) {
            mapBinding.mapSearchHint.setText(mapHintRes)
        }
        bindListPageUi(state)
        val listItems = state.activeListResults()
        resultsAdapter.submit(listItems)
        val emptyMessage = when {
            state.listViewMode == ListViewMode.SAVED -> getString(R.string.saved_empty)
            state.isMapBrowse() -> getString(R.string.results_empty_browse)
            else -> getString(R.string.results_empty)
        }
        listBinding.emptyResults.text = emptyMessage
        listBinding.emptyResults.isVisible = listItems.isEmpty() &&
            !(state.listViewMode == ListViewMode.SEARCH && state.isSearching)
        listBinding.resultsList.isVisible = listItems.isNotEmpty()

        if (binding.pager.currentItem != state.currentPage) {
            syncingPager = true
            binding.pager.setCurrentItem(state.currentPage, true)
            syncingPager = false
        }
        updatePageIndicator(state.currentPage)

        // Browse keeps selection in state.results; list tab mode must not hide the map card.
        val selected = if (state.isMapBrowse()) {
            state.results.firstOrNull { it.site.id == state.selectedSiteId }
        } else {
            listItems.firstOrNull { it.site.id == state.selectedSiteId }
        }
        bindPoiCard(selected, state)

        if (mapReady) {
            state.userPosition?.let {
                mapController.updateUserLocation(it.latitude, it.longitude, it.approximate)
            }
            if (state.isMapBrowse()) {
                if (state.mapBrowseRevision > 0) {
                    mapController.setBrowseLayer(
                        state.allSites,
                        state.mapBrowseRevision,
                    )
                    mapController.setBrowseZanocujPolygons(state.zanocujPolygons)
                }
                applyBrowseMapFilters(state)
                val browseHash = (state.selectedSiteId?.hashCode() ?: 0) xor
                    state.profile.hashCode() xor
                    state.mapBrowseRevision.hashCode() xor
                    state.browseCarFilter.hashCode() xor
                    state.zanocujPolygons.size xor
                    state.zanocujPolygons.fold(0) { acc, p -> acc * 31 + p.id.hashCode() }
                if (forceMarkers || browseHash != lastRenderedResultsToken) {
                    lastRenderedResultsToken = browseHash
                    mapController.renderResults(listItems, selected, state.profile, state.zanocujPolygons)
                }
            } else {
                mapController.exitBrowseMode()
                mapController.updateSearchPin(
                    if (state.searchOriginMode == SearchOriginMode.LINE) null else state.mapSearchPin,
                )
                mapController.updateCorridorLine(
                    if (state.searchOriginMode == SearchOriginMode.LINE) state.corridorLine else emptyList(),
                )
                val resultsHash = listItems.hashCode() xor (state.selectedSiteId?.hashCode() ?: 0) xor
                    state.profile.hashCode() xor state.zanocujPolygons.size xor
                    state.browseCarFilter.hashCode() xor
                    (state.mapSearchPin?.hashCode() ?: 0) xor state.listViewMode.hashCode() xor
                    state.corridorLine.hashCode() xor state.searchOriginMode.hashCode()
                if (forceMarkers || resultsHash != lastRenderedResultsToken) {
                    lastRenderedResultsToken = resultsHash
                    val polygons = if (state.listViewMode == ListViewMode.SAVED) emptyList() else state.zanocujPolygons
                    mapController.renderResults(
                        listItems,
                        selected,
                        state.profile,
                        polygons,
                    )
                }
            }
            applyCameraRequest(state, selected, listItems)
            applyFollowCameraIfNeeded(state)
        }

        bindMapTrackingFab(state.mapTrackingMode)
        bindMapFilterFab(state)

        state.message?.let { showMessage(it) }
        handleAppUpdateState(state)
    }

    private fun handleAppUpdateState(state: UiState) {
        when {
            state.appUpdateDownloading -> {
                dismissUpdateOfferDialog()
                ensureUpdateProgressDialog()
                val percent = state.appUpdateDownloadPercent
                if (percent == null) {
                    setUpdateProgressPhase(getString(R.string.app_update_downloading), indeterminate = true)
                } else {
                    setUpdateProgressPhase(
                        getString(R.string.app_update_download_percent, percent),
                        indeterminate = false,
                        progress = percent,
                    )
                }
                return
            }
            state.appUpdateInstallFile != null -> {
                dismissUpdateOfferDialog()
                ensureUpdateProgressDialog()
                setUpdateProgressPhase(getString(R.string.app_update_installing), indeterminate = true)
                val apk = state.appUpdateInstallFile
                viewModel.consumeAppUpdateInstall()
                beginApkInstall(apk)
                return
            }
            state.appUpdateError != null -> {
                if (!waitingForSystemInstallerUi) {
                    dismissUpdateProgressDialog()
                }
                Snackbar.make(binding.root, state.appUpdateError, Snackbar.LENGTH_LONG).show()
                viewModel.consumeAppUpdateError()
                return
            }
            state.appUpdateOffer != null -> {
                if (!waitingForSystemInstallerUi && !installSessionStarted) {
                    dismissUpdateProgressDialog()
                }
                val offer = state.appUpdateOffer
                if (shownUpdateVersionCode == offer.versionCode && updateOfferDialog?.isShowing == true) {
                    return
                }
                showUpdateOfferDialog(offer)
            }
            else -> {
                if (!waitingForSystemInstallerUi && !installSessionStarted) {
                    dismissUpdateProgressDialog()
                }
                if (updateOfferDialog?.isShowing != true) {
                    shownUpdateVersionCode = null
                }
            }
        }
    }

    private fun showUpdateOfferDialog(offer: AppUpdateOffer) {
        dismissUpdateOfferDialog()
        shownUpdateVersionCode = offer.versionCode
        val message = buildString {
            if (offer.releaseNotes.isNotBlank()) {
                append(offer.releaseNotes.trim())
                append("\n\n")
            }
            append(getString(R.string.app_update_current, BuildConfig.VERSION_NAME))
        }
        val builder = AlertDialog.Builder(this)
            .setTitle(getString(R.string.app_update_title, offer.versionName))
            .setMessage(message)
            .setCancelable(!offer.mandatory)
            .setPositiveButton(R.string.app_update_action) { _, _ ->
                onUpdateActionClicked()
            }
            .setOnDismissListener {
                updateOfferDialog = null
            }
        if (!offer.mandatory) {
            builder.setNegativeButton(R.string.app_update_later) { _, _ ->
                viewModel.dismissAppUpdate()
                shownUpdateVersionCode = null
            }
        }
        updateOfferDialog = builder.show()
    }

    private fun onUpdateActionClicked() {
        dismissUpdateOfferDialog()
        shownUpdateVersionCode = null
        installSessionStarted = false
        waitingForSystemInstallerUi = false
        ensureUpdateProgressDialog()
        setUpdateProgressPhase(getString(R.string.app_update_downloading), indeterminate = true)
        ensureInstallPermissionThen(PendingAfterInstallPermission.START_DOWNLOAD) {
            viewModel.startAppUpdateDownload()
        }
    }

    private fun ensureInstallPermissionThen(
        pending: PendingAfterInstallPermission,
        onReady: () -> Unit,
    ) {
        if (AppUpdateInstaller.canInstallPackages(this)) {
            onReady()
            return
        }
        pendingAfterInstallPermission = pending
        AlertDialog.Builder(this)
            .setMessage(R.string.app_update_install_sources)
            .setPositiveButton(R.string.app_update_open_settings) { _, _ ->
                unknownSourcesLauncher.launch(AppUpdateInstaller.unknownSourcesSettingsIntent(this))
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                pendingAfterInstallPermission = null
                pendingInstallApk = null
                dismissUpdateProgressDialog()
                Snackbar.make(binding.root, R.string.app_update_permission_denied, Snackbar.LENGTH_LONG).show()
            }
            .setCancelable(false)
            .show()
    }

    private fun resumePendingAfterInstallPermission() {
        val pending = pendingAfterInstallPermission ?: return
        if (!AppUpdateInstaller.canInstallPackages(this)) {
            pendingAfterInstallPermission = null
            pendingInstallApk = null
            dismissUpdateProgressDialog()
            Snackbar.make(binding.root, R.string.app_update_permission_denied, Snackbar.LENGTH_LONG).show()
            return
        }
        pendingAfterInstallPermission = null
        when (pending) {
            PendingAfterInstallPermission.START_DOWNLOAD -> {
                ensureUpdateProgressDialog()
                setUpdateProgressPhase(getString(R.string.app_update_downloading), indeterminate = true)
                if (viewModel.state.value.appUpdateOffer != null) {
                    viewModel.startAppUpdateDownload()
                }
            }
            PendingAfterInstallPermission.INSTALL_APK -> {
                pendingInstallApk?.let { startPackageInstallerSession(it) }
            }
        }
    }

    private fun dismissUpdateOfferDialog() {
        updateOfferDialog?.dismiss()
        updateOfferDialog = null
    }

    private fun ensureUpdateProgressDialog() {
        if (updateProgressDialog?.isShowing == true) return
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }
        val text = TextView(this).apply {
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
        }
        val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            isIndeterminate = true
        }
        container.addView(text)
        container.addView(progress)
        updateProgressText = text
        updateProgressBar = progress
        updateProgressDialog = AlertDialog.Builder(this)
            .setTitle(R.string.app_update_downloading)
            .setView(container)
            .setCancelable(false)
            .create()
        updateProgressDialog?.show()
    }

    private fun setUpdateProgressPhase(message: String, indeterminate: Boolean, progress: Int = 0) {
        ensureUpdateProgressDialog()
        updateProgressText?.text = message
        updateProgressDialog?.setTitle(
            when {
                message.contains("%") || message == getString(R.string.app_update_downloading) ->
                    getString(R.string.app_update_downloading)
                else -> getString(R.string.app_update_installing)
            },
        )
        updateProgressBar?.isIndeterminate = indeterminate
        if (!indeterminate) {
            updateProgressBar?.progress = progress
        }
    }

    private fun dismissUpdateProgressDialog() {
        updateProgressDialog?.dismiss()
        updateProgressDialog = null
        updateProgressBar = null
        updateProgressText = null
    }

    private fun beginApkInstall(apkFile: File) {
        pendingInstallApk = apkFile
        installSessionStarted = false
        ensureInstallPermissionThen(PendingAfterInstallPermission.INSTALL_APK) {
            startPackageInstallerSession(apkFile)
        }
    }

    private fun startPackageInstallerSession(apkFile: File) {
        if (installSessionStarted) return
        installSessionStarted = true
        pendingInstallApk = null
        ensureUpdateProgressDialog()
        setUpdateProgressPhase(getString(R.string.app_update_preparing_installer), indeterminate = true)
        registerInstallReceiver()
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    AppUpdateInstaller.commitSession(this@MainActivity, apkFile)
                }
            } catch (e: Exception) {
                installSessionStarted = false
                waitingForSystemInstallerUi = false
                unregisterInstallReceiver()
                dismissUpdateProgressDialog()
                Snackbar.make(
                    binding.root,
                    e.message ?: "Nie udało się uruchomić instalacji",
                    Snackbar.LENGTH_LONG,
                ).show()
            }
        }
    }

    private fun registerInstallReceiver() {
        if (installReceiverRegistered) return
        val filter = IntentFilter(AppUpdateInstaller.ACTION_INSTALL_STATUS)
        ContextCompat.registerReceiver(
            this,
            installStatusReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        installReceiverRegistered = true
    }

    private fun unregisterInstallReceiver() {
        if (!installReceiverRegistered) return
        runCatching { unregisterReceiver(installStatusReceiver) }
        installReceiverRegistered = false
    }

    private fun applyCameraRequest(
        state: UiState,
        selected: RestSiteResult?,
        listItems: List<RestSiteResult>,
    ) {
        val request = state.mapCameraRequest ?: return
        val token = when (request) {
            is MapCameraRequest.ShowAllResults -> request.token
            is MapCameraRequest.ShowPoi -> request.token
            is MapCameraRequest.CenterOn -> request.token
        }
        if (token == lastAppliedCameraToken) return
        lastAppliedCameraToken = token
        when (request) {
            is MapCameraRequest.ShowAllResults -> {
                if (state.mapTrackingMode == MapTrackingMode.TRACKING) {
                    viewModel.pauseMapTrackingForPoiInteraction()
                }
                val origin = state.searchOrigin()
                mapController.showAllResultsOnMap(listItems, origin)
            }
            is MapCameraRequest.ShowPoi -> {
                if (state.mapTrackingMode == MapTrackingMode.TRACKING) {
                    viewModel.pauseMapTrackingForPoiInteraction()
                }
                val poi = listItems.firstOrNull { it.site.id == request.siteId } ?: selected
                if (poi != null) {
                    mapController.showPoiOnMap(poi)
                }
            }
            is MapCameraRequest.CenterOn -> {
                mapController.centerOn(request.latitude, request.longitude, request.zoom)
            }
        }
        viewModel.consumeMapCameraRequest()
    }

    private fun bindListPageUi(state: UiState) {
        syncingListUi = true
        when (state.listViewMode) {
            ListViewMode.SEARCH -> listBinding.listModeGroup.check(R.id.btnListSearch)
            ListViewMode.SAVED -> listBinding.listModeGroup.check(R.id.btnListSaved)
        }
        listBinding.btnListSearch.setText(
            if (state.isMapBrowse()) R.string.list_mode_browse_selection else R.string.list_mode_search,
        )
        listBinding.savedFiltersRow.isVisible = state.listViewMode == ListViewMode.SAVED
        if (state.listViewMode == ListViewMode.SAVED) {
            bindSavedCategoryFilter(state)
        }
        syncingListUi = false
    }

    private fun bindSavedCategoryFilter(state: UiState) {
        val labels = buildList {
            add(getString(R.string.saved_filter_all))
            state.savedCategories.forEach { add(it.name) }
        }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, labels).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        listBinding.savedCategoryFilter.adapter = adapter
        val selectedIndex = savedFilterPosition(state.savedCategories, state.savedCategoryFilterId)
        listBinding.savedCategoryFilter.setSelection(selectedIndex)
    }

    private fun savedFilterPosition(
        categories: List<SavedPointCategory>,
        filterId: String?,
    ): Int {
        if (filterId == null) return 0
        val index = categories.indexOfFirst { it.id == filterId }
        return if (index >= 0) index + 1 else 0
    }

    private fun savedFilterIdAtPosition(
        categories: List<SavedPointCategory>,
        position: Int,
    ): String? = if (position <= 0) null else categories.getOrNull(position - 1)?.id

    private fun bindPoiCard(selected: RestSiteResult?, state: UiState) {
        if (selected == null) {
            mapBinding.poiCard.isVisible = false
            return
        }
        mapBinding.poiCard.isVisible = true
        mapBinding.cardTitle.text = "Miejsce odpoczynku „${selected.site.name}”"
        mapBinding.cardDistance.text = formatPoiDistance(state, selected.distanceKm)
        mapBinding.cardFeatures.text = selected.site.featureSummaryPl()
        mapBinding.cardZanocuj.text = when (selected.site.zanocujStatus) {
            ZanocujStatus.IN_ZONE -> getString(R.string.zanocuj_in_zone_emoji)
            ZanocujStatus.NEAR_ZONE -> getString(R.string.zanocuj_near_zone_emoji)
            ZanocujStatus.OUTSIDE_ZONE -> zanocujLabel(
                selected.site.zanocujStatus,
                selected.site.distanceToZanocujBoundaryMeters,
            )
        }
        mapBinding.cardZanocuj.isVisible = selected.site.zanocujStatus != ZanocujStatus.OUTSIDE_ZONE
        mapBinding.cardNavigate.isVisible = true
        val saved = state.savedPoint(selected.site.id)
        val isSaved = saved != null
        mapBinding.btnSaveCard.setImageResource(
            if (isSaved) android.R.drawable.btn_star_big_on else android.R.drawable.btn_star_big_off,
        )
        mapBinding.cardEditSaved.isVisible = isSaved
        if (saved != null) {
            val categoryLabel = categoriesLabelFor(saved.categoryIds, state.savedCategories)
            val parts = buildList {
                add(
                    if (saved.categoryIds.size > 1) {
                        getString(R.string.saved_item_categories, categoryLabel)
                    } else {
                        getString(R.string.saved_item_category, categoryLabel)
                    },
                )
                saved.userComment?.let { add(getString(R.string.saved_item_comment, it)) }
            }
            mapBinding.cardSavedMeta.text = parts.joinToString(" · ")
            mapBinding.cardSavedMeta.isVisible = true
        } else {
            mapBinding.cardSavedMeta.isVisible = false
        }
    }

    private fun categoriesLabelFor(
        categoryIds: Set<String>,
        categories: List<SavedPointCategory>,
    ): String {
        if (categoryIds.isEmpty()) return getString(R.string.saved_category_none)
        val names = categoryIds.mapNotNull { id ->
            categories.firstOrNull { it.id == id }?.name
        }.sorted()
        return names.ifEmpty { listOf(getString(R.string.saved_category_none)) }.joinToString(", ")
    }

    private fun savedMetaFor(siteId: String): Pair<String?, String?>? {
        val saved = viewModel.state.value.savedPoint(siteId) ?: return null
        val category = categoriesLabelFor(saved.categoryIds, viewModel.state.value.savedCategories)
        return category to saved.userComment
    }

    private fun onSaveClicked(item: RestSiteResult) {
        val state = viewModel.state.value
        if (state.isSaved(item.site.id)) {
            showEditSavedDialog(item.site.id, allowRemove = true)
        } else {
            viewModel.toggleSave(item.site)
            showEditSavedDialog(item.site.id, allowRemove = false)
        }
    }

    private fun showEditSavedDialog(siteId: String, allowRemove: Boolean = true) {
        val state = viewModel.state.value
        val saved = state.savedPoint(siteId) ?: return
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_saved, null)
        val commentInput = dialogView.findViewById<EditText>(R.id.savedCommentInput)
        val checkboxContainer = dialogView.findViewById<LinearLayout>(R.id.savedCategoryCheckboxContainer)
        commentInput.setText(saved.userComment.orEmpty())
        val categoryCheckboxes = linkedMapOf<String, CheckBox>()
        if (state.savedCategories.isEmpty()) {
            checkboxContainer.addView(
                TextView(this).apply {
                    text = getString(R.string.saved_no_categories_defined)
                    setTextAppearance(android.R.style.TextAppearance_Material_Body2)
                },
            )
        } else {
            state.savedCategories.forEach { category ->
                val checkBox = CheckBox(this).apply {
                    text = category.name
                    isChecked = category.id in saved.categoryIds
                }
                categoryCheckboxes[category.id] = checkBox
                checkboxContainer.addView(checkBox)
            }
        }
        val builder = AlertDialog.Builder(this)
            .setTitle(R.string.saved_dialog_title)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val selectedIds = categoryCheckboxes.filterValues { it.isChecked }.keys.toSet()
                viewModel.updateSavedPoint(
                    siteId = siteId,
                    categoryIds = selectedIds,
                    userComment = commentInput.text?.toString(),
                )
            }
            .setNegativeButton(android.R.string.cancel, null)
        if (allowRemove) {
            builder.setNeutralButton(R.string.btn_unsave) { _, _ ->
                viewModel.toggleSave(saved.site)
            }
        }
        builder.show()
    }

    private fun showManageCategoriesDialog() {
        val state = viewModel.state.value
        if (state.savedCategories.isEmpty()) {
            promptAddCategory()
            return
        }
        val names = state.savedCategories.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.saved_categories_title)
            .setItems(names) { _, which ->
                val category = state.savedCategories[which]
                showCategoryActionsDialog(category)
            }
            .setPositiveButton(R.string.saved_add_category) { _, _ -> promptAddCategory() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showCategoryActionsDialog(category: SavedPointCategory) {
        AlertDialog.Builder(this)
            .setTitle(category.name)
            .setItems(
                arrayOf(
                    getString(R.string.saved_rename_category),
                    getString(R.string.saved_delete_category),
                ),
            ) { _, which ->
                when (which) {
                    0 -> promptRenameCategory(category)
                    1 -> confirmDeleteCategory(category)
                }
            }
            .show()
    }

    private fun promptAddCategory() {
        val input = EditText(this).apply {
            hint = getString(R.string.saved_category_name_hint)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.saved_add_category)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = input.text?.toString().orEmpty()
                if (name.trim().length >= 2) viewModel.addSavedCategory(name)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun promptRenameCategory(category: SavedPointCategory) {
        val input = EditText(this).apply {
            setText(category.name)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.saved_rename_category)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                viewModel.renameSavedCategory(category.id, input.text?.toString().orEmpty())
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmDeleteCategory(category: SavedPointCategory) {
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.saved_delete_category_confirm, category.name))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                viewModel.deleteSavedCategory(category.id)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun selectedResult(): RestSiteResult? {
        val state = viewModel.state.value
        return if (state.isMapBrowse()) {
            state.results.firstOrNull { it.site.id == state.selectedSiteId }
        } else {
            state.activeListResults().firstOrNull { it.site.id == state.selectedSiteId }
        }
    }

    private fun updatePageIndicator(page: Int) {
        binding.dot0.setBackgroundResource(
            if (page == AppPages.SEARCH) R.drawable.page_dot_active else R.drawable.page_dot_inactive,
        )
        binding.dot1.setBackgroundResource(
            if (page == AppPages.MAP) R.drawable.page_dot_active else R.drawable.page_dot_inactive,
        )
        binding.dot2.setBackgroundResource(
            if (page == AppPages.LIST) R.drawable.page_dot_active else R.drawable.page_dot_inactive,
        )
        val labels = listOf(
            getString(R.string.page_search),
            getString(R.string.page_map),
            getString(R.string.page_list),
        )
        binding.pageLabels.text = labels.mapIndexed { index, label ->
            if (index == page) "【$label】" else label
        }.joinToString("  ·  ")
        binding.btnPagePrev.isEnabled = page > AppPages.SEARCH
        binding.btnPageNext.isEnabled = page < AppPages.LIST
        binding.btnPagePrev.alpha = if (binding.btnPagePrev.isEnabled) 1f else 0.35f
        binding.btnPageNext.alpha = if (binding.btnPageNext.isEnabled) 1f else 0.35f
    }

    private fun buildStatus(state: UiState): String {
        val profile = when (state.profile) {
            TravelProfile.CAR -> getString(R.string.profile_car)
            TravelProfile.MOTORCYCLE -> getString(R.string.profile_moto)
        }
        val filterLabel = state.browseCarFilter.summaryPl()
        val offline = if (state.offlineBdl.isReady) " · offline" else ""
        if (state.isMapBrowse()) {
            return getString(
                R.string.status_browse,
                profile,
                filterLabel,
                "",
                state.allSites.size,
                offline,
            )
        }
        val radius = "${state.searchConfig.searchRadiusKm.toInt()} km"
        val origin = when (state.searchOriginMode) {
            SearchOriginMode.GPS -> "od: GPS"
            SearchOriginMode.MAP -> "od: mapa"
            SearchOriginMode.LOCALITY -> {
                val name = state.localityQuery.trim().ifBlank { "?" }
                "od: $name"
            }
            SearchOriginMode.LINE -> {
                "linia: ${state.corridorLine.size} pkt L${state.corridorLeftKm.toInt()}/P${state.corridorRightKm.toInt()}"
            }
        }
        return if (state.searchOriginMode == SearchOriginMode.LINE) {
            "$profile · $origin · $filterLabel · wyników: ${state.results.size}$offline"
        } else {
            "$profile · $radius · $filterLabel · wyników: ${state.results.size} · $origin$offline"
        }
    }

    private fun formatPoiDistance(state: UiState, distanceKm: Double): String {
        val hasOrigin = if (state.isMapBrowse()) {
            state.userPosition != null
        } else {
            state.searchOrigin() != null
        }
        return if (!hasOrigin) {
            getString(R.string.poi_distance_no_gps)
        } else {
            String.format(Locale.forLanguageTag("pl-PL"), "%.1f km", distanceKm)
        }
    }

    private fun bindSearchOriginUi(state: UiState) {
        syncingSearchOriginUi = true
        when (state.searchOriginMode) {
            SearchOriginMode.GPS -> searchBinding.searchOriginGroup.check(R.id.radioSearchGps)
            SearchOriginMode.MAP -> searchBinding.searchOriginGroup.check(R.id.radioSearchMap)
            SearchOriginMode.LOCALITY -> searchBinding.searchOriginGroup.check(R.id.radioSearchLocality)
            SearchOriginMode.LINE -> searchBinding.searchOriginGroup.check(R.id.radioSearchLine)
        }

        val localityVisible = state.searchOriginMode == SearchOriginMode.LOCALITY
        val lineVisible = state.searchOriginMode == SearchOriginMode.LINE
        searchBinding.corridorPanel.isVisible = lineVisible
        // Radius: hide entirely in LINE mode; custom fields only when "Własny…" selected.
        searchBinding.radiusSpinner.isVisible = !lineVisible
        searchBinding.radiusLabel.isVisible = !lineVisible
        if (lineVisible) {
            setCustomRadiusUiVisible(false)
        } else {
            val presets = SearchConfig.SEARCH_RADIUS_PRESETS_KM
            val isCustom = state.searchConfig.searchRadiusKm !in presets
            if (isCustom && searchBinding.radiusSpinner.selectedItemPosition != presets.size) {
                searchBinding.radiusSpinner.setSelection(presets.size, false)
            }
            setCustomRadiusUiVisible(
                searchBinding.radiusSpinner.selectedItemPosition >= presets.size,
            )
        }
        // Locality input also for LINE shortcuts that need a name.
        val localityForLine = lineVisible &&
            state.localityPickPurpose != pl.navilas.finder.domain.LocalityPickPurpose.SEARCH_ORIGIN
        searchBinding.localityInputLayout.isVisible = localityVisible || localityForLine
        if ((localityVisible || localityForLine) &&
            searchBinding.localityInput.text?.toString() != state.localityQuery
        ) {
            searchBinding.localityInput.setText(state.localityQuery)
            searchBinding.localityInput.setSelection(state.localityQuery.length)
        }
        bindLocalityCandidates(state.localityCandidates)
        if (lineVisible) {
            searchBinding.corridorStatus.text = getString(
                R.string.corridor_status,
                state.corridorLine.size,
                formatKm(state.corridorLeftKm),
                formatKm(state.corridorRightKm),
            )
            syncingCorridorUi = true
            val leftText = formatKm(state.corridorLeftKm)
            val rightText = formatKm(state.corridorRightKm)
            if (searchBinding.corridorLeftInput.text?.toString() != leftText) {
                searchBinding.corridorLeftInput.setText(leftText)
            }
            if (searchBinding.corridorRightInput.text?.toString() != rightText) {
                searchBinding.corridorRightInput.setText(rightText)
            }
            syncingCorridorUi = false
        }
        syncingSearchOriginUi = false
    }

    private fun bindLocalityCandidates(candidates: List<pl.navilas.finder.data.osm.GeocodedPlace>?) {
        val list = searchBinding.localityCandidatesList
        val show = !candidates.isNullOrEmpty()
        searchBinding.localityCandidatesPanel.isVisible = show
        if (!show) {
            list.removeAllViews()
            return
        }
        // Rebuild only when content identity changes (size + first/last labels).
        val signature = candidates!!.joinToString("|") { "${it.latitude},${it.longitude}" }
        if (list.tag == signature && list.childCount == candidates.size) return
        list.tag = signature
        list.removeAllViews()
        val padH = (12 * resources.displayMetrics.density).toInt()
        val padV = (10 * resources.displayMetrics.density).toInt()
        candidates.forEach { place ->
            val row = com.google.android.material.button.MaterialButton(
                this,
                null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle,
            ).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                ).also { lp ->
                    lp.topMargin = (4 * resources.displayMetrics.density).toInt()
                }
                text = place.shortLabel()
                isAllCaps = false
                textAlignment = android.view.View.TEXT_ALIGNMENT_VIEW_START
                setPadding(padH, padV, padH, padV)
                setOnClickListener { viewModel.applyLocalityChoice(place) }
            }
            list.addView(row)
        }
    }

    private fun formatKm(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

    private fun showCorridorVertexMenu(index: Int) {
        val options = arrayOf(
            getString(R.string.corridor_vertex_move),
            getString(R.string.corridor_vertex_insert),
            getString(R.string.corridor_vertex_delete),
        )
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.corridor_vertex_title, index + 1))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> viewModel.beginMoveCorridorPoint(index)
                    1 -> viewModel.beginInsertCorridorPoint(index)
                    2 -> viewModel.deleteCorridorPoint(index)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun updateOfflinePanelVisibility() {
        searchBinding.offlinePanel.isVisible = offlineSectionExpanded
        searchBinding.btnToggleOffline.text = getString(
            if (offlineSectionExpanded) R.string.offline_toggle_collapse else R.string.offline_toggle_expand,
        )
    }

    private fun bindOfflineUi(state: UiState) {
        if (state.offlineBdl.status == OfflineBdlStatus.DOWNLOADING) {
            offlineSectionExpanded = true
        }
        updateOfflinePanelVisibility()
        val offline = state.offlineBdl
        syncingOfflineUi = true
        when (offline.pendingConfig.scope) {
            BdlDataScope.NAVILAS_CORE ->
                searchBinding.offlineScopeGroup.check(R.id.radioOfflineNavilas)
            BdlDataScope.FULL_BDL ->
                searchBinding.offlineScopeGroup.check(R.id.radioOfflineFull)
        }
        when (offline.pendingConfig.zanocujQuality) {
            ZanocujPolygonQuality.PRECISE ->
                searchBinding.offlineZanocujGroup.check(R.id.radioZanocujPrecise)
            ZanocujPolygonQuality.SIMPLIFIED ->
                searchBinding.offlineZanocujGroup.check(R.id.radioZanocujSimplified)
        }
        syncingOfflineUi = false

        searchBinding.offlineSizeHint.text = getString(
            R.string.offline_size_estimate,
            offline.pendingConfig.estimatedSizeLabel(),
        )
        searchBinding.offlineStatusText.text = when (offline.status) {
            OfflineBdlStatus.NOT_DOWNLOADED -> getString(R.string.offline_status_none)
            OfflineBdlStatus.DOWNLOADING -> getString(
                R.string.offline_status_downloading,
                offline.progressLabel ?: "${(offline.progress * 100).toInt()}%",
            )
            OfflineBdlStatus.READY -> {
                val scopeLabel = when (offline.storedConfig?.scope) {
                    BdlDataScope.FULL_BDL -> "pełna baza"
                    BdlDataScope.NAVILAS_CORE, null -> "NaviLas"
                }
                val qualityLabel = when (offline.storedConfig?.zanocujQuality) {
                    ZanocujPolygonQuality.PRECISE -> "strefy dokładne"
                    ZanocujPolygonQuality.SIMPLIFIED, null -> "strefy uproszczone"
                }
                val updatedAt = offline.downloadedAt?.let { formatOfflineUpdatedAt(it) }
                    ?: getString(R.string.offline_updated_unknown)
                getString(
                    R.string.offline_status_ready,
                    scopeLabel,
                    qualityLabel,
                    formatStorageBytes(offline.storageBytes),
                    updatedAt,
                )
            }
            OfflineBdlStatus.ERROR -> getString(
                R.string.offline_status_error,
                offline.errorMessage ?: "błąd",
            )
        }

        val pendingMismatch = offline.isReady &&
            offline.storedConfig != null &&
            !offline.pendingConfig.matches(offline.storedConfig)
        searchBinding.offlineHint.setText(
            if (pendingMismatch) R.string.offline_pending_mismatch else R.string.offline_hint,
        )
        searchBinding.btnDownloadOffline.setText(
            if (pendingMismatch) R.string.offline_download_again else R.string.offline_download,
        )

        val downloading = offline.status == OfflineBdlStatus.DOWNLOADING
        searchBinding.offlineProgress.isVisible = downloading
        if (downloading) {
            searchBinding.offlineProgress.progress = (offline.progress * 100).toInt()
        }
        searchBinding.btnDownloadOffline.isEnabled = !downloading && !state.isSearching
        searchBinding.btnDeleteOffline.isVisible = offline.isReady
        searchBinding.btnDeleteOffline.isEnabled = !downloading
        searchBinding.offlineScopeGroup.isEnabled = !downloading
        searchBinding.offlineZanocujGroup.isEnabled = !downloading
    }

    private fun formatOfflineUpdatedAt(epochMs: Long): String {
        val formatter = java.text.DateFormat.getDateTimeInstance(
            java.text.DateFormat.MEDIUM,
            java.text.DateFormat.SHORT,
            Locale.forLanguageTag("pl-PL"),
        )
        return formatter.format(java.util.Date(epochMs))
    }

    private fun formatStorageBytes(bytes: Long): String = when {
        bytes >= 1024 * 1024 -> String.format(
            Locale.forLanguageTag("pl-PL"),
            "%.1f MB",
            bytes / (1024.0 * 1024.0),
        )
        bytes >= 1024 -> String.format(
            Locale.forLanguageTag("pl-PL"),
            "%.0f KB",
            bytes / 1024.0,
        )
        else -> "$bytes B"
    }

    private fun showDetails(item: RestSiteResult) {
        viewModel.selectSite(item.site.id)
        val site = item.site
        val state = viewModel.state.value
        val saved = state.savedPoint(site.id)
        val features = buildList {
            site.features.forEach { add("• ${it.labelPl}") }
            site.naturalSpring?.let { add("• ${it.labelPl()}") }
        }.joinToString("\n").ifBlank { "• (brak flag BDL)" }
        val related = site.relatedObjects.joinToString("\n") {
            "• ${it.name} (${it.layerName}, ${it.distanceMeters.toInt()} m)"
        }.ifBlank { "• brak w ${stateConfigLinkRadius()} m" }
        val zone = zanocujLabel(site.zanocujStatus, site.distanceToZanocujBoundaryMeters)
        val savedBlock = saved?.let {
            buildString {
                append("\n\nTwoje notatki:\n")
                append("Kategorie: ")
                append(categoriesLabelFor(it.categoryIds, state.savedCategories))
                append('\n')
                append("Komentarz: ")
                append(it.userComment ?: "—")
            }
        }.orEmpty()
        val builder = AlertDialog.Builder(this)
            .setTitle(site.name)
            .setMessage(
                """
                Cechy BDL:
                $features
                
                Powiązane obiekty BDL:
                $related
                
                Zanocuj: $zone
                
                Źródło: ${site.sourceLayerName}$savedBlock
                """.trimIndent(),
            )
            .setNeutralButton(R.string.btn_navigate) { _, _ -> showNavigateChooser(item) }
            .setPositiveButton(android.R.string.ok, null)
        if (saved != null) {
            builder.setNegativeButton(R.string.btn_edit_saved) { _, _ -> showEditSavedDialog(site.id) }
        } else {
            builder.setNegativeButton(R.string.btn_save) { _, _ -> onSaveClicked(item) }
        }
        builder.show()
    }

    private fun stateConfigLinkRadius(): Int =
        viewModel.state.value.searchConfig.restLinkRadiusMeters.toInt()

    private fun showNavigateChooser(item: RestSiteResult) {
        viewModel.selectSite(item.site.id)
        showNavAppChooser(item)
    }

    private fun showNavAppChooser(item: RestSiteResult) {
        val target = item.navigationTarget
        val options = arrayOf(
            getString(R.string.nav_google),
            getString(R.string.nav_osmand),
            getString(R.string.nav_gpx),
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.btn_navigate)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openUri(NavigationLinks.googleMapsDirUrl(target))
                    1 -> openOsmAnd(item)
                    2 -> shareGpx(item)
                }
            }
            .show()
    }

    private fun openOsmAnd(item: RestSiteResult) {
        val geo = NavigationLinks.osmAndGeoUri(item.navigationTarget, item.site.name)
        val mapUrl = NavigationLinks.osmAndMapUrl(item.navigationTarget, currentProfile)
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(geo)))
        } catch (_: Exception) {
            openUri(mapUrl)
        }
    }

    private fun shareGpx(item: RestSiteResult) {
        val features = item.site.features.joinToString(", ") { it.labelPl }
        val gpx = NavigationLinks.gpxWaypoint(
            name = item.site.name,
            destination = item.navigationTarget,
            description = features.ifBlank { item.site.description },
        )
        val dir = File(cacheDir, "gpx").apply { mkdirs() }
        val file = File(dir, "navilas-${System.currentTimeMillis()}.gpx")
        file.writeText(gpx)
        val uri = FileProvider.getUriForFile(
            this,
            getString(R.string.file_provider_authority),
            file,
        )
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/gpx+xml"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, item.site.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send, getString(R.string.nav_gpx)))
    }

    private fun openUri(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    private fun showMessage(message: AppMessage) {
        val text = when (message) {
            is AppMessage.Info -> message.text
            is AppMessage.Error -> message.text
        }
        Snackbar.make(binding.root, text, Snackbar.LENGTH_LONG).show()
        viewModel.consumeMessage()
    }

    override fun onStart() {
        super.onStart()
        if (::mapView.isInitialized) mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        if (::mapView.isInitialized) mapView.onResume()
        resumePendingAfterInstallPermission()
    }

    override fun onPause() {
        if (::mapView.isInitialized) mapView.onPause()
        if (waitingForSystemInstallerUi) {
            // System installer is on top — drop our overlay to avoid flicker underneath.
            dismissUpdateProgressDialog()
        }
        super.onPause()
    }

    override fun onStop() {
        if (::mapView.isInitialized) mapView.onStop()
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::mapView.isInitialized) mapView.onSaveInstanceState(outState)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        if (::mapView.isInitialized) mapView.onLowMemory()
    }

    override fun onDestroy() {
        unregisterInstallReceiver()
        if (::mapView.isInitialized) mapView.onDestroy()
        super.onDestroy()
    }
}

private class MainPagerAdapter(
    private val searchPage: View,
    private val mapPage: View,
    private val listPage: View,
) : RecyclerView.Adapter<MainPagerAdapter.Holder>() {
    init {
        setHasStableIds(true)
    }

    override fun getItemCount(): Int = AppPages.COUNT

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getItemViewType(position: Int): Int = position

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val page = when (viewType) {
            AppPages.SEARCH -> searchPage
            AppPages.MAP -> mapPage
            else -> listPage
        }
        (page.parent as? ViewGroup)?.removeView(page)
        page.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        return Holder(page)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) = Unit

    class Holder(itemView: View) : RecyclerView.ViewHolder(itemView)
}

internal fun zanocujLabel(status: ZanocujStatus, distanceMeters: Double?): String = when (status) {
    ZanocujStatus.IN_ZONE -> "W strefie Zanocuj w lesie"
    ZanocujStatus.NEAR_ZONE -> {
        val d = distanceMeters?.toInt()
        if (d != null) "Blisko strefy Zanocuj w lesie — ${d} m" else "Blisko strefy Zanocuj w lesie"
    }
    ZanocujStatus.OUTSIDE_ZONE -> "Poza strefą"
}

private class ResultsAdapter(
    private val onDetails: (RestSiteResult) -> Unit,
    private val onNavigate: (RestSiteResult) -> Unit,
    private val onSelect: (RestSiteResult) -> Unit,
    private val onSave: (RestSiteResult) -> Unit,
    private val profileProvider: () -> TravelProfile,
    private val isSavedProvider: (String) -> Boolean,
    private val savedMetaProvider: (String) -> Pair<String?, String?>?,
    private val distanceLabelProvider: (RestSiteResult) -> String,
) : RecyclerView.Adapter<ResultsAdapter.Holder>() {
    private var items: List<RestSiteResult> = emptyList()

    fun submit(newItems: List<RestSiteResult>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_rest_site, parent, false)
        return Holder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(items[position])
    }

    inner class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.itemTitle)
        private val features: TextView = itemView.findViewById(R.id.itemFeatures)
        private val zanocuj: TextView = itemView.findViewById(R.id.itemZanocuj)
        private val road: TextView = itemView.findViewById(R.id.itemRoad)
        private val comment: TextView = itemView.findViewById(R.id.itemComment)
        private val savedCategory: TextView = itemView.findViewById(R.id.itemSavedCategory)
        private val distance: TextView = itemView.findViewById(R.id.itemDistance)
        private val btnSave: com.google.android.material.button.MaterialButton =
            itemView.findViewById(R.id.btnSave)
        private val btnDetails: View = itemView.findViewById(R.id.btnDetails)
        private val btnNavigate: View = itemView.findViewById(R.id.btnNavigate)

        fun bind(item: RestSiteResult) {
            title.text = "Miejsce odpoczynku „${item.site.name}”"
            features.text = item.site.featureSummaryPl()
            zanocuj.text = zanocujLabel(item.site.zanocujStatus, item.site.distanceToZanocujBoundaryMeters)
            zanocuj.isVisible = item.site.zanocujStatus != ZanocujStatus.OUTSIDE_ZONE
            distance.text = distanceLabelProvider(item)

            val savedMeta = savedMetaProvider(item.site.id)
            if (savedMeta != null) {
                val labelRes = if (savedMeta.first?.contains(",") == true) {
                    R.string.saved_item_categories
                } else {
                    R.string.saved_item_category
                }
                savedCategory.text = itemView.context.getString(
                    labelRes,
                    savedMeta.first ?: itemView.context.getString(R.string.saved_category_none),
                )
                savedCategory.isVisible = true
                val userComment = savedMeta.second
                if (!userComment.isNullOrBlank()) {
                    comment.text = itemView.context.getString(R.string.saved_item_comment, userComment)
                    comment.isVisible = true
                } else {
                    comment.isVisible = false
                }
            } else {
                savedCategory.isVisible = false
                comment.isVisible = false
            }

            val isSaved = isSavedProvider(item.site.id)
            btnSave.text = itemView.context.getString(
                if (isSaved) R.string.btn_edit_saved else R.string.btn_save,
            )
            btnSave.setOnClickListener { onSave(item) }

            val assessment = item.roadAssessment
            if (profileProvider() == TravelProfile.MOTORCYCLE && assessment != null) {
                road.isVisible = true
                val dist = assessment.distanceToRoadMeters
                val type = RoadClassifier.polishRoadType(assessment.nearestRoad?.type)
                val stars = assessment.roadSuitability?.toStars() ?: "—"
                val distLabel = if (dist != null) {
                    String.format(Locale.forLanguageTag("pl-PL"), "%.0f m od drogi", dist)
                } else {
                    "brak drogi"
                }
                val targetHint = when (item.navigationTargetKind) {
                    NavigationTargetKind.OSM_ROAD -> "Cel nawigacji: droga przy miejscu"
                    else -> "Brak celu drogowego OSM"
                }
                road.text = "$targetHint · $distLabel · $type · $stars"
            } else {
                road.isVisible = false
            }

            btnNavigate.isVisible = true
            btnDetails.setOnClickListener {
                onDetails(item)
            }
            btnNavigate.setOnClickListener {
                onNavigate(item)
            }
            itemView.setOnClickListener { onSelect(item) }
        }
    }
}
