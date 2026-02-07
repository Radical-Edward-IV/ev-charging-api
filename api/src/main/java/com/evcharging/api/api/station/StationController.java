package com.evcharging.api.api.station;

import com.evcharging.api.common.ApiResponse;
import com.evcharging.api.domain.station.ChargingStation;
import com.evcharging.api.domain.station.StationService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/stations")
public class StationController {

    private final StationService stationService;

    public StationController(StationService stationService) {
        this.stationService = stationService;
    }

    @GetMapping
    public ApiResponse<Page<StationResponse>> findAll(Pageable pageable) {
        Page<StationResponse> page = stationService.findAll(pageable).map(StationResponse::from);
        return ApiResponse.success(page);
    }

    @GetMapping("/nearby")
    public ApiResponse<List<StationResponse>> findNearby(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "5") double radius) {
        List<StationResponse> stations = stationService.findNearby(lat, lng, radius)
                .stream().map(StationResponse::from).toList();
        return ApiResponse.success(stations);
    }

    @GetMapping("/{id}")
    public ApiResponse<StationResponse> findById(@PathVariable Long id) {
        ChargingStation station = stationService.findById(id);
        return ApiResponse.success(StationResponse.from(station));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<StationResponse> create(@Valid @RequestBody StationRequest request) {
        ChargingStation station = stationService.create(request.toEntity());
        return ApiResponse.success(StationResponse.from(station));
    }

    @PutMapping("/{id}")
    public ApiResponse<StationResponse> update(@PathVariable Long id,
                                               @Valid @RequestBody StationRequest request) {
        ChargingStation station = stationService.update(id, request.name(), request.address(),
                request.latitude(), request.longitude(),
                request.operatorName(), request.contactNumber(), request.operatingHours());
        return ApiResponse.success(StationResponse.from(station));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        stationService.delete(id);
    }
}
