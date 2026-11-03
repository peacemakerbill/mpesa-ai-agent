package com.darajaagentx.api.repository.jpa;

import com.darajaagentx.api.model.Device;
import com.darajaagentx.api.repository.DeviceRepository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** PostgreSQL and MySQL. */
public interface JpaDeviceRepository extends JpaRepository<Device, UUID>, DeviceRepository {
}
