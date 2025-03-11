package com.example.rma

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {

    private lateinit var firebaseAuth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        firebaseAuth = FirebaseAuth.getInstance()


        val trenutniKorisnik = firebaseAuth.currentUser
        val emailTextView = findViewById<TextView>(R.id.emailTextView)
        if (trenutniKorisnik != null) {

            emailTextView.text = "Добродошли: ${trenutniKorisnik.email}"
        } else {
            emailTextView.text = "Нисте улоговани!"
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val dugmeLogIn = findViewById<Button>(R.id.buttonIgraj)
        dugmeLogIn.setOnClickListener {
            val intent = Intent(this, Slovoplet_igra::class.java)
            startActivity(intent)
        }

        val dugmeKako = findViewById<Button>(R.id.buttonLogika)
        dugmeKako.setOnClickListener {

            dijalogObjasnjenjeMain()
        }

        val dugmeSignIn = findViewById<Button>(R.id.buttonSignOut)
        dugmeSignIn.setOnClickListener {
            firebaseAuth.signOut()
            val intent = Intent(this, SignInActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
    private fun dijalogObjasnjenjeMain() {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_objasnjenje_main)
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog.setCancelable(true)

        val dialogBtnCancel = dialog.findViewById<Button>(R.id.buttonIskljuci)
        dialogBtnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }
}

