/*
 * WickedSalemWitchCityTour v1.5
 * Copyright (c) 2026 Destructive AI Gurus, LLC. All rights reserved.
 *
 * S229 — Field-edit mode UI: third top-bar button (debug-only, R8-stripped)
 * that lets the operator capture proposed corrections to POIs while walking.
 * The Activity-side glue lives here as Kotlin extension functions on
 * [SalemMainActivity], matching the SalemMainActivityDetour / Directions /
 * Dialogs split-file pattern. The persistence + state-machine logic is in
 * [com.example.wickedsalemwitchcitytour.userdata.FieldEditManager].
 */

package com.example.wickedsalemwitchcitytour.ui

import android.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.example.locationmapapp.data.model.PlaceResult
import com.example.locationmapapp.util.DebugLogger
import com.example.wickedsalemwitchcitytour.R
import com.example.wickedsalemwitchcitytour.content.model.SalemPoi
import com.example.wickedsalemwitchcitytour.ui.menu.PoiCategories
import com.example.wickedsalemwitchcitytour.ui.menu.PoiCategory
import com.example.wickedsalemwitchcitytour.userdata.FieldEditManager
import org.osmdroid.util.GeoPoint

// ─── Toolbar wiring ──────────────────────────────────────────────────────────

/**
 * Wire the third top-bar icon (`toolbarFieldEditIcon`) to a session-scoped
 * ON/OFF toggle. Called once from [SalemMainActivity.onCreate] when
 * [com.example.wickedsalemwitchcitytour.ui.BuildDefaults.FIELD_EDIT_ENABLED]
 * is true. The whole call is dead-code-stripped from retail by R8.
 */
internal fun SalemMainActivity.wireFieldEditToolbar() {
    val wrap = findViewById<View?>(R.id.toolbarFieldEditWrap) ?: return
    val icon = findViewById<View?>(R.id.toolbarFieldEditIcon) ?: return
    val badge = findViewById<TextView?>(R.id.fieldEditBadge)

    wrap.visibility = View.VISIBLE
    refreshFieldEditIconTint()

    icon.setOnClickListener {
        val mgr = fieldEditManager
        mgr.setEnabled(!mgr.isEnabled())
        refreshFieldEditIconTint()
    }

    fieldEditManager.setCountListener { count ->
        runOnUiThread {
            if (count <= 0) {
                badge?.visibility = View.GONE
            } else {
                badge?.visibility = View.VISIBLE
                badge?.text = if (count > 99) "99+" else count.toString()
            }
        }
    }
}

internal fun SalemMainActivity.refreshFieldEditIconTint() {
    val icon = findViewById<View?>(R.id.toolbarFieldEditIcon) ?: return
    val color = if (fieldEditManager.isEnabled()) 0xFFE53935.toInt() else 0xFFFFFFFF.toInt()
    (icon as? android.widget.ImageView)?.setColorFilter(color)
}

// ─── Marker-tap interception ────────────────────────────────────────────────

/**
 * Returns true if the tap was consumed by field-edit mode (sheet was shown).
 * Called from [SalemMainActivity.addPoiMarker]'s click handler before the
 * normal `openPoiDetailFromPlace` path.
 */
internal fun SalemMainActivity.consumePoiTapForFieldEdit(place: PlaceResult): Boolean {
    if (!com.example.wickedsalemwitchcitytour.ui.BuildDefaults.FIELD_EDIT_ENABLED) return false
    if (!fieldEditManager.isEnabled()) return false
    showFieldEditSheet(place)
    return true
}

/**
 * S229 — companion overload for the bundled Salem POI marker layer
 * ([SalemMainActivity.narrationMarkers]). Called from inside the marker
 * click handler in [SalemMainActivity.loadNarrationMarkers] before the
 * normal `PoiDetailSheet.show(...)` path.
 */
internal fun SalemMainActivity.consumeSalemPoiTapForFieldEdit(poi: SalemPoi): Boolean {
    if (!com.example.wickedsalemwitchcitytour.ui.BuildDefaults.FIELD_EDIT_ENABLED) return false
    if (!fieldEditManager.isEnabled()) return false
    showFieldEditSheet(poi)
    return true
}

// ─── Map-tap interception (for "Move here") ─────────────────────────────────

/**
 * Returns true if the tap was consumed (we're in tap-to-place mode). Called
 * from the [org.osmdroid.events.MapEventsReceiver.singleTapConfirmedHelper]
 * before the existing walk-sim/follow stops.
 */
