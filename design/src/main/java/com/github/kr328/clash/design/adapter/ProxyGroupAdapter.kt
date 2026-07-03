package com.github.kr328.clash.design.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.github.kr328.clash.core.model.Proxy
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.component.ProxyView
import com.github.kr328.clash.design.component.ProxyViewConfig
import com.github.kr328.clash.design.component.ProxyViewState

class ProxyGroupAdapter(
    private val config: ProxyViewConfig,
    private val headerClicked: (groupIndex: Int) -> Unit,
    private val itemClicked: (groupIndex: Int, proxyName: String) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    data class ProxyGroupData(
        val name: String,
        val type: Proxy.Type,
        val currentProxyName: String,
        val states: List<ProxyViewState>,
        val selectable: Boolean,
    )

    class HeaderHolder(view: View) : RecyclerView.ViewHolder(view) {
        val groupName: TextView = view.findViewById(R.id.group_name)
        val groupType: TextView = view.findViewById(R.id.group_type)
        val expandIcon: ImageView = view.findViewById(R.id.expand_icon)
    }

    class ProxyHolder(val proxyView: ProxyView) : RecyclerView.ViewHolder(proxyView)

    private var groups: List<ProxyGroupData> = emptyList()
    private val expanded: MutableSet<Int> = mutableSetOf()
    private var flatItems: List<FlatItem> = emptyList()

    private data class FlatItem(
        val type: Int,
        val groupIndex: Int,
        val proxyIndex: Int = -1,
    )

    override fun getItemCount(): Int = flatItems.size

    override fun getItemViewType(position: Int): Int = flatItems[position].type

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_HEADER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_proxy_group_header, parent, false)
                HeaderHolder(view)
            }
            else -> {
                val view = ProxyView(config.context, config)
                ProxyHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = flatItems[position]
        val group = groups[item.groupIndex]

        when (holder) {
            is HeaderHolder -> {
                holder.groupName.text = group.name
                holder.groupType.text = group.currentProxyName
                holder.groupType.visibility = if (group.currentProxyName.isNotEmpty()) View.VISIBLE else View.GONE
                holder.expandIcon.rotation = if (item.groupIndex in expanded) 180f else 0f
                holder.itemView.setOnClickListener {
                    headerClicked(item.groupIndex)
                }
            }
            is ProxyHolder -> {
                val state = group.states[item.proxyIndex]
                holder.proxyView.apply {
                    this.state = state
                    setOnClickListener {
                        itemClicked(item.groupIndex, state.proxy.name)
                    }
                    isFocusable = group.selectable
                    isClickable = group.selectable
                    state.update(true)
                }
            }
        }
    }

    fun setGroups(groups: List<ProxyGroupData>) {
        this.groups = groups
        rebuildFlatItems()
        notifyDataSetChanged()
    }

    fun updateGroup(index: Int, data: ProxyGroupData) {
        val mutable = groups.toMutableList()
        mutable[index] = data
        groups = mutable
        rebuildFlatItems()
        notifyDataSetChanged()
    }

    fun toggleGroup(index: Int) {
        if (index in expanded) expanded.remove(index)
        else expanded.add(index)
        rebuildFlatItems()
        notifyDataSetChanged()
    }

    fun expandAll() {
        for (i in groups.indices) expanded.add(i)
        rebuildFlatItems()
        notifyDataSetChanged()
    }

    private fun rebuildFlatItems() {
        val items = mutableListOf<FlatItem>()
        for (i in groups.indices) {
            items.add(FlatItem(TYPE_HEADER, i))
            if (i in expanded) {
                for (j in groups[i].states.indices) {
                    items.add(FlatItem(TYPE_PROXY, i, j))
                }
            }
        }
        flatItems = items
    }

    companion object {
        const val TYPE_HEADER = 0
        const val TYPE_PROXY = 1
    }
}
