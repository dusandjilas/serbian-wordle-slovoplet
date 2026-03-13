package com.example.rma

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class CoinRepository(context: Context) {

    private val prefs = context.getSharedPreferences("game_prefs", Context.MODE_PRIVATE)
    private val db    = FirebaseFirestore.getInstance()
    private val auth  = FirebaseAuth.getInstance()

    companion object {
        private const val KEY   = "coins"
        private const val TAG   = "CoinRepository"
        private const val FB_KEY = "storedCoins"
    }

    fun getLocal(): Int = prefs.getInt(KEY, 0)

    private fun setLocal(amount: Int) {
        prefs.edit().putInt(KEY, amount).commit()
    }

    fun load(onResult: (Int) -> Unit) {
        val localVal = getLocal()
        onResult(localVal)

        val user = auth.currentUser
        if (user == null) {
            Log.d(TAG, "load: no Firebase user, using local=$localVal")
            return
        }

        Log.d(TAG, "load: fetching from Firestore for uid=${user.uid}")
        db.collection("users").document(user.uid)
            .get()
            .addOnSuccessListener { snap ->
                val remote = snap.getLong(FB_KEY)?.toInt()
                Log.d(TAG, "load: remote=$remote local=$localVal")
                if (remote != null && remote > localVal) {
                    setLocal(remote)
                    onResult(remote)
                } else if (remote != null && remote < localVal) {
                    Log.d(TAG, "load: local > remote, pushing local to Firestore")
                    pushToFirestore(localVal)
                }
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "load: Firestore failed, keeping local=$localVal", e)
            }
    }

    fun set(amount: Int) {
        setLocal(amount)
        pushToFirestore(amount)
    }

    fun add(amount: Int): Int {
        val newTotal = getLocal() + amount
        setLocal(newTotal)
        pushToFirestore(newTotal)
        return newTotal
    }

    fun spend(amount: Int): Boolean {
        val current = getLocal()
        if (current < amount) return false
        val newTotal = current - amount
        setLocal(newTotal)
        pushToFirestore(newTotal)
        return true
    }

    private fun pushToFirestore(coins: Int) {
        val user = auth.currentUser
        if (user == null) {
            Log.d(TAG, "pushToFirestore: skipped — no Firebase user")
            return
        }
        db.collection("users").document(user.uid)
            .set(mapOf(FB_KEY to coins), SetOptions.merge())
            .addOnSuccessListener {
                Log.d(TAG, "pushToFirestore: coins=$coins saved for uid=${user.uid}")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "pushToFirestore: failed for uid=${user.uid}", e)
            }
    }
}
