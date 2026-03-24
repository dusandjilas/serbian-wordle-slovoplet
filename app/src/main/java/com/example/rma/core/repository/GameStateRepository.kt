package com.example.rma

import android.content.Context
import com.example.rma.game.GameMode
import com.example.rma.game.GuessResult
import com.example.rma.game.LetterState
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate


class GameStateRepository(context: Context) {

    private val prefs = context.getSharedPreferences("game_state", Context.MODE_PRIVATE)

    private fun key(mode: GameMode, suffix: String) = "${mode.name.lowercase()}_$suffix"


    fun save(mode: GameMode, targetWord: String, guesses: List<GuessResult>, currentInput: String = "") {
        val guessesJson = JSONArray().apply {
            guesses.forEach { gr ->
                put(JSONObject().apply {
                    put("guess", gr.guess)
                    put("isCorrect", gr.isCorrect)
                    put("states", JSONArray().apply { gr.letterStates.forEach { put(it.name) } })
                })
            }
        }
        prefs.edit()
            .putString(key(mode, "target"),  targetWord)
            .putString(key(mode, "guesses"), guessesJson.toString())
            .putString(key(mode, "input"),   currentInput)
            .putString(key(mode, "date"),    LocalDate.now().toString())
            .apply()
    }


    fun clearClassic() {
        GameMode.values().filter { it == GameMode.CLASSIC }.forEach { clear(it) }
    }

    private fun clear(mode: GameMode) {
        prefs.edit()
            .remove(key(mode, "target"))
            .remove(key(mode, "guesses"))
            .remove(key(mode, "input"))
            .remove(key(mode, "date"))
            .apply()
    }



    fun load(mode: GameMode): SavedGameState? {
        val target = prefs.getString(key(mode, "target"), null) ?: return null

        if (mode == GameMode.DAILY) {
            val savedDate = prefs.getString(key(mode, "date"), null) ?: return null
            if (savedDate != LocalDate.now().toString()) { clear(GameMode.DAILY); return null }
        }

        val guessesJson = prefs.getString(key(mode, "guesses"), null) ?: return null
        val input       = prefs.getString(key(mode, "input"), "") ?: ""

        return try {
            val arr = JSONArray(guessesJson)
            val guesses = (0 until arr.length()).map { i ->
                val obj    = arr.getJSONObject(i)
                val states = obj.getJSONArray("states")
                GuessResult(
                    guess        = obj.getString("guess"),
                    isCorrect    = obj.getBoolean("isCorrect"),
                    letterStates = (0 until states.length()).map { LetterState.valueOf(states.getString(it)) }
                )
            }
            SavedGameState(target = target, guesses = guesses, currentInput = input)
        } catch (e: Exception) {
            null
        }
    }
}

data class SavedGameState(
    val target: String,
    val guesses: List<GuessResult>,
    val currentInput: String = ""
)
