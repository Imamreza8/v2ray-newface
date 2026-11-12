package com.v2ray.ang

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import go.Seq
import libv2ray.CoreCallbackHandler
import libv2ray.Libv2ray
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Exercises the native core's packaged-asset fallback, without a real server or VPN. */
@RunWith(AndroidJUnit4::class)
class GeoAssetsCoreTest {
    @Test
    fun coreStartsWithPackagedPrivateCnAndGeoData() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for (name in listOf("geoip.dat", "geosite.dat", "geoip-only-cn-private.dat")) {
            context.assets.open(name).use { assertTrue("Empty asset: $name", it.read() != -1) }
        }

        // Isolate the test from any downloaded file: Xray must fall back to the APK assets.
        val emptyDir = File(context.cacheDir, "geo-asset-core-test")
        check(emptyDir.mkdirs() || emptyDir.isDirectory)
        check(emptyDir.listFiles().orEmpty().isEmpty())
        Seq.setContext(context.applicationContext)
        Libv2ray.initCoreEnv(emptyDir.absolutePath, "")
        val controller = Libv2ray.newCoreController(object : CoreCallbackHandler {
            override fun startup(): Long = 0
            override fun shutdown(): Long = 0
            override fun onEmitStatus(l: Long, s: String?): Long = 0
        })
        val config = """
            {
              "log": {"loglevel": "none"},
              "outbounds": [{"protocol": "freedom", "tag": "direct"}],
              "routing": {"rules": [
                {"type": "field", "ip": ["ext:geoip-only-cn-private.dat:private"], "outboundTag": "direct"},
                {"type": "field", "ip": ["ext:geoip-only-cn-private.dat:cn"], "outboundTag": "direct"},
                {"type": "field", "ip": ["geoip:google"], "outboundTag": "direct"},
                {"type": "field", "domain": ["geosite:private"], "outboundTag": "direct"}
              ]}
            }
        """.trimIndent()
        try {
            controller.startLoop(config, 0)
            assertTrue("Xray core did not start", controller.isRunning)
        } finally {
            if (controller.isRunning) controller.stopLoop()
        }
    }
}
