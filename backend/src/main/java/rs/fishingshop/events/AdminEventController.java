package rs.fishingshop.events;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import rs.fishingshop.auth.EmployeePrincipal;

@RestController
class AdminEventController {

    private final AdminEventBroadcaster broadcaster;

    AdminEventController(AdminEventBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    // no-transform: the Next.js proxy gzips responses and would hold back the stream until enough
    // bytes collect; its compression skips no-transform responses (measured in the 5.4 spike).
    @GetMapping(path = "/api/admin/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter events(@AuthenticationPrincipal EmployeePrincipal employee,
            @RequestHeader(name = "Last-Event-ID", required = false) String lastEventId,
            HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-cache, no-transform");
        response.setHeader("X-Accel-Buffering", "no");
        return broadcaster.subscribe(employee.getRole(), lastEventId);
    }
}
