package com.yeex.dlof.data.repository

import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await

/**
 * Writes to /reports/{autoId} — a node [database.rules.json][/firebase/database.rules.json]
 * already fully validates (reporterId, targetType, targetId, reason, status,
 * createdAt) and restricts to admin-only read, but that nothing in the app
 * actually wrote to before this: [com.yeex.dlof.ui.components.ParagraphCard]'s
 * "الإبلاغ" overflow action used to just show a "قريبًا" toast. Reports are
 * create-only from the client (see the ".write" rule) — reviewing them is an
 * admin-side concern, matching [verificationRequests]'s same pattern.
 */
class ReportRepository(
    private val db: FirebaseDatabase = FirebaseDatabase.getInstance()
) {
    private val reportsRef get() = db.getReference("reports")

    suspend fun submitReport(
        reporterId: String,
        reporterIdentifier: String,
        targetType: String, // "user" | "paragraph" | "room"
        targetId: String,
        reason: String,
        note: String = ""
    ) {
        val ref = reportsRef.push()
        val payload = mapOf(
            "reporterId" to reporterId,
            "reporterIdentifier" to reporterIdentifier,
            "targetType" to targetType,
            "targetId" to targetId,
            "reason" to reason,
            "note" to note,
            "status" to "pending",
            "createdAt" to System.currentTimeMillis()
        )
        ref.setValue(payload).await()
    }
}
