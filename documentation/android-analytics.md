# MeritRanker Android — Production Analytics, Crashlytics & Client Observability

## 1. Overview & Architecture

The MeritRanker Android application (`com.bytechminds.meritranker`) integrates a lightweight, fail-open observability layer backed by **Firebase Analytics (GA4)** and **Firebase Crashlytics**.

### Authority & Architecture Rules
- **Decoupled Facade**: Telemetry is abstracted behind `AppObservability`, `AnalyticsTracker`, and `CrashReporter`.
- **Zero Recomposition Noise**: Screen views are tracked strictly on navigation destination changes.
- **Strict PII & Content Redaction**: Question text, AI answers, conversation transcripts, voice audio, student names, emails, and authentication tokens are strictly forbidden from telemetry.
- **Fail-Open Policy**: Any Firebase initialization or network outage never impacts or blocks core learning features.
- **Session-Based Crashlytics Rate Limiting**: Repeated identical backend non-fatal errors (`operation + error_category + status`) are deduplicated with a 5-minute sliding window per session.

---

## 2. Canonical Screen Taxonomy

| Canonical Name | Destination Key / Composable | Business Context |
| :--- | :--- | :--- |
| `login` | `LoginScreen` | Pre-auth student landing |
| `onboarding_name` | `OnboardingScreen` (Step 1) | Student profile setup |
| `onboarding_exam` | `OnboardingScreen` (Step 2) | Target exam selection |
| `onboarding_stage` | `OnboardingScreen` (Step 3) | Exam tier / stage selection |
| `onboarding_language` | `OnboardingScreen` (Step 4) | Study language preference |
| `smart_tutor` | `AskDoubtScreen` / Tab 0 | AI Doubt Solving & Conversation |
| `smart_tutor_voice` | Voice Mode Sheet | Hands-free voice questioning |
| `practice_home` | `PracticeScreen` / Tab 1 | Practice hub & quick actions |
| `practice_list` | Quiz / Mock / PYQ / Wrong list | Activity selection catalog |
| `practice_player` | `QuestionPlayerScreen` | Real-time question testing engine |
| `practice_result` | `ResultFeedbackScreen` | Post-test score summary |
| `practice_review` | `PracticeReviewScreen` | Detailed solution & explanation view |
| `progress` | `ProgressScreen` / Tab 2 | Analytics & mastery dashboard |
| `subject_progress` | `SubjectProgressScreen` | Drill-down subject mastery |
| `profile` | `ProfileSettingsScreen` / Tab 3 | Student profile & preferences |
| `profile_feedback` | Feedback BottomSheet | Bug reports & feature requests |
| `profile_legal` | Legal & Terms Screen | Privacy policy & terms of service |
| `mandatory_update` | In-App Update Dialog | Play Store update enforcement |

---

## 3. Event Taxonomy & Parameters

### A. Authentication & Onboarding
| Event Name | When Emitted | Parameters | PII Class | Business Question Answered |
| :--- | :--- | :--- | :--- | :--- |
| `login_started` | User taps Google Sign-In | `method` (`"google"`) | Non-PII | How many sign-in attempts occur? |
| `login_succeeded` | OAuth handshake completes | `method` (`"google"`) | Non-PII | What is the login conversion rate? |
| `login_failed` | OAuth or token exchange fails | `method`, `error_category` | Non-PII | Why are students failing to sign in? |
| `onboarding_started` | Incomplete profile screen opens | *None* | Non-PII | How many new students enter onboarding? |
| `onboarding_completed` | User completes onboarding | `exam_profile_id`, `stage`, `study_language` | Non-PII | What is onboarding completion rate & primary exam choice? |
| `onboarding_failed` | Onboarding save API fails | `error_category` | Non-PII | Are onboarding errors network or backend related? |
| `exam_profile_selected` | User switches exam in top bar | `exam_profile_id`, `stage` | Non-PII | How often do students switch exam contexts? |

