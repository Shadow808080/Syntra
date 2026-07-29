package com.example.syntra

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the `@mention` markup in comment bodies.
 *
 * The tricky part is not finding "@name" — it is deciding where the name STOPS. A
 * mention almost always sits inside a sentence, so the handle has to shed trailing
 * punctuation while the punctuation stays on screen. Get that wrong and every mention
 * followed by a comma links to a user that does not exist.
 */
class MentionTextTest {

    /** The handles that were turned into tappable links, in order. */
    private fun handlesIn(text: String): List<String> {
        val built = mentionedText(text, Color.Blue) {}
        return built.getLinkAnnotations(0, built.length)
            .mapNotNull { (it.item as? LinkAnnotation.Clickable)?.tag }
            .map { it.removePrefix("mention:") }
    }

    @Test
    fun `plain text has no links`() {
        assertTrue(handlesIn("halo apa kabar").isEmpty())
    }

    @Test
    fun `single mention is linked`() {
        assertEquals(listOf("reza"), handlesIn("halo @reza"))
    }

    @Test
    fun `trailing punctuation is stripped from the handle`() {
        assertEquals(listOf("reza"), handlesIn("cek @reza, mantap"))
        assertEquals(listOf("reza"), handlesIn("kamu @reza?"))
        assertEquals(listOf("reza"), handlesIn("terima kasih @reza."))
        assertEquals(listOf("reza"), handlesIn("hebat @reza!"))
    }

    @Test
    fun `visible text keeps the punctuation it started with`() {
        // The handle is trimmed for the LINK, not for what the reader sees.
        assertEquals("cek @reza, mantap", mentionedText("cek @reza, mantap", Color.Blue) {}.text)
    }

    @Test
    fun `several mentions all link`() {
        assertEquals(listOf("budi", "rani"), handlesIn("@budi dan @rani ikut"))
    }

    @Test
    fun `a lone at sign is not a mention`() {
        // "@" with nothing after it would otherwise produce an empty handle and a
        // link that navigates to a blank username.
        assertTrue(handlesIn("harga @ 5000").isEmpty())
    }

    @Test
    fun `hashtags are styled but never linked`() {
        assertTrue(handlesIn("#syntra keren").isEmpty())
    }

    @Test
    fun `tapping a mention reports the stripped handle`() {
        var tapped: String? = null
        val built = mentionedText("halo @reza!", Color.Blue) { tapped = it }
        val link = built.getLinkAnnotations(0, built.length)
            .mapNotNull { it.item as? LinkAnnotation.Clickable }
            .single()
        link.linkInteractionListener?.onClick(link)
        assertEquals("reza", tapped)
    }
}
