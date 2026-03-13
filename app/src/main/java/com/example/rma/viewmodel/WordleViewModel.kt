package com.example.rma.viewmodel

import androidx.lifecycle.ViewModel
import com.example.rma.game.GameMode
import com.example.rma.game.GuessResult
import com.example.rma.game.LetterState
import com.example.rma.game.WordRepository
import com.example.rma.game.WordleEngine

class WordleViewModel(private val repository: WordRepository) : ViewModel() {

    private val engine = WordleEngine(repository)

    val maxAttempts: Int get() = engine.maxAttempts
    val wordLength: Int  get() = engine.wordLength
    val guesses: List<GuessResult> get() = engine.getGuesses()
    val targetWord: String get() = engine.targetWord
    val hasWon: Boolean get() = engine.hasWon()
    val hasLost: Boolean get() = engine.hasLost()
    val gameMode: GameMode get() = engine.getMode()

    fun setMode(mode: GameMode) = engine.setMode(mode)
    fun submitGuess(guess: String): GuessResult? = engine.submitGuess(guess)
    fun checkGuess(word: String): Boolean = engine.checkGuess(word)
    fun reset() = engine.reset()

    fun restoreState(targetWord: String, savedGuesses: List<GuessResult>) {
        engine.restoreState(targetWord, savedGuesses)
    }
}
