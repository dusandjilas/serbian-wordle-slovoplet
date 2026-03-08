package com.example.rma

import android.content.Intent
import android.os.Bundle
import android.widget.Button
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
            // User already signed in → go to MainActivity
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        } else {
            // User not signed in → show a simple launcher screen
            setContentView(R.layout.activity_launcher)

            val signInButton = findViewById<Button>(R.id.buttonSignIn)
            val guestButton = findViewById<Button>(R.id.buttonGuest)

            signInButton.setOnClickListener {
                val signInDialog = SignInBottomSheet()
                signInDialog.show(supportFragmentManager, "SignInBottomSheet")
            }

            guestButton.setOnClickListener {
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
        }
    }
}
