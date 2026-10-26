/*
 * Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.jlinalg.cli;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.function.Function;

/** Unlock only after policy checks; renew only at documented provider endpoints. */
final class StudyCredentials {
    record Credential(String token, List<String> secrets, boolean renewed) {
        @Override public String toString() { return "Credential[REDACTED]"; }
        String redact(String text) {
            for (String secret : secrets) if (!secret.isBlank()) {
                String encoded = StudyAssistance.json(secret);
                text = text.replace(encoded.substring(1, encoded.length()-1), "[REDACTED_CREDENTIAL]");
                text = text.replace(secret, "[REDACTED_CREDENTIAL]");
            }
            return text;
        }
    }
    record Reply(int status, String body) {
        @Override public String toString() { return "CredentialResponse[REDACTED]"; }
    }
    @FunctionalInterface interface Transport {
        Reply send(URI endpoint, String contentType, String body) throws IOException, InterruptedException;
    }
    @FunctionalInterface interface Passwords { char[] read(Path file, String environment) throws IOException; }
    static final class Failure extends IOException {
        private static final long serialVersionUID = 1L;
        final String status;
        Failure(String status, String message) { super(message); this.status = status; }
    }
    private static final ThreadLocal<Map<String,char[]>> PASSWORDS = new ThreadLocal<>();
    private final Passwords passwords;
    private final Transport transport;
    private final Clock clock;

    StudyCredentials() { this(StudyCredentials::password, StudyCredentials::send, Clock.systemUTC()); }
    StudyCredentials(Passwords passwords, Transport transport, Clock clock) {
        this.passwords = passwords; this.transport = transport; this.clock = clock;
    }

    static AutoCloseableSession session() { return new AutoCloseableSession(); }
    static final class AutoCloseableSession implements AutoCloseable {
        private final Map<String,char[]> previous = PASSWORDS.get();
        AutoCloseableSession() { PASSWORDS.set(new HashMap<>()); }
        @Override public void close() {
            PASSWORDS.get().values().forEach(value -> Arrays.fill(value, '\0'));
            if (previous == null) PASSWORDS.remove(); else PASSWORDS.set(previous);
        }
    }

    static synchronized char[] password(Path file, String environment) throws IOException {
        String cacheKey = file.toString() + "\n" + environment;
        var cache = PASSWORDS.get();
        if (cache != null && cache.containsKey(cacheKey)) return cache.get(cacheKey).clone();
        char[] value;
        if (environment != null && !environment.isBlank()) {
            if (!environment.matches("[A-Za-z_][A-Za-z0-9_]*")) throw new IOException("Invalid credential password environment variable name");
            String found = System.getenv(environment);
            if (found == null || found.isBlank()) throw new IOException("Credential password environment variable is unset");
            value = found.toCharArray();
        } else {
            var console = System.console();
            if (console == null) throw new IOException("No secure console; configure credential_password_env with a secret-manager supplied variable name");
            value = console.readPassword("Unlock credential file %s: ", file.getFileName());
            if (value == null) throw new IOException("Credential unlock cancelled");
        }
        if (cache != null) cache.put(cacheKey, value.clone());
        return value;
    }

