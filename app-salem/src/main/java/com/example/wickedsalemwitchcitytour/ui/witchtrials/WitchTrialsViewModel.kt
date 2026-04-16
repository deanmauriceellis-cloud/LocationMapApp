/*
 * WickedSalemWitchCityTour v1.5 — Phase 9X (Session 127)
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 */

package com.example.wickedsalemwitchcitytour.ui.witchtrials

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wickedsalemwitchcitytour.content.model.SalemPoi
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
 * Phase 9X.7 (S133) adds cross-linking indexes for entity auto-detection.
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
    suspend fun getNewspapersByMonthDay(month: Int, day: Int): List<WitchTrialsNewspaper> =
        repository.getNewspapersByMonthDay(month, day)

    suspend fun getAllBios(): List<WitchTrialsNpcBio> = repository.getAllBios()
    suspend fun getBioById(id: String): WitchTrialsNpcBio? = repository.getBioById(id)

    // ── Historic Sites (S134) ─────────────────────────────────────────
    suspend fun getHistoricSites(): List<SalemPoi> = repository.getHistoricSites()

    // ── Phase 9X.7 — Cross-linking indexes (S133) ─────────────────────

    /** NPC id → bio. Built lazily on first access. */
    var bioIndex: Map<String, WitchTrialsNpcBio> = emptyMap()
        private set

    /** NPC name → bio, sorted longest-first for greedy matching. */
    var nameIndex: List<Pair<String, WitchTrialsNpcBio>> = emptyList()
        private set

    /** True once indexes are built. */
    var linkIndexesReady: Boolean = false
        private set

    /** Cached bios list for cross-link navigation. */
    private var cachedBios: List<WitchTrialsNpcBio> = emptyList()

    /**
     * Ensure cross-linking indexes are built. Call before rendering linked
     * text. Idempotent — builds only once.
     */
    suspend fun ensureLinkIndexes() {
        if (linkIndexesReady) return
        cachedBios = repository.getAllBios()
        val (bi, ni) = buildLinkIndexes(cachedBios)
        bioIndex = bi
        nameIndex = ni
        linkIndexesReady = true
    }
}
