package com.example.openelsewhere

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton

class AppDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        const val EXTRA_APP_NAME = "extra_app_name"
    }

    private lateinit var prefs: AppPreferences
    private lateinit var selectedPackageName: String
    private lateinit var llBlocked: LinearLayout
    private lateinit var llUnblocked: LinearLayout
    private lateinit var tvBlockedEmpty: TextView
    private lateinit var tvUnblockedEmpty: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_app_detail)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.detail_root)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        prefs = AppPreferences.getInstance(this)
        selectedPackageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: run {
            finish()
            return
        }
        val appName = intent.getStringExtra(EXTRA_APP_NAME) ?: selectedPackageName

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar_detail)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.title = appName
        toolbar.setNavigationOnClickListener { finish() }

        llBlocked = findViewById(R.id.ll_blocked_activities)
        llUnblocked = findViewById(R.id.ll_unblocked_activities)
        tvBlockedEmpty = findViewById(R.id.tv_blocked_empty)
        tvUnblockedEmpty = findViewById(R.id.tv_unblocked_empty)

        refreshLists()
    }

    override fun onResume() {
        super.onResume()
        refreshLists()
    }

    private fun refreshLists() {
        buildBlockedList()
        buildUnblockedList()
    }

    private fun buildBlockedList() {
        llBlocked.removeAllViews()
        val blocked = prefs.getBlockedActivityLog(selectedPackageName)
            .filter { it !in prefs.getUnblockedActivities(selectedPackageName) }

        if (blocked.isEmpty()) {
            tvBlockedEmpty.visibility = View.VISIBLE
        } else {
            tvBlockedEmpty.visibility = View.GONE
            blocked.sorted().forEach { activityClass ->
                llBlocked.addView(
                    inflateActivityRow(
                        activityClass = activityClass,
                        buttonLabel = getString(R.string.btn_allow_activity),
                        onButtonClick = {
                            prefs.addUnblockedActivity(selectedPackageName, activityClass)
                            refreshLists()
                        }
                    )
                )
            }
        }
    }

    private fun buildUnblockedList() {
        llUnblocked.removeAllViews()
        val unblocked = prefs.getUnblockedActivities(selectedPackageName)

        if (unblocked.isEmpty()) {
            tvUnblockedEmpty.visibility = View.VISIBLE
        } else {
            tvUnblockedEmpty.visibility = View.GONE
            unblocked.sorted().forEach { activityClass ->
                llUnblocked.addView(
                    inflateActivityRow(
                        activityClass = activityClass,
                        buttonLabel = getString(R.string.btn_reblock_activity),
                        onButtonClick = {
                            prefs.removeUnblockedActivity(selectedPackageName, activityClass)
                            refreshLists()
                        }
                    )
                )
            }
        }
    }

    private fun inflateActivityRow(
        activityClass: String,
        buttonLabel: String,
        onButtonClick: () -> Unit
    ): View {
        val row = LayoutInflater.from(this)
            .inflate(R.layout.item_activity_row, llBlocked, false)
        val tvClass = row.findViewById<TextView>(R.id.tv_activity_class)
        val button = row.findViewById<MaterialButton>(R.id.btn_activity_action)

        val displayName = if (activityClass.startsWith(selectedPackageName)) {
            activityClass.removePrefix(selectedPackageName).trimStart('.')
        } else {
            activityClass
        }

        tvClass.text = displayName
        button.text = buttonLabel
        button.setOnClickListener { onButtonClick() }
        return row
    }
}
