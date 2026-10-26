/*
 * Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.jlinalg.study.StudyOperationExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StudyCredentialsTest {
    @TempDir Path root;
    static final String PASS = "a long synthetic passphrase";
    static final Instant NOW = Instant.parse("2026-10-26T18:00:00Z");
    static final Clock CLOCK = Clock.fixed(NOW,ZoneOffset.UTC);

    Map<String,Object> record(String provider,String auth,String renewal) throws Exception {
        var r=new LinkedHashMap<String,Object>();
        r.put("provider",provider);r.put("auth",auth);r.put("renewal",renewal);
        r.put("endpoint",StudyCredentials.endpoint(provider,"https://gateway.example/v1/chat/completions"));
        r.put("access_token","SYNTHETIC_ACCESS_SECRET");
        if(auth.equals("oauth"))r.put("expires_at",NOW.minusSeconds(10).toString());
        if(Set.of("openai","google").contains(renewal)) {r.put("client_id","issued-client");r.put("refresh_token","SYNTHETIC_REFRESH_SECRET");}
        return r;
    }
    Map<String,Object> config(Path file,Map<String,Object> r) {
        var c=new LinkedHashMap<String,Object>();
        c.put("provider",r.get("provider"));c.put("auth",r.get("auth"));c.put("credential_file",file.toString());
        if(r.get("provider").equals("openai_compatible"))c.put("endpoint",r.get("endpoint"));
        return c;
    }
    Path project() throws Exception {return Files.createDirectories(root.resolve("project"));}
    StudyCredentials client(StudyCredentials.Transport transport) {
        return new StudyCredentials((file,env)->PASS.toCharArray(),transport,CLOCK);
    }
    Path save(Map<String,Object> r) throws Exception {
        StudyCredentials.validate(r);Path file=root.resolve("private.credentials.json");
        StudyCredentialVault.create(file,r,PASS.toCharArray());return file;
    }

    @Test void encryptedRoundTripRandomnessPermissionsAndNoOverwrite() throws Exception {
        var r=record("openai","api_key","none");Path file=save(r);
        assertEquals(r,StudyCredentialVault.read(file,PASS.toCharArray()));
        String encoded=Files.readString(file);
        assertFalse(encoded.contains("SYNTHETIC_ACCESS_SECRET"));
        assertFalse(encoded.contains(PASS));
        assertFalse(encoded.contains("api.openai.com"));
        assertNotEquals(encoded,new String(StudyCredentialVault.encrypt(r,PASS.toCharArray()),java.nio.charset.StandardCharsets.UTF_8));
        assertThrows(Exception.class,()->StudyCredentialVault.create(file,r,PASS.toCharArray()));
        assertEquals(encoded,Files.readString(file));
        var posix=Files.getFileAttributeView(file,java.nio.file.attribute.PosixFileAttributeView.class);
        if(posix!=null)assertEquals("rw-------",java.nio.file.attribute.PosixFilePermissions.toString(posix.readAttributes().permissions()));
        else {
            var acl=Files.getFileAttributeView(file,java.nio.file.attribute.AclFileAttributeView.class).getAcl();
            assertEquals(1,acl.size());assertEquals(Files.getOwner(file),acl.get(0).principal());
        }
    }

    @Test void wrongPasswordTamperingBoundsAndDuplicateFieldsAreRejected() throws Exception {
        Path file=save(record("gemini","api_key","none"));
        assertThrows(Exception.class,()->StudyCredentialVault.read(file,"wrong long password".toCharArray()));
        String original=Files.readString(file);
        var envelope=StudyCredentialVault.object(SimpleJson.parse(original));
        for(String field:List.of("nonce","salt","ciphertext","iterations","format")) {
            var changed=new LinkedHashMap<>(envelope);
            if(field.equals("iterations"))changed.put(field,2_000_001);
            else if(field.equals("format"))changed.put(field,"unknown");
            else {
                byte[] bytes=Base64.getDecoder().decode(changed.get(field).toString());bytes[0]^=1;
                changed.put(field,Base64.getEncoder().encodeToString(bytes));
            }
            Files.writeString(file,StudyAssistance.json(changed));
            assertThrows(Exception.class,()->StudyCredentialVault.read(file,PASS.toCharArray()),field);
        }
        Files.writeString(file,original.replaceFirst("\\{","{\"format\":\"duplicate\","));
        assertThrows(Exception.class,()->StudyCredentialVault.read(file,PASS.toCharArray()));
        Files.writeString(file,"x".repeat(StudyCredentialVault.MAX_BYTES+1));
        assertThrows(Exception.class,()->StudyCredentialVault.read(file,PASS.toCharArray()));
    }

    @Test void googleRenewsAndPreservesNonRotatingRefreshToken() throws Exception {
        var r=record("gemini","oauth","google");r.put("client_secret","SYNTHETIC_CLIENT_SECRET");
        Path file=save(r);var calls=new AtomicInteger();
        var c=client((uri,type,body)->{
            calls.incrementAndGet();assertEquals("https://oauth2.googleapis.com/token",uri.toString());
            assertEquals("application/x-www-form-urlencoded",type);assertTrue(body.contains("grant_type=refresh_token"));
            assertTrue(body.contains("client_secret=SYNTHETIC_CLIENT_SECRET"));
            return new StudyCredentials.Reply(200,"{\"access_token\":\"NEW_ACCESS_SECRET\",\"expires_in\":3600,\"token_type\":\"Bearer\"}");
        });
        var result=c.load(config(file,r),project());
        assertEquals("NEW_ACCESS_SECRET",result.token());assertTrue(result.renewed());
        var updated=StudyCredentialVault.read(file,PASS.toCharArray());
        assertEquals("SYNTHETIC_REFRESH_SECRET",updated.get("refresh_token"));
        assertEquals(NOW.plusSeconds(3600).toString(),updated.get("expires_at"));
        assertFalse(c.load(config(file,r),project()).renewed());assertEquals(1,calls.get());
        assertFalse(result.toString().contains("SECRET"));
        assertEquals("[REDACTED_CREDENTIAL]",result.redact("SYNTHETIC_CLIENT_SECRET"));
    }

    @Test void openaiRotatesRefreshAndHonorsEarliestTime() throws Exception {
        var r=record("openai","oauth","openai");Path file=save(r);
        var c=client((uri,type,body)->{
            assertEquals("https://auth.openai.com/api/accounts/oauth/token",uri.toString());
            assertTrue(body.contains("resource=https%3A%2F%2Fapi.openai.com%2Fv1"));
            assertFalse(body.contains("scope="));
            return new StudyCredentials.Reply(200,"{\"access_token\":\"ROTATED_ACCESS\",\"refresh_token\":\"ROTATED_REFRESH\",\"expires_in\":3600,\"token_type\":\"Bearer\"}");
        });
        assertTrue(c.load(config(file,r),project()).renewed());
        assertEquals("ROTATED_REFRESH",StudyCredentialVault.read(file,PASS.toCharArray()).get("refresh_token"));
        var saved=StudyCredentialVault.read(file,PASS.toCharArray());
        saved.put("expires_at",NOW.plusSeconds(20).toString());saved.put("earliest_refresh_at",NOW.plusSeconds(10).toString());
        StudyCredentialVault.replace(file,saved,PASS.toCharArray());
        assertFalse(client((u,t,b)->{fail("No premature refresh");return null;}).load(config(file,r),project()).renewed());
    }

    @Test void refreshFailuresPreserveFileAndNeverFallback() throws Exception {
        var r=record("openai","oauth","openai");Path file=save(r);byte[] original=Files.readAllBytes(file);
        for(var reply:List.of(new StudyCredentials.Reply(401,"REFRESH_SECRET_DO_NOT_LOG"),
                new StudyCredentials.Reply(200,"{\"access_token\":\"PARTIAL\",\"expires_in\":3600,\"token_type\":\"Bearer\"}"),
                new StudyCredentials.Reply(200,"not JSON"))) {
            var failure=assertThrows(StudyCredentials.Failure.class,()->client((u,t,b)->reply).load(config(file,r),project()));
            assertFalse(failure.getMessage().contains("REFRESH_SECRET"));assertArrayEquals(original,Files.readAllBytes(file));
        }
    }

    @Test void bindingExpiryAndProjectLocationAreEnforced() throws Exception {
        var r=record("xai","api_key","none");r.put("expires_at",NOW.minusSeconds(1).toString());Path file=save(r);
        var c=client((u,t,b)->{fail("No renewal for API keys");return null;});
        assertEquals("credential_expired",assertThrows(StudyCredentials.Failure.class,()->c.load(config(file,r),project())).status);
        var mismatch=new LinkedHashMap<>(config(file,r));mismatch.put("provider","openai");
        assertEquals("credential_binding_mismatch",assertThrows(StudyCredentials.Failure.class,()->c.load(mismatch,project())).status);
        Path inside=project().resolve("secret.json");Files.copy(file,inside);
        assertThrows(StudyCredentials.Failure.class,()->c.load(config(inside,r),project()));
        assertThrows(Exception.class,()->StudyCredentialVault.external(Path.of("relative.json"),project()));
    }

    @Test void claudeFederationRereadsRotatingIdentityFile() throws Exception {
        var r=record("anthropic","oauth","anthropic_wif");
        Path identity=root.resolve("identity.jwt");Files.writeString(identity,"eyJhbGciOiJSUzI1NiJ9.FIRST_ASSERTION.signature");
        r.put("identity_token_file",identity.toString());r.put("federation_rule_id","fdrl_test");
        r.put("organization_id","organization");r.put("service_account_id","svac_test");
        Path file=save(r);var count=new AtomicInteger();
        var c=client((uri,type,body)->{
            assertEquals("https://api.anthropic.com/v1/oauth/token",uri.toString());assertEquals("application/json",type);
            assertTrue(body.contains("urn:ietf:params:oauth:grant-type:jwt-bearer"));
            assertTrue(body.contains(count.getAndIncrement()==0?"eyJhbGciOiJSUzI1NiJ9.FIRST_ASSERTION.signature":"eyJhbGciOiJSUzI1NiJ9.SECOND_ASSERTION.signature"));
            return new StudyCredentials.Reply(200,"{\"access_token\":\"CLAUDE_ACCESS\",\"expires_in\":3600,\"token_type\":\"Bearer\"}");
        });
        assertTrue(c.load(config(file,r),project()).renewed());
        var expired=StudyCredentialVault.read(file,PASS.toCharArray());expired.put("expires_at",NOW.toString());
        StudyCredentialVault.replace(file,expired,PASS.toCharArray());Files.writeString(identity,"eyJhbGciOiJSUzI1NiJ9.SECOND_ASSERTION.signature");
        assertTrue(c.load(config(file,r),project()).renewed());assertEquals(2,count.get());
    }

    @Test void refreshLockPreventsConcurrentRotatingTokenUse() throws Exception {
        var r=record("openai","oauth","openai");Path file=save(r);Path project=project();
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        var c=client((u,t,b)->{entered.countDown();release.await();return new StudyCredentials.Reply(200,"{\"access_token\":\"NEW\",\"refresh_token\":\"NEW_REFRESH\",\"expires_in\":3600,\"token_type\":\"Bearer\"}");});
        var pool=Executors.newSingleThreadExecutor();
        try {
            var first=pool.submit(()->c.load(config(file,r),project));
            assertTrue(entered.await(10,TimeUnit.SECONDS));
            assertEquals("credential_busy",assertThrows(StudyCredentials.Failure.class,()->c.load(config(file,r),project)).status);
            release.countDown();assertEquals("NEW",first.get(15,TimeUnit.SECONDS).token());
        } finally {release.countDown();pool.shutdownNow();}
    }

    StudyOperationExecutor.Context context(Map<String,Object> config,String name,Map<String,Object> projectMetadata,Map<String,Object> resources) throws Exception {
        config=new LinkedHashMap<>(config);config.put("enabled",true);config.put("next_steps",true);config.put("model","synthetic-model");
        return new StudyOperationExecutor.Context(project(),root.resolve(name),Map.of("type","advisor_review","hypothesis","H1"),
            Map.of("assistance",config,"project",projectMetadata,"resources",resources),Map.of(),Map.of());
    }

    @Test void encryptedAdaptersRedactAllSecretsAndOpenaiRequiresCompletedStream() throws Exception {
        for(String provider:List.of("openai","gemini","anthropic","xai")) {
            Path folder=Files.createDirectory(root.resolve(provider));
            var r=record(provider,provider.equals("openai")||provider.equals("gemini")?"oauth":"api_key","none");
            if(r.get("auth").equals("oauth"))r.put("expires_at",NOW.plusSeconds(3600).toString());
            r.put("refresh_token","DO_NOT_LOG_REFRESH");
            Path file=folder.resolve("saved.json");StudyCredentialVault.create(file,r,PASS.toCharArray());
            var ctx=context(config(file,r),"operation-"+provider,Map.of(),Map.of());
            var result=new StudyAssistance((uri,wire,key,body,timeout)->{
                assertEquals("SYNTHETIC_ACCESS_SECRET",key);
                String answer="{\"proposals\":[]}";
                String response=switch(provider) {
                    case "openai" -> {
                        assertTrue(body.contains("\"stream\":true"));
                        var part=Map.of("type","output_text","text",answer);
                        var output=List.of(Map.of("content",List.of(part)));
                        var complete=Map.of("status","completed","model","synthetic","echo","SYNTHETIC_ACCESS_SECRET DO_NOT_LOG_REFRESH","output",output);
                        yield "event: response.completed\ndata: "+StudyAssistance.json(Map.of("type","response.completed","response",complete))+"\n\n";
                    }
                    case "gemini" -> {
                        assertEquals("gemini_oauth",wire);
                        var content=Map.of("parts",List.of(Map.of("text",answer)));
                        var candidate=Map.of("finishReason","STOP","content",content);
                        yield StudyAssistance.json(Map.of("candidates",List.of(candidate)));
                    }
                    case "anthropic" -> {
                        assertTrue(body.contains("\"max_tokens\""));assertFalse(body.contains("\"temperature\""));
                        yield StudyAssistance.json(Map.of("stop_reason","end_turn","content",List.of(Map.of("type","text","text",answer))));
                    }
                    default -> {
                        var choice=Map.of("finish_reason","stop","message",Map.of("content",answer));
                        yield StudyAssistance.json(Map.of("choices",List.of(choice)));
                    }
                };
                return new StudyAssistance.Reply(200,response);
            },ignored->{fail("No environment-token fallback");return null;},client((u,t,b)->{fail("No renewal needed");return null;})).execute(ctx,Map.of("title","Synthetic aggregate"));
            assertEquals(StudyOperationExecutor.State.COMPLETE,result.state(),result.details().toString());
            try(var files=Files.walk(ctx.outputDirectory())) {
                for(Path path:files.filter(Files::isRegularFile).toList()) {
                    String saved=Files.readString(path);
                    assertFalse(saved.contains("SYNTHETIC_ACCESS_SECRET"));assertFalse(saved.contains("DO_NOT_LOG_REFRESH"));assertFalse(saved.contains(PASS));
                }
            }
        }
        assertThrows(Exception.class,()->StudyAssistance.completedStream("data: {\"type\":\"response.output_text.delta\",\"delta\":\"partial\"}\n\n"));
        assertThrows(Exception.class,()->StudyAssistance.completedStream("data: {\"type\":\"response.failed\"}\n\n"));
    }

    @Test void jsonEscapedSecretsAreAlsoRedacted() {
        String secret="synthetic"+(char)34+"quoted"+(char)92+"credential";
        var credential=new StudyCredentials.Credential(secret,List.of(secret),false);
        assertEquals(StudyAssistance.json(Map.of("echo","[REDACTED_CREDENTIAL]")),credential.redact(StudyAssistance.json(Map.of("echo",secret))));
        assertEquals("[REDACTED_CREDENTIAL]",credential.redact(secret));
    }

    @Test void credentialStudyValidationAcceptsProvidersAndRejectsAmbiguousSecrets() throws Exception {
        var source=Map.<String,Object>of("schema_version",1,
            "project",Map.of("id","synthetic","title","Synthetic","description","Credential validation fixture"),
            "datasets",List.of(Map.of("id","d","type","generic","stage","association_results","files",Map.of("results","missing.tsv"))),
            "hypotheses",List.of(Map.of("id","H1","description","Synthetic","inputs",Map.of("discovery",List.of("d")),"analysis",Map.of("mode","manual","goals",List.of()))));
        Path yaml=root.resolve("validation.yaml");
        for(String provider:List.of("openai","gemini","anthropic","xai")) {
            var study=new LinkedHashMap<>(source);
            var config=new LinkedHashMap<String,Object>(Map.of("enabled",true,"provider",provider,"model","fixture","auth",provider.equals("xai")?"api_key":"oauth","credential_file",root.resolve("external.json").toString()));
            study.put("assistance",config);
            Files.writeString(yaml,new org.yaml.snakeyaml.Yaml().dump(study));
            assertTrue(org.jlinalg.study.StudyValidator.validate(yaml,false).valid(),provider);
            config.put("api_key_env","DO_NOT_FALLBACK");
            Files.writeString(yaml,new org.yaml.snakeyaml.Yaml().dump(study));
            assertFalse(org.jlinalg.study.StudyValidator.validate(yaml,false).valid());
            config.remove("api_key_env");config.put("credential_file","relative.json");
            Files.writeString(yaml,new org.yaml.snakeyaml.Yaml().dump(study));
            assertFalse(org.jlinalg.study.StudyValidator.validate(yaml,false).valid());
        }
        var output=new java.io.ByteArrayOutputStream();var error=new java.io.ByteArrayOutputStream();
        assertEquals(2,StudyCli.run(new String[]{"credentials-save","--token","DO_NOT_ECHO"},new java.io.PrintStream(output),new java.io.PrintStream(error)));
        assertFalse(error.toString(java.nio.charset.StandardCharsets.UTF_8).contains("DO_NOT_ECHO"));
        error.reset();
        assertEquals(2,StudyCli.run(new String[]{"run","--credential-file",root.resolve("ignored.json").toString()},new java.io.PrintStream(output),new java.io.PrintStream(error)));
        assertTrue(error.toString(java.nio.charset.StandardCharsets.UTF_8).contains("in study.yaml"));
    }

    @Test void policiesPreventPasswordPromptsAndRenewal() throws Exception {
        var r=record("openai","oauth","openai");Path file=save(r);
        var never=new StudyCredentials((p,e)->{fail("No password read");return null;},(u,t,b)->{fail("No refresh");return null;},CLOCK);
        var assistance=new StudyAssistance((u,p,k,b,t)->{fail("No inference");return null;},k->{fail("No environment read");return null;},never);
        assertEquals("offline_policy",assistance.execute(context(config(file,r),"offline",Map.of(),Map.of("network","offline")),Map.of()).details().get("status"));
        assertEquals("policy_denied",assistance.execute(context(config(file,r),"denied",Map.of("data_policy",Map.of("remote_transmission","deny_all")),Map.of()),Map.of()).details().get("status"));
    }
}
