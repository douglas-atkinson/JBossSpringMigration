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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.jboss.as.quickstarts.kitchensink.data.MemberRepository;
import org.jboss.as.quickstarts.kitchensink.model.Member;
import org.jboss.as.quickstarts.kitchensink.rest.MemberResourceRESTService;
import org.jboss.as.quickstarts.kitchensink.service.MemberRegistration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Characterization tests for {@link MemberResourceRESTService}, now a Spring MVC
 * {@code @RestController}. {@code @WebMvcTest} loads just the web layer with a real
 * {@code MockMvc} (so requests go through actual HTTP routing/serialization, not direct Java
 * method calls) and {@code @MockitoBean} standing in for {@link MemberRepository}/
 * {@link MemberRegistration}, isolating these assertions (status codes, JSON error shapes) from
 * persistence entirely.
 */
@WebMvcTest(MemberResourceRESTService.class)
class MemberResourceRESTServiceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MemberResourceRESTService memberResourceRESTService;

    @MockitoBean
    private MemberRepository memberRepositoryMock;

    @MockitoBean
    private MemberRegistration memberRegistrationMock;

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

        mockMvc.perform(post("/rest/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(member)))
                .andExpect(status().isOk());

        verify(memberRegistrationMock).register(argThatMatchesEmail(member.getEmail()));
    }

    @Test
    void createMember_withInvalidData_returnsBadRequestWithViolationsPerField() throws Exception {
        // Name contains a digit, email is malformed, phone number is too short - all three
        // bean validation constraints should be reported in a single response.
        Member member = validMember("Bob123", "not-an-email", "123");

        mockMvc.perform(post("/rest/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(member)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.name").exists())
                .andExpect(jsonPath("$.email").exists())
                .andExpect(jsonPath("$.phoneNumber").exists());
    }

    @Test
    void createMember_withDuplicateEmail_returnsConflict() throws Exception {
        String email = "carol.conflict@mailinator.com";
        when(memberRepositoryMock.findByEmail(email))
                .thenReturn(Optional.of(validMember("Carol Conflict", email, "2125552222")));

        Member duplicate = validMember("Carol Duplicate", email, "2125553333");

        mockMvc.perform(post("/rest/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicate)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.email").value("Email taken"));
    }

    @Test
    void lookupMemberById_withExistingId_returnsMember() throws Exception {
        Member member = validMember("Dave Discoverable", "dave.discoverable@mailinator.com", "2125554444");
        when(memberRepositoryMock.findById(42L)).thenReturn(Optional.of(member));

        mockMvc.perform(get("/rest/members/42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(member.getEmail()));
    }

    @Test
    void lookupMemberById_withUnknownId_returnsNotFound() throws Exception {
        when(memberRepositoryMock.findById(anyLong())).thenReturn(Optional.empty());

        mockMvc.perform(get("/rest/members/999999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void listAllMembers_delegatesToRepository() throws Exception {
        List<Member> expected = List.of(
                validMember("Amy Ordering", "amy.ordering@mailinator.com", "2125555555"),
                validMember("Mia Ordering", "mia.ordering@mailinator.com", "2125556666"));
        when(memberRepositoryMock.findAllByOrderByNameAsc()).thenReturn(expected);

        mockMvc.perform(get("/rest/members"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].email").value("amy.ordering@mailinator.com"))
                .andExpect(jsonPath("$[1].email").value("mia.ordering@mailinator.com"));
    }

    @Test
    void emailAlreadyExists_reflectsRegisteredEmails() {
        String existingEmail = "erin.exists@mailinator.com";
        String unknownEmail = "nobody.here@mailinator.com";
        when(memberRepositoryMock.findByEmail(existingEmail))
                .thenReturn(Optional.of(validMember("Erin Exists", existingEmail, "2125558888")));
        when(memberRepositoryMock.findByEmail(unknownEmail)).thenReturn(Optional.empty());

        assertTrue(memberResourceRESTService.emailAlreadyExists(existingEmail));
        assertFalse(memberResourceRESTService.emailAlreadyExists(unknownEmail));
    }

    private static Member argThatMatchesEmail(String email) {
        return org.mockito.ArgumentMatchers.argThat(candidate -> candidate != null && email.equals(candidate.getEmail()));
    }
}
