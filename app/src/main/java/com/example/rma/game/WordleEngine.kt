package com.example.rma.game

import android.os.Build
import androidx.annotation.RequiresApi
import java.time.LocalDate

enum class GameMode {
    CLASSIC,
    DAILY
}

class WordleEngine(
    private val repository: WordRepository,
    private var gameMode: GameMode = GameMode.CLASSIC
) {

    val maxAttempts = 6
    val wordLength = 5

    var targetWord: String = createWordForMode()
        private set

    private val guesses = mutableListOf<GuessResult>()

    private fun createWordForMode(): String {
        return when (gameMode) {
            GameMode.CLASSIC -> repository.getRandomWord()
            GameMode.DAILY -> repository.getDailyWord(LocalDate.now())
        }
    }

    fun setMode(newMode: GameMode) {
        gameMode = newMode
        reset()
    }

    fun getMode(): GameMode = gameMode

    fun checkGuess(word: String): Boolean {
        return word.length == wordLength && repository.isValidWord(word)
    }

    fun submitGuess(guess: String): GuessResult? {
        if (guess.length != wordLength) return null
        if (!repository.isValidWord(guess)) return null
        if (hasWon() || hasLost()) return null

        val letterStates = MutableList(wordLength) { LetterState.ABSENT }
        val targetCharCounts = targetWord.groupingBy { it }.eachCount().toMutableMap()

        for (i in 0 until wordLength) {
            if (guess[i] == targetWord[i]) {
                letterStates[i] = LetterState.CORRECT
                targetCharCounts[guess[i]] = targetCharCounts[guess[i]]!! - 1
            }
        }

        for (i in 0 until wordLength) {
            if (letterStates[i] == LetterState.ABSENT) {
                val char = guess[i]
                if (targetCharCounts[char]?.let { it > 0 } == true) {
                    letterStates[i] = LetterState.PRESENT
                    targetCharCounts[char] = targetCharCounts[char]!! - 1
                }
            }
        }

        val result = GuessResult(
            guess = guess,
            letterStates = letterStates,
            isCorrect = guess == targetWord
        )

        guesses.add(result)
        return result
    }

    fun getGuesses(): List<GuessResult> = guesses
    fun hasWon(): Boolean = guesses.lastOrNull()?.isCorrect == true
    fun hasLost(): Boolean = guesses.size >= maxAttempts && !hasWon()

    fun reset() {
        targetWord = createWordForMode()
        guesses.clear()
    }
}