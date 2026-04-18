/*
 * WickedSalemApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.wickedsalemwitchcitytour.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.example.wickedsalemwitchcitytour.R
import com.example.locationmapapp.data.model.GeocodeSuggestion
import com.example.wickedsalemwitchcitytour.ui.menu.PoiCategories
import com.example.wickedsalemwitchcitytour.ui.menu.PoiCategory
import com.example.locationmapapp.util.DebugLogger
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import android.content.Context

// ── S144 Find modal Salem-voice reinforcement (#49) ────────────────────────
//
// (a) V1 is offline-only. These categories either require a live cache-proxy
//     or have zero POIs in the bundled Room DB. They are filtered out of the
//     Find grid so users only see tiles that will return results.
//
// (b) Per-tile label override — keeps the canonical PoiCategories.kt labels
//     (mirrored to the web admin tool) generic, while the in-app Find surface
//     speaks Salem's 1692/colonial voice. Each key is a PoiLayerId constant
//     from PoiCategories.ALL; any id not in the map falls back to cat.label.
//
// (c) Illustration asset name per tile — painterly cartoon icons generated
//     in Forge (see ~/AI-Studio/katrina-mascot/find-tiles/). File lives in
//     res/drawable-nodpi/find_tile_<id>.jpg. Missing asset → fall back to
//     the original colored-rectangle + label-only tile.
private val FIND_HIDE_IN_V1: Set<String> = setOf(
    com.example.locationmapapp.ui.menu.PoiLayerId.FUEL_CHARGING,
    com.example.locationmapapp.ui.menu.PoiLayerId.TRANSIT,
    com.example.locationmapapp.ui.menu.PoiLayerId.PARKING,
    com.example.locationmapapp.ui.menu.PoiLayerId.EMERGENCY,
)

private val FIND_SALEM_LABEL: Map<String, String> = mapOf(
    com.example.locationmapapp.ui.menu.PoiLayerId.FOOD_DRINK           to "Taverns & Cafés",
    com.example.locationmapapp.ui.menu.PoiLayerId.CIVIC                to "Town Halls & Civic",
    com.example.locationmapapp.ui.menu.PoiLayerId.PARKS_REC            to "Parks & Gardens",
    com.example.locationmapapp.ui.menu.PoiLayerId.SHOPPING             to "Shops & Markets",
    com.example.locationmapapp.ui.menu.PoiLayerId.HEALTHCARE           to "Apothecaries & Clinics",
    com.example.locationmapapp.ui.menu.PoiLayerId.EDUCATION            to "Schools & Libraries",
    com.example.locationmapapp.ui.menu.PoiLayerId.LODGING              to "Inns & Lodging",
    com.example.locationmapapp.ui.menu.PoiLayerId.FINANCE              to "Banks",
    com.example.locationmapapp.ui.menu.PoiLayerId.WORSHIP              to "Churches & Meetinghouses",
    com.example.locationmapapp.ui.menu.PoiLayerId.HISTORICAL_BUILDINGS to "Historic Sites",
    com.example.locationmapapp.ui.menu.PoiLayerId.AUTO_SERVICES        to "Auto & Repair",
    com.example.locationmapapp.ui.menu.PoiLayerId.ENTERTAINMENT        to "Amusements & Stages",
    com.example.locationmapapp.ui.menu.PoiLayerId.OFFICES              to "Offices",
    com.example.locationmapapp.ui.menu.PoiLayerId.WITCH_SHOP           to "Witch & Occult",
    com.example.locationmapapp.ui.menu.PoiLayerId.PSYCHIC              to "Psychic & Tarot",
    com.example.locationmapapp.ui.menu.PoiLayerId.TOUR_COMPANIES       to "Tours & Guides",
)

private val FIND_TILE_ASSET: Map<String, String> = mapOf(
    com.example.locationmapapp.ui.menu.PoiLayerId.FOOD_DRINK           to "find_tile_food_drink",
    com.example.locationmapapp.ui.menu.PoiLayerId.CIVIC                to "find_tile_civic",
    com.example.locationmapapp.ui.menu.PoiLayerId.PARKS_REC            to "find_tile_parks",
    com.example.locationmapapp.ui.menu.PoiLayerId.SHOPPING             to "find_tile_shopping",
    com.example.locationmapapp.ui.menu.PoiLayerId.HEALTHCARE           to "find_tile_healthcare",
    com.example.locationmapapp.ui.menu.PoiLayerId.EDUCATION            to "find_tile_education",
    com.example.locationmapapp.ui.menu.PoiLayerId.LODGING              to "find_tile_lodging",
    com.example.locationmapapp.ui.menu.PoiLayerId.FINANCE              to "find_tile_finance",
    com.example.locationmapapp.ui.menu.PoiLayerId.WORSHIP              to "find_tile_worship",
    com.example.locationmapapp.ui.menu.PoiLayerId.HISTORICAL_BUILDINGS to "find_tile_historic",
    com.example.locationmapapp.ui.menu.PoiLayerId.AUTO_SERVICES        to "find_tile_auto",
    com.example.locationmapapp.ui.menu.PoiLayerId.ENTERTAINMENT        to "find_tile_entertainment",
    com.example.locationmapapp.ui.menu.PoiLayerId.OFFICES              to "find_tile_offices",
    com.example.locationmapapp.ui.menu.PoiLayerId.WITCH_SHOP           to "find_tile_witch",
    com.example.locationmapapp.ui.menu.PoiLayerId.PSYCHIC              to "find_tile_psychic",
    com.example.locationmapapp.ui.menu.PoiLayerId.TOUR_COMPANIES       to "find_tile_tours",
)

@Suppress("unused")
private const val MODULE_ID = "(C) Dean Maurice Ellis, 2026 - Module MainActivityFind.kt"

// ── Find Dialog (POI Discovery) ──────────────────────────────────────────

// Filter mode state
internal var findFilterActive = false
internal var findFilterTags = listOf<String>()
internal var findFilterLabel = ""
internal var findFilterBanner: View? = null

// Filter-and-map mode state (exclusive map view of find results)
internal var filterAndMapActive = false
internal var filterAndMapResults = listOf<com.example.locationmapapp.data.model.FindResult>()
internal var filterAndMapLabel = ""
internal var savedRadarWasOn = false
internal var savedRadarWasAnimating = false

@SuppressLint("SetTextI18n")
internal fun SalemMainActivity.showFindDialog() {
    // Auto-exit filter modes when reopening Find
    if (findFilterActive) exitFindFilterMode()
    if (filterAndMapActive) exitFilterAndMapMode()
    val center = binding.mapView.mapCenter
    findViewModel.loadFindCounts(center.latitude, center.longitude)
    val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
    showFindCategoryGrid(dialog)
    dialog.show()
}

@SuppressLint("SetTextI18n")
internal fun SalemMainActivity.showFindCategoryGrid(dialog: android.app.Dialog) {
    val density = resources.displayMetrics.density
    val dp = { v: Int -> (v * density).toInt() }
    val counts = findViewModel.findCounts.value

    // ── Header ──
    val titleText = TextView(this).apply {
        text = "Find"
        textSize = 18f
        setTextColor(Color.WHITE)
        setTypeface(null, android.graphics.Typeface.BOLD)
    }
    val headerHint = TextView(this).apply {
        textSize = 12f
        setTextColor(Color.parseColor("#9E9E9E"))
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(10)
        }
        visibility = View.GONE
    }
    val closeBtn = TextView(this).apply {
        text = "\u2715"
        textSize = 20f
        setTextColor(Color.WHITE)
        setPadding(dp(12), 0, dp(4), 0)
        setOnClickListener { dialog.dismiss() }
    }
    val header = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(dp(16), dp(12), dp(12), dp(8))
        gravity = android.view.Gravity.CENTER_VERTICAL
        addView(titleText)
        addView(headerHint)
        addView(closeBtn)
    }

    // ── Category Grid — auto-fit cell height ──
    val colCount = 4
    val visibleCats = PoiCategories.ALL.filter { it.id !in FIND_HIDE_IN_V1 }
    val totalCells = visibleCats.size + 1 // +1 for Favorites
    val rowCount = (totalCells + colCount - 1) / colCount
    val screenH = resources.displayMetrics.heightPixels
    val headerH = dp(40)     // header area
    val searchBarH = dp(50)  // search bar area
    val gridPadV = dp(20)    // grid vertical padding
    val marginPerRow = dp(6) // top+bottom margins per cell
    val availH = screenH - headerH - searchBarH - gridPadV
    val cellH = maxOf(dp(36), minOf(dp(120), (availH / rowCount) - marginPerRow))

    val grid = android.widget.GridLayout(this).apply {
        columnCount = colCount
        setPadding(dp(8), 0, dp(8), dp(8))
    }

    // ── Favorites cell (first in grid) ──
    val favCount = favoritesManager.getCount()
    val favCell = android.widget.FrameLayout(this).apply {
        val lp = android.widget.GridLayout.LayoutParams().apply {
            width = 0
            height = cellH
            columnSpec = android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f)
            setMargins(dp(3), dp(3), dp(3), dp(3))
        }
        layoutParams = lp
        val bg = android.graphics.drawable.GradientDrawable().apply {
            setColor(Color.argb(77, 0xFF, 0xD7, 0x00))
            cornerRadius = dp(6).toFloat()
        }
        background = bg
        setPadding(dp(6), dp(6), dp(6), dp(6))
    }
    val favLabel = TextView(this).apply {
        text = "Favorites"
        textSize = 12f
        setTextColor(Color.WHITE)
        setTypeface(null, android.graphics.Typeface.BOLD)
        maxLines = 2
        layoutParams = android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
            android.view.Gravity.BOTTOM
        )
    }
    if (favCount > 0) {
        val favBadge = TextView(this).apply {
            text = favCount.toString()
            textSize = 10f
            setTextColor(Color.parseColor("#FFD700"))
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.TOP or android.view.Gravity.END
            )
        }
        favCell.addView(favBadge)
    }
    favCell.addView(favLabel)
    favCell.setOnClickListener { showFavoritesResults(dialog) }
    grid.addView(favCell)

    for (cat in visibleCats) {
        val catCount = counts?.let { c ->
            cat.tags.sumOf { tag -> c.counts[tag] ?: 0 }
        }

        val cell = android.widget.FrameLayout(this).apply {
            val lp = android.widget.GridLayout.LayoutParams().apply {
                width = 0
                height = cellH
                columnSpec = android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f)
                setMargins(dp(3), dp(3), dp(3), dp(3))
            }
            layoutParams = lp
            val bg = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.argb(77, Color.red(cat.color), Color.green(cat.color), Color.blue(cat.color)))
                cornerRadius = dp(6).toFloat()
            }
            background = bg
            setPadding(dp(6), dp(6), dp(6), dp(6))
        }

        // S144: background illustration — resolved by category id.
        // Missing drawable → no illustration, tile stays a clean color swatch.
        FIND_TILE_ASSET[cat.id]?.let { assetName ->
            val resId = resources.getIdentifier(assetName, "drawable", packageName)
            if (resId != 0) {
                val art = ImageView(this).apply {
                    setImageResource(resId)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    alpha = 0.85f
                    layoutParams = android.widget.FrameLayout.LayoutParams(
                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                    )
                }
                cell.addView(art)
                val tint = android.view.View(this).apply {
                    setBackgroundColor(Color.argb(0x99, 0, 0, 0))
                    layoutParams = android.widget.FrameLayout.LayoutParams(
                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                    )
                }
                cell.addView(tint)
            }
        }

        // Label — Salem-voice override if present, else PoiCategories canonical label.
        val displayLabel = FIND_SALEM_LABEL[cat.id] ?: cat.label
        val label = TextView(this).apply {
            text = displayLabel
            textSize = 12f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            maxLines = 2
            setShadowLayer(3f, 1f, 1f, Color.BLACK)
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.BOTTOM
            )
        }

        // Count badge
        if (catCount != null && catCount > 0) {
            val badge = TextView(this).apply {
                text = if (catCount >= 1000) "%.1fk".format(catCount / 1000.0) else catCount.toString()
                textSize = 10f
                setTextColor(Color.parseColor("#9E9E9E"))
                layoutParams = android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                    android.view.Gravity.TOP or android.view.Gravity.END
                )
            }
            cell.addView(badge)
        }

        cell.addView(label)

        // Short tap
        cell.setOnClickListener {
            if (cat.subtypes != null) {
                showFindSubtypeGrid(dialog, cat)
            } else {
                showFindResults(dialog, cat.label, cat.tags, null)
            }
        }

        // Long press → filter mode
        cell.setOnLongClickListener {
            dialog.dismiss()
            enterFindFilterMode(cat.tags, cat.label)
            true
        }

        grid.addView(cell)
    }

    // ── Search bar + grid in a FrameLayout ──
    val searchResultsList = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(8), 0, dp(8), dp(8))
    }
    val searchScroll = android.widget.ScrollView(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        )
        addView(searchResultsList)
        visibility = View.GONE
    }

    // Define gridScroll early so text watcher can toggle it
    val gridScroll = android.widget.ScrollView(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        )
        visibility = View.VISIBLE
    }

    val searchBar = android.widget.EditText(this).apply {
        hint = "Search by name or keyword (e.g., historic, food)..."
        textSize = 14f
        setTextColor(Color.WHITE)
        setHintTextColor(Color.parseColor("#757575"))
        setBackgroundColor(Color.parseColor("#2A2A2A"))
        setPadding(dp(12), dp(10), dp(40), dp(10))
        setSingleLine(true)
        inputType = android.text.InputType.TYPE_CLASS_TEXT
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(dp(8), dp(4), dp(8), dp(4)) }
    }

    // Clear button overlaid on search bar
    val searchBarContainer = android.widget.FrameLayout(this).apply {
        addView(searchBar)
    }
    val clearBtn = TextView(this).apply {
        text = "\u2715"
        textSize = 16f
        setTextColor(Color.parseColor("#9E9E9E"))
        setPadding(dp(12), dp(10), dp(12), dp(10))
        layoutParams = android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
            android.view.Gravity.END or android.view.Gravity.CENTER_VERTICAL
        )
        visibility = View.GONE
        setOnClickListener {
            searchBar.text.clear()
            visibility = View.GONE
            searchScroll.visibility = View.GONE
            searchResultsList.removeAllViews()
            gridScroll.visibility = View.VISIBLE
            headerHint.visibility = View.GONE
        }
    }
    searchBarContainer.addView(clearBtn)

    // Debounced search
    var searchJob: Job? = null
    searchBar.addTextChangedListener(object : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: android.text.Editable?) {
            val query = s?.toString()?.trim() ?: ""
            searchJob?.cancel()
            if (query.length < 2) {
                clearBtn.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
                searchScroll.visibility = View.GONE
                searchResultsList.removeAllViews()
                gridScroll.visibility = View.VISIBLE
                headerHint.visibility = View.GONE
                return
            }
            clearBtn.visibility = View.VISIBLE
            gridScroll.visibility = View.GONE
            searchScroll.visibility = View.VISIBLE
            searchResultsList.removeAllViews()
            headerHint.visibility = View.GONE
            // Show spinner
            val spinner = android.widget.ProgressBar(this@showFindCategoryGrid).apply {
                setPadding(0, dp(24), 0, dp(24))
            }
            searchResultsList.addView(spinner)

            searchJob = lifecycleScope.launch {
                delay(1000)
                val mapCenter = binding.mapView.mapCenter
                val response = findViewModel.searchPoisByName(query, mapCenter.latitude, mapCenter.longitude)
                searchResultsList.removeAllViews()
                if (response == null || response.results.isEmpty()) {
                    headerHint.text = "No results"
                    headerHint.setTextColor(Color.parseColor("#9E9E9E"))
                    headerHint.visibility = View.VISIBLE
                    searchResultsList.addView(TextView(this@showFindCategoryGrid).apply {
                        text = "No results for \"$query\""
                        textSize = 14f
                        setTextColor(Color.parseColor("#9E9E9E"))
                        setPadding(dp(16), dp(24), dp(16), dp(24))
                    })
                    return@launch
                }
                // Update header hint with count + category
                val countLabel = if (response.totalCount >= 200) "200+" else "${response.totalCount}"
                val catPart = if (response.categoryHint != null) " ${response.categoryHint}" else ""
                val refineHint = if (response.totalCount >= 50) " · refine to narrow" else ""
                headerHint.text = "$countLabel$catPart$refineHint"
                headerHint.setTextColor(if (response.categoryHint != null) Color.parseColor("#00BCD4") else Color.parseColor("#9E9E9E"))
                headerHint.visibility = View.VISIBLE
                // "Filter and Map" button at top of search results
                searchResultsList.addView(TextView(this@showFindCategoryGrid).apply {
                    text = "Filter and Map"
                    textSize = 14f
                    setTextColor(Color.WHITE)
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    gravity = android.view.Gravity.CENTER
                    setPadding(dp(16), dp(10), dp(16), dp(10))
                    background = android.graphics.drawable.GradientDrawable().apply {
                        setColor(Color.parseColor("#00897B"))
                        cornerRadius = dp(6).toFloat()
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { setMargins(dp(4), dp(4), dp(4), dp(8)) }
                    setOnClickListener {
                        dialog.dismiss()
                        val filterLabel = response.categoryHint ?: "\"$query\""
                        enterFilterAndMapMode(response.results, filterLabel)
                    }
                })
                // Get category label lookup for display
                val catLabelMap = mutableMapOf<String, Pair<String, Int>>()
                for (cat in com.example.wickedsalemwitchcitytour.ui.menu.PoiCategories.ALL) {
                    for (tag in cat.tags) { catLabelMap[tag] = Pair(cat.label, cat.color) }
                }
                for (result in response.results) {
                    val row = LinearLayout(this@showFindCategoryGrid).apply {
                        orientation = LinearLayout.HORIZONTAL
                        setPadding(dp(4), dp(6), dp(4), dp(6))
                        gravity = android.view.Gravity.CENTER_VERTICAL
                    }
                    // Color dot
                    val catInfo = catLabelMap[result.category]
                    val catColor = catInfo?.second ?: Color.parseColor("#757575")
                    val catLabel = catInfo?.first
                    val colorDot = View(this@showFindCategoryGrid).apply {
                        val size = dp(8)
                        layoutParams = LinearLayout.LayoutParams(size, size).apply { marginEnd = dp(6) }
                        background = android.graphics.drawable.GradientDrawable().apply {
                            shape = android.graphics.drawable.GradientDrawable.OVAL
                            setColor(catColor)
                        }
                    }
                    // Distance
                    val distText = TextView(this@showFindCategoryGrid).apply {
                        text = formatDistanceDirection(
                            mapCenter.latitude, mapCenter.longitude, result.lat, result.lon
                        )
                        textSize = 11f
                        setTextColor(Color.parseColor("#4FC3F7"))
                        layoutParams = LinearLayout.LayoutParams(dp(60), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                            marginEnd = dp(6)
                        }
                    }
                    // Info column: name + detail + category
                    val infoCol = LinearLayout(this@showFindCategoryGrid).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    }
                    val nameText = TextView(this@showFindCategoryGrid).apply {
                        text = result.name ?: result.typeValue.replaceFirstChar { it.uppercase() }
                        textSize = 13f
                        setTextColor(Color.WHITE)
                        setTypeface(typeface, android.graphics.Typeface.BOLD)
                        maxLines = 1
                        ellipsize = android.text.TextUtils.TruncateAt.END
                    }
                    infoCol.addView(nameText)
                    // Detail line: cuisine/brand/typeValue
                    val detailStr = result.detail ?: result.typeValue.replaceFirstChar { it.uppercase() }
                    val detailText = TextView(this@showFindCategoryGrid).apply {
                        text = detailStr
                        textSize = 11f
                        setTextColor(Color.parseColor("#9E9E9E"))
                        maxLines = 1
                        ellipsize = android.text.TextUtils.TruncateAt.END
                    }
                    infoCol.addView(detailText)
                    // Category label
                    if (catLabel != null) {
                        infoCol.addView(TextView(this@showFindCategoryGrid).apply {
                            text = catLabel
                            textSize = 10f
                            setTextColor(catColor)
                            maxLines = 1
                        })
                    }
                    row.addView(colorDot)
                    row.addView(distText)
                    row.addView(infoCol)
                    row.setOnClickListener {
                        dialog.dismiss()
                        showPoiDetailDialog(result)
                    }
                    searchResultsList.addView(row)
                    searchResultsList.addView(View(this@showFindCategoryGrid).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, 1
                        ).apply { setMargins(dp(18), 0, 0, 0) }
                        setBackgroundColor(Color.parseColor("#2A2A2A"))
                    })
                }
                // Footer: result count + scope
                val scopeLabel = when {
                    response.scopeM >= 99999000 -> "global"
                    response.scopeM >= 1000000 -> "${response.scopeM / 1000}km"
                    response.scopeM >= 1000 -> "${response.scopeM / 1000}km"
                    else -> "${response.scopeM}m"
                }
                val farthest = response.results.lastOrNull()?.let {
                    formatDistanceDirection(mapCenter.latitude, mapCenter.longitude, it.lat, it.lon)
                } ?: ""
                searchResultsList.addView(TextView(this@showFindCategoryGrid).apply {
                    text = "${response.totalCount} results within $scopeLabel${if (farthest.isNotEmpty()) " (farthest: $farthest)" else ""}"
                    textSize = 11f
                    setTextColor(Color.parseColor("#757575"))
                    setPadding(dp(4), dp(8), dp(4), dp(8))
                })

            }
        }
    })

    gridScroll.addView(grid)

    val container = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(Color.parseColor("#1A1A1A"))
        addView(header)
        addView(searchBarContainer)
        addView(gridScroll)
        addView(searchScroll)
    }

    dialog.setContentView(container)
    dialog.window?.let { win ->
        val dm = resources.displayMetrics
        win.setLayout((dm.widthPixels * 0.90).toInt(), (dm.heightPixels * 0.85).toInt())
        win.setBackgroundDrawableResource(android.R.color.transparent)
        win.setGravity(android.view.Gravity.CENTER)
    }
}

@SuppressLint("SetTextI18n")
internal fun SalemMainActivity.showFindSubtypeGrid(dialog: android.app.Dialog, cat: com.example.wickedsalemwitchcitytour.ui.menu.PoiCategory) {
    val density = resources.displayMetrics.density
    val dp = { v: Int -> (v * density).toInt() }
    val counts = findViewModel.findCounts.value
    val subtypes = cat.subtypes ?: return

    // ── Header with back ──
    val backBtn = TextView(this).apply {
        text = "\u2190"
        textSize = 20f
        setTextColor(Color.WHITE)
        setPadding(0, 0, dp(12), 0)
        setOnClickListener { showFindCategoryGrid(dialog) }
    }
    val titleText = TextView(this).apply {
        text = cat.label
        textSize = 18f
        setTextColor(Color.WHITE)
        setTypeface(null, android.graphics.Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    }
    val closeBtn = TextView(this).apply {
        text = "\u2715"
        textSize = 20f
        setTextColor(Color.WHITE)
        setPadding(dp(12), 0, dp(4), 0)
        setOnClickListener { dialog.dismiss() }
    }
    val header = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(dp(16), dp(12), dp(12), dp(8))
        gravity = android.view.Gravity.CENTER_VERTICAL
        addView(backBtn)
        addView(titleText)
        addView(closeBtn)
    }

    // ── Dynamic column count + auto-fit cell height ──
    val colCount = when {
        subtypes.size <= 4 -> 2
        else -> 3
    }
    val subRowCount = (subtypes.size + colCount - 1) / colCount
    val screenH = resources.displayMetrics.heightPixels
    val headerH = dp(40)
    val gridPadV = dp(20)
    val marginPerRow = dp(6)
    val availH = screenH - headerH - gridPadV
    val subCellH = maxOf(dp(36), minOf(dp(120), (availH / subRowCount) - marginPerRow))

    val grid = android.widget.GridLayout(this).apply {
        columnCount = colCount
        setPadding(dp(8), 0, dp(8), dp(8))
    }

    for (sub in subtypes) {
        val subCount = counts?.let { c ->
            sub.tags.sumOf { tag -> c.counts[tag] ?: 0 }
        }

        val cell = android.widget.FrameLayout(this).apply {
            val lp = android.widget.GridLayout.LayoutParams().apply {
                width = 0
                height = subCellH
                columnSpec = android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f)
                setMargins(dp(3), dp(3), dp(3), dp(3))
            }
            layoutParams = lp
            val bg = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.argb(77, Color.red(cat.color), Color.green(cat.color), Color.blue(cat.color)))
                cornerRadius = dp(6).toFloat()
            }
            background = bg
            setPadding(dp(6), dp(6), dp(6), dp(6))
        }

        val label = TextView(this).apply {
            text = sub.label
            textSize = 12f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            maxLines = 2
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.BOTTOM
            )
        }

        if (subCount != null && subCount > 0) {
            val badge = TextView(this).apply {
                text = if (subCount >= 1000) "%.1fk".format(subCount / 1000.0) else subCount.toString()
                textSize = 10f
                setTextColor(Color.parseColor("#9E9E9E"))
                layoutParams = android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                    android.view.Gravity.TOP or android.view.Gravity.END
                )
            }
            cell.addView(badge)
        }

        cell.addView(label)

        cell.setOnClickListener {
            showFindResults(dialog, sub.label, sub.tags, cat)
        }

        cell.setOnLongClickListener {
            dialog.dismiss()
            enterFindFilterMode(sub.tags, sub.label)
            true
        }

        grid.addView(cell)
    }

    val gridScroll = android.widget.ScrollView(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        )
        addView(grid)
    }

    val container = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(Color.parseColor("#1A1A1A"))
        addView(header)
        addView(gridScroll)
    }

    dialog.setContentView(container)
    dialog.window?.let { win ->
        val dm = resources.displayMetrics
        win.setLayout((dm.widthPixels * 0.90).toInt(), (dm.heightPixels * 0.85).toInt())
        win.setBackgroundDrawableResource(android.R.color.transparent)
        win.setGravity(android.view.Gravity.CENTER)
    }
}

@SuppressLint("SetTextI18n")
internal fun SalemMainActivity.showFindResults(
    dialog: android.app.Dialog,
    title: String,
    tags: List<String>,
    parentCategory: com.example.wickedsalemwitchcitytour.ui.menu.PoiCategory?
) {
    val density = resources.displayMetrics.density
    val dp = { v: Int -> (v * density).toInt() }

    // ── Header with back ──
    val backBtn = TextView(this).apply {
        text = "\u2190"
        textSize = 20f
        setTextColor(Color.WHITE)
        setPadding(0, 0, dp(12), 0)
        setOnClickListener {
            if (parentCategory != null) {
                showFindSubtypeGrid(dialog, parentCategory)
            } else {
                showFindCategoryGrid(dialog)
            }
        }
    }
    val titleText = TextView(this).apply {
        text = title
        textSize = 18f
        setTextColor(Color.WHITE)
        setTypeface(null, android.graphics.Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    }
    val closeBtn = TextView(this).apply {
        text = "\u2715"
        textSize = 20f
        setTextColor(Color.WHITE)
        setPadding(dp(12), 0, dp(4), 0)
        setOnClickListener { dialog.dismiss() }
    }
    val header = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(dp(16), dp(12), dp(12), dp(8))
        gravity = android.view.Gravity.CENTER_VERTICAL
        addView(backBtn)
        addView(titleText)
        addView(closeBtn)
    }

    // ── Loading spinner ──
    val spinner = android.widget.ProgressBar(this).apply {
        setPadding(0, dp(24), 0, dp(24))
    }

    val resultsList = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(8), 0, dp(8), dp(8))
    }

    val footer = TextView(this).apply {
        textSize = 11f
        setTextColor(Color.parseColor("#757575"))
        setPadding(dp(16), dp(8), dp(16), dp(12))
    }

    val scrollView = android.widget.ScrollView(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        )
        addView(resultsList)
    }

    val container = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(Color.parseColor("#1A1A1A"))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        )
        addView(header)
        addView(spinner)
        addView(scrollView)
        addView(footer)
    }

    dialog.setContentView(container)
    dialog.window?.let { win ->
        val dm = resources.displayMetrics
        win.setLayout((dm.widthPixels * 0.90).toInt(), (dm.heightPixels * 0.75).toInt())
        win.setBackgroundDrawableResource(android.R.color.transparent)
        win.setGravity(android.view.Gravity.CENTER)
    }

    // Fetch results
    val center = binding.mapView.mapCenter
    lifecycleScope.launch {
        val response = findViewModel.findNearbyDirectly(center.latitude, center.longitude, tags, 50)
        spinner.visibility = View.GONE

        if (response == null || response.results.isEmpty()) {
            resultsList.addView(TextView(this@showFindResults).apply {
                text = "No results found nearby"
                textSize = 14f
                setTextColor(Color.parseColor("#9E9E9E"))
                setPadding(dp(16), dp(24), dp(16), dp(24))
            })
            return@launch
        }

        for (result in response.results) {
            val row = LinearLayout(this@showFindResults).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(4), dp(8), dp(4), dp(8))
                gravity = android.view.Gravity.CENTER_VERTICAL
            }

            // Left column: distance + direction
            val distText = TextView(this@showFindResults).apply {
                text = formatDistanceDirection(
                    center.latitude, center.longitude,
                    result.lat, result.lon
                )
                textSize = 12f
                setTextColor(Color.parseColor("#4FC3F7"))
                layoutParams = LinearLayout.LayoutParams(dp(65), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    marginEnd = dp(8)
                }
            }

            // Right column: name + details
            val infoCol = LinearLayout(this@showFindResults).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val nameText = TextView(this@showFindResults).apply {
                text = result.name ?: result.typeValue.replaceFirstChar { it.uppercase() }
                textSize = 14f
                setTextColor(Color.WHITE)
                setTypeface(null, android.graphics.Typeface.BOLD)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            infoCol.addView(nameText)

            // Detail line (cuisine/type)
            val detailStr = result.detail ?: result.typeValue.replaceFirstChar { it.uppercase() }
            val detailText = TextView(this@showFindResults).apply {
                text = detailStr
                textSize = 12f
                setTextColor(Color.parseColor("#9E9E9E"))
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            infoCol.addView(detailText)

            // Address line
            if (result.address != null) {
                val addrText = TextView(this@showFindResults).apply {
                    text = result.address
                    textSize = 11f
                    setTextColor(Color.parseColor("#616161"))
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                }
                infoCol.addView(addrText)
            }

            row.addView(distText)
            row.addView(infoCol)

            // Tap → POI detail dialog
            row.setOnClickListener {
                dialog.dismiss()
                showPoiDetailDialog(result)
            }

            resultsList.addView(row)

            // Separator line
            resultsList.addView(View(this@showFindResults).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1
                ).apply { setMargins(dp(65), 0, 0, 0) }
                setBackgroundColor(Color.parseColor("#2A2A2A"))
            })
        }

        // Footer
        val maxDistMi = if (response.results.isNotEmpty()) {
            response.results.last().distanceM / 1609.34
        } else 0.0
        footer.text = "Showing ${response.results.size} nearest (within %.1f mi)".format(maxDistMi)

        // "Filter and Map" button
        val filterMapBtn = TextView(this@showFindResults).apply {
            text = "Filter and Map"
            textSize = 14f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.parseColor("#00897B"))
                cornerRadius = dp(6).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(16), dp(4), dp(16), dp(12)) }
            setOnClickListener {
                dialog.dismiss()
                enterFilterAndMapMode(response.results, title)
            }
        }
        (footer.parent as? LinearLayout)?.addView(filterMapBtn)
    }
}

@SuppressLint("SetTextI18n")
internal fun SalemMainActivity.showFavoritesResults(dialog: android.app.Dialog) {
    val density = resources.displayMetrics.density
    val dp = { v: Int -> (v * density).toInt() }
    val center = binding.mapView.mapCenter

    // ── Header with back ──
    val backBtn = TextView(this).apply {
        text = "\u2190"
        textSize = 20f
        setTextColor(Color.WHITE)
        setPadding(0, 0, dp(12), 0)
        setOnClickListener { showFindCategoryGrid(dialog) }
    }
    val titleText = TextView(this).apply {
        text = "Favorites"
        textSize = 18f
        setTextColor(Color.WHITE)
        setTypeface(null, android.graphics.Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    }
    val closeBtn = TextView(this).apply {
        text = "\u2715"
        textSize = 20f
        setTextColor(Color.WHITE)
        setPadding(dp(12), 0, dp(4), 0)
        setOnClickListener { dialog.dismiss() }
    }
    val header = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(dp(16), dp(12), dp(12), dp(8))
        gravity = android.view.Gravity.CENTER_VERTICAL
        addView(backBtn)
        addView(titleText)
        addView(closeBtn)
    }

    val resultsList = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(8), 0, dp(8), dp(8))
    }

    val favorites = favoritesManager.getFavorites()
    if (favorites.isEmpty()) {
        resultsList.addView(TextView(this).apply {
            text = "No favorites yet — tap the star in any POI detail"
            textSize = 14f
            setTextColor(Color.parseColor("#9E9E9E"))
            setPadding(dp(16), dp(24), dp(16), dp(24))
        })
    } else {
        val results = favorites.map { it.toFindResult(center.latitude, center.longitude) }
            .sortedBy { it.distanceM }
        for (result in results) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(4), dp(8), dp(4), dp(8))
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            val distText = TextView(this).apply {
                text = formatDistanceDirection(
                    center.latitude, center.longitude, result.lat, result.lon
                )
                textSize = 12f
                setTextColor(Color.parseColor("#FFD700"))
                layoutParams = LinearLayout.LayoutParams(dp(65), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    marginEnd = dp(8)
                }
            }
            val infoCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            infoCol.addView(TextView(this).apply {
                text = result.name ?: result.typeValue.replaceFirstChar { it.uppercase() }
                textSize = 14f
                setTextColor(Color.WHITE)
                setTypeface(null, android.graphics.Typeface.BOLD)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            val detailStr = result.detail ?: result.typeValue.replaceFirstChar { it.uppercase() }
            infoCol.addView(TextView(this).apply {
                text = detailStr
                textSize = 12f
                setTextColor(Color.parseColor("#9E9E9E"))
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            if (result.address != null) {
                infoCol.addView(TextView(this).apply {
                    text = result.address
                    textSize = 11f
                    setTextColor(Color.parseColor("#616161"))
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                })
            }
            row.addView(distText)
            row.addView(infoCol)
            row.setOnClickListener {
                dialog.dismiss()
                showPoiDetailDialog(result)
            }
            resultsList.addView(row)
            resultsList.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1
                ).apply { setMargins(dp(65), 0, 0, 0) }
                setBackgroundColor(Color.parseColor("#2A2A2A"))
            })
        }
    }

    val scrollView = android.widget.ScrollView(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        )
        addView(resultsList)
    }

    val footer = TextView(this).apply {
        text = "${favorites.size} favorites"
        textSize = 11f
        setTextColor(Color.parseColor("#757575"))
        setPadding(dp(16), dp(8), dp(16), dp(12))
    }

    val container = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(Color.parseColor("#1A1A1A"))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        )
        addView(header)
        addView(scrollView)
        addView(footer)
    }

    dialog.setContentView(container)
    dialog.window?.let { win ->
        val dm = resources.displayMetrics
        win.setLayout((dm.widthPixels * 0.90).toInt(), (dm.heightPixels * 0.75).toInt())
        win.setBackgroundDrawableResource(android.R.color.transparent)
        win.setGravity(android.view.Gravity.CENTER)
    }
}

internal fun SalemMainActivity.poiCategoryColor(categoryTag: String): Int {
    return PoiCategories.ALL.firstOrNull { it.tags.contains(categoryTag) }?.color
        ?: Color.parseColor("#757575")
}

@SuppressLint("SetJavaScriptEnabled")
internal fun SalemMainActivity.showPoiDetailDialog(result: com.example.locationmapapp.data.model.FindResult) {
    val density = resources.displayMetrics.density
    val dp = { v: Int -> (v * density).toInt() }
    val catColor = poiCategoryColor(result.category)
    val center = binding.mapView.mapCenter

    val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)

    // ── Header: color dot + compact distance + name + close ──
    val dot = View(this).apply {
        val size = dp(12)
        layoutParams = LinearLayout.LayoutParams(size, size).apply { marginEnd = dp(6) }
        background = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(catColor)
        }
    }
    // Compact distance from current GPS: "1.4mi(NE)"
    val gpsLoc = viewModel.currentLocation.value?.point
    val compactDist = if (gpsLoc != null) {
        val results = FloatArray(2)
        android.location.Location.distanceBetween(
            gpsLoc.latitude, gpsLoc.longitude, result.lat, result.lon, results
        )
        val distMi = results[0] / 1609.34
        val cardinal = bearingToCardinal(results[1])
        val distStr = if (distMi < 0.1) "%.0fft".format(results[0] * 3.28084)
            else if (distMi < 10) "%.1fmi".format(distMi)
            else "%.0fmi".format(distMi)
        "$distStr($cardinal)"
    } else null
    val distLabel = TextView(this).apply {
        text = compactDist ?: ""
        textSize = 12f
        setTextColor(Color.parseColor("#4FC3F7"))
        setPadding(0, 0, dp(8), 0)
        if (compactDist == null) visibility = View.GONE
    }
    val titleText = TextView(this).apply {
        text = result.name ?: result.typeValue.replaceFirstChar { it.uppercase() }
        textSize = 18f
        setTextColor(Color.WHITE)
        setTypeface(null, android.graphics.Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        maxLines = 2
        ellipsize = android.text.TextUtils.TruncateAt.END
    }
    val isFav = favoritesManager.isFavorite(result.type, result.id)
    val starIcon = ImageView(this).apply {
        setImageResource(if (isFav) R.drawable.ic_star else R.drawable.ic_star_outline)
        layoutParams = LinearLayout.LayoutParams(dp(28), dp(28)).apply {
            marginStart = dp(4)
            marginEnd = dp(4)
        }
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        setOnClickListener {
            if (favoritesManager.isFavorite(result.type, result.id)) {
                favoritesManager.removeFavorite(result.type, result.id)
                setImageResource(R.drawable.ic_star_outline)
                toast("Removed from favorites")
            } else {
                favoritesManager.addFavorite(com.example.locationmapapp.data.model.FavoriteEntry(
                    osmType = result.type,
                    osmId = result.id,
                    name = result.name,
                    lat = result.lat,
                    lon = result.lon,
                    category = result.category,
                    address = result.address,
                    phone = result.phone,
                    openingHours = result.openingHours
                ))
                setImageResource(R.drawable.ic_star)
                toast("Added to favorites")
            }
        }
    }
    val closeBtn = TextView(this).apply {
        text = "\u2715"
        textSize = 20f
        setTextColor(Color.WHITE)
        setPadding(dp(12), dp(4), dp(4), dp(4))
        setOnClickListener { dialog.dismiss() }
    }
    val header = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = android.view.Gravity.CENTER_VERTICAL
        setPadding(dp(16), dp(12), dp(12), dp(4))
        addView(dot)
        addView(distLabel)
        addView(titleText)
        addView(starIcon)
        addView(closeBtn)
    }

    // ── Color bar ──
    val colorBar = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(4)
        )
        setBackgroundColor(catColor)
    }

    // ── Info rows ──
    val infoContainer = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(8), dp(16), dp(8))
    }

    fun addInfoRow(label: String, value: String, tappable: Boolean = false, onClick: (() -> Unit)? = null) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(4), 0, dp(4))
        }
        row.addView(TextView(this).apply {
            text = label
            textSize = 13f
            setTextColor(Color.parseColor("#999999"))
            layoutParams = LinearLayout.LayoutParams(dp(100), LinearLayout.LayoutParams.WRAP_CONTENT)
        })
        row.addView(TextView(this).apply {
            text = value
            textSize = 14f
            setTextColor(if (tappable) Color.parseColor("#4FC3F7") else Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            if (onClick != null) setOnClickListener { onClick() }
        })
        infoContainer.addView(row)
    }

    // Distance
    addInfoRow("Distance", formatDistanceDirection(
        center.latitude, center.longitude, result.lat, result.lon
    ))

    // Type
    val typeLine = buildString {
        append(result.typeValue.replaceFirstChar { it.uppercase() })
        result.detail?.let { append(" ($it)") }
    }
    addInfoRow("Type", typeLine)

    // Address
    result.address?.let { addr -> addInfoRow("Address", addr) }

    // Phone (tappable)
    result.phone?.let { phone ->
        addInfoRow("Phone", phone, tappable = true) {
            try {
                startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                })
            } catch (_: Exception) {}
        }
    }

    // Hours
    result.openingHours?.let { hours -> addInfoRow("Hours", hours) }

    // ── Website button area ──
    val websiteArea = android.widget.FrameLayout(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        )
        setBackgroundColor(Color.parseColor("#111111"))
    }

    // Spinner while resolving
    val loadingLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = android.view.Gravity.CENTER
        layoutParams = android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT
        )
    }
    loadingLayout.addView(android.widget.ProgressBar(this))
    loadingLayout.addView(TextView(this).apply {
        text = "Resolving website..."
        textSize = 12f
        setTextColor(Color.parseColor("#757575"))
        gravity = android.view.Gravity.CENTER
        setPadding(0, dp(8), 0, 0)
    })
    websiteArea.addView(loadingLayout)

    // Resolved URL stored here for Reviews button fallback
    var resolvedUrl: String? = null

    // Async resolve → show big button or "no website"
    lifecycleScope.launch {
        val websiteInfo = findViewModel.fetchPoiWebsiteDirectly(
            result.type, result.id, result.name, result.lat, result.lon
        )
        websiteArea.removeAllViews()
        val siteUrl = websiteInfo?.url
        if (siteUrl != null) {
            resolvedUrl = siteUrl
            websiteArea.addView(TextView(this@showPoiDetailDialog).apply {
                text = "\uD83C\uDF10  Load Website"
                textSize = 18f
                setTextColor(Color.WHITE)
                setTypeface(null, android.graphics.Typeface.BOLD)
                gravity = android.view.Gravity.CENTER
                setBackgroundColor(Color.parseColor("#1565C0"))
                layoutParams = android.widget.FrameLayout.LayoutParams(
                    dp(260), dp(56)
                ).apply { gravity = android.view.Gravity.CENTER }
                setOnClickListener { showFullScreenWebView(siteUrl, result.name ?: "Website") }
            })
        } else {
            websiteArea.addView(TextView(this@showPoiDetailDialog).apply {
                text = "No website available"
                textSize = 14f
                setTextColor(Color.parseColor("#616161"))
                gravity = android.view.Gravity.CENTER
                layoutParams = android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                )
            })
        }
    }

    // ── Action buttons ──
    val buttonContainer = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = android.view.Gravity.CENTER
        setPadding(dp(16), dp(8), dp(16), dp(12))
    }
    val buttonLp = LinearLayout.LayoutParams(0, dp(44), 1f).apply {
        setMargins(dp(3), 0, dp(3), 0)
    }

    // Directions button
    buttonContainer.addView(TextView(this).apply {
        text = "Directions"
        textSize = 11f
        setTextColor(Color.WHITE)
        setTypeface(null, android.graphics.Typeface.BOLD)
        gravity = android.view.Gravity.CENTER
        setBackgroundColor(Color.parseColor("#2E7D32"))
        layoutParams = buttonLp
        setPadding(dp(4), dp(8), dp(4), dp(8))
        setOnClickListener {
            val dest = if (result.name != null) {
                Uri.encode("${result.name}, ${result.lat},${result.lon}")
            } else {
                "${result.lat},${result.lon}"
            }
            val mapsUrl = "https://www.google.com/maps/dir/?api=1&destination=$dest"
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(mapsUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                })
            } catch (_: Exception) {}
        }
    })

    // Call button
    buttonContainer.addView(TextView(this).apply {
        text = "Call"
        textSize = 11f
        setTextColor(Color.WHITE)
        setTypeface(null, android.graphics.Typeface.BOLD)
        gravity = android.view.Gravity.CENTER
        setBackgroundColor(Color.parseColor("#1565C0"))
        layoutParams = buttonLp
        setPadding(dp(4), dp(8), dp(4), dp(8))
        alpha = if (result.phone != null) 1.0f else 0.4f
        setOnClickListener {
            if (result.phone != null) {
                try {
                    startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${result.phone}")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                    })
                } catch (_: Exception) {}
            }
        }
    })

    // Reviews button
    buttonContainer.addView(TextView(this).apply {
        text = "Reviews"
        textSize = 11f
        setTextColor(Color.WHITE)
        setTypeface(null, android.graphics.Typeface.BOLD)
        gravity = android.view.Gravity.CENTER
        setBackgroundColor(Color.parseColor("#F57F17"))
        layoutParams = buttonLp
        setPadding(dp(4), dp(8), dp(4), dp(8))
        setOnClickListener {
            val name = Uri.encode(result.name ?: "")
            val loc = Uri.encode("${result.lat},${result.lon}")
            val yelpUrl = "https://www.yelp.com/search?find_desc=$name&find_loc=$loc"
            showFullScreenWebView(yelpUrl, "Reviews")
        }
    })

    // Map button
    buttonContainer.addView(TextView(this).apply {
        text = "Map"
        textSize = 11f
        setTextColor(Color.WHITE)
        setTypeface(null, android.graphics.Typeface.BOLD)
        gravity = android.view.Gravity.CENTER
        setBackgroundColor(Color.parseColor("#546E7A"))
        layoutParams = buttonLp
        setPadding(dp(4), dp(8), dp(4), dp(8))
        setOnClickListener {
            dialog.dismiss()
            val point = result.toGeoPoint()
            binding.mapView.controller.animateTo(point, 18.0, 800L)
            lifecycleScope.launch {
                delay(1000)
                loadCachedPoisForVisibleArea()
            }
        }
    })

    // Share button
    buttonContainer.addView(TextView(this).apply {
        text = "Share"
        textSize = 11f
        setTextColor(Color.WHITE)
        setTypeface(null, android.graphics.Typeface.BOLD)
        gravity = android.view.Gravity.CENTER
        setBackgroundColor(Color.parseColor("#00796B"))
        layoutParams = buttonLp
        setPadding(dp(4), dp(8), dp(4), dp(8))
        setOnClickListener {
            val shareText = buildString {
                append(result.name ?: result.typeValue.replaceFirstChar { it.uppercase() })
                if (result.address != null) append("\n${result.address}")
                if (result.phone != null) append("\nPhone: ${result.phone}")
                if (result.openingHours != null) append("\nHours: ${result.openingHours}")
                append("\nhttps://www.google.com/maps/search/?api=1&query=${result.lat},${result.lon}")
            }
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareText)
                putExtra(Intent.EXTRA_SUBJECT, result.name ?: "Shared Location")
            }
            startActivity(Intent.createChooser(shareIntent, "Share POI"))
        }
    })

    // ── Comments Section ──
    val commentsSection = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(8), dp(16), dp(8))
    }

    val commentsHeader = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = android.view.Gravity.CENTER_VERTICAL
        setPadding(0, dp(4), 0, dp(4))
    }
    val commentsTitle = TextView(this).apply {
        text = "Comments"
        textSize = 14f
        setTextColor(Color.parseColor("#9E9E9E"))
        setTypeface(null, android.graphics.Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    }
    val addCommentBtn = TextView(this).apply {
        text = "+ Add"
        textSize = 13f
        setTextColor(Color.parseColor("#64B5F6"))
        visibility = if (socialViewModel.isLoggedIn()) View.VISIBLE else View.GONE
    }
    commentsHeader.addView(commentsTitle)
    commentsHeader.addView(addCommentBtn)
    commentsSection.addView(commentsHeader)

    val commentsList = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
    }
    val commentsLoading = TextView(this).apply {
        text = "Loading comments..."
        textSize = 12f
        setTextColor(Color.parseColor("#80FFFFFF"))
        setPadding(0, dp(4), 0, dp(4))
    }
    commentsList.addView(commentsLoading)
    commentsSection.addView(commentsList)

    // Load comments from server
    val osmType = result.type
    val osmId = result.id
    socialViewModel.loadComments(osmType, osmId)

    fun renderComments(comments: List<com.example.locationmapapp.data.model.PoiComment>) {
        commentsList.removeAllViews()
        if (comments.isEmpty()) {
            commentsList.addView(TextView(this).apply {
                text = "No comments yet"
                textSize = 12f
                setTextColor(Color.parseColor("#80FFFFFF"))
                setPadding(0, dp(4), 0, dp(4))
            })
            return
        }
        commentsTitle.text = "Comments (${comments.size})"
        for (comment in comments.take(20)) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.parseColor("#2A2A2A"))
                setPadding(dp(10), dp(8), dp(10), dp(8))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(4) }
            }
            // Author line: name + time
            val authorLine = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            authorLine.addView(TextView(this).apply {
                text = comment.authorName
                textSize = 12f
                setTextColor(Color.parseColor("#4FC3F7"))
                setTypeface(null, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            // Relative time
            val relTime = try {
                val instant = java.time.Instant.parse(comment.createdAt)
                val dur = java.time.Duration.between(instant, java.time.Instant.now())
                when {
                    dur.toMinutes() < 1 -> "just now"
                    dur.toMinutes() < 60 -> "${dur.toMinutes()}m ago"
                    dur.toHours() < 24 -> "${dur.toHours()}h ago"
                    dur.toDays() < 30 -> "${dur.toDays()}d ago"
                    else -> comment.createdAt.take(10)
                }
            } catch (_: Exception) { comment.createdAt.take(10) }
            authorLine.addView(TextView(this).apply {
                text = relTime
                textSize = 11f
                setTextColor(Color.parseColor("#666666"))
            })
            card.addView(authorLine)

            // Rating stars
            val r = comment.rating
            if (r != null && r > 0) {
                card.addView(TextView(this).apply {
                    text = "\u2605".repeat(r) + "\u2606".repeat(5 - r)
                    textSize = 12f
                    setTextColor(Color.parseColor("#FFB300"))
                    setPadding(0, dp(2), 0, 0)
                })
            }

            // Content
            card.addView(TextView(this).apply {
                text = comment.content
                textSize = 13f
                setTextColor(Color.WHITE)
                setPadding(0, dp(4), 0, dp(2))
            })

            // Vote row
            val voteRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, dp(4), 0, 0)
            }
            val upBtn = TextView(this).apply {
                text = "\u25B2 ${comment.upvotes}"
                textSize = 11f
                setTextColor(if (comment.viewerVote == 1) Color.parseColor("#4CAF50") else Color.parseColor("#666666"))
                setPadding(0, 0, dp(12), 0)
            }
            val downBtn = TextView(this).apply {
                text = "\u25BC ${comment.downvotes}"
                textSize = 11f
                setTextColor(if (comment.viewerVote == -1) Color.parseColor("#F44336") else Color.parseColor("#666666"))
            }
            if (socialViewModel.isLoggedIn()) {
                upBtn.setOnClickListener {
                    socialViewModel.voteOnComment(comment.id, 1) { counts ->
                        runOnUiThread {
                            if (counts != null) {
                                upBtn.text = "\u25B2 ${counts.first}"
                                downBtn.text = "\u25BC ${counts.second}"
                                upBtn.setTextColor(Color.parseColor("#4CAF50"))
                                downBtn.setTextColor(Color.parseColor("#666666"))
                            }
                        }
                    }
                }
                downBtn.setOnClickListener {
                    socialViewModel.voteOnComment(comment.id, -1) { counts ->
                        runOnUiThread {
                            if (counts != null) {
                                upBtn.text = "\u25B2 ${counts.first}"
                                downBtn.text = "\u25BC ${counts.second}"
                                upBtn.setTextColor(Color.parseColor("#666666"))
                                downBtn.setTextColor(Color.parseColor("#F44336"))
                            }
                        }
                    }
                }
            }
            voteRow.addView(upBtn)
            voteRow.addView(downBtn)

            // Delete button for own comments (hide if already deleted)
            val currentUser = socialViewModel.authUser.value
            if (!comment.isDeleted && currentUser != null && (currentUser.id == comment.userId || currentUser.role in listOf("owner", "support"))) {
                voteRow.addView(View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(0, 0, 1f)
                })
                voteRow.addView(TextView(this).apply {
                    text = "Delete"
                    textSize = 11f
                    setTextColor(Color.parseColor("#EF5350"))
                    setOnClickListener {
                        socialViewModel.deleteComment(comment.id, osmType, osmId)
                    }
                })
            }

            card.addView(voteRow)
            commentsList.addView(card)
        }
    }

    socialViewModel.poiComments.observe(this) { comments ->
        renderComments(comments ?: emptyList())
    }

    // Add comment sub-dialog
    addCommentBtn.setOnClickListener {
        showAddCommentDialog(osmType, osmId)
    }

    // ── Container ──
    val scrollContent = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(infoContainer)
        addView(websiteArea)
        addView(commentsSection)
        addView(buttonContainer)
    }
    val scrollView = android.widget.ScrollView(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        )
        addView(scrollContent)
    }
    val container = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(Color.parseColor("#1A1A1A"))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        )
        addView(header)
        addView(colorBar)
        addView(scrollView)
    }

    dialog.setContentView(container)
    dialog.window?.let { win ->
        val dm = resources.displayMetrics
        win.setLayout((dm.widthPixels * 0.90).toInt(), (dm.heightPixels * 0.85).toInt())
        win.setBackgroundDrawableResource(android.R.color.transparent)
        win.setGravity(android.view.Gravity.CENTER)
    }
    dialog.show()
}

@SuppressLint("SetJavaScriptEnabled")
internal fun SalemMainActivity.showFullScreenWebView(url: String, title: String) {
    val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)

    val density = resources.displayMetrics.density
    val dp = { v: Int -> (v * density).toInt() }
    var webView: android.webkit.WebView? = null

    // ── Top bar: back + title + close ──
    val backBtn = TextView(this).apply {
        text = "\u2190"
        textSize = 22f
        setTextColor(Color.WHITE)
        setPadding(dp(12), dp(6), dp(12), dp(6))
        setOnClickListener {
            if (webView?.canGoBack() == true) webView?.goBack() else dialog.dismiss()
        }
    }
    val titleText = TextView(this).apply {
        text = title
        textSize = 16f
        setTextColor(Color.WHITE)
        setTypeface(null, android.graphics.Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
    }
    val closeBtn = TextView(this).apply {
        text = "\u2715"
        textSize = 20f
        setTextColor(Color.WHITE)
        setPadding(dp(12), dp(6), dp(12), dp(6))
        setOnClickListener { dialog.dismiss() }
    }
    val topBar = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = android.view.Gravity.CENTER_VERTICAL
        setBackgroundColor(Color.parseColor("#1A1A1A"))
        setPadding(dp(4), dp(4), dp(4), dp(4))
        addView(backBtn)
        addView(titleText)
        addView(closeBtn)
    }

    // ── WebView ──
    val wv = android.webkit.WebView(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        )
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.setSupportZoom(true)
        setInitialScale(0)
        setBackgroundColor(Color.BLACK)
        webViewClient = object : android.webkit.WebViewClient() {
            override fun onRenderProcessGone(
                view: android.webkit.WebView?,
                detail: android.webkit.RenderProcessGoneDetail?
            ): Boolean {
                dialog.dismiss()
                return true
            }
        }
        loadUrl(url)
    }
    webView = wv

    val container = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(Color.BLACK)
        addView(topBar)
        addView(wv)
    }

    dialog.setContentView(container)
    dialog.window?.let { win ->
        win.setLayout(
            android.view.WindowManager.LayoutParams.MATCH_PARENT,
            android.view.WindowManager.LayoutParams.MATCH_PARENT
        )
        win.setBackgroundDrawableResource(android.R.color.transparent)
    }
    dialog.setOnDismissListener { wv.destroy() }
    dialog.show()
}

/** Distance in meters between two GeoPoints (null-safe — returns MAX_VALUE if from is null). */
internal fun SalemMainActivity.distanceBetween(from: GeoPoint?, to: GeoPoint): Float {
    if (from == null) return Float.MAX_VALUE
    val results = FloatArray(1)
    android.location.Location.distanceBetween(
        from.latitude, from.longitude, to.latitude, to.longitude, results)
    return results[0]
}

