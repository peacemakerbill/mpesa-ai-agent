package com.darajaagentx.api.repository.mongo;

import com.darajaagentx.api.model.Device;
import com.darajaagentx.api.repository.DeviceRepository;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.UUID;

/** MongoDB. */
public interface MongoDeviceRepository extends MongoRepository<Device, UUID>, DeviceRepository {
}
