/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.helidon.examples.quickstart.se;

import javax.json.Json;
import javax.json.JsonObject;

import io.helidon.common.http.Http;
import io.helidon.config.Config;
import io.helidon.metrics.RegistryFactory;
import io.helidon.security.webserver.WebSecurity;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;
import io.opentracing.Span;
import io.opentracing.util.GlobalTracer;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.MetricRegistry;

/**
 * A simple service to greet you. Examples:
 *
 * Get default greeting message:
 * curl -X GET http://localhost:8080/greet
 *
 * Get greeting message for Joe:
 * curl -X GET http://localhost:8080/greet/Joe
 *
 * Change greeting
 * curl -X PUT http://localhost:8080/greet/greeting/Hola
 *
 * Change greeting using JSON post
 * curl -X POST -d '{"greeting" : "Howdy"}' http://localhost:8080/greet/greeting
 *
 * Change greeting using JSON post to an artificially slow handler
 * curl -X POST -d '{"greeting" : "Hi"}' http://localhost:8080/greet/slowgreeting
 *
 * The message is returned as a JSON object
 */

public class GreetService implements Service {

    /**
     * This gets config from application.yaml on classpath
     * and uses "app" section.
     */
    private static final Config CONFIG = Config.create().get("app");

    /**
     * The config value for the key {@code greeting}.
     */
    private static volatile String greeting = CONFIG.get("greeting").asString("Ciao");

    /**
     * Create metric registry.
     */
    private final MetricRegistry registry = RegistryFactory.getRegistryFactory().get()
        .getRegistry(MetricRegistry.Type.APPLICATION);

    /**
     * Counter for all requests to this service.
     */
    private final Counter greetCounter = registry.counter("accessctr");

    /**
     * A service registers itself by updating the routine rules.
     * @param rules the routing rules.
     */
    @Override
    public final void update(final Routing.Rules rules) {
        rules
            .any(this::counterFilter)
            .get("/", this::getDefaultMessageHandler)
            .get("/greeting", this::getGreetingHandler)
            .get("/{name}", this::getMessageHandler)
            .put("/greeting/{greeting}", WebSecurity.authenticate(), this::updateGreetingHandler)
            .post("/greeting", this::updateGreetingJsonHandler)
            .post("/slowgreeting", this::updateGreetingJsonSlowlyHandler);
    }

    /**
     * Runs for every request and increments a simple counter.
     * Calls request.next() to ensure other handlers are called.
     * @param request
     * @param response
     */
    private void counterFilter(final ServerRequest request,
                               final ServerResponse response) {
        displayThread();
        this.greetCounter.inc();
        request.next();
    }

    /**
     * Return a wordly greeting message.
     * curl -X GET http://localhost:8080/greet
     * @param request the server request
     * @param response the server response
     */
    private void getDefaultMessageHandler(final ServerRequest request,
                                          final ServerResponse response) {
        displayThread();

        String msg = String.format("%s %s!", greeting, "World");

        JsonObject returnObject = Json.createObjectBuilder()
                .add("message", msg)
                .build();
        response.send(returnObject);
    }

    /**
     * Return a greeting message using the name that was provided.
     * curl -X GET http://localhost:8080/greet/Joe
     * @param request the server request
     * @param response the server response
     */
    private void getMessageHandler(final ServerRequest request,
                                   final ServerResponse response) {
        displayThread();
        String name = request.path().param("name");
        String msg = String.format("%s %s!", greeting, name);

        JsonObject returnObject = Json.createObjectBuilder()
                .add("message", msg)
                .build();
        response.send(returnObject);
    }

    /**
     * Get the greeting in use. curl -X GET
     * http://localhost:8080/greet/greeting
     *
     * @param request the server request
     * @param response the server response
     */
    private void getGreetingHandler(final ServerRequest request,
            final ServerResponse response) {
        displayThread();

        JsonObject returnObject = Json.createObjectBuilder()
                .add("greeting", greeting)
                .build();
        response.send(returnObject);
    }