/** Format distance + cardinal direction between two points. */
internal fun SalemMainActivity.formatDistanceDirection(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): String {
    val results = FloatArray(2)
    android.location.Location.distanceBetween(fromLat, fromLon, toLat, toLon, results)
    val distM = results[0]
    val bearing = results[1]
    val cardinal = bearingToCardinal(bearing)
    val distMi = distM / 1609.34

    return when {
        distMi < 0.1 -> "%.0f ft %s".format(distM * 3.28084, cardinal)
        distMi < 10.0 -> "%.1f mi %s".format(distMi, cardinal)
        else -> "%.0f mi %s".format(distMi, cardinal)
    }
}

internal fun SalemMainActivity.bearingToCardinal(bearing: Float): String {
    val normalized = ((bearing % 360) + 360) % 360
    return when {
        normalized < 22.5 || normalized >= 337.5 -> "N"
        normalized < 67.5 -> "NE"
        normalized < 112.5 -> "E"
        normalized < 157.5 -> "SE"
        normalized < 202.5 -> "S"
        normalized < 247.5 -> "SW"
        normalized < 292.5 -> "W"
        else -> "NW"
    }
}

// ── Map Filter Mode ──────────────────────────────────────────────────────

internal fun SalemMainActivity.enterFindFilterMode(tags: List<String>, label: String) {
    findFilterActive = true
    findFilterTags = tags
    findFilterLabel = label
    DebugLogger.i("SalemMainActivity", "enterFindFilterMode: $label tags=$tags")

    // Show dismissible banner
    showFindFilterBanner(label)

    // Load filtered POIs
    loadFilteredPois()
}

