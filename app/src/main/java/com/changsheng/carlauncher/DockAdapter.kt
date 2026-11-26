package com.changsheng.carlauncher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView

class DockAdapter(
    private var items: List<AppItem>,
    private val onClick: (AppItem) -> Unit,
    private val onLongClick: (Int, AppItem) -> Unit
) : RecyclerView.Adapter<DockAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.ivDockIcon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_dock_app, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.icon.setImageDrawable(item.icon)
        holder.itemView.setOnClickListener { onClick(item) }
        holder.itemView.setOnLongClickListener { onLongClick(position, item); true }
    }

    override fun getItemCount(): Int = items.size
    fun update(newItems: List<AppItem>) { items = newItems; notifyDataSetChanged() }
}
