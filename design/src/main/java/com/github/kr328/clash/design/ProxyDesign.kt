package com.github.kr328.clash.design

import android.content.Context
import android.content.res.ColorStateList
import android.view.View
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import com.github.kr328.clash.core.model.Proxy
import com.github.kr328.clash.core.model.TunnelState
import com.github.kr328.clash.design.adapter.ProxyGroupAdapter
import com.github.kr328.clash.design.component.ProxyMenu
import com.github.kr328.clash.design.component.ProxyViewConfig
import com.github.kr328.clash.design.component.ProxyViewState
import com.github.kr328.clash.design.databinding.DesignProxyBinding
import com.github.kr328.clash.design.model.ProxyState
import com.github.kr328.clash.design.store.UiStore
import com.github.kr328.clash.design.util.applyFrom
import com.github.kr328.clash.design.util.bindInsets
import com.github.kr328.clash.design.util.getPixels
import com.github.kr328.clash.design.util.invalidateChildren
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.resolveThemedColor
import com.github.kr328.clash.design.util.root
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ProxyDesign(
    context: Context,
    overrideMode: TunnelState.Mode?,
    private var groupNames: List<String>,
    uiStore: UiStore,
) : Design<ProxyDesign.Request>(context) {
    sealed class Request {
        object ReloadAll : Request()
        object ReLaunch : Request()

        data class PatchMode(val mode: TunnelState.Mode?) : Request()
        data class Reload(val index: Int) : Request()
        data class Select(val index: Int, val name: String) : Request()
        data class UrlTest(val index: Int) : Request()
    }

    private val binding = DesignProxyBinding
        .inflate(context.layoutInflater, context.root, false)

    private var config = ProxyViewConfig(context, uiStore.proxyLine)

    private val menu: ProxyMenu by lazy {
        ProxyMenu(context, binding.menuView, overrideMode, uiStore, requests) {
            config.proxyLine = uiStore.proxyLine
        }
    }

    private lateinit var groupAdapter: ProxyGroupAdapter

    private val urlTestingGroups = mutableSetOf<Int>()

    override val root: View = binding.root

    suspend fun updateGroup(
        position: Int,
        proxies: List<Proxy>,
        selectable: Boolean,
        parent: ProxyState,
        links: Map<String, ProxyState>,
        groupType: Proxy.Type,
        currentProxyName: String,
    ) {
        val states = withContext(Dispatchers.Default) {
            proxies.map {
                val link = if (it.type.group) links[it.name] else null

                ProxyViewState(config, it, parent, link)
            }
        }

        withContext(Dispatchers.Main) {
            groupAdapter.updateGroup(
                position,
                ProxyGroupAdapter.ProxyGroupData(
                    name = groupNames[position],
                    type = groupType,
                    currentProxyName = currentProxyName,
                    states = states,
                    selectable = selectable,
                )
            )

            urlTestingGroups.remove(position)

            updateUrlTestButtonStatus()
        }
    }

    fun resetGroups(newOverrideMode: TunnelState.Mode?, newGroupNames: List<String>) {
        groupNames = newGroupNames

        if (newGroupNames.isEmpty()) {
            binding.emptyView.visibility = View.VISIBLE
            binding.urlTestView.visibility = View.GONE
            binding.proxyList.visibility = View.GONE
            binding.urlTestFloatView.visibility = View.GONE
        } else {
            binding.emptyView.visibility = View.GONE
            binding.urlTestView.visibility = View.VISIBLE
            binding.proxyList.visibility = View.VISIBLE
            binding.urlTestFloatView.visibility = View.VISIBLE

            groupAdapter.setGroups(
                newGroupNames.map { name ->
                    ProxyGroupAdapter.ProxyGroupData(
                        name = name,
                        type = Proxy.Type.Selector,
                        currentProxyName = "",
                        states = emptyList(),
                        selectable = false,
                    )
                }
            )
        }
    }

    suspend fun requestRedrawVisible() {
        withContext(Dispatchers.Main) {
            binding.proxyList.invalidateChildren()
        }
    }

    suspend fun showModeSwitchTips() {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, R.string.mode_switch_tips, Toast.LENGTH_LONG).show()
        }
    }

    init {
        binding.self = this

        binding.activityBarLayout.applyFrom(context)

        binding.menuView.setOnClickListener {
            menu.show()
        }

        groupAdapter = ProxyGroupAdapter(
            config,
            headerClicked = { groupIndex ->
                groupAdapter.toggleGroup(groupIndex)
            },
            itemClicked = { groupIndex, proxyName ->
                requests.trySend(Request.Select(groupIndex, proxyName))
            }
        )

        if (groupNames.isEmpty()) {
            binding.emptyView.visibility = View.VISIBLE

            binding.urlTestView.visibility = View.GONE
            binding.proxyList.visibility = View.GONE
            binding.urlTestFloatView.visibility = View.GONE
        } else {
            binding.urlTestFloatView.supportImageTintList = ColorStateList.valueOf(
                context.resolveThemedColor(com.google.android.material.R.attr.colorOnPrimary)
            )

            binding.proxyList.apply {
                layoutManager = GridLayoutManager(context, 2).apply {
                    spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                        override fun getSpanSize(position: Int): Int {
                            return when (groupAdapter.getItemViewType(position)) {
                                ProxyGroupAdapter.TYPE_HEADER -> 2
                                else -> 1
                            }
                        }
                    }
                }
                adapter = groupAdapter
                clipToPadding = false

                val toolbarHeight = context.getPixels(R.dimen.toolbar_height)
                bindInsets(surface, toolbarHeight)
            }

            groupAdapter.setGroups(
                groupNames.map { name ->
                    ProxyGroupAdapter.ProxyGroupData(
                        name = name,
                        type = Proxy.Type.Selector,
                        currentProxyName = "",
                        states = emptyList(),
                        selectable = false,
                    )
                }
            )
        }
    }

    fun requestUrlTesting() {
        for (i in groupNames.indices) {
            urlTestingGroups.add(i)

            requests.trySend(Request.UrlTest(i))
        }

        updateUrlTestButtonStatus()
    }

    private fun updateUrlTestButtonStatus() {
        if (urlTestingGroups.isNotEmpty()) {
            binding.urlTestFloatView.hide()
            binding.urlTestView.visibility = View.GONE
            binding.urlTestProgressView.visibility = View.VISIBLE
        } else {
            binding.urlTestFloatView.show()
            binding.urlTestView.visibility = View.VISIBLE
            binding.urlTestProgressView.visibility = View.GONE
        }
    }
}
