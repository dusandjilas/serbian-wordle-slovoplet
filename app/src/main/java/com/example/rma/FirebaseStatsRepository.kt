package com.example.rma

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class FirebaseStatsRepository {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    fun syncStats(profileManager: GameProfileManager) {
        val user = auth.currentUser ?: return

        val data = hashMapOf(
            "uid" to user.uid,
            "displayName" to (user.displayName ?: user.email ?: "Guest"),
            "email" to user.email,

            "classicWins" to profileManager.getClassicWins(),
            "classicLosses" to profileManager.getClassicLosses(),
            "classicGamesPlayed" to profileManager.getClassicGamesPlayed(),
            "classicStreak" to profileManager.getClassicStreak(),
            "bestClassicStreak" to profileManager.getBestClassicStreak(),
            "winRate" to profileManager.getClassicWinRate(),
            "guessDistribution" to profileManager.getAllGuessDistribution(),

            "dailyGamesPlayed" to profileManager.getDailyGamesPlayed(),
            "dailyWins" to profileManager.getDailyWins(),
            "dailyLosses" to profileManager.getDailyLosses(),
            "lastDailyPlayedDate" to profileManager.getLastDailyPlayedDate(),
            "lastDailyWon" to profileManager.wasLastDailyWon(),

            "storedCoins" to profileManager.getStoredCoins(),

            "xp" to profileManager.getXp(),
            "level" to profileManager.getLevel(),
        )

        db.collection("users")
            .document(user.uid)
            .set(data)
            .addOnSuccessListener {
                Log.d("FirestoreStats", "Stats synced for uid=${user.uid}")
            }
            .addOnFailureListener { e ->
                Log.e("FirestoreStats", "Failed to sync stats", e)
            }
    }

    fun loadStats(
        onSuccess: (Map<String, Any>) -> Unit,
        onNoData: () -> Unit,
        onFailure: (Exception) -> Unit = {}
    ) {
        val user = auth.currentUser ?: return

        db.collection("users")
            .document(user.uid)
            .get()
            .addOnSuccessListener { snapshot ->
                val data = snapshot.data
                if (data != null) {
                    Log.d("FirestoreStats", "Loaded stats for uid=${user.uid}")
                    onSuccess(data)
                } else {
                    Log.d("FirestoreStats", "No remote stats found for uid=${user.uid}")
                    onNoData()
                }
            }
            .addOnFailureListener { e ->
                Log.e("FirestoreStats", "Failed to load stats", e)
                onFailure(e)
            }
    }
}