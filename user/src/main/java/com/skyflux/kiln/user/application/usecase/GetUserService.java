package com.skyflux.kiln.user.application.usecase;

import com.skyflux.kiln.common.exception.AppCode;
import com.skyflux.kiln.common.exception.AppException;
import com.skyflux.kiln.user.application.port.in.GetUserUseCase;
import com.skyflux.kiln.user.application.port.out.UserRepository;
import com.skyflux.kiln.user.domain.model.User;
import com.skyflux.kiln.user.domain.model.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class GetUserService implements GetUserUseCase {

    private final UserRepository repo;

    GetUserService(UserRepository repo) {
        this.repo = repo;
    }

    @Override
    public User execute(UserId id) {
        java.util.Objects.requireNonNull(id, "id");
        return repo.findById(id)
                .orElseThrow(() -> new AppException(AppCode.NOT_FOUND));
    }
}
