package com.changsheng.carlauncher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AppDrawerAdapter(
    private var items: List<AppItem>,
    private var hiddenSet: Set<String>,
    private val onClick: (AppItem) -> Unit,
    private val onLongClick: (AppItem) -> Unit
) : RecyclerView.Adapter<AppDrawerAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.ivAppIcon)
        val name: TextView = view.findViewById(R.id.tvAppName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_drawer_app, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.icon.setImageDrawable(item.icon)
        holder.name.text = item.label
        holder.itemView.alpha = if (hiddenSet.contains(item.packageName)) 0.4f else 1.0f
        holder.itemView.setOnClickListener { onClick(item) }
        holder.itemView.setOnLongClickListener { onLongClick(item); true }
    }

    override fun getItemCount(): Int = items.size
    fun update(newItems: List<AppItem>, newHidden: Set<String>) { items = newItems; hiddenSet = newHidden; notifyDataSetChanged() }
}
