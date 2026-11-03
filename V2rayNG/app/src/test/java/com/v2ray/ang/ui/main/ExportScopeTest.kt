package com.v2ray.ang.ui.main

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ExportScopeTest {
    @Test
    fun scopeSelectsOnlyTheFrozenIdsAndHandlesNoSelection() {
        val proposal = ExportProposal("group", "Name", listOf("a", "b", "c"), listOf("b"), "c", ServerStatusFilter.FAILED, true)
        assertEquals(listOf("a", "b", "c"), exportScopeGuids(proposal, ExportScope.GROUP))
        assertEquals(listOf("b"), exportScopeGuids(proposal, ExportScope.VISIBLE))
        assertEquals(listOf("c"), exportScopeGuids(proposal, ExportScope.SELECTED))
        assertEquals(emptyList<String>(), exportScopeGuids(proposal.copy(selectedGuid = null), ExportScope.SELECTED))
        assertEquals(emptyList<String>(), exportScopeGuids(proposal.copy(groupGuids = emptyList()), ExportScope.GROUP))
    }
}
