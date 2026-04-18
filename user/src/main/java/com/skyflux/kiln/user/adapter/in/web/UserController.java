package com.skyflux.kiln.user.adapter.in.web;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.skyflux.kiln.common.result.PageQuery;
import com.skyflux.kiln.common.result.PageResult;
import com.skyflux.kiln.common.result.R;
import com.skyflux.kiln.user.application.port.in.DeleteUserUseCase;
import com.skyflux.kiln.user.application.port.in.GetUserUseCase;
import com.skyflux.kiln.user.application.port.in.ListUsersUseCase;
import com.skyflux.kiln.user.application.port.in.RegisterUserUseCase;
import com.skyflux.kiln.user.application.port.in.UpdateUserUseCase;
import com.skyflux.kiln.user.domain.model.User;
import com.skyflux.kiln.user.domain.model.UserId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final GetUserUseCase getUser;
    private final RegisterUserUseCase registerUser;
    private final ListUsersUseCase listUsersUseCase;
    private final UpdateUserUseCase updateUserUseCase;
    private final DeleteUserUseCase deleteUserUseCase;

    public UserController(GetUserUseCase getUser,
                          RegisterUserUseCase registerUser,
                          ListUsersUseCase listUsersUseCase,
                          UpdateUserUseCase updateUserUseCase,
                          DeleteUserUseCase deleteUserUseCase) {
        this.getUser = getUser;
        this.registerUser = registerUser;
        this.listUsersUseCase = listUsersUseCase;
        this.updateUserUseCase = updateUserUseCase;
        this.deleteUserUseCase = deleteUserUseCase;
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

    @GetMapping
    @SaCheckLogin
    public R<PageResult<UserResponse>> list(@Valid PageQuery query) {
        return R.ok(listUsersUseCase.execute(query).map(UserResponse::from));
    }

    @PutMapping("/{id}")
    @SaCheckLogin
    public R<UserResponse> update(@PathVariable UUID id,
                                  @Valid @RequestBody UpdateUserRequest req) {
        User updated = updateUserUseCase.execute(
                new UpdateUserUseCase.Command(new UserId(id), req.name()));
        return R.ok(UserResponse.from(updated));
    }

    @DeleteMapping("/{id}")
    @SaCheckRole("ADMIN")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        deleteUserUseCase.execute(new UserId(id));
    }

    public record UpdateUserRequest(@NotBlank @Size(max = 100) String name) {}
}
