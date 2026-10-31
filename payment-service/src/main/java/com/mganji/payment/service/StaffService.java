package com.mganji.payment.service;

import com.mganji.payment.config.StaffProperties;
import com.mganji.payment.model.StaffRole;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/** Looks up who is staff. A phone that is not listed is an ordinary customer (empty result). */
@Service
public class StaffService {

    private final StaffProperties props;

    public StaffService(StaffProperties props) {
        this.props = props;
    }

    public Optional<StaffProperties.Member> find(String normalizedPhone) {
        return props.members().stream().filter(m -> m.phone().equals(normalizedPhone)).findFirst();
    }

    public Optional<StaffRole> roleOf(String normalizedPhone) {
        return find(normalizedPhone).map(StaffProperties.Member::role);
    }

    /** Phones that can approve payouts, notified when a request is waiting. */
    public List<String> approverPhones() {
        return props.members().stream()
                .filter(m -> m.role().atLeast(StaffRole.APPROVER))
                .map(StaffProperties.Member::phone)
                .toList();
    }
}
