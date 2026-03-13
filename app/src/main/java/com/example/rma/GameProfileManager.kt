package com.example.rma

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.auth.FirebaseAuth
import java.time.LocalDate

class GameProfileManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("game_profile", Context.MODE_PRIVATE)

    private val firebaseStatsRepository = FirebaseStatsRepository()

    private fun profileKey(): String {
        val user = FirebaseAuth.getInstance().currentUser
        return user?.uid ?: "guest"
    }

    private fun key(name: String): String = "${profileKey()}_$name"

    companion object {
        private const val KEY_CLASSIC_STREAK      = "classic_streak"
        private const val KEY_CLASSIC_BEST_STREAK = "classic_best_streak"
        private const val KEY_CLASSIC_WINS        = "classic_wins"
        private const val KEY_CLASSIC_LOSSES      = "classic_losses"
        private const val KEY_DAILY_PLAYED_DATE   = "daily_played_date"
        private const val KEY_DAILY_WINS          = "daily_wins"
        private const val KEY_DAILY_LOSSES        = "daily_losses"
        private const val KEY_TOTAL_XP            = "total_xp"
        private const val KEY_GUESS_DIST_PREFIX   = "guess_dist_"
        private const val MAX_GUESSES             = 6

        private val CLASSIC_WIN_XP = intArrayOf(500, 400, 300, 200, 150, 100)
        private const val CLASSIC_LOSS_XP = 30

        private const val DAILY_MULTIPLIER = 1.5f
    }

    fun getTotalXp(): Int = prefs.getInt(key(KEY_TOTAL_XP), 0)

    fun getXp(): Int = getTotalXp()

    private fun addXp(amount: Int) {
        prefs.edit().putInt(key(KEY_TOTAL_XP), getTotalXp() + amount).apply()
    }

    fun getLevel(): Int {
        val xp = getTotalXp()
        var level = 1
        while (xp >= level * level * 200) level++
        return level
    }

    fun getXpProgress(): Float {
        val xp = getTotalXp()
        val level = getLevel()
        val prev  = (level - 1) * (level - 1) * 200
        val next  = level * level * 200
        return if (next == prev) 0f else ((xp - prev).toFloat() / (next - prev)).coerceIn(0f, 1f)
    }

    fun awardClassicXp(guessCount: Int): Int {
        val xp = if (guessCount in 1..MAX_GUESSES)
            CLASSIC_WIN_XP[guessCount - 1]
        else
            CLASSIC_LOSS_XP
        addXp(xp)
        return xp
    }

    fun awardDailyXp(guessCount: Int): Int {
        val base = if (guessCount in 1..MAX_GUESSES)
            CLASSIC_WIN_XP[guessCount - 1]
        else
            CLASSIC_LOSS_XP
        val xp = (base * DAILY_MULTIPLIER).toInt()
        addXp(xp)
        return xp
    }

    fun getClassicStreak(): Int = prefs.getInt(key(KEY_CLASSIC_STREAK), 0)
    fun getBestClassicStreak(): Int = prefs.getInt(key(KEY_CLASSIC_BEST_STREAK), 0)
    fun getClassicWins(): Int = prefs.getInt(key(KEY_CLASSIC_WINS), 0)
    fun getClassicLosses(): Int = prefs.getInt(key(KEY_CLASSIC_LOSSES), 0)
    fun getClassicGamesPlayed(): Int = getClassicWins() + getClassicLosses()

    fun getClassicWinRate(): Int {
        val played = getClassicGamesPlayed()
        return if (played == 0) 0 else (getClassicWins() * 100) / played
    }

    fun recordClassicWin(guessCount: Int) {
        val newStreak = getClassicStreak() + 1
        val best = maxOf(newStreak, getBestClassicStreak())
        prefs.edit()
            .putInt(key(KEY_CLASSIC_STREAK), newStreak)
            .putInt(key(KEY_CLASSIC_BEST_STREAK), best)
            .putInt(key(KEY_CLASSIC_WINS), getClassicWins() + 1)
            .apply()
        recordGuessDistribution(guessCount)
        awardClassicXp(guessCount)
        firebaseStatsRepository.syncStats(this)
    }

    fun recordClassicLoss() {
        prefs.edit()
            .putInt(key(KEY_CLASSIC_STREAK), 0)
            .putInt(key(KEY_CLASSIC_LOSSES), getClassicLosses() + 1)
            .apply()
        awardClassicXp(0)
        firebaseStatsRepository.syncStats(this)
    }

    fun getLastDailyPlayedDate(): String? =
        prefs.getString(key(KEY_DAILY_PLAYED_DATE), null)

    fun getDailyWins(): Int = prefs.getInt(key(KEY_DAILY_WINS), 0)
    fun getDailyLosses(): Int = prefs.getInt(key(KEY_DAILY_LOSSES), 0)
    fun getDailyGamesPlayed(): Int = getDailyWins() + getDailyLosses()

    fun hasPlayedDailyToday(): Boolean {
        val today = LocalDate.now().toString()
        return prefs.getString(key(KEY_DAILY_PLAYED_DATE), "") == today
    }

    fun markDailyPlayedToday(won: Boolean) {
        val today = LocalDate.now().toString()
        prefs.edit().putString(key(KEY_DAILY_PLAYED_DATE), today).apply()
        firebaseStatsRepository.syncStats(this)
    }

    fun wasLastDailyWon(): Boolean =
        prefs.getBoolean(key("daily_last_won"), false)

    fun recordDailyResult(won: Boolean, guessCount: Int = 0) {
        if (won) prefs.edit().putInt(key(KEY_DAILY_WINS), getDailyWins() + 1).apply()
        else prefs.edit().putInt(key(KEY_DAILY_LOSSES), getDailyLosses() + 1).apply()
        awardDailyXp(if (won) guessCount else 0)
        firebaseStatsRepository.syncStats(this)
    }

    fun getGuessDistribution(attempt: Int): Int {
        require(attempt in 1..6) { "Attempt must be between 1 and 6" }
        return prefs.getInt(key(KEY_GUESS_DIST_PREFIX + attempt), 0)
    }

    fun recordGuessDistribution(guessCount: Int) {
        if (guessCount !in 1..MAX_GUESSES) return
        val statKey = key(KEY_GUESS_DIST_PREFIX + guessCount)
        prefs.edit().putInt(statKey, prefs.getInt(statKey, 0) + 1).apply()
    }

    fun getAllGuessDistribution(): List<Int> =
        (1..MAX_GUESSES).map { prefs.getInt(key(KEY_GUESS_DIST_PREFIX + it), 0) }

    fun getStoredCoins(): Int = prefs.getInt(key("stored_coins"), 0)

    fun setStoredCoins(value: Int) {
        prefs.edit().putInt(key("stored_coins"), value).apply()
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
            .putInt(key(KEY_CLASSIC_WINS), classicWins)
            .putInt(key(KEY_CLASSIC_LOSSES), classicLosses)
            .putInt(key("classic_games_played"), classicGamesPlayed)
            .putInt(key(KEY_CLASSIC_STREAK), classicStreak)
            .putInt(key(KEY_CLASSIC_BEST_STREAK), bestClassicStreak)
            .putInt(key("daily_games_played"), dailyGamesPlayed)
            .putInt(key(KEY_DAILY_WINS), dailyWins)
            .putInt(key(KEY_DAILY_LOSSES), dailyLosses)
            .putBoolean(key("daily_last_won"), lastDailyWon)
            .putInt(key("stored_coins"), storedCoins)
            .putInt(key(KEY_TOTAL_XP), xp)

        if (lastDailyPlayedDate != null) {
            editor.putString(key(KEY_DAILY_PLAYED_DATE), lastDailyPlayedDate)
        } else {
            editor.remove(key(KEY_DAILY_PLAYED_DATE))
        }

        for (i in 1..6) {
            editor.putInt(
                key(KEY_GUESS_DIST_PREFIX + i),
                guessDistribution.getOrElse(i - 1) { 0 }
            )
        }

        editor.apply()
    }

    fun resetClassicStats() {
        val editor = prefs.edit()
        editor.remove(key(KEY_CLASSIC_STREAK))
        editor.remove(key(KEY_CLASSIC_BEST_STREAK))
        editor.remove(key("classic_games_played"))
        editor.remove(key(KEY_CLASSIC_WINS))
        editor.remove(key(KEY_CLASSIC_LOSSES))
        for (i in 1..6) {
            editor.remove(key(KEY_GUESS_DIST_PREFIX + i))
        }
        editor.apply()

        firebaseStatsRepository.syncStats(this)
    }

    fun resetDailyStats() {
        prefs.edit()
            .remove(key(KEY_DAILY_PLAYED_DATE))
            .remove(key("daily_last_won"))
            .remove(key("daily_games_played"))
            .remove(key(KEY_DAILY_WINS))
            .remove(key(KEY_DAILY_LOSSES))
            .apply()

        firebaseStatsRepository.syncStats(this)
    }

    fun resetAllStats() {
        val editor = prefs.edit()

        editor.remove(key(KEY_CLASSIC_STREAK))
        editor.remove(key(KEY_CLASSIC_BEST_STREAK))
        editor.remove(key("classic_games_played"))
        editor.remove(key(KEY_CLASSIC_WINS))
        editor.remove(key(KEY_CLASSIC_LOSSES))

        for (i in 1..6) {
            editor.remove(key(KEY_GUESS_DIST_PREFIX + i))
        }

        editor.remove(key(KEY_DAILY_PLAYED_DATE))
        editor.remove(key("daily_last_won"))
        editor.remove(key("daily_games_played"))
        editor.remove(key(KEY_DAILY_WINS))
        editor.remove(key(KEY_DAILY_LOSSES))

        editor.apply()

        firebaseStatsRepository.syncStats(this)
    }
}
