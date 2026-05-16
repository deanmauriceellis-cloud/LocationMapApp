/*
 * WickedSalemWitchCityTour v1.5
 * Copyright (c) 2026 Destructive AI Gurus, LLC. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.wickedsalemwitchcitytour.ui

import android.app.AlertDialog
import android.app.Dialog
import android.content.DialogInterface
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.locationmapapp.util.DebugLogger
import com.example.wickedsalemwitchcitytour.R
import com.example.wickedsalemwitchcitytour.content.dao.PoiPassportDao
import com.example.wickedsalemwitchcitytour.content.model.PoiPassport
import com.example.wickedsalemwitchcitytour.content.model.SalemPoi
import com.example.wickedsalemwitchcitytour.tour.TourState
import com.example.wickedsalemwitchcitytour.userdata.dao.PassportVisitDao
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * S268 — POI Passport sheet.
 *
 * Replaces the four leftover stops-based tour UIs (HUD banner, Active Tour
 * dialog, completion-stats body, numbered polyline pins). Shows the operator-
 * authored passport list (from baked Room `poi_passport`) joined against the
 * user's lifetime visit log (`passport_visit` in `user_data.db`). Each POI
 * row shows a filled/hollow witch-hat stamp + heard-count + first-heard date.
 *
 * Persistence:
 *   - The passport list itself is baked into salem_content.db (read-only).
 *   - Visit state lives in user_data.db, POI-keyed only — survives launches
 *     and force-stops. Wiped only by `adb uninstall` or the in-sheet Reset.
 *
 * Plan: `docs/plans/poi-passport-replaces-tour-stops.md`. Entry point: new
 * witch-hat icon on the toolbar (S268 toolbar wiring).
 */
@AndroidEntryPoint
class PassportSheet : DialogFragment() {

    @Inject internal lateinit var poiPassportDao: PoiPassportDao
    @Inject internal lateinit var passportVisitDao: PassportVisitDao

    /** Active passport id; null means "first available from the bake." */
    private var activePassportId: String? = null

    /** All passport summaries baked in salem_content.db. Loaded once on open. */
    private var allPassports: List<PoiPassportDao.PassportSummary> = emptyList()

    /** Rows for the currently-displayed passport, flat for the RecyclerView adapter. */
    private val rows = mutableListOf<RowItem>()

    private lateinit var titleView: TextView
    private lateinit var counterView: TextView
    private lateinit var pickerView: TextView
    private lateinit var emptyView: TextView
    private lateinit var listView: RecyclerView
    private lateinit var overflowView: ImageView
    private lateinit var closeView: ImageView