internal fun SalemMainActivity.consumeMapTapForFieldEdit(p: GeoPoint): Boolean {
    if (!awaitingFieldEditMapTap) return false
    awaitingFieldEditMapTap = false
    fieldEditManager.setProposedLocation(p.latitude, p.longitude)
    DebugLogger.i("FieldEditUI", "consumeMapTapForFieldEdit ${p.latitude},${p.longitude}")
    // Re-open the appropriate sheet for the currently active edit so the
    // operator sees the proposed location filled in.
    val edit = fieldEditManager.activeEdit
    if (edit != null) {
        when (edit.kind) {
            FieldEditManager.Kind.CREATE -> showFieldEditCreateSheetForActive(edit)
            FieldEditManager.Kind.UPDATE -> showFieldEditSheetForActive(edit)
        }
    }
    return true
}

/**
 * S230 — long-press on an empty spot in field-edit mode opens the CREATE
 * sheet seeded at that lat/lng. Returns true if consumed (caller skips the
 * normal manual-mode teleport).
 */
internal fun SalemMainActivity.consumeMapLongPressForFieldEdit(p: GeoPoint): Boolean {
    if (!com.example.wickedsalemwitchcitytour.ui.BuildDefaults.FIELD_EDIT_ENABLED) return false
    if (!fieldEditManager.isEnabled()) return false
    DebugLogger.i("FieldEditUI", "consumeMapLongPressForFieldEdit ${p.latitude},${p.longitude}")
    showFieldEditCreateSheet(p.latitude, p.longitude)
    return true
}

// ─── Sheet UI ────────────────────────────────────────────────────────────────

internal fun SalemMainActivity.showFieldEditSheet(place: PlaceResult) {
    val edit = fieldEditManager.openEditFor(
        poiId = place.id,
        poiName = place.name,
        currentLat = place.lat,
        currentLng = place.lon,
        currentCategory = place.category,
        currentSubcategory = null,
    )
    showFieldEditSheetForActive(edit)
}

internal fun SalemMainActivity.showFieldEditSheet(poi: SalemPoi) {
    val edit = fieldEditManager.openEditFor(
        poiId = poi.id,
        poiName = poi.name,
        currentLat = poi.lat,
        currentLng = poi.lng,
        currentCategory = poi.category,
        currentSubcategory = poi.subcategory,
    )
    showFieldEditSheetForActive(edit)
}

