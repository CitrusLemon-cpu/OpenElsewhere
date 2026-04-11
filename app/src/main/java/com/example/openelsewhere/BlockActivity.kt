package com.example.openelsewhere

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class BlockActivity : AppCompatActivity() {

    companion object {
        @Volatile
        var instance: BlockActivity? = null

        fun finishIfShowing() {
            instance?.finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_block)

        findViewById<Button>(R.id.btn_go_back).setOnClickListener {
            BlockerAccessibilityService.instance?.performGlobalAction(
                AccessibilityService.GLOBAL_ACTION_BACK
            )
            finish()
        }
    }

    override fun onStart() {
        super.onStart()
        instance = this
    }

    override fun onStop() {
        super.onStop()
        if (instance === this) {
            instance = null
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        BlockerAccessibilityService.instance?.performGlobalAction(
            AccessibilityService.GLOBAL_ACTION_BACK
        )
        super.onBackPressed()
    }
}
