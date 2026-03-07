package com.example.rma

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

/**
 * Single source of truth for coins.
 *
 * Design rules:
 * 1. Every mutation writes SharedPrefs SYNCHRONOUSLY (commit, not apply)
 *    so the value survives process death even if Firestore hasn't responded.
 * 2. load() takes the MAX of local and remote so a slow Firebase response
 *    never silently zeroes out coins earned in the same session.
 * 3. pushToFirestore() is best-effort — failures are logged but never block the UI.
 */
class CoinRepository(context: Context) {

    // Use the same prefs file + key that the old CoinManager used so
    // existing installs don't lose their locally-stored balance.
    private val prefs = context.getSharedPreferences("game_prefs", Context.MODE_PRIVATE)
    private val db    = FirebaseFirestore.getInstance()
    private val auth  = FirebaseAuth.getInstance()

    companion object {
        private const val KEY   = "coins"
        private const val TAG   = "CoinRepository"
        private const val FB_KEY = "storedCoins"
    }

    // ── Local (synchronous) ──────────────────────────────────────────────────

    fun getLocal(): Int = prefs.getInt(KEY, 0)

    /** commit() not apply() — survives immediate process kill */
    private fun setLocal(amount: Int) {
        prefs.edit().putInt(KEY, amount).commit()
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Load from Firestore and reconcile with local.
     * Always calls [onResult] with the best available value immediately,
     * then again if Firestore returns a higher value.
     */
    fun load(onResult: (Int) -> Unit) {
        // Emit local value immediately so the UI never shows 0 while waiting
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
                    // Remote is higher (e.g. purchased on another device) — adopt it
                    setLocal(remote)
                    onResult(remote)
                } else if (remote != null && remote < localVal) {
                    // Local is higher (offline spend/earn) — push local up to Firebase
                    Log.d(TAG, "load: local > remote, pushing local to Firestore")
                    pushToFirestore(localVal)
                }
                // If equal, nothing to do
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "load: Firestore failed, keeping local=$localVal", e)
            }
    }

    /** Set coins to an absolute value and persist everywhere. */
    fun set(amount: Int) {
        setLocal(amount)
        pushToFirestore(amount)
    }

    /** Add [amount] coins, persist, return new total. */
    fun add(amount: Int): Int {
        val newTotal = getLocal() + amount
        setLocal(newTotal)
        pushToFirestore(newTotal)
        return newTotal
    }

    /**
     * Spend [amount] if sufficient balance exists.
     * Returns true on success, false if insufficient funds.
     */
    fun spend(amount: Int): Boolean {
        val current = getLocal()
        if (current < amount) return false
        val newTotal = current - amount
        setLocal(newTotal)
        pushToFirestore(newTotal)
        return true
    }

    // ── Firestore push (best-effort) ──────────────────────────────────────────

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
                // Local value is already written — will reconcile on next load()
            }
    }
}
