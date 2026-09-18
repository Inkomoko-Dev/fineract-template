/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.fineract.useradministration.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.DataValidatorBuilder;
import org.apache.fineract.infrastructure.core.domain.EmailDetail;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.core.service.PlatformEmailService;
import org.apache.fineract.infrastructure.security.domain.BasicPasswordEncodablePlatformUser;
import org.apache.fineract.infrastructure.security.service.PlatformPasswordEncoder;
import org.apache.fineract.useradministration.api.AppUserApiConstant;
import org.apache.fineract.useradministration.domain.AppUser;
import org.apache.fineract.useradministration.domain.AppUserPasswordReset;
import org.apache.fineract.useradministration.domain.AppUserPasswordResetRepository;
import org.apache.fineract.useradministration.domain.AppUserPreviousPassword;
import org.apache.fineract.useradministration.domain.AppUserPreviousPasswordRepository;
import org.apache.fineract.useradministration.domain.AppUserRepository;
import org.apache.fineract.useradministration.domain.PasswordValidationPolicy;
import org.apache.fineract.useradministration.domain.PasswordValidationPolicyRepository;
import org.apache.fineract.useradministration.exception.PasswordPreviouslyUsedException;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetWritePlatformService {

    private static final int OTP_LENGTH = 6;
    private static final int OTP_TTL_MINUTES = 15;
    private static final int MAX_ATTEMPTS = 5;
    private static final int MAX_REQUESTS_PER_HOUR = 5;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final AppUserRepository appUserRepository;
    private final AppUserPasswordResetRepository passwordResetRepository;
    private final AppUserPreviousPasswordRepository previousPasswordRepository;
    private final PasswordValidationPolicyRepository passwordValidationPolicyRepository;
    private final PlatformPasswordEncoder platformPasswordEncoder;
    private final PlatformEmailService emailService;

    /**
     * Starts a self-service password reset from username only. If the account exists, is active,
     * and has an email on file, a one-time code is sent silently to that address. The HTTP response
     * is always the same shape so callers cannot enumerate accounts.
     */
    @Transactional
    public Map<String, Object> requestReset(final String usernameRaw) {
        final String username = StringUtils.trimToEmpty(usernameRaw);
        validateUsername(username);

        final Map<String, Object> response = genericAcceptedResponse();

        final AppUser user = this.appUserRepository.findAppUserByName(username);
        if (user == null || user.isDeleted() || user.isNotEnabled() || StringUtils.isBlank(user.getEmail())) {
            return response;
        }

        final LocalDateTime now = DateUtils.getLocalDateTimeOfSystem();
        final long recent = this.passwordResetRepository.countRecentForUser(user.getId(), now.minusHours(1));
        if (recent >= MAX_REQUESTS_PER_HOUR) {
            // Still return the generic success body — do not reveal rate-limit vs missing account.
            log.warn("Password reset rate-limited for user {}", user.getId());
            return response;
        }

        for (final AppUserPasswordReset active : this.passwordResetRepository.findActiveForUser(user.getId(), now)) {
            active.markUsed();
            this.passwordResetRepository.save(active);
        }

        final String otp = generateOtp();
        final AppUserPasswordReset request = new AppUserPasswordReset(user, hashToken(otp), maskEmail(user.getEmail()),
                now.plusMinutes(OTP_TTL_MINUTES));
        this.passwordResetRepository.saveAndFlush(request);

        try {
            sendOtpEmail(user, otp);
        } catch (final Exception e) {
            log.error("Failed to send password reset OTP to user {}", user.getId(), e);
            // Do not surface delivery failure to the client (enumeration / probing signal).
        }

        return response;
    }

    /**
     * Confirms reset with username + emailed OTP. Possession of the code proves control of the
     * registered inbox; no email is collected from the client.
     */
    @Transactional
    @Caching(evict = { @CacheEvict(value = "users", allEntries = true), @CacheEvict(value = "usersByUsername", allEntries = true) })
    public Map<String, Object> confirmReset(final String usernameRaw, final String tokenRaw, final String password,
            final String repeatPassword) {
        final String username = StringUtils.trimToEmpty(usernameRaw);
        final String token = StringUtils.trimToEmpty(tokenRaw);
        validateUsername(username);
        validatePasswordParams(token, password, repeatPassword);

        final AppUser user = this.appUserRepository.findAppUserByName(username);
        if (user == null || user.isDeleted() || user.isNotEnabled()) {
            throw new GeneralPlatformDomainRuleException("error.msg.password.reset.invalid",
                    "Invalid verification details. Check your username and code.");
        }

        final LocalDateTime now = DateUtils.getLocalDateTimeOfSystem();
        final List<AppUserPasswordReset> active = this.passwordResetRepository.findActiveForUser(user.getId(), now);
        if (active.isEmpty()) {
            throw new GeneralPlatformDomainRuleException("error.msg.password.reset.expired",
                    "The verification code has expired. Please request a new one.");
        }

        final AppUserPasswordReset request = active.get(0);
        if (request.getAttemptCount() >= MAX_ATTEMPTS) {
            request.markUsed();
            this.passwordResetRepository.save(request);
            throw new GeneralPlatformDomainRuleException("error.msg.password.reset.locked",
                    "Too many invalid verification attempts. Please request a new code.");
        }

        if (!constantTimeEquals(request.getTokenHash(), hashToken(token))) {
            request.incrementAttempt();
            this.passwordResetRepository.save(request);
            throw new GeneralPlatformDomainRuleException("error.msg.password.reset.token.invalid",
                    "Invalid verification code.");
        }

        final String encoded = this.platformPasswordEncoder
                .encode(new BasicPasswordEncodablePlatformUser(user.getId(), user.getUsername(), password));

        final PageRequest pageRequest = PageRequest.of(0, AppUserApiConstant.numberOfPreviousPasswords, Sort.Direction.DESC,
                "removalDate");
        final List<AppUserPreviousPassword> previous = this.previousPasswordRepository.findByUserId(user.getId(), pageRequest);
        for (final AppUserPreviousPassword prior : previous) {
            if (StringUtils.equals(prior.getPassword(), encoded)) {
                throw new PasswordPreviouslyUsedException();
            }
        }

        final AppUserPreviousPassword currentAsPreview = new AppUserPreviousPassword(user);
        user.updatePassword(encoded);
        this.appUserRepository.saveAndFlush(user);
        this.previousPasswordRepository.save(currentAsPreview);

        request.markUsed();
        this.passwordResetRepository.save(request);

        final Map<String, Object> response = new LinkedHashMap<>();
        response.put("resourceId", user.getId());
        response.put("username", user.getUsername());
        response.put("message", "Password updated successfully. You can now sign in.");
        return response;
    }

    private Map<String, Object> genericAcceptedResponse() {
        final Map<String, Object> response = new LinkedHashMap<>();
        response.put("accepted", true);
        response.put("message",
                "If an account exists for that username, a verification code has been sent to the email on file.");
        response.put("expiresInMinutes", OTP_TTL_MINUTES);
        return response;
    }

    private void validateUsername(final String username) {
        final List<ApiParameterError> errors = new ArrayList<>();
        final DataValidatorBuilder validator = new DataValidatorBuilder(errors).resource("passwordreset");
        validator.reset().parameter("username").value(username).notBlank().notExceedingLengthOf(100);
        throwValidationErrors(errors);
    }

    private void validatePasswordParams(final String token, final String password, final String repeatPassword) {
        final List<ApiParameterError> errors = new ArrayList<>();
        final DataValidatorBuilder validator = new DataValidatorBuilder(errors).resource("passwordreset");
        validator.reset().parameter("token").value(token).notBlank().notExceedingLengthOf(20);
        validator.reset().parameter("password").value(password).notBlank();
        validator.reset().parameter("repeatPassword").value(repeatPassword).notBlank();

        if (StringUtils.isNotBlank(password)) {
            final PasswordValidationPolicy policy = this.passwordValidationPolicyRepository.findActivePasswordValidationPolicy();
            if (policy != null) {
                validator.reset().parameter("password").value(password).matchesRegularExpression(policy.getRegex(),
                        policy.getDescription());
            }
            validator.reset().parameter("password").value(password).equalToParameter("repeatPassword", repeatPassword);
        }
        throwValidationErrors(errors);
    }

    private void throwValidationErrors(final List<ApiParameterError> errors) {
        if (!errors.isEmpty()) {
            throw new PlatformApiDataValidationException(errors);
        }
    }

    private String generateOtp() {
        final int bound = (int) Math.pow(10, OTP_LENGTH);
        final int value = SECURE_RANDOM.nextInt(bound / 10, bound);
        return String.valueOf(value);
    }

    private String hashToken(final String token) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            final byte[] hashed = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (final Exception e) {
            throw new IllegalStateException("Unable to hash password reset token", e);
        }
    }

    private boolean constantTimeEquals(final String left, final String right) {
        if (left == null || right == null) {
            return false;
        }
        final byte[] a = left.getBytes(StandardCharsets.UTF_8);
        final byte[] b = right.getBytes(StandardCharsets.UTF_8);
        if (a.length != b.length) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length; i++) {
            result |= a[i] ^ b[i];
        }
        return result == 0;
    }

    private String maskEmail(final String email) {
        final int at = email.indexOf('@');
        if (at <= 1) {
            return "***";
        }
        final String local = email.substring(0, at);
        final String domain = email.substring(at);
        final String visible = local.substring(0, Math.min(2, local.length()));
        return visible + "***" + domain;
    }

    private void sendOtpEmail(final AppUser user, final String otp) {
        final String subject = "Password reset verification code";
        final String body = "Dear " + HtmlUtils.htmlEscape(StringUtils.defaultIfBlank(user.getDisplayName(), user.getUsername()))
                + ",<br><br>"
                + "We received a request to reset the password for username <strong>"
                + HtmlUtils.htmlEscape(user.getUsername()) + "</strong>.<br><br>"
                + "Your verification code is: <strong style=\"font-size:18px;letter-spacing:2px\">" + HtmlUtils.htmlEscape(otp)
                + "</strong><br><br>"
                + "This code expires in " + OTP_TTL_MINUTES + " minutes. If you did not request a password reset, ignore this email.<br><br>"
                + "Inkomoko";
        this.emailService.sendDefinedEmail(new EmailDetail(subject, body, user.getEmail(), user.getDisplayName()));
    }
}
