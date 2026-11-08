package com.banking.config_server;

import com.banking.config_server.support.TestVault;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the Config Server against a real Vault in Docker, with the "native" profile serving a
 * temporary folder instead of the GitHub repository, as in local development.
 *
 * <p>The GitHub user and token of the git backend come from Vault; the server must not hand
 * them to the services it serves.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("native")
class ConfigServerApplicationTests {

    private static final String GIT_USERNAME = "banking-bot";
    private static final String GIT_TOKEN = "test-git-token-from-vault";
    private static final Path REPOSITORY = repository();

    static {
        // Read by application.yaml through VAULT_URI and VAULT_APPROLE_FILE.
        TestVault.start("config-server", Map.of(
                "spring.cloud.config.server.git.username", GIT_USERNAME,
                "spring.cloud.config.server.git.password", GIT_TOKEN));
    }

    @DynamicPropertySource
    static void nativeRepository(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.config.server.native.search-locations", () -> REPOSITORY.toUri().toString());
        // No registry in the tests: the server must not try to register with a real Eureka.
        registry.add("eureka.client.enabled", () -> "false");
    }

    @Autowired
    private ConfigurableEnvironment environment;

    @LocalServerPort
    private int port;

    @Test
    void readsTheGitCredentialsFromVault() {
        assertThat(sourceOf("spring.cloud.config.server.git.username")).contains("secret/config-server");
        assertThat(sourceOf("spring.cloud.config.server.git.password")).contains("secret/config-server");
        assertThat(environment.getProperty("spring.cloud.config.server.git.password")).isEqualTo(GIT_TOKEN);
    }

    @Test
    void servesTheRepositoryButNotItsOwnSecrets() {
        String body = RestClient.create("http://localhost:" + port).get()
                .uri("/demo-service/dev")
                .retrieve()
                .body(String.class);

        assertThat(body).contains("demo-service-dev.yml", "demo.greeting", "hello from dev");
        assertThat(body).doesNotContain(GIT_TOKEN, GIT_USERNAME, "spring.cloud.vault", "secret/config-server");
    }

    /** Name of the first (winning) property source that defines {@code key}. */
    private String sourceOf(String key) {
        return environment.getPropertySources().stream()
                // Boot's view over all the other sources, not a source of its own.
                .filter(source -> !"configurationProperties".equals(source.getName()))
                .filter(source -> source.containsProperty(key))
                .map(PropertySource::getName)
                .findFirst()
                .orElse("none");
    }

    /** A throwaway banking-config with one service and no secrets. */
    private static Path repository() {
        try {
            Path folder = Files.createTempDirectory("banking-config-");
            Files.writeString(folder.resolve("demo-service-dev.yml"), """
                    demo:
                      greeting: hello from dev
                    """);
            folder.toFile().deleteOnExit();
            folder.resolve("demo-service-dev.yml").toFile().deleteOnExit();
            return folder;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
