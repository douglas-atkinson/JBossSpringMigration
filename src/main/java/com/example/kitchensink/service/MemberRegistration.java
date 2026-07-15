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
package com.example.kitchensink.service;

import com.example.kitchensink.data.MemberRepository;
import com.example.kitchensink.data.MemberSequenceGenerator;
import com.example.kitchensink.model.Member;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class MemberRegistration {

    private static final Logger log = LoggerFactory.getLogger(MemberRegistration.class);

    private final MemberRepository memberRepository;

    private final MemberSequenceGenerator sequenceGenerator;

    public MemberRegistration(MemberRepository memberRepository, MemberSequenceGenerator sequenceGenerator) {
        this.memberRepository = memberRepository;
        this.sequenceGenerator = sequenceGenerator;
    }

    public void register(Member member) {
        log.info("Registering {}", member.getName());
        member.setId(sequenceGenerator.nextId());
        memberRepository.save(member);
    }
}
