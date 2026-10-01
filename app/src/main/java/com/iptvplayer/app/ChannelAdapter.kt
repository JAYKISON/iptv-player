package com.iptvplayer.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load

class ChannelAdapter(
    private val onClick: (Int) -> Unit,
    private val onLongClick: (Channel) -> Unit
) : RecyclerView.Adapter<ChannelAdapter.VH>() {

    private var items: List<Channel> = emptyList()
    private var favs: Set<String> = emptySet()

    val current: List<Channel> get() = items

    fun submit(list: List<Channel>, favorites: Set<String>) {
        items = list
        favs = favorites
        notifyDataSetChanged()
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val logo: ImageView = v.findViewById(R.id.logo)
        val name: TextView = v.findViewById(R.id.name)
        val group: TextView = v.findViewById(R.id.group)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_channel, parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val ch = items[position]
        h.name.text = if (ch.url in favs) "★ ${ch.name}" else ch.name
        h.group.text = ch.group
        h.logo.load(ch.logo) {
            placeholder(R.drawable.ic_tv)
            error(R.drawable.ic_tv)
            crossfade(true)
        }
        h.itemView.setOnClickListener {
            val pos = h.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) onClick(pos)
        }
        h.itemView.setOnLongClickListener { onLongClick(ch); true }
    }
}
