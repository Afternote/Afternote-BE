package com.afternote.domain.admin.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;

import static org.assertj.core.api.Assertions.assertThat;

class AdminPageControllerTest {

    private final AdminPageController controller = new AdminPageController();

    @Test
    @DisplayName("관리자 로그인 페이지 반환")
    void loginPage_Success() {
        assertThat(controller.loginPage()).isEqualTo("admin/login");
    }

    @Test
    @DisplayName("관리자 인증 목록 페이지 반환")
    void verificationsPage_Success() {
        assertThat(controller.verificationsPage()).isEqualTo("admin/verifications");
    }

    @Test
    @DisplayName("관리자 인증 상세 페이지 반환 및 모델 세팅")
    void verificationDetailPage_Success() {
        ConcurrentModel model = new ConcurrentModel();

        String view = controller.verificationDetailPage(9L, model);

        assertThat(view).isEqualTo("admin/verification-detail");
        assertThat(model.getAttribute("verificationId")).isEqualTo(9L);
    }
}
