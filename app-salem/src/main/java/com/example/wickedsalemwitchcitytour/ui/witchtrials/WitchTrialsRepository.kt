/*
 * WickedSalemWitchCityTour v1.5 — Phase 9X (Session 127)
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 */

package com.example.wickedsalemwitchcitytour.ui.witchtrials

import com.example.wickedsalemwitchcitytour.content.dao.WitchTrialsArticleDao
import com.example.wickedsalemwitchcitytour.content.dao.WitchTrialsNewspaperDao
import com.example.wickedsalemwitchcitytour.content.dao.WitchTrialsNpcBioDao
import com.example.wickedsalemwitchcitytour.content.model.WitchTrialsArticle
import com.example.wickedsalemwitchcitytour.content.model.WitchTrialsNewspaper
import com.example.wickedsalemwitchcitytour.content.model.WitchTrialsNpcBio
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Read-side facade for the three Witch Trials Room tables. Phase 1 ships
 * just enough to power the menu shell; Phases 3-5 wire detail screens to
 * these methods.
 */
@Singleton
class WitchTrialsRepository @Inject constructor(
    private val articleDao: WitchTrialsArticleDao,
    private val npcBioDao: WitchTrialsNpcBioDao,
    private val newspaperDao: WitchTrialsNewspaperDao
) {

    suspend fun getAllArticles(): List<WitchTrialsArticle> = articleDao.findAll()
    suspend fun getArticleById(id: String): WitchTrialsArticle? = articleDao.findById(id)
    suspend fun getArticleByOrder(order: Int): WitchTrialsArticle? = articleDao.findByOrder(order)
    suspend fun getArticleCount(): Int = articleDao.count()

    suspend fun getAllBios(): List<WitchTrialsNpcBio> = npcBioDao.findAll()
    suspend fun getBioById(id: String): WitchTrialsNpcBio? = npcBioDao.findById(id)
    suspend fun getBiosByFaction(faction: String): List<WitchTrialsNpcBio> = npcBioDao.findByFaction(faction)
    suspend fun getBioCount(): Int = npcBioDao.count()

    suspend fun getAllNewspapers(): List<WitchTrialsNewspaper> = newspaperDao.findAll()
    suspend fun getNewspaperById(id: String): WitchTrialsNewspaper? = newspaperDao.findById(id)
    suspend fun getNewspaperByDate(date: String): WitchTrialsNewspaper? = newspaperDao.findByDate(date)
    suspend fun getNewspapersByPhase(phase: Int): List<WitchTrialsNewspaper> = newspaperDao.findByPhase(phase)
    suspend fun getNewspaperCount(): Int = newspaperDao.count()
}
