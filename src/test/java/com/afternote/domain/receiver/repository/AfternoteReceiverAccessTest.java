package com.afternote.domain.receiver.repository;

import com.afternote.domain.afternote.model.Afternote;
import com.afternote.domain.afternote.model.AfternoteCategoryType;
import com.afternote.domain.afternote.model.AfternoteReceiver;
import com.afternote.domain.receiver.model.Receiver;
import com.afternote.domain.user.model.User;
import com.afternote.domain.user.model.UserStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.sql.init.mode=never",
        "spring.datasource.url=jdbc:h2:mem:receiver-access;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = AfternoteReceiverAccessTest.Config.class)
class AfternoteReceiverAccessTest {
    @Configuration
    @EntityScan("com.afternote.domain")
    @EnableJpaRepositories(basePackageClasses = AfternoteReceiverRepository.class)
    @EnableJpaAuditing
    static class Config { }

    @Autowired EntityManager em;
    @Autowired AfternoteReceiverRepository repository;

    @Test
    void detailQueryExcludesDraftsAndOtherReceivers() {
        User owner = User.builder().email("owner@example.test").name("작성자")
                .status(UserStatus.ACTIVE).build();
        em.persist(owner);
        Receiver receiver = Receiver.builder().userId(owner.getId()).name("수신자").relation("친구").build();
        em.persist(receiver);
        Receiver other = Receiver.builder().userId(owner.getId()).name("다른 수신자").relation("친구").build();
        em.persist(other);
        Afternote published = note(owner, false);
        Afternote draft = note(owner, true);
        em.persist(AfternoteReceiver.builder().afternote(published).receiver(receiver).build());
        em.persist(AfternoteReceiver.builder().afternote(draft).receiver(receiver).build());
        em.flush();
        em.clear();

        assertThat(repository.findByAfternoteIdAndReceiverIdWithAfternote(published.getId(), receiver.getId())).isPresent();
        assertThat(repository.findByAfternoteIdAndReceiverIdWithAfternote(draft.getId(), receiver.getId())).isEmpty();
        assertThat(repository.findByAfternoteIdAndReceiverIdWithAfternote(published.getId(), other.getId())).isEmpty();
        assertThat(repository.findByReceiverIdWithAfternote(receiver.getId())).hasSize(1);
    }

    private Afternote note(User owner, boolean draft) {
        Afternote note = Afternote.builder().user(owner).categoryType(AfternoteCategoryType.SOCIAL)
                .title("계정").sortOrder(0).isDraft(draft).build();
        em.persist(note);
        return note;
    }
}
