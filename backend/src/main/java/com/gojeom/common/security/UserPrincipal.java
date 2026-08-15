package com.gojeom.common.security;

import java.util.UUID;

/**
 * 인증된 사용자. {@code SecurityContext}의 principal로 들어간다.
 *
 * <p>컨트롤러에서 {@code @AuthenticationPrincipal UserPrincipal me}로 받는다.
 * 소유권 검증은 Service 진입부에서 이 {@code id}로 한다. (ARCHITECTURE.md L-5)
 */
public record UserPrincipal(UUID id, String email) {
}
