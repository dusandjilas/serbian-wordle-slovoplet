package com.example.rma

import android.content.Context
import android.content.SharedPreferences

class CoinManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("game_prefs", Context.MODE_PRIVATE)

    fun getCoins(): Int {
        return prefs.getInt("coins", 0)
    }

    fun addCoins(amount: Int) {
        val newCoins = getCoins() + amount
        prefs.edit().putInt("coins", newCoins).apply()
    }

    fun spendCoins(amount: Int): Boolean {
        val current = getCoins()
        return if (current >= amount) {
            prefs.edit().putInt("coins", current - amount).apply()
            true
        } else {
            false
        }
    }

    fun setCoins(amount: Int) {
        prefs.edit().putInt("coins", amount).apply()
    }
}