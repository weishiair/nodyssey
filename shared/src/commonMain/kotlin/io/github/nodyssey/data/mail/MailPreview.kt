package io.github.nodyssey.data.mail

data class MailPreview(val snippet: String, val verificationCode: String?) {
    companion object {
        fun from(mail: MailMessage): MailPreview {
            val body = MailBody.text(mail)
            return MailPreview(
                snippet = MAIL_PREVIEW_LINK.replace(body, "").replace(MAIL_WHITESPACE, " ").trim().take(180),
                verificationCode = MailVerificationCode.extract(mail.subject, body),
            )
        }
    }
}

/** Conservative, local detection only: never fetch links or send mail content to another service. */
object MailVerificationCode {
    fun extract(subject: String?, body: String): String? {
        val subjectCodes = contextualCandidates(subject.orEmpty())
        val bodyCodes = contextualCandidates(body)
        val contextual = (subjectCodes + bodyCodes).distinct()
        if (contextual.isNotEmpty()) return contextual.singleOrNull()
        if (!CONTEXT.containsMatchIn(subject.orEmpty())) return null
        // Some senders name the code only in the subject and put its value on a separate body line.
        return candidates(body).filter { match ->
            val lineStart = body.lastIndexOf('\n', match.range.first - 1) + 1
            val lineEnd = body.indexOf('\n', match.range.last + 1).takeIf { it >= 0 } ?: body.length
            body.substring(lineStart, lineEnd).trim() == match.value
        }.map { it.value }.distinct().singleOrNull()
    }

    private fun contextualCandidates(text: String): List<String> {
        val contexts = CONTEXT.findAll(text).toList()
        return candidates(text).filter { candidate ->
            contexts.any { context ->
                when {
                    context.range.last < candidate.range.first -> {
                        candidate.range.first - context.range.last <= 65 &&
                            text.substring(context.range.last + 1, candidate.range.first).none { it.isDigit() || it in ".!?。！？" }
                    }

                    candidate.range.last < context.range.first -> {
                        context.range.first - candidate.range.last <= 33 &&
                            text.substring(candidate.range.last + 1, context.range.first).none { it.isDigit() || it in ".!?。！？" }
                    }

                    else -> false
                }
            }
        }.map { it.value }
    }

    private fun candidates(text: String): List<MatchResult> {
        val excluded = EXCLUDED.findAll(text).map { it.range }.toList()
        return TOKEN.findAll(text).filter { match ->
            match.value.any(Char::isDigit) && excluded.none { it.first <= match.range.last && it.last >= match.range.first }
        }.toList()
    }

    private val CONTEXT = Regex(
        "验证码|驗證碼|校验码|校驗碼|动态码|動態碼|一次性密码|一次性密碼|登录码|登入碼|安全码|安全碼|确认码|確認碼|" +
            "\\b(?:(?:verification|security|login|sign[ -]?in|authentication|auth|confirmation|2fa)\\s+code|" +
            "one[ -]?time\\s+(?:password|passcode|code)|otp)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val TOKEN = Regex("(?<![a-z0-9_])[a-z0-9]{4,8}(?![a-z0-9_])", RegexOption.IGNORE_CASE)
    private val EXCLUDED = Regex(
        "(?:https?://|mailto:)\\S+|[\\w.+-]+@[\\w.-]+|" +
            "\\b\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}\\b|\\b\\d{1,2}[-/]\\d{1,2}[-/]\\d{4}\\b|" +
            "[$€£¥￥]\\s*[\\d,.]+|\\b\\d+[.,]\\d+\\b",
        RegexOption.IGNORE_CASE,
    )
}

private val MAIL_PREVIEW_LINK = Regex("\\((?:https?://|mailto:)[^\\s)]+\\)|(?:https?://|mailto:)\\S+", RegexOption.IGNORE_CASE)
private val MAIL_WHITESPACE = Regex("\\s+")
