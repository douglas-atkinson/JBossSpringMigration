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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.as.quickstarts.kitchensink.data.MemberRepository;
import org.jboss.as.quickstarts.kitchensink.model.Member;
import org.jboss.as.quickstarts.kitchensink.rest.MemberResourceRESTService;
import org.jboss.as.quickstarts.kitchensink.service.MemberRegistration;
import org.jboss.as.quickstarts.kitchensink.util.Resources;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Characterization tests for {@link MemberResourceRESTService}, written against the current
 * Jakarta EE implementation before the Spring Boot migration. These pin down the observable
 * behavior of the REST layer (status codes and response bodies) so the same behavior can be
 * verified after the port.
 *
 * <p>Methods are invoked directly on the CDI-managed bean rather than over HTTP, mirroring
 * {@link MemberRegistrationIT}. This exercises the real validation, persistence, and uniqueness
 * logic without requiring a deployed servlet container to issue HTTP requests against.</p>
 */
@RunWith(Arquillian.class)
public class MemberResourceRESTServiceIT {

    @Deployment
    public static Archive<?> createTestArchive() {
        return ShrinkWrap.create(WebArchive.class, "test.war")
            .addClasses(Member.class, MemberRegistration.class, MemberRepository.class,
                    MemberResourceRESTService.class, Resources.class)
            .addAsResource("META-INF/test-persistence.xml", "META-INF/persistence.xml")
            .addAsWebInfResource(new StringAsset("<beans xmlns=\"https://jakarta.ee/xml/ns/jakartaee\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
                        + "xsi:schemaLocation=\"https://jakarta.ee/xml/ns/jakartaee https://jakarta.ee/xml/ns/jakartaee/beans_3_0.xsd\"\n"
                        + "bean-discovery-mode=\"all\">\n"
                        + "</beans>"), "beans.xml")
            // Deploy our test datasource
            .addAsWebInfResource("test-ds.xml");
    }

    @Inject
    MemberResourceRESTService memberResourceRESTService;

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
    public void createMember_withValidData_returnsOkAndPersists() throws Exception {
        Member member = validMember("Alice Anderson", "alice.anderson@mailinator.com", "2125551111");

        Response response = memberResourceRESTService.createMember(member);

        assertEquals(200, response.getStatus());
        assertNotNull("a persisted member should have a generated id", member.getId());
    }

    @Test
    public void createMember_withInvalidData_returnsBadRequestWithViolationsPerField() throws Exception {
        // Name contains a digit, email is malformed, phone number is too short - all three
        // bean validation constraints should be reported in a single response.
        Member member = validMember("Bob123", "not-an-email", "123");

        Response response = memberResourceRESTService.createMember(member);

        assertEquals(400, response.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, String> violations = (Map<String, String>) response.getEntity();
        assertTrue("expected a violation for 'name'", violations.containsKey("name"));
        assertTrue("expected a violation for 'email'", violations.containsKey("email"));
        assertTrue("expected a violation for 'phoneNumber'", violations.containsKey("phoneNumber"));
    }

    @Test
    public void createMember_withDuplicateEmail_returnsConflict() throws Exception {
        String email = "carol.conflict@mailinator.com";
        memberRegistration.register(validMember("Carol Conflict", email, "2125552222"));

        Member duplicate = validMember("Carol Duplicate", email, "2125553333");
        Response response = memberResourceRESTService.createMember(duplicate);

        assertEquals(409, response.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) response.getEntity();
        assertEquals("Email taken", body.get("email"));
    }

    @Test
    public void lookupMemberById_withExistingId_returnsMember() throws Exception {
        Member member = validMember("Dave Discoverable", "dave.discoverable@mailinator.com", "2125554444");
        memberRegistration.register(member);

        Member found = memberResourceRESTService.lookupMemberById(member.getId());

        assertNotNull(found);
        assertEquals(member.getEmail(), found.getEmail());
    }

    @Test
    public void lookupMemberById_withUnknownId_throwsNotFound() {
        try {
            memberResourceRESTService.lookupMemberById(999999999L);
            fail("expected a WebApplicationException for an unknown id");
        } catch (WebApplicationException e) {
            assertEquals(404, e.getResponse().getStatus());
        }
    }

    @Test
    public void listAllMembers_returnsResultsOrderedByName() throws Exception {
        memberRegistration.register(validMember("Zed Ordering", "zed.ordering@mailinator.com", "2125555555"));
        memberRegistration.register(validMember("Amy Ordering", "amy.ordering@mailinator.com", "2125556666"));
        memberRegistration.register(validMember("Mia Ordering", "mia.ordering@mailinator.com", "2125557777"));

        List<Member> members = memberResourceRESTService.listAllMembers();

        int amyIndex = indexOfEmail(members, "amy.ordering@mailinator.com");
        int miaIndex = indexOfEmail(members, "mia.ordering@mailinator.com");
        int zedIndex = indexOfEmail(members, "zed.ordering@mailinator.com");

        assertTrue("Amy should be listed before Mia", amyIndex < miaIndex);
        assertTrue("Mia should be listed before Zed", miaIndex < zedIndex);
    }

    @Test
    public void emailAlreadyExists_reflectsRegisteredEmails() throws Exception {
        String email = "erin.exists@mailinator.com";
        memberRegistration.register(validMember("Erin Exists", email, "2125558888"));

        assertTrue(memberResourceRESTService.emailAlreadyExists(email));
        assertFalse(memberResourceRESTService.emailAlreadyExists("nobody.here@mailinator.com"));
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
