package com.example.meritrankerstudent.data.local

import com.example.meritrankerstudent.data.model.*
import org.json.JSONArray
import org.json.JSONObject

object EntityMappers {

    // UserProfile <-> CachedProfileEntity
    fun userProfileToEntity(profile: UserProfile): CachedProfileEntity {
        return CachedProfileEntity(
            userId = profile.userId,
            name = profile.name,
            email = profile.email,
            preparing = profile.preparing,
            examProfileId = profile.examProfileId,
            examStage = profile.examStage,
            additionalExamsJson = JSONArray(profile.additionalExams).toString(),
            examTagsJson = if (profile.examTags.isNotEmpty()) JSONArray(profile.examTags).toString() else "[]",
            dateOfBirth = profile.dateOfBirth,
            language = profile.language,
            profileCompleted = profile.profileCompleted,
            onboardingStep = profile.onboardingStep,
            role = profile.role,
            lastSyncedAt = System.currentTimeMillis()
        )
    }

    fun entityToUserProfile(entity: CachedProfileEntity): UserProfile {
        val additionalExams = mutableListOf<String>()
        entity.additionalExamsJson?.let { raw ->
            try {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) additionalExams.add(arr.getString(i))
            } catch (_: Exception) {}
        }

        val examTags = mutableListOf<String>()
        entity.examTagsJson?.let { raw ->
            try {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) examTags.add(arr.getString(i))
            } catch (_: Exception) {}
        }

        return UserProfile(
            userId = entity.userId,
            email = entity.email,
            name = entity.name,
            preparing = entity.preparing,
            examProfileId = entity.examProfileId,
            examStage = entity.examStage,
            additionalExams = additionalExams,
            examTags = examTags,
            dateOfBirth = entity.dateOfBirth,
            language = entity.language,
            profileCompleted = entity.profileCompleted,
            onboardingStep = entity.onboardingStep,
            role = entity.role
        )
    }

    // ExamProfile <-> CachedExamProfileEntity
    fun examProfileToEntity(profile: ExamProfile): CachedExamProfileEntity {
        val sectionsJson = JSONArray().apply {
            profile.sections.forEach { s ->
                put(JSONObject().apply {
                    put("sectionId", s.sectionId)
                    put("name", s.name)
                    put("subject", s.subject)
                    put("level", s.level)
                    put("questionCount", s.questionCount)
                    put("marks", s.marks)
                    put("timeMinutes", s.timeMinutes)
                    put("negativeMarks", s.negativeMarks ?: 0.0)
                    put("marksPerQuestion", s.marksPerQuestion ?: 0.0)
                    put("questionStyles", JSONArray(s.questionStyles))
                    put("excludeTopics", JSONArray(s.excludeTopics))
                })
            }
        }.toString()

        val previousCutoffJson = profile.previousCutoff?.let { cutoff ->
            JSONObject().apply {
                put("year", cutoff.year)
                put("overall", cutoff.overall)
                val cats = JSONArray()
                cutoff.categories.forEach { c ->
                    cats.put(JSONObject().apply {
                        put("category", c.category)
                        put("cutoff", c.cutoff)
                    })
                }
                put("categories", cats)
            }.toString()
        }

        return CachedExamProfileEntity(
            examProfileId = profile.examProfileId,
            examId = profile.examId,
            examName = profile.examName,
            stage = profile.stage,
            description = profile.description,
            sectionsJson = sectionsJson,
            previousCutoffJson = previousCutoffJson,
            active = profile.active,
            totalQuestions = profile.totalQuestions,
            totalMarks = profile.totalMarks,
            totalTimeMinutes = profile.totalTimeMinutes,
            effectiveYear = profile.effectiveYear,
            lastSyncedAt = System.currentTimeMillis()
        )
    }

    fun entityToExamProfile(entity: CachedExamProfileEntity): ExamProfile {
        val sections = mutableListOf<ExamSection>()
        entity.sectionsJson?.let { raw ->
            try {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val qStyles = mutableListOf<String>()
                    val qStylesArr = obj.optJSONArray("questionStyles")
                    if (qStylesArr != null) {
                        for (j in 0 until qStylesArr.length()) qStyles.add(qStylesArr.getString(j))
                    }
                    val exTopics = mutableListOf<String>()
                    val exTopicsArr = obj.optJSONArray("excludeTopics")
                    if (exTopicsArr != null) {
                        for (j in 0 until exTopicsArr.length()) exTopics.add(exTopicsArr.getString(j))
                    }
                    sections.add(
                        ExamSection(
                            sectionId = obj.getString("sectionId"),
                            name = obj.getString("name"),
                            subject = obj.getString("subject"),
                            level = obj.getString("level"),
                            questionCount = obj.getInt("questionCount"),
                            marks = obj.getDouble("marks"),
                            timeMinutes = obj.getInt("timeMinutes"),
                            questionStyles = qStyles,
                            negativeMarks = if (obj.has("negativeMarks")) obj.getDouble("negativeMarks") else null,
                            marksPerQuestion = if (obj.has("marksPerQuestion")) obj.getDouble("marksPerQuestion") else null,
                            excludeTopics = exTopics
                        )
                    )
                }
            } catch (_: Exception) {}
        }

        var previousCutoff: ExamPreviousCutoff? = null
        entity.previousCutoffJson?.let { raw ->
            try {
                val obj = JSONObject(raw)
                val cats = mutableListOf<ExamCategoryCutoff>()
                val catsArr = obj.optJSONArray("categories")
                if (catsArr != null) {
                    for (i in 0 until catsArr.length()) {
                        val cObj = catsArr.getJSONObject(i)
                        cats.add(
                            ExamCategoryCutoff(
                                category = cObj.getString("category"),
                                cutoff = cObj.getDouble("cutoff")
                            )
                        )
                    }
                }
                previousCutoff = ExamPreviousCutoff(
                    year = obj.getInt("year"),
                    overall = obj.getDouble("overall"),
                    categories = cats
                )
            } catch (_: Exception) {}
        }

        return ExamProfile(
            examProfileId = entity.examProfileId,
            examId = entity.examId,
            examName = entity.examName,
            stage = entity.stage,
            description = entity.description ?: "",
            sections = sections,
            totalQuestions = entity.totalQuestions,
            totalMarks = entity.totalMarks,
            totalTimeMinutes = entity.totalTimeMinutes,
            previousCutoff = previousCutoff,
            active = entity.active,
            effectiveYear = entity.effectiveYear
        )
    }

    // PracticeActivitySummary <-> CachedPracticeActivityEntity
    fun practiceActivityToEntity(activity: PracticeActivitySummary, ownerUserId: String): CachedPracticeActivityEntity {
        return CachedPracticeActivityEntity(
            activityId = activity.activityId,
            ownerUserId = ownerUserId,
            title = activity.title,
            activityType = activity.activityType.name,
            language = activity.language,
            subject = activity.subject,
            topic = activity.topic,
            examsJson = if (activity.exams.isNotEmpty()) JSONArray(activity.exams).toString() else null,
            questionCount = activity.questionCount,
            durationMinutes = activity.durationMinutes,
            hasResumableAttempt = activity.hasResumableAttempt,
            resumableAttemptId = activity.resumableAttemptId,
            playable = activity.playable,
            generationStatus = activity.generationStatus,
            readyCount = activity.readyCount,
            progressPercent = activity.progressPercent,
            errorCode = activity.errorCode,
            latestAttemptId = activity.latestAttemptId,
            latestAttemptStatus = activity.latestAttemptStatus,
            latestAttemptScore = activity.latestAttemptScore,
            latestAttemptMaximumScore = activity.latestAttemptMaximumScore,
            lastSyncedAt = System.currentTimeMillis()
        )
    }

    fun entityToPracticeActivity(entity: CachedPracticeActivityEntity): PracticeActivitySummary {
        val exams = mutableListOf<String>()
        entity.examsJson?.let { raw ->
            try {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) exams.add(arr.getString(i))
            } catch (_: Exception) {}
        }

        val type = try {
            PracticeActivityType.valueOf(entity.activityType)
        } catch (_: Exception) {
            PracticeActivityType.QUIZ
        }

        return PracticeActivitySummary(
            activityId = entity.activityId,
            title = entity.title,
            activityType = type,
            language = entity.language,
            subject = entity.subject,
            topic = entity.topic,
            exams = exams,
            questionCount = entity.questionCount,
            durationMinutes = entity.durationMinutes,
            hasResumableAttempt = entity.hasResumableAttempt,
            resumableAttemptId = entity.resumableAttemptId,
            playable = entity.playable,
            generationStatus = entity.generationStatus,
            readyCount = entity.readyCount,
            progressPercent = entity.progressPercent,
            errorCode = entity.errorCode,
            latestAttemptId = entity.latestAttemptId,
            latestAttemptStatus = entity.latestAttemptStatus,
            latestAttemptScore = entity.latestAttemptScore,
            latestAttemptMaximumScore = entity.latestAttemptMaximumScore
        )
    }

    // PracticeQuestionPayload <-> CachedPracticeQuestionEntity
    fun practiceQuestionToEntity(q: PracticeQuestionPayload, activityId: String, ownerUserId: String): CachedPracticeQuestionEntity {
        return CachedPracticeQuestionEntity(
            ownerUserId = ownerUserId,
            activityId = activityId,
            questionId = q.questionId,
            position = q.position,
            question = q.question,
            optionsJson = JSONArray(q.options).toString(),
            section = q.section,
            subject = q.subject,
            topic = q.topic,
            marks = q.marks,
            negativeMarks = q.negativeMarks,
            format = q.format,
            language = q.language,
            lastSyncedAt = System.currentTimeMillis()
        )
    }

    fun entityToPracticeQuestion(entity: CachedPracticeQuestionEntity): PracticeQuestionPayload {
        val options = mutableListOf<String>()
        try {
            val arr = JSONArray(entity.optionsJson)
            for (i in 0 until arr.length()) options.add(arr.getString(i))
        } catch (_: Exception) {}

        return PracticeQuestionPayload(
            questionId = entity.questionId,
            position = entity.position,
            question = entity.question,
            options = options,
            section = entity.section,
            subject = entity.subject,
            topic = entity.topic,
            marks = entity.marks,
            negativeMarks = entity.negativeMarks,
            format = entity.format,
            language = entity.language
        )
    }

    // StudentPerformanceResponse <-> CachedStudentPerformanceEntity
    fun studentPerformanceToEntity(
        response: StudentPerformanceResponse,
        ownerUserId: String,
        examProfileId: String,
        view: GetStudentPerformanceView,
        subjectId: String?,
        isDirty: Boolean = false
    ): CachedStudentPerformanceEntity {
        val overallJson = JSONObject().apply {
            put("attempted", response.overall.attempted)
            put("correct", response.overall.correct)
            put("wrong", response.overall.wrong)
            response.overall.skipped?.let { put("skipped", it) }
            put("accuracy", response.overall.accuracy)
            put("label", response.overall.label)
            put("comment", response.overall.comment)
            response.overall.averageTimeMs?.let { put("averageTimeMs", it) }
            response.overall.targetPaceMs?.let { put("targetPaceMs", it) }
            response.overall.speedLabel?.let { put("speedLabel", it) }
        }.toString()

        val itemsJson = JSONArray().apply {
            response.items.forEach { item ->
                put(JSONObject().apply {
                    put("subjectId", item.subjectId)
                    put("subjectName", item.subjectName)
                    put("attempted", item.attempted)
                    put("correct", item.correct)
                    put("wrong", item.wrong)
                    item.skipped?.let { put("skipped", it) }
                    put("accuracy", item.accuracy)
                    put("label", item.label)
                    put("comment", item.comment)
                    item.averageTimeMs?.let { put("averageTimeMs", it) }
                    item.targetPaceMs?.let { put("targetPaceMs", it) }
                    item.speedLabel?.let { put("speedLabel", it) }
                })
            }
        }.toString()

        val insightsJson = JSONArray().apply {
            response.insights.forEach { ins ->
                put(JSONObject().apply {
                    put("subjectId", ins.subjectId)
                    put("attemptId", ins.attemptId)
                    put("questionId", ins.questionId)
                    put("descriptor", ins.descriptor)
                    put("given", ins.given)
                    put("find", ins.find)
                    ins.commonTrap?.let { put("commonTrap", it) }
                    put("resultType", ins.resultType)
                    put("assessmentMode", ins.assessmentMode)
                    put("createdAt", ins.createdAt)
                })
            }
        }.toString()

        val safeSubjectId = subjectId ?: "ALL"

        return CachedStudentPerformanceEntity(
            ownerUserId = ownerUserId,
            examProfileId = examProfileId,
            view = view.name,
            subjectId = safeSubjectId,
            overallJson = overallJson,
            itemsJson = itemsJson,
            insightsJson = insightsJson,
            isUpdatingLatestPerformance = response.isUpdatingLatestPerformance,
            isDirty = isDirty,
            lastProcessedAt = response.lastProcessedAt,
            lastSyncedAt = System.currentTimeMillis()
        )
    }

    fun entityToStudentPerformance(entity: CachedStudentPerformanceEntity): StudentPerformanceResponse {
        val oObj = JSONObject(entity.overallJson)
        val overall = StudentPerformanceOverall(
            attempted = oObj.optInt("attempted", 0),
            correct = oObj.optInt("correct", 0),
            wrong = oObj.optInt("wrong", 0),
            skipped = if (oObj.has("skipped") && !oObj.isNull("skipped")) oObj.getInt("skipped") else null,
            accuracy = oObj.optDouble("accuracy", 0.0),
            label = oObj.optString("label", ""),
            comment = oObj.optString("comment", ""),
            averageTimeMs = if (oObj.has("averageTimeMs") && !oObj.isNull("averageTimeMs")) oObj.getLong("averageTimeMs") else null,
            targetPaceMs = if (oObj.has("targetPaceMs") && !oObj.isNull("targetPaceMs")) oObj.getLong("targetPaceMs") else null,
            speedLabel = oObj.optString("speedLabel").takeIf { it.isNotEmpty() }
        )

        val items = mutableListOf<StudentPerformanceItem>()
        try {
            val iArr = JSONArray(entity.itemsJson)
            for (i in 0 until iArr.length()) {
                val itemObj = iArr.getJSONObject(i)
                items.add(
                    StudentPerformanceItem(
                        subjectId = itemObj.optString("subjectId"),
                        subjectName = itemObj.optString("subjectName"),
                        attempted = itemObj.optInt("attempted", 0),
                        correct = itemObj.optInt("correct", 0),
                        wrong = itemObj.optInt("wrong", 0),
                        skipped = if (itemObj.has("skipped") && !itemObj.isNull("skipped")) itemObj.getInt("skipped") else null,
                        accuracy = itemObj.optDouble("accuracy", 0.0),
                        label = itemObj.optString("label", ""),
                        comment = itemObj.optString("comment", ""),
                        averageTimeMs = if (itemObj.has("averageTimeMs") && !itemObj.isNull("averageTimeMs")) itemObj.getLong("averageTimeMs") else null,
                        targetPaceMs = if (itemObj.has("targetPaceMs") && !itemObj.isNull("targetPaceMs")) itemObj.getLong("targetPaceMs") else null,
                        speedLabel = itemObj.optString("speedLabel").takeIf { it.isNotEmpty() }
                    )
                )
            }
        } catch (_: Exception) {}

        val insights = mutableListOf<StudentPerformanceInsight>()
        try {
            val insArr = JSONArray(entity.insightsJson)
            for (i in 0 until insArr.length()) {
                val insightObj = insArr.getJSONObject(i)
                insights.add(
                    StudentPerformanceInsight(
                        subjectId = insightObj.optString("subjectId"),
                        attemptId = insightObj.optString("attemptId"),
                        questionId = insightObj.optString("questionId"),
                        descriptor = insightObj.optString("descriptor"),
                        given = insightObj.optString("given"),
                        find = insightObj.optString("find"),
                        commonTrap = insightObj.optString("commonTrap").takeIf { it.isNotEmpty() },
                        resultType = insightObj.optString("resultType", "UNKNOWN"),
                        assessmentMode = insightObj.optString("assessmentMode", "PRACTICE"),
                        createdAt = insightObj.optString("createdAt", "")
                    )
                )
            }
        } catch (_: Exception) {}

        return StudentPerformanceResponse(
            overall = overall,
            items = items,
            insights = insights,
            isUpdatingLatestPerformance = entity.isUpdatingLatestPerformance,
            lastProcessedAt = entity.lastProcessedAt
        )
    }
}