@SuppressLint("SetTextI18n")
internal fun SalemMainActivity.showFindFilterBanner(label: String) {
    removeFindFilterBanner()
    val density = resources.displayMetrics.density
    val dp = { v: Int -> (v * density).toInt() }

    val banner = TextView(this).apply {
        text = "Showing: $label  \u2715"
        textSize = 13f
        setTextColor(Color.WHITE)
        setBackgroundColor(Color.parseColor("#CC333333"))
        setPadding(dp(16), dp(8), dp(16), dp(8))
        gravity = android.view.Gravity.CENTER
        setOnClickListener { exitFindFilterMode() }
    }

    val parent = binding.mapView.parent as? ViewGroup ?: return
    val lp = android.widget.FrameLayout.LayoutParams(
        android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
        android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
        android.view.Gravity.TOP
    )
    parent.addView(banner, lp)
    findFilterBanner = banner
}

internal fun SalemMainActivity.removeFindFilterBanner() {
    findFilterBanner?.let {
        (it.parent as? ViewGroup)?.removeView(it)
    }
    findFilterBanner = null
}

internal fun SalemMainActivity.loadFilteredPois() {
    val center = binding.mapView.mapCenter
    lifecycleScope.launch {
        val response = findViewModel.findNearbyDirectly(center.latitude, center.longitude, findFilterTags, 200)
        if (response != null) {
            val places = response.results.map { it.toPlaceResult() }
            replaceAllPoiMarkers(places)
        }
    }
}

