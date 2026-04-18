package com.skyflux.kiln.user.application.usecase;

import com.skyflux.kiln.common.result.PageQuery;
import com.skyflux.kiln.common.result.PageResult;
import com.skyflux.kiln.user.application.port.in.ListUsersUseCase;
import com.skyflux.kiln.user.application.port.out.UserRepository;
import com.skyflux.kiln.user.domain.model.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * Lists active users (soft-deleted / INACTIVE excluded), paginated.
 *
 * <p>Tenant isolation is primarily enforced by DB-level RLS. The application
 * layer delegates directly to the repository because {@code listActive} is
 * bounded to the authenticated tenant's connection; there is no cross-tenant
 * join to defend against at this layer.
 */

/** Lists active users (soft-deleted / INACTIVE excluded), paginated. */
@Service
@Transactional(readOnly = true)
class ListUsersService implements ListUsersUseCase {

    private final UserRepository repo;

    ListUsersService(UserRepository repo) {
        this.repo = repo;
    }

    @Override
    public PageResult<User> execute(PageQuery query) {
        Objects.requireNonNull(query, "query");
        return repo.listActive(query);
    }
}
