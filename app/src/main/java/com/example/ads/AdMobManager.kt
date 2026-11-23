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
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Official Google AdMob test Ad Unit IDs covering:
 * - Adaptive Banner
 * - Interstitial
 * - Rewarded
 * - Rewarded Interstitial
 * - App Open
 *
 * Replace these constants and `com.google.android.gms.ads.APPLICATION_ID` in AndroidManifest.xml
 * with your production AdMob unit IDs before publishing to Google Play.
 */
object AdMobConfig {
    const val BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"
    const val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"
    const val REWARDED_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"
    const val REWARDED_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/5354046379"
    const val APP_OPEN_AD_UNIT_ID = "ca-app-pub-3940256099942544/9257395921"
}

object AdMobManager {
    private const val TAG = "AdMobManager"
    private const val INTERSTITIAL_COOLDOWN_MS = 55_000L
    private const val REWARDED_INTERSTITIAL_COOLDOWN_MS = 90_000L
    private const val APP_OPEN_COOLDOWN_MS = 240_000L
    private const val APP_OPEN_EXPIRY_MS = 4 * 60 * 60 * 1000L

    private val initStarted = AtomicBoolean(false)
    private val isLoadingInterstitial = AtomicBoolean(false)
    private val isLoadingRewarded = AtomicBoolean(false)
    private val isLoadingRewardedInterstitial = AtomicBoolean(false)
    private val isLoadingAppOpen = AtomicBoolean(false)
    private val isShowingFullScreenAd = AtomicBoolean(false)
    private val mainScope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val _isPremiumActive = MutableStateFlow(false)
    val isPremiumActive: StateFlow<Boolean> = _isPremiumActive.asStateFlow()

    private val _rewardAdFreeUntilMs = MutableStateFlow(0L)
    val rewardAdFreeUntilMs: StateFlow<Long> = _rewardAdFreeUntilMs.asStateFlow()

    private val _isRewardedReady = MutableStateFlow(false)
    val isRewardedReady: StateFlow<Boolean> = _isRewardedReady.asStateFlow()

    private val _isRewardedInterstitialReady = MutableStateFlow(false)
    val isRewardedInterstitialReady: StateFlow<Boolean> = _isRewardedInterstitialReady.asStateFlow()

    @Volatile
    private var interstitialAd: InterstitialAd? = null

    @Volatile
    private var rewardedAd: RewardedAd? = null

    @Volatile
    private var rewardedInterstitialAd: RewardedInterstitialAd? = null

    @Volatile
    private var appOpenAd: AppOpenAd? = null

    @Volatile
    private var appOpenLoadedAt: Long = 0L

    @Volatile
    private var lastInterstitialShownAt: Long = 0L

    @Volatile
    private var lastRewardedInterstitialShownAt: Long = 0L

    @Volatile
    private var lastAppOpenShownAt: Long = 0L

    fun areAdsSuppressed(): Boolean {
        return _isPremiumActive.value || System.currentTimeMillis() < _rewardAdFreeUntilMs.value
    }

    fun setPremiumActive(active: Boolean, context: Context? = null) {
        _isPremiumActive.value = active
        if (active) {
            // Premium members never see ads: drop any cached ad objects immediately
            interstitialAd = null
            rewardedAd = null
            rewardedInterstitialAd = null
            appOpenAd = null
            _isRewardedReady.value = false
            _isRewardedInterstitialReady.value = false
        } else if (context != null && _isInitialized.value) {
            preloadAllAds(context.applicationContext)
        }
    }

