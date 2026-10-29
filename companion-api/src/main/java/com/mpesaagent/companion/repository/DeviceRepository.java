package com.mpesaagent.companion.repository;

import com.mpesaagent.companion.model.Device;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.Optional;
import java.util.UUID;

@NoRepositoryBean
public interface DeviceRepository extends CrudRepository<Device, UUID> {

    Optional<Device> findByPhoneAndDeviceId(String phone, String deviceId);
}
