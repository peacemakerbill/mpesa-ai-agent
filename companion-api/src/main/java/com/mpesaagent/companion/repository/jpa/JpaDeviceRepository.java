package com.mpesaagent.companion.repository.jpa;

import com.mpesaagent.companion.model.Device;
import com.mpesaagent.companion.repository.DeviceRepository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** PostgreSQL and MySQL. */
public interface JpaDeviceRepository extends JpaRepository<Device, UUID>, DeviceRepository {
}
