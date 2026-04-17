package com.skyflux.kiln.user.adapter.in.web;

import com.skyflux.kiln.user.application.port.in.GetUserUseCase;
import com.skyflux.kiln.user.domain.model.User;
import com.skyflux.kiln.user.domain.model.UserId;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final GetUserUseCase getUser;

    public UserController(GetUserUseCase getUser) {
        this.getUser = getUser;
    }

    @GetMapping("/{id}")
    public UserResponse get(@PathVariable String id) {
        User user = getUser.execute(UserId.of(id));
        return UserResponse.from(user);
    }
}
