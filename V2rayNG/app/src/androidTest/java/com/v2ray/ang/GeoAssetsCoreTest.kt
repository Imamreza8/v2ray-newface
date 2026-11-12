package com.v2ray.ang

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.util.Utils
import go.Seq
import libv2ray.CoreCallbackHandler
import libv2ray.Libv2ray
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the native core's packaged-asset fallback, without a real server or VPN. */
@RunWith(AndroidJUnit4::class)
class GeoAssetsCoreTest {
    @Test
    fun coreStartsWithInstalledPrivateCnAndGeoData() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for (name in listOf("geoip.dat", "geosite.dat", "geoip-only-cn-private.dat")) {
            context.assets.open(name).use { assertTrue("Empty asset: $name", it.read() != -1) }
        }

        // This is a fresh emulator install. Match the app's real asset-copy path: Xray
        // checks the filesystem for ext: rules before its bundled-asset fallback can run.
        SettingsManager.initAssets(context, context.assets)
        val assetDir = java.io.File(Utils.userAssetPath(context))
        for (name in listOf("geoip.dat", "geosite.dat", "geoip-only-cn-private.dat")) {
            val installed = java.io.File(assetDir, name)
            assertTrue("Missing installed asset: $name", installed.isFile && installed.length() > 0)
        }
        Seq.setContext(context.applicationContext)
        Libv2ray.initCoreEnv(assetDir.absolutePath, "")
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
