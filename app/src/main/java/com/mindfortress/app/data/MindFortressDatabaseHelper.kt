package com.mindfortress.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import java.util.Calendar

class MindFortressDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        val createBlockEventsTable = """
            CREATE TABLE $TABLE_BLOCK_EVENTS (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_PACKAGE_NAME TEXT NOT NULL,
                $COL_TIMESTAMP INTEGER NOT NULL,
                $COL_BLOCK_REASON TEXT NOT NULL
            )
        """.trimIndent()

        val createFocusSessionsTable = """
            CREATE TABLE $TABLE_FOCUS_SESSIONS (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_SESSION_TYPE TEXT NOT NULL,
                $COL_START_TIME INTEGER NOT NULL,
                $COL_DURATION_MINUTES INTEGER NOT NULL,
                $COL_COMPLETED INTEGER NOT NULL
            )
        """.trimIndent()

        val createUserMistakesTable = """
            CREATE TABLE $TABLE_USER_MISTAKES (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_SUBJECT TEXT NOT NULL,
                $COL_TOPIC TEXT NOT NULL,
                $COL_QUESTION_PROMPT TEXT NOT NULL,
                $COL_USER_MISTAKE TEXT NOT NULL,
                $COL_CORRECT_SOLUTION TEXT NOT NULL,
                $COL_INTERVAL_DAYS INTEGER NOT NULL DEFAULT 1,
                $COL_REPETITIONS INTEGER NOT NULL DEFAULT 0,
                $COL_EASE_FACTOR REAL NOT NULL DEFAULT 2.5,
                $COL_NEXT_REVIEW_TIME INTEGER NOT NULL
            )
        """.trimIndent()

        val createAcademicTopicsTable = """
            CREATE TABLE $TABLE_ACADEMIC_TOPICS (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_SUBJECT TEXT NOT NULL,
                $COL_TOPIC TEXT NOT NULL UNIQUE,
                $COL_MASTERY_SCORE INTEGER NOT NULL DEFAULT 0,
                $COL_TOTAL_REVIEWS INTEGER NOT NULL DEFAULT 0,
                $COL_LAST_REVIEWED INTEGER NOT NULL
            )
        """.trimIndent()

        val createFeynmanSessionsTable = """
            CREATE TABLE $TABLE_FEYNMAN_SESSIONS (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_CONCEPT TEXT NOT NULL,
                $COL_EXPLANATION TEXT NOT NULL,
                $COL_SCORE INTEGER NOT NULL,
                $COL_JARGON_COUNT INTEGER NOT NULL,
                $COL_FEEDBACK TEXT NOT NULL,
                $COL_TIMESTAMP INTEGER NOT NULL
            )
        """.trimIndent()

        val createDopamineRelapsesTable = """
            CREATE TABLE $TABLE_DOPAMINE_RELAPSES (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_TRIGGER_TYPE TEXT NOT NULL,
                $COL_EMOTIONAL_STATE TEXT NOT NULL,
                $COL_NOTES TEXT NOT NULL,
                $COL_COUNTERMEASURE TEXT NOT NULL,
                $COL_TIMESTAMP INTEGER NOT NULL
            )
        """.trimIndent()

        db.execSQL(createBlockEventsTable)
        db.execSQL(createFocusSessionsTable)
        db.execSQL(createUserMistakesTable)
        db.execSQL(createAcademicTopicsTable)
        db.execSQL(createFeynmanSessionsTable)
        db.execSQL(createDopamineRelapsesTable)
        Log.i(TAG, "All MindFortress SQLite tables created successfully.")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_BLOCK_EVENTS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_FOCUS_SESSIONS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_USER_MISTAKES")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_ACADEMIC_TOPICS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_FEYNMAN_SESSIONS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_DOPAMINE_RELAPSES")
        onCreate(db)
    }

    /**
     * Logs an interception or block event to the database.
     */
    fun logBlockEvent(packageName: String, reason: String) {
        try {
            val values = ContentValues().apply {
                put(COL_PACKAGE_NAME, packageName)
                put(COL_TIMESTAMP, System.currentTimeMillis())
                put(COL_BLOCK_REASON, reason)
            }
            writableDatabase.insert(TABLE_BLOCK_EVENTS, null, values)
            Log.d(TAG, "Logged block event: $packageName ($reason)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to log block event: ${e.message}")
        }
    }

    /**
     * Returns count of blocked events that occurred since 00:00 today.
     */
    fun getBlockEventsCountToday(): Int {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = cal.timeInMillis
        var count = 0
        try {
            val cursor = readableDatabase.rawQuery(
                "SELECT COUNT(*) FROM $TABLE_BLOCK_EVENTS WHERE $COL_TIMESTAMP >= ?",
                arrayOf(startOfDay.toString())
            )
            cursor.use {
                if (it.moveToFirst()) {
                    count = it.getInt(0)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query block events count today: ${e.message}")
        }
        return count
    }

    /**
     * Records a deep work or monk mode focus session.
     */
    fun logFocusSession(sessionType: String, startTime: Long, durationMinutes: Int, completed: Boolean) {
        try {
            val values = ContentValues().apply {
                put(COL_SESSION_TYPE, sessionType)
                put(COL_START_TIME, startTime)
                put(COL_DURATION_MINUTES, durationMinutes)
                put(COL_COMPLETED, if (completed) 1 else 0)
            }
            writableDatabase.insert(TABLE_FOCUS_SESSIONS, null, values)
            Log.d(TAG, "Logged focus session: $sessionType ($durationMinutes min, completed=$completed)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to log focus session: ${e.message}")
        }
    }

    // --- USER MISTAKES & SUPERMEMO SM-2 ENGINE ---

    fun recordUserMistake(
        subject: String,
        topic: String,
        questionPrompt: String,
        userMistake: String,
        correctSolution: String
    ): Long {
        return try {
            val now = System.currentTimeMillis()
            val values = ContentValues().apply {
                put(COL_SUBJECT, subject)
                put(COL_TOPIC, topic)
                put(COL_QUESTION_PROMPT, questionPrompt)
                put(COL_USER_MISTAKE, userMistake)
                put(COL_CORRECT_SOLUTION, correctSolution)
                put(COL_INTERVAL_DAYS, 1)
                put(COL_REPETITIONS, 0)
                put(COL_EASE_FACTOR, 2.5f)
                put(COL_NEXT_REVIEW_TIME, now + (24 * 60 * 60 * 1000L)) // 1 day out
            }
            val id = writableDatabase.insert(TABLE_USER_MISTAKES, null, values)
            upsertAcademicTopic(subject, topic, scoreDelta = -5)
            Log.d(TAG, "Recorded user mistake #$id for $topic")
            id
        } catch (e: Exception) {
            Log.e(TAG, "Error recording user mistake: ${e.message}")
            -1L
        }
    }

    fun getDueUserMistakes(limit: Int = 5): List<UserMistakeRecord> {
        val list = mutableListOf<UserMistakeRecord>()
        try {
            val now = System.currentTimeMillis()
            val cursor = readableDatabase.query(
                TABLE_USER_MISTAKES,
                null,
                "$COL_NEXT_REVIEW_TIME <= ?",
                arrayOf(now.toString()),
                null,
                null,
                "$COL_NEXT_REVIEW_TIME ASC",
                limit.toString()
            )
            cursor.use {
                while (it.moveToNext()) {
                    list.add(
                        UserMistakeRecord(
                            id = it.getLong(it.getColumnIndexOrThrow(COL_ID)),
                            subject = it.getString(it.getColumnIndexOrThrow(COL_SUBJECT)),
                            topic = it.getString(it.getColumnIndexOrThrow(COL_TOPIC)),
                            questionPrompt = it.getString(it.getColumnIndexOrThrow(COL_QUESTION_PROMPT)),
                            userMistake = it.getString(it.getColumnIndexOrThrow(COL_USER_MISTAKE)),
                            correctSolution = it.getString(it.getColumnIndexOrThrow(COL_CORRECT_SOLUTION)),
                            intervalDays = it.getInt(it.getColumnIndexOrThrow(COL_INTERVAL_DAYS)),
                            repetitions = it.getInt(it.getColumnIndexOrThrow(COL_REPETITIONS)),
                            easeFactor = it.getFloat(it.getColumnIndexOrThrow(COL_EASE_FACTOR)),
                            nextReviewTime = it.getLong(it.getColumnIndexOrThrow(COL_NEXT_REVIEW_TIME))
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching due mistakes: ${e.message}")
        }
        return list
    }

    fun reviewUserMistake(mistakeId: Long, qualityScore: Int): UserMistakeRecord? {
        // SM-2 algorithm: quality 0-5
        val q = qualityScore.coerceIn(0, 5)
        try {
            val cursor = readableDatabase.query(
                TABLE_USER_MISTAKES,
                null,
                "$COL_ID = ?",
                arrayOf(mistakeId.toString()),
                null,
                null,
                null
            )
            var current: UserMistakeRecord? = null
            cursor.use {
                if (it.moveToFirst()) {
                    current = UserMistakeRecord(
                        id = it.getLong(it.getColumnIndexOrThrow(COL_ID)),
                        subject = it.getString(it.getColumnIndexOrThrow(COL_SUBJECT)),
                        topic = it.getString(it.getColumnIndexOrThrow(COL_TOPIC)),
                        questionPrompt = it.getString(it.getColumnIndexOrThrow(COL_QUESTION_PROMPT)),
                        userMistake = it.getString(it.getColumnIndexOrThrow(COL_USER_MISTAKE)),
                        correctSolution = it.getString(it.getColumnIndexOrThrow(COL_CORRECT_SOLUTION)),
                        intervalDays = it.getInt(it.getColumnIndexOrThrow(COL_INTERVAL_DAYS)),
                        repetitions = it.getInt(it.getColumnIndexOrThrow(COL_REPETITIONS)),
                        easeFactor = it.getFloat(it.getColumnIndexOrThrow(COL_EASE_FACTOR)),
                        nextReviewTime = it.getLong(it.getColumnIndexOrThrow(COL_NEXT_REVIEW_TIME))
                    )
                }
            }
            if (current == null) return null

            var reps = current!!.repetitions
            var interval = current!!.intervalDays
            var ef = current!!.easeFactor

            if (q >= 3) {
                when (reps) {
                    0 -> interval = 1
                    1 -> interval = 6
                    else -> interval = (interval * ef).toInt().coerceAtLeast(interval + 1)
                }
                reps++
            } else {
                reps = 0
                interval = 1
            }

            // EF' = EF + (0.1 - (5 - q) * (0.08 + (5 - q) * 0.02))
            ef = ef + (0.1f - (5 - q) * (0.08f + (5 - q) * 0.02f))
            if (ef < 1.3f) ef = 1.3f

            val nextReview = System.currentTimeMillis() + (interval.toLong() * 24 * 60 * 60 * 1000L)

            val values = ContentValues().apply {
                put(COL_REPETITIONS, reps)
                put(COL_INTERVAL_DAYS, interval)
                put(COL_EASE_FACTOR, ef)
                put(COL_NEXT_REVIEW_TIME, nextReview)
            }
            writableDatabase.update(TABLE_USER_MISTAKES, values, "$COL_ID = ?", arrayOf(mistakeId.toString()))
            upsertAcademicTopic(current!!.subject, current!!.topic, scoreDelta = if (q >= 4) 15 else 5)

            return current!!.copy(
                intervalDays = interval,
                repetitions = reps,
                easeFactor = ef,
                nextReviewTime = nextReview
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error updating mistake SM-2 review: ${e.message}")
            return null
        }
    }

    fun upsertAcademicTopic(subject: String, topic: String, scoreDelta: Int) {
        try {
            val cursor = readableDatabase.query(
                TABLE_ACADEMIC_TOPICS,
                null,
                "$COL_TOPIC = ?",
                arrayOf(topic),
                null,
                null,
                null
            )
            val now = System.currentTimeMillis()
            var existingScore = 50
            var existingReviews = 0
            var exists = false
            cursor.use {
                if (it.moveToFirst()) {
                    exists = true
                    existingScore = it.getInt(it.getColumnIndexOrThrow(COL_MASTERY_SCORE))
                    existingReviews = it.getInt(it.getColumnIndexOrThrow(COL_TOTAL_REVIEWS))
                }
            }

            val newScore = (existingScore + scoreDelta).coerceIn(0, 100)
            val values = ContentValues().apply {
                put(COL_SUBJECT, subject)
                put(COL_TOPIC, topic)
                put(COL_MASTERY_SCORE, newScore)
                put(COL_TOTAL_REVIEWS, existingReviews + 1)
                put(COL_LAST_REVIEWED, now)
            }

            if (exists) {
                writableDatabase.update(TABLE_ACADEMIC_TOPICS, values, "$COL_TOPIC = ?", arrayOf(topic))
            } else {
                writableDatabase.insert(TABLE_ACADEMIC_TOPICS, null, values)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error upserting academic topic: ${e.message}")
        }
    }

    fun getAcademicTopics(): List<AcademicTopicRecord> {
        val list = mutableListOf<AcademicTopicRecord>()
        try {
            val cursor = readableDatabase.query(
                TABLE_ACADEMIC_TOPICS,
                null,
                null,
                null,
                null,
                null,
                "$COL_MASTERY_SCORE ASC"
            )
            cursor.use {
                while (it.moveToNext()) {
                    list.add(
                        AcademicTopicRecord(
                            id = it.getLong(it.getColumnIndexOrThrow(COL_ID)),
                            subject = it.getString(it.getColumnIndexOrThrow(COL_SUBJECT)),
                            topic = it.getString(it.getColumnIndexOrThrow(COL_TOPIC)),
                            masteryScore = it.getInt(it.getColumnIndexOrThrow(COL_MASTERY_SCORE)),
                            totalReviews = it.getInt(it.getColumnIndexOrThrow(COL_TOTAL_REVIEWS)),
                            lastReviewed = it.getLong(it.getColumnIndexOrThrow(COL_LAST_REVIEWED))
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching academic topics: ${e.message}")
        }
        return list
    }

    // --- FEYNMAN TECHNIQUE SESSIONS ---

    fun recordFeynmanSession(
        concept: String,
        explanation: String,
        score: Int,
        jargonCount: Int,
        feedback: String
    ): Long {
        return try {
            val values = ContentValues().apply {
                put(COL_CONCEPT, concept)
                put(COL_EXPLANATION, explanation)
                put(COL_SCORE, score)
                put(COL_JARGON_COUNT, jargonCount)
                put(COL_FEEDBACK, feedback)
                put(COL_TIMESTAMP, System.currentTimeMillis())
            }
            writableDatabase.insert(TABLE_FEYNMAN_SESSIONS, null, values)
        } catch (e: Exception) {
            Log.e(TAG, "Error recording Feynman session: ${e.message}")
            -1L
        }
    }

    // --- DOPAMINE ARMOR & RELAPSE TRACKING ---

    fun recordDopamineRelapse(
        triggerType: String,
        emotionalState: String,
        notes: String,
        countermeasure: String
    ): Long {
        return try {
            val values = ContentValues().apply {
                put(COL_TRIGGER_TYPE, triggerType)
                put(COL_EMOTIONAL_STATE, emotionalState)
                put(COL_NOTES, notes)
                put(COL_COUNTERMEASURE, countermeasure)
                put(COL_TIMESTAMP, System.currentTimeMillis())
            }
            writableDatabase.insert(TABLE_DOPAMINE_RELAPSES, null, values)
        } catch (e: Exception) {
            Log.e(TAG, "Error recording dopamine relapse: ${e.message}")
            -1L
        }
    }

    fun getTopDopamineTriggers(): Map<String, Int> {
        val map = mutableMapOf<String, Int>()
        try {
            val cursor = readableDatabase.rawQuery(
                "SELECT $COL_TRIGGER_TYPE, COUNT(*) as count FROM $TABLE_DOPAMINE_RELAPSES GROUP BY $COL_TRIGGER_TYPE ORDER BY count DESC",
                null
            )
            cursor.use {
                while (it.moveToNext()) {
                    val trigger = it.getString(0)
                    val count = it.getInt(1)
                    map[trigger] = count
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying dopamine triggers: ${e.message}")
        }
        return map
    }

    fun getDopamineRelapseCount(): Int {
        var count = 0
        try {
            val cursor = readableDatabase.rawQuery("SELECT COUNT(*) FROM $TABLE_DOPAMINE_RELAPSES", null)
            cursor.use {
                if (it.moveToFirst()) count = it.getInt(0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying relapse count: ${e.message}")
        }
        return count
    }

    companion object {
        private const val TAG = "MindFortressDB"
        const val DATABASE_NAME = "mindfortress_local.db"
        const val DATABASE_VERSION = 2

        const val TABLE_BLOCK_EVENTS = "block_events"
        const val TABLE_FOCUS_SESSIONS = "focus_sessions"
        const val TABLE_USER_MISTAKES = "user_mistakes"
        const val TABLE_ACADEMIC_TOPICS = "academic_topics"
        const val TABLE_FEYNMAN_SESSIONS = "feynman_sessions"
        const val TABLE_DOPAMINE_RELAPSES = "dopamine_relapses"

        const val COL_ID = "id"
        const val COL_PACKAGE_NAME = "package_name"
        const val COL_TIMESTAMP = "timestamp"
        const val COL_BLOCK_REASON = "block_reason"

        const val COL_SESSION_TYPE = "session_type"
        const val COL_START_TIME = "start_time"
        const val COL_DURATION_MINUTES = "duration_minutes"
        const val COL_COMPLETED = "completed"

        const val COL_SUBJECT = "subject"
        const val COL_TOPIC = "topic"
        const val COL_QUESTION_PROMPT = "question_prompt"
        const val COL_USER_MISTAKE = "user_mistake"
        const val COL_CORRECT_SOLUTION = "correct_solution"
        const val COL_INTERVAL_DAYS = "interval_days"
        const val COL_REPETITIONS = "repetitions"
        const val COL_EASE_FACTOR = "ease_factor"
        const val COL_NEXT_REVIEW_TIME = "next_review_time"

        const val COL_MASTERY_SCORE = "mastery_score"
        const val COL_TOTAL_REVIEWS = "total_reviews"
        const val COL_LAST_REVIEWED = "last_reviewed"

        const val COL_CONCEPT = "concept"
        const val COL_EXPLANATION = "explanation"
        const val COL_SCORE = "score"
        const val COL_JARGON_COUNT = "jargon_count"
        const val COL_FEEDBACK = "feedback"

        const val COL_TRIGGER_TYPE = "trigger_type"
        const val COL_EMOTIONAL_STATE = "emotional_state"
        const val COL_NOTES = "notes"
        const val COL_COUNTERMEASURE = "countermeasure"

        @Volatile
        private var instance: MindFortressDatabaseHelper? = null

        fun getInstance(context: Context): MindFortressDatabaseHelper {
            return instance ?: synchronized(this) {
                instance ?: MindFortressDatabaseHelper(context.applicationContext).also { instance = it }
            }
        }
    }
}

data class UserMistakeRecord(
    val id: Long,
    val subject: String,
    val topic: String,
    val questionPrompt: String,
    val userMistake: String,
    val correctSolution: String,
    val intervalDays: Int,
    val repetitions: Int,
    val easeFactor: Float,
    val nextReviewTime: Long
)

data class AcademicTopicRecord(
    val id: Long,
    val subject: String,
    val topic: String,
    val masteryScore: Int,
    val totalReviews: Int,
    val lastReviewed: Long
)

data class FeynmanSessionRecord(
    val id: Long,
    val concept: String,
    val explanation: String,
    val score: Int,
    val jargonCount: Int,
    val feedback: String,
    val timestamp: Long
)

data class RelapseRecord(
    val id: Long,
    val triggerType: String,
    val emotionalState: String,
    val notes: String,
    val countermeasure: String,
    val timestamp: Long
)
