package com.example.rma

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import java.time.LocalDate

class GameProfileManager(context: Context) {

    private val prefs = context.getSharedPreferences("game_profile_prefs", Context.MODE_PRIVATE)
    private val firebaseStatsRepository = FirebaseStatsRepository()

    private fun profileKey(): String {
        val user = FirebaseAuth.getInstance().currentUser
        return user?.uid ?: "guest"
    }

    private fun key(name: String): String = "${profileKey()}_$name"

    // ---------- Classic streak ----------
    fun getClassicStreak(): Int = prefs.getInt(key("classic_streak"), 0)

    fun getBestClassicStreak(): Int = prefs.getInt(key("classic_best_streak"), 0)

    // ---------- Classic totals ----------
    fun getClassicGamesPlayed(): Int = prefs.getInt(key("classic_games_played"), 0)

    fun getClassicWins(): Int = prefs.getInt(key("classic_wins"), 0)

    fun getClassicLosses(): Int = prefs.getInt(key("classic_losses"), 0)

    fun getClassicWinRate(): Int {
        val games = getClassicGamesPlayed()
        if (games == 0) return 0
        return ((getClassicWins() * 100f) / games).toInt()
    }

    // ---------- Guess distribution ----------
    fun getGuessDistribution(attempt: Int): Int {
        require(attempt in 1..6) { "Attempt must be between 1 and 6" }
        return prefs.getInt(key("guess_dist_$attempt"), 0)
    }

    fun getAllGuessDistribution(): List<Int> {
        return (1..6).map { getGuessDistribution(it) }
    }

    // ---------- Classic result recording ----------
    fun recordClassicWin(attemptsUsed: Int) {
        require(attemptsUsed in 1..6) { "attemptsUsed must be between 1 and 6" }

        val newStreak = getClassicStreak() + 1
        val best = getBestClassicStreak()

        prefs.edit()
            .putInt(key("classic_streak"), newStreak)
            .putInt(key("classic_best_streak"), maxOf(best, newStreak))
            .putInt(key("classic_games_played"), getClassicGamesPlayed() + 1)
            .putInt(key("classic_wins"), getClassicWins() + 1)
            .putInt(key("guess_dist_$attemptsUsed"), getGuessDistribution(attemptsUsed) + 1)
            .apply()

        firebaseStatsRepository.syncStats(this)
    }

    fun recordClassicLoss() {
        prefs.edit()
            .putInt(key("classic_streak"), 0)
            .putInt(key("classic_games_played"), getClassicGamesPlayed() + 1)
            .putInt(key("classic_losses"), getClassicLosses() + 1)
            .apply()

        firebaseStatsRepository.syncStats(this)
    }

    // ---------- Daily ----------
    fun getLastDailyPlayedDate(): String? =
        prefs.getString(key("daily_last_played_date"), null)

    fun hasPlayedDailyToday(): Boolean {
        return getLastDailyPlayedDate() == LocalDate.now().toString()
    }

    fun markDailyPlayedToday(won: Boolean) {
        prefs.edit()
            .putString(key("daily_last_played_date"), LocalDate.now().toString())
            .putBoolean(key("daily_last_won"), won)
            .apply()

        firebaseStatsRepository.syncStats(this)
    }

    fun wasLastDailyWon(): Boolean =
        prefs.getBoolean(key("daily_last_won"), false)

    // ---------- Optional daily counters ----------
    fun getDailyGamesPlayed(): Int = prefs.getInt(key("daily_games_played"), 0)

    fun getDailyWins(): Int = prefs.getInt(key("daily_wins"), 0)

    fun getDailyLosses(): Int = prefs.getInt(key("daily_losses"), 0)

    fun recordDailyResult(won: Boolean) {
        val editor = prefs.edit()
            .putInt(key("daily_games_played"), getDailyGamesPlayed() + 1)

        if (won) {
            editor.putInt(key("daily_wins"), getDailyWins() + 1)
        } else {
            editor.putInt(key("daily_losses"), getDailyLosses() + 1)
        }

        editor.apply()
        firebaseStatsRepository.syncStats(this)
    }

    // ---------- Coins ----------
    fun getStoredCoins(): Int = prefs.getInt(key("stored_coins"), 0)

    fun setStoredCoins(value: Int) {
        prefs.edit().putInt(key("stored_coins"), value).apply()
        firebaseStatsRepository.syncStats(this)
    }

