/*
 * Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.*;
import java.util.*;

/** Local credential management: secrets are read only from a masked console or named variables. */
final class StudyCredentialCli {
    private StudyCredentialCli() { }
    static int run(String action, String[] args, PrintStream output) throws IOException {
        Set<String> allowed = action.equals("credentials-check") ? Set.of("file","password-env") : Set.of(
            "out","provider","auth","endpoint","password-env","token-env","refresh-token-env","client-secret-env",
            "client-id","expires-at","earliest-refresh-at","renewal","identity-token-file","federation-rule-id",
            "organization-id","service-account-id","workspace-id");
        var options = new LinkedHashMap<String,String>();
        for (int i=0;i<args.length;i++) {
            String option = args[i];
            if (!option.startsWith("--") || !allowed.contains(option.substring(2)) || i+1>=args.length || args[i+1].startsWith("--"))
                throw new IOException("Unknown or missing credential option; see study help. Secrets must not be passed as arguments");
            if (options.putIfAbsent(option.substring(2),args[++i]) != null) throw new IOException("Duplicate credential option");
        }
        Path file = Path.of(required(options,action.equals("credentials-check") ? "file" : "out")).toAbsolutePath().normalize();
        char[] password = null;
        try {
            password = StudyCredentials.password(file,options.get("password-env"));
            if (action.equals("credentials-check")) {
                var record = StudyCredentialVault.read(file,password);
                StudyCredentials.validate(record);
                var status = new LinkedHashMap<String,Object>();
                for (String key : List.of("provider","auth","renewal","expires_at")) if (record.containsKey(key)) status.put(key,record.get(key));
                output.println("Credential file decrypted and integrity checked locally.");
                output.println(StudyAssistance.json(status));
                output.println("No token displayed or network request made. This does not verify provider authorization.");
                return 0;
            }
            if (System.console()!=null && !options.containsKey("password-env")) {
                char[] confirmation=System.console().readPassword("Confirm credential-file passphrase: ");
                try { if (!Arrays.equals(password,confirmation)) throw new IOException("Passphrases do not match"); }
                finally { if (confirmation!=null) Arrays.fill(confirmation,'\0'); }
            }
            String provider = required(options,"provider"), auth = options.getOrDefault("auth","api_key");
            var record = new LinkedHashMap<String,Object>();
            record.put("provider",provider); record.put("auth",auth);
            record.put("endpoint",StudyCredentials.endpoint(provider,options.getOrDefault("endpoint","")));
            if (!provider.equals("openai_compatible") && options.containsKey("endpoint")) throw new IOException("Custom endpoints require openai_compatible");
            String renewal=options.getOrDefault("renewal","none"); record.put("renewal",renewal);
            if (!renewal.equals("anthropic_wif")) record.put("access_token",secret(options.get("token-env"),"API key or OAuth access token"));
            for (String option : List.of("client-id","expires-at","earliest-refresh-at","identity-token-file","federation-rule-id","organization-id","service-account-id","workspace-id"))
                if (options.containsKey(option)) record.put(option.replace('-','_'),options.get(option));
            if (Set.of("openai","google").contains(renewal)) record.put("refresh_token",secret(options.get("refresh-token-env"),"OAuth refresh token"));
            else if (options.containsKey("refresh-token-env")) throw new IOException("Refresh tokens require renewal openai or google");
            if (options.containsKey("client-secret-env")) {
                if (!renewal.equals("google")) throw new IOException("A client secret is only supported for Google renewal");
                record.put("client_secret",secret(options.get("client-secret-env"),"OAuth client secret"));
            }
            StudyCredentials.validate(record);
            StudyCredentialVault.create(file,record,password);
            output.println("Saved password-encrypted credential file: "+file);
            output.println("Existing files are never overwritten. Keep the passphrase separate and keep this file outside study projects and source control.");
            return 0;
        } finally { if (password!=null) Arrays.fill(password,'\0'); }
    }
    private static String required(Map<String,String> options,String key) throws IOException {
        String found=options.get(key);
        if (found==null||found.isBlank()) throw new IOException("Missing --"+key);
        return found;
    }
    private static String secret(String environment,String label) throws IOException {
        if (environment!=null) {
            if (!environment.matches("[A-Za-z_][A-Za-z0-9_]*")) throw new IOException("Credential input must name an environment variable");
            String value=System.getenv(environment);
            if (value==null||value.isBlank()) throw new IOException("Credential input environment variable is unset");
            return value;
        }
        var console=System.console();
        if (console==null) throw new IOException("No secure console; supply the named input environment variable option");
        char[] value=console.readPassword("%s: ",label);
        if (value==null) throw new IOException("Credential input cancelled");
        try { return new String(value); } finally { Arrays.fill(value,'\0'); }
    }
}
