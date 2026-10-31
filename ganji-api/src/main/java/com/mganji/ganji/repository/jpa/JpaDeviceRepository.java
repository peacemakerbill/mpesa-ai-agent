package com.mganji.ganji.repository.jpa;

import com.mganji.ganji.model.Device;
import com.mganji.ganji.repository.DeviceRepository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** PostgreSQL and MySQL. */
public interface JpaDeviceRepository extends JpaRepository<Device, UUID>, DeviceRepository {
}
