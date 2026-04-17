package com.skyflux.kiln.user.adapter.in.web;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.skyflux.kiln.user.application.port.in.CountUsersUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-only endpoints. Protected by {@code @SaCheckRole("ADMIN")};
 * regular users hitting these get HTTP 403 via Sa-Token → GlobalExceptionHandler.
 *
 * <p>Phase 4.2 demo surface: exercises the real (Phase 4.2 auth module)
 * {@code StpInterfaceImpl} backend end-to-end. Phase 5+ will split admin-
 * facing endpoints into their own module as they multiply.
 *
 * <p>Per Gate 3 I3, the controller talks to an <em>inbound</em> use case
 * ({@link CountUsersUseCase}), never directly to the outbound
 * {@code UserRepository} — the indirection keeps the hexagonal layering
 * honest and leaves a seam for future authorization/auditing without
 * touching the web adapter.
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final CountUsersUseCase countUsers;

    public AdminController(CountUsersUseCase countUsers) {
        this.countUsers = countUsers;
    }

    @GetMapping("/users/count")
    @SaCheckRole("ADMIN")
    public UserCountResponse userCount() {
        return new UserCountResponse(countUsers.execute());
    }
}
