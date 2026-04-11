package com.example.openelsewhere

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: AppPreferences
    private lateinit var adapter: AppListAdapter
    private lateinit var switchPause: SwitchMaterial
    private lateinit var tvPauseTitle: TextView
    private lateinit var tvPauseSubtitle: TextView
    private lateinit var cardPause: MaterialCardView
    private lateinit var etSearch: TextInputEditText

    private var allApps: List<AppListItem> = emptyList()
    private var currentQuery: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        prefs = AppPreferences.getInstance(this)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_settings) {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            } else {
                false
            }
        }

        cardPause = findViewById(R.id.card_pause)
        switchPause = findViewById(R.id.switch_pause)
        tvPauseTitle = findViewById(R.id.tv_pause_title)
        tvPauseSubtitle = findViewById(R.id.tv_pause_subtitle)
        etSearch = findViewById(R.id.et_search)

        switchPause.isChecked = !prefs.isPaused
        updatePauseCard(prefs.isPaused)
        switchPause.setOnCheckedChangeListener { _, isChecked ->
            prefs.isPaused = !isChecked
            updatePauseCard(!isChecked)
        }

        adapter = AppListAdapter(
            onWatchToggled = { packageName, isWatched ->
                prefs.setWatched(packageName, isWatched)
                allApps = allApps.map { app ->
                    if (app.packageName == packageName) app.copy(isWatched = isWatched) else app
                }
                filterAndSubmit(currentQuery)
            },
            onItemClicked = { item ->
                startActivity(Intent(this, AppDetailActivity::class.java).apply {
                    putExtra(AppDetailActivity.EXTRA_PACKAGE_NAME, item.packageName)
                    putExtra(AppDetailActivity.EXTRA_APP_NAME, item.appName)
                })
            }
        )

        val recyclerView = findViewById<RecyclerView>(R.id.rv_apps)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        recyclerView.addItemDecoration(
            DividerItemDecoration(this, DividerItemDecoration.VERTICAL)
        )

        etSearch.doAfterTextChanged {
            currentQuery = it?.toString().orEmpty()
            filterAndSubmit(currentQuery)
        }

        loadApps()
    }

    override fun onResume() {
        super.onResume()
        if (allApps.isNotEmpty()) {
            allApps = allApps.map { app ->
                app.copy(isWatched = prefs.isWatched(app.packageName))
            }
            filterAndSubmit(etSearch.text?.toString().orEmpty())
        }
        switchPause.setOnCheckedChangeListener(null)
        switchPause.isChecked = !prefs.isPaused
        switchPause.setOnCheckedChangeListener { _, isChecked ->
            prefs.isPaused = !isChecked
            updatePauseCard(!isChecked)
        }
        updatePauseCard(prefs.isPaused)
    }

    private fun loadApps() {
        lifecycleScope.launch {
            val apps = withContext(Dispatchers.IO) {
                val pm = packageManager
                pm.getInstalledApplications(0)
                    .filter { appInfo ->
                        appInfo.packageName != packageName &&
                            (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0 &&
                            (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
                    }
                    .map { appInfo ->
                        AppListItem(
                            packageName = appInfo.packageName,
                            appName = pm.getApplicationLabel(appInfo).toString(),
                            icon = pm.getApplicationIcon(appInfo),
                            isWatched = prefs.isWatched(appInfo.packageName)
                        )
                    }
            }
            allApps = apps
            filterAndSubmit(currentQuery)
        }
    }

    private fun filterAndSubmit(query: String) {
        val filtered = if (query.isBlank()) {
            allApps
        } else {
            allApps.filter {
                it.appName.contains(query, ignoreCase = true) ||
                    it.packageName.contains(query, ignoreCase = true)
            }
        }
        submitSortedList(filtered)
    }

    private fun submitSortedList(list: List<AppListItem>) {
        val sorted = list.sortedWith(
            compareByDescending<AppListItem> { it.isWatched }
                .thenBy { it.appName.lowercase() }
        )
        adapter.submitList(sorted)
    }

    private fun updatePauseCard(isPaused: Boolean) {
        if (isPaused) {
            tvPauseTitle.text = getString(R.string.label_protection_paused)
            tvPauseSubtitle.text = getString(R.string.desc_protection_paused)
            cardPause.setCardBackgroundColor(
                MaterialColors.getColor(
                    cardPause,
                    com.google.android.material.R.attr.colorErrorContainer
                )
            )
        } else {
            tvPauseTitle.text = getString(R.string.label_protection_active)
            tvPauseSubtitle.text = getString(R.string.desc_protection_active)
            cardPause.setCardBackgroundColor(
                MaterialColors.getColor(
                    cardPause,
                    com.google.android.material.R.attr.colorSurface
                )
            )
        }
    }
}
