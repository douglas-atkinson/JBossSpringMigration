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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import jakarta.persistence.NoResultException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.jboss.as.quickstarts.kitchensink.data.MemberRepository;
import org.jboss.as.quickstarts.kitchensink.model.Member;
import org.jboss.as.quickstarts.kitchensink.rest.MemberResourceRESTService;
import org.jboss.as.quickstarts.kitchensink.service.MemberRegistration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Characterization tests for {@link MemberResourceRESTService}, run as a plain JUnit 5 test with
 * Mockito standing in for {@link MemberRepository}/{@link MemberRegistration} and a real Bean
 * Validation {@link Validator} - no Arquillian, ShrinkWrap, or running JBoss/WildFly instance
 * involved. Mocking the persistence collaborators is also a better fit for the upcoming Spring
 * Boot/MongoDB migration: these assertions (status codes, JSON error shapes) don't depend on JPA
 * at all, so they carry forward unchanged regardless of what backs MemberRepository later.
 */
class MemberResourceRESTServiceTest {

    private static ValidatorFactory validatorFactory;

    private MemberRepository memberRepositoryMock;
    private MemberRegistration memberRegistrationMock;
    private MemberResourceRESTService memberResourceRESTService;

    @BeforeAll
    static void createValidatorFactory() {
        // Same rationale as MemberValidationTest: avoid needing a Jakarta EL implementation on
        // the classpath, which a standalone (non-container) test doesn't have.
        validatorFactory = Validation.byDefaultProvider()
                .configure()
                .messageInterpolator(new ParameterMessageInterpolator())
                .buildValidatorFactory();
    }

    @AfterAll
    static void closeValidatorFactory() {
        validatorFactory.close();
    }

    @BeforeEach
    void setUp() throws Exception {
        memberRepositoryMock = mock(MemberRepository.class);
        memberRegistrationMock = mock(MemberRegistration.class);

        memberResourceRESTService = new MemberResourceRESTService();
        setField(memberResourceRESTService, "log", Logger.getLogger(MemberResourceRESTService.class.getName()));
        setField(memberResourceRESTService, "validator", validatorFactory.getValidator());
        setField(memberResourceRESTService, "repository", memberRepositoryMock);
        setField(memberResourceRESTService, "registration", memberRegistrationMock);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Member validMember(String name, String email, String phoneNumber) {
        Member member = new Member();
        member.setName(name);
        member.setEmail(email);
        member.setPhoneNumber(phoneNumber);
        return member;
    }

    @Test
    void createMember_withValidData_returnsOkAndRegisters() throws Exception {
        Member member = validMember("Alice Anderson", "alice.anderson@mailinator.com", "2125551111");

        Response response = memberResourceRESTService.createMember(member);

        assertEquals(200, response.getStatus());
        verify(memberRegistrationMock).register(member);
    }

    @Test
    void createMember_withInvalidData_returnsBadRequestWithViolationsPerField() {
        // Name contains a digit, email is malformed, phone number is too short - all three
        // bean validation constraints should be reported in a single response.
        Member member = validMember("Bob123", "not-an-email", "123");

        Response response = memberResourceRESTService.createMember(member);

        assertEquals(400, response.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, String> violations = (Map<String, String>) response.getEntity();
        assertTrue(violations.containsKey("name"), "expected a violation for 'name'");
        assertTrue(violations.containsKey("email"), "expected a violation for 'email'");
        assertTrue(violations.containsKey("phoneNumber"), "expected a violation for 'phoneNumber'");
    }

    @Test
    void createMember_withDuplicateEmail_returnsConflict() throws Exception {
        String email = "carol.conflict@mailinator.com";
        when(memberRepositoryMock.findByEmail(email)).thenReturn(validMember("Carol Conflict", email, "2125552222"));

        Member duplicate = validMember("Carol Duplicate", email, "2125553333");
        Response response = memberResourceRESTService.createMember(duplicate);

        assertEquals(409, response.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) response.getEntity();
        assertEquals("Email taken", body.get("email"));
    }

    @Test
    void lookupMemberById_withExistingId_returnsMember() {
        Member member = validMember("Dave Discoverable", "dave.discoverable@mailinator.com", "2125554444");
        when(memberRepositoryMock.findById(42L)).thenReturn(member);

        Member found = memberResourceRESTService.lookupMemberById(42L);

        assertNotNull(found);
        assertEquals(member.getEmail(), found.getEmail());
    }

    @Test
    void lookupMemberById_withUnknownId_throwsNotFound() {
        when(memberRepositoryMock.findById(anyLong())).thenReturn(null);

        WebApplicationException exception = assertThrows(WebApplicationException.class,
                () -> memberResourceRESTService.lookupMemberById(999999999L));

        assertEquals(404, exception.getResponse().getStatus());
    }

    @Test
    void listAllMembers_delegatesToRepository() {
        List<Member> expected = List.of(
                validMember("Amy Ordering", "amy.ordering@mailinator.com", "2125555555"),
                validMember("Mia Ordering", "mia.ordering@mailinator.com", "2125556666"));
        when(memberRepositoryMock.findAllOrderedByName()).thenReturn(expected);

        List<Member> members = memberResourceRESTService.listAllMembers();

        assertEquals(expected, members);
    }

    @Test
    void emailAlreadyExists_reflectsRegisteredEmails() {
        String existingEmail = "erin.exists@mailinator.com";
        String unknownEmail = "nobody.here@mailinator.com";
        when(memberRepositoryMock.findByEmail(existingEmail))
                .thenReturn(validMember("Erin Exists", existingEmail, "2125558888"));
        when(memberRepositoryMock.findByEmail(unknownEmail)).thenThrow(new NoResultException());

        assertTrue(memberResourceRESTService.emailAlreadyExists(existingEmail));
        assertFalse(memberResourceRESTService.emailAlreadyExists(unknownEmail));
    }
}