internal fun SalemMainActivity.exitFindFilterMode() {
    DebugLogger.i("SalemMainActivity", "exitFindFilterMode")
    findFilterActive = false
    findFilterTags = emptyList()
    findFilterLabel = ""
    removeFindFilterBanner()
    // Restore normal POI display
    loadCachedPoisForVisibleArea()
}

// ── Filter and Map Mode ──────────────────────────────────────────────────

@SuppressLint("SetTextI18n")
internal fun SalemMainActivity.enterFilterAndMapMode(results: List<com.example.locationmapapp.data.model.FindResult>, label: String) {
    // Exit existing filter modes
    if (findFilterActive) exitFindFilterMode()
    if (filterAndMapActive) exitFilterAndMapMode()

    filterAndMapActive = true
    filterAndMapResults = results
    filterAndMapLabel = label
    DebugLogger.i("SalemMainActivity", "enterFilterAndMapMode: $label (${results.size} results)")

    // Stop all background jobs
    stopSilentFill()
    if (populateJob != null) stopPopulatePois()
    if (idlePopulateJob?.isActive == true) stopIdlePopulate()
    stopAircraftRefresh()
    trainRefreshJob?.cancel(); trainRefreshJob = null
    subwayRefreshJob?.cancel(); subwayRefreshJob = null
    busRefreshJob?.cancel(); busRefreshJob = null
    followedAircraftRefreshJob?.cancel(); followedAircraftRefreshJob = null
    autoFollowAircraftJob?.cancel(); autoFollowAircraftJob = null

    // Clear follow state
    followedVehicleId = null
    followedAircraftIcao = null
    statusLineManager.clear(StatusLineManager.Priority.VEHICLE_FOLLOW)
    statusLineManager.clear(StatusLineManager.Priority.AIRCRAFT_FOLLOW)

    // Save and clear radar state
    savedRadarWasAnimating = radarAnimating
    savedRadarWasOn = radarTileOverlay != null || radarAnimating
    if (radarAnimating) stopRadarAnimation()
    if (radarTileOverlay != null) {
        binding.mapView.overlays.remove(radarTileOverlay); radarTileOverlay = null
    }

    // Clear ALL overlays except GPS marker
    clearAllPoiMarkers()
    clearTrainMarkers(); clearSubwayMarkers(); clearBusMarkers()
    clearStationMarkers(); clearBusStopMarkers()
    clearAircraftMarkers()
    clearFlightTrail()
    clearWebcamMarkers()
    clearMetarMarkers()
    clearAllGeofenceOverlays()
    binding.mapView.invalidate()

    // Convert results → PlaceResult and add as labeled markers under "filter-map" layer
    val places = results.map { it.toPlaceResult() }
    for (place in places) {
        val m = Marker(binding.mapView).apply {
            position = GeoPoint(place.lat, place.lon)
            icon = MarkerIconHelper.labeledDot(this@enterFilterAndMapMode, place.category, place.name)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            title = place.name
            snippet = buildPlaceSnippet(place)
            relatedObject = place
            setOnMarkerClickListener { _, _ ->
                openPoiDetailFromPlace(place)
                true
            }
        }
        poiMarkers.getOrPut("filter-map") { mutableListOf() }.add(m)
        binding.mapView.overlays.add(m)
    }

    // Keep current position, start at zoom 18 and zoom out until results are visible
    val center = binding.mapView.mapCenter
    var zoom = 18.0
    binding.mapView.controller.setZoom(zoom)
    binding.mapView.invalidate()
    // Check if any results are in the viewport; if not, step down zoom
    if (places.isNotEmpty()) {
        while (zoom >= 3.0) {
            binding.mapView.controller.setZoom(zoom)
            // Force layout so bounding box is accurate
            binding.mapView.layoutParams = binding.mapView.layoutParams
            val bb = binding.mapView.boundingBox
            val visible = places.any { bb.contains(GeoPoint(it.lat, it.lon)) }
            if (visible) break
            zoom -= 1.0
        }
    }
    binding.mapView.invalidate()

    // Status line with tap-to-exit
    statusLineManager.set(
        StatusLineManager.Priority.FIND_FILTER,
        "Showing ${results.size} $label — tap to clear"
    ) { exitFilterAndMapMode() }
}

