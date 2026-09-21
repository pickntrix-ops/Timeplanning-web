package com.timeplanning.app

import kotlinx.serialization.Serializable

/** Mirrors the server's TaskType enum (server/domain/Enums.kt). */
enum class TaskType { EXERCISE, CLEANING, JOB, PROJECT, GENERAL }

enum class TaskStatus { PENDING, SCHEDULED, COMPLETED, ABANDONED }

enum class GeneralSubcategory { LAPTOP, SHOPPING, ERRAND, ADMIN, SOCIAL }

enum class CleaningTier { BASIC, IN_DEPTH }

@Serializable
data class Task(
    val id: Long,
    val name: String,
    val taskType: TaskType,
    val subcategory: GeneralSubcategory? = null,
    val cleaningTier: CleaningTier? = null,
    val personId: Long? = null,
    val personName: String? = null,
    val blockInstanceId: Long? = null,
    val dueDate: String? = null,
    val durationMinutes: Int,
    val isPinned: Boolean = false,
    val pinnedDay: String? = null,
    val status: TaskStatus,
    val rolloverCount: Int = 0,
    val queuePosition: Int? = null,
)

@Serializable
data class CreateTaskRequest(
    val name: String,
    val taskType: TaskType,
    val subcategory: GeneralSubcategory? = null,
    val cleaningTier: CleaningTier? = null,
    val dueDate: String? = null,
    val durationMinutes: Int,
)

@Serializable
data class BlockInstance(
    val id: Long,
    val templateId: Long? = null,
    val blockDate: String,
    val taskType: TaskType,
    val startTime: String,
    val endTime: String,
    val source: String,
    val isProtected: Boolean = false,
    val notes: String? = null,
)
