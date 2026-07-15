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
package com.example.kitchensink.test;

import static org.hamcrest.Matchers.empty;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.List;
import java.util.Optional;

import com.example.kitchensink.controller.MemberController;
import com.example.kitchensink.data.MemberRepository;
import com.example.kitchensink.model.Member;
import com.example.kitchensink.service.MemberRegistration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Characterization tests for {@link MemberController}, the Spring MVC replacement for the
 * original JSF {@code MemberController} + {@code MemberListProducer} pair. {@code @WebMvcTest}
 * loads just the web layer with a real {@code MockMvc}, with {@code @MockitoBean} standing in for
 * {@link MemberRepository}/{@link MemberRegistration}, matching the pattern used by
 * {@link MemberResourceRESTServiceTest}.
 */
@WebMvcTest(MemberController.class)
class MemberControllerTest {

    @Autowired
    private MockMvc mockMvc;

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
    void index_withNoMembers_showsEmptyFormAndEmptyList() throws Exception {
        when(memberRepositoryMock.findAllByOrderByNameAsc()).thenReturn(List.of());

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("registration"))
                .andExpect(model().attributeExists("newMember"))
                .andExpect(model().attribute("members", empty()));
    }

    @Test
    void index_withExistingMembers_showsThemInModel() throws Exception {
        List<Member> expected = List.of(
                validMember("Amy Ordering", "amy.ordering@mailinator.com", "2125555555"),
                validMember("Mia Ordering", "mia.ordering@mailinator.com", "2125556666"));
        when(memberRepositoryMock.findAllByOrderByNameAsc()).thenReturn(expected);

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("registration"))
                .andExpect(model().attribute("members", expected));
    }

    @Test
    void register_withValidData_registersAndRedirectsWithFlashMessage() throws Exception {
        mockMvc.perform(post("/register")
                        .param("name", "Alice Anderson")
                        .param("email", "alice.anderson@mailinator.com")
                        .param("phoneNumber", "2125551111"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attribute("registeredMessage", "Registered!"));

        verify(memberRegistrationMock).register(any(Member.class));
    }

    @Test
    void register_withInvalidData_redisplaysFormWithFieldErrors() throws Exception {
        when(memberRepositoryMock.findAllByOrderByNameAsc()).thenReturn(List.of());

        // Name contains a digit, email is malformed, phone number is too short.
        mockMvc.perform(post("/register")
                        .param("name", "Bob123")
                        .param("email", "not-an-email")
                        .param("phoneNumber", "123"))
                .andExpect(status().isOk())
                .andExpect(view().name("registration"))
                .andExpect(model().attributeHasFieldErrors("newMember", "name", "email", "phoneNumber"));

        verify(memberRegistrationMock, never()).register(any(Member.class));
    }

    @Test
    void register_withDuplicateEmail_redisplaysFormWithEmailTakenError() throws Exception {
        String email = "carol.conflict@mailinator.com";
        when(memberRepositoryMock.findByEmail(email))
                .thenReturn(Optional.of(validMember("Carol Conflict", email, "2125552222")));
        when(memberRepositoryMock.findAllByOrderByNameAsc()).thenReturn(List.of());

        mockMvc.perform(post("/register")
                        .param("name", "Carol Duplicate")
                        .param("email", email)
                        .param("phoneNumber", "2125553333"))
                .andExpect(status().isOk())
                .andExpect(view().name("registration"))
                .andExpect(model().attributeHasFieldErrors("newMember", "email"));

        verify(memberRegistrationMock, never()).register(any(Member.class));
    }

    @Test
    void register_withConcurrentDuplicateEmail_redisplaysFormWithEmailTakenError() throws Exception {
        // emailAlreadyExists finds nothing (no prior registration yet), but a concurrent request
        // wins the race and registers the same email first, so the save itself hits the unique
        // index and MemberRegistration surfaces that as a DuplicateKeyException.
        String email = "dana.race@mailinator.com";
        when(memberRepositoryMock.findByEmail(email)).thenReturn(Optional.empty());
        when(memberRepositoryMock.findAllByOrderByNameAsc()).thenReturn(List.of());
        doThrow(new DuplicateKeyException("E11000 duplicate key error"))
                .when(memberRegistrationMock).register(any(Member.class));

        mockMvc.perform(post("/register")
                        .param("name", "Dana Racer")
                        .param("email", email)
                        .param("phoneNumber", "2125554444"))
                .andExpect(status().isOk())
                .andExpect(view().name("registration"))
                .andExpect(model().attributeHasFieldErrors("newMember", "email"));
    }
}
