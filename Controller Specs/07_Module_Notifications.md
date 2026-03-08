# ReviewFlow — Module 7: Notifications
> Controller: `NotificationController.java`
> Base path: `/api/v1/notifications`

---

## 7.1 GET /notifications

### Must Have
- [ ] Returns all notifications for the currently authenticated user
- [ ] Sorted by `created_at DESC` (newest first)
- [ ] Each notification: `{ id, type, title, message, isRead, actionUrl, createdAt }`
- [ ] `actionUrl` is a relative frontend path for navigation on click
- [ ] Supports `?unreadOnly=true` filter
- [ ] Supports pagination

### Responses
- [ ] `200 OK` — paginated notifications
- [ ] `401 Unauthorized`

---

## 7.2 GET /notifications/unread-count

### Must Have
- [ ] Returns count of unread notifications for current user
- [ ] Response: `{ "count": 3 }`
- [ ] This endpoint is polled every 30s by frontend — must be fast
- [ ] No pagination — just the count

### Responses
- [ ] `200 OK` — `{ count: 0 }` (never null, always a number)
- [ ] `401 Unauthorized`

---

## 7.3 PATCH /notifications/{id}/read

### Must Have
- [ ] Marks a single notification as read
- [ ] Notification must belong to the current user
- [ ] Sets `is_read = true`

### Responses
- [ ] `200 OK`
- [ ] `403 Forbidden` — notification belongs to a different user
- [ ] `404 Not Found`

---

## 7.4 PATCH /notifications/read-all

### Must Have
- [ ] Marks ALL unread notifications for current user as read
- [ ] Returns `{ message: "All notifications marked as read", updatedCount: 5 }`

### Responses
- [ ] `200 OK` — all marked read (even if 0 unread — idempotent)
- [ ] `401 Unauthorized`

---

## 7.5 DELETE /notifications/{id} ⭐ NEW

### Must Have
- [ ] Deletes a single notification
- [ ] Notification must belong to the current user

### Responses
- [ ] `200 OK` — `{ message: "Notification deleted" }`
- [ ] `403 Forbidden` — belongs to different user
- [ ] `404 Not Found`
