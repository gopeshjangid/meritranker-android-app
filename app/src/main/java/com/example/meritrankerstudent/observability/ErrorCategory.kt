package com.example.meritrankerstudent.observability

enum class ErrorCategory(val key: String) {
    OFFLINE("offline"),
    TIMEOUT("timeout"),
    DNS_NETWORK("dns_network"),
    AUTH("auth"),
    FORBIDDEN("forbidden"),
    NOT_FOUND("not_found"),
    RATE_LIMITED("rate_limited"),
    VALIDATION("validation"),
    BACKEND_4XX("backend_4xx"),
    BACKEND_5XX("backend_5xx"),
    GRAPHQL("graphql"),
    CONTRACT_PARSE("contract_parse"),
    STREAM_DISCONNECT("stream_disconnect"),
    DATABASE("database"),
    UNKNOWN("unknown")
}

enum class OperationName(val key: String) {
    GET_EXAM_PROFILES("get_exam_profiles"),
    GET_STUDENT_PERFORMANCE("get_student_performance"),
    GENERATE_PRACTICE("generate_practice"),
    GET_PRACTICE("get_practice"),
    SAVE_PRACTICE_RESPONSE("save_practice_response"),
    SUBMIT_PRACTICE_ATTEMPT("submit_practice_attempt"),
    SUBMIT_PRODUCT_FEEDBACK("submit_product_feedback"),
    SUBMIT_AI_REPORT("submit_ai_report"),
    SMART_TUTOR_STREAM("smart_tutor_stream"),
    PROFILE_LOAD("profile_load"),
    PROFILE_UPDATE("profile_update"),
    AUTH_SIGN_IN("auth_sign_in"),
    AUTH_SIGN_OUT("auth_sign_out"),
    AUTH_DELETE_ACCOUNT("auth_delete_account"),
    CHECK_APP_UPDATE("check_app_update")
}