internal fun SalemMainActivity.exitFilterAndMapMode() {
    if (!filterAndMapActive) return
    DebugLogger.i("SalemMainActivity", "exitFilterAndMapMode")

    filterAndMapActive = false
    filterAndMapResults = emptyList()
    filterAndMapLabel = ""

    // Clear status line
    statusLineManager.clear(StatusLineManager.Priority.FIND_FILTER)

    // Clear filter-map markers
    clearPoiMarkers("filter-map")

    // Restore normal POI display
    loadCachedPoisForVisibleArea()

    // Restore radar if it was on
    if (savedRadarWasOn) {
        addRadarOverlay()
    }
    savedRadarWasOn = false
    savedRadarWasAnimating = false

    // Fix label state to match current zoom
    val zoom = binding.mapView.zoomLevelDouble
    poiLabelsShowing = zoom >= 18.0
    refreshPoiMarkerIcons()

    // Other layers (transit, aircraft, webcams, METAR, geofences) will
    // naturally restore on the next scroll/zoom event based on current prefs
}

// ── Legend Dialog ─────────────────────────────────────────────────────────

// =========================================================================
// GO TO LOCATION — geocoder dialog
// =========================================================================

@SuppressLint("SetTextI18n")
internal fun SalemMainActivity.showGoToLocationDialog() {
    val density = resources.displayMetrics.density
    val dp = { v: Int -> (v * density).toInt() }

    val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)

    // ── Header ──
    val titleText = TextView(this).apply {
        text = "Go to Location"
        textSize = 18f
        setTextColor(Color.WHITE)
        setTypeface(null, android.graphics.Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    }
    val closeBtn = TextView(this).apply {
        text = "\u2715"
        textSize = 20f
        setTextColor(Color.WHITE)
        setPadding(dp(12), 0, dp(4), 0)
        setOnClickListener { dialog.dismiss() }
    }
    val header = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(dp(16), dp(12), dp(12), dp(8))
        gravity = android.view.Gravity.CENTER_VERTICAL
        addView(titleText)
        addView(closeBtn)
    }

    // ── Results container (scrollable) ──
    val resultsContainer = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), 0, dp(16), dp(16))
    }
    val scrollView = android.widget.ScrollView(this).apply {
        addView(resultsContainer)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        )
    }

    // ── Search input area ──
    val input = android.widget.EditText(this).apply {
        hint = "City, state, address, or zip..."
        setHintTextColor(Color.GRAY)
        setTextColor(Color.WHITE)
        textSize = 16f
        setBackgroundColor(Color.argb(50, 255, 255, 255))
        setPadding(dp(12), dp(10), dp(12), dp(10))
        setSingleLine(true)
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    }
    val searchBtn = TextView(this).apply {
        text = "Search"
        textSize = 14f
        setTextColor(Color.WHITE)
        setTypeface(null, android.graphics.Typeface.BOLD)
        setBackgroundColor(Color.argb(180, 0, 150, 136))
        setPadding(dp(16), dp(10), dp(16), dp(10))
    }
    val inputRow = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(dp(16), dp(8), dp(16), dp(8))
        gravity = android.view.Gravity.CENTER_VERTICAL
        addView(input)
        addView(searchBtn)
    }

    // ── Nominatim geocode via proxy ──
    val geocodeClient = okhttp3.OkHttpClient.Builder()
        .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .addInterceptor(com.example.locationmapapp.util.network.OfflineModeInterceptor())
        .addInterceptor(com.example.locationmapapp.util.network.LocalServerCircuitBreakerInterceptor())
        .build()

    val doSearch = {
        val query = input.text.toString().trim()
        if (query.isEmpty()) {
            toast("Enter a location to search")
        } else {
            resultsContainer.removeAllViews()
            val searching = TextView(this).apply {
                text = "Searching..."
                textSize = 14f
                setTextColor(Color.GRAY)
                setPadding(0, dp(12), 0, 0)
            }
            resultsContainer.addView(searching)

            lifecycleScope.launch {
                try {
                    val results = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        val url = okhttp3.HttpUrl.Builder()
                            .scheme("http").host("10.0.0.229").port(3000)
                            .addPathSegment("geocode")
                            .addQueryParameter("q", query)
                            .addQueryParameter("limit", "5")
                            .build()
                        val request = okhttp3.Request.Builder().url(url).build()
                        val response = geocodeClient.newCall(request).execute()
                        if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
                        val body = response.body?.string() ?: "[]"
                        com.google.gson.Gson().fromJson(
                            body, Array<GeocodeSuggestion>::class.java
                        ).toList()
                    }
                    resultsContainer.removeAllViews()
                    if (results.isEmpty()) {
                        val noResults = TextView(this@showGoToLocationDialog).apply {
                            text = "No results found"
                            textSize = 14f
                            setTextColor(Color.GRAY)
                            setPadding(0, dp(12), 0, 0)
                        }
                        resultsContainer.addView(noResults)
                    } else {
                        for (geo in results) {
                            val label = geo.display_name
                            val row = TextView(this@showGoToLocationDialog).apply {
                                text = label
                                textSize = 15f
                                setTextColor(Color.WHITE)
                                setPadding(dp(8), dp(14), dp(8), dp(14))
                                val bg = android.graphics.drawable.GradientDrawable().apply {
                                    setColor(Color.argb(40, 255, 255, 255))
                                    cornerRadius = dp(4).toFloat()
                                }
                                background = bg
                                val lp = LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT,
                                    LinearLayout.LayoutParams.WRAP_CONTENT
                                )
                                lp.bottomMargin = dp(4)
                                layoutParams = lp
                                setOnClickListener {
                                    val point = GeoPoint(geo.lat, geo.lon)
                                    val shortLabel = listOfNotNull(geo.city, geo.state)
                                        .joinToString(", ").ifEmpty { label }
                                    dialog.dismiss()
                                    goToLocation(point, shortLabel)
                                }
                            }
                            resultsContainer.addView(row)
                        }
                    }
                } catch (e: Exception) {
                    DebugLogger.e("SalemMainActivity", "Geocode error: ${e.message}")
                    resultsContainer.removeAllViews()
                    val errView = TextView(this@showGoToLocationDialog).apply {
                        text = "Geocoder unavailable"
                        textSize = 14f
                        setTextColor(Color.argb(255, 255, 100, 100))
                        setPadding(0, dp(12), 0, 0)
                    }
                    resultsContainer.addView(errView)
                }
            }
        }
    }

    searchBtn.setOnClickListener { doSearch() }
    input.setOnEditorActionListener { _, actionId, _ ->
        if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE ||
            actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
            doSearch()
            true
        } else false
    }

    // ── Auto-suggest as you type (500ms debounce, >= 3 chars) ──
    var autoSearchJob: kotlinx.coroutines.Job? = null
    input.addTextChangedListener(object : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: android.text.Editable?) {
            autoSearchJob?.cancel()
            val query = s?.toString()?.trim() ?: ""
            if (query.length < 3) {
                resultsContainer.removeAllViews()
                return
            }
            autoSearchJob = lifecycleScope.launch {
                delay(500)
                doSearch()
            }
        }
    })

    // ── Assemble layout ──
    val root = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(Color.argb(240, 30, 30, 30))
        addView(header)
        addView(inputRow)
        addView(scrollView)
    }
    dialog.setContentView(root)
    dialog.show()

    // Auto-show keyboard
    input.requestFocus()
    input.postDelayed({
        val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.showSoftInput(input, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
    }, 200)
}

/**
 * Navigate map to a geocoded location — mirrors the long-press handler pattern.
 */
internal fun SalemMainActivity.goToLocation(point: GeoPoint, label: String) {
    DebugLogger.i("SalemMainActivity", "goToLocation: ${point.latitude},${point.longitude} — $label")
    // Stop any active scanner / idle scanner / silent fill
    if (populateJob != null) stopPopulatePois()
    stopProbe10km()
    stopIdlePopulate(clearState = true)
    stopSilentFill()
    // Switch to manual mode at new location
    viewModel.setManualLocation(point)
    val targetZoom = if (binding.mapView.zoomLevelDouble < 18.0) 18.0 else binding.mapView.zoomLevelDouble
    binding.mapView.controller.animateTo(point, targetZoom, null)
    triggerFullSearch(point)
    // Programmatic animateTo doesn't fire onScroll — schedule bbox POI refresh
    cachePoiJob?.cancel()
    cachePoiJob = lifecycleScope.launch {
        delay(2000)
        loadCachedPoisForVisibleArea()
    }
    scheduleSilentFill(point, 3000)
    toast("Moved to: $label")
}