### B. Smart Tutor
| Event Name | When Emitted | Parameters | PII Class | Business Question Answered |
| :--- | :--- | :--- | :--- | :--- |
| `doubt_submitted` | Student sends doubt query | `exam_profile_id`, `has_attachment`, `is_voice` | Non-PII (Zero content) | How many doubts are submitted per exam? |
| `doubt_stream_started` | SSE connection established | `exam_profile_id` | Non-PII | Is the AI streaming backend responding? |
| `doubt_first_response_received` | First token received from SSE | `exam_profile_id`, `latency_bucket` | Non-PII | What is the Time-to-First-Token (TTFT)? |
| `doubt_completed` | Full AI answer generated | `exam_profile_id`, `duration_bucket`, `is_follow_up` | Non-PII | What is total query completion duration? |
| `doubt_failed` | Stream disconnects / 5xx | `exam_profile_id`, `error_category`, `duration_bucket` | Non-PII | What causes Smart Tutor failures? |
| `doubt_report_submitted` | Student reports incorrect AI answer | `report_category` | Non-PII | Which topics or answers trigger student reports? |
| `voice_started` | Student taps microphone | `mode` (`"auto"`, `"hindi"`, `"english"`) | Non-PII (Zero audio) | How frequently is voice mode used? |
| `voice_completed` | Speech recognition completes | `mode`, `duration_bucket` | Non-PII | How long are voice queries on average? |
| `voice_cancelled` | User closes voice mode | `mode` | Non-PII | What is voice cancellation frequency? |
| `voice_failed` | Speech recognizer errors | `mode`, `error_category` | Non-PII | Why does speech recognition fail? |

### C. Practice & Testing
| Event Name | When Emitted | Parameters | PII Class | Business Question Answered |
| :--- | :--- | :--- | :--- | :--- |
| `practice_viewed` | Practice home tab opens | `exam_profile_id` | Non-PII | How many students browse practice tests? |
| `practice_started` | New test attempt loads | `exam_profile_id`, `practice_type`, `question_count_bucket` | Non-PII | What test types are most popular? |
| `practice_resumed` | In-progress test resumed | `exam_profile_id`, `practice_type` | Non-PII | How often do students resume interrupted tests? |
| `practice_submitted` | Student submits answers | `exam_profile_id`, `practice_type`, `answered_count_bucket` | Non-PII | How many questions do students attempt? |
| `practice_completed` | Test results finalized | `exam_profile_id`, `practice_type`, `duration_bucket` | Non-PII | How many tests are completed vs abandoned? |
| `practice_abandoned` | Player exited before submission | `exam_profile_id`, `practice_type`, `progress_bucket` | Non-PII | At what completion percentage do students drop off? |

### D. Progress & Mastery
| Event Name | When Emitted | Parameters | PII Class | Business Question Answered |
| :--- | :--- | :--- | :--- | :--- |
| `progress_viewed` | Progress tab loads | `exam_profile_id`, `view` (`PRACTICE` / `REAL_EXAM`) | Non-PII | How often do students check analytics? |
| `subject_progress_viewed` | Subject drill-down opened | `exam_profile_id`, `subject_id`, `view` | Non-PII | Which subjects receive the most review? |
| `progress_refresh_failed` | Progress revalidation fails | `exam_profile_id`, `error_category` | Non-PII | Are performance metrics updating reliably? |

### E. System Reliability & Backend Operations
| Event Name | When Emitted | Parameters | PII Class | Business Question Answered |
| :--- | :--- | :--- | :--- | :--- |
| `backend_operation` | On completion of critical backend operation (Denominator tracking) | `feature`, `operation`, `outcome` (`"success"`/`"failure"`), `duration_bucket`, `error_category` (failures only), `status_bucket`, `network_state` | Non-PII | What is the total volume and success/failure rate per backend API? |
| `backend_operation_failed` | On backend server/network API failure | `feature`, `operation`, `error_category`, `status_bucket`, `is_retryable`, `network_state` | Non-PII | Which backend endpoints fail and what status code? |
| `feature_error` | Generic client feature failure | `feature`, `operation`, `error_category`, `is_retryable` | Non-PII | Which client features generate errors? |

---

## 4. User Properties

| Property Name | Allowed Values | Purpose |
| :--- | :--- | :--- |
| `study_language` | `"english"`, `"hindi"`, `"en"`, `"hi"` | Demographic language segmentation |
| `onboarding_status` | `"completed"`, `"in_progress"` | Funnel conversion segmentation |
| `exam_profile_id` | Authoritative ID (e.g. `RRB_NTPC_CBT_1_2024`) | Cohort segmentation by active target exam (if user-level filtering is required) |

---

## 5. Google Analytics 4 (GA4) Configuration Steps

### Custom Dimensions to Register in GA4:
1. `exam_profile_id` (Event-scoped)
2. `practice_type` (Event-scoped)
3. `feature` (Event-scoped)
4. `operation` (Event-scoped)
5. `outcome` (Event-scoped)
6. `error_category` (Event-scoped)
7. `status_bucket` (Event-scoped)
8. `latency_bucket` (Event-scoped)
9. `progress_bucket` (Event-scoped)
10. `study_language` (User-scoped)
11. `onboarding_status` (User-scoped)

### Recommended Key Events (Conversions):
- `onboarding_completed`
- `doubt_completed`
- `practice_completed`
