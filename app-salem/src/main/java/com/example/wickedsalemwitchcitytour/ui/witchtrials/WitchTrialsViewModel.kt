/*
 * WickedSalemWitchCityTour v1.5 — Phase 9X (Session 127)
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 */

package com.example.wickedsalemwitchcitytour.ui.witchtrials

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wickedsalemwitchcitytour.content.model.WitchTrialsArticle
import com.example.wickedsalemwitchcitytour.content.model.WitchTrialsNewspaper
import com.example.wickedsalemwitchcitytour.content.model.WitchTrialsNpcBio
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel backing the Salem Witch Trials top-level feature.
 *
 * Phase 1 ships counts + lazy load methods. Real list/detail flows
 * land in Phase 3 (history tiles), Phase 4 (newspapers), Phase 5 (people).
 */
@HiltViewModel
class WitchTrialsViewModel @Inject constructor(
    private val repository: WitchTrialsRepository
) : ViewModel() {

    /** Counts shown on the 3-panel menu cards (article / newspaper / bio totals). */
    data class MenuCounts(
        val articles: Int = 0,
        val newspapers: Int = 0,
        val bios: Int = 0
    )

    private val _menuCounts = MutableStateFlow(MenuCounts())
    val menuCounts: StateFlow<MenuCounts> = _menuCounts.asStateFlow()

    fun loadMenuCounts() {
        viewModelScope.launch {
            _menuCounts.value = MenuCounts(
                articles = repository.getArticleCount(),
                newspapers = repository.getNewspaperCount(),
                bios = repository.getBioCount()
            )
        }
    }

    suspend fun getAllArticles(): List<WitchTrialsArticle> = repository.getAllArticles()
    suspend fun getArticleByOrder(order: Int): WitchTrialsArticle? = repository.getArticleByOrder(order)

    suspend fun getAllNewspapers(): List<WitchTrialsNewspaper> = repository.getAllNewspapers()
    suspend fun getNewspaperByDate(date: String): WitchTrialsNewspaper? = repository.getNewspaperByDate(date)

    suspend fun getAllBios(): List<WitchTrialsNpcBio> = repository.getAllBios()
    suspend fun getBioById(id: String): WitchTrialsNpcBio? = repository.getBioById(id)
}
