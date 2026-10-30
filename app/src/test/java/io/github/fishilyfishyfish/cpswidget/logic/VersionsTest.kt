package io.github.fishilyfishyfish.cpswidget.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VersionsTest {
    @Test
    fun parsesBuildTag() {
        assertEquals(12, Versions.buildFromTag("build-12"))
        assertEquals(7, Versions.buildFromTag(" build-7 "))
    }

    @Test
    fun rejectsOtherTags() {
        assertNull(Versions.buildFromTag("v1.0.3"))
        assertNull(Versions.buildFromTag("build-"))
        assertNull(Versions.buildFromTag("build-12-test"))
    }
}
