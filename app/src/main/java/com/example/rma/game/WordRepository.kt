package com.example.rma.game

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import com.example.rma.R
import java.lang.Math.floorMod
import java.time.LocalDate
import java.time.temporal.ChronoUnit


class WordRepository(private val context: Context) {
    val words: List<String> = loadWordsFromCsv()

    fun getRandomWord(): String = words.random()

    fun getDailyWord(date: LocalDate = LocalDate.now()): String {
        if (words.isEmpty()) error("Word list is empty")

        val baseDate = LocalDate.of(2026, 1, 1)
        val daysBetween = ChronoUnit.DAYS.between(baseDate, date).toInt()
        val index = floorMod(daysBetween, words.size)
        return words[index]
    }

    fun isValidWord(word: String): Boolean = word.uppercase() in words

    private fun loadWordsFromCsv(): List<String> {
        val reci = mutableListOf<String>()
        context.resources.openRawResource(R.raw.petslova).bufferedReader().useLines { lines ->
            lines.forEach { reci.add(it.trim().uppercase()) }
        }
        return reci
    }
}
