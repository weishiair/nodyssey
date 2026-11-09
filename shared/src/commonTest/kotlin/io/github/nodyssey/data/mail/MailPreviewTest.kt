package io.github.nodyssey.data.mail

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MailPreviewTest {
    @Test
    fun `code on a separate paragraph retains leading zeroes`() {
        assertEquals("016845", MailVerificationCode.extract("Your Example Verification Code", "Hello,\n\nYour Example verification code is:\n\n016845\n\nThis code expires in 5 minutes."))
        assertEquals("004219", MailVerificationCode.extract("登录验证码", "004219 是您的验证码，请勿透露给他人。"))
        assertEquals("A9C2F4", MailVerificationCode.extract(null, "Your one-time password: A9C2F4"))
        assertEquals("7021", MailVerificationCode.extract(null, "您的驗證碼：7021，有效時間為 5 分鐘。"))
        assertEquals("012345", MailVerificationCode.extract(null, "验证码为012345，请勿泄露。"))
    }

    @Test
    fun `subject context can identify a standalone code in an html only mail`() {
        val preview = MailPreview.from(
            MailMessage(21, 7, subject = "Your verification code", bodyHtml = "<p>Hello,</p><table><tr><td><b>001234</b></td></tr></table><p>Expires in 5 minutes.</p>"),
        )
        assertEquals("001234", preview.verificationCode)
        assertEquals("Hello, 001234 Expires in 5 minutes.", preview.snippet)
    }

    @Test
    fun `dates amounts urls and ordinary order numbers are not verification codes`() {
        listOf(
            "Order #123456 was placed on 2026-11-09 for $1234.56.",
            "Your verification code expires on 2026-11-09.",
            "Your verification code is available at https://example.org/code/123456",
            "Your verification code was sent to user123456@example.org.",
            "Your verification code costs $1234.",
            "Your verification code has reference 123456789012.",
        ).forEach { body -> assertNull(MailVerificationCode.extract(null, body), body) }
        assertNull(MailVerificationCode.extract("Your verification code", "Order 123456 is ready."))
    }

    @Test
    fun `conflicting codes are ambiguous but repeated values are safe to copy`() {
        assertNull(MailVerificationCode.extract("Verification code 123456", "Your verification code is 654321"))
        assertNull(MailVerificationCode.extract(null, "验证码：123456；备用验证码：654321"))
        assertEquals("123456", MailVerificationCode.extract("Verification code 123456", "Your verification code is 123456"))
    }

    @Test
    fun `preview removes active html and tracking urls without fetching them`() {
        val preview = MailPreview.from(
            MailMessage(21, 7, bodyHtml = "<script>verification code 111111</script><p>Weekly update</p><img src='https://example.org/track'><p><a href='https://example.org/update'>Read more</a></p>"),
        )
        assertNull(preview.verificationCode)
        assertEquals("Weekly update Read more", preview.snippet)
    }
}
