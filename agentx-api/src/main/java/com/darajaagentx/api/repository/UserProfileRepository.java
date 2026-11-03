package com.darajaagentx.api.repository;

import com.darajaagentx.api.model.UserProfile;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.Optional;
import java.util.UUID;

@NoRepositoryBean
public interface UserProfileRepository extends CrudRepository<UserProfile, UUID> {

    Optional<UserProfile> findByPhone(String phone);
}
