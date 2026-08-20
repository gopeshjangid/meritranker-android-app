package com.example.meritrankerstudent.observability

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

object BackendErrorClassifier {

    fun classify(throwable: Throwable?): ErrorCategory {
        if (throwable == null) return ErrorCategory.UNKNOWN

        val message = throwable.message?.lowercase() ?: ""

        return when {
            throwable is SocketTimeoutException || message.contains("timeout") -> ErrorCategory.TIMEOUT
            throwable is UnknownHostException || throwable is ConnectException || message.contains("unable to resolve host") -> ErrorCategory.DNS_NETWORK
            message.contains("offline") || message.contains("no address associated") || message.contains("network unreachable") -> ErrorCategory.OFFLINE
            message.contains("401") || message.contains("unauthorized") || message.contains("not authenticated") -> ErrorCategory.AUTH
            message.contains("403") || message.contains("forbidden") || message.contains("access denied") -> ErrorCategory.FORBIDDEN
            message.contains("404") || message.contains("not found") -> ErrorCategory.NOT_FOUND
            message.contains("429") || message.contains("too many requests") || message.contains("rate limit") -> ErrorCategory.RATE_LIMITED
            message.contains("500") || message.contains("502") || message.contains("503") || message.contains("504") || message.contains("internal server error") -> ErrorCategory.BACKEND_5XX
            message.contains("400") || message.contains("bad request") || message.contains("422") -> ErrorCategory.BACKEND_4XX
            message.contains("graphql") || message.contains("appsync") -> ErrorCategory.GRAPHQL
            message.contains("json") || message.contains("parse") || message.contains("serialization") -> ErrorCategory.CONTRACT_PARSE
            message.contains("stream") || message.contains("sse") || message.contains("disconnect") -> ErrorCategory.STREAM_DISCONNECT
            message.contains("sqlite") || message.contains("room") || message.contains("database") -> ErrorCategory.DATABASE
            message.contains("validation") || message.contains("invalid argument") -> ErrorCategory.VALIDATION
            throwable is IOException -> ErrorCategory.DNS_NETWORK
            else -> ErrorCategory.UNKNOWN
        }
    }

    fun extractStatusBucket(throwable: Throwable?): String {
        if (throwable == null) return "unknown"
        val message = throwable.message ?: ""
        return when {
            message.contains("401") -> "401"
            message.contains("403") -> "403"
            message.contains("404") -> "404"
            message.contains("409") -> "409"
            message.contains("429") -> "429"
            message.contains("500") || message.contains("502") || message.contains("503") || message.contains("504") -> "5xx"
            message.contains("400") || message.contains("422") -> "4xx"
            else -> "unknown"
        }
    }
}
