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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import com.example.kitchensink.data.MemberRepository;
import com.example.kitchensink.data.MemberSequenceGenerator;
import com.example.kitchensink.model.Member;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.context.annotation.Import;

/**
 * Characterization tests for {@link MemberRepository}, now a Spring Data MongoDB repository
 * interface. {@code @DataMongoTest} auto-configures an embedded, ephemeral MongoDB instance (via
 * de.flapdoodle.embed.mongo.spring30x) and a real implementation of the repository - no
 * Arquillian, no hand-rolled Mongo client bootstrap, no external container of any kind.
 *
 * <p>{@code findByEmail} returns an empty {@link Optional} for an unknown email, matching the
 * Spring Data idiom that {@link MemberResourceRESTService#emailAlreadyExists} relies on - this
 * held true before the Mongo migration (as a Spring Data JPA repository) and still holds now.</p>
 *
 * <p>Unlike JPA's identity generation, Mongo's native {@code _id} isn't a sequential number, so
 * {@code id} is now assigned explicitly before each save - here via a local counter, and in
 * production via {@link MemberSequenceGenerator} (covered separately below). All test methods
 * share one embedded Mongo instance for the class, and Mongo enforces a unique index on
 * {@code _id}, so each saved member needs a distinct id.</p>
 */
@DataMongoTest
@Import(MemberSequenceGenerator.class)
class MemberRepositoryTest {

    private static final AtomicLong NEXT_TEST_ID = new AtomicLong(1);

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private MemberSequenceGenerator sequenceGenerator;

    private static Member validMember(String name, String email, String phoneNumber) {
        Member member = new Member();
        member.setId(NEXT_TEST_ID.getAndIncrement());
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

    @Test
    void sequenceGenerator_assignsStrictlyIncreasingIds() {
        long first = sequenceGenerator.nextId();
        long second = sequenceGenerator.nextId();
        long third = sequenceGenerator.nextId();

        assertTrue(second > first, "second id should be greater than first");
        assertTrue(third > second, "third id should be greater than second");
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
