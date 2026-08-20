package com.example.meritrankerstudent.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

class LegalConstantsTest {

    @Test
    fun testPrivacyPolicyUrl_isHttpsAndValid() {
        val url = LegalConstants.PRIVACY_POLICY_URL
        assertTrue("Privacy policy URL must be HTTPS", url.startsWith("https://"))
        val uri = URI(url)
        assertEquals("meritranker.com", uri.host)
        assertEquals("/privacy-policy", uri.path)
    }

    @Test
    fun testTermsOfServiceUrl_isHttpsAndValid() {
        val url = LegalConstants.TERMS_OF_SERVICE_URL
        assertTrue("Terms of service URL must be HTTPS", url.startsWith("https://"))
        val uri = URI(url)
        assertEquals("meritranker.com", uri.host)
        assertEquals("/terms-of-service", uri.path)
    }

    @Test
    fun testAccountDeletionUrl_isHttpsAndValid() {
        val url = LegalConstants.ACCOUNT_DELETION_URL
        assertTrue("Account deletion URL must be HTTPS", url.startsWith("https://"))
        val uri = URI(url)
        assertEquals("meritranker.com", uri.host)
        assertEquals("/account-deletion", uri.path)
    }

    @Test
    fun testPrivacyAndSupportEmails() {
        assertEquals("privacy@meritranker.com", LegalConstants.PRIVACY_EMAIL)
        assertEquals("support@meritranker.com", LegalConstants.SUPPORT_EMAIL)
    }

    @Test
    fun testOperatingEntity() {
        assertEquals("Bytech Minds Pvt. Ltd.", LegalConstants.OPERATING_ENTITY)
    }

    @Test
    fun testAiDisclaimerText() {
        assertEquals("AI can make mistakes. Verify important exam information.", LegalConstants.AI_DISCLAIMER_TEXT)
    }
}
