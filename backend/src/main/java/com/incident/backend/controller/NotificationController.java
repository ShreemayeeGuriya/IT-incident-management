package com.incident.backend.controller;

import com.incident.backend.dto.NotificationResponse;
import com.incident.backend.dto.PageResponse;
import com.incident.backend.dto.UnreadNotificationCountResponse;
import com.incident.backend.security.UserPrincipal;
import com.incident.backend.service.NotificationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public PageResponse<NotificationResponse> list(@RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "20") int size,
                                                    @AuthenticationPrincipal UserPrincipal principal) {
        return notificationService.listForUser(principal.getUser(), page, size);
    }

    @GetMapping("/unread-count")
    public UnreadNotificationCountResponse unreadCount(@AuthenticationPrincipal UserPrincipal principal) {
        return notificationService.unreadCount(principal.getUser());
    }

    @PatchMapping("/{id}/read")
    public NotificationResponse markRead(@PathVariable Long id,
                                         @AuthenticationPrincipal UserPrincipal principal) {
        return notificationService.markRead(id, principal.getUser());
    }
}
