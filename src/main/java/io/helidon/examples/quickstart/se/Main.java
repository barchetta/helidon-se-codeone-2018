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

import java.io.IOException;
import java.util.Optional;
import java.util.logging.LogManager;

import io.helidon.common.http.Http;
import io.helidon.config.Config;
import io.helidon.metrics.MetricsSupport;
import io.helidon.security.Security;
import io.helidon.security.webserver.WebSecurity;
import io.helidon.security.google.GoogleTokenProvider;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerConfiguration;
import io.helidon.webserver.StaticContentSupport;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.json.JsonSupport;
import io.helidon.webserver.zipkin.ZipkinTracerBuilder;

import io.opentracing.Tracer;
import io.opentracing.util.GlobalTracer;

/**
 * Simple Hello World rest application.
 * DONE:
 *  Basic routing
 *  JSON payload request
 *  JSON payload response
 *  JSON payload async
 *  Metrics
 *  Tracing
 *  Health and Ready Check
 * TODO:
 *  Security
 *  Big payloads (streaming)
 *  Error / Exception handling
 *  Logging
 */
public final class Main {

    /**
     * Cannot be instantiated.
     */
    private Main() { }

    /**
     * Creates new {@link Routing}.
     *
     * @return the new instance
     */
    private static Routing createRouting(WebSecurity webSecurity) {
        return Routing.builder()
                .register(webSecurity)
                .register(JsonSupport.get())
                .register(MetricsSupport.create())
                .get("/health", (req, res) -> {
                    res.status(Http.Status.OK_200);
                    res.send("OK!");
                })
                .get("/ready", (req, res) -> {
                    res.status(Http.Status.OK_200);
                    res.send("Ready!");
                })
                .register(StaticContentSupport.builder("/WEB", Main.class.getClassLoader())
                    .welcomeFileName("index.html")
                    .build())
                .register("/greet", new GreetService())
                .build();
    }

    /**
     * Create a {@code Tracer} instance using the given {@code Config}.
     * @param config the configuration root
     * @return the created {@code Tracer}
     */
    private static Tracer createTracer(final Config config) {
        Optional<String> zipkinEndpoint = config.get("services.zipkin.endpoint")
                .asOptionalString();

        if (zipkinEndpoint.isPresent()) {
            System.out.println("Sending trace data to " + zipkinEndpoint.get());
            Tracer tracer = ZipkinTracerBuilder.forService("greet-service")
                    .zipkin(zipkinEndpoint.get())
                    .build();
            GlobalTracer.register(tracer);
        } else {
            System.out.println("services.zipkin.uri not defined. Sending no trace data");
        }

        return GlobalTracer.get();
    }

    /**
     * Create a configured instance of {@link WebSecurity} using the given config.
     * @param config the configuration root
     * @return the created {@code WebSecurity}
     */
    private static WebSecurity createWebSecurity(final Config config) {
        Security security = Security.builder()
            .addProvider(GoogleTokenProvider.builder()
                .clientId(config.get("security.properties.google-client-id").asString()))
            .build();
        return WebSecurity.from(security);
    }

    /**
     * Application main entry point.
     * @param args command line arguments.
     * @throws IOException if there are problems reading logging properties
     */
    public static void main(final String[] args) throws IOException {
        startServer();
    }

    /**
     * Start the server.
     * @return the created {@link WebServer} instance
     * @throws IOException if there are problems reading logging properties
     */
    protected static WebServer startServer() throws IOException {

        // load logging configuration
        LogManager.getLogManager().readConfiguration(
                Main.class.getResourceAsStream("/logging.properties"));

        // By default this will pick up application.yaml from the classpath
        final Config config = Config.create();

        final Tracer tracer = createTracer(config);

        final ServerConfiguration serverConfig = ServerConfiguration
                .builder(config.get("webserver"))
                .tracer(tracer)
                .build();

        final WebSecurity webSecurity = createWebSecurity(config);

        final Routing routing = createRouting(webSecurity);

        final WebServer server = WebServer.create(serverConfig, routing);

        // Start the server and print some info.
        server.start().thenAccept(ws -> {
            System.out.println(
                    "WEB server is up! http://localhost:" + ws.port());
        });

        // Server threads are not demon. NO need to block. Just react.
        server.whenShutdown().thenRun(()
                -> System.out.println("WEB server is DOWN. Good bye!"));

        return server;
    }
}
