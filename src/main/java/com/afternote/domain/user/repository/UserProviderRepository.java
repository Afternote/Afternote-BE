package com.afternote.domain.user.repository;

import com.afternote.domain.user.model.AuthProvider;
import com.afternote.domain.user.model.UserProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserProviderRepository extends JpaRepository<UserProvider, Long> {

    Optional<UserProvider> findByProviderAndProviderId(AuthProvider provider, String providerId);
}
 