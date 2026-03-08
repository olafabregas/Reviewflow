package com.reviewflow.model.enums;

public enum NotificationType {

    // ── TEAM ─────────────────────────────────────────────────────
    TEAM_INVITE,           // Student invited to join a team
    TEAM_LOCKED,           // Team lock date passed or manually locked by instructor

    // ── SUBMISSIONS ───────────────────────────────────────────────
    NEW_SUBMISSION,        // Team member uploaded a new version

    // ── EVALUATIONS ───────────────────────────────────────────────
    FEEDBACK_PUBLISHED,    // Instructor published an evaluation

    // ── DEADLINES ─────────────────────────────────────────────────
    DEADLINE_WARNING_48H,  // Assignment due in 48 hours — student has not submitted
    DEADLINE_WARNING_24H   // Assignment due in 24 hours — student has not submitted
}