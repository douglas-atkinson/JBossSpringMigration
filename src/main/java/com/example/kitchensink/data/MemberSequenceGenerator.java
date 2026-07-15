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
package com.example.kitchensink.data;

import org.bson.Document;
import com.example.kitchensink.model.Member;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import static org.springframework.data.mongodb.core.query.Criteria.where;

/**
 * Emulates a relational auto-increment id via an atomic counter document, since MongoDB's native
 * {@code _id} is an ObjectId rather than a sequential number. Backs {@link Member#getId()}, which
 * the REST API ({@code /rest/members/{id:[0-9]+}}) still expects to be numeric.
 */
@Component
public class MemberSequenceGenerator {

    private static final String SEQUENCE_NAME = "member";

    private final MongoOperations mongoOperations;

    public MemberSequenceGenerator(MongoOperations mongoOperations) {
        this.mongoOperations = mongoOperations;
    }

    public long nextId() {
        Document counter = mongoOperations.findAndModify(
                Query.query(where("_id").is(SEQUENCE_NAME)),
                new Update().inc("seq", 1L),
                FindAndModifyOptions.options().returnNew(true).upsert(true),
                Document.class,
                "database_sequences");
        return counter.getLong("seq");
    }
}
