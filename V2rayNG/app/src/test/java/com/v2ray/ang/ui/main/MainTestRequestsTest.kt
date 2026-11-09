package com.v2ray.ang.ui.main

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MainTestRequestsTest {
    @Test
    fun onlyTheLatestCurrentRequestCanFinishOnce() {
        val requests = MainTestRequests()
        assertFalse(requests.isTesting)
        assertFalse(requests.completeCurrent(""))
        val first = requests.beginCurrent()
        val second = requests.beginCurrent()
        assertNotEquals(first, second)
        assertFalse(requests.completeCurrent(first))
        assertTrue(requests.isTesting)
        assertTrue(requests.completeCurrent(second))
        assertFalse(requests.completeCurrent(second))
        assertFalse(requests.isTesting)
    }

    @Test
    fun stopAndRestartInvalidateThePreviousConnectionRequest() {
        val requests = MainTestRequests()
        val stopped = requests.beginCurrent()
        requests.invalidateCurrent()
        assertFalse(requests.completeCurrent(stopped))
        val restarted = requests.beginCurrent()
        assertFalse(requests.completeCurrent(stopped))
        assertTrue(requests.completeCurrent(restarted))
    }

    @Test
    fun bulkCancellationAndReplacementRejectLateCompletionEvenInTheSameGroup() {
        val requests = MainTestRequests()
        val cancelled = requests.beginBulk("group")
        requests.cancelBulk()
        assertNull(requests.completeBulk(cancelled.id))
        val replaced = requests.beginBulk("group")
        val current = requests.beginBulk("group")
        assertNull(requests.completeBulk(replaced.id))
        assertEquals(current, requests.bulk)
        assertEquals(current, requests.completeBulk(current.id))
        assertNull(requests.completeBulk(current.id))
        assertFalse(requests.isTesting)
    }

    @Test
    fun currentResultDoesNotFinishAnIndependentBulkTest() {
        val requests = MainTestRequests()
        val bulk = requests.beginBulk("") // All servers is a valid group ID.
        val current = requests.beginCurrent()
        assertTrue(requests.completeCurrent(current))
        assertTrue(requests.isTesting)
        assertEquals(bulk, requests.completeBulk(bulk.id))
        assertFalse(requests.isTesting)
    }
    @Test
    fun currentConnectionTestIsAvailableOnlyWhenConnectedAndIdle() {
        assertTrue(canStartCurrentConnectionTest(isRunning = true, isTesting = false))
        assertFalse(canStartCurrentConnectionTest(isRunning = false, isTesting = false))
        assertFalse(canStartCurrentConnectionTest(isRunning = true, isTesting = true))
        assertFalse(canStartCurrentConnectionTest(isRunning = false, isTesting = true))
    }
    @Test
    fun currentConnectionTestFromOptionsRunsWithoutAProposal() {
        assertEquals(MainAction.TestCurrentServer, actionForTestChoice(ScopedTestKind.CURRENT))
        for (kind in ScopedTestKind.entries.filter { it != ScopedTestKind.CURRENT }) {
            assertEquals(MainAction.PrepareScopedTest(kind), actionForTestChoice(kind))
            assertTrue(testChoiceEnabled(kind, isTesting = false, isRunning = false))
            assertFalse(testChoiceEnabled(kind, isTesting = true, isRunning = true))
        }
        assertTrue(testChoiceEnabled(ScopedTestKind.CURRENT, isTesting = false, isRunning = true))
        assertFalse(testChoiceEnabled(ScopedTestKind.CURRENT, isTesting = false, isRunning = false))
        assertFalse(testChoiceEnabled(ScopedTestKind.CURRENT, isTesting = true, isRunning = true))
    }

    @Test
    fun currentConnectionTestNeverShowsAStaleBulkProgressOrResumeAction() {
        val base = MainUiState(runStats = TestRunStats(total = 10, done = 4), resumableCount = 6)
        assertEquals(TestStrip.NONE, testStrip(MainUiState()))
        assertEquals(TestStrip.RESUME, testStrip(base))
        assertEquals(TestStrip.NONE, testStrip(base.copy(isTesting = true)))
        assertEquals(TestStrip.BULK_PROGRESS, testStrip(base.copy(isTesting = true, isBulkTesting = true)))
        assertEquals(TestStrip.NONE, testStrip(base.copy(isTesting = true, isBulkTesting = true,
            runStats = TestRunStats(total = 10, done = 10))))
    }
}
