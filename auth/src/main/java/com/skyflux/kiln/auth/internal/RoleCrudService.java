package com.skyflux.kiln.auth.internal;

import com.skyflux.kiln.auth.domain.Role;
import com.skyflux.kiln.auth.repo.RoleJooqRepository;
import com.skyflux.kiln.common.exception.AppCode;
import com.skyflux.kiln.common.exception.AppException;
import com.skyflux.kiln.common.util.Ids;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * CRUD operations for the dynamic role catalogue.
 *
 * <p>Seeded system roles (ADMIN / USER) can also be managed here, but deletion
 * of a role that has existing {@code user_roles} assignments will fail at the DB
 * layer due to the {@code ON DELETE RESTRICT} FK — which is the desired safety
 * behaviour.
 */
@Service
class RoleCrudService {

    private final RoleJooqRepository roleRepo;

    RoleCrudService(RoleJooqRepository roleRepo) {
        this.roleRepo = roleRepo;
    }

    @Transactional
    public Role createRole(UUID tenantId, String code, String name) {
        if (roleRepo.findByCode(code).isPresent()) {
            throw new AppException(AppCode.CONFLICT, "Role code already exists: " + code);
        }
        Role role = new Role(Ids.next(), code, name, tenantId);
        roleRepo.save(role);
        return role;
    }

    @Transactional
    public void deleteRole(UUID roleId) {
        roleRepo.delete(roleId);
    }

    public List<Role> listRoles() {
        return roleRepo.listAll();
    }
}
