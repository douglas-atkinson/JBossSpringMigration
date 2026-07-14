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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.reflect.Field;
import java.util.List;
import java.util.logging.Logger;

import jakarta.enterprise.event.Event;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.NoResultException;
import jakarta.persistence.Persistence;

import org.jboss.as.quickstarts.kitchensink.data.MemberRepository;
import org.jboss.as.quickstarts.kitchensink.model.Member;
import org.jboss.as.quickstarts.kitchensink.service.MemberRegistration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Characterization tests for {@link MemberRepository}, run as a plain JUnit 5 test against a
 * standalone (non-container, RESOURCE_LOCAL) Hibernate/H2 persistence unit - see
 * src/test/resources/META-INF/persistence.xml. No Arquillian, ShrinkWrap, or running
 * JBoss/WildFly instance is involved; MemberRepository and MemberRegistration are constructed
 * directly here, with their {@code @Inject} fields set via reflection instead of a DI container.
 */
class MemberRepositoryTest {

    private static EntityManagerFactory entityManagerFactory;

    private EntityManager entityManager;
    private MemberRepository memberRepository;
    private MemberRegistration memberRegistration;

    @BeforeAll
    static void createEntityManagerFactory() {
        entityManagerFactory = Persistence.createEntityManagerFactory("kitchensinkTest");
    }

    @AfterAll
    static void closeEntityManagerFactory() {
        entityManagerFactory.close();
    }

    @BeforeEach
    void setUp() throws Exception {
        entityManager = entityManagerFactory.createEntityManager();

        memberRepository = new MemberRepository();
        setField(memberRepository, "em", entityManager);

        memberRegistration = new MemberRegistration();
        setField(memberRegistration, "em", entityManager);
        setField(memberRegistration, "log", Logger.getLogger(MemberRegistration.class.getName()));
        setField(memberRegistration, "memberEventSrc", Mockito.mock(Event.class));
    }

    @AfterEach
    void tearDown() {
        entityManager.close();
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private void register(Member member) throws Exception {
        entityManager.getTransaction().begin();
        memberRegistration.register(member);
        entityManager.getTransaction().commit();
    }

    private static Member validMember(String name, String email, String phoneNumber) {
        Member member = new Member();
        member.setName(name);
        member.setEmail(email);
        member.setPhoneNumber(phoneNumber);
        return member;
    }

    @Test
    void findById_withExistingId_returnsMember() throws Exception {
        Member member = validMember("Gina Gettable", "gina.gettable@mailinator.com", "2125550001");
        register(member);

        Member found = memberRepository.findById(member.getId());

        assertEquals(member.getEmail(), found.getEmail());
    }

    @Test
    void findById_withUnknownId_returnsNull() {
        Member found = memberRepository.findById(999999999L);

        assertNull(found);
    }

    @Test
    void findByEmail_withExistingEmail_returnsMember() throws Exception {
        String email = "harry.hittable@mailinator.com";
        register(validMember("Harry Hittable", email, "2125550002"));

        Member found = memberRepository.findByEmail(email);

        assertEquals(email, found.getEmail());
    }

    @Test
    void findByEmail_withUnknownEmail_throwsNoResultException() {
        assertThrows(NoResultException.class,
                () -> memberRepository.findByEmail("nobody.missing@mailinator.com"));
    }

    @Test
    void findAllOrderedByName_returnsResultsSortedByName() throws Exception {
        register(validMember("Zoe Sortable", "zoe.sortable@mailinator.com", "2125550003"));
        register(validMember("Abby Sortable", "abby.sortable@mailinator.com", "2125550004"));
        register(validMember("Mona Sortable", "mona.sortable@mailinator.com", "2125550005"));

        List<Member> members = memberRepository.findAllOrderedByName();

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
