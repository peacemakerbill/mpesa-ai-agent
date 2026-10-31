package com.mganji.ganji.repository.mongo;

import com.mganji.ganji.model.Device;
import com.mganji.ganji.repository.DeviceRepository;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.UUID;

/** MongoDB. */
public interface MongoDeviceRepository extends MongoRepository<Device, UUID>, DeviceRepository {
}
