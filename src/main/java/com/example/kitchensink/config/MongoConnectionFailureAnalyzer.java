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

import com.mongodb.MongoTimeoutException;
import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

/**
 * Replaces the deep, driver-internal stack trace behind a {@link MongoTimeoutException} (thrown
 * when the driver's server-selection timeout - shortened in {@link MongoConfig} - expires because
 * no MongoDB instance is reachable) with a short, actionable startup failure message. This can be
 * thrown from several different places during context startup (index creation, template
 * initialization, a repository's first query, ...), so a {@code FailureAnalyzer} catches it
 * regardless of where it originates, rather than relying on one specific bean's initialization
 * order.
 */
public class MongoConnectionFailureAnalyzer extends AbstractFailureAnalyzer<MongoTimeoutException> {

    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, MongoTimeoutException cause) {
        return new FailureAnalysis(
                "Could not connect to MongoDB: " + cause.getMessage(),
                "Make sure a local MongoDB instance is installed and running, and that "
                        + "spring.data.mongodb.host/port in application.properties match its "
                        + "address (defaults to localhost:27017).",
                cause);
    }
}
