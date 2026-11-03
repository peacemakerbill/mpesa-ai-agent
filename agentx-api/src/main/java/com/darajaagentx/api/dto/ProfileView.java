package com.darajaagentx.api.dto;

import com.darajaagentx.api.model.UserProfile;
import com.darajaagentx.api.util.PersonNames;

import java.time.Instant;

/**
 * A customer's profile as the app shows it. The phone number is the account and can not be changed here.
 *
 * @param pictureVersion changes whenever the picture changes, so the app knows when to fetch it again
 * @param complete       true when the first and last name are filled in
 * @param emailDelivers  false when this server only writes emails to its log, so the app can say that emails are not set up
 */
public record ProfileView(
        String phone,
        String status,
        String firstName,
        String middleName,
        String lastName,
        String fullName,
        String initials,
        String email,
        boolean emailVerified,
        String pendingEmail,
        String address,
        String city,
        String county,
        String country,
        Double latitude,
        Double longitude,
        boolean hasPicture,
        long pictureVersion,
        boolean notifySignIn,
        boolean notifyPayments,
        boolean complete,
        boolean emailDelivers,
        Instant createdAt
) {

    public static ProfileView of(UserProfile p, boolean emailDelivers) {
        boolean complete = notBlank(p.getFirstName()) && notBlank(p.getLastName());
        return new ProfileView(p.getPhone(), p.getStatus().name(), p.getFirstName(), p.getMiddleName(), p.getLastName(),
                PersonNames.full(p.getFirstName(), p.getMiddleName(), p.getLastName()), PersonNames.initials(p.getFirstName(), p.getLastName()),
                p.getEmail(), p.isEmailVerified(), p.getPendingEmail(), p.getAddress(), p.getCity(), p.getCounty(), p.getCountry(),
                p.getLatitude(), p.getLongitude(), p.hasPicture(), p.getPictureUpdatedAt() == null ? 0 : p.getPictureUpdatedAt().toEpochMilli(),
                p.isNotifySignIn(), p.isNotifyPayments(), complete, emailDelivers, p.getCreatedAt());
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
