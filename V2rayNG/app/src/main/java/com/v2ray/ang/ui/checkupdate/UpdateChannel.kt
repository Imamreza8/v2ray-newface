package com.v2ray.ang.ui.checkupdate

/** Newface cannot install the upstream release APK: it has its own package and signing key. */
internal fun usesNewfaceUpdates(flavor: String): Boolean = flavor == "newface"