    fun grantRewardedAdFreePass(minutes: Int = 30) {
        val until = System.currentTimeMillis() + minutes * 60_000L
        _rewardAdFreeUntilMs.value = maxOf(_rewardAdFreeUntilMs.value, until)
    }

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
        if (!_isPremiumActive.value) {
            preloadAllAds(context.applicationContext)
        }
    }

    fun preloadAllAds(context: Context) {
        if (_isPremiumActive.value) return
        val appContext = context.applicationContext
        loadInterstitial(appContext)
        loadRewarded(appContext)
        loadRewardedInterstitial(appContext)
        loadAppOpenAd(appContext)
    }

    fun loadInterstitial(context: Context) {
        if (_isPremiumActive.value) return
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
                            interstitialAd = if (_isPremiumActive.value) null else ad
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

    fun loadRewarded(context: Context) {
        if (_isPremiumActive.value) return
        if (rewardedAd != null || !isLoadingRewarded.compareAndSet(false, true)) return
        val appContext = context.applicationContext
        val request = AdRequest.Builder().build()
        mainScope.launch {
            runCatching {
                RewardedAd.load(
                    appContext,
                    AdMobConfig.REWARDED_AD_UNIT_ID,
                    request,
                    object : RewardedAdLoadCallback() {
                        override fun onAdLoaded(ad: RewardedAd) {
                            if (_isPremiumActive.value) {
                                rewardedAd = null
                                _isRewardedReady.value = false
                            } else {
                                rewardedAd = ad
                                _isRewardedReady.value = true
                            }
                            isLoadingRewarded.set(false)
                        }

                        override fun onAdFailedToLoad(error: LoadAdError) {
                            rewardedAd = null
                            _isRewardedReady.value = false
                            isLoadingRewarded.set(false)
                            Log.d(TAG, "Rewarded ad failed to load: ${error.message}")
                        }
                    }
                )
            }.onFailure {
                _isRewardedReady.value = false
                isLoadingRewarded.set(false)
            }
        }
    }

    fun loadRewardedInterstitial(context: Context) {
        if (_isPremiumActive.value) return
        if (rewardedInterstitialAd != null || !isLoadingRewardedInterstitial.compareAndSet(false, true)) return
        val appContext = context.applicationContext
        val request = AdRequest.Builder().build()
        mainScope.launch {
            runCatching {
                RewardedInterstitialAd.load(
                    appContext,
                    AdMobConfig.REWARDED_INTERSTITIAL_AD_UNIT_ID,
                    request,
                    object : RewardedInterstitialAdLoadCallback() {
                        override fun onAdLoaded(ad: RewardedInterstitialAd) {
                            if (_isPremiumActive.value) {
                                rewardedInterstitialAd = null
                                _isRewardedInterstitialReady.value = false
                            } else {
                                rewardedInterstitialAd = ad
                                _isRewardedInterstitialReady.value = true
                            }
                            isLoadingRewardedInterstitial.set(false)
                        }

                        override fun onAdFailedToLoad(error: LoadAdError) {
                            rewardedInterstitialAd = null
                            _isRewardedInterstitialReady.value = false
                            isLoadingRewardedInterstitial.set(false)
                            Log.d(TAG, "Rewarded Interstitial failed to load: ${error.message}")
                        }
                    }
                )
            }.onFailure {
                _isRewardedInterstitialReady.value = false
                isLoadingRewardedInterstitial.set(false)
            }
        }
    }

    fun loadAppOpenAd(context: Context) {
        if (_isPremiumActive.value) return
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
                            if (_isPremiumActive.value) {
                                appOpenAd = null
                            } else {
                                appOpenAd = ad
                                appOpenLoadedAt = System.currentTimeMillis()
                            }
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

    fun showAppOpenIfAvailable(activity: Activity?) {
        if (areAdsSuppressed()) return
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
     * Shows a preloaded Interstitial ad for non-premium users (respecting cooldown).
     * Premium members always skip ads and invoke [onContinue] immediately.
     */
    fun showInterstitialIfReady(activity: Activity?, onContinue: () -> Unit = {}) {
        if (areAdsSuppressed()) {
            onContinue()
            return
        }
        val ad = interstitialAd
        val now = System.currentTimeMillis()
        if (activity == null || activity.isFinishing || activity.isDestroyed || ad == null ||
            (now - lastInterstitialShownAt) < INTERSTITIAL_COOLDOWN_MS ||
            !isShowingFullScreenAd.compareAndSet(false, true)
        ) {
            if (activity != null && ad == null) loadInterstitial(activity)
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

    /**
     * Shows a preloaded Rewarded Interstitial ad for non-premium users (e.g., after batch scanning
     * or high-value PDF export), rewarding a temporary 15-minute ad-free bonus on completion.
     * Premium members skip immediately.
     */
    fun showRewardedInterstitialIfReady(
        activity: Activity?,
        onContinue: (earnedReward: Boolean) -> Unit = {}
    ) {
        if (areAdsSuppressed()) {
            onContinue(true)
            return
        }
        val ad = rewardedInterstitialAd
        val now = System.currentTimeMillis()
        if (activity == null || activity.isFinishing || activity.isDestroyed || ad == null ||
            (now - lastRewardedInterstitialShownAt) < REWARDED_INTERSTITIAL_COOLDOWN_MS ||
            !isShowingFullScreenAd.compareAndSet(false, true)
        ) {
            if (activity != null && ad == null) loadRewardedInterstitial(activity)
            // Fall back smoothly to standard interstitial if rewarded interstitial isn't loaded yet
            showInterstitialIfReady(activity) { onContinue(false) }
            return
        }

        rewardedInterstitialAd = null
        _isRewardedInterstitialReady.value = false
        lastRewardedInterstitialShownAt = now
        var earned = false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                isShowingFullScreenAd.set(false)
                loadRewardedInterstitial(activity)
                onContinue(earned)
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                isShowingFullScreenAd.set(false)
                loadRewardedInterstitial(activity)
                onContinue(false)
            }
        }
        runCatching {
            ad.show(activity) {
                earned = true
                grantRewardedAdFreePass(15)
            }
        }.onFailure {
            isShowingFullScreenAd.set(false)
            loadRewardedInterstitial(activity)
            onContinue(false)
        }
    }

    /**
     * Shows an opt-in Rewarded Ad so non-premium users can watch a short video ad to unlock
     * 30 minutes of ad-free PDF editing & conversion.
     */
    fun showRewardedAd(
        activity: Activity?,
        onRewardEarned: () -> Unit,
        onUnavailable: (String) -> Unit = {},
        onDismissed: () -> Unit = {}
    ) {
        if (_isPremiumActive.value) {
            onRewardEarned()
            return
        }
        val ad = rewardedAd
        if (activity == null || activity.isFinishing || activity.isDestroyed) {
            onUnavailable("Unable to display ad right now.")
            return
        }
        if (ad == null) {
            loadRewarded(activity)
            onUnavailable("Rewarded ad is still loading. Please try again in a moment.")
            return
        }
        if (!isShowingFullScreenAd.compareAndSet(false, true)) {
            onUnavailable("Another ad is currently playing.")
            return
        }

        rewardedAd = null
        _isRewardedReady.value = false
        var earned = false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                isShowingFullScreenAd.set(false)
                loadRewarded(activity)
                if (!earned) onDismissed()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                isShowingFullScreenAd.set(false)
                loadRewarded(activity)
                onUnavailable(adError.message)
            }
        }
        runCatching {
            ad.show(activity) {
                earned = true
                grantRewardedAdFreePass(30)
                onRewardEarned()
            }
        }.onFailure { err ->
            isShowingFullScreenAd.set(false)
            loadRewarded(activity)
            onUnavailable(err.message ?: "Could not show rewarded ad.")
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
    val isPremium by AdMobManager.isPremiumActive.collectAsStateWithLifecycle()
    val rewardUntil by AdMobManager.rewardAdFreeUntilMs.collectAsStateWithLifecycle()
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(rewardUntil) {
        val remaining = rewardUntil - System.currentTimeMillis()
        if (remaining > 0L) {
            nowMs = System.currentTimeMillis()
            delay(remaining + 250L)
            nowMs = System.currentTimeMillis()
        }
    }

    // Premium members (or active rewarded ad-free pass) never see banner ads
    if (isPremium || nowMs < rewardUntil) return

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
