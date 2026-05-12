package com.afternote.domain.user.dto;

import com.afternote.domain.user.model.AuthProvider;
import com.afternote.domain.user.model.User;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserConnectedAccountResponse {

    @Schema(description = "일반(로컬) 계정 연결 여부", example = "true")
    private boolean local;

    @Schema(description = "구글 계정 연결 여부", example = "false")
    private boolean google;

    @Schema(description = "네이버 계정 연결 여부", example = "false")
    private boolean naver;

    @Schema(description = "카카오 계정 연결 여부", example = "true")
    private boolean kakao;

    @Schema(description = "애플 계정 연결 여부", example = "false")
    private boolean apple;

    @Schema(description = "로컬(이메일)로 표시할 주소. 연결된 경우에만 채움", nullable = true)
    private String localEmail;

    @Schema(description = "구글 연동 시 UI에 표시할 이메일(현재 계정 이메일과 동일)", nullable = true)
    private String googleEmail;

    @Schema(description = "네이버 연동 시 UI에 표시할 이메일", nullable = true)
    private String naverEmail;

    @Schema(description = "카카오 연동 시 UI에 표시할 이메일", nullable = true)
    private String kakaoEmail;

    @Schema(description = "애플 연동 시 UI에 표시할 이메일", nullable = true)
    private String appleEmail;

    public static UserConnectedAccountResponse from(User user) {
        String email = user.getEmail();
        boolean local = user.hasProvider(AuthProvider.LOCAL);
        boolean google = user.hasProvider(AuthProvider.GOOGLE);
        boolean naver = user.hasProvider(AuthProvider.NAVER);
        boolean kakao = user.hasProvider(AuthProvider.KAKAO);
        boolean apple = user.hasProvider(AuthProvider.APPLE);

        return UserConnectedAccountResponse.builder()
                .local(local)
                .google(google)
                .naver(naver)
                .kakao(kakao)
                .apple(apple)
                .localEmail(local ? email : null)
                .googleEmail(google ? email : null)
                .naverEmail(naver ? email : null)
                .kakaoEmail(kakao ? email : null)
                .appleEmail(apple ? email : null)
                .build();
    }
}
