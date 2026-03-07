package com.example.rma

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.rma.databinding.ActivitySignInBinding
import com.google.firebase.auth.FirebaseAuth

class SignInActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignInBinding
    private lateinit var firebaseAuth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignInBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firebaseAuth = FirebaseAuth.getInstance()

        binding.guessButton.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }

        binding.textView.setOnClickListener {
            val intent = Intent(this, SignUpActivity::class.java)
            startActivity(intent)
        }

        binding.buttonUloguj.setOnClickListener {
            val email = binding.textfieldEmail.text.toString()
            val pass = binding.textfieldSifra.text.toString()

            if (email.isNotEmpty() && pass.isNotEmpty()) {
                firebaseAuth.signInWithEmailAndPassword(email, pass).addOnCompleteListener {
                    if (it.isSuccessful) {
                        val profileManager = GameProfileManager(this)
                        val repo = FirebaseStatsRepository()

                        repo.loadStats(
                            onSuccess = { data ->
                                profileManager.importFromRemote(data)
                                startActivity(Intent(this, MainActivity::class.java))
                                finish()
                            },
                            onNoData = {
                                profileManager.resetAllStats()
                                profileManager.setStoredCoins(0)
                                repo.syncStats(profileManager)
                                startActivity(Intent(this, MainActivity::class.java))
                                finish()
                            },
                            onFailure = {
                                Toast.makeText(this, "Greška pri učitavanju naloga.", Toast.LENGTH_SHORT).show()
                                startActivity(Intent(this, MainActivity::class.java))
                                finish()
                            }
                        )
                    } else {
                        Toast.makeText(this, it.exception.toString(), Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(this, "Не смете оставити празна поља!", Toast.LENGTH_SHORT).show()
            }
        }
    }
}