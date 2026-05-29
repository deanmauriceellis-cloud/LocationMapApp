/*
 * WickedSalemApp v1.5
 * Copyright (c) 2026 Destructive AI Gurus, LLC. All rights reserved.
 */

package com.example.wickedsalemwitchcitytour.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S304 Phase 1a — unit test for [JobCoordinator]'s registry semantics: named
 * tracking, cancel-before-relaunch, cancel(name), cancelAll(), and self-eviction
 * on completion. Uses Dispatchers.Unconfined so a launch runs synchronously up
 * to the awaitCancellation() suspension — no coroutines-test dependency needed.
 */
class JobCoordinatorTest {

    private fun newCoordinator() = JobCoordinator(CoroutineScope(Dispatchers.Unconfined))

    @Test fun launch_registersActiveJob() {
        val jc = newCoordinator()
        jc.launch("a") { awaitCancellation() }
        assertTrue(jc.isActive("a"))
        assertEquals(1, jc.activeCount())
        jc.cancelAll()
    }

    @Test fun launch_sameName_cancelsPrior() {
        val jc = newCoordinator()
        val first = jc.launch("a") { awaitCancellation() }
        val second = jc.launch("a") { awaitCancellation() }
        assertTrue(first.isCancelled)
        assertTrue(second.isActive)
        assertEquals(1, jc.activeCount())
        jc.cancelAll()
    }

    @Test fun cancel_byName_stopsOnlyThatJob() {
        val jc = newCoordinator()
        jc.launch("a") { awaitCancellation() }
        jc.launch("b") { awaitCancellation() }
        jc.cancel("a")
        assertFalse(jc.isActive("a"))
        assertTrue(jc.isActive("b"))
        assertEquals(1, jc.activeCount())
        jc.cancelAll()
    }

    @Test fun cancel_unknownName_isNoOp() {
        val jc = newCoordinator()
        jc.cancel("nope")   // must not throw
        assertEquals(0, jc.activeCount())
    }

    @Test fun cancelAll_cancelsEveryJob() {
        val jc = newCoordinator()
        jc.launch("a") { awaitCancellation() }
        jc.launch("b") { awaitCancellation() }
        jc.launch("c") { awaitCancellation() }
        assertEquals(3, jc.activeCount())
        jc.cancelAll()
        assertEquals(0, jc.activeCount())
        assertFalse(jc.isActive("a"))
    }

    @Test fun completedJob_selfEvicts() {
        val jc = newCoordinator()
        jc.launch("done") { /* completes immediately */ }
        // With Unconfined the block runs to completion synchronously, so the
        // self-evict invokeOnCompletion has already fired.
        assertFalse(jc.isActive("done"))
        assertEquals(0, jc.activeCount())
    }
}
