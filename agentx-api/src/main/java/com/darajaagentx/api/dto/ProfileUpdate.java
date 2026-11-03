package com.darajaagentx.api.dto;

/**
 * Changes to a profile. A field left out stays as it is. An empty text clears an optional field (the first and last name
 * can not be cleared). The email is changed with its own code check, not here.
 *
 * @param clearLocation true removes the saved latitude and longitude
 */
public record ProfileUpdate(
        String firstName,
        String middleName,
        String lastName,
        String address,
        String city,
        String county,
        String country,
        Double latitude,
        Double longitude,
        Boolean clearLocation,
        Boolean notifySignIn,
        Boolean notifyPayments
) {
}
