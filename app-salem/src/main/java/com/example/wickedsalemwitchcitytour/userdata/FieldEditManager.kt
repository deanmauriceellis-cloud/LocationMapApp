/*
 * WickedSalemWitchCityTour v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.wickedsalemwitchcitytour.userdata

import android.content.Context
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.locationmapapp.util.DebugLogger
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * S229 — third top-bar button: field-edit mode.
 *
 * When the operator toggles the icon ON and taps a POI marker on the map, an
 * `ActiveEdit` is opened: the operator can record a proposed move, a category
 * change, a subcategory change, a free-text note, and one or more recon-photo
 * filenames. On Save, the edit is appended as a single JSON line to the
 * session's JSONL file at:
 *
 *   /sdcard/Documents/WickedSalemFieldEdits/edits-YYYYMMDD-HHMMSS.jsonl
 *
 * The file is created lazily on first save. One file per process boot — easier
 * to triage at home (one walk = one file) and survives crashes (every line is
 * an independently-parseable JSON object).
 *
 * State machine:
 *   - enabled = false → no UI hook, normal POI taps open the detail dialog.
 *   - enabled = true  → POI taps call [openEditFor], producing an ActiveEdit.
 *
 * Within an active edit the operator may invoke "Move here", which puts the
 * Activity into a tap-to-place mode. The activity's map onSingleTap calls
 * [setProposedLocation], which captures lat/lng into the active edit and
 * re-opens the bottom sheet.
 *
 * R8 strips this class entirely in retail builds because every call site is
 * gated by [com.example.wickedsalemwitchcitytour.ui.BuildDefaults.FIELD_EDIT_ENABLED]
 * (a `const val` wired to `BuildConfig.RECON_DEFAULTS = false` for release).
 */
