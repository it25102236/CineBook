package com.moviebooking.moviebookingsystem.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@Service
public class SmsService {

    private final String accountSid;
    private final String authToken;
    private final String fromNumber;
    private final String countryCode;
    private final RestClient restClient = RestClient.create();

    public SmsService(
            @Value("${app.sms.twilio.account-sid:}") String accountSid,
            @Value("${app.sms.twilio.auth-token:}") String authToken,
            @Value("${app.sms.twilio.from-number:}") String fromNumber,
            @Value("${app.sms.country-code:+94}") String countryCode) {
        this.accountSid = accountSid;
        this.authToken = authToken;
        this.fromNumber = fromNumber;
        this.countryCode = countryCode;
    }

    public void sendVerificationCode(String telephone, String code) {
        if (accountSid.isBlank() || authToken.isBlank() || fromNumber.isBlank()) {
            throw new IllegalStateException("Twilio SMS is not configured.");
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("To", toE164(telephone));
        form.add("From", fromNumber);
        form.add("Body", "Your MovieBook verification code is " + code + ".");

        restClient.post()
                .uri("https://api.twilio.com/2010-04-01/Accounts/"
                        + accountSid + "/Messages.json")
                .headers(headers -> headers.setBasicAuth(accountSid, authToken))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .toBodilessEntity();
    }

    private String toE164(String telephone) {
        String normalized = telephone == null ? "" : telephone.trim();
        if (!normalized.matches("\\d{10}")) {
            throw new IllegalArgumentException("Telephone must contain 10 digits.");
        }

        String nationalNumber = normalized.startsWith("0")
                ? normalized.substring(1)
                : normalized;
        return countryCode + nationalNumber;
    }
}