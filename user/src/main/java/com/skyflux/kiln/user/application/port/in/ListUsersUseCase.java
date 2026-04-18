package com.skyflux.kiln.user.application.port.in;

import com.skyflux.kiln.common.result.PageQuery;
import com.skyflux.kiln.common.result.PageResult;
import com.skyflux.kiln.user.domain.model.User;

/** Inbound port: list active users with pagination. */
public interface ListUsersUseCase {
    PageResult<User> execute(PageQuery query);
}
