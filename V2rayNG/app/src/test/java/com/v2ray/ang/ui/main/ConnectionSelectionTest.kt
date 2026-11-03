package com.v2ray.ang.ui.main

import com.v2ray.ang.dto.ConnectionTestResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConnectionSelectionTest {
    @Test
    fun changingSelectionDropsOldConnectionResultButKeepsBulkProgress() {
        val previous = MainUiState(
            selectedGuid = "a", isRunning = true, isTesting = true,
            status = MainStatus.TestProgress("4/10"),
            connectionStatus = MainStatus.ConnectionTest(ConnectionTestResult(180, country = "US")),
            selectedInfo = SelectedServerInfo("old", 180),
        )
        assertSame(previous, stateAfterSelectionChange(previous, "a", true))
        val next = stateAfterSelectionChange(previous, "b", true)
        assertEquals("b", next.selectedGuid)
        assertEquals(MainStatus.Connected, next.connectionStatus)
        assertEquals(previous.status, next.status)
        assertTrue(next.isTesting)
        assertNull(next.selectedInfo)
        val bulkWithoutProgress = stateAfterSelectionChange(previous.copy(status = previous.connectionStatus), "b", true)
        assertEquals(MainStatus.Testing, bulkWithoutProgress.status)
    }

    @Test
    fun selectionChangeClearsCurrentTestWithoutInventingRunningState() {
        val requests = MainTestRequests()
        val request = requests.beginCurrent()
        requests.invalidateCurrent()
        assertFalse(requests.completeCurrent(request))
        val running = MainUiState(selectedGuid = "a", isRunning = true, isTesting = true,
            status = MainStatus.Testing, connectionStatus = MainStatus.Testing)
        val switched = stateAfterSelectionChange(running, "b", requests.bulk != null)
        assertFalse(switched.isTesting)
        assertEquals(MainStatus.Connected, switched.status)
        assertEquals(MainStatus.Connected, switched.connectionStatus)
        val stopped = stateAfterSelectionChange(running.copy(isRunning = false), null, false)
        assertNull(stopped.selectedGuid)
        assertEquals(MainStatus.Disconnected, stopped.status)
        assertEquals(MainStatus.Disconnected, stopped.connectionStatus)
    }
}
