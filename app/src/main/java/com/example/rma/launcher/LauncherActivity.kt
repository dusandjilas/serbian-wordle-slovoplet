package com.example.rma.launcher

import com.example.rma.auth.SignInActivity
import com.example.rma.core.managers.AppFlowPrefs
import com.example.rma.game.ui.SlovopletIgra
import com.example.rma.main.MainActivity

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class LauncherActivity : AppCompatActivity() {

    private lateinit var firebaseAuth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!AppFlowPrefs.isFirstGameFinished(this)) {
            startActivity(
                Intent(this, SlovopletIgra::class.java)
                    .putExtra("game_mode", "CLASSIC")
                    .putExtra("first_time_flow", true)
            )
            finish()
            return
        }

        firebaseAuth = FirebaseAuth.getInstance()
        val currentUser = firebaseAuth.currentUser

        if (currentUser != null) {
            startActivity(Intent(this, MainActivity::class.java))
        } else {
            startActivity(Intent(this, SignInActivity::class.java))
        }
        finish()
    }
}
