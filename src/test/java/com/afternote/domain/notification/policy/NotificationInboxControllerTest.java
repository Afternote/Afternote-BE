package com.afternote.domain.notification.policy;

import com.afternote.global.exception.GlobalExceptionHandler;
import com.afternote.global.resolver.UserId;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    @Test void otherUsersOrMissingNotificationReturns404() throws Exception {
        when(repository.findByIdAndUserId(9L, 7L)).thenReturn(Optional.empty());
        mvc.perform(patch("/api/v1/users/notifications/9/read")).andExpect(status().isNotFound());
        verify(repository).findByIdAndUserId(9L, 7L);
    }
}
