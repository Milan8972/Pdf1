package com.example.ads

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.LocalPaperPilotColors
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Official Google AdMob test Ad Unit IDs.
 * Replace these constants and `com.google.android.gms.ads.APPLICATION_ID` in AndroidManifest.xml
 * with your production IDs from the AdMob Console before publishing to Google Play.
 */
object AdMobConfig {
    const val BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"
    const val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"
    const val APP_OPEN_AD_UNIT_ID = "ca-app-pub-3940256099942544/9257395921"
}

object AdMobManager {
    private const val TAG = "AdMobManager"
    private const val INTERSTITIAL_COOLDOWN_MS = 60_000L
    private const val APP_OPEN_COOLDOWN_MS = 240_000L
    private const val APP_OPEN_EXPIRY_MS = 4 * 60 * 60 * 1000L

    private val initStarted = AtomicBoolean(false)
    private val isLoadingInterstitial = AtomicBoolean(false)
    private val isLoadingAppOpen = AtomicBoolean(false)
    private val isShowingFullScreenAd = AtomicBoolean(false)
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    @Volatile
    private var interstitialAd: InterstitialAd? = null

    @Volatile
    private var lastInterstitialShownAt: Long = 0L

    @Volatile
    private var appOpenAd: AppOpenAd? = null

    @Volatile
    private var appOpenLoadedAt: Long = 0L

    @Volatile
    private var lastAppOpenShownAt: Long = 0L

    fun configureTestDevices() {
        runCatching {
            val requestConfig = RequestConfiguration.Builder()
                .setTestDeviceIds(listOf(AdRequest.DEVICE_ID_EMULATOR))
                .build()
            MobileAds.setRequestConfiguration(requestConfig)
        }
    }

    fun onMobileAdsInitialized(context: Context) {
        initStarted.set(true)
        _isInitialized.value = true
        val appContext = context.applicationContext
        loadInterstitial(appContext)
        loadAppOpenAd(appContext)
    }

    fun initialize(context: Context) {
        if (!initStarted.compareAndSet(false, true)) return
        val appContext = context.applicationContext
        ioScope.launch {
            runCatching {
                configureTestDevices()
                MobileAds.initialize(appContext) {
                    onMobileAdsInitialized(appContext)
                }
            }.onFailure { err ->
                Log.w(TAG, "AdMob initialization failed: ${err.message}")
                _isInitialized.value = true
            }
        }
    }

    fun loadInterstitial(context: Context) {
        if (interstitialAd != null || !isLoadingInterstitial.compareAndSet(false, true)) return
        val appContext = context.applicationContext
        val request = AdRequest.Builder().build()
        mainScope.launch {
            runCatching {
                InterstitialAd.load(
                    appContext,
                    AdMobConfig.INTERSTITIAL_AD_UNIT_ID,
                    request,
                    object : InterstitialAdLoadCallback() {
                        override fun onAdLoaded(ad: InterstitialAd) {
                            interstitialAd = ad
                            isLoadingInterstitial.set(false)
                        }

                        override fun onAdFailedToLoad(error: LoadAdError) {
                            interstitialAd = null
                            isLoadingInterstitial.set(false)
                            Log.d(TAG, "Interstitial failed to load: ${error.message}")
                        }
                    }
                )
            }.onFailure {
                isLoadingInterstitial.set(false)
            }
        }
    }

    fun loadAppOpenAd(context: Context) {
        val currentAd = appOpenAd
        if (currentAd != null && (System.currentTimeMillis() - appOpenLoadedAt) < APP_OPEN_EXPIRY_MS) return
        if (!isLoadingAppOpen.compareAndSet(false, true)) return
        val appContext = context.applicationContext
        val request = AdRequest.Builder().build()
        mainScope.launch {
            runCatching {
                AppOpenAd.load(
                    appContext,
                    AdMobConfig.APP_OPEN_AD_UNIT_ID,
                    request,
                    object : AppOpenAd.AppOpenAdLoadCallback() {
                        override fun onAdLoaded(ad: AppOpenAd) {
                            appOpenAd = ad
                            appOpenLoadedAt = System.currentTimeMillis()
                            isLoadingAppOpen.set(false)
                        }

                        override fun onAdFailedToLoad(error: LoadAdError) {
                            appOpenAd = null
                            isLoadingAppOpen.set(false)
                            Log.d(TAG, "App Open ad failed to load: ${error.message}")
                        }
                    }
                )
            }.onFailure {
                isLoadingAppOpen.set(false)
            }
        }
    }

