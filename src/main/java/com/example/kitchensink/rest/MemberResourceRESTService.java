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
package com.example.kitchensink.rest;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ValidationException;
import jakarta.validation.Validator;

import com.example.kitchensink.data.MemberRepository;
import com.example.kitchensink.model.Member;
import com.example.kitchensink.service.MemberRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Spring MVC replacement for the original JAX-RS resource. Mapped at the same "/rest/members"
 * path (see application.properties' server.servlet.context-path for the outer "/kitchensink"
 * prefix) so existing clients, including RemoteMemberRegistrationIT, keep working unchanged.
 */
@RestController
@RequestMapping("/rest/members")
public class MemberResourceRESTService {

    private static final Logger log = LoggerFactory.getLogger(MemberResourceRESTService.class);

    private final Validator validator;

    private final MemberRepository repository;

    private final MemberRegistration registration;

    public MemberResourceRESTService(Validator validator, MemberRepository repository, MemberRegistration registration) {
        this.validator = validator;
        this.repository = repository;
        this.registration = registration;
    }

    @GetMapping
    public List<Member> listAllMembers() {
        return repository.findAllByOrderByNameAsc();
    }

    @GetMapping("/{id:[0-9]+}")
    public Member lookupMemberById(@PathVariable long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    /**
     * Creates a new member from the values provided. Performs validation, and will return either
     * 200 ok, or a map of fields and their related errors.
     */
    @PostMapping
    public ResponseEntity<Object> createMember(@RequestBody Member member) {
        try {
            // Validates member using bean validation
            validateMember(member);

            registration.register(member);

            return ResponseEntity.ok().build();
        } catch (ConstraintViolationException ce) {
            // Handle bean validation issues
            return createViolationResponse(ce.getConstraintViolations());
        } catch (ValidationException e) {
            // Handle the unique constraint violation caught by the optimistic emailAlreadyExists check
            return emailTakenResponse();
        } catch (DuplicateKeyException e) {
            // Two concurrent requests can both pass the emailAlreadyExists check in validateMember;
            // the unique index on Member.email is what actually catches the race.
            return emailTakenResponse();
        } catch (Exception e) {
            // Handle generic exceptions
            Map<String, String> responseObj = new HashMap<>();
            responseObj.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(responseObj);
        }
    }

    /**
     * <p>
     * Validates the given Member variable and throws validation exceptions based on the type of error. If the error is standard
     * bean validation errors then it will throw a ConstraintValidationException with the set of the constraints violated.
     * </p>
     * <p>
     * If the error is caused because an existing member with the same email is registered it throws a regular validation
     * exception so that it can be interpreted separately.
     * </p>
     *
     * @param member Member to be validated
     * @throws ConstraintViolationException If Bean Validation errors exist
     * @throws ValidationException If member with the same email already exists
     */
    private void validateMember(Member member) throws ConstraintViolationException, ValidationException {
        // Create a bean validator and check for issues.
        Set<ConstraintViolation<Member>> violations = validator.validate(member);

        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(new HashSet<>(violations));
        }

        // Check the uniqueness of the email address
        if (emailAlreadyExists(member.getEmail())) {
            throw new ValidationException("Unique Email Violation");
        }
    }

    /**
     * Builds a "Bad Request" response including a map of all violation fields, and their message. This can then be used
     * by clients to show violations.
     *
     * @param violations A set of violations that needs to be reported
     * @return response containing all violations
     */
    private ResponseEntity<Object> createViolationResponse(Set<ConstraintViolation<?>> violations) {
        log.debug("Validation completed. violations found: {}", violations.size());

        Map<String, String> responseObj = new HashMap<>();

        for (ConstraintViolation<?> violation : violations) {
            responseObj.put(violation.getPropertyPath().toString(), violation.getMessage());
        }

        return ResponseEntity.badRequest().body(responseObj);
    }

    private ResponseEntity<Object> emailTakenResponse() {
        Map<String, String> responseObj = new HashMap<>();
        responseObj.put("email", "Email taken");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(responseObj);
    }

    /**
     * Checks if a member with the same email address is already registered.
     *
     * @param email The email to check
     * @return True if the email already exists, and false otherwise
     */
    public boolean emailAlreadyExists(String email) {
        return repository.findByEmail(email).isPresent();
    }
}