    // ---------- Debug / reset helpers ----------
    fun resetClassicStats() {
        val editor = prefs.edit()
        editor.remove(key("classic_streak"))
        editor.remove(key("classic_best_streak"))
        editor.remove(key("classic_games_played"))
        editor.remove(key("classic_wins"))
        editor.remove(key("classic_losses"))
        for (i in 1..6) {
            editor.remove(key("guess_dist_$i"))
        }
        editor.apply()

        firebaseStatsRepository.syncStats(this)
    }

    fun getXp(): Int = prefs.getInt(key("xp"), 0)

    fun getLevel(): Int {
        val xp = getXp()
        return (xp / 100) + 1
    }

    fun getXpProgress(): Float {
        val xp = getXp()
        return (xp % 100) / 100f
    }

    fun addXp(amount: Int) {
        val newXp = getXp() + amount
        prefs.edit().putInt(key("xp"), newXp).apply()
        firebaseStatsRepository.syncStats(this)
    }

    fun importFromRemote(data: Map<String, Any>) {
        val classicWins = (data["classicWins"] as? Long)?.toInt() ?: 0
        val classicLosses = (data["classicLosses"] as? Long)?.toInt() ?: 0
        val classicGamesPlayed = (data["classicGamesPlayed"] as? Long)?.toInt() ?: 0
        val classicStreak = (data["classicStreak"] as? Long)?.toInt() ?: 0
        val bestClassicStreak = (data["bestClassicStreak"] as? Long)?.toInt() ?: 0
        val xp = (data["xp"] as? Long)?.toInt() ?: 0
        val dailyGamesPlayed = (data["dailyGamesPlayed"] as? Long)?.toInt() ?: 0
        val dailyWins = (data["dailyWins"] as? Long)?.toInt() ?: 0
        val dailyLosses = (data["dailyLosses"] as? Long)?.toInt() ?: 0
        val lastDailyPlayedDate = data["lastDailyPlayedDate"] as? String
        val lastDailyWon = data["lastDailyWon"] as? Boolean ?: false

        val storedCoins = (data["storedCoins"] as? Long)?.toInt() ?: 0

        val guessDistribution =
            (data["guessDistribution"] as? List<*>)?.mapNotNull { (it as? Long)?.toInt() }
                ?: listOf(0, 0, 0, 0, 0, 0)

        val editor = prefs.edit()
            .putInt(key("classic_wins"), classicWins)
            .putInt(key("classic_losses"), classicLosses)
            .putInt(key("classic_games_played"), classicGamesPlayed)
            .putInt(key("classic_streak"), classicStreak)
            .putInt(key("classic_best_streak"), bestClassicStreak)
            .putInt(key("daily_games_played"), dailyGamesPlayed)
            .putInt(key("daily_wins"), dailyWins)
            .putInt(key("daily_losses"), dailyLosses)
            .putBoolean(key("daily_last_won"), lastDailyWon)
            .putInt(key("stored_coins"), storedCoins)
            .putInt(key("xp"), xp)

        if (lastDailyPlayedDate != null) {
            editor.putString(key("daily_last_played_date"), lastDailyPlayedDate)
        } else {
            editor.remove(key("daily_last_played_date"))
        }

        for (i in 1..6) {
            editor.putInt(
                key("guess_dist_$i"),
                guessDistribution.getOrElse(i - 1) { 0 }
            )
        }

        editor.apply()
    }

    fun resetDailyStats() {
        prefs.edit()
            .remove(key("daily_last_played_date"))
            .remove(key("daily_last_won"))
            .remove(key("daily_games_played"))
            .remove(key("daily_wins"))
            .remove(key("daily_losses"))
            .apply()

        firebaseStatsRepository.syncStats(this)
    }



    fun resetAllStats() {
        val editor = prefs.edit()

        editor.remove(key("classic_streak"))
        editor.remove(key("classic_best_streak"))
        editor.remove(key("classic_games_played"))
        editor.remove(key("classic_wins"))
        editor.remove(key("classic_losses"))

        for (i in 1..6) {
            editor.remove(key("guess_dist_$i"))
        }

        editor.remove(key("daily_last_played_date"))
        editor.remove(key("daily_last_won"))
        editor.remove(key("daily_games_played"))
        editor.remove(key("daily_wins"))
        editor.remove(key("daily_losses"))

        editor.apply()

        firebaseStatsRepository.syncStats(this)
    }
}