package com.mpesaagent.companion.repository.mongo;

import com.mpesaagent.companion.model.Device;
import com.mpesaagent.companion.repository.DeviceRepository;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.UUID;

/** MongoDB. */
public interface MongoDeviceRepository extends MongoRepository<Device, UUID>, DeviceRepository {
}