private fun SalemMainActivity.showFieldEditSheetForActive(edit: FieldEditManager.ActiveEdit) {
    val view = LayoutInflater.from(this).inflate(R.layout.dialog_field_edit, null, false)

    val nameTv         = view.findViewById<TextView>(R.id.fieldEditPoiName)
    val idTv           = view.findViewById<TextView>(R.id.fieldEditPoiId)
    val curLatLngTv    = view.findViewById<TextView>(R.id.fieldEditCurrentLatLng)
    val propLatLngTv   = view.findViewById<TextView>(R.id.fieldEditProposedLatLng)
    val moveBtn        = view.findViewById<Button>(R.id.fieldEditMoveHereBtn)
    val curCatTv       = view.findViewById<TextView>(R.id.fieldEditCurrentCategory)
    val propCatTv      = view.findViewById<TextView>(R.id.fieldEditProposedCategory)
    val pickCatBtn     = view.findViewById<Button>(R.id.fieldEditPickCategoryBtn)
    val propSubTv      = view.findViewById<TextView>(R.id.fieldEditProposedSubcategory)
    val pickSubBtn     = view.findViewById<Button>(R.id.fieldEditPickSubcategoryBtn)
    val noteEdit       = view.findViewById<EditText>(R.id.fieldEditNoteEdit)
    val photosTv       = view.findViewById<TextView>(R.id.fieldEditPhotosSummary)
    val attachBtn      = view.findViewById<Button>(R.id.fieldEditAttachPhotoBtn)
    val cancelBtn      = view.findViewById<Button>(R.id.fieldEditCancelBtn)
    val saveBtn        = view.findViewById<Button>(R.id.fieldEditSaveBtn)

    nameTv.text = edit.poiName.ifBlank { "(unnamed POI)" }
    idTv.text = edit.poiId

    fun renderState() {
        curLatLngTv.text = formatLatLng("Current", edit.currentLat, edit.currentLng)
        propLatLngTv.text = if (edit.proposedLat != null && edit.proposedLng != null)
            formatLatLng("Proposed", edit.proposedLat, edit.proposedLng) else "Proposed: (unchanged)"

        curCatTv.text = "Current: ${edit.currentCategory ?: "—"}"
        propCatTv.text = "Proposed: ${edit.proposedCategory ?: "(unchanged)"}"
        propSubTv.text = "Proposed: ${edit.proposedSubcategory ?: "(unchanged)"}"

        val n = edit.photoFilenames.size
        photosTv.text = when (n) {
            0 -> "No photos attached"
            1 -> "1 photo: ${edit.photoFilenames[0]}"
            else -> "$n photos:\n  • " + edit.photoFilenames.joinToString("\n  • ")
        }
    }
    renderState()
    noteEdit.setText(edit.note ?: "")

    val dialog = AlertDialog.Builder(this)
        .setView(view)
        .setCancelable(false)
        .create()

    moveBtn.setOnClickListener {
        // Capture the typed note before dismissing — operator might have
        // started typing before deciding to relocate.
        fieldEditManager.setNote(noteEdit.text?.toString())
        awaitingFieldEditMapTap = true
        Toast.makeText(this, "Tap on the map to set new location for ${edit.poiName}", Toast.LENGTH_LONG).show()
        dialog.dismiss()
    }

    pickCatBtn.setOnClickListener {
        showCategoryPicker(edit.proposedCategory ?: edit.currentCategory) { picked ->
            // Save uppercase to match the salem_pois.category convention used
            // by the web admin (PoiCategories.ALL ids are lowercase PoiLayerId
            // strings, which are the runtime layer keys, not the DB category).
            fieldEditManager.setProposedCategory(picked.id.uppercase())
            // Subcategory is keyed off category — clearing makes the operator
            // re-pick when they change category.
            fieldEditManager.setProposedSubcategory(null)
            renderState()
        }
    }

    pickSubBtn.setOnClickListener {
        // Subcategory list comes from whatever proposed category is set, or
        // (if none) the current category. SalemPoi.category is stored uppercase
        // ("SHOPPING") while PoiCategories.ALL.id is lowercase ("shopping"),
        // so match case-insensitively.
        val catId = edit.proposedCategory ?: edit.currentCategory
        val parent = catId?.let { id -> PoiCategories.ALL.firstOrNull { it.id.equals(id, ignoreCase = true) } }
        if (parent == null) {
            Toast.makeText(this, "Pick a category first", Toast.LENGTH_SHORT).show()
            return@setOnClickListener
        }
        val subtypes = parent.subtypes.orEmpty()
        if (subtypes.isEmpty()) {
            Toast.makeText(this, "${parent.label} has no subcategories", Toast.LENGTH_SHORT).show()
            return@setOnClickListener
        }
        val labels = subtypes.map { it.label }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Subcategory of ${parent.label}")
            .setItems(labels) { _, which ->
                fieldEditManager.setProposedSubcategory(labels[which])
                renderState()
            }
            .setNegativeButton("Clear") { _, _ ->
                fieldEditManager.setProposedSubcategory(null)
                renderState()
            }
            .setNeutralButton("Cancel", null)
            .show()
    }

    attachBtn.setOnClickListener {
        // Stash the in-progress note text before launching the camera Activity
        // (which will pause this Activity and tear the dialog down). On return
        // we re-open the sheet via the camera one-shot listener.
        fieldEditManager.setNote(noteEdit.text?.toString())
        katrinaCameraManager.setOneShotPostCapture { filename ->
            fieldEditManager.addPhoto(filename)
            // Re-open the sheet so the operator sees the photo attached.
            val current = fieldEditManager.activeEdit ?: return@setOneShotPostCapture
            runOnUiThread { showFieldEditSheetForActive(current) }
        }
        dialog.dismiss()
        katrinaCameraManager.requestCapture()
    }

    cancelBtn.setOnClickListener {
        fieldEditManager.cancelActiveEdit()
        dialog.dismiss()
    }

    saveBtn.setOnClickListener {
        // Capture note before save.
        fieldEditManager.setNote(noteEdit.text?.toString())
        if (!edit.hasAnyChange()) {
            Toast.makeText(this, "Nothing to save — fill in at least one field", Toast.LENGTH_SHORT).show()
            return@setOnClickListener
        }
        fieldEditManager.saveActiveEdit()
        dialog.dismiss()
    }

    dialog.show()
}

// ─── Category picker ────────────────────────────────────────────────────────

