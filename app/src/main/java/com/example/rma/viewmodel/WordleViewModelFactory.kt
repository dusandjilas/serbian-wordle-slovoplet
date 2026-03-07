package com.example.rma.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.rma.game.WordRepository

class WordleViewModelFactory(private val repository: WordRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WordleViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return WordleViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}