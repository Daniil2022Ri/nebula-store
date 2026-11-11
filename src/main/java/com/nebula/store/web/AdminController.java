package com.nebula.store.web;

import com.nebula.store.domain.OrderStatus;
import com.nebula.store.service.AdminService;
import com.nebula.store.web.dto.AdminOrderPage;
import com.nebula.store.web.dto.AdminStats;
import com.nebula.store.web.dto.OrderDto;
import com.nebula.store.web.dto.StatusUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Back-office endpoints. Every request here must carry {@code X-Admin-Token};
 * the check itself lives in {@link com.nebula.store.config.AdminTokenFilter}.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/orders")
    public AdminOrderPage list(@RequestParam(required = false) OrderStatus status,
                               @RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "10") int size) {
        return adminService.list(status, page, size);
    }

    @GetMapping("/orders/{code}")
    public OrderDto get(@PathVariable String code) {
        return adminService.get(code);
    }

    @PatchMapping("/orders/{code}/status")
    public OrderDto updateStatus(@PathVariable String code,
                                 @Valid @RequestBody StatusUpdateRequest request) {
        return adminService.updateStatus(code, request.status());
    }

    @GetMapping("/stats")
    public AdminStats stats() {
        return adminService.stats();
    }
}
