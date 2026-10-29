package com.mpesaagent.agent.service;

import com.mpesaagent.agent.client.PaymentClient;
import com.mpesaagent.agent.dto.StaffView;
import com.mpesaagent.agent.exception.PaymentApiException;
import com.mpesaagent.agent.model.Identity;
import com.mpesaagent.agent.model.Role;
import com.mpesaagent.agent.store.StateStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * Finds out who is chatting. The role comes from payment-service's staff list, keyed by the signed-in phone number
 * (which companion-api verified with a code), never from anything the person types. If the lookup fails the person
 * is treated as an ordinary customer, so a failure can only remove abilities, never add them.
 */
@Service
public class RoleService {

    private static final Logger log = LoggerFactory.getLogger(RoleService.class);
    private static final Duration CACHE = Duration.ofMinutes(5);

    private final PaymentClient payments;
    private final StateStore store;
    private final com.mpesaagent.agent.config.AgentProperties props;

    public RoleService(PaymentClient payments, StateStore store, com.mpesaagent.agent.config.AgentProperties props) {
        this.payments = payments;
        this.store = store;
        this.props = props;
    }

    public Identity identify(String phone) {
        if (!props.businessPayments()) {
            // Roles only matter for the business payment tools, which are off. Everyone is a customer.
            return new Identity(phone, Role.CUSTOMER, null);
        }
        Optional<String> cached = store.get("role:" + phone);
        if (cached.isPresent()) {
            String[] parts = cached.get().split("\\|", 2);
            return new Identity(phone, Role.parse(parts[0]), parts.length > 1 && !parts[1].isBlank() ? parts[1] : null);
        }
        try {
            StaffView view = payments.staff(phone);
            Role role = Role.parse(view.role());
            String name = view.name() == null || view.name().isBlank() ? null : view.name();
            store.set("role:" + phone, role.name() + "|" + (name == null ? "" : name), CACHE);
            return new Identity(phone, role, name);
        } catch (PaymentApiException e) {
            log.warn("Could not look up the role of a chat partner, treating as customer: {}", e.getMessage());
            return new Identity(phone, Role.CUSTOMER, null);
        }
    }
}
