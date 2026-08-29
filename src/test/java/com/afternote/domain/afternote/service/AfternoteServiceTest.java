package com.afternote.domain.afternote.service;

import com.afternote.domain.afternote.dto.AfternoteCreateRequest;
import com.afternote.domain.afternote.dto.AfternoteCreateResponse;
import com.afternote.domain.afternote.dto.AfternotePageResponse;
import com.afternote.domain.afternote.dto.AfternoteUpdateRequest;
import com.afternote.domain.afternote.dto.AfternotedetailResponse;
import com.afternote.domain.afternote.dto.LeaveMessageBlock;
import com.afternote.domain.afternote.model.Afternote;
import com.afternote.domain.afternote.model.AfternoteCategoryType;
import com.afternote.domain.afternote.model.AfternotePlaylist;
import com.afternote.domain.afternote.model.AfternotePlaylistItem;
import com.afternote.domain.afternote.model.AfternoteReceiver;
import com.afternote.domain.afternote.model.AfternoteSecureContent;
import com.afternote.domain.afternote.repository.AfternoteRepository;
import com.afternote.domain.afternote.service.relation.EncryptedKey;
import com.afternote.domain.image.service.S3Service;
import com.afternote.domain.receiver.model.Receiver;
import com.afternote.domain.user.event.UserActivityTouchedEvent;
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
import org.springframework.context.ApplicationEventPublisher;
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

    @Mock
    private ApplicationEventPublisher eventPublisher;

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

        given(afternoteRepository.findByUserIdAndCategoryTypeAndIsDraftOrderByCreatedAtDesc(
                eq(1L), eq(AfternoteCategoryType.SOCIAL), eq(false), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(afternote)));

        AfternotePageResponse response = afternoteService.getAfternotes(1L, AfternoteCategoryType.SOCIAL, 0, 10, null);

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).getAfternoteId()).isEqualTo(10L);
        assertThat(response.getContent().get(0).getIsDraft()).isFalse();
        verify(afternoteRepository).findByUserIdAndCategoryTypeAndIsDraftOrderByCreatedAtDesc(
                eq(1L), eq(AfternoteCategoryType.SOCIAL), eq(false), any(Pageable.class));
        verify(afternoteRepository, never()).findByUserIdAndIsDraftOrderByCreatedAtDesc(any(), any(), any(Pageable.class));
    }

    @Test
    @DisplayName("애프터노트 생성 성공")
    void createAfternote_Success() {
        AfternoteCreateRequest request = org.mockito.Mockito.mock(AfternoteCreateRequest.class);
        given(request.getCategory()).willReturn(AfternoteCategoryType.SOCIAL);
        given(request.getTitle()).willReturn("social");
        given(request.getActions()).willReturn(List.of("action1"));
        given(request.getLeaveMessage()).willReturn(List.of(
                LeaveMessageBlock.builder().title("t1").body("message").build()
        ));

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
        assertThat(captor.getValue().getIsDraft()).isFalse();
        assertThat(captor.getValue().getLeaveMessage()).hasSize(1);
        assertThat(captor.getValue().getLeaveMessage().get(0).getBody()).isEqualTo("message");
        verify(validator).validateCreateRequest(request);
        verify(relationService).saveRelationsByCategory(any(Afternote.class), eq(request));
        verify(eventPublisher).publishEvent(any(UserActivityTouchedEvent.class));
    }

    @Test
    @DisplayName("BUSINESS 애프터노트 생성 성공 - actions 저장")
    void createAfternote_Business_Success() {
        AfternoteCreateRequest request = org.mockito.Mockito.mock(AfternoteCreateRequest.class);
        given(request.getCategory()).willReturn(AfternoteCategoryType.BUSINESS);
        given(request.getTitle()).willReturn("네이버 메일");
        given(request.getActions()).willReturn(List.of("만기 후 해지"));
        given(request.getLeaveMessage()).willReturn(List.of(
                LeaveMessageBlock.builder().title("남김").body("본문").build()
        ));

        User user = sampleUser(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(afternoteRepository.findMaxSortOrderByUserId(1L)).willReturn(Optional.of(1));
        given(afternoteRepository.save(any(Afternote.class))).willAnswer(invocation -> {
            Afternote saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 110L);
            return saved;
        });

        AfternoteCreateResponse response = afternoteService.createAfternote(1L, request);

        assertThat(response.getAfternoteId()).isEqualTo(110L);
        ArgumentCaptor<Afternote> captor = ArgumentCaptor.forClass(Afternote.class);
        verify(afternoteRepository).save(captor.capture());
        assertThat(captor.getValue().getCategoryType()).isEqualTo(AfternoteCategoryType.BUSINESS);
        assertThat(captor.getValue().getActions()).containsExactly("만기 후 해지");
        verify(relationService).saveRelationsByCategory(any(Afternote.class), eq(request));
    }

    @Test
    @DisplayName("애프터노트 임시저장 생성 성공")
    void createAfternote_Draft_Success() {
        AfternoteCreateRequest request = org.mockito.Mockito.mock(AfternoteCreateRequest.class);
        given(request.getCategory()).willReturn(AfternoteCategoryType.SOCIAL);
        given(request.getTitle()).willReturn("draft social");
        given(request.isDraftValue()).willReturn(true);

        User user = sampleUser(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(afternoteRepository.findMaxSortOrderByUserId(1L)).willReturn(Optional.empty());
        given(afternoteRepository.save(any(Afternote.class))).willAnswer(invocation -> {
            Afternote saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 102L);
            return saved;
        });

        AfternoteCreateResponse response = afternoteService.createAfternote(1L, request);

        assertThat(response.getAfternoteId()).isEqualTo(102L);
        ArgumentCaptor<Afternote> captor = ArgumentCaptor.forClass(Afternote.class);
        verify(afternoteRepository).save(captor.capture());
        assertThat(captor.getValue().getIsDraft()).isTrue();
    }

    @Test
    @DisplayName("PLAYLIST 생성 시 leaveMessage 저장")
    void createAfternote_Playlist_LeaveMessage_Saved() {
        AfternoteCreateRequest request = org.mockito.Mockito.mock(AfternoteCreateRequest.class);
        given(request.getCategory()).willReturn(AfternoteCategoryType.PLAYLIST);
        given(request.getTitle()).willReturn("playlist");
        given(request.getLeaveMessage()).willReturn(List.of(
                LeaveMessageBlock.builder().title("남긴말").body("본문").build()
        ));

        User user = sampleUser(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(afternoteRepository.findMaxSortOrderByUserId(1L)).willReturn(Optional.of(0));
        given(afternoteRepository.save(any(Afternote.class))).willAnswer(invocation -> {
            Afternote saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 101L);
            return saved;
        });

        afternoteService.createAfternote(1L, request);

        ArgumentCaptor<Afternote> captor = ArgumentCaptor.forClass(Afternote.class);
        verify(afternoteRepository).save(captor.capture());
        assertThat(captor.getValue().getLeaveMessage()).hasSize(1);
        assertThat(captor.getValue().getLeaveMessage().get(0).getTitle()).isEqualTo("남긴말");
        assertThat(captor.getValue().getActions()).isNullOrEmpty();
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

        AfternoteUpdateRequest request = org.mockito.Mockito.mock(AfternoteUpdateRequest.class);

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
                .leaveMessage(new ArrayList<>(List.of(
                        LeaveMessageBlock.builder().title("").body("before-message").build()
                )))
            .actions(new ArrayList<>(List.of("a1")))
                .build();
        ReflectionTestUtils.setField(afternote, "id", 10L);

        AfternoteUpdateRequest request = new AfternoteUpdateRequest(
                null,
                "after",
                List.of("a2", "a3"),
                List.of(LeaveMessageBlock.builder().title("t").body("after-message").build()),
                null,
                null,
                null,
                null
        );

        given(afternoteRepository.findById(10L)).willReturn(Optional.of(afternote));

        AfternoteCreateResponse response = afternoteService.updateAfternote(1L, 10L, request);

        assertThat(response.getAfternoteId()).isEqualTo(10L);
        assertThat(afternote.getTitle()).isEqualTo("after");
        assertThat(afternote.getLeaveMessage()).hasSize(1);
        assertThat(afternote.getLeaveMessage().get(0).getBody()).isEqualTo("after-message");
        assertThat(afternote.getActions()).containsExactly("a2", "a3");
        verify(validator).validateUpdateRequest(request, AfternoteCategoryType.SOCIAL);
        ArgumentCaptor<AfternoteCreateRequest> writeCaptor = ArgumentCaptor.forClass(AfternoteCreateRequest.class);
        verify(validator).validatePublishRequirements(writeCaptor.capture(), eq(afternote));
        assertThat(writeCaptor.getValue().getCategory()).isEqualTo(AfternoteCategoryType.SOCIAL);
        assertThat(writeCaptor.getValue().getTitle()).isEqualTo("after");
        verify(relationService).updateRelationsByCategory(eq(afternote), writeCaptor.capture(), eq(AfternoteCategoryType.SOCIAL));
    }

    @Test
    @DisplayName("애프터노트 상세 조회 - receivers 에 name·relation 포함")
    void getDetailAfternote_ReceiversIncludeNameAndRelation() {
        User owner = sampleUser(1L);
        Afternote afternote = Afternote.builder()
                .user(owner)
                .categoryType(AfternoteCategoryType.GALLERY)
                .title("구글 포토")
                .sortOrder(1)
                .actions(new ArrayList<>(List.of("QA runtime check")))
                .build();
        ReflectionTestUtils.setField(afternote, "id", 1L);

        Receiver receiver = Receiver.builder()
                .name("김소희")
                .relation("딸")
                .phone("010")
                .email("a@a.com")
                .userId(1L)
                .build();
        ReflectionTestUtils.setField(receiver, "id", 1L);

        AfternoteReceiver link = AfternoteReceiver.builder()
                .afternote(afternote)
                .receiver(receiver)
                .build();
        afternote.getReceivers().add(link);

        given(afternoteRepository.findById(1L)).willReturn(Optional.of(afternote));

        AfternotedetailResponse response = afternoteService.getDetailAfternote(1L, 1L);

        assertThat(response.getReceivers()).hasSize(1);
        assertThat(response.getReceivers().get(0).getReceiverId()).isEqualTo(1L);
        assertThat(response.getReceivers().get(0).getName()).isEqualTo("김소희");
        assertThat(response.getReceivers().get(0).getRelation()).isEqualTo("딸");
    }

    @Test
    @DisplayName("애프터노트 상세 조회 - 모든 카테고리 임시저장 응답 생성 매핑")
    void getDetailAfternote_AllCategoriesHaveResponseFactory() {
        User owner = sampleUser(1L);

        for (AfternoteCategoryType category : AfternoteCategoryType.values()) {
            long afternoteId = 100L + category.ordinal();
            Afternote afternote = Afternote.builder()
                    .user(owner)
                    .categoryType(category)
                    .title(category.name())
                    .sortOrder(category.ordinal())
                    .isDraft(true)
                    .build();
            ReflectionTestUtils.setField(afternote, "id", afternoteId);

            given(afternoteRepository.findById(afternoteId)).willReturn(Optional.of(afternote));

            AfternotedetailResponse response = afternoteService.getDetailAfternote(1L, afternoteId);

            assertThat(response).isInstanceOf(AfternotedetailResponse.Draft.class);
            assertThat(response.getCategory()).isEqualTo(category);
            assertThat(response.getTitle()).isEqualTo(category.name());
            assertThat(response.getIsDraft()).isTrue();
        }
    }

    @Test
    @DisplayName("임시저장 PLAYLIST 상세는 playlist 없이 200")
    void getDetailAfternote_DraftPlaylist_WithoutPlaylist() {
        User owner = sampleUser(1L);
        Afternote afternote = Afternote.builder()
                .user(owner)
                .categoryType(AfternoteCategoryType.PLAYLIST)
                .title("추억")
                .sortOrder(1)
                .isDraft(true)
                .build();
        ReflectionTestUtils.setField(afternote, "id", 20L);
        given(afternoteRepository.findById(20L)).willReturn(Optional.of(afternote));

        AfternotedetailResponse response = afternoteService.getDetailAfternote(1L, 20L);

        assertThat(response).isInstanceOf(AfternotedetailResponse.Draft.class);
        assertThat(((AfternotedetailResponse.Draft) response).getPlaylist()).isNull();
    }

    @Test
    @DisplayName("발행 완료 PLAYLIST 상세는 playlist와 곡을 포함한다")
    void getDetailAfternote_PublishedPlaylist_IncludesSongs() {
        User owner = sampleUser(1L);
        Afternote afternote = Afternote.builder()
                .user(owner)
                .categoryType(AfternoteCategoryType.PLAYLIST)
                .title("추억")
                .sortOrder(1)
                .isDraft(false)
                .build();
        ReflectionTestUtils.setField(afternote, "id", 21L);
        attachPlaylist(afternote, "보고싶다", "김범수");
        given(afternoteRepository.findById(21L)).willReturn(Optional.of(afternote));
        given(s3Service.generateGetPresignedUrl("cover.jpg")).willReturn("https://cdn/cover.jpg");

        AfternotedetailResponse response = afternoteService.getDetailAfternote(1L, 21L);

        assertThat(response).isInstanceOf(AfternotedetailResponse.PublishedPlaylist.class);
        AfternotedetailResponse.PublishedPlaylist published = (AfternotedetailResponse.PublishedPlaylist) response;
        assertThat(published.getIsDraft()).isFalse();
        assertThat(published.getPlaylist()).isNotNull();
        assertThat(published.getPlaylist().getSongs()).hasSize(1);
        assertThat(published.getPlaylist().getSongs().get(0).getTitle()).isEqualTo("보고싶다");
        assertThat(published.getPlaylist().getSongs().get(0).getCoverUrl()).isEqualTo("https://cdn/cover.jpg");
    }

    @Test
    @DisplayName("발행 완료 PLAYLIST인데 playlist가 없으면 200으로 직렬화하지 않는다")
    void getDetailAfternote_PublishedPlaylist_MissingPlaylist_Fails() {
        User owner = sampleUser(1L);
        Afternote afternote = Afternote.builder()
                .user(owner)
                .categoryType(AfternoteCategoryType.PLAYLIST)
                .title("추억")
                .sortOrder(1)
                .isDraft(false)
                .build();
        ReflectionTestUtils.setField(afternote, "id", 22L);
        given(afternoteRepository.findById(22L)).willReturn(Optional.of(afternote));

        assertThatThrownBy(() -> afternoteService.getDetailAfternote(1L, 22L))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode()).isEqualTo(ErrorCode.PLAYLIST_REQUIRED));
    }

    @Test
    @DisplayName("발행 완료 PLAYLIST인데 곡이 없으면 200으로 직렬화하지 않는다")
    void getDetailAfternote_PublishedPlaylist_EmptySongs_Fails() {
        User owner = sampleUser(1L);
        Afternote afternote = Afternote.builder()
                .user(owner)
                .categoryType(AfternoteCategoryType.PLAYLIST)
                .title("추억")
                .sortOrder(1)
                .isDraft(false)
                .build();
        ReflectionTestUtils.setField(afternote, "id", 23L);
        AfternotePlaylist playlist = AfternotePlaylist.builder()
                .afternote(afternote)
                .title("추모 플레이리스트")
                .items(new ArrayList<>())
                .build();
        ReflectionTestUtils.setField(afternote, "playlist", playlist);
        given(afternoteRepository.findById(23L)).willReturn(Optional.of(afternote));

        assertThatThrownBy(() -> afternoteService.getDetailAfternote(1L, 23L))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode()).isEqualTo(ErrorCode.PLAYLIST_SONGS_REQUIRED));
    }

    @Test
    @DisplayName("발행 완료 SOCIAL인데 credentials가 없으면 200으로 직렬화하지 않는다")
    void getDetailAfternote_PublishedSocial_MissingCredentials_Fails() {
        User owner = sampleUser(1L);
        Afternote afternote = Afternote.builder()
                .user(owner)
                .categoryType(AfternoteCategoryType.SOCIAL)
                .title("인스타그램")
                .sortOrder(1)
                .isDraft(false)
                .build();
        ReflectionTestUtils.setField(afternote, "id", 24L);
        given(afternoteRepository.findById(24L)).willReturn(Optional.of(afternote));

        assertThatThrownBy(() -> afternoteService.getDetailAfternote(1L, 24L))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.SOCIAL_CREDENTIALS_REQUIRED));
    }

    @Test
    @DisplayName("발행 완료 SOCIAL 상세는 credentials를 포함한다")
    void getDetailAfternote_PublishedSocial_IncludesCredentials() {
        User owner = sampleUser(1L);
        Afternote afternote = Afternote.builder()
                .user(owner)
                .categoryType(AfternoteCategoryType.SOCIAL)
                .title("인스타그램")
                .sortOrder(1)
                .isDraft(false)
                .build();
        ReflectionTestUtils.setField(afternote, "id", 25L);
        afternote.getSecureContents().add(AfternoteSecureContent.builder()
                .afternote(afternote)
                .keyName(EncryptedKey.ACCOUNT_ID.value())
                .encryptedValue("enc-id")
                .build());
        afternote.getSecureContents().add(AfternoteSecureContent.builder()
                .afternote(afternote)
                .keyName(EncryptedKey.ACCOUNT_PASSWORD.value())
                .encryptedValue("enc-pw")
                .build());
        given(afternoteRepository.findById(25L)).willReturn(Optional.of(afternote));
        given(chaChaEncryptionUtil.decrypt("enc-id")).willReturn("my_insta_id");
        given(chaChaEncryptionUtil.decrypt("enc-pw")).willReturn("password123");

        AfternotedetailResponse response = afternoteService.getDetailAfternote(1L, 25L);

        assertThat(response).isInstanceOf(AfternotedetailResponse.Published.class);
        assertThat(response.getCredentials().getId()).isEqualTo("my_insta_id");
        assertThat(response.getCredentials().getPassword()).isEqualTo("password123");
        assertThat(((AfternotedetailResponse.Published) response).getPlaylist()).isNull();
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

    private void attachPlaylist(Afternote afternote, String songTitle, String artist) {
        AfternotePlaylist playlist = AfternotePlaylist.builder()
                .afternote(afternote)
                .title("추모 플레이리스트")
                .atmosphere("차분")
                .items(new ArrayList<>())
                .build();
        playlist.getItems().add(AfternotePlaylistItem.builder()
                .playlist(playlist)
                .songTitle(songTitle)
                .artist(artist)
                .coverUrl("cover.jpg")
                .sortOrder(1)
                .build());
        ReflectionTestUtils.setField(afternote, "playlist", playlist);
    }
}
