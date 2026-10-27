/*
 * Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.*;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.*;
import com.nimbusds.jwt.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class StudyOAuthTest {
    @TempDir Path root;
    static RSAKey key;
    static final String PASS="synthetic login passphrase";
    static final Instant NOW=Instant.parse("2026-10-26T18:00:00Z");
    static final Clock CLOCK=Clock.fixed(NOW,ZoneOffset.UTC);
    @BeforeAll static void signingKey() throws Exception {key=new RSAKeyGenerator(2048).keyID("test-key").generate();}
    static String sign(String issuer,String audience,String nonce,String subject,Instant expiry,RSAKey signing) throws Exception {
        var claims=new JWTClaimsSet.Builder().issuer(issuer).audience(audience).subject(subject)
            .issueTime(Date.from(NOW.minusSeconds(2))).expirationTime(Date.from(expiry)).claim("nonce",nonce)
            .claim("email","synthetic@example.test").claim("email_verified",true).build();
        var jwt=new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signing.getKeyID()).build(),claims);
        jwt.sign(new RSASSASigner(signing));return jwt.serialize();
    }
    static String keys() {return new JWKSet(key.toPublicJWK()).toString();}
    static String discovery(StudyOAuth.Provider p) {
        return StudyAssistance.json(Map.of("issuer",p.issuer(),"authorization_endpoint",p.authorize(),
            "token_endpoint",p.token(),"jwks_uri",p.jwks(),"id_token_signing_alg_values_supported",List.of("RS256")));
    }
    static StudyOAuth.Get get(StudyOAuth.Provider p) {
        return uri->{if(uri.toString().equals(p.jwks()))return keys();
            assertEquals(p.issuer()+"/.well-known/openid-configuration",uri.toString());return discovery(p);};
    }
    static HttpResponse<String> request(URI uri,String method) throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(uri).method(method,HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.ofString());
    }
    static void callback(URI auth,Map<String,String> extra) throws Exception {
        var q=StudyOAuth.query(auth.getRawQuery());
        var values=new LinkedHashMap<String,Object>();values.put("state",q.get("state"));values.put("code","SYNTHETIC_CODE");values.putAll(extra);
        var response=request(URI.create(q.get("redirect_uri")+"?"+StudyOAuth.form(values)),"GET");
        assertEquals(200,response.statusCode());
        assertEquals("no-store",response.headers().firstValue("Cache-Control").orElse(""));
        assertFalse(response.body().contains("SYNTHETIC_CODE"));
    }
    StudyOAuth.Attempt attempt(StudyOAuth.Provider p) {
        return StudyOAuth.attempt(p,p==StudyOAuth.OPENAI?"dynamic_agent_client":"google-client","google-secret","urn:uuid:host",
            URI.create("http://127.0.0.1:12345/auth/callback"),null);
    }
    Map<String,Object> tokens(StudyOAuth.Attempt a,String audience,String subject) throws Exception {
        var r=new LinkedHashMap<String,Object>();
        r.put("access_token","SYNTHETIC_ACCESS");r.put("refresh_token","SYNTHETIC_REFRESH");
        r.put("token_type","Bearer");r.put("expires_in",3600);r.put("scope",a.provider().scopes());
        r.put("id_token",sign(a.provider().issuer(),audience,a.nonce(),subject,NOW.plusSeconds(3600),key));
        return r;
    }
    @Test void pkceAndProviderParametersAreFreshAndCorrect() throws Exception {
        for(var provider:List.of(StudyOAuth.OPENAI,StudyOAuth.GOOGLE)) {
            var a=attempt(provider);var b=attempt(provider);
            var q=StudyOAuth.query(StudyOAuth.authorization(a).getRawQuery());
            assertNotEquals(a.state(),b.state());assertNotEquals(a.verifier(),b.verifier());assertNotEquals(a.nonce(),b.nonce());
            assertEquals("S256",q.get("code_challenge_method"));
            assertEquals(Base64.getUrlEncoder().withoutPadding().encodeToString(
                java.security.MessageDigest.getInstance("SHA-256").digest(a.verifier().getBytes(StandardCharsets.US_ASCII))),q.get("code_challenge"));
            assertEquals(a.redirect().toString(),q.get("redirect_uri"));assertFalse(a.toString().contains(a.verifier()));
            if(provider==StudyOAuth.OPENAI) {
                assertEquals("OmicsMatic",q.get("agent_name_hint"));assertEquals("https://api.openai.com/v1",q.get("resource"));
                assertEquals(a.hostId(),q.get("ext_agent_host_id"));
            } else {assertEquals("offline",q.get("access_type"));assertTrue(q.get("prompt").contains("consent"));}
        }
    }
    @Test void openaiExchangeUsesIssuedClientAndPersistsValidatedIdentity() throws Exception {
        var a=attempt(StudyOAuth.OPENAI);var response=tokens(a,"oaiapp_issued","account-1");
        var oauth=new StudyOAuth(get(a.provider()),(uri,type,body)->{
            assertEquals(a.provider().token(),uri.toString());assertEquals("application/x-www-form-urlencoded",type);
            var fields=StudyOAuth.query(body);
            assertEquals("authorization_code",fields.get("grant_type"));assertEquals("oaiapp_issued",fields.get("client_id"));
            assertEquals(a.verifier(),fields.get("code_verifier"));assertEquals(a.redirect().toString(),fields.get("redirect_uri"));
            assertEquals("https://api.openai.com/v1",fields.get("resource"));assertFalse(fields.containsKey("client_secret"));
            return new StudyCredentials.Reply(200,StudyAssistance.json(response));
        },CLOCK);
        var saved=oauth.finish(a,Map.of("state",a.state(),"code","CODE","client_id","oaiapp_issued"));
        assertEquals("account-1",saved.get("subject"));assertEquals(true,saved.get("identity_verified"));
        assertEquals("oaiapp_issued",saved.get("client_id"));assertEquals(a.hostId(),saved.get("ext_agent_host_id"));
        assertEquals(NOW.plusSeconds(3600).toString(),saved.get("expires_at"));
    }
    @Test void googleRequiresBearerAndRefreshTokensAndHonorsActualScopes() throws Exception {
        var a=attempt(StudyOAuth.GOOGLE);
        for(String defect:List.of("refresh_token","token_type","scope","expires_in","access_token")) {
            var response=tokens(a,a.clientId(),"subject");
            switch(defect) {
                case "refresh_token","access_token"->response.remove(defect);
                case "token_type"->response.put(defect,"DPoP");
                case "scope"->response.put(defect,"openid email");
                case "expires_in"->response.put(defect,1.5);
            }
            var oauth=new StudyOAuth(get(a.provider()),(u,t,b)->new StudyCredentials.Reply(200,StudyAssistance.json(response)),CLOCK);
            assertThrows(IOException.class,()->oauth.finish(a,Map.of("state",a.state(),"code","CODE")));
        }
        var response=tokens(a,a.clientId(),"subject");response.remove("scope");
        var oauth=new StudyOAuth(get(a.provider()),(u,t,b)->{
            assertEquals("google-secret",StudyOAuth.query(b).get("client_secret"));
            return new StudyCredentials.Reply(200,StudyAssistance.json(response));
        },CLOCK);
        assertEquals("google",oauth.finish(a,Map.of("state",a.state(),"code","CODE")).get("renewal"));
    }
    @Test void stateErrorsAndClientMixupsNeverExchangeACode() throws Exception {
        var a=attempt(StudyOAuth.OPENAI);
        var oauth=new StudyOAuth(uri->{fail("No discovery");return "";},(u,t,b)->{fail("No code exchange");return null;},CLOCK);
        for(var callback:List.of(Map.of("state","wrong","code","CODE"),Map.of("state",a.state(),"error","SECRET_ERROR"),
                Map.of("state",a.state(),"code","CODE"),Map.of("state",a.state(),"code","CODE","client_id","dynamic_agent_client"),
                Map.of("state",a.state(),"code","CODE","client_id","oaiapp_ok","iss","https://attacker.example")))
            assertFalse(assertThrows(IOException.class,()->oauth.finish(a,callback)).getMessage().contains("SECRET"));
        var returning=new StudyOAuth.Attempt(a.provider(),"oaiapp_original","",a.hostId(),a.state(),a.nonce(),a.verifier(),a.redirect(),null);
        assertThrows(IOException.class,()->oauth.finish(returning,Map.of("state",a.state(),"code","CODE","client_id","oaiapp_other")));
    }
    @Test void badSignaturesIssuersAudiencesNoncesExpiriesAndKeyIdsAreRejected() throws Exception {
        var other=new RSAKeyGenerator(2048).keyID("test-key").generate();
        for(String token:List.of(
                sign(StudyOAuth.OPENAI.issuer(),"client","nonce","sub",NOW.plusSeconds(600),other),
                sign("https://attacker.example","client","nonce","sub",NOW.plusSeconds(600),key),
                sign(StudyOAuth.OPENAI.issuer(),"wrong","nonce","sub",NOW.plusSeconds(600),key),
                sign(StudyOAuth.OPENAI.issuer(),"client","wrong","sub",NOW.plusSeconds(600),key),
                sign(StudyOAuth.OPENAI.issuer(),"client","nonce","sub",NOW.minusSeconds(60),key)))
            assertThrows(IOException.class,()->StudyOAuth.verifyIdentity(StudyOAuth.OPENAI,token,"client","nonce",keys(),List.of("RS256"),CLOCK));
        String valid=sign(StudyOAuth.OPENAI.issuer(),"client","nonce","sub",NOW.plusSeconds(600),key);
        assertThrows(IOException.class,()->StudyOAuth.verifyIdentity(StudyOAuth.OPENAI,valid,"client","nonce","{\"keys\":[]}",List.of("RS256"),CLOCK));
        assertThrows(IOException.class,()->StudyOAuth.verifyIdentity(StudyOAuth.OPENAI,valid,"client","nonce",keys(),List.of("HS256"),CLOCK));
        assertEquals("sub",StudyOAuth.verifyIdentity(StudyOAuth.OPENAI,valid,"client","nonce",keys(),List.of("RS256"),CLOCK).get("subject"));
    }
    @Test void duplicateJwtFieldsAndUnsignedTokensAreRejected() throws Exception {
        var duplicate=new JWSObject(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test-key").build(),
            new Payload("{\"iss\":\"https://auth.openai.com\",\"iss\":\"https://attacker.example\"}"));
        duplicate.sign(new RSASSASigner(key));
        for(String token:List.of(duplicate.serialize(),"eyJhbGciOiJub25lIn0.e30."))
            assertThrows(IOException.class,()->StudyOAuth.verifyIdentity(StudyOAuth.OPENAI,token,"client","nonce",keys(),List.of("RS256"),CLOCK));
    }
    @Test void callbackRejectsUnsolicitedAndDuplicateFieldsAndIsOneUse() throws Exception {
        try(var callback=new StudyOAuthCallback(0,"state")) {
            String url=callback.redirect().toString();
            assertEquals("127.0.0.1",callback.redirect().getHost());
            assertEquals(400,request(URI.create(url+"?state=wrong&code=x"),"GET").statusCode());
            assertEquals(400,request(URI.create(url+"?state=state&state=state&code=x"),"GET").statusCode());
            assertEquals(404,request(URI.create(url+"/?state=state&code=x"),"GET").statusCode());
            assertEquals(405,request(URI.create(url+"?state=state&code=x"),"POST").statusCode());
            assertEquals(200,request(URI.create(url+"?state=state&code=CODE"),"GET").statusCode());
            assertEquals("CODE",callback.await(Duration.ofSeconds(1)).get("code"));
            assertEquals(409,request(URI.create(url+"?state=state&code=REPLAY"),"GET").statusCode());
        }
    }
    @Test void callbackTimeoutAndDenialAreBounded() throws Exception {
        try(var callback=new StudyOAuthCallback(0,"state")) {
            assertThrows(IOException.class,()->callback.await(Duration.ofMillis(30)));
            assertEquals(200,request(URI.create(callback.redirect()+"?state=state&error=access_denied"),"GET").statusCode());
            assertEquals("access_denied",callback.await(Duration.ofSeconds(1)).get("error"));
        }
    }
    @Test void hostIdIsStableAndInvalidOrLinkedFilesAreNotReplaced() throws Exception {
        Path file=root.resolve("host.json");String id=StudyLoginCli.hostId(file);
        assertEquals(id,StudyLoginCli.hostId(file));assertTrue(id.startsWith("urn:uuid:"));
        Files.writeString(file,"{\"host_id\":\"INVALID\"}");
        assertThrows(IOException.class,()->StudyLoginCli.hostId(file));assertTrue(Files.readString(file).contains("INVALID"));
    }
    @Test void registrationMustBeADesktopClientAndDoesNotChooseEndpoints() throws Exception {
        Path file=root.resolve("client.json");
        Files.writeString(file,"{\"web\":{\"client_id\":\"wrong-type\"}}");
        assertThrows(IOException.class,()->StudyLoginCli.googleClient(file));
        Files.writeString(file,"{\"installed\":{\"client_id\":\"google-client\",\"client_secret\":\"secret\",\"token_uri\":\"https://attacker.example\"}}");
        assertEquals("google-client",StudyLoginCli.googleClient(file).get("client_id"));
        assertEquals("https://oauth2.googleapis.com/token",StudyOAuth.GOOGLE.token());
    }
    @Test void cliCompletesRealLoopbackAndEncryptedSaveWithoutTokenOutput() throws Exception {
        Path file=root.resolve("openai.credentials.json");var auth=new AtomicReference<Map<String,String>>();
        var oauth=new StudyOAuth(get(StudyOAuth.OPENAI),(u,t,b)->{
            var q=auth.get();
            try {
                var response=Map.of("access_token","SYNTHETIC_ACCESS","refresh_token","SYNTHETIC_REFRESH",
                    "token_type","Bearer","expires_in",3600,"scope",StudyOAuth.OPENAI.scopes(),
                    "id_token",sign(StudyOAuth.OPENAI.issuer(),"oaiapp_test",q.get("nonce"),"subject",NOW.plusSeconds(3600),key));
                return new StudyCredentials.Reply(200,StudyAssistance.json(response));
            } catch(Exception e) {throw new IOException("Synthetic token fixture failed");}
        },CLOCK);
        var bytes=new ByteArrayOutputStream();
        int code=StudyLoginCli.run(new String[]{"--provider","openai","--out",file.toString(),"--host-id-file",root.resolve("host.json").toString()},
            new PrintStream(bytes),oauth,uri->{auth.set(StudyOAuth.query(uri.getRawQuery()));callback(uri,Map.of("client_id","oaiapp_test"));},(f,e)->PASS.toCharArray());
        assertEquals(0,code);assertTrue(Files.exists(file));
        var saved=StudyCredentialVault.read(file,PASS.toCharArray());assertEquals("subject",saved.get("subject"));
        for(String secret:List.of("SYNTHETIC_ACCESS","SYNTHETIC_REFRESH",PASS,String.valueOf(saved.get("id_token")))) {
            assertFalse(bytes.toString(StandardCharsets.UTF_8).contains(secret));assertFalse(Files.readString(file).contains(secret));
        }
        assertTrue(bytes.toString().contains("credential_file:"));assertTrue(bytes.toString().contains("Continue with ChatGPT"));
        byte[] original=Files.readAllBytes(file);
        assertThrows(IOException.class,()->StudyLoginCli.run(new String[]{"--out",file.toString()},new PrintStream(bytes),oauth,u->fail("No browser"),(f,e)->{fail("No password");return null;}));
        assertArrayEquals(original,Files.readAllBytes(file));
    }
    @Test void providerRestrictionsAndInvalidOptionsFailBeforeOpeningBrowserOrPrompting() throws Exception {
        for(String provider:List.of("anthropic","xai"))
            assertThrows(IOException.class,()->StudyLoginCli.run(new String[]{"--provider",provider,"--auth","oauth","--out",root.resolve(provider+".json").toString()},
                System.out,new StudyOAuth(),u->fail("No browser"),(f,e)->{fail("No password");return null;}));
        for(String[] options:List.of(new String[]{"--port","-1"},new String[]{"--port","65536"},new String[]{"--provider","openai","--provider","gemini"},new String[]{"--unknown","x"}))
            assertThrows(IOException.class,()->StudyLoginCli.run(options,System.out,new StudyOAuth(),u->fail("No browser"),(f,e)->{fail("No password");return null;}));
    }
    @Test void scopeLossSavesRotatedTokensBeforeBlockingInference() throws Exception {
        Path file=root.resolve("scope.credentials.json"),project=Files.createDirectory(root.resolve("project"));
        var r=new LinkedHashMap<String,Object>(Map.of("provider","openai","auth","oauth","renewal","openai",
            "endpoint",StudyCredentials.endpoint("openai",""),"access_token","OLD","refresh_token","OLD_REFRESH",
            "client_id","issued","expires_at",NOW.minusSeconds(1).toString(),"identity_verified",true,"scopes",List.of("openid")));
        StudyCredentialVault.create(file,r,PASS.toCharArray());
        var c=new StudyCredentials((f,e)->PASS.toCharArray(),(u,t,b)->new StudyCredentials.Reply(200,
            "{\"access_token\":\"LATEST_ACCESS\",\"refresh_token\":\"LATEST_REFRESH\",\"expires_in\":3600,\"token_type\":\"Bearer\",\"scope\":\"openid\"}"),CLOCK);
        var failure=assertThrows(StudyCredentials.Failure.class,()->c.load(Map.of("provider","openai","auth","oauth","credential_file",file.toString()),project));
        assertEquals("credential_scope_denied",failure.status);
        assertEquals("LATEST_REFRESH",StudyCredentialVault.read(file,PASS.toCharArray()).get("refresh_token"));
    }

    @Test void geminiCliEncryptsClientSecretWithoutTouchingAdc() throws Exception {
        Path client=root.resolve("desktop.json"),file=root.resolve("gemini.credentials.json");
        Files.writeString(client,"{\"installed\":{\"client_id\":\"google-client\",\"client_secret\":\"SYNTHETIC_CLIENT_SECRET\"}}");
        var authorization=new AtomicReference<Map<String,String>>();
        var oauth=new StudyOAuth(get(StudyOAuth.GOOGLE),(u,t,b)->{
            try {
                assertEquals("SYNTHETIC_CLIENT_SECRET",StudyOAuth.query(b).get("client_secret"));
                return new StudyCredentials.Reply(200,StudyAssistance.json(Map.of("access_token","SYNTHETIC_ACCESS",
                    "refresh_token","SYNTHETIC_REFRESH","token_type","Bearer","expires_in",3600,"scope",StudyOAuth.GOOGLE.scopes(),
                    "id_token",sign("accounts.google.com","google-client",authorization.get().get("nonce"),"google-user",NOW.plusSeconds(3600),key))));
            } catch(Exception e) {throw new IOException("Synthetic fixture error");}
        },CLOCK);
        var bytes=new ByteArrayOutputStream();
        StudyLoginCli.run(new String[]{"--provider","gemini","--client-json",client.toString(),"--out",file.toString()},new PrintStream(bytes),
            oauth,uri->{authorization.set(StudyOAuth.query(uri.getRawQuery()));assertEquals("accounts.google.com",uri.getHost());assertEquals("/",URI.create(authorization.get().get("redirect_uri")).getPath());callback(uri,Map.of());},(f,e)->PASS.toCharArray());
        var record=StudyCredentialVault.read(file,PASS.toCharArray());
        assertEquals("SYNTHETIC_CLIENT_SECRET",record.get("client_secret"));assertEquals("google-user",record.get("subject"));
        assertFalse(bytes.toString().contains("SYNTHETIC_CLIENT_SECRET"));assertFalse(Files.readString(file).contains("SYNTHETIC_CLIENT_SECRET"));
    }
    @Test void reauthorizationRejectsDifferentAccountAndPreservesOriginalFile() throws Exception {
        Path old=root.resolve("old.json"),fresh=root.resolve("new.json");
        var previous=new LinkedHashMap<String,Object>();
        previous.putAll(Map.of("provider","openai","auth","oauth","renewal","openai","endpoint",StudyCredentials.endpoint("openai",""),
            "access_token","OLD","refresh_token","OLD_REFRESH","client_id","oaiapp_saved","expires_at",NOW.plusSeconds(3600).toString()));
        previous.putAll(Map.of("identity_verified",true,"issuer",StudyOAuth.OPENAI.issuer(),"subject","original-user","scopes",List.of("openid")));
        StudyCredentialVault.create(old,previous,PASS.toCharArray());byte[] original=Files.readAllBytes(old);
        var authorization=new AtomicReference<Map<String,String>>();
        var oauth=new StudyOAuth(get(StudyOAuth.OPENAI),(u,t,b)->{
            try {
                return new StudyCredentials.Reply(200,StudyAssistance.json(Map.of("access_token","NEW","refresh_token","NEW_REFRESH",
                    "token_type","Bearer","expires_in",3600,"scope",StudyOAuth.OPENAI.scopes(),
                    "id_token",sign(StudyOAuth.OPENAI.issuer(),"oaiapp_saved",authorization.get().get("nonce"),"different-user",NOW.plusSeconds(3600),key))));
            } catch(Exception e) {throw new IOException("Synthetic fixture error");}
        },CLOCK);
        var error=assertThrows(IOException.class,()->StudyLoginCli.run(new String[]{"--provider","openai","--from",old.toString(),"--out",fresh.toString(),
            "--host-id-file",root.resolve("host.json").toString()},new PrintStream(new ByteArrayOutputStream()),oauth,
            uri->{var q=StudyOAuth.query(uri.getRawQuery());authorization.set(q);assertEquals("oaiapp_saved",q.get("client_id"));
                assertFalse(q.containsKey("agent_name_hint"));assertFalse(q.containsKey("id_token_hint"));callback(uri,Map.of());},(f,e)->PASS.toCharArray()));
        assertTrue(error.getMessage().contains("differs"));assertFalse(Files.exists(fresh));assertArrayEquals(original,Files.readAllBytes(old));
    }
    @Test void noBrowserTimeoutCreatesNoCredentialAndNeverLaunchesBrowser() throws Exception {
        Path file=root.resolve("timeout.json");var bytes=new ByteArrayOutputStream();
        assertThrows(IOException.class,()->StudyLoginCli.run(new String[]{"--provider","openai","--out",file.toString(),"--host-id-file",
            root.resolve("host.json").toString(),"--no-browser","--timeout-seconds","1"},new PrintStream(bytes),
            new StudyOAuth(uri->{fail("No metadata fetch before a callback");return "";},(u,t,b)->{fail("No token exchange");return null;},CLOCK),
            uri->fail("No browser launch"),(f,e)->PASS.toCharArray()));
        assertFalse(Files.exists(file));assertTrue(bytes.toString().contains("https://auth.openai.com/"));
    }
    @Test void discoveryCannotRedirectSigningKeyFetchAndProviderErrorsAreSanitized() throws Exception {
        var a=attempt(StudyOAuth.OPENAI);var valid=tokens(a,"oaiapp_test","user");
        var reads=new AtomicInteger();
        var oauth=new StudyOAuth(uri->{reads.incrementAndGet();return discovery(a.provider()).replace(a.provider().jwks(),"https://attacker.example/keys");},
            (u,t,b)->new StudyCredentials.Reply(200,StudyAssistance.json(valid)),CLOCK);
        assertThrows(IOException.class,()->oauth.finish(a,Map.of("state",a.state(),"code","CODE","client_id","oaiapp_test")));
        assertEquals(1,reads.get());
        var denied=new StudyOAuth(get(a.provider()),(u,t,b)->new StudyCredentials.Reply(400,"SYNTHETIC_PROVIDER_SECRET"),CLOCK);
        assertFalse(assertThrows(IOException.class,()->denied.finish(a,Map.of("state",a.state(),"code","CODE","client_id","oaiapp_test")))
            .getMessage().contains("SYNTHETIC_PROVIDER_SECRET"));
    }

    @Test void invalidGrantRetriesWithIssuedClientAndFreshPkceAndNonce() throws Exception {
        Path file=root.resolve("retry.json");var current=new AtomicReference<Map<String,String>>();
        var requests=new ArrayList<Map<String,String>>();var calls=new AtomicInteger();
        var oauth=new StudyOAuth(get(StudyOAuth.OPENAI),(u,t,b)->{
            if(calls.getAndIncrement()==0)return new StudyCredentials.Reply(400,"{\"error\":\"invalid_grant\"}");
            try {
                return new StudyCredentials.Reply(200,StudyAssistance.json(Map.of("access_token","RETRY_ACCESS","refresh_token","RETRY_REFRESH",
                    "token_type","Bearer","expires_in",3600,"scope",StudyOAuth.OPENAI.scopes(),
                    "id_token",sign(StudyOAuth.OPENAI.issuer(),"oaiapp_retry",current.get().get("nonce"),"user",NOW.plusSeconds(3600),key))));
            } catch(Exception e) {throw new IOException("Synthetic fixture error");}
        },CLOCK);
        StudyLoginCli.run(new String[]{"--provider","openai","--out",file.toString(),"--host-id-file",root.resolve("host.json").toString()},
            new PrintStream(new ByteArrayOutputStream()),oauth,uri->{
                var q=StudyOAuth.query(uri.getRawQuery());current.set(q);requests.add(q);
                callback(uri,requests.size()==1?Map.of("client_id","oaiapp_retry"):Map.of());
            },(f,e)->PASS.toCharArray());
        assertEquals(2,calls.get());assertEquals("dynamic_agent_client",requests.get(0).get("client_id"));
        assertEquals("oaiapp_retry",requests.get(1).get("client_id"));
        for(String field:List.of("state","nonce","code_challenge"))assertNotEquals(requests.get(0).get(field),requests.get(1).get(field));
        assertEquals(requests.get(0).get("ext_agent_host_id"),requests.get(1).get("ext_agent_host_id"));
        assertEquals("oaiapp_retry",StudyCredentialVault.read(file,PASS.toCharArray()).get("client_id"));
    }
}