    /**
     * Set the greeting to use in future messages.
     * curl -X PUT http://localhost:8080/greet/greeting/Hola
     * @param request the server request
     * @param response the server response
     */
    private void updateGreetingHandler(final ServerRequest request,
                                       final ServerResponse response) {
        displayThread();
        greeting = request.path().param("greeting");

        JsonObject returnObject = Json.createObjectBuilder()
                .add("greeting", greeting)
                .build();
        response.send(returnObject);
    }

    /**
     * Set the greeting to use in future messages.
     * Assumes new greeting is in json payload
     * curl -X POST -d '{"greeting" : "Howdy"}' http://localhost:8080/greet/greeting
     * @param request the server request
     * @param response the server response
     */
    private void updateGreetingJsonHandler(final ServerRequest request,
                                           final ServerResponse response) {
        displayThread();

        /*
         * Get the request body, and when it completes process it
         * synchronously using updateGreetingFromJson().
         */
        request.content().as(JsonObject.class).thenAccept(jo -> updateGreetingFromJson(jo, response) );

        // No! This will deadlock. You should never block in your handler
        //JsonObject jo = request.content().as(JsonObject.class).toCompletableFuture().get();
    }

    private void updateGreetingFromJson(JsonObject jo, ServerResponse response) {
        displayThread();

        if (jo.isNull("greeting")) {
            response.status(Http.Status.BAD_REQUEST_400)
                    .send("No greeting in your JSON dude!");
            return;
        }

        greeting = jo.getString("greeting");

        JsonObject returnObject = Json.createObjectBuilder()
                .add("greeting", greeting)
                .build();
        response.send(returnObject);
    }

    /**
     * Slowly set the greeting to use in future messages.
     * Assumes new greeting is in json payload.
     * curl -X POST -d '{"greeting" : "Hi"}' http://localhost:8080/greet/slowgreeting
     * @param request the server request
     * @param response the server response
     */
    private void updateGreetingJsonSlowlyHandler(final ServerRequest request,
                                                 final ServerResponse response) {
        displayThread();

        /*
         * We create a tracing span for this long operation. We create it as
         * a child of the request's span.
         */
        //Tracer tracer = request.webServer().configuration().tracer();
        Span span = GlobalTracer.get()
                .buildSpan("updateGreetingFromJsonSlowlyHandler")
                .asChildOf(request.spanContext())
                .start();

        /*
         * Get the request body, and when it completes process it asynchronously
         * using updateGreetingFromJsonSlowly(). We use Async because the operation
         * is slow and this will offload the work to the default CompletionStage
         * threadpool. In real life you'd want to provide your own ExecutorService
         * instead of using the default pool since it's used for multiple things
         * by the concurrency package.
         *
         * We pass the span to the consumer so it can close it.
         */
        request.content().as(JsonObject.class)
                .thenAcceptAsync(jo -> updateGreetingFromJsonSlowly(jo, response, span) );
    }

    private void updateGreetingFromJsonSlowly(JsonObject jo, ServerResponse response, Span span) {
        displayThread();

        if (jo.isNull("greeting")) {
            response.status(Http.Status.BAD_REQUEST_400)
                    .send("No greeting in your JSON dude!");
        } else {
            int delayInSecs = jo.getInt("delay", 2);
            try {
                Thread.sleep(delayInSecs * 1000);
            } catch (InterruptedException e) {
            }

            greeting = jo.getString("greeting");
            JsonObject returnObject = Json.createObjectBuilder()
                    .add("greeting", greeting)
                    .build();
            response.send(returnObject);
        }

        if (span != null) {
            span.finish();
        }
    }

    private void displayThread() {
        String methodName = Thread.currentThread().getStackTrace()[2].getMethodName();
        System.out.println("Method=" + methodName + " " + "Thread=" + Thread.currentThread().getName());
    }
}
