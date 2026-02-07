package com.evcharging.api.api.charger;

import com.evcharging.api.common.ApiResponse;
import com.evcharging.api.domain.charger.Charger;
import com.evcharging.api.domain.charger.ChargerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class ChargerController {

    private final ChargerService chargerService;

    public ChargerController(ChargerService chargerService) {
        this.chargerService = chargerService;
    }

    @GetMapping("/stations/{stationId}/chargers")
    public ApiResponse<List<ChargerResponse>> findByStation(@PathVariable Long stationId) {
        List<ChargerResponse> chargers = chargerService.findByStation(stationId)
                .stream().map(ChargerResponse::from).toList();
        return ApiResponse.success(chargers);
    }

    @PostMapping("/stations/{stationId}/chargers")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ChargerResponse> create(@PathVariable Long stationId,
                                               @Valid @RequestBody CreateChargerRequest request) {
        Charger charger = chargerService.create(stationId, request.toEntity());
        return ApiResponse.success(ChargerResponse.from(charger));
    }

    @PatchMapping("/chargers/{id}/status")
    public ApiResponse<ChargerResponse> changeStatus(@PathVariable Long id,
                                                     @Valid @RequestBody ChargerStatusRequest request) {
        Charger charger = chargerService.changeStatus(id, request.status());
        return ApiResponse.success(ChargerResponse.from(charger));
    }
}
