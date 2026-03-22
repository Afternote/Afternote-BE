package com.afternote.domain.afternote.service;

import com.afternote.domain.afternote.dto.AfternoteCreateRequest;
import com.afternote.domain.afternote.dto.AfternoteCreateResponse;
import com.afternote.domain.afternote.dto.AfternotePageResponse;
import com.afternote.domain.afternote.model.Afternote;
import com.afternote.domain.afternote.model.AfternoteCategoryType;
import com.afternote.domain.afternote.repository.AfternoteRepository;
import com.afternote.domain.image.service.S3Service;
import com.afternote.domain.user.model.AuthProvider;
import com.afternote.domain.user.model.User;
import com.afternote.domain.user.model.UserStatus;
import com.afternote.domain.user.repository.UserRepository;
import com.afternote.global.exception.CustomException;
import com.afternote.global.exception.ErrorCode;
import com.afternote.global.util.ChaChaEncryptionUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AfternoteServiceTest {

    @InjectMocks
    private AfternoteService afternoteService;

    @Mock
    private AfternoteRepository afternoteRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AfternoteRelationService relationService;

    @Mock
    private AfternoteValidator validator;

    @Mock
    private ChaChaEncryptionUtil chaChaEncryptionUtil;

    @Mock
    private S3Service s3Service;

    @Test
    @DisplayName("애프터노트 목록 조회 성공 - 카테고리 필터")
    void getAfternotes_WithCategory_Success() {
        User user = sampleUser(1L);
        Afternote afternote = Afternote.builder()
                .user(user)
                .categoryType(AfternoteCategoryType.SOCIAL)
                .title("title")
                .sortOrder(1)
                .build();
        ReflectionTestUtils.setField(afternote, "id", 10L);

        given(afternoteRepository.findByUserIdAndCategoryTypeOrderByCreatedAtDesc(eq(1L), eq(AfternoteCategoryType.SOCIAL), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(afternote)));

        AfternotePageResponse response = afternoteService.getAfternotes(1L, AfternoteCategoryType.SOCIAL, 0, 10);

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).getAfternoteId()).isEqualTo(10L);
        verify(afternoteRepository).findByUserIdAndCategoryTypeOrderByCreatedAtDesc(eq(1L), eq(AfternoteCategoryType.SOCIAL), any(Pageable.class));
        verify(afternoteRepository, never()).findByUserIdOrderByCreatedAtDesc(any(), any(Pageable.class));
    }

    @Test
    @DisplayName("애프터노트 생성 성공")
    void createAfternote_Success() {
        AfternoteCreateRequest request = org.mockito.Mockito.mock(AfternoteCreateRequest.class);
        given(request.getCategory()).willReturn(AfternoteCategoryType.SOCIAL);
        given(request.getTitle()).willReturn("social");
        given(request.getActions()).willReturn(List.of("action1"));
        given(request.getLeaveMessage()).willReturn("message");

        User user = sampleUser(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(afternoteRepository.findMaxSortOrderByUserId(1L)).willReturn(Optional.of(2));
        given(afternoteRepository.save(any(Afternote.class))).willAnswer(invocation -> {
            Afternote saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 100L);
            return saved;
        });

        AfternoteCreateResponse response = afternoteService.createAfternote(1L, request);

        assertThat(response.getAfternoteId()).isEqualTo(100L);

        ArgumentCaptor<Afternote> captor = ArgumentCaptor.forClass(Afternote.class);
        verify(afternoteRepository).save(captor.capture());
        assertThat(captor.getValue().getSortOrder()).isEqualTo(3);
        verify(validator).validateCreateRequest(request);
        verify(relationService).saveRelationsByCategory(any(Afternote.class), eq(request));
    }

    @Test
    @DisplayName("애프터노트 생성 실패 - 사용자 없음")
    void createAfternote_UserNotFound_Fail() {
        AfternoteCreateRequest request = org.mockito.Mockito.mock(AfternoteCreateRequest.class);
        given(userRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> afternoteService.createAfternote(1L, request))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND));
    }

    @Test
    @DisplayName("애프터노트 수정 실패 - 접근 권한 없음")
    void updateAfternote_AccessDenied_Fail() {
        User owner = sampleUser(2L);
        Afternote afternote = Afternote.builder()
                .user(owner)
                .categoryType(AfternoteCategoryType.SOCIAL)
                .title("title")
                .sortOrder(1)
                .build();
        given(afternoteRepository.findById(10L)).willReturn(Optional.of(afternote));

        AfternoteCreateRequest request = org.mockito.Mockito.mock(AfternoteCreateRequest.class);

        assertThatThrownBy(() -> afternoteService.updateAfternote(1L, 10L, request))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode()).isEqualTo(ErrorCode.AFTERNOTE_ACCESS_DENIED));
    }

    @Test
    @DisplayName("애프터노트 수정 성공 - relation update 호출 검증")
    void updateAfternote_Success_VerifyRelationUpdate() {
        User owner = sampleUser(1L);
        Afternote afternote = Afternote.builder()
                .user(owner)
                .categoryType(AfternoteCategoryType.SOCIAL)
                .title("before")
                .sortOrder(1)
                .leaveMessage("before-message")
            .actions(new ArrayList<>(List.of("a1")))
                .build();
        ReflectionTestUtils.setField(afternote, "id", 10L);

        AfternoteCreateRequest request = org.mockito.Mockito.mock(AfternoteCreateRequest.class);
        given(request.getTitle()).willReturn("after");
        given(request.getLeaveMessage()).willReturn("after-message");
        given(request.getActions()).willReturn(List.of("a2", "a3"));

        given(afternoteRepository.findById(10L)).willReturn(Optional.of(afternote));

        AfternoteCreateResponse response = afternoteService.updateAfternote(1L, 10L, request);

        assertThat(response.getAfternoteId()).isEqualTo(10L);
        assertThat(afternote.getTitle()).isEqualTo("after");
        assertThat(afternote.getLeaveMessage()).isEqualTo("after-message");
        assertThat(afternote.getActions()).containsExactly("a2", "a3");
        verify(validator).validateUpdateRequest(request, AfternoteCategoryType.SOCIAL);
        verify(relationService).updateRelationsByCategory(afternote, request, AfternoteCategoryType.SOCIAL);
    }

    @Test
    @DisplayName("애프터노트 삭제 실패 - 찾을 수 없음")
    void deleteAfternote_NotFound_Fail() {
        given(afternoteRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> afternoteService.deleteAfternote(1L, 999L))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode()).isEqualTo(ErrorCode.AFTERNOTE_NOT_FOUND));
    }

    @Test
    @DisplayName("애프터노트 삭제 실패 - 접근 권한 없음")
    void deleteAfternote_AccessDenied_Fail() {
        User owner = sampleUser(2L);
        Afternote afternote = Afternote.builder()
                .user(owner)
                .categoryType(AfternoteCategoryType.SOCIAL)
                .title("title")
                .sortOrder(1)
                .build();
        given(afternoteRepository.findById(10L)).willReturn(Optional.of(afternote));

        assertThatThrownBy(() -> afternoteService.deleteAfternote(1L, 10L))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode()).isEqualTo(ErrorCode.AFTERNOTE_ACCESS_DENIED));
    }

    private User sampleUser(Long id) {
        User user = User.builder()
                .email("test@test.com")
                .password("pw")
                .name("tester")
                .status(UserStatus.ACTIVE)
                .provider(AuthProvider.LOCAL)
                .build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
