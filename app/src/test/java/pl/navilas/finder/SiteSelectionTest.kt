package pl.navilas.finder

import org.junit.Assert.assertEquals
import org.junit.Test
import pl.navilas.finder.domain.SiteSelection

class SiteSelectionTest {
    @Test
    fun toggle_adds_then_removes() {
        val one = SiteSelection.toggle(emptyList(), "a")
        assertEquals(listOf("a"), one)
        assertEquals(emptyList<String>(), SiteSelection.toggle(one, "a"))
    }

    @Test
    fun add_on_primary_keeps_selection() {
        assertEquals(listOf("a"), SiteSelection.add(listOf("a"), "a"))
    }

    @Test
    fun add_moves_existing_to_primary() {
        assertEquals(listOf("b", "a"), SiteSelection.add(listOf("a", "b"), "a"))
    }

    @Test
    fun add_drops_oldest_above_max() {
        val full = (1..SiteSelection.MAX).map { "s$it" }
        val next = SiteSelection.add(full, "new")
        assertEquals(SiteSelection.MAX, next.size)
        assertEquals("new", next.last())
        assertEquals("s2", next.first())
    }
}
