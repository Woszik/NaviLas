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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
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
import pl.navilas.finder.databinding.PageListBinding
import pl.navilas.finder.databinding.PageMapBinding
import pl.navilas.finder.databinding.PageSearchBinding
import pl.navilas.finder.domain.AppMessage
import pl.navilas.finder.domain.NavigationTargetKind
import pl.navilas.finder.domain.RestSiteResult
import pl.navilas.finder.domain.SearchConfig
import pl.navilas.finder.domain.SearchOriginMode
import pl.navilas.finder.domain.TravelProfile
import pl.navilas.finder.domain.ZanocujFilterMode
import pl.navilas.finder.domain.ZanocujStatus
import pl.navilas.finder.domain.BdlDataScope
import pl.navilas.finder.domain.ListViewMode
import pl.navilas.finder.domain.OfflineBdlStatus
import pl.navilas.finder.domain.SavedPointCategory
import pl.navilas.finder.domain.ZanocujPolygonQuality
import pl.navilas.finder.domain.estimatedSizeLabel
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
    private var syncingListUi = false
    private var offlineSectionExpanded = false
    private var lastRenderedResultsToken: Int = -1
    private var lastAppliedCameraToken: Long = -1L
    private var updateOfferDialog: AlertDialog? = null
    private var updateProgressDialog: AlertDialog? = null
    private var updateProgressBar: ProgressBar? = null
    private var updateProgressText: TextView? = null
    private var shownUpdateVersionCode: Int? = null
    private var pendingInstallApk: File? = null
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
        when {
            fine || coarse -> viewModel.refreshLocation()
            else -> Snackbar.make(
                binding.root,
                "Odmówiono lokalizacji. Mapa działa, ale wyszukiwanie wymaga pozycji.",
                Snackbar.LENGTH_LONG,
            ).show()
        }
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
                    viewModel.setMapSearchPin(lat, lon)
                }
                applyUi(viewModel.state.value, forceMarkers = true)
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
        searchBinding.zanocujFilter.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                R.id.radioZanocujOnly -> ZanocujFilterMode.ONLY_IN_ZONE
                else -> ZanocujFilterMode.ALL
            }
            viewModel.setZanocujFilter(mode)
        }
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

    private fun setupSearchOriginSection() {
        searchBinding.searchOriginGroup.setOnCheckedChangeListener { _, checkedId ->
            if (syncingSearchOriginUi) return@setOnCheckedChangeListener
            val mode = when (checkedId) {
                R.id.radioSearchMap -> SearchOriginMode.MAP
                R.id.radioSearchLocality -> SearchOriginMode.LOCALITY
                else -> SearchOriginMode.GPS
            }
            viewModel.setSearchOriginMode(mode)
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
                    viewModel.setLocalityQuery(s?.toString().orEmpty())
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
        val labels = presets.map { "${it.toInt()} km" }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
        searchBinding.radiusSpinner.adapter = adapter
        val defaultIndex = presets.indexOf(SearchConfig.DEFAULT_SEARCH_RADIUS_KM).coerceAtLeast(0)
        searchBinding.radiusSpinner.setSelection(defaultIndex, false)
        updateSearchButtonLabel(presets[defaultIndex])
        searchBinding.radiusSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val km = presets[position]
                viewModel.setSearchRadiusKm(km)
                updateSearchButtonLabel(km)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun updateSearchButtonLabel(radiusKm: Double) {
        searchBinding.btnSearch.text = getString(R.string.search_with_radius, radiusKm.toInt())
    }

    private fun requestLocationPermissions() {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ),
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
            state.offlineBdl.status == OfflineBdlStatus.DOWNLOADING
        searchBinding.btnSearch.isEnabled = !state.isSearching && !state.isLocating &&
            !state.isAnalyzingRoads
        searchBinding.btnClearMapPin.isVisible = state.searchOriginMode == SearchOriginMode.MAP &&
            state.usesMapPinForSearch()
        searchBinding.statusText.text = buildStatus(state)
        bindSearchOriginUi(state)
        bindOfflineUi(state)
        mapBinding.mapSearchHint.isVisible = true
        bindListPageUi(state)
        val listItems = state.activeListResults()
        resultsAdapter.submit(listItems)
        val emptyMessage = when (state.listViewMode) {
            ListViewMode.SEARCH -> getString(R.string.results_empty)
            ListViewMode.SAVED -> getString(R.string.saved_empty)
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

        val selected = listItems.firstOrNull { it.site.id == state.selectedSiteId }
        bindPoiCard(selected, state)

        if (mapReady) {
            state.userPosition?.let {
                mapController.updateUserLocation(it.latitude, it.longitude, it.approximate)
            }
            mapController.updateSearchPin(state.mapSearchPin)
            val resultsHash = listItems.hashCode() xor (state.selectedSiteId?.hashCode() ?: 0) xor
                state.profile.hashCode() xor state.zanocujPolygons.size xor
                (state.mapSearchPin?.hashCode() ?: 0) xor state.listViewMode.hashCode()
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
            applyCameraRequest(state, selected, listItems)
        }

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
        }
        if (token == lastAppliedCameraToken) return
        lastAppliedCameraToken = token
        when (request) {
            is MapCameraRequest.ShowAllResults -> {
                val origin = state.searchOrigin()
                mapController.showAllResultsOnMap(listItems, origin)
            }
            is MapCameraRequest.ShowPoi -> {
                val poi = listItems.firstOrNull { it.site.id == request.siteId } ?: selected
                if (poi != null) {
                    mapController.showPoiOnMap(poi)
                }
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
        mapBinding.cardDistance.text =
            String.format(Locale.forLanguageTag("pl-PL"), "%.1f km", selected.distanceKm)
        mapBinding.cardFeatures.text = selected.site.features.joinToString(" · ") { it.labelPl }
            .ifBlank { "Cechy BDL: brak flag" }
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
        return state.activeListResults().firstOrNull { it.site.id == state.selectedSiteId }
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
            TravelProfile.CAR -> "SAMOCHÓD"
            TravelProfile.MOTORCYCLE -> "MOTOCYKL"
        }
        val filter = when (state.zanocujFilter) {
            ZanocujFilterMode.ALL -> "wszystkie"
            ZanocujFilterMode.ONLY_IN_ZONE -> "tylko Zanocuj"
        }
        val radius = "${state.searchConfig.searchRadiusKm.toInt()} km"
        val origin = when (state.searchOriginMode) {
            SearchOriginMode.GPS -> "od: GPS"
            SearchOriginMode.MAP -> "od: mapa"
            SearchOriginMode.LOCALITY -> {
                val name = state.localityQuery.trim().ifBlank { "?" }
                "od: $name"
            }
        }
        val offline = if (state.offlineBdl.isReady) " · offline" else ""
        return "$profile · $radius · $filter · wyników: ${state.results.size} · $origin$offline"
    }

    private fun bindSearchOriginUi(state: UiState) {
        syncingSearchOriginUi = true
        when (state.searchOriginMode) {
            SearchOriginMode.GPS -> searchBinding.searchOriginGroup.check(R.id.radioSearchGps)
            SearchOriginMode.MAP -> searchBinding.searchOriginGroup.check(R.id.radioSearchMap)
            SearchOriginMode.LOCALITY -> searchBinding.searchOriginGroup.check(R.id.radioSearchLocality)
        }
        syncingSearchOriginUi = false

        val localityVisible = state.searchOriginMode == SearchOriginMode.LOCALITY
        searchBinding.localityInputLayout.isVisible = localityVisible
        if (localityVisible && searchBinding.localityInput.text?.toString() != state.localityQuery) {
            searchBinding.localityInput.setText(state.localityQuery)
            searchBinding.localityInput.setSelection(state.localityQuery.length)
        }
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
                getString(
                    R.string.offline_status_ready,
                    scopeLabel,
                    qualityLabel,
                    formatStorageBytes(offline.storageBytes),
                )
            }
            OfflineBdlStatus.ERROR -> getString(
                R.string.offline_status_error,
                offline.errorMessage ?: "błąd",
            )
        }

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
        val features = site.features.joinToString("\n") { "• ${it.labelPl}" }.ifBlank { "• (brak flag BDL)" }
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
            features.text = item.site.features.joinToString(" · ") { it.labelPl }
                .ifBlank { "Cechy BDL: brak flag" }
            zanocuj.text = zanocujLabel(item.site.zanocujStatus, item.site.distanceToZanocujBoundaryMeters)
            zanocuj.isVisible = item.site.zanocujStatus != ZanocujStatus.OUTSIDE_ZONE
            distance.text = String.format(Locale.forLanguageTag("pl-PL"), "%.1f km", item.distanceKm)

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
