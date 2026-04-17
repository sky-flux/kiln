package com.skyflux.kiln.user.adapter.in.web;

import cn.dev33.satoken.stp.StpUtil;
import com.skyflux.kiln.user.application.port.in.AuthenticateUserUseCase;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inbound adapter exposing the auth flow over HTTP.
 *
 * <ul>
 *   <li>{@code POST /api/v1/auth/login} — verify credentials, start a Sa-Token
 *       session, return the opaque token value to the client.</li>
 *   <li>{@code POST /api/v1/auth/logout} — invalidate the current Sa-Token session.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthenticateUserUseCase authenticate;

    public AuthController(AuthenticateUserUseCase authenticate) {
        this.authenticate = authenticate;
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest req) {
        String token = authenticate.execute(
                new AuthenticateUserUseCase.Command(req.email(), req.password()));
        return new TokenResponse(token);
    }

    @PostMapping("/logout")
    public void logout() {
        StpUtil.logout();
    }
}
