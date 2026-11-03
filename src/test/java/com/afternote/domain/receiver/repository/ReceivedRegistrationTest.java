package com.afternote.domain.receiver.repository;

import com.afternote.domain.receiver.controller.ReceivedController;
import com.afternote.domain.receiver.model.*;
import com.afternote.domain.receiver.service.ReceivedService;
import com.afternote.domain.timeletter.model.*;
import com.afternote.domain.timeletter.repository.TimeLetterRepository;
import com.afternote.domain.user.model.*;
import com.afternote.global.exception.GlobalExceptionHandler;
import com.afternote.global.resolver.UserIdArgumentResolver;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.sql.init.mode=never",
        "spring.datasource.url=jdbc:h2:mem:receiver-registration;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = ReceivedRegistrationTest.Config.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ReceivedRegistrationTest {
    @Configuration
    @EntityScan("com.afternote.domain")
    @EnableJpaRepositories(basePackageClasses = {TimeLetterReceiverRepository.class, TimeLetterRepository.class})
    @EnableJpaAuditing
    static class Config {
        @Bean
        ReceivedService receivedService(ReceiverRepository receivers, TimeLetterReceiverRepository links,
                                        TimeLetterRepository letters) {
            return new ReceivedService(receivers, links, null, null, null, null, letters,
                    null, null, null, null, null, null, null, null);
        }
    }
    @Autowired EntityManager em;
    @Autowired ReceivedService service;
    @Autowired TimeLetterReceiverRepository links;
    @Autowired PlatformTransactionManager tm;
    MockMvc mvc;
    Long userId, letterId, receiverId, secondId;
    TransactionTemplate tx;

    @BeforeEach void setup() {
        tx = new TransactionTemplate(tm);
        tx.executeWithoutResult(s -> {
            em.createNativeQuery("ALTER TABLE time_letter_receiver ADD CONSTRAINT IF NOT EXISTS "
                    + "uk_time_letter_receiver_pair UNIQUE (time_letter_id, receiver_id)").executeUpdate();
            User user = User.builder().email(UUID.randomUUID()+"@example.test").name("작성자").status(UserStatus.ACTIVE).build();
            em.persist(user); userId = user.getId();
            TimeLetter letter = TimeLetter.builder().user(user).title("편지").sendAt(LocalDateTime.now().plusDays(1)).build();
            em.persist(letter); letterId = letter.getId();
            Receiver receiver = Receiver.builder().userId(userId).name("수신자").relation("친구").build();
            em.persist(receiver); receiverId = receiver.getId();
            Receiver second = Receiver.builder().userId(userId).name("수신자2").relation("친구").build();
            em.persist(second); secondId = second.getId();
        });
        mvc = MockMvcBuilders.standaloneSetup(new ReceivedController(service))
                .setCustomArgumentResolvers(new UserIdArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    String register(Long user, String ids) throws Exception {
        return mvc.perform(post("/api/v1/received/time-letters").requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE, user)
                .contentType("application/json").content("{\"timeLetterID\":"+letterId+",\"receiverIds\":["+ids+"]}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    @Test void repeatedRequestsReuseIdsAndPreserveDeliveryAndReadState() throws Exception {
        String first = register(userId, receiverId.toString());
        tx.executeWithoutResult(s -> {
            TimeLetterReceiver link = links.findByTimeLetterId(letterId).get(0);
            link.delivered(LocalDateTime.of(2026, 1, 1, 0, 0)); link.markAsRead();
        });
        assertThat(register(userId, receiverId+","+receiverId)).isEqualTo(first);
        String mixed = register(userId, secondId+","+receiverId);
        com.fasterxml.jackson.databind.ObjectMapper json = new com.fasterxml.jackson.databind.ObjectMapper();
        assertThat(json.readTree(mixed).path("data").get(1)).isEqualTo(json.readTree(first).path("data").get(0));
        tx.executeWithoutResult(s -> {
            assertThat(links.findByTimeLetterId(letterId)).hasSize(2);
            TimeLetterReceiver link = links.findByTimeLetterId(letterId).stream()
                    .filter(l -> l.getReceiver().getId().equals(receiverId)).findFirst().orElseThrow();
            assertThat(link.getDeliveredAt()).isEqualTo(LocalDateTime.of(2026, 1, 1, 0, 0));
            assertThat(link.getReadAt()).isNotNull();
        });
    }

    @Test void foreignOwnerCannotReuseExistingLink() throws Exception {
        register(userId, receiverId.toString());
        mvc.perform(post("/api/v1/received/time-letters")
                .requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE, userId + 100000)
                .contentType("application/json").content("{\"timeLetterID\":"+letterId+",\"receiverIds\":["+receiverId+"]}"))
                .andExpect(status().isNotFound());
        assertThat(links.findByTimeLetterId(letterId)).hasSize(1);
    }

    @Test void concurrentHttpRequestsReturnSameLinkAndOnlyOneRow() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch ready = new CountDownLatch(4), start = new CountDownLatch(1);
        try {
            List<Future<String>> results = new ArrayList<>();
            for (int i=0; i<4; i++) results.add(pool.submit(() -> {
                ready.countDown(); if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("start timeout");
                return register(userId, receiverId.toString());
            }));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); start.countDown();
            Set<String> responses = new HashSet<>();
            for (Future<String> result : results) responses.add(result.get(20, TimeUnit.SECONDS));
            assertThat(responses).hasSize(1);
            assertThat(links.findByTimeLetterId(letterId)).hasSize(1);
        } finally { start.countDown(); pool.shutdownNow(); }
    }
}
