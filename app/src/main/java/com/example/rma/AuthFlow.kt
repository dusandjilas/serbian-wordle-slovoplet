package com.example.rma

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

object AuthFlow {

    fun continueToMain(activity: AppCompatActivity) {
        val profileManager = GameProfileManager(activity)
        val repo = FirebaseStatsRepository()

        repo.loadStats(
            onSuccess = { data ->
                profileManager.importFromRemote(data)
                goToMain(activity)
            },
            onNoData = {
                profileManager.resetAllStats()
                profileManager.setStoredCoins(0)
                repo.syncStats(profileManager)
                goToMain(activity)
            },
            onFailure = {
                Toast.makeText(activity, "Greška pri učitavanju naloga.", Toast.LENGTH_SHORT).show()
                goToMain(activity)
            }
        )
    }

    fun showAuthError(context: Context, message: String?) {
        Toast.makeText(context, message ?: "Prijava nije uspela.", Toast.LENGTH_SHORT).show()
    }

    private fun goToMain(activity: AppCompatActivity) {
        activity.startActivity(Intent(activity, MainActivity::class.java))
        activity.finish()
    }
}
