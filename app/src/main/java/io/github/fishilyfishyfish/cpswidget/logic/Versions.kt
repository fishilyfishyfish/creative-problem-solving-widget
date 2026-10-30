package io.github.fishilyfishyfish.cpswidget.logic

object Versions {
    private val TAG = Regex("^build-(\\d+)$")

    /** Release 태그 "build-12" → 12. 형식이 다르면 null. */
    fun buildFromTag(tag: String): Int? = TAG.find(tag.trim())?.groupValues?.get(1)?.toIntOrNull()
}
