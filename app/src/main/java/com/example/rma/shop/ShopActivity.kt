package com.example.rma.shop

import com.example.rma.ads.AdManager
import com.example.rma.core.repository.CoinRepository

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import android.widget.Toast

class ShopActivity : AppCompatActivity() {
    private lateinit var adManager: AdManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        adManager = AdManager(this)
        adManager.loadAd()

        val coinRepo = CoinRepository(this)

        setContent {
            MaterialTheme {
                var coins by remember { mutableIntStateOf(coinRepo.getLocal()) }
                val isAdReady by adManager.adReady.collectAsState()

                ShopScreen(
                    coins = coins,
                    onBack = { finish() },
                    onFreeAd25 = {
                        if (isAdReady && adManager.isAdReady()) {
                            adManager.showAd { coins = coinRepo.add(25) }
                        } else {
                            adManager.loadAd()
                            Toast.makeText(this, "Reklama se učitava, pokušaj ponovo za par sekundi", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onBuyCoins = { amount -> coins = coinRepo.add(amount) },
                    onBuyNoAds = { }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!adManager.isAdReady()) adManager.loadAd()
    }
}
