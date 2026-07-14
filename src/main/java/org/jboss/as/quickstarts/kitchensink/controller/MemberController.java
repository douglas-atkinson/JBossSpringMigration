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
package org.jboss.as.quickstarts.kitchensink.controller;

import jakarta.validation.Valid;

import org.jboss.as.quickstarts.kitchensink.data.MemberRepository;
import org.jboss.as.quickstarts.kitchensink.model.Member;
import org.jboss.as.quickstarts.kitchensink.service.MemberRegistration;
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

        if (result.hasErrors()) {
            model.addAttribute("members", memberRepository.findAllByOrderByNameAsc());
            return "registration";
        }

        memberRegistration.register(newMember);
        redirectAttributes.addFlashAttribute("registeredMessage", "Registered!");
        return "redirect:/";
    }

    private boolean emailAlreadyExists(String email) {
        return memberRepository.findByEmail(email).isPresent();
    }
}
