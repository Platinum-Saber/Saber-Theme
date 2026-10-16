package com.sabertheme.core.briefing

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MailLabelsTest {

    private fun mail(sender: String, subject: String, preview: String = "") = QueuedMail("k", sender, subject, preview, 0)

    // Replies Gemma 4 E2B gave on the S23 in the Prompt Lab test.
    @Test
    fun `parses the replies the model gave on the phone`() {
        assertThat(LabelParser.parse("""{"label":"reply","reason":"interview slot confirmation needed","action":"reply","delivery":null}"""))
            .isEqualTo(MailVerdict(MailLabel.Reply))
        assertThat(LabelParser.parse("""{"label":"urgent","reason":"registration closes tonight","action":"Register now","delivery":null}"""))
            .isEqualTo(MailVerdict(MailLabel.Urgent))
        assertThat(
            LabelParser.parse(
                """{"label":"delivery","reason":"parcel is shipped and arriving","action":"none","delivery":{"item":"Kotlin Coroutines Deep Dive","status":"Arriving","eta":"Oct 24 - Oct 29"}}""",
            ),
        ).isEqualTo(MailVerdict(MailLabel.Delivery, DeliveryInfo("Kotlin Coroutines Deep Dive", "Arriving", "Oct 24 - Oct 29")))
    }

    @Test
    fun `tolerates chatter and code fences around the JSON`() {
        val reply = "Sure!\n```json\n{\"label\": \"Bill\", \"delivery\": null}\n```"
        assertThat(LabelParser.parse(reply)).isEqualTo(MailVerdict(MailLabel.Bill))
    }

    @Test
    fun `unknown labels and broken JSON give null`() {
        assertThat(LabelParser.parse("""{"label":"spam"}""")).isNull()
        assertThat(LabelParser.parse("""{"label":"promo" """)).isNull()
        assertThat(LabelParser.parse("I think this is promo")).isNull()
    }

    @Test
    fun `a delivery block on another label is dropped`() {
        val verdict = LabelParser.parse("""{"label":"info","delivery":{"item":"x","status":"","eta":""}}""")
        assertThat(verdict).isEqualTo(MailVerdict(MailLabel.Info))
    }

    @Test
    fun `newsletter senders are promo before the model runs`() {
        // The model labelled this one "info" on the phone.
        assertThat(MailRules.before(mail("Medium <newsletter@medium.com>", "Top stories for you"))).isEqualTo(MailLabel.Promo)
        assertThat(MailRules.before(mail("promotions@pickme.lk", "40% off"))).isEqualTo(MailLabel.Promo)
        assertThat(MailRules.before(mail("noreply@github.com", "Dependabot: bump compose-bom"))).isEqualTo(MailLabel.Info)
        assertThat(MailRules.before(mail("hr@techstart.io", "Internship interview"))).isNull()
    }

    @Test
    fun `fallback labels the test inbox sensibly without the model`() {
        assertThat(MailRules.fallback(mail("no-reply@daraz.lk", "Your package is out for delivery", "Expected today by 6 pm")).label)
            .isEqualTo(MailLabel.Delivery)
        assertThat(MailRules.fallback(mail("hr@techstart.io", "Internship interview", "Please reply with a slot by Friday")).label)
            .isEqualTo(MailLabel.Reply)
        assertThat(MailRules.fallback(mail("club@ieee-student.lk", "Hackathon registrations close tonight")).label)
            .isEqualTo(MailLabel.Urgent)
        assertThat(MailRules.fallback(mail("billing@dialog.lk", "Your bill for September is ready", "Amount due LKR 2,340.00")).label)
            .isEqualTo(MailLabel.Bill)
        assertThat(MailRules.fallback(mail("friend@gmail.com", "Photos from Sunday")).label).isEqualTo(MailLabel.Info)
    }

    @Test
    fun `delivery today comes from the eta or the status`() {
        assertThat(MailRules.arrivesToday(DeliveryInfo("hub", "Out for delivery", ""))).isTrue()
        assertThat(MailRules.arrivesToday(DeliveryInfo("hub", "Shipped", "Today by 6 pm"))).isTrue()
        assertThat(MailRules.arrivesToday(DeliveryInfo("book", "Arriving", "Oct 24 - Oct 29"))).isFalse()
    }

    @Test
    fun `prompt keeps the mail on one line and caps the preview`() {
        val prompt = MailPrompt.build(mail("A <a@b.c>", "Hi\nthere", "x".repeat(2000)), "Friday 16 Oct 2026")
        val line = prompt.lines().last()
        assertThat(line).startsWith("EMAIL: From: A <a@b.c> | Subject: Hi there | ")
        assertThat(line.length).isLessThan(700)
    }
}