    /**
     * Shows a preloaded App Open ad when returning from background, subject to cooldown.
     */
    fun showAppOpenIfAvailable(activity: Activity?) {
        val ad = appOpenAd
        val now = System.currentTimeMillis()
        val isFresh = ad != null && (now - appOpenLoadedAt) < APP_OPEN_EXPIRY_MS
        if (activity == null || activity.isFinishing || activity.isDestroyed ||
            !isFresh || (now - lastAppOpenShownAt) < APP_OPEN_COOLDOWN_MS ||
            !isShowingFullScreenAd.compareAndSet(false, true)
        ) {
            if (activity != null && !isFresh) loadAppOpenAd(activity)
            return
        }

        appOpenAd = null
        lastAppOpenShownAt = now
        ad!!.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                isShowingFullScreenAd.set(false)
                loadAppOpenAd(activity)
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                isShowingFullScreenAd.set(false)
                loadAppOpenAd(activity)
            }
        }
        runCatching {
            ad.show(activity)
        }.onFailure {
            isShowingFullScreenAd.set(false)
            loadAppOpenAd(activity)
        }
    }

    /**
     * Shows a preloaded interstitial ad at natural completion moments (e.g., after a tool
     * finishes or an edited PDF is saved), enforcing a cooldown so ads stay non-intrusive.
     * Always invokes [onContinue] immediately if offline, not loaded, or still in cooldown.
     */
    fun showInterstitialIfReady(activity: Activity?, onContinue: () -> Unit = {}) {
        val ad = interstitialAd
        val now = System.currentTimeMillis()
        if (activity == null || activity.isFinishing || activity.isDestroyed || ad == null ||
            (now - lastInterstitialShownAt) < INTERSTITIAL_COOLDOWN_MS ||
            !isShowingFullScreenAd.compareAndSet(false, true)
        ) {
            if (activity != null && ad == null) {
                loadInterstitial(activity)
            }
            onContinue()
            return
        }

        interstitialAd = null
        lastInterstitialShownAt = now
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                isShowingFullScreenAd.set(false)
                loadInterstitial(activity)
                onContinue()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                isShowingFullScreenAd.set(false)
                loadInterstitial(activity)
                onContinue()
            }
        }
        runCatching {
            ad.show(activity)
        }.onFailure {
            isShowingFullScreenAd.set(false)
            loadInterstitial(activity)
            onContinue()
        }
    }
}

fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

@Composable
fun AdMobBannerBar(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val extra = LocalPaperPilotColors.current
    val sdkReady by AdMobManager.isInitialized.collectAsStateWithLifecycle()

    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .testTag("admob_banner_container")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(thickness = 0.5.dp, color = extra.subtleBorder)
            BoxWithConstraints(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                val adWidthDp = maxWidth.value.toInt().coerceAtLeast(320)
                val adSize = remember(adWidthDp) {
                    val adaptive = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, adWidthDp)
                    if (adaptive.height > 0) adaptive else AdSize.BANNER
                }
                var adFailed by remember(adSize) { mutableStateOf(false) }

                val adView = remember(adSize) {
                    AdView(context).apply {
                        adUnitId = AdMobConfig.BANNER_AD_UNIT_ID
                        setAdSize(adSize)
                        adListener = object : AdListener() {
                            override fun onAdLoaded() {
                                adFailed = false
                            }

                            override fun onAdFailedToLoad(error: LoadAdError) {
                                adFailed = true
                                Log.d("AdMobBannerBar", "Banner failed to load: ${error.message}")
                            }
                        }
                    }
                }

                LaunchedEffect(adView, sdkReady) {
                    if (sdkReady) {
                        runCatching { adView.loadAd(AdRequest.Builder().build()) }
                    }
                }

                DisposableEffect(lifecycleOwner, adView) {
                    val observer = LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_RESUME -> runCatching { adView.resume() }
                            Lifecycle.Event.ON_PAUSE -> runCatching { adView.pause() }
                            else -> Unit
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                        runCatching { adView.destroy() }
                    }
                }

                val minHeightDp = if (adFailed) 0.dp else adSize.height.coerceAtLeast(50).dp
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = minHeightDp)
                ) {
                    AndroidView(
                        factory = { adView },
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                    )
                }
            }
        }
    }
}
