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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.List;
import java.util.Optional;

import org.jboss.as.quickstarts.kitchensink.data.MemberRepository;
import org.jboss.as.quickstarts.kitchensink.model.Member;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

/**
 * Characterization tests for {@link MemberRepository}, now a Spring Data JPA repository
 * interface. {@code @DataJpaTest} auto-configures an embedded H2 database and a real
 * implementation of the repository - no Arquillian, no hand-rolled EntityManager bootstrap, no
 * container of any kind.
 *
 * <p>Note the behavioral change from the pre-migration version: {@code findByEmail} now returns
 * an empty {@link Optional} for an unknown email instead of throwing JPA's
 * {@code NoResultException} - that exception-based "not found" signal was always a JPA/Hibernate
 * implementation detail, and Spring Data's Optional-based idiom is what
 * {@link MemberResourceRESTService#emailAlreadyExists} now relies on.</p>
 */
@DataJpaTest
class MemberRepositoryTest {

    @Autowired
    private MemberRepository memberRepository;

    private static Member validMember(String name, String email, String phoneNumber) {
        Member member = new Member();
        member.setName(name);
        member.setEmail(email);
        member.setPhoneNumber(phoneNumber);
        return member;
    }

    @Test
    void findById_withExistingId_returnsMember() {
        Member member = memberRepository.save(validMember("Gina Gettable", "gina.gettable@mailinator.com", "2125550001"));

        Optional<Member> found = memberRepository.findById(member.getId());

        assertTrue(found.isPresent());
        assertEquals(member.getEmail(), found.get().getEmail());
    }

    @Test
    void findById_withUnknownId_returnsEmpty() {
        Optional<Member> found = memberRepository.findById(999999999L);

        assertFalse(found.isPresent());
    }

    @Test
    void findByEmail_withExistingEmail_returnsMember() {
        String email = "harry.hittable@mailinator.com";
        memberRepository.save(validMember("Harry Hittable", email, "2125550002"));

        Optional<Member> found = memberRepository.findByEmail(email);

        assertTrue(found.isPresent());
        assertEquals(email, found.get().getEmail());
    }

    @Test
    void findByEmail_withUnknownEmail_returnsEmpty() {
        Optional<Member> found = memberRepository.findByEmail("nobody.missing@mailinator.com");

        assertFalse(found.isPresent());
    }

    @Test
    void findAllByOrderByNameAsc_returnsResultsSortedByName() {
        memberRepository.save(validMember("Zoe Sortable", "zoe.sortable@mailinator.com", "2125550003"));
        memberRepository.save(validMember("Abby Sortable", "abby.sortable@mailinator.com", "2125550004"));
        memberRepository.save(validMember("Mona Sortable", "mona.sortable@mailinator.com", "2125550005"));

        List<Member> members = memberRepository.findAllByOrderByNameAsc();

        int abbyIndex = indexOfEmail(members, "abby.sortable@mailinator.com");
        int monaIndex = indexOfEmail(members, "mona.sortable@mailinator.com");
        int zoeIndex = indexOfEmail(members, "zoe.sortable@mailinator.com");

        assertTrue(abbyIndex < monaIndex, "Abby should be listed before Mona");
        assertTrue(monaIndex < zoeIndex, "Mona should be listed before Zoe");
    }

    private static int indexOfEmail(List<Member> members, String email) {
        for (int i = 0; i < members.size(); i++) {
            if (email.equals(members.get(i).getEmail())) {
                return i;
            }
        }
        fail("expected to find a member with email " + email);
        return -1;
    }
}
