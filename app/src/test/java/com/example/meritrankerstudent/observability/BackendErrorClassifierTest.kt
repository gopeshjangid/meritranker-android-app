package com.example.meritrankerstudent.observability

import org.junit.Assert.*
import org.junit.Test
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class BackendErrorClassifierTest {

    @Test
    fun classify_timeoutException() {
        val ex = SocketTimeoutException("Read timed out")
        assertEquals(ErrorCategory.TIMEOUT, BackendErrorClassifier.classify(ex))
    }

    @Test
    fun classify_dnsAndNetworkExceptions() {
        val ex1 = UnknownHostException("Unable to resolve host api.meritranker.com")
        val ex2 = ConnectException("Connection refused")
        assertEquals(ErrorCategory.DNS_NETWORK, BackendErrorClassifier.classify(ex1))
        assertEquals(ErrorCategory.DNS_NETWORK, BackendErrorClassifier.classify(ex2))
    }

    @Test
    fun classify_authAndForbidden() {
        val ex1 = Exception("HTTP 401 Unauthorized")
        val ex2 = Exception("HTTP 403 Forbidden: Access Denied")
        assertEquals(ErrorCategory.AUTH, BackendErrorClassifier.classify(ex1))
        assertEquals(ErrorCategory.FORBIDDEN, BackendErrorClassifier.classify(ex2))
    }

    @Test
    fun classify_serverErrors() {
        val ex1 = Exception("HTTP 500 Internal Server Error")
        val ex2 = Exception("HTTP 503 Service Unavailable")
        assertEquals(ErrorCategory.BACKEND_5XX, BackendErrorClassifier.classify(ex1))
        assertEquals(ErrorCategory.BACKEND_5XX, BackendErrorClassifier.classify(ex2))
    }

    @Test
    fun classify_rateLimited() {
        val ex = Exception("HTTP 429 Too Many Requests: Rate limit exceeded")
        assertEquals(ErrorCategory.RATE_LIMITED, BackendErrorClassifier.classify(ex))
    }

    @Test
    fun classify_graphQLErrors() {
        val ex = Exception("AppSync GraphQL error: Validation failed")
        assertEquals(ErrorCategory.GRAPHQL, BackendErrorClassifier.classify(ex))
    }

    @Test
    fun extractStatusBucket_correctMapping() {
        assertEquals("401", BackendErrorClassifier.extractStatusBucket(Exception("Error 401")))
        assertEquals("403", BackendErrorClassifier.extractStatusBucket(Exception("Error 403")))
        assertEquals("404", BackendErrorClassifier.extractStatusBucket(Exception("Error 404")))
        assertEquals("429", BackendErrorClassifier.extractStatusBucket(Exception("Error 429")))
        assertEquals("5xx", BackendErrorClassifier.extractStatusBucket(Exception("Error 500")))
        assertEquals("4xx", BackendErrorClassifier.extractStatusBucket(Exception("Error 400")))
        assertEquals("unknown", BackendErrorClassifier.extractStatusBucket(Exception("Generic error")))
    }
}
