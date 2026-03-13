package com.example.rma.game

data class GuessResult(
    val guess: String,
    val letterStates: List<LetterState>,
    val isCorrect: Boolean
)
