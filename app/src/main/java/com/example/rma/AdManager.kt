package com.example.rma

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardItem
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

class AdManager(private val context: Context) {

    private var rewardedAd: RewardedAd? = null

    fun loadAd(adUnitId: String = "ca-app-pub-3940256099942544/5224354917") {
        RewardedAd.load(
            context,
            adUnitId,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    Log.d("AdManager", "Rewarded ad loaded")
                    rewardedAd = ad
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Log.d("AdManager", "Failed to load ad: ${adError.message}")
                    rewardedAd = null
                }
            }
        )
    }

    fun showAd(onReward: () -> Unit) {
        val ad = rewardedAd
        if (ad != null && context is Activity) {
            ad.show(context) { rewardItem: RewardItem ->
                Log.d("AdManager", "User earned reward: ${rewardItem.amount} ${rewardItem.type}")
                onReward()
            }
            rewardedAd = null
            loadAd() // Preload next ad
        } else {
            Log.d("AdManager", "Ad not ready")
        }
    }

    fun isAdReady(): Boolean {
        return rewardedAd != null
    }
}