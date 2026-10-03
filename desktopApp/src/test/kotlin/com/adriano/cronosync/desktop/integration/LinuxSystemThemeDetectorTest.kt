package com.adriano.cronosync.desktop.integration

import com.adriano.cronosync.desktop.integration.LinuxSystemThemeDetector.Companion.parseColorSchemeChange
import com.adriano.cronosync.desktop.integration.LinuxSystemThemeDetector.Companion.parseGsettings
import com.adriano.cronosync.desktop.integration.LinuxSystemThemeDetector.Companion.parsePortalReply
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LinuxSystemThemeDetectorTest {

    @Test
    fun readsThePortalReply() {
        assertEquals(true, parsePortalReply("(<uint32 1>,)")) // prefere escuro
        assertEquals(false, parsePortalReply("(<uint32 2>,)")) // prefere claro
        assertEquals(false, parsePortalReply("(<uint32 0>,)")) // sem preferência = claro
        assertEquals(true, parsePortalReply("(<<uint32 1>>,)")) // formato do método antigo Read
        assertNull(parsePortalReply("Error: GDBus.Error:org.freedesktop.portal.Error.NotFound"))
    }

    @Test
    fun reactsOnlyToColorSchemeChanges() {
        val toDark = "/org/freedesktop/portal/desktop: org.freedesktop.portal.Settings.SettingChanged " +
            "('org.freedesktop.appearance', 'color-scheme', <uint32 1>)"
        val toLight = toDark.replace("uint32 1", "uint32 0")
        val accentColor = "/org/freedesktop/portal/desktop: org.freedesktop.portal.Settings.SettingChanged " +
            "('org.freedesktop.appearance', 'accent-color', <(0.2, 0.4, 0.9)>)"

        assertEquals(true, parseColorSchemeChange(toDark))
        assertEquals(false, parseColorSchemeChange(toLight))
        assertNull(parseColorSchemeChange(accentColor))
        assertNull(parseColorSchemeChange("The name org.freedesktop.portal.Desktop is owned by :1.50"))
    }

    @Test
    fun fallsBackToGnomeSettings() {
        assertEquals(true, parseGsettings("'prefer-dark'\n"))
        assertEquals(false, parseGsettings("'default'\n"))
        assertNull(parseGsettings("No such key"))
    }
}
