package com.example.meritrankerstudent.util

import android.content.Context
import android.content.Intent
import android.net.Uri

object LegalConstants {
    const val PRIVACY_POLICY_URL = "https://meritranker.com/privacy-policy"
    const val TERMS_OF_SERVICE_URL = "https://meritranker.com/terms-of-service"
    const val ACCOUNT_DELETION_URL = "https://meritranker.com/account-deletion"
    const val PRIVACY_EMAIL = "privacy@meritranker.com"
    const val SUPPORT_EMAIL = "support@meritranker.com"
    const val OPERATING_ENTITY = "Bytech Minds Pvt. Ltd."
    const val AI_DISCLAIMER_TEXT = "AI can make mistakes. Verify important exam information."
    const val NON_GOVERNMENT_DISCLAIMER = "MeritRanker is an independent exam-preparation platform and is not affiliated with or endorsed by any government authority."

    fun openUrl(context: Context, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }

    fun openEmail(context: Context, email: String, subject: String = "", body: String = "") {
        try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:$email")
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            openUrl(context, "mailto:$email")
        }
    }

    fun openPlayStoreListing(context: Context) {
        val packageName = context.packageName
        try {
            val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY
            }
            context.startActivity(marketIntent)
        } catch (_: Exception) {
            openUrl(context, "https://play.google.com/store/apps/details?id=$packageName")
        }
    }
}
