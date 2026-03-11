package com.example.rma

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.widget.Toast

class ShopActivity : AppCompatActivity() {
    private lateinit var adManager: AdManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        adManager = AdManager(this)
        adManager.loadAd("ca-app-pub-3940256099942544/5224354917")

        val coinRepo = CoinRepository(this)

        setContent {
            MaterialTheme {
                var coins by remember { mutableIntStateOf(coinRepo.getLocal()) }

                ShopScreen(
                    coins = coins,
                    onBack = { finish() },
                    onFreeAd25 = {
                        if (adManager.isAdReady()) {
                            adManager.showAd { coins = coinRepo.add(25) }
                        } else {
                            Toast.makeText(this, "Реклама није спремна, покушај касније", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onBuyCoins = { amount -> coins = coinRepo.add(amount) },
                    onBuyNoAds = { }
                )
            }
        }
    }
}
