package com.opencamera.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AdvancedFilterControlBindingsTest {
    @Test
    fun `bindings follow enum order and cover every advanced control`() {
        val viewsByControl = FilterAdvancedControl.entries.associateWith { it.name.lowercase() }

        val bindings = orderedAdvancedFilterControlBindings(viewsByControl)

        assertEquals(FilterAdvancedControl.entries, bindings.map { it.second })
        assertEquals(
            FilterAdvancedControl.entries.map { it.name.lowercase() },
            bindings.map { it.first }
        )
    }

    @Test
    fun `missing advanced control binding fails fast`() {
        val incomplete = FilterAdvancedControl.entries
            .dropLast(1)
            .associateWith { it.name }

        assertFailsWith<IllegalArgumentException> {
            orderedAdvancedFilterControlBindings(incomplete)
        }
    }
}
