/*
 * JBoss, Home of Professional Open Source
 * Copyright 2015, Red Hat, Inc. and/or its affiliates, and individual
 * contributors by the @authors tag. See the copyright.txt in the
 * distribution for a full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.as.quickstarts.kitchensink.test;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.jboss.as.quickstarts.kitchensink.model.Member;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Characterization tests for the Bean Validation constraints declared on {@link Member}, run
 * directly against Hibernate Validator with no CDI container or Arquillian deployment involved.
 * These pin the exact boundaries (min/max lengths, digit counts, pattern rules) so the same
 * constraints can be verified after the Spring Boot migration.
 */
class MemberValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        // The default message interpolator requires a Jakarta EL implementation, which is
        // normally supplied by the JBoss EAP container at runtime but isn't on the classpath
        // for a standalone unit test. ParameterMessageInterpolator avoids that dependency;
        // these tests only assert on the presence/absence of violations, not message text.
        validatorFactory = Validation.byDefaultProvider()
                .configure()
                .messageInterpolator(new ParameterMessageInterpolator())
                .buildValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidatorFactory() {
        validatorFactory.close();
    }

    private static Member memberWithName(String name) {
        Member member = new Member();
        member.setName(name);
        return member;
    }

    private static Member memberWithEmail(String email) {
        Member member = new Member();
        member.setEmail(email);
        return member;
    }

    private static Member memberWithPhoneNumber(String phoneNumber) {
        Member member = new Member();
        member.setPhoneNumber(phoneNumber);
        return member;
    }

    private <T> void assertHasViolation(String property, T bean) {
        Set<ConstraintViolation<T>> violations = validator.validateProperty(bean, property);
        assertTrue(!violations.isEmpty(), "expected a violation on '" + property + "' but found none");
    }

    private <T> void assertNoViolations(String property, T bean) {
        Set<ConstraintViolation<T>> violations = validator.validateProperty(bean, property);
        assertTrue(violations.isEmpty(), "expected no violations on '" + property + "' but found: " + violations);
    }

    // --- name ---

    @Test
    void name_null_isRejected() {
        assertHasViolation("name", memberWithName(null));
    }

    @Test
    void name_empty_isRejected() {
        assertHasViolation("name", memberWithName(""));
    }

    @Test
    void name_containingDigits_isRejected() {
        assertHasViolation("name", memberWithName("John3"));
    }

    @Test
    void name_atMaxLength_isAccepted() {
        // exactly 25 characters
        assertNoViolations("name", memberWithName("Abcdefghijklmnopqrstuvwxy"));
    }

    @Test
    void name_overMaxLength_isRejected() {
        // 26 characters
        assertHasViolation("name", memberWithName("Abcdefghijklmnopqrstuvwxyz"));
    }

    @Test
    void name_validAlphabeticName_isAccepted() {
        assertNoViolations("name", memberWithName("Jane Doe"));
    }

    // --- email ---

    @Test
    void email_null_isRejected() {
        assertHasViolation("email", memberWithEmail(null));
    }

    @Test
    void email_empty_isRejected() {
        assertHasViolation("email", memberWithEmail(""));
    }

    @Test
    void email_malformed_isRejected() {
        assertHasViolation("email", memberWithEmail("not-an-email"));
    }

    @Test
    void email_wellFormed_isAccepted() {
        assertNoViolations("email", memberWithEmail("jane@mailinator.com"));
    }

    // --- phoneNumber ---

    @Test
    void phoneNumber_null_isRejected() {
        assertHasViolation("phoneNumber", memberWithPhoneNumber(null));
    }

    @Test
    void phoneNumber_underMinLength_isRejected() {
        // 9 digits
        assertHasViolation("phoneNumber", memberWithPhoneNumber("212555121"));
    }

    @Test
    void phoneNumber_atMinLength_isAccepted() {
        // exactly 10 digits
        assertNoViolations("phoneNumber", memberWithPhoneNumber("2125551212"));
    }

    @Test
    void phoneNumber_atMaxLength_isAccepted() {
        // exactly 12 digits
        assertNoViolations("phoneNumber", memberWithPhoneNumber("212555121234"));
    }

    @Test
    void phoneNumber_overMaxLength_isRejected() {
        // 13 digits
        assertHasViolation("phoneNumber", memberWithPhoneNumber("2125551212345"));
    }

    @Test
    void phoneNumber_containingNonDigits_isRejected() {
        assertHasViolation("phoneNumber", memberWithPhoneNumber("212-555-1212"));
    }
}
