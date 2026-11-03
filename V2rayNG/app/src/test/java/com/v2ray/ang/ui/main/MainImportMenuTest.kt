package com.v2ray.ang.ui.main

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MainImportMenuTest {

    @Test
    fun regularShareMenuContainsOnlyShareActions() {
        val expected = listOf(
            ServerMenuAction.ShareQRCode,
            ServerMenuAction.ShareClipboard,
            ServerMenuAction.ShareFullContent,
        )
        assertEquals(expected, serverMenuActions(isComplexProfile = false, includeManagementActions = false))
    }

    @Test
    fun regularMoreMenuContainsEveryActionInDisplayOrder() {
        assertEquals(
            ServerMenuAction.entries,
            serverMenuActions(isComplexProfile = false, includeManagementActions = true),
        )
    }

    @Test
    fun complexShareMenuContainsOnlyFullContent() {
        assertEquals(
            listOf(ServerMenuAction.ShareFullContent),
            serverMenuActions(isComplexProfile = true, includeManagementActions = false),
        )
    }

    @Test
    fun complexMoreMenuRetainsManagementActions() {
        val expected = listOf(
            ServerMenuAction.ShareFullContent,
            ServerMenuAction.Edit,
            ServerMenuAction.Delete,
        )
        assertEquals(expected, serverMenuActions(isComplexProfile = true, includeManagementActions = true))
    }
    @Test
    fun importMenuKeepsEveryExistingActionExactlyOnce() {
        val quick = quickImportActions()
        val manual = manualImportActions()
        assertEquals(3, quick.size)
        assertEquals(ImportMenuAction.entries.toSet(), (quick + manual).toSet())
        assertEquals(ImportMenuAction.entries.size, (quick + manual).size)
        assertEquals(true, manual.all { it.action is MainAction.ImportManually })
        assertEquals(true, quick.none { it.action is MainAction.ImportManually })
    }

    @Test
    fun toolCategoriesKeepAllActionsAndSeparateDeletion() {
        val categorized = ToolCategory.entries.flatMap { category ->
            MainMoreMenuAction.entries.filter { toolCategory(it) == category }
        }
        assertEquals(MainMoreMenuAction.entries.toSet(), categorized.toSet())
        assertEquals(MainMoreMenuAction.entries.size, categorized.size)
        assertEquals(
            setOf(MainMoreMenuAction.DeleteAll, MainMoreMenuAction.DeleteDuplicate, MainMoreMenuAction.DeleteInvalid),
            categorized.filter { toolCategory(it) == ToolCategory.DELETE }.toSet(),
        )
    }

}
