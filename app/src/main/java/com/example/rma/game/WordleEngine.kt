package com.example.rma.game

import java.time.LocalDate

enum class GameMode { CLASSIC, DAILY }

class WordleEngine(
    private val repository: WordRepository,
    private var gameMode: GameMode = GameMode.CLASSIC
) {
    val maxAttempts = 6
    val wordLength  = 5

    var targetWord: String = createWordForMode()
        private set

    private val guesses = mutableListOf<GuessResult>()

    private fun createWordForMode() = when (gameMode) {
        GameMode.CLASSIC -> repository.getRandomWord()
        GameMode.DAILY   -> repository.getDailyWord(LocalDate.now())
    }

    fun setMode(newMode: GameMode) {
        gameMode = newMode
        reset()
    }

    fun getMode(): GameMode = gameMode

    fun checkGuess(word: String) = word.length == wordLength && repository.isValidWord(word)

    fun submitGuess(guess: String): GuessResult? {
        if (guess.length != wordLength) return null
        if (!repository.isValidWord(guess)) return null
        if (hasWon() || hasLost()) return null

        val letterStates      = MutableList(wordLength) { LetterState.ABSENT }
        val targetCharCounts  = targetWord.groupingBy { it }.eachCount().toMutableMap()

        for (i in 0 until wordLength) {
            if (guess[i] == targetWord[i]) {
                letterStates[i] = LetterState.CORRECT
                targetCharCounts[guess[i]] = targetCharCounts[guess[i]]!! - 1
            }
        }
        for (i in 0 until wordLength) {
            if (letterStates[i] == LetterState.ABSENT) {
                val ch = guess[i]
                if ((targetCharCounts[ch] ?: 0) > 0) {
                    letterStates[i] = LetterState.PRESENT
                    targetCharCounts[ch] = targetCharCounts[ch]!! - 1
                }
            }
        }

        return GuessResult(guess = guess, letterStates = letterStates, isCorrect = guess == targetWord)
            .also { guesses.add(it) }
    }

    /**
     * Restores engine state from a saved game.
     * Sets [targetWord] directly and injects [savedGuesses] bypassing validation.
     */
    fun restoreState(savedTarget: String, savedGuesses: List<GuessResult>) {
        targetWord = savedTarget
        guesses.clear()
        guesses.addAll(savedGuesses)
    }

    fun getGuesses(): List<GuessResult> = guesses
    fun hasWon(): Boolean = guesses.lastOrNull()?.isCorrect == true
    fun hasLost(): Boolean = guesses.size >= maxAttempts && !hasWon()

    fun reset() {
        targetWord = createWordForMode()
        guesses.clear()
    }
}
