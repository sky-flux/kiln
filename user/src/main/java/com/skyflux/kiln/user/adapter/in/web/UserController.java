package com.skyflux.kiln.user.adapter.in.web;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.skyflux.kiln.user.application.port.in.GetUserUseCase;
import com.skyflux.kiln.user.application.port.in.RegisterUserUseCase;
import com.skyflux.kiln.user.domain.model.User;
import com.skyflux.kiln.user.domain.model.UserId;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final GetUserUseCase getUser;
    private final RegisterUserUseCase registerUser;

    public UserController(GetUserUseCase getUser, RegisterUserUseCase registerUser) {
        this.getUser = getUser;
        this.registerUser = registerUser;
    }

    @GetMapping("/{id}")
    @SaCheckLogin
    public UserResponse get(@PathVariable String id) {
        User user = getUser.execute(UserId.of(id));
        return UserResponse.from(user);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserIdResponse register(@Valid @RequestBody RegisterUserRequest req) {
        UserId id = registerUser.execute(
                new RegisterUserUseCase.Command(req.name(), req.email(), req.password()));
        return new UserIdResponse(id.value().toString());
    }
}
