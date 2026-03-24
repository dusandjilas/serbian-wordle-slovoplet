package com.example.rma

import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import kotlin.random.Random

object PlayerNameManager {

    private val adjectives = listOf(
        "Brzi", "Spretni", "Mudri", "Zlatni", "Divlji", "Tihi", "Vatreni", "Hrabri", "Ludi", "Ledeni"
    )
    private val nouns = listOf(
        "Vuk", "Soko", "Lisac", "Jabuka", "Orao", "Ris", "Medved", "Jelen", "Zmaj", "Tigar"
    )

    fun generateRandomName(): String {
        val suffix = Random.nextInt(100, 1000)
        return "${adjectives.random()}${nouns.random()}$suffix"
    }

    fun assignRandomNameIfMissing(
        user: FirebaseUser,
        onDone: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        if (!user.displayName.isNullOrBlank()) {
            onDone()
            return
        }
        val randomName = generateRandomName()
        val request = UserProfileChangeRequest.Builder()
            .setDisplayName(randomName)
            .build()

        user.updateProfile(request)
            .addOnSuccessListener { onDone() }
            .addOnFailureListener { onFailure(it) }
    }
}
