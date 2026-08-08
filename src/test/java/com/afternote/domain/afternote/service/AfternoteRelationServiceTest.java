package com.afternote.domain.afternote.service;

import com.afternote.domain.afternote.dto.AfternoteCreateRequest;
import com.afternote.domain.afternote.model.Afternote;
import com.afternote.domain.afternote.model.AfternoteCategoryType;
import com.afternote.domain.afternote.service.relation.AfternoteCategoryRelationStrategy;
import com.afternote.domain.afternote.service.relation.AfternoteRelationStrategyFactory;
import com.afternote.domain.receiver.model.Receiver;
import com.afternote.domain.receiver.repository.ReceivedRepository;
import com.afternote.domain.user.model.AuthProvider;
import com.afternote.domain.user.model.User;
import com.afternote.domain.user.model.UserStatus;
import com.afternote.global.exception.CustomException;
import com.afternote.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AfternoteRelationServiceTest {

    @InjectMocks
    private AfternoteRelationService relationService;

    @Mock
    private ReceivedRepository receiverRepository;
    @Mock
    private AfternoteRelationStrategyFactory relationStrategyFactory;

    @Test
    @DisplayName("타 계정 receiverId 연결 시 NOT_ENOUGH_PERMISSION")
    void saveRelations_OtherUserReceiver_Forbidden() {
        User owner = sampleUser(1L);
        Afternote afternote = Afternote.builder()
                .user(owner)
                .categoryType(AfternoteCategoryType.GALLERY)
                .title("t")
                .sortOrder(1)
                .build();
        ReflectionTestUtils.setField(afternote, "receivers", new ArrayList<>());

        Receiver foreign = Receiver.builder()
                .name("other")
                .relation("친구")
                .userId(99L)
                .build();
        ReflectionTestUtils.setField(foreign, "id", 7L);

        AfternoteCreateRequest request = mock(AfternoteCreateRequest.class);
        AfternoteCreateRequest.ReceiverRequest rr = mock(AfternoteCreateRequest.ReceiverRequest.class);
        given(rr.getReceiverId()).willReturn(7L);
        given(request.getReceivers()).willReturn(List.of(rr));
        given(receiverRepository.findByIdIn(List.of(7L))).willReturn(List.of(foreign));

        assertThatThrownBy(() -> relationService.saveRelationsByCategory(afternote, request))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.NOT_ENOUGH_PERMISSION));
        verify(relationStrategyFactory, never()).get(any());
    }

    @Test
    @DisplayName("본인 소유 receiverId 는 연결 성공")
    void saveRelations_OwnedReceiver_Success() {
        User owner = sampleUser(1L);
        Afternote afternote = Afternote.builder()
                .user(owner)
                .categoryType(AfternoteCategoryType.GALLERY)
                .title("t")
                .sortOrder(1)
                .build();
        ReflectionTestUtils.setField(afternote, "receivers", new ArrayList<>());

        Receiver owned = Receiver.builder()
                .name("mine")
                .relation("친구")
                .userId(1L)
                .build();
        ReflectionTestUtils.setField(owned, "id", 3L);

        AfternoteCreateRequest request = mock(AfternoteCreateRequest.class);
        AfternoteCreateRequest.ReceiverRequest rr = mock(AfternoteCreateRequest.ReceiverRequest.class);
        given(rr.getReceiverId()).willReturn(3L);
        given(request.getReceivers()).willReturn(List.of(rr));
        given(request.getCategory()).willReturn(AfternoteCategoryType.GALLERY);
        given(receiverRepository.findByIdIn(List.of(3L))).willReturn(List.of(owned));

        AfternoteCategoryRelationStrategy strategy = mock(AfternoteCategoryRelationStrategy.class);
        given(relationStrategyFactory.get(AfternoteCategoryType.GALLERY)).willReturn(strategy);

        relationService.saveRelationsByCategory(afternote, request);

        assertThat(afternote.getReceivers()).hasSize(1);
        verify(strategy).save(afternote, request);
    }

    private static User sampleUser(Long id) {
        User user = User.builder()
                .email("u@test.com")
                .password("pw")
                .name("tester")
                .status(UserStatus.ACTIVE)
                .provider(AuthProvider.LOCAL)
                .build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
