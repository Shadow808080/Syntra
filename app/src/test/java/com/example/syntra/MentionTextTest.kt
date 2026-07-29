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
    fun `a handle never contains an at sign`() {
        // "@@budi" once yielded the handle "@budi" — a link to an account that cannot
        // exist, because only ONE leading @ was stripped and @ was not treated as a
        // terminator either.
        assertEquals(listOf("budi"), handlesIn("@@budi"))
        assertTrue(handlesIn("@@").isEmpty())
    }

    @Test
    fun `a newline ends a handle instead of being swallowed into it`() {
        // Comment bodies come from the server and are not guaranteed single-line.
        // Splitting on a literal " " made "@reza\nkeren" one token, so the link
        // carried the handle "reza\nkeren" and ate the rest of the line.
        assertEquals(listOf("reza"), handlesIn("@reza\nkeren"))
        assertEquals(listOf("reza"), handlesIn("halo\t@reza"))
    }

    @Test
    fun `a mention after a newline is still found`() {
        // The mirror image: the token did not start with "@", so it was not a mention
        // at all and simply rendered as grey text.
        assertEquals(listOf("reza"), handlesIn("keren\n@reza"))
        assertEquals(listOf("budi", "rani"), handlesIn("@budi\n@rani"))
    }

    @Test
    fun `the visible text is preserved byte for byte`() {
        // Rejoining split tokens with " " quietly collapsed repeated spaces and turned
        // newlines into spaces, silently rewriting what someone actually typed.
        for (input in listOf("a  b", "@reza\nkeren", "halo\t@budi", "  spasi awal", "")) {
            assertEquals(input, mentionedText(input, Color.Blue) {}.text)
        }
    }

    @Test
    fun `hashtags are styled but never linked`() {
        assertTrue(handlesIn("#syntra keren").isEmpty())
    }

    @Test
    fun `a known handle renders as the display name but still links the handle`() {
        val names = mapOf("reza" to "Reza Ramadhan")
        val built = mentionedText("halo @reza", Color.Blue, { names[it] }) {}
        // The reader sees the name…
        assertEquals("halo @Reza Ramadhan", built.text)
        // …while the link still carries the username, which is what the backend and
        // the profile lookup actually understand.
        assertEquals(
            listOf("reza"),
            built.getLinkAnnotations(0, built.length)
                .mapNotNull { (it.item as? LinkAnnotation.Clickable)?.tag }
                .map { it.removePrefix("mention:") },
        )
    }

    @Test
    fun `an unknown handle falls back to the raw username`() {
        val built = mentionedText("halo @reza", Color.Blue, { null }) {}
        assertEquals("halo @reza", built.text)
    }

    @Test
    fun `punctuation after a substituted name is kept`() {
        val names = mapOf("reza" to "Reza Ramadhan")
        // "@reza." — the regex keeps the trailing dot in the match, so it has to be
        // re-attached after the name rather than swallowed.
        assertEquals(
            "terima kasih @Reza Ramadhan.",
            mentionedText("terima kasih @reza.", Color.Blue, { names[it] }) {}.text,
        )
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
