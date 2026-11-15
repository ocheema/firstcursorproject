package com.joinqueue.notifier

import java.util.Properties
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

object EmailSender {
    fun sendJoinedEmail(
        gmailUser: String,
        gmailAppPassword: String,
        notifyEmail: String,
        campaignUrl: String,
        attempts: Int
    ) {
        val props = Properties().apply {
            put("mail.smtp.auth", "true")
            put("mail.smtp.starttls.enable", "true")
            put("mail.smtp.host", "smtp.gmail.com")
            put("mail.smtp.port", "587")
        }

        val password = gmailAppPassword.replace("\\s+".toRegex(), "")
        val session = Session.getInstance(props, object : Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication {
                return PasswordAuthentication(gmailUser, password)
            }
        })

        val message = MimeMessage(session).apply {
            setFrom(InternetAddress(gmailUser))
            setRecipients(Message.RecipientType.TO, InternetAddress.parse(notifyEmail))
            subject = "Joined Feather queue"
            setText(
                """
                Successfully joined the Feather queue.

                Campaign: $campaignUrl
                Joined at: ${java.time.Instant.now()}
                Click attempts: $attempts
                """.trimIndent()
            )
        }

        Transport.send(message)
    }
}
