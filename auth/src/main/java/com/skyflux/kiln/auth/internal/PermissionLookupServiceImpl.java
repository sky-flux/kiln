package com.skyflux.kiln.auth.internal;

import com.skyflux.kiln.auth.domain.PermissionLookupService;
import com.skyflux.kiln.auth.repo.PermissionJooqRepository;
import com.skyflux.kiln.auth.repo.UserRoleJooqRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Thin composition of the two repos needed to answer Sa-Token's role and
 * permission queries. Intentionally package-private — it's a bean that gets
 * wired into {@code StpInterfaceImpl}; external modules see only
 * {@link PermissionLookupService}.
 */
@Service
class PermissionLookupServiceImpl implements PermissionLookupService {

    private final PermissionJooqRepository permissions;
    private final UserRoleJooqRepository userRoles;

    PermissionLookupServiceImpl(PermissionJooqRepository permissions,
                                UserRoleJooqRepository userRoles) {
        this.permissions = permissions;
        this.userRoles = userRoles;
    }

    @Override
    public List<String> permissionsFor(UUID userId) {
        return permissions.findCodesByUserId(userId);
    }

    @Override
    public List<String> rolesFor(UUID userId) {
        return userRoles.findRoleCodesByUserId(userId);
    }
}
