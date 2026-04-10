package com.example.openelsewhere

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.switchmaterial.SwitchMaterial

class AppListAdapter(
    private val onWatchToggled: (packageName: String, isWatched: Boolean) -> Unit,
    private val onItemClicked: (item: AppListItem) -> Unit
) : ListAdapter<AppListItem, AppListAdapter.ViewHolder>(AppListItem.DIFF_CALLBACK) {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val ivIcon: ImageView = view.findViewById(R.id.iv_app_icon)
        private val tvName: TextView = view.findViewById(R.id.tv_app_name)
        private val tvPackage: TextView = view.findViewById(R.id.tv_package_name)
        private val switchWatch: SwitchMaterial = view.findViewById(R.id.switch_watch)

        fun bind(item: AppListItem) {
            ivIcon.setImageDrawable(item.icon)
            tvName.text = item.appName
            tvPackage.text = item.packageName

            switchWatch.setOnCheckedChangeListener(null)
            switchWatch.isChecked = item.isWatched
            switchWatch.setOnCheckedChangeListener { _, isChecked ->
                onWatchToggled(item.packageName, isChecked)
            }

            itemView.setOnClickListener {
                if (item.isWatched) onItemClicked(item)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}
