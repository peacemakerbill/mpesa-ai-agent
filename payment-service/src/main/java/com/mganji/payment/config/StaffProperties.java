package com.mganji.payment.config;

import com.mganji.payment.model.StaffRole;
import com.mganji.payment.util.PhoneNumberUtil;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * The people who may do more than a normal customer. Anyone not listed is a customer.
 * Example environment variables: STAFF_MEMBERS_0_PHONE=0712345678, STAFF_MEMBERS_0_ROLE=ADMIN.
 */
@ConfigurationProperties(prefix = "staff")
public record StaffProperties(List<Member> members) {

    public record Member(String phone, String name, StaffRole role) {
    }

    public StaffProperties {
        members = members == null ? List.of() : members.stream()
                .map(m -> new Member(
                        PhoneNumberUtil.normalize(m.phone()),
                        m.name() == null ? "" : m.name(),
                        m.role() == null ? StaffRole.STAFF : m.role()))
                .toList();
    }
}