    Credential load(Map<String,Object> config, Path project) throws Failure {
        char[] password = null;
        try {
            Path file = StudyCredentialVault.external(Path.of(text(config.get("credential_file"))), project);
            password = passwords.read(file, text(config.get("credential_password_env")));
            // A separate stable lock survives atomic replacement. It is held across read/refresh/save.
            Path lockPath = file.resolveSibling(file.getFileName() + ".lock");
            if (Files.isSymbolicLink(lockPath)) throw new IOException("Credential lock cannot be a link");
            try (FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
                StudyCredentialVault.restrict(lockPath);
                try (var lock = tryLock(channel)) {
                    if (!lock.isValid()) throw new IOException("Credential lock unavailable");
                    var stored = StudyCredentialVault.read(file, password);
                    validate(stored);
                    if (!text(stored.get("provider")).equals(text(config.get("provider")))
                            || !text(stored.get("auth")).equals(text(config.getOrDefault("auth","api_key")))
                            || !text(stored.get("endpoint")).equals(endpoint(text(config.get("provider")), text(config.get("endpoint")))))
                        throw new Failure("credential_binding_mismatch", "Saved credential does not match the selected provider, authentication method and endpoint");
                    List<String> secrets = secrets(stored);
                    boolean renewed = false;
                    Instant now = clock.instant();
                    Instant expires = instant(stored.get("expires_at"));
                    if (expires != null && !expires.isAfter(now.plusSeconds(30))) {
                        Instant earliest = instant(stored.get("earliest_refresh_at"));
                        if (earliest != null && earliest.isAfter(now)) {
                            if (!expires.isAfter(now)) throw new Failure("credential_expired", "Credential expired before its permitted refresh time; sign in again");
                        } else if (!text(stored.get("renewal")).equals("none")) {
                            stored = renew(stored, secrets, project);
                            StudyCredentialVault.replace(file, stored, password);
                            renewed = true;
                        } else if (!expires.isAfter(now)) {
                            throw new Failure("credential_expired", "Saved credential expired; obtain a replacement or configure the provider-supported renewal flow");
                        }
                    }
                    secrets.addAll(secrets(stored));
                    return new Credential(text(stored.get("access_token")), List.copyOf(secrets), renewed);
                }
            }
        } catch (Failure failure) { throw failure; }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new Failure("credential_renewal_interrupted", "Credential renewal interrupted; no inference request was sent");
        } catch (IOException | RuntimeException invalid) {
            throw new Failure("credential_file_unavailable", "Cannot unlock or use the credential file; check the password, file integrity, permissions and required fields. No environment credential fallback was used");
        } finally { if (password != null) Arrays.fill(password, '\0'); }
    }

    private static FileLock tryLock(FileChannel channel) throws IOException {
        try {
            FileLock lock = channel.tryLock();
            if (lock == null) throw new Failure("credential_busy", "Another process is using this credential file; retry after it finishes");
            return lock;
        } catch (OverlappingFileLockException busy) {
            throw new Failure("credential_busy", "Another request is renewing this credential file; retry after it finishes");
        }
    }

    private Map<String,Object> renew(Map<String,Object> record, List<String> secrets, Path project) throws IOException, InterruptedException {
        String renewal = text(record.get("renewal"));
        String contentType = "application/x-www-form-urlencoded";
        URI uri;
        var request = new LinkedHashMap<String,Object>();
        if (renewal.equals("anthropic_wif")) {
            uri = URI.create("https://api.anthropic.com/v1/oauth/token");
            Path identity = Path.of(text(record.get("identity_token_file")));
            if (!identity.isAbsolute() || !Files.isRegularFile(identity, LinkOption.NOFOLLOW_LINKS) || Files.size(identity) > 16_384)
                throw new Failure("credential_identity_unavailable", "Provide a current IdP identity token file for Claude federation");
            identity = StudyCredentialVault.external(identity, project);
            String assertion = Files.readString(identity).strip();
            if (!assertion.matches("[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+")) throw new Failure("credential_identity_unavailable", "Claude federation identity token is empty");
            secrets.add(assertion);
            request.put("grant_type","urn:ietf:params:oauth:grant-type:jwt-bearer");
            request.put("assertion",assertion);
            for (String key : List.of("federation_rule_id","organization_id","service_account_id","workspace_id"))
                if (record.containsKey(key)) request.put(key, record.get(key));
            contentType = "application/json";
        } else {
            uri = URI.create(renewal.equals("openai") ? "https://auth.openai.com/api/accounts/oauth/token" : "https://oauth2.googleapis.com/token");
            request.put("grant_type","refresh_token");
            request.put("client_id",record.get("client_id")); request.put("refresh_token",record.get("refresh_token"));
            if (renewal.equals("openai")) request.put("resource","https://api.openai.com/v1");
            else if (record.containsKey("client_secret")) request.put("client_secret",record.get("client_secret"));
        }
        Reply reply = transport.send(uri, contentType, contentType.equals("application/json") ? StudyAssistance.json(request) : form(request));
        if (reply.status() != 200) throw new Failure("credential_renewal_denied", "Provider rejected credential renewal (HTTP " + reply.status() + "); saved credentials were retained. Reauthorize or inspect the provider account. No inference request was sent");
        Map<String,Object> response;
        try { response = StudyCredentialVault.object(SimpleJson.parse(reply.body())); }
        catch (IOException invalid) { throw new Failure("credential_renewal_invalid", "Provider returned an invalid token response; saved credentials were retained"); }
        String token = text(response.get("access_token"));
        Object duration = response.get("expires_in");
        if (token.isBlank() || token.length() > 32768 || !(duration instanceof Number n) || n.doubleValue() != n.longValue()
                || n.longValue() < 60 || n.longValue() > 31_536_000
                || !"Bearer".equalsIgnoreCase(text(response.get("token_type")))
                || renewal.equals("openai") && text(response.get("refresh_token")).isBlank())
            throw new Failure("credential_renewal_invalid", "Provider returned incomplete token credentials; saved credentials were retained");
        var updated = new LinkedHashMap<>(record);
        updated.put("access_token",token);
        updated.put("expires_at",clock.instant().plusSeconds(n.longValue()).toString());
        for (String key : List.of("refresh_token","id_token"))
            if (!text(response.get(key)).isBlank()) updated.put(key,response.get(key));
        updated.remove("earliest_refresh_at");
        if (response.containsKey("earliest_refresh_at")) {
            Object raw = response.get("earliest_refresh_at");
            try {
                Instant earliest = raw instanceof Number number ? Instant.ofEpochSecond(number.longValue()) : Instant.parse(text(raw));
                updated.put("earliest_refresh_at", earliest.toString());
            } catch (RuntimeException invalid) {
                // Persist the latest rotating token even when optional scheduling metadata is unusable.
                updated.put("earliest_refresh_at", updated.get("expires_at"));
            }
        }
        validate(updated);
        return updated;
    }

    static void validate(Map<String,Object> r) throws IOException {
        String provider = required(r,"provider"), auth = required(r,"auth"), renewal = text(r.getOrDefault("renewal","none"));
        if (!Set.of("openai","gemini","anthropic","xai","openai_compatible").contains(provider)
                || !Set.of("api_key","oauth").contains(auth) || !Set.of("none","openai","google","anthropic_wif").contains(renewal))
            throw new IOException("Unsupported credential provider or renewal mode");
        if (!endpoint(provider,text(r.get("endpoint"))).equals(required(r,"endpoint")))
            throw new IOException("Credential endpoint is invalid");
        if (auth.equals("oauth") && provider.equals("xai")) throw new IOException("Grok uses API keys; no OAuth refresh grant is configured");
        if (!renewal.equals("none") && !auth.equals("oauth")) throw new IOException("API keys cannot use OAuth renewal");
        if (!renewal.equals("none") && !renewal.equals(provider.equals("gemini") ? "google" : provider.equals("anthropic") ? "anthropic_wif" : provider))
            throw new IOException("Renewal flow does not match the credential provider");
        if (!renewal.equals("anthropic_wif")) required(r,"access_token");
        if (auth.equals("oauth") && !renewal.equals("anthropic_wif") && instant(r.get("expires_at")) == null)
            throw new IOException("OAuth credentials require expires_at");
        if (r.containsKey("expires_at")) instant(r.get("expires_at"));
        if (r.containsKey("earliest_refresh_at")) instant(r.get("earliest_refresh_at"));
        if (Set.of("openai","google").contains(renewal)) { required(r,"refresh_token"); required(r,"client_id"); }
        if (renewal.equals("anthropic_wif")) {
            for (String key : List.of("identity_token_file","federation_rule_id","organization_id","service_account_id")) required(r,key);
            if (!Path.of(text(r.get("identity_token_file"))).isAbsolute()) throw new IOException("Identity token file must be absolute");
            if (!r.containsKey("expires_at")) r.put("expires_at", Instant.EPOCH.toString());
        }
        r.putIfAbsent("renewal","none");
        for (String key : List.of("access_token","refresh_token","client_secret","client_id"))
            if (r.containsKey(key) && (text(r.get(key)).length() > 32768 || text(r.get(key)).chars().anyMatch(Character::isISOControl)))
                throw new IOException("Invalid credential text");
    }

    static String endpoint(String provider, String supplied) throws IOException {
        return switch (provider) {
            case "openai" -> "https://api.openai.com/v1/responses";
            case "gemini" -> "https://generativelanguage.googleapis.com/v1beta";
            case "anthropic" -> "https://api.anthropic.com/v1/messages";
            case "xai" -> "https://api.x.ai/v1/chat/completions";
            case "openai_compatible" -> StudyAssistance.compatibleEndpoint(supplied).toString();
            default -> throw new IOException("Unsupported credential provider");
        };
    }

    private static List<String> secrets(Map<String,Object> record) {
        var values = new ArrayList<String>();
        for (String key : List.of("access_token","refresh_token","client_secret","id_token"))
            if (!text(record.get(key)).isBlank()) values.add(text(record.get(key)));
        return values;
    }
    private static String required(Map<String,Object> record, String key) throws IOException {
        String value = text(record.get(key));
        if (value.isBlank()) throw new IOException("Credential field is required: " + key);
        return value;
    }
    private static Instant instant(Object value) throws IOException {
        if (value == null) return null;
        try { return Instant.parse(text(value)); }
        catch (RuntimeException bad) { throw new IOException("Credential expiry must be an ISO-8601 UTC timestamp"); }
    }
    private static String form(Map<String,Object> values) {
        return String.join("&",values.entrySet().stream().map(e -> URLEncoder.encode(e.getKey(),StandardCharsets.UTF_8)+"="+URLEncoder.encode(text(e.getValue()),StandardCharsets.UTF_8)).toList());
    }
    private static Reply send(URI endpoint, String contentType, String body) throws IOException, InterruptedException {
        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.NEVER).build();
        var request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(30)).header("Content-Type",contentType)
            .POST(HttpRequest.BodyPublishers.ofString(body,StandardCharsets.UTF_8)).build();
        var pending = client.sendAsync(request, ignored -> new StudyAssistance.BoundedBody());
        try {
            var response = pending.get(30,java.util.concurrent.TimeUnit.SECONDS);
            return new Reply(response.statusCode(),new String(response.body(),StandardCharsets.UTF_8));
        } catch (java.util.concurrent.TimeoutException expired) {
            pending.cancel(true); throw new IOException("Credential renewal timed out");
        } catch (java.util.concurrent.ExecutionException failed) {
            throw new IOException("Credential renewal unavailable");
        } catch (InterruptedException interrupted) { pending.cancel(true); throw interrupted; }
    }
    private static String text(Object value) { return value == null ? "" : String.valueOf(value); }
}
