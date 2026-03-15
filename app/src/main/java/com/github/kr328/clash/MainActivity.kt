package com.github.kr328.clash

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.ImageButton
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.core.content.ContextCompat
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.common.util.setUUID
import com.github.kr328.clash.common.util.ticker
import com.github.kr328.clash.design.MainDesign
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.util.startClashService
import com.github.kr328.clash.util.stopClashService
import com.github.kr328.clash.util.withClash
import com.github.kr328.clash.util.withProfile
import com.github.kr328.clash.core.bridge.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.util.showExceptionToast
import com.github.kr328.clash.service.model.Profile
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*


class MainActivity : BaseActivity<MainDesign>() {
    private var rotateJob: Job? = null          // ✅ 旋转协程引用
    private var updateButton: ImageButton? = null  // ✅ 按钮引用

    private var providerRotateJob: Job? = null       // Provider 旋转协程引用
    private var updateProvidersButton: ImageButton? = null // Provider 更新按钮引用

    override suspend fun main() {
        val design = MainDesign(this)
        // 获取按钮引用
        updateButton = design.root.findViewById(R.id.syncButton)                  // Profile 更新按钮

        updateProvidersButton = design.root.findViewById(R.id.syncProviderButton) // Provider 更新按钮

        setContentDesign(design)

        design.fetch()

        val ticker = ticker(TimeUnit.SECONDS.toMillis(1))

        while (isActive) {
            select<Unit> {
                events.onReceive {
                    when (it) {
                        Event.ActivityStart,
                        Event.ServiceRecreated,
                        Event.ClashStop, Event.ClashStart,
                        Event.ProfileLoaded, Event.ProfileChanged -> design.fetch()
                        else -> Unit
                    }
                }
                design.requests.onReceive {
                    when (it) {
                        MainDesign.Request.UpdateActive -> {
                            updateButton?.let { startRotate(it) }
                            withProfile {
                                val active = queryActive()
                                if (active != null && active.imported && active.type != Profile.Type.File) {
                                    update(active.uuid)
                                }
                            }
                        }
                        MainDesign.Request.UpdateProvider -> {
                            // 开始旋转独立的 Provider 按钮
                            updateProvidersButton?.let { startProviderRotate(it) }

                            launch {
                                try {
                                    val allProviders = withClash { queryProviders() }

                                    var successCount = 0
                                    var failCount = 0

                                    allProviders.forEach { provider ->
                                        try {
                                            withClash { updateProvider(provider.type, provider.name) }
                                            successCount++
                                        } catch (e: Exception) {
                                            failCount++
                                            design.showExceptionToast(
                                                getString(
                                                    R.string.format_update_provider_failure,
                                                    provider.name,
                                                    e.message
                                                )
                                            )
                                        }
                                    }

                                    stopProviderRotate()

                                    design.showToast(
                                        "更新完成：成功 $successCount，失败 $failCount",
                                        ToastDuration.Long
                                    )
                                } catch (e: Exception) {
                                    stopProviderRotate()
                                    design.showToast(
                                        "更新出错：${e.message}",
                                        ToastDuration.Long
                                    )
                                }
                            }
                        }
                        MainDesign.Request.BuyProfile -> {
                            withProfile {
                                val active = queryActive()
                                if (active != null && active.source.startsWith("https://www.aider.host/")) {
                                    // 跳转浏览器
                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                        data = android.net.Uri.parse(active.source)
                                    }
                                    startActivity(intent)
                                } else {
                                    // 跳转浏览器官网
                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                        data = android.net.Uri.parse("https://www.aider.host/boost")
                                    }
                                    startActivity(intent)
                                }
                            }
                        }
                        MainDesign.Request.ToggleStatus -> {
                            if (clashRunning)
                                stopClashService()
                            else
                                design.startClash()
                        }
                        MainDesign.Request.OpenProxy ->
                            startActivity(ProxyActivity::class.intent)
                        MainDesign.Request.OpenProfiles ->
                            startActivity(ProfilesActivity::class.intent)
                        MainDesign.Request.OpenProviders ->
                            startActivity(ProvidersActivity::class.intent)
                        MainDesign.Request.OpenLogs -> {
                            if (LogcatService.running) {
                                startActivity(LogcatActivity::class.intent)
                            } else {
                                startActivity(LogsActivity::class.intent)
                            }
                        }
                        MainDesign.Request.OpenSettings ->
                            startActivity(SettingsActivity::class.intent)
                        MainDesign.Request.OpenHelp ->
                            startActivity(HelpActivity::class.intent)
                        MainDesign.Request.OpenAbout ->
                            design.showAbout(queryAppVersionName())
                    }
                }
                if (clashRunning) {
                    ticker.onReceive {
                        design.fetchTraffic()
                    }
                }
            }
        }
    }

    override fun onProfileUpdateCompleted(uuid: UUID?) {
        stopRotate()
        if(uuid == null)
            return;
        launch {
            var name: String? = null;
            withProfile {
                name = queryByUUID(uuid)?.name
            }
            design?.showToast(
                getString(R.string.toast_profile_updated_complete, name),
                ToastDuration.Long
            )
        }
    }
    override fun onProfileUpdateFailed(uuid: UUID?, reason: String?) {
        stopRotate()
        if(uuid == null)
            return;
        launch {

            var name: String? = null;
            withProfile {
                name = queryByUUID(uuid)?.name
            }
            design?.showToast(
                getString(R.string.toast_profile_updated_failed, name, reason),
                ToastDuration.Long
            ){
                setAction(R.string.edit) {
                    startActivity(PropertiesActivity::class.intent.setUUID(uuid))
                }
            }
        }
    }
    private fun startRotate(button: ImageButton) {
        rotateJob?.cancel()
        button.isEnabled = false

        rotateJob = launch {
            while (true) {
                button.animate()
                    .rotationBy(360f)
                    .setDuration(1000)
                    .setInterpolator(android.view.animation.LinearInterpolator())
                    .start()
                delay(1000)
            }
        }
    }

    private fun stopRotate() {
        rotateJob?.cancel()
        rotateJob = null
        updateButton?.animate()?.cancel()
        updateButton?.rotation = 0f
        updateButton?.isEnabled = true
    }

    private fun startProviderRotate(button: ImageButton) {
        providerRotateJob?.cancel()
        button.isEnabled = false

        providerRotateJob = launch {
            while (true) {
                button.animate()
                    .rotationBy(360f)
                    .setDuration(1000)
                    .setInterpolator(android.view.animation.LinearInterpolator())
                    .start()
                delay(1000)
            }
        }
    }

    private fun stopProviderRotate() {
        providerRotateJob?.cancel()
        providerRotateJob = null
        updateProvidersButton?.animate()?.cancel()
        updateProvidersButton?.rotation = 0f
        updateProvidersButton?.isEnabled = true
    }
    private suspend fun MainDesign.fetch() {
        setClashRunning(clashRunning)

        val state = withClash {
            queryTunnelState()
        }
        val providers = withClash {
            queryProviders()
        }

        setMode(state.mode)
        setHasProviders(providers.isNotEmpty())

        withProfile {
            val activeProfile = queryActive()
            setProfileName(queryActive()?.name)
            setProfile(activeProfile)

            // 设置过期时间
            if (activeProfile != null && activeProfile.expire > 0) {
                val formatted = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                    .format(java.util.Date(activeProfile.expire))
                setProfileExpiry(formatted)
                setProfile(activeProfile)
            } else {
                setProfileExpiry("")
            }
        }
    }

    private suspend fun MainDesign.fetchTraffic() {
        withClash {
            setForwarded(queryTrafficTotal())
        }
    }

    private suspend fun MainDesign.startClash() {
        val active = withProfile { queryActive() }

        if (active == null || !active.imported) {
            showToast(R.string.no_profile_selected, ToastDuration.Long) {
                setAction(R.string.profiles) {
                    startActivity(ProfilesActivity::class.intent)
                }
            }

            return
        }

        val vpnRequest = startClashService()

        try {
            if (vpnRequest != null) {
                val result = startActivityForResult(
                    ActivityResultContracts.StartActivityForResult(),
                    vpnRequest
                )

                if (result.resultCode == RESULT_OK)
                    startClashService()
            }
        } catch (e: Exception) {
            design?.showToast(R.string.unable_to_start_vpn, ToastDuration.Long)
        }
    }

    private suspend fun queryAppVersionName(): String {
        return withContext(Dispatchers.IO) {
            packageManager.getPackageInfo(packageName, 0).versionName + "\n" + Bridge.nativeCoreVersion().replace("_", "-")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val requestPermissionLauncher =
                registerForActivityResult(RequestPermission()
                ) { isGranted: Boolean ->
                }
            if (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

val mainActivityAlias = "${MainActivity::class.java.name}Alias"