/*
 * WickedSalemWitchCityTour v1.0
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.wickedsalemwitchcitytour.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.locationmapapp.util.DebugLogger
import com.example.wickedsalemwitchcitytour.content.SalemContentRepository
import com.example.wickedsalemwitchcitytour.content.model.EventsCalendar
import com.example.wickedsalemwitchcitytour.content.model.TimelineEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@Suppress("unused")
private const val MODULE_ID = "(C) Dean Maurice Ellis, 2026 - Module EventsViewModel.kt"

@HiltViewModel
class EventsViewModel @Inject constructor(
    private val repository: SalemContentRepository
) : ViewModel() {

    private val TAG = "EventsVM"
    private val isoFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val monthDayFormat = SimpleDateFormat("MM-dd", Locale.US)

    private val _todayEvents = MutableStateFlow<List<EventsCalendar>>(emptyList())
    val todayEvents: StateFlow<List<EventsCalendar>> = _todayEvents.asStateFlow()

    private val _upcomingEvents = MutableStateFlow<List<EventsCalendar>>(emptyList())
    val upcomingEvents: StateFlow<List<EventsCalendar>> = _upcomingEvents.asStateFlow()

    private val _monthEvents = MutableStateFlow<List<EventsCalendar>>(emptyList())
    val monthEvents: StateFlow<List<EventsCalendar>> = _monthEvents.asStateFlow()

    private val _allEvents = MutableStateFlow<List<EventsCalendar>>(emptyList())
    val allEvents: StateFlow<List<EventsCalendar>> = _allEvents.asStateFlow()

    /** "On this date in 1692" — timeline events matching today's month-day. */
    private val _onThisDay = MutableStateFlow<List<TimelineEvent>>(emptyList())
    val onThisDay: StateFlow<List<TimelineEvent>> = _onThisDay.asStateFlow()

    /** Whether we're currently in October (peak Salem season). */
    val isOctober: Boolean
        get() = Calendar.getInstance().get(Calendar.MONTH) == Calendar.OCTOBER

    /** Whether we're in tourist season (April 1 - mid November). */
    val isTouristSeason: Boolean
        get() {
            val month = Calendar.getInstance().get(Calendar.MONTH)
            return month in Calendar.APRIL..Calendar.OCTOBER
        }

    init {
        loadEvents()
    }

    private fun loadEvents() {
        viewModelScope.launch {
            try {
                val today = isoFormat.format(Date())
                val currentMonth = Calendar.getInstance().get(Calendar.MONTH) + 1 // 1-based
                val monthDay = monthDayFormat.format(Date())

                _todayEvents.value = repository.getActiveEvents(today)
                _upcomingEvents.value = repository.getUpcomingEvents(today)
                _monthEvents.value = repository.getEventsByMonth(currentMonth)
                _allEvents.value = repository.getAllEvents()
                _onThisDay.value = repository.getTimelineByMonthDay(monthDay)

                DebugLogger.i(TAG, "Events loaded: " +
                        "today=${_todayEvents.value.size}, " +
                        "upcoming=${_upcomingEvents.value.size}, " +
                        "month=${_monthEvents.value.size}, " +
                        "onThisDay=${_onThisDay.value.size}")
            } catch (e: Exception) {
                DebugLogger.e(TAG, "Failed to load events: ${e.message}", e)
            }
        }
    }

    fun refreshEvents() = loadEvents()

    /** Filter events by type. */
    fun getEventsByType(type: String): List<EventsCalendar> =
        _allEvents.value.filter { it.eventType == type }

    /** Get events at a specific venue. */
    fun getEventsAtVenue(poiId: String): List<EventsCalendar> =
        _allEvents.value.filter { it.venuePoiId == poiId }

    /** All distinct event types present in the data. */
    fun getEventTypes(): List<String> =
        _allEvents.value.map { it.eventType }.distinct().sorted()
}
