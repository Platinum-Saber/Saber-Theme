package com.sabertheme.core.widgetdata

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GmailMapperTest {

    private fun fields(
        title: String? = "Ada Lovelace",
        text: String? = "Engine notes",
        bigText: String? = "Engine notes\nThe second draft is attached.\nSee page 3.",
        textLines: List<String>? = null,
        isSummary: Boolean = false,
        isOngoing: Boolean = false,
    ) = GmailFields("0|com.google.android.gm|42|gig:1:inbox|10244", title, text, bigText, textLines, isSummary, isOngoing, postTime = 1_000L)

    private fun map(f: GmailFields) = GmailMapper.toMessages(f, open = null)

    @Test
    fun `single mail takes sender from title and splits subject off the big text`() {
        val mail = map(fields()).single()
        assertThat(mail.key).isEqualTo("0|com.google.android.gm|42|gig:1:inbox|10244")
        assertThat(mail.sender).isEqualTo("Ada Lovelace")
        assertThat(mail.subject).isEqualTo("Engine notes")
        assertThat(mail.preview).isEqualTo("The second draft is attached.\nSee page 3.")
        assertThat(mail.postTime).isEqualTo(1_000L)
    }

    @Test
    fun `missing big text falls back to text as the subject`() {
        val mail = map(fields(bigText = null)).single()
        assertThat(mail.subject).isEqualTo("Engine notes")
        assertThat(mail.preview).isEmpty()
    }

    @Test
    fun `big text without the subject prefix splits on its first line`() {
        val mail = map(fields(text = null, bigText = "Lunch?\nTomorrow at one.")).single()
        assertThat(mail.subject).isEqualTo("Lunch?")
        assertThat(mail.preview).isEqualTo("Tomorrow at one.")
    }

    @Test
    fun `InboxStyle bundle maps one mail per line`() {
        val lines = listOf("Ada Lovelace   Engine notes", "Charles Babbage   Re: Engine notes", " ")
        val mails = map(fields(title = "2 new messages", text = null, bigText = null, textLines = lines))
        assertThat(mails.map { it.sender }).containsExactly("Ada Lovelace", "Charles Babbage").inOrder()
        assertThat(mails.map { it.subject }).containsExactly("Engine notes", "Re: Engine notes").inOrder()
        assertThat(mails.map { it.key.substringAfterLast('#') }).containsExactly("0", "1").inOrder()
        assertThat(mails.all { it.preview.isEmpty() }).isTrue()
    }

    @Test
    fun `bundle line without a separator keeps the title as sender`() {
        val mail = map(fields(title = "Ada Lovelace", textLines = listOf("Engine notes"))).single()
        assertThat(mail.sender).isEqualTo("Ada Lovelace")
        assertThat(mail.subject).isEqualTo("Engine notes")
    }

    @Test
    fun `group summaries and ongoing notices are skipped`() {
        assertThat(map(fields(isSummary = true))).isEmpty()
        assertThat(map(fields(isSummary = true, textLines = listOf("Ada   Hi")))).isEmpty()
        assertThat(map(fields(isOngoing = true))).isEmpty()
    }

    @Test
    fun `mail without a sender or any text is skipped`() {
        assertThat(map(fields(title = null))).isEmpty()
        assertThat(map(fields(text = null, bigText = null))).isEmpty()
    }

    @Test
    fun `long previews are capped`() {
        val mail = map(fields(bigText = "Engine notes\n" + "x".repeat(GmailMapper.MAX_PREVIEW + 500))).single()
        assertThat(mail.preview).hasLength(GmailMapper.MAX_PREVIEW)
    }
}
