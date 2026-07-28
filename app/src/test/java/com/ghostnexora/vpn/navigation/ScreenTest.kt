package com.ghostnexora.vpn.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenTest {

    @Test
    fun `drawer screens are computed after dashboard initialization`() {
        // Match application launch order: NavHost reads Dashboard before the
        // navigation drawer asks for its complete model.
        assertEquals("dashboard", Screen.Dashboard.route)

        val firstRead = Screen.drawerItems
        val secondRead = Screen.drawerItems

        assertEquals(
            listOf(
                "dashboard",
                "profiles",
                "create_profile",
                "app_routing",
                "compatibility",
                "import",
                "export",
                "history",
                "logs",
                "settings",
                "documentation",
                "about"
            ),
            firstRead.map(Screen::route)
        )
        assertTrue(firstRead.all { it.route.isNotBlank() })

        // A new list on every read proves there is no static backing list that
        // can capture an uninitialized Screen singleton during class loading.
        assertNotSame(firstRead, secondRead)
        assertEquals(firstRead, secondRead)
    }
}
