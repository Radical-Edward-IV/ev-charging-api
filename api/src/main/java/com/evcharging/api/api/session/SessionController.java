package com.evcharging.api.api.session;

import com.evcharging.api.common.ApiResponse;
import com.evcharging.api.domain.session.ChargingSession;
import com.evcharging.api.domain.session.SessionService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping("/chargers/{chargerId}/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SessionResponse> startCharging(@PathVariable Long chargerId) {
        ChargingSession session = sessionService.startCharging(chargerId);
        return ApiResponse.success(SessionResponse.from(session));
    }

    @PatchMapping("/sessions/{id}/complete")
    public ApiResponse<SessionResponse> completeCharging(
            @PathVariable Long id,
            @Valid @RequestBody SessionCompleteRequest request) {
        ChargingSession session = sessionService.completeCharging(
                id, request.energyDeliveredKwh(), request.cost());
        return ApiResponse.success(SessionResponse.from(session));
    }

    @GetMapping("/sessions")
    public ApiResponse<List<SessionResponse>> findSessions(
            @RequestParam Long chargerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        List<SessionResponse> sessions = sessionService.findByCharger(chargerId, startDate, endDate)
                .stream().map(SessionResponse::from).toList();
        return ApiResponse.success(sessions);
    }
}
