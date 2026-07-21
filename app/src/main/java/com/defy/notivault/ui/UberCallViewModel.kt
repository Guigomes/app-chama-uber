package com.defy.notivault.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.defy.notivault.data.UberCallDao
import com.defy.notivault.data.UberCallEntity
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class UberCallViewModel(
    private val dao: UberCallDao
) : ViewModel() {

    val calls: StateFlow<List<UberCallEntity>> = dao
        .getAllOrderedByRecent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun clearHistory() {
        viewModelScope.launch {
            dao.deleteAll()
        }
    }
}

class UberCallViewModelFactory(
    private val dao: UberCallDao
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(UberCallViewModel::class.java)) {
            return UberCallViewModel(dao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
