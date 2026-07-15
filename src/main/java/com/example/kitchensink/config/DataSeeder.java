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
package com.example.kitchensink.config;

import com.example.kitchensink.data.MemberRepository;
import com.example.kitchensink.model.Member;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Seeds the same demo member the original {@code import.sql} loaded on every startup. There's no
 * Mongo equivalent of Hibernate's SQL-import mechanism, and the embedded Mongo instance is
 * ephemeral per run anyway, so this runs unconditionally on every startup against an empty
 * collection.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private final MemberRepository memberRepository;

    public DataSeeder(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @Override
    public void run(String... args) {
        if (memberRepository.count() == 0) {
            Member member = new Member();
            member.setId(0L);
            member.setName("John Smith");
            member.setEmail("john.smith@mailinator.com");
            member.setPhoneNumber("2125551212");
            memberRepository.save(member);
        }
    }
}
