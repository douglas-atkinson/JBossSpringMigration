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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.persistence.NoResultException;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.as.quickstarts.kitchensink.data.MemberRepository;
import org.jboss.as.quickstarts.kitchensink.model.Member;
import org.jboss.as.quickstarts.kitchensink.service.MemberRegistration;
import org.jboss.as.quickstarts.kitchensink.util.Resources;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Characterization tests for {@link MemberRepository}, written against the current Jakarta EE
 * JPA Criteria query implementation before the Spring Boot migration. These pin the exact
 * lookup and ordering behavior so the same behavior can be verified once the repository is
 * reimplemented (e.g. with Spring Data JPA).
 *
 * <p>Requires a managed JBoss EAP container (JBOSS_HOME) to run, consistent with the existing
 * {@link MemberRegistrationIT} and {@link MemberResourceRESTServiceIT}.</p>
 */
@RunWith(Arquillian.class)
public class MemberRepositoryIT {

    @Deployment
    public static Archive<?> createTestArchive() {
        return ShrinkWrap.create(WebArchive.class, "test.war")
            .addClasses(Member.class, MemberRegistration.class, MemberRepository.class, Resources.class)
            .addAsResource("META-INF/test-persistence.xml", "META-INF/persistence.xml")
            .addAsWebInfResource(new StringAsset("<beans xmlns=\"https://jakarta.ee/xml/ns/jakartaee\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
                        + "xsi:schemaLocation=\"https://jakarta.ee/xml/ns/jakartaee https://jakarta.ee/xml/ns/jakartaee/beans_3_0.xsd\"\n"
                        + "bean-discovery-mode=\"all\">\n"
                        + "</beans>"), "beans.xml")
            // Deploy our test datasource
            .addAsWebInfResource("test-ds.xml");
    }

    @Inject
    MemberRepository memberRepository;

    @Inject
    MemberRegistration memberRegistration;

    private static Member validMember(String name, String email, String phoneNumber) {
        Member member = new Member();
        member.setName(name);
        member.setEmail(email);
        member.setPhoneNumber(phoneNumber);
        return member;
    }

    @Test
    public void findById_withExistingId_returnsMember() throws Exception {
        Member member = validMember("Gina Gettable", "gina.gettable@mailinator.com", "2125550001");
        memberRegistration.register(member);

        Member found = memberRepository.findById(member.getId());

        assertEquals(member.getEmail(), found.getEmail());
    }

    @Test
    public void findById_withUnknownId_returnsNull() {
        Member found = memberRepository.findById(999999999L);

        assertNull(found);
    }

    @Test
    public void findByEmail_withExistingEmail_returnsMember() throws Exception {
        String email = "harry.hittable@mailinator.com";
        memberRegistration.register(validMember("Harry Hittable", email, "2125550002"));

        Member found = memberRepository.findByEmail(email);

        assertEquals(email, found.getEmail());
    }

    @Test
    public void findByEmail_withUnknownEmail_throwsNoResultException() {
        try {
            memberRepository.findByEmail("nobody.missing@mailinator.com");
            fail("expected NoResultException for an email with no matching member");
        } catch (NoResultException expected) {
            // expected: MemberResourceRESTService.emailAlreadyExists relies on this being thrown
        }
    }

    @Test
    public void findAllOrderedByName_returnsResultsSortedByName() throws Exception {
        memberRegistration.register(validMember("Zoe Sortable", "zoe.sortable@mailinator.com", "2125550003"));
        memberRegistration.register(validMember("Abby Sortable", "abby.sortable@mailinator.com", "2125550004"));
        memberRegistration.register(validMember("Mona Sortable", "mona.sortable@mailinator.com", "2125550005"));

        List<Member> members = memberRepository.findAllOrderedByName();

        int abbyIndex = indexOfEmail(members, "abby.sortable@mailinator.com");
        int monaIndex = indexOfEmail(members, "mona.sortable@mailinator.com");
        int zoeIndex = indexOfEmail(members, "zoe.sortable@mailinator.com");

        assertTrue("Abby should be listed before Mona", abbyIndex < monaIndex);
        assertTrue("Mona should be listed before Zoe", monaIndex < zoeIndex);
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