private fun SalemMainActivity.showCategoryPicker(
    currentCategoryId: String?,
    onPick: (PoiCategory) -> Unit,
) {
    val cats = PoiCategories.ALL
    val labels = cats.map { it.label }.toTypedArray()
    // Case-insensitive match: PoiCategories.ALL.id is lowercase, salem_pois.category is uppercase.
    val checked = currentCategoryId?.let { id ->
        cats.indexOfFirst { it.id.equals(id, ignoreCase = true) }.takeIf { it >= 0 }
    } ?: -1
    AlertDialog.Builder(this)
        .setTitle("Pick category")
        .setSingleChoiceItems(labels, checked) { d, which ->
            onPick(cats[which])
            d.dismiss()
        }
        .setNegativeButton("Cancel", null)
        .show()
}

// ─── CREATE sheet (long-press → seed new POI) ───────────────────────────────

internal fun SalemMainActivity.showFieldEditCreateSheet(lat: Double, lng: Double) {
    val edit = fieldEditManager.openCreateAt(lat, lng)
    showFieldEditCreateSheetForActive(edit)
}

private fun SalemMainActivity.showFieldEditCreateSheetForActive(edit: FieldEditManager.ActiveEdit) {
    val view = LayoutInflater.from(this).inflate(R.layout.dialog_field_edit_create, null, false)

    val nameEdit  = view.findViewById<EditText>(R.id.fieldEditCreateNameEdit)
    val latLngTv  = view.findViewById<TextView>(R.id.fieldEditCreateLatLng)
    val moveBtn   = view.findViewById<Button>(R.id.fieldEditCreateMoveHereBtn)
    val noteEdit  = view.findViewById<EditText>(R.id.fieldEditCreateNoteEdit)
    val photosTv  = view.findViewById<TextView>(R.id.fieldEditCreatePhotosSummary)
    val attachBtn = view.findViewById<Button>(R.id.fieldEditCreateAttachPhotoBtn)
    val cancelBtn = view.findViewById<Button>(R.id.fieldEditCreateCancelBtn)
    val saveBtn   = view.findViewById<Button>(R.id.fieldEditCreateSaveBtn)

    fun renderState() {
        latLngTv.text = formatLatLng("Pin", edit.proposedLat, edit.proposedLng)
        val n = edit.photoFilenames.size
        photosTv.text = when (n) {
            0 -> "No photos attached"
            1 -> "1 photo: ${edit.photoFilenames[0]}"
            else -> "$n photos:\n  • " + edit.photoFilenames.joinToString("\n  • ")
        }
    }
    renderState()
    nameEdit.setText(edit.proposedName ?: "")
    noteEdit.setText(edit.note ?: "")

    val dialog = AlertDialog.Builder(this)
        .setView(view)
        .setCancelable(false)
        .create()

    moveBtn.setOnClickListener {
        // Capture name + note before dismissing so the next sheet re-render shows them.
        fieldEditManager.setProposedName(nameEdit.text?.toString())
        fieldEditManager.setNote(noteEdit.text?.toString())
        awaitingFieldEditMapTap = true
        Toast.makeText(this, "Tap on the map to re-pin the new POI", Toast.LENGTH_LONG).show()
        dialog.dismiss()
    }

    attachBtn.setOnClickListener {
        fieldEditManager.setProposedName(nameEdit.text?.toString())
        fieldEditManager.setNote(noteEdit.text?.toString())
        katrinaCameraManager.setOneShotPostCapture { filename ->
            fieldEditManager.addPhoto(filename)
            val current = fieldEditManager.activeEdit ?: return@setOneShotPostCapture
            runOnUiThread { showFieldEditCreateSheetForActive(current) }
        }
        dialog.dismiss()
        katrinaCameraManager.requestCapture()
    }

    cancelBtn.setOnClickListener {
        fieldEditManager.cancelActiveEdit()
        dialog.dismiss()
    }

    saveBtn.setOnClickListener {
        fieldEditManager.setProposedName(nameEdit.text?.toString())
        fieldEditManager.setNote(noteEdit.text?.toString())
        if (!edit.hasAnyChange()) {
            Toast.makeText(
                this,
                "Type a name (and confirm the pin location) before saving",
                Toast.LENGTH_SHORT
            ).show()
            return@setOnClickListener
        }
        fieldEditManager.saveActiveEdit()
        dialog.dismiss()
    }

    dialog.show()
}

// ─── Helpers ────────────────────────────────────────────────────────────────

private fun formatLatLng(prefix: String, lat: Double?, lng: Double?): String {
    if (lat == null || lng == null) return "$prefix: —"
    return "$prefix: ${"%.5f".format(lat)}, ${"%.5f".format(lng)}"
}