class FieldEditManager(
    private val activity: AppCompatActivity,
) {
    private val context: Context get() = activity.applicationContext

    @Volatile private var enabled: Boolean = false
    @Volatile var activeEdit: ActiveEdit? = null
        private set
    @Volatile private var pendingCount: Int = 0
    private val sessionTs: String = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())

    /** Listeners on the count get notified after every successful append so the
     *  toolbar badge can refresh. Single subscriber for now. */
    private var countListener: ((Int) -> Unit)? = null

    fun setCountListener(listener: ((Int) -> Unit)?) { countListener = listener }

    fun isEnabled(): Boolean = enabled
    fun pendingCount(): Int = pendingCount

    fun setEnabled(enable: Boolean) {
        if (enable == enabled) return
        enabled = enable
        if (!enable) {
            // Discard any in-flight edit when the operator turns the mode off.
            activeEdit = null
        }
        DebugLogger.i(TAG, "setEnabled $enable (pending=$pendingCount)")
        Toast.makeText(
            activity,
            if (enable) "Field-edit ON — tap a POI to capture an edit"
                else "Field-edit OFF",
            Toast.LENGTH_SHORT
        ).show()
    }

    /** Called by the activity when the operator taps a POI in field-edit mode. */
    fun openEditFor(
        poiId: String,
        poiName: String,
        currentLat: Double?,
        currentLng: Double?,
        currentCategory: String?,
        currentSubcategory: String?,
    ): ActiveEdit {
        val edit = ActiveEdit(
            kind = Kind.UPDATE,
            poiId = poiId,
            poiName = poiName,
            currentLat = currentLat,
            currentLng = currentLng,
            currentCategory = currentCategory,
            currentSubcategory = currentSubcategory,
        )
        activeEdit = edit
        DebugLogger.i(TAG, "openEditFor id=$poiId name=$poiName")
        return edit
    }

    /**
     * S230 — operator long-pressed an empty spot on the map in field-edit mode.
     * Open a fresh CREATE edit pre-filled with that lat/lng. The operator must
     * still type a name before [saveActiveEdit] will accept it.
     */
    fun openCreateAt(lat: Double, lng: Double): ActiveEdit {
        val edit = ActiveEdit(
            kind = Kind.CREATE,
            poiId = null,
            poiName = "",
            currentLat = null,
            currentLng = null,
            currentCategory = null,
            currentSubcategory = null,
        )
        edit.proposedLat = lat
        edit.proposedLng = lng
        activeEdit = edit
        DebugLogger.i(TAG, "openCreateAt $lat,$lng")
        return edit
    }

    fun setProposedName(name: String?) {
        activeEdit?.proposedName = name?.takeIf { it.isNotBlank() }
    }

    /** Operator finished a tap-to-place pass — capture the new lat/lng. */
    fun setProposedLocation(lat: Double, lng: Double) {
        val edit = activeEdit ?: run {
            DebugLogger.w(TAG, "setProposedLocation: no active edit")
            return
        }
        edit.proposedLat = lat
        edit.proposedLng = lng
        DebugLogger.i(TAG, "setProposedLocation $lat,$lng for ${edit.poiId}")
    }

    fun setProposedCategory(category: String?) {
        activeEdit?.proposedCategory = category
    }

    fun setProposedSubcategory(subcategory: String?) {
        activeEdit?.proposedSubcategory = subcategory
    }

    fun setNote(note: String?) {
        activeEdit?.note = note?.takeIf { it.isNotBlank() }
    }

    fun addPhoto(filename: String) {
        val edit = activeEdit ?: return
        edit.photoFilenames.add(filename)
        DebugLogger.i(TAG, "addPhoto $filename to ${edit.poiId} (now ${edit.photoFilenames.size})")
    }

    fun cancelActiveEdit() {
        DebugLogger.i(TAG, "cancelActiveEdit ${activeEdit?.poiId}")
        activeEdit = null
    }

    /** Persist the active edit as one JSONL line and clear it. Returns true on success. */
    fun saveActiveEdit(): Boolean {
        val edit = activeEdit ?: run {
            DebugLogger.w(TAG, "saveActiveEdit: no active edit")
            return false
        }
        val obj = JSONObject().apply {
            put("schema", SCHEMA_VERSION)
            put("kind", edit.kind.name.lowercase())
            put("ts", System.currentTimeMillis())
            put("session_ts", sessionTs)
            put("device_model", "${Build.MANUFACTURER ?: ""} ${Build.MODEL ?: ""}".trim())
            edit.poiId?.let { put("poi_id", it) }
            if (edit.poiName.isNotEmpty()) put("poi_name", edit.poiName)
            edit.currentLat?.let { put("current_lat", it) }
            edit.currentLng?.let { put("current_lng", it) }
            edit.currentCategory?.let { put("current_category", it) }
            edit.currentSubcategory?.let { put("current_subcategory", it) }
            edit.proposedName?.let { put("proposed_name", it) }
            edit.proposedLat?.let { put("proposed_lat", it) }
            edit.proposedLng?.let { put("proposed_lng", it) }
            edit.proposedCategory?.let { put("proposed_category", it) }
            edit.proposedSubcategory?.let { put("proposed_subcategory", it) }
            edit.note?.let { put("note", it) }
            if (edit.photoFilenames.isNotEmpty()) {
                put("photo_filenames", JSONArray(edit.photoFilenames))
            }
        }
        return try {
            val file = ensureJsonlFile()
            FileWriter(file, /* append = */ true).use { it.append(obj.toString()).append('\n') }
            pendingCount++
            DebugLogger.i(TAG, "saveActiveEdit appended ${edit.poiId} → ${file.absolutePath} (count=$pendingCount)")
            countListener?.invoke(pendingCount)
            Toast.makeText(activity, "Field edit saved (#$pendingCount this session)", Toast.LENGTH_SHORT).show()
            activeEdit = null
            true
        } catch (t: Throwable) {
            DebugLogger.e(TAG, "saveActiveEdit failed: ${t.message}")
            Toast.makeText(activity, "Field-edit save failed: ${t.message}", Toast.LENGTH_LONG).show()
            false
        }
    }

    private fun ensureJsonlFile(): File {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            FIELD_EDITS_FOLDER
        )
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "edits-$sessionTs.jsonl")
    }

    /** Update = mutate an existing POI (poiId set, currents populated).
     *  Create = brand-new POI seeded by a long-press; poiId/currents null,
     *  proposedName + proposedLat + proposedLng required to save. */
    enum class Kind { UPDATE, CREATE }

    /** A single in-flight field edit. Captured incrementally as the operator
     *  fills out the bottom sheet. Discarded on Cancel; serialized to JSONL on Save. */
    class ActiveEdit(
        val kind: Kind,
        val poiId: String?,
        val poiName: String,
        val currentLat: Double?,
        val currentLng: Double?,
        val currentCategory: String?,
        val currentSubcategory: String?,
    ) {
        @Volatile var proposedName: String? = null
        @Volatile var proposedLat: Double? = null
        @Volatile var proposedLng: Double? = null
        @Volatile var proposedCategory: String? = null
        @Volatile var proposedSubcategory: String? = null
        @Volatile var note: String? = null
        val photoFilenames: MutableList<String> = mutableListOf()

        fun hasAnyChange(): Boolean = when (kind) {
            Kind.CREATE ->
                !proposedName.isNullOrBlank() && proposedLat != null && proposedLng != null
            Kind.UPDATE ->
                proposedLat != null || proposedLng != null ||
                proposedCategory != null || proposedSubcategory != null ||
                !note.isNullOrBlank() || photoFilenames.isNotEmpty()
        }
    }

    companion object {
        private const val TAG = "FieldEditManager"
        private const val SCHEMA_VERSION = 2
        private const val FIELD_EDITS_FOLDER = "WickedSalemFieldEdits"
    }
}
