package com.gojeom.user;

import com.gojeom.common.response.ApiResponse;
import com.gojeom.common.security.UserPrincipal;
import com.gojeom.user.dto.UserDtos.MeResponse;
import com.gojeom.user.dto.UserDtos.NicknameUpdateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ApiResponse<MeResponse> me(@AuthenticationPrincipal UserPrincipal me) {
        return ApiResponse.ok(userService.getMe(me.id()));
    }

    /** 닉네임 변경. 시안 04 "이름 설정"이 가입 직후에 오므로 필요하다. */
    @PatchMapping("/me")
    public ApiResponse<MeResponse> updateNickname(
            @AuthenticationPrincipal UserPrincipal me,
            @Valid @RequestBody NicknameUpdateRequest request) {
        return ApiResponse.ok(userService.updateNickname(me.id(), request));
    }

    /**
     * 계정 삭제. (PRD §10 · API.md §5 7번)
     *
     * <p>계정은 soft delete, <b>사진은 즉시 하드 삭제</b>다.
     */
    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMe(@AuthenticationPrincipal UserPrincipal me) {
        userService.deleteAccount(me.id());
    }
}
