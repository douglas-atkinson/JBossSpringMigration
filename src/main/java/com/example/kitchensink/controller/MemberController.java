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
package com.example.kitchensink.controller;

import jakarta.validation.Valid;

import com.example.kitchensink.data.MemberRepository;
import com.example.kitchensink.model.Member;
import com.example.kitchensink.service.MemberRegistration;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Spring MVC replacement for the original JSF {@code MemberController} + {@code MemberListProducer}
 * pair. Renders the member registration page at "/" (see application.properties' context-path for
 * the outer "/kitchensink" prefix), reusing the same {@link MemberRepository} and
 * {@link MemberRegistration} beans the REST API uses.
 */
@Controller
public class MemberController {

    private final MemberRepository memberRepository;

    private final MemberRegistration memberRegistration;

    public MemberController(MemberRepository memberRepository, MemberRegistration memberRegistration) {
        this.memberRepository = memberRepository;
        this.memberRegistration = memberRegistration;
    }

    @GetMapping("/")
    public String index(Model model) {
        if (!model.containsAttribute("newMember")) {
            model.addAttribute("newMember", new Member());
        }
        model.addAttribute("members", memberRepository.findAllByOrderByNameAsc());
        return "registration";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("newMember") Member newMember, BindingResult result, Model model,
            RedirectAttributes redirectAttributes) {
        if (!result.hasErrors() && emailAlreadyExists(newMember.getEmail())) {
            result.rejectValue("email", "duplicate", "Email taken");
        }

        if (!result.hasErrors()) {
            try {
                memberRegistration.register(newMember);
                redirectAttributes.addFlashAttribute("registeredMessage", "Registered!");
                return "redirect:/";
            } catch (DuplicateKeyException e) {
                // Two concurrent requests can both pass the emailAlreadyExists check above; the
                // unique index on Member.email is what actually catches the race, so treat it the
                // same as the optimistic check finding the duplicate up front.
                result.rejectValue("email", "duplicate", "Email taken");
            }
        }

        model.addAttribute("members", memberRepository.findAllByOrderByNameAsc());
        return "registration";
    }

    private boolean emailAlreadyExists(String email) {
        return memberRepository.findByEmail(email).isPresent();
    }
}
