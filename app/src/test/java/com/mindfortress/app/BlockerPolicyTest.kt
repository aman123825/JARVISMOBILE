package com.mindfortress.app

import com.mindfortress.app.service.BlockerPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockerPolicyTest {

    private val emptyCustom = emptySet<String>()

    @Test
    fun isDistractingPackage_defaultSocialMediaApps_areBlocked() {
        // Prominent infinite-scroll social media apps
        assertTrue(BlockerPolicy.isDistractingPackage("com.instagram.android", emptyCustom))
        assertTrue(BlockerPolicy.isDistractingPackage("com.zhiliaoapp.musically", emptyCustom))
        assertTrue(BlockerPolicy.isDistractingPackage("com.ss.android.ugc.trill", emptyCustom))
        assertTrue(BlockerPolicy.isDistractingPackage("com.facebook.katana", emptyCustom))
        assertTrue(BlockerPolicy.isDistractingPackage("com.snapchat.android", emptyCustom))
        assertTrue(BlockerPolicy.isDistractingPackage("com.twitter.android", emptyCustom))
        assertTrue(BlockerPolicy.isDistractingPackage("com.reddit.frontpage", emptyCustom))
        assertTrue(BlockerPolicy.isDistractingPackage("com.pinterest", emptyCustom))
        assertTrue(BlockerPolicy.isDistractingPackage("com.tinder", emptyCustom))
    }

    @Test
    fun isDistractingPackage_addictiveGamingPackages_areBlocked() {
        // Known distracting games
        assertTrue(BlockerPolicy.isDistractingPackage("com.roblox.client", emptyCustom))
        assertTrue(BlockerPolicy.isDistractingPackage("com.king.candycrushsaga", emptyCustom))
        assertTrue(BlockerPolicy.isDistractingPackage("com.kiloo.subwaysurf", emptyCustom))
        assertTrue(BlockerPolicy.isDistractingPackage("com.dts.freefireth", emptyCustom))
        assertTrue(BlockerPolicy.isDistractingPackage("com.pubg.imobile", emptyCustom))
        assertTrue(BlockerPolicy.isDistractingPackage("com.tencent.ig", emptyCustom))
        assertTrue(BlockerPolicy.isDistractingPackage("com.supercell.clashofclans", emptyCustom))
        assertTrue(BlockerPolicy.isDistractingPackage("com.supercell.brawlstars", emptyCustom))
        assertTrue(BlockerPolicy.isDistractingPackage("com.activision.callofduty.shooter", emptyCustom))
        assertTrue(BlockerPolicy.isDistractingPackage("com.miHoYo.GenshinImpact", emptyCustom))
    }

    @Test
    fun isDistractingPackage_gamblingAndCasinoKeywords_areBlocked() {
        // Auto-intercept suspicious package names containing gambling keywords
        assertTrue(BlockerPolicy.isDistractingPackage("com.vegas.lucky.casino.free", emptyCustom))
        assertTrue(BlockerPolicy.isDistractingPackage("com.royal.slots.machine", emptyCustom))
        assertTrue(BlockerPolicy.isDistractingPackage("org.online.poker.app", emptyCustom))
        assertTrue(BlockerPolicy.isDistractingPackage("com.sports.betting.live", emptyCustom))
        assertTrue(BlockerPolicy.isDistractingPackage("io.crypto.gambling.hub", emptyCustom))
        assertTrue(BlockerPolicy.isDistractingPackage("com.monaco.roulette.game", emptyCustom))
        assertTrue(BlockerPolicy.isDistractingPackage("net.mega.jackpot.spin", emptyCustom))
    }

    @Test
    fun isDistractingPackage_whitelistedAndEssentialPackages_areAllowed() {
        // App itself is exempt
        assertFalse(BlockerPolicy.isDistractingPackage("com.mindfortress.app", emptyCustom))
        assertFalse(BlockerPolicy.isDistractingPackage("com.mindfortress.app", setOf("com.mindfortress.app")))

        // Utility, productivity, and education apps are not blocked by default
        assertFalse(BlockerPolicy.isDistractingPackage("com.google.android.calculator", emptyCustom))
        assertFalse(BlockerPolicy.isDistractingPackage("com.google.android.apps.docs", emptyCustom))
        assertFalse(BlockerPolicy.isDistractingPackage("org.khanacademy.android", emptyCustom))
        assertFalse(BlockerPolicy.isDistractingPackage("org.wikipedia", emptyCustom))
    }

    @Test
    fun isDistractingPackage_customUserBlockedPackage_isBlocked() {
        val customApp = "com.custom.procrastination.reader"
        assertFalse(BlockerPolicy.isDistractingPackage(customApp, emptyCustom))

        val userBlocked = setOf(customApp)
        assertTrue(BlockerPolicy.isDistractingPackage(customApp, userBlocked))
    }
}
