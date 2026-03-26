package com.reviewflow.model.enums;

public enum NotificationType {

    // ── TEAM ─────────────────────────────────────────────────────
    TEAM_INVITE, // Student invited to join a team
    TEAM_LOCKED, // Team lock date passed or manually locked by instructor

    // ── SUBMISSIONS ───────────────────────────────────────────────
    NEW_SUBMISSION, // Team member uploaded a new version

    // ── EVALUATIONS ───────────────────────────────────────────────
    FEEDBACK_PUBLISHED, // Instructor published an evaluation

    // ── ANNOUNCEMENTS ──────────────────────────────────────────────
    ANNOUNCEMENT, // Announcement published (course or platform)
    SYSTEM, // Generic system workflow notifications (e.g., extension requests/decisions)

    // ── DEADLINES ─────────────────────────────────────────────────
    DEADLINE_WARNING_48H, // Assignment due in 48 hours — student has not submitted
    DEADLINE_WARNING_24H   // Assignment due in 24 hours — student has not submitted
}
