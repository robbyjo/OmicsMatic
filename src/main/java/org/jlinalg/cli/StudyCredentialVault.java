/*
 * Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;

/** Versioned, authenticated, password-encrypted local credential storage. */
final class StudyCredentialVault {
    static final int ITERATIONS = 600_000;
    static final int MAX_BYTES = 131_072;
    private static final String FORMAT = "omicsmatic-credentials-v1";
    private static final SecureRandom RANDOM = new SecureRandom();
    private StudyCredentialVault() { }

    static byte[] encrypt(Map<String,Object> credentials, char[] password) throws IOException {
        checkPassword(password);
        byte[] salt = new byte[16], nonce = new byte[12];
        RANDOM.nextBytes(salt); RANDOM.nextBytes(nonce);
        var header = new LinkedHashMap<String,Object>();
        header.put("format", FORMAT); header.put("cipher", "AES-256-GCM");
        header.put("kdf", "PBKDF2-HMAC-SHA256"); header.put("iterations", ITERATIONS);
        header.put("salt", Base64.getEncoder().encodeToString(salt));
        header.put("nonce", Base64.getEncoder().encodeToString(nonce));
        byte[] plain = StudyAssistance.json(credentials).getBytes(StandardCharsets.UTF_8);
        if (plain.length > MAX_BYTES / 2) throw new IOException("Credential record is too large");
        byte[] key = null;
        try {
            key = derive(password, salt, ITERATIONS);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
            cipher.updateAAD(StudyAssistance.json(header).getBytes(StandardCharsets.UTF_8));
            header.put("ciphertext", Base64.getEncoder().encodeToString(cipher.doFinal(plain)));
            return (StudyAssistance.json(header) + "\n").getBytes(StandardCharsets.UTF_8);
        } catch (GeneralSecurityException failure) {
            throw new IOException("Required credential encryption is unavailable");
        } finally {
            Arrays.fill(plain, (byte)0);
            if (key != null) Arrays.fill(key, (byte)0);
        }
    }

    static Map<String,Object> read(Path file, char[] password) throws IOException {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) > MAX_BYTES)
            throw new IOException("Credential file is missing, linked, or too large");
        try {
            try (var input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
                return decrypt(input.readNBytes(MAX_BYTES + 1), password);
            }
        } catch (IOException | IllegalArgumentException failure) {
            throw new IOException("Cannot unlock credential file: wrong password, invalid format, or modified file");
        }
    }

    static Map<String,Object> decrypt(byte[] encoded, char[] password) throws IOException {
        checkPassword(password);
        if (encoded.length > MAX_BYTES) throw new IOException("Credential file is too large");
        var envelope = object(SimpleJson.parse(new String(encoded, StandardCharsets.UTF_8)));
        if (!envelope.keySet().equals(Set.of("format","cipher","kdf","iterations","salt","nonce","ciphertext"))
                || !FORMAT.equals(envelope.get("format")) || !"AES-256-GCM".equals(envelope.get("cipher"))
                || !"PBKDF2-HMAC-SHA256".equals(envelope.get("kdf")))
            throw new IOException("Unsupported credential format");
        Object work = envelope.get("iterations");
        if (!(work instanceof Number n) || n.longValue() < ITERATIONS || n.longValue() > 2_000_000
                || n.doubleValue() != n.longValue()) throw new IOException("Invalid credential KDF cost");
        byte[] salt = Base64.getDecoder().decode(String.valueOf(envelope.get("salt")));
        byte[] nonce = Base64.getDecoder().decode(String.valueOf(envelope.get("nonce")));
        byte[] encrypted = Base64.getDecoder().decode(String.valueOf(envelope.remove("ciphertext")));
        if (salt.length != 16 || nonce.length != 12 || encrypted.length < 16) throw new IOException("Invalid credential envelope");
        byte[] key = null, plain = null;
        try {
            key = derive(password, salt, n.intValue());
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
            cipher.updateAAD(StudyAssistance.json(envelope).getBytes(StandardCharsets.UTF_8));
            plain = cipher.doFinal(encrypted);
            return object(SimpleJson.parse(new String(plain, StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException failure) {
            throw new IOException("Cannot unlock credential file");
        } finally {
            if (plain != null) Arrays.fill(plain, (byte)0);
            if (key != null) Arrays.fill(key, (byte)0);
        }
    }

    static void create(Path file, Map<String,Object> credentials, char[] password) throws IOException {
        byte[] bytes = encrypt(credentials, password);
        Path target = file.toAbsolutePath().normalize();
        Files.createDirectories(target.getParent());
        boolean created = false;
        // CREATE_NEW never replaces an existing credential, even when another process races us.
        try (FileChannel channel = FileChannel.open(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            created = true;
            restrict(target);
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) channel.write(buffer);
            channel.force(true);
        } catch (IOException | RuntimeException failure) {
            if (created) Files.deleteIfExists(target);
            throw failure;
        } finally { Arrays.fill(bytes, (byte)0); }
    }

    static void replace(Path file, Map<String,Object> credentials, char[] password) throws IOException {
        byte[] bytes = encrypt(credentials, password);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Credential replacement refuses missing files or links");
        Path temp = Files.createTempFile(file.getParent(), ".omicsmatic-credential-", ".tmp");
        try {
            restrict(temp);
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            // No non-atomic fallback: a rotating refresh token must be durably replaced as one record.
            Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Arrays.fill(bytes, (byte)0);
            Files.deleteIfExists(temp);
        }
    }

    static Path external(Path file, Path project) throws IOException {
        if (!file.isAbsolute()) throw new IOException("credential_file must be an absolute path outside the study project");
        Path actual = file.toRealPath();
        if (Files.isSymbolicLink(file) || actual.startsWith(project.toRealPath()))
            throw new IOException("Credential files must be outside the study project and cannot be symbolic links");
        return actual;
    }

    static void restrict(Path path) throws IOException {
        var posix = Files.getFileAttributeView(path, PosixFileAttributeView.class);
        if (posix != null) {
            posix.setPermissions(PosixFilePermissions.fromString("rw-------"));
            return;
        }
        var acl = Files.getFileAttributeView(path, AclFileAttributeView.class);
        if (acl == null) throw new IOException("Filesystem cannot restrict credential file permissions");
        acl.setAcl(List.of(AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(Files.getOwner(path))
            .setPermissions(EnumSet.allOf(AclEntryPermission.class)).build()));
    }

    static Map<String,Object> object(Object raw) throws IOException {
        if (!(raw instanceof Map<?,?> map)) throw new IOException("Expected credential object");
        var value = new LinkedHashMap<String,Object>();
        for (var e : map.entrySet()) value.put(String.valueOf(e.getKey()), e.getValue());
        return value;
    }

    private static byte[] derive(char[] password, byte[] salt, int iterations) throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, 256);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
        finally { spec.clearPassword(); }
    }
    private static void checkPassword(char[] password) throws IOException {
        if (password == null || password.length < 12 || password.length > 1024)
            throw new IOException("Use a credential-file passphrase of 12 to 1024 characters");
    }
}
