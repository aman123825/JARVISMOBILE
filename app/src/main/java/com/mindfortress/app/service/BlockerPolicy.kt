package com.mindfortress.app.service

object BlockerPolicy {

    val DEFAULT_BLOCKED_PACKAGES = setOf(
        // Social Media & Infinite Scroll
        "com.instagram.android",
        "com.zhiliaoapp.musically",
        "com.ss.android.ugc.trill",
        "com.facebook.katana",
        "com.facebook.orca",
        "com.snapchat.android",
        "com.twitter.android",
        "com.reddit.frontpage",
        "com.pinterest",
        "com.tinder",
        "com.badoo.mobile",
        "com.bumble.app",
        "com.grindr.android",
        "com.hily",

        // Addictive & Distracting Games
        "com.roblox.client",
        "com.king.candycrushsaga",
        "com.king.candycrushsodasaga",
        "com.kiloo.subwaysurf",
        "com.dts.freefireth",
        "com.dts.freefiremax",
        "com.pubg.imobile",
        "com.pubg.krmobile",
        "com.tencent.ig",
        "com.supercell.clashofclans",
        "com.supercell.brawlstars",
        "com.supercell.clashroyale",
        "com.activision.callofduty.shooter",
        "com.ea.gp.fifamobile",
        "com.miniclip.eightballpool",
        "com.playrix.gardenscapes",
        "com.playrix.homescapes",
        "com.outfit7.mytalkingtomfree",
        "com.moonactive.coinmaster",
        "com.scopely.monopolygo",
        "com.miHoYo.GenshinImpact",
        "com.riotgames.league.wildrift",
        "com.innersloth.spacemafia"
    )

    // Suspicious keywords in package names for auto-identifying distracting games & gambling
    val SUSPICIOUS_PACKAGE_KEYWORDS = listOf(
        "casino", "slots", "poker", "betting", "gambling", "roulette", "jackpot"
    )

    // Upstream Safe & Adult-Filtering DNS Servers (DoT/UDP)
    // Cloudflare 1.1.1.3 Family Filter (blocks malware + adult porn websites systemwide)
    const val SAFE_DNS_PRIMARY = "1.1.1.3"
    const val SAFE_DNS_SECONDARY = "1.0.0.3"
    const val SAFE_DNS_IPV6_PRIMARY = "2606:4700:4700::1113"
    const val SAFE_DNS_IPV6_SECONDARY = "2606:4700:4700::1003"

    // CleanBrowsing Adult & Explicit Filter (Alternative High-Security)
    const val CLEANBROWSING_ADULT_DNS = "185.228.168.10"

    // System Private DNS hostname recommendation for the user's phone settings
    const val RECOMMENDED_PRIVATE_DNS_HOSTNAME = "family.cloudflare-dns.com"
    const val ALTERNATIVE_PRIVATE_DNS_HOSTNAME = "adult-filter-dns.cleanbrowsing.org"

    fun isDistractingPackage(packageName: String, userCustomBlocked: Set<String>): Boolean {
        if (packageName == "com.mindfortress.app") return false
        if (userCustomBlocked.contains(packageName)) return true
        if (DEFAULT_BLOCKED_PACKAGES.contains(packageName)) return true

        val lower = packageName.lowercase()
        return SUSPICIOUS_PACKAGE_KEYWORDS.any { lower.contains(it) }
    }
}
