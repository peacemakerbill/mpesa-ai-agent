package com.darajaagentx.api.repository.mongo;

import com.darajaagentx.api.model.UserProfile;
import com.darajaagentx.api.repository.UserProfileRepository;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.UUID;

/** MongoDB. */
public interface MongoUserProfileRepository extends MongoRepository<UserProfile, UUID>, UserProfileRepository {
}
