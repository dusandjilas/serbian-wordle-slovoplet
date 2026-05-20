package com.example.rma.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardItem
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AdManager(private val context: Context) {

    private var rewardedAd: RewardedAd? = null
    private var adUnitId: String = DEFAULT_REWARDED_AD_UNIT_ID
    private val _adReady = MutableStateFlow(false)
    val adReady: StateFlow<Boolean> = _adReady

    init {
        MobileAds.initialize(context)
    }

    fun loadAd(adUnitId: String = DEFAULT_REWARDED_AD_UNIT_ID) {
        this.adUnitId = adUnitId
        RewardedAd.load(
            context,
            adUnitId,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    Log.d("AdManager", "Rewarded ad loaded")
                    rewardedAd = ad
                    _adReady.value = true
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Log.d("AdManager", "Failed to load ad: ${adError.message}")
                    rewardedAd = null
                    _adReady.value = false
                }
            }
        )
    }

    fun showAd(onReward: () -> Unit) {
        val ad = rewardedAd
        if (ad != null && context is Activity) {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    rewardedAd = null
                    _adReady.value = false
                    loadAd(this@AdManager.adUnitId)
                }

                override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
                    Log.d("AdManager", "Failed to show ad: ${adError.message}")
                    rewardedAd = null
                    _adReady.value = false
                    loadAd(this@AdManager.adUnitId)
                }
            }
            ad.show(context) { rewardItem: RewardItem ->
                Log.d("AdManager", "User earned reward: ${rewardItem.amount} ${rewardItem.type}")
                onReward()
            }
        } else {
            Log.d("AdManager", "Ad not ready")
            loadAd(adUnitId)
        }
    }

    fun isAdReady(): Boolean {
        return _adReady.value && rewardedAd != null
    }

    private companion object {
        private const val DEFAULT_REWARDED_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"
    }
}
