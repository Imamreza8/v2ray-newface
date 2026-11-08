package com.v2ray.ang.ui.main

import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.ServersCache
import com.v2ray.ang.enums.EConfigType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class RecordedExitCountryTest {
    @Test
    fun acceptsOnlyRecordedKnownRegionsAndNeverGuessesFromName() {
        assertEquals("DE", validRecordedExitCountry(" de "))
        assertEquals("US", validRecordedExitCountry("US"))
        listOf(null, "", "USA", "ZZ", "1A", "🇩🇪").forEach { assertNull(validRecordedExitCountry(it)) }
        val profile = ProfileItem(configType = EConfigType.VLESS, remarks = "🇩🇪 Demo")
        assertNull(buildServerRowUiModel(ServersCache("guid", profile), "").exitCountry)
        assertEquals("FR", buildServerRowUiModel(ServersCache("guid", profile, exitCountry = "fr"), "").exitCountry)
    }
}
