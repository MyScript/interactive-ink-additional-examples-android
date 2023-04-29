package com.myscript.iink.demo.inksample.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.microsoft.device.ink.InkView
import com.myscript.iink.demo.ink.serialization.json
import com.myscript.iink.demo.ink.serialization.parseJson
import com.myscript.iink.demo.inksample.data.InkRepository
import com.myscript.iink.demo.inksample.data.InkRepositoryImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class InkViewModel(private val repository: InkRepository): ViewModel() {

    private val _strokes: MutableLiveData<List<InkView.Brush>> = MutableLiveData(listOf())
    val strokes: LiveData<List<InkView.Brush>>
        get() = _strokes


    fun clearInk() {
        viewModelScope.launch(Dispatchers.Main) {
            _strokes.value = emptyList()
        }
    }

    fun loadInk() {
        viewModelScope.launch(Dispatchers.Main) {
            val jsonString = withContext(Dispatchers.IO) {
                repository.readInkFromFile()
            }
            jsonString?.let {
                val brushes = parseJson(jsonString)
                _strokes.value = brushes
            } ?: run {
                _strokes.value = listOf()
            }
        }
    }

    fun saveInk() {
        viewModelScope.launch(Dispatchers.Main) {
            withContext(Dispatchers.IO) {
                _strokes.value?.json()?.let { jsonString ->
                    repository.saveInkToFile(jsonString)
                }
            }
        }
    }

    fun addStroke(brush: InkView.Brush) {
        viewModelScope.launch(Dispatchers.Main) {
            _strokes.value = _strokes.value?.plus(brush)
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val application = checkNotNull(this[APPLICATION_KEY])
                InkViewModel(InkRepositoryImpl(application))
            }
        }
    }
}