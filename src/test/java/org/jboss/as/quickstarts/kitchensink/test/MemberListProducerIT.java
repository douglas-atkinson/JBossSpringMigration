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
import static org.junit.Assert.assertTrue;

import java.util.List;

import jakarta.inject.Inject;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.as.quickstarts.kitchensink.data.MemberListProducer;
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
 * Characterization test for the CDI-event-driven refresh performed by {@link MemberListProducer}.
 * {@link MemberRegistration#register(Member)} fires a CDI event that {@code MemberListProducer}
 * observes ({@code Reception.IF_EXISTS}) to re-query the member list. This mechanism has no
 * direct Spring equivalent, so this test pins the *observable effect* - the list reflects a
 * newly registered member without an explicit re-fetch call - rather than the CDI plumbing
 * itself, so it still applies after the migration replaces the event with something else
 * (e.g. re-querying in the caller, or a Spring {@code ApplicationEventPublisher}).
 *
 * <p>Requires a managed JBoss EAP container (JBOSS_HOME) to run, consistent with the existing
 * {@link MemberRegistrationIT} and {@link MemberResourceRESTServiceIT}.</p>
 */
@RunWith(Arquillian.class)
public class MemberListProducerIT {

    @Deployment
    public static Archive<?> createTestArchive() {
        return ShrinkWrap.create(WebArchive.class, "test.war")
            .addClasses(Member.class, MemberRegistration.class, MemberRepository.class,
                    MemberListProducer.class, Resources.class)
            .addAsResource("META-INF/test-persistence.xml", "META-INF/persistence.xml")
            .addAsWebInfResource(new StringAsset("<beans xmlns=\"https://jakarta.ee/xml/ns/jakartaee\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
                        + "xsi:schemaLocation=\"https://jakarta.ee/xml/ns/jakartaee https://jakarta.ee/xml/ns/jakartaee/beans_3_0.xsd\"\n"
                        + "bean-discovery-mode=\"all\">\n"
                        + "</beans>"), "beans.xml")
            // Deploy our test datasource
            .addAsWebInfResource("test-ds.xml");
    }

    @Inject
    MemberListProducer memberListProducer;

    @Inject
    MemberRegistration memberRegistration;

    @Test
    public void registeringMember_refreshesTheObservedMemberList() throws Exception {
        // Referencing the producer bean's list now ensures a contextual instance exists in this
        // request scope, so the Reception.IF_EXISTS observer on MemberListProducer will actually
        // receive the event fired by register() below rather than being skipped.
        int originalSize = memberListProducer.getMembers().size();

        Member member = new Member();
        member.setName("Iris Instant");
        member.setEmail("iris.instant@mailinator.com");
        member.setPhoneNumber("2125550006");
        memberRegistration.register(member);

        List<Member> refreshed = memberListProducer.getMembers();

        assertEquals(originalSize + 1, refreshed.size());
        assertTrue("expected the newly registered member to appear without an explicit re-fetch",
                refreshed.stream().anyMatch(m -> member.getEmail().equals(m.getEmail())));
    }
}
