package com.afternote.domain.notification.policy;

import com.afternote.global.exception.GlobalExceptionHandler;
import com.afternote.global.resolver.UserId;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.method.support.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class NotificationInboxControllerTest {
    private final NotificationDispatchRepository repository=mock(NotificationDispatchRepository.class);
    private final MockMvc mvc=MockMvcBuilders.standaloneSetup(new NotificationInboxController(repository, new ObjectMapper()))
            .setControllerAdvice(new GlobalExceptionHandler()).setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                public boolean supportsParameter(MethodParameter p) { return p.hasParameterAnnotation(UserId.class); }
                public Object resolveArgument(MethodParameter p, ModelAndViewContainer m, NativeWebRequest r, WebDataBinderFactory f) { return 7L; }
            }).build();
    @Test void invalidPageReturns400ThroughGlobalAdvice() throws Exception {
        mvc.perform(get("/api/v1/users/notifications").param("page", "-1")).andExpect(status().isBadRequest());
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"-1", "42949673", "677841162", "2147483647", "2147483648", "abc"})
    void invalidOrOverflowingPageNeverQueriesRepository(String page) throws Exception {
        mvc.perform(get("/api/v1/users/notifications").param("page", page))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1400));
        verifyNoInteractions(repository);
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {0, 42949672})
    void validPageIncludingLargestSupportedOffsetIsAccepted(int page) throws Exception {
        when(repository.findByUserIdAndVisibleAtIsNotNullAndStatusNotOrderByIdDesc(
                eq(7L), eq(NotificationDispatch.Status.CANCELLED), any())).thenReturn(List.of());
        mvc.perform(get("/api/v1/users/notifications").param("page", Integer.toString(page)))
                .andExpect(status().isOk());
        verify(repository).findByUserIdAndVisibleAtIsNotNullAndStatusNotOrderByIdDesc(
                7L, NotificationDispatch.Status.CANCELLED, org.springframework.data.domain.PageRequest.of(page, 50));
    }
    @Test void otherUsersOrMissingNotificationReturns404() throws Exception {
        when(repository.findByIdAndUserId(9L, 7L)).thenReturn(Optional.empty());
        mvc.perform(patch("/api/v1/users/notifications/9/read")).andExpect(status().isNotFound());
        verify(repository).findByIdAndUserId(9L, 7L);
    }
    @Test void listReturnsStringNavigationMapAndFullUnreadCount() throws Exception {
        var item=new NotificationDispatch(7L,"example",NotificationCategory.E,NotificationEvent.SCHEDULED,
                "제목","본문","{\"destination\":\"TIME_LETTER_DETAIL\",\"timeLetterId\":\"123\"}",LocalDateTime.now());
        ReflectionTestUtils.setField(item,"id",9L);
        item.inAppOnly(LocalDateTime.now());
        when(repository.countByUserIdAndVisibleAtIsNotNullAndReadAtIsNullAndStatusNot(7L,NotificationDispatch.Status.CANCELLED)).thenReturn(80L);
        when(repository.findByUserIdAndVisibleAtIsNotNullAndStatusNotOrderByIdDesc(
                eq(7L),eq(NotificationDispatch.Status.CANCELLED),any())).thenReturn(List.of(item));
        mvc.perform(get("/api/v1/users/notifications"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.unreadCount").value(80))
                .andExpect(jsonPath("$.data.items[0].id").value(9))
                .andExpect(jsonPath("$.data.items[0].data.destination").value("TIME_LETTER_DETAIL"))
                .andExpect(jsonPath("$.data.items[0].data.timeLetterId").value("123"));
    }
    @Test void repeatedReadsKeepFirstReadTimeAndCancelledItemsAreNotReadable() throws Exception {
        var item=new NotificationDispatch(7L,"example",NotificationCategory.E,NotificationEvent.SCHEDULED,"title","body","{}",LocalDateTime.now());
        item.inAppOnly(LocalDateTime.now());
        when(repository.findByIdAndUserId(9L,7L)).thenReturn(Optional.of(item));
        mvc.perform(patch("/api/v1/users/notifications/9/read")).andExpect(status().isOk());
        LocalDateTime first=item.getReadAt();
        mvc.perform(patch("/api/v1/users/notifications/9/read")).andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(item.getReadAt()).isEqualTo(first);
        item.finish(NotificationDispatch.Status.CANCELLED,"SOURCE_CHANGED");
        mvc.perform(patch("/api/v1/users/notifications/9/read")).andExpect(status().isNotFound());
    }
}
