package com.darajaagentx.api.repository.jpa;

import com.darajaagentx.api.model.UserProfile;
import com.darajaagentx.api.repository.UserProfileRepository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** PostgreSQL and MySQL. */
public interface JpaUserProfileRepository extends JpaRepository<UserProfile, UUID>, UserProfileRepository {
}
