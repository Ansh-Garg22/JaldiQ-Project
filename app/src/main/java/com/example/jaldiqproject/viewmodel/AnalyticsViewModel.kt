package com.example.jaldiqproject.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.jaldiqproject.data.ShopRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * ViewModel for the Owner Analytics screen.
 * Holds the selected date and dynamically observes the analytics counter
 * for that date via a callbackFlow from ShopRepository.
 */
@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val repository: ShopRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val shopId: String = savedStateHandle.get<String>("shopId") ?: ""

    private val _selectedDate = MutableStateFlow(todayString())
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    private val _customersServed = MutableStateFlow(0)
    val customersServed: StateFlow<Int> = _customersServed.asStateFlow()

    private var analyticsJob: Job? = null

    init {
        observeAnalyticsForDate(_selectedDate.value)
    }

    /**
     * Called when the user picks a new date from the DatePicker.
     * Cancels the old listener and starts a new one for the new date.
     */
    fun onDateSelected(dateString: String) {
        _selectedDate.value = dateString
        observeAnalyticsForDate(dateString)
    }

    /**
     * Called when the user picks a date from DatePicker using epoch millis.
     */
    fun onDateSelectedMillis(millis: Long) {
        val formatted = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(millis))
        onDateSelected(formatted)
    }

    private fun observeAnalyticsForDate(date: String) {
        analyticsJob?.cancel()
        analyticsJob = viewModelScope.launch {
            repository.observeAnalyticsForDate(shopId, date).collect { count ->
                _customersServed.value = count
            }
        }
    }

    private fun todayString(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
}