    // ── Dialog config (matches PoiDetailSheet's full-screen pattern) ────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_FRAME, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.BLACK))
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
            )
        }
        return dialog
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT
        )
    }

    override fun onCancel(dialog: DialogInterface) { super.onCancel(dialog) }
    override fun onDismiss(dialog: DialogInterface) { super.onDismiss(dialog) }

    // ── View ────────────────────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.sheet_passport, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        titleView    = view.findViewById(R.id.passportTitle)
        counterView  = view.findViewById(R.id.passportCounter)
        pickerView   = view.findViewById(R.id.passportPicker)
        emptyView    = view.findViewById(R.id.passportEmpty)
        listView     = view.findViewById(R.id.passportList)
        overflowView = view.findViewById(R.id.passportOverflow)
        closeView    = view.findViewById(R.id.passportClose)

        listView.layoutManager = LinearLayoutManager(requireContext())
        listView.adapter = adapter

        closeView.setOnClickListener { dismissAllowingStateLoss() }
        overflowView.setOnClickListener { showOverflowMenu(it) }
        pickerView.setOnClickListener { showPassportPickerMenu(it) }

        // S269 — caller-supplied target passport (e.g. tour-completion CTA
        // hands us the just-ended tour's passportId; toolbar tap during an
        // active tour passes the active tour's passportId). When null we
        // fall back to the first passport from the bake.
        val argPid = arguments?.getString(ARG_PASSPORT_ID)
        if (!argPid.isNullOrBlank()) activePassportId = argPid

        loadAll()
    }

    // ── Data load ───────────────────────────────────────────────────────

    private fun loadAll() {
        lifecycleScope.launch {
            val snapshot = withContext(Dispatchers.IO) {
                val passports = poiPassportDao.listPassports()
                if (passports.isEmpty()) {
                    LoadSnapshot(emptyList(), null, emptyList(), 0, 0)
                } else {
                    val pid = activePassportId ?: passports.first().passport_id
                    val (rows, heard, total) = buildRowsForPassport(pid)
                    LoadSnapshot(passports, pid, rows, heard, total)
                }
            }
            allPassports = snapshot.passports
            activePassportId = snapshot.activePid
            applyState(snapshot.rows, snapshot.heardCount, snapshot.totalCount)
        }
    }

    private suspend fun buildRowsForPassport(passportId: String): RowResult {
        val pois = poiPassportDao.findByPassport(passportId)
        if (pois.isEmpty()) return RowResult(emptyList(), 0, 0)
        val heardIds = passportVisitDao.listHeardAmong(pois.map { it.poiId }).toSet()

        // Pull last-heard / heard-count details for the heard set.
        val visitMap = passportVisitDao.listAll()
            .filter { it.poiId in heardIds }
            .associateBy { it.poiId }

        val grouped = pois.groupBy { it.poiCategory }
        val result = mutableListOf<RowItem>()
        // Stable category order: by total POI count desc, then label asc.
        val orderedCats = grouped.entries.sortedWith(
            compareByDescending<Map.Entry<String, List<PoiPassport>>> { it.value.size }
                .thenBy { it.key }
        )
        for ((cat, items) in orderedCats) {
            val heardInCat = items.count { it.poiId in heardIds }
            result += RowItem.Header(prettyCategory(cat), heardInCat, items.size)
            items.sortedBy { it.displayOrder }.forEach { p ->
                val visit = visitMap[p.poiId]
                result += RowItem.Poi(p, heard = visit != null, heardCount = visit?.heardCount ?: 0,
                    firstHeardAtMs = visit?.firstHeardAtMs ?: 0L)
            }
        }
        return RowResult(result, heardIds.size, pois.size)
    }

    private fun applyState(newRows: List<RowItem>, heard: Int, total: Int) {
        rows.clear()
        rows.addAll(newRows)
        adapter.notifyDataSetChanged()

        val active = allPassports.firstOrNull { it.passport_id == activePassportId }
            ?: allPassports.firstOrNull()
        titleView.text = active?.passport_name ?: "POI Passport"
        if (allPassports.isEmpty() || newRows.isEmpty()) {
            counterView.text = "0 / 0"
            emptyView.visibility = View.VISIBLE
            listView.visibility = View.GONE
        } else {
            counterView.text = "$heard / $total"
            emptyView.visibility = View.GONE
            listView.visibility = View.VISIBLE
        }
        pickerView.visibility = if (allPassports.size > 1) View.VISIBLE else View.GONE
        if (pickerView.visibility == View.VISIBLE) {
            pickerView.text = "Switch passport ▾"
        }
    }

    // ── Overflow / picker menus ─────────────────────────────────────────

    private fun showOverflowMenu(anchor: View) {
        val popup = PopupMenu(requireContext(), anchor)
        // S269 — when a tour is in flight (Active / Paused / Detour), the
        // sheet picks up the canonical "End tour" action here. Reads the
        // engine state through SalemMainActivity.tourViewModel; absent or
        // Idle states omit the entry. Operator-confirmed: more in-tour
        // actions will land here later.
        val activity = (requireActivity() as? SalemMainActivity)
        val tourActive = when (activity?.tourViewModel?.tourState?.value) {
            is TourState.Active, is TourState.Paused, is TourState.Detour -> true
            else -> false
        }
        if (tourActive) {
            popup.menu.add(0, MENU_END_TOUR, 0, "End tour")
        }
        popup.menu.add(0, MENU_RESET, 1, "Reset all stamps")
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_END_TOUR -> {
                    activity?.tourViewModel?.endTour()
                    dismissAllowingStateLoss()
                    true
                }
                MENU_RESET -> { confirmReset(); true }
                else -> false
            }
        }
        popup.show()
    }

    private fun showPassportPickerMenu(anchor: View) {
        if (allPassports.size <= 1) return
        val popup = PopupMenu(requireContext(), anchor)
        allPassports.forEachIndexed { idx, p ->
            val tag = if (p.tour_id == null) "(global)" else "(tour)"
            popup.menu.add(0, idx, idx, "${p.passport_name}  $tag")
        }
        popup.setOnMenuItemClickListener { item ->
            val picked = allPassports.getOrNull(item.itemId) ?: return@setOnMenuItemClickListener true
            if (picked.passport_id != activePassportId) {
                activePassportId = picked.passport_id
                loadAll()
            }
            true
        }
        popup.show()
    }

    private fun confirmReset() {
        AlertDialog.Builder(requireContext())
            .setTitle("Reset all stamps?")
            .setMessage("This clears every POI you've ever heard from the Passport. This cannot be undone.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Reset") { _, _ ->
                lifecycleScope.launch {
                    val n = withContext(Dispatchers.IO) { passportVisitDao.deleteAll() }
                    DebugLogger.i(TAG, "Passport reset: deleted $n visit rows")
                    loadAll()
                }
            }
            .show()
    }

    // ── RecyclerView adapter ────────────────────────────────────────────

    private val adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount(): Int = rows.size
        override fun getItemViewType(position: Int): Int =
            if (rows[position] is RowItem.Header) TYPE_HEADER else TYPE_POI

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val li = LayoutInflater.from(parent.context)
            return if (viewType == TYPE_HEADER) {
                HeaderVh(li.inflate(R.layout.item_passport_category_header, parent, false))
            } else {
                PoiVh(li.inflate(R.layout.item_passport_row, parent, false))
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val row = rows[position]) {
                is RowItem.Header -> (holder as HeaderVh).bind(row)
                is RowItem.Poi    -> (holder as PoiVh).bind(row)
            }
        }
    }

    private inner class HeaderVh(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val label: TextView = itemView.findViewById(R.id.headerLabel)
        private val count: TextView = itemView.findViewById(R.id.headerCount)
        fun bind(h: RowItem.Header) {
            label.text = h.label
            count.text = "${h.heardCount} / ${h.totalCount}"
        }
    }

    private inner class PoiVh(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val stamp: ImageView = itemView.findViewById(R.id.rowStamp)
        private val name: TextView = itemView.findViewById(R.id.rowName)
        private val detail: TextView = itemView.findViewById(R.id.rowDetail)
        fun bind(p: RowItem.Poi) {
            name.text = p.entry.poiName
            if (p.heard) {
                stamp.alpha = 1.0f
                stamp.setColorFilter(ContextCompat.getColor(itemView.context, R.color.passport_stamp_filled))
                val dateStr = SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(p.firstHeardAtMs))
                detail.text = "Heard ${p.heardCount}× · first $dateStr"
                detail.setTextColor(0xFFA89D85.toInt())
            } else {
                stamp.alpha = 0.25f
                stamp.setColorFilter(0xFFA89D85.toInt())
                detail.text = "Unheard"
                detail.setTextColor(0xFF7C8085.toInt())
            }
            itemView.setOnClickListener { openPoiDetail(p.entry.poiId) }
        }
    }

    private fun openPoiDetail(poiId: String) {
        lifecycleScope.launch {
            val activity = (requireActivity() as? SalemMainActivity)
            val poi: SalemPoi? = withContext(Dispatchers.IO) {
                activity?.poiCache?.findById(poiId)
            }
            if (poi == null) {
                DebugLogger.w(TAG, "openPoiDetail: POI not found in cache id=$poiId")
                return@launch
            }
            PoiDetailSheet.show(poi, parentFragmentManager)
        }
    }

    // ── Row types + helpers ─────────────────────────────────────────────

    private sealed class RowItem {
        data class Header(val label: String, val heardCount: Int, val totalCount: Int) : RowItem()
        data class Poi(
            val entry: PoiPassport,
            val heard: Boolean,
            val heardCount: Int,
            val firstHeardAtMs: Long,
        ) : RowItem()
    }

    private fun prettyCategory(raw: String): String {
        return raw.replace('_', ' ').lowercase(Locale.US)
            .split(' ')
            .joinToString(" ") { w -> w.replaceFirstChar { c -> c.uppercase() } }
    }

    private data class LoadSnapshot(
        val passports: List<PoiPassportDao.PassportSummary>,
        val activePid: String?,
        val rows: List<RowItem>,
        val heardCount: Int,
        val totalCount: Int,
    )

    private data class RowResult(
        val rows: List<RowItem>,
        val heardCount: Int,
        val totalCount: Int,
    )

    companion object {
        private const val TAG = "PassportSheet"
        private const val TAG_SHOW = "passport_sheet"
        private const val ARG_PASSPORT_ID = "arg_passport_id"
        private const val MENU_RESET = 1
        private const val MENU_END_TOUR = 2
        private const val TYPE_HEADER = 0
        private const val TYPE_POI = 1

        /**
         * S269 — `passportId` lets the caller pin the sheet to a specific
         * passport on open (e.g. tour-completion CTA → the just-ended tour's
         * passport; toolbar tap during an active tour → the active tour's
         * passport). Pass null (or omit) for "first passport in the bake".
         */
        fun show(fragmentManager: FragmentManager, passportId: String? = null) {
            (fragmentManager.findFragmentByTag(TAG_SHOW) as? PassportSheet)?.dismissAllowingStateLoss()
            val sheet = PassportSheet()
            if (!passportId.isNullOrBlank()) {
                sheet.arguments = Bundle().apply { putString(ARG_PASSPORT_ID, passportId) }
            }
            sheet.show(fragmentManager, TAG_SHOW)
        }
    }
}
