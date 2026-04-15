/*
 * WickedSalemWitchCityTour v1.5 — Phase 9X (Session 127, hydrated S129)
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 */

package com.example.wickedsalemwitchcitytour.ui.witchtrials

import android.content.Context
import com.example.locationmapapp.util.DebugLogger
import com.example.wickedsalemwitchcitytour.content.dao.WitchTrialsArticleDao
import com.example.wickedsalemwitchcitytour.content.dao.WitchTrialsNewspaperDao
import com.example.wickedsalemwitchcitytour.content.dao.WitchTrialsNpcBioDao
import com.example.wickedsalemwitchcitytour.content.model.WitchTrialsArticle
import com.example.wickedsalemwitchcitytour.content.model.WitchTrialsNewspaper
import com.example.wickedsalemwitchcitytour.content.model.WitchTrialsNpcBio
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Read-side facade for the three Witch Trials Room tables, plus first-run
 * hydration from bundled JSON assets (articles.json, npc_bios.json,
 * newspapers.json under assets/witch_trials/). The publish scripts in
 * cache-proxy/scripts/publish-witch-trials*.js generate those assets.
 */
@Singleton
class WitchTrialsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val articleDao: WitchTrialsArticleDao,
    private val npcBioDao: WitchTrialsNpcBioDao,
    private val newspaperDao: WitchTrialsNewspaperDao
) {

    companion object {
        private const val TAG = "WitchTrialsRepo"
        private const val ASSET_ARTICLES = "witch_trials/articles.json"
    }

    private val hydrateMutex = Mutex()
    @Volatile private var articlesHydrated = false

    suspend fun getAllArticles(): List<WitchTrialsArticle> {
        hydrateArticlesIfNeeded()
        return articleDao.findAll()
    }

    suspend fun getArticleById(id: String): WitchTrialsArticle? {
        hydrateArticlesIfNeeded()
        return articleDao.findById(id)
    }

    suspend fun getArticleByOrder(order: Int): WitchTrialsArticle? {
        hydrateArticlesIfNeeded()
        return articleDao.findByOrder(order)
    }

    suspend fun getArticleCount(): Int {
        hydrateArticlesIfNeeded()
        return articleDao.count()
    }

    suspend fun getAllBios(): List<WitchTrialsNpcBio> = npcBioDao.findAll()
    suspend fun getBioById(id: String): WitchTrialsNpcBio? = npcBioDao.findById(id)
    suspend fun getBiosByFaction(faction: String): List<WitchTrialsNpcBio> = npcBioDao.findByFaction(faction)
    suspend fun getBioCount(): Int = npcBioDao.count()

    suspend fun getAllNewspapers(): List<WitchTrialsNewspaper> = newspaperDao.findAll()
    suspend fun getNewspaperById(id: String): WitchTrialsNewspaper? = newspaperDao.findById(id)
    suspend fun getNewspaperByDate(date: String): WitchTrialsNewspaper? = newspaperDao.findByDate(date)
    suspend fun getNewspapersByPhase(phase: Int): List<WitchTrialsNewspaper> = newspaperDao.findByPhase(phase)
    suspend fun getNewspaperCount(): Int = newspaperDao.count()

    // ── Hydration ──────────────────────────────────────────────────────

    private data class ArticleBundle(
        val version: Int,
        val count: Int,
        @SerializedName("generated_at") val generatedAt: String?,
        val articles: List<ArticleJson>
    )

    private data class ArticleJson(
        val id: String,
        val tileOrder: Int,
        val tileKind: String,
        val title: String,
        val periodLabel: String?,
        val teaser: String,
        val body: String,
        val relatedNpcIds: String?,
        val relatedEventIds: String?,
        val relatedNewspaperDates: String?,
        val dataSource: String?,
        val confidence: Float?,
        val verifiedDate: String?,
        val generatorModel: String?
    )

    private suspend fun hydrateArticlesIfNeeded() {
        if (articlesHydrated) {
            DebugLogger.i(TAG, "hydrateArticlesIfNeeded: already hydrated (memo flag)")
            return
        }
        hydrateMutex.withLock {
            if (articlesHydrated) return
            val existing = try { articleDao.count() } catch (e: Exception) {
                DebugLogger.e(TAG, "hydrateArticlesIfNeeded: count() threw — DB schema mismatch? ${e.message}", e)
                -1
            }
            DebugLogger.i(TAG, "hydrateArticlesIfNeeded: existing=$existing")
            if (existing >= 16) {
                articlesHydrated = true
                DebugLogger.i(TAG, "articles already hydrated ($existing rows)")
                return
            }
            try {
                DebugLogger.i(TAG, "hydrateArticlesIfNeeded: opening asset $ASSET_ARTICLES")
                val bundle = withContext(Dispatchers.IO) {
                    context.assets.open(ASSET_ARTICLES).bufferedReader().use { reader ->
                        Gson().fromJson(reader, ArticleBundle::class.java)
                    }
                }
                DebugLogger.i(TAG, "hydrateArticlesIfNeeded: parsed bundle v${bundle.version} count=${bundle.count} listSize=${bundle.articles.size}")
                val rows = bundle.articles.map { a ->
                    WitchTrialsArticle(
                        id = a.id,
                        tileOrder = a.tileOrder,
                        tileKind = a.tileKind,
                        title = a.title,
                        periodLabel = a.periodLabel,
                        teaser = a.teaser,
                        body = a.body,
                        relatedNpcIds = a.relatedNpcIds ?: "[]",
                        relatedEventIds = a.relatedEventIds ?: "[]",
                        relatedNewspaperDates = a.relatedNewspaperDates ?: "[]",
                        dataSource = a.dataSource ?: "ollama_direct_salem_village",
                        confidence = a.confidence ?: 0.7f,
                        verifiedDate = a.verifiedDate,
                        generatorModel = a.generatorModel
                    )
                }
                DebugLogger.i(TAG, "hydrateArticlesIfNeeded: mapped ${rows.size} rows. First row id='${rows.firstOrNull()?.id}' title='${rows.firstOrNull()?.title?.take(30)}' bodyLen=${rows.firstOrNull()?.body?.length}")
                try {
                    articleDao.insertAll(rows)
                    DebugLogger.i(TAG, "hydrateArticlesIfNeeded: insertAll returned")
                } catch (e: Exception) {
                    DebugLogger.e(TAG, "hydrateArticlesIfNeeded: insertAll threw: ${e.message}", e)
                }
                val postCount = try { articleDao.count() } catch (e: Exception) { -1 }
                articlesHydrated = true
                DebugLogger.i(TAG, "hydrated ${rows.size} articles from $ASSET_ARTICLES (v${bundle.version}) postInsertCount=$postCount")
            } catch (e: Exception) {
                DebugLogger.e(TAG, "Failed to hydrate from $ASSET_ARTICLES: ${e.message}", e)
            }
        }
    }
}
