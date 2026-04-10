package com.example.openelsewhere

import android.graphics.drawable.Drawable
import androidx.recyclerview.widget.DiffUtil

data class AppListItem(
    val packageName: String,
    val appName: String,
    val icon: Drawable,
    val isWatched: Boolean
) {
    companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<AppListItem>() {
            override fun areItemsTheSame(old: AppListItem, new: AppListItem) =
                old.packageName == new.packageName

            override fun areContentsTheSame(old: AppListItem, new: AppListItem) =
                old.isWatched == new.isWatched && old.appName == new.appName
        }
    }
}
