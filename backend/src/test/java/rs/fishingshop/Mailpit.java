package rs.fishingshop;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;

final class Mailpit {

    private static final GenericContainer<?> container = new GenericContainer<>("axllent/mailpit:v1.27")
            .withExposedPorts(1025, 8025);

    static {
        container.start();
    }

    private static final RestClient api = RestClient.create(
            "http://" + container.getHost() + ":" + container.getMappedPort(8025) + "/api/v1");

    private Mailpit() {
    }

    static void register(DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", container::getHost);
        registry.add("spring.mail.port", () -> container.getMappedPort(1025));
    }

    static void clear() {
        api.delete().uri("/messages").retrieve().toBodilessEntity();
    }

    static String messages() {
        return api.get().uri("/messages").retrieve().body(String.class);
    }

    static String message(String id) {
        return api.get().uri("/message/{id}", id).retrieve().body(String.class);
    }
}
