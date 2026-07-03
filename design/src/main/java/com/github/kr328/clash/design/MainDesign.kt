package com.github.kr328.clash.design

import android.content.Context
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.databinding.ObservableBoolean
import androidx.databinding.ObservableField
import com.github.kr328.clash.core.model.TunnelState
import com.github.kr328.clash.core.util.trafficTotal
import com.github.kr328.clash.design.databinding.DesignAboutBinding
import com.github.kr328.clash.design.databinding.DesignMainBinding
import com.github.kr328.clash.design.store.UiStore
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.resolveThemedColor
import com.github.kr328.clash.design.util.root
import com.github.kr328.clash.service.model.Profile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainDesign(context: Context, val uiStore: UiStore) : Design<MainDesign.Request>(context) {
    enum class Request {
        ToggleStatus,
        OpenProxy,
        OpenProfiles,
        OpenProviders,
        OpenLogs,
        OpenSettings,
        OpenHelp,
        OpenAbout,
        UpdateActive,
        UpdateProvider,
        BuyProfile,
        SetModeRule,
        SetModeGlobal,
        SetModeDirect,
    }
    val profileExpiry = ObservableField<String>("") // 初始化为空字符串，避免 null
    var showBuyButton = ObservableBoolean(true)

    suspend fun setProfile(profile: Profile?) {
        withContext(Dispatchers.Main){
            // 订购按钮控制
//            showBuyButton.set(profile?.source?.startsWith("https://www.aider.host/") == true)
            showBuyButton.set(true)
        }
    }
    // 在 MainDesign 类中添加以下方法
    suspend fun setProfileExpiry(expiry: String?) {
        withContext(Dispatchers.Main) {
            binding.profileExpiry = expiry // 这一步会将数据传给 XML
        }
    }
    private val binding = DesignMainBinding
        .inflate(context.layoutInflater, context.root, false)

    override val root: View
        get() = binding.root

    suspend fun setProfileName(name: String?) {
        withContext(Dispatchers.Main) {
            binding.profileName = name
        }
    }

    suspend fun setClashRunning(running: Boolean) {
        withContext(Dispatchers.Main) {
            binding.clashRunning = running
        }
    }

    suspend fun setForwarded(value: Long) {
        withContext(Dispatchers.Main) {
            binding.forwarded = value.trafficTotal()
        }
    }

    suspend fun setMode(mode: TunnelState.Mode) {
        withContext(Dispatchers.Main) {
            binding.mode = when (mode) {
                TunnelState.Mode.Direct -> context.getString(R.string.direct_mode)
                TunnelState.Mode.Global -> context.getString(R.string.global_mode)
                TunnelState.Mode.Rule -> context.getString(R.string.rule_mode)
                else -> context.getString(R.string.rule_mode)
            }
            binding.currentMode = mode
        }
    }

    suspend fun saveMode(mode: TunnelState.Mode) {
        withContext(Dispatchers.Main) {
            uiStore.proxyModeOverride = when (mode) {
                TunnelState.Mode.Rule -> "rule"
                TunnelState.Mode.Global -> "global"
                TunnelState.Mode.Direct -> "direct"
                else -> ""
            }
        }
    }

    suspend fun setHasProviders(has: Boolean) {
        withContext(Dispatchers.Main) {
            binding.hasProviders = has
        }
    }

    suspend fun showAbout(versionName: String) {
        withContext(Dispatchers.Main) {
            val binding = DesignAboutBinding.inflate(context.layoutInflater).apply {
                this.versionName = versionName
            }

            AlertDialog.Builder(context)
                .setView(binding.root)
                .show()
        }
    }

    init {
        binding.self = this

        binding.colorClashStarted = context.resolveThemedColor(com.google.android.material.R.attr.colorPrimary)
        binding.colorClashStopped = context.resolveThemedColor(R.attr.colorClashStopped)
    }

    fun request(request: Request) {
        requests.trySend(request)
    }
}