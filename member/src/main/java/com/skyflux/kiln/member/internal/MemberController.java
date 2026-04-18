package com.skyflux.kiln.member.internal;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.skyflux.kiln.common.result.PageQuery;
import com.skyflux.kiln.common.result.PageResult;
import com.skyflux.kiln.common.result.R;
import com.skyflux.kiln.member.domain.Member;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
class MemberController {

    private final MemberService service;

    MemberController(MemberService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/members/me")
    @SaCheckLogin
    R<MemberResponse> getMyProfile() {
        return R.ok(MemberResponse.from(service.getMyMember()));
    }

    @GetMapping("/api/v1/admin/members")
    @SaCheckRole("ADMIN")
    R<PageResult<MemberResponse>> list(@Valid PageQuery query) {
        return R.ok(service.list(query).map(MemberResponse::from));
    }

    @PostMapping("/api/v1/admin/members/{userId}/points")
    @SaCheckRole("ADMIN")
    R<MemberResponse> awardPoints(@PathVariable UUID userId,
                                   @Valid @RequestBody AwardPointsRequest req) {
        return R.ok(MemberResponse.from(service.awardPoints(userId, req.points())));
    }

    record AwardPointsRequest(@Min(1) int points) {}

    record MemberResponse(String id, String userId, String level, int points, String status) {
        static MemberResponse from(Member m) {
            return new MemberResponse(
                    m.id().value().toString(), m.userId().toString(),
                    m.level().name(), m.points(), m.status());
        }
    }
}
