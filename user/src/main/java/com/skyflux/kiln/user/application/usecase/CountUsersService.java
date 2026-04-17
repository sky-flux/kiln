package com.skyflux.kiln.user.application.usecase;

import com.skyflux.kiln.user.application.port.in.CountUsersUseCase;
import com.skyflux.kiln.user.application.port.out.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class CountUsersService implements CountUsersUseCase {

    private final UserRepository users;

    CountUsersService(UserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public long execute() {
        return users.countAll();
    }
}
