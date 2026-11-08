package com.banking.config_server.support;

import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.vault.VaultContainer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * A real HashiCorp Vault in Docker (dev mode), set up the way infra/vault sets it up for
 * local development: the service logs in with its own AppRole, whose policy only reads
 * {@code secret/<application>} and {@code secret/<application>/*}, where its secrets are.
 *
 * <p>The service finds Vault as it does outside tests: application.yaml reads
 * {@code VAULT_URI} and imports the role ID and secret ID from the properties file named
 * by {@code VAULT_APPROLE_FILE}.
 */
public final class TestVault {

    private static final String IMAGE = "hashicorp/vault:2.1.2";
    private static final String ROOT_TOKEN = "test-root-token";

    private final VaultContainer<?> container;

    private TestVault(VaultContainer<?> container) {
        this.container = container;
    }

    /**
     * Starts Vault, stores the secrets of {@code application} and points the application at
     * them through the system properties VAULT_URI and VAULT_APPROLE_FILE.
     */
    public static TestVault start(String application, Map<String, String> secrets) {
        VaultContainer<?> container = new VaultContainer<>(IMAGE).withVaultToken(ROOT_TOKEN);
        container.start();
        TestVault vault = new TestVault(container);

        vault.run("vault", "auth", "enable", "approle");
        String policyFile = "/tmp/" + application + ".hcl";
        container.copyFileToContainer(Transferable.of(policy(application)), policyFile);
        vault.run("vault", "policy", "write", application, policyFile);
        vault.run("vault", "write", "auth/approle/role/" + application, "token_policies=" + application);
        String roleId = vault.run("vault", "read", "-field=role_id", "auth/approle/role/" + application + "/role-id");
        String secretId = vault.run("vault", "write", "-f", "-field=secret_id",
                "auth/approle/role/" + application + "/secret-id");

        List<String> put = new ArrayList<>(List.of("vault", "kv", "put", "secret/" + application));
        secrets.forEach((key, value) -> put.add(key + "=" + value));
        vault.run(put.toArray(String[]::new));

        System.setProperty("VAULT_URI", container.getHttpHostAddress());
        System.setProperty("VAULT_APPROLE_FILE", appRoleFile(application, roleId, secretId));
        return vault;
    }

    /** The same policy infra/vault gives each service: read its own secrets, nothing else. */
    static String policy(String application) {
        return """
                path "secret/data/%1$s" { capabilities = ["read"] }
                path "secret/data/%1$s/*" { capabilities = ["read"] }
                """.formatted(application);
    }

    /** Runs a command inside the container as root and returns its output. */
    public String run(String... command) {
        try {
            ExecResult result = container.execInContainer(command);
            if (result.getExitCode() != 0) {
                throw new IllegalStateException("Vault command failed: " + Arrays.toString(command)
                        + "\n" + result.getStderr());
            }
            return result.getStdout().trim();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static String appRoleFile(String application, String roleId, String secretId) {
        try {
            Path file = Files.createTempFile(application + "-approle-", ".properties");
            file.toFile().deleteOnExit();
            Files.writeString(file, """
                    spring.cloud.vault.app-role.role-id=%s
                    spring.cloud.vault.app-role.secret-id=%s
                    """.formatted(roleId, secretId));
            return file.toAbsolutePath().toString().replace('\\', '/');
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
