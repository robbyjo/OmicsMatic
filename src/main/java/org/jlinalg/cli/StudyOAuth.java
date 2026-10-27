/*
 * Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.jlinalg.cli;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.*;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jwt.SignedJWT;
import java.io.IOException;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;

/** Provider-fixed OAuth code/PKCE exchange; tokens never appear in diagnostics. */
final class StudyOAuth {
    record Provider(String name, String issuer, String authorize, String token, String jwks, String scopes) { }
    static final Provider OPENAI = new Provider("openai", "https://auth.openai.com",
        "https://auth.openai.com/api/accounts/authorize", "https://auth.openai.com/api/accounts/oauth/token",
        "https://auth.openai.com/.well-known/jwks.json",
        "openid profile email offline_access resource.invoke chatgpt.tokens.use.direct");
    static final Provider GOOGLE = new Provider("gemini", "https://accounts.google.com",
        "https://accounts.google.com/o/oauth2/v2/auth", "https://oauth2.googleapis.com/token",
        "https://www.googleapis.com/oauth2/v3/certs",
        "openid email profile https://www.googleapis.com/auth/cloud-platform https://www.googleapis.com/auth/generative-language.retriever");
    static final class RetryAuthorization extends IOException {
        private static final long serialVersionUID = 1L;
        final String clientId;
        RetryAuthorization(String clientId) { super("Authorization code expired or was rejected; a fresh authorization is required"); this.clientId=clientId; }
    }
    interface Get { String read(URI uri) throws IOException, InterruptedException; }
    record Attempt(Provider provider, String clientId, String clientSecret, String hostId,
            String state, String nonce, String verifier, URI redirect, Map<String,Object> previous) {
        @Override public String toString() { return "OAuth attempt [redacted]"; }
    }
    private final Get get;
    private final StudyCredentials.Transport post;
    private final Clock clock;
    StudyOAuth() { this(StudyOAuth::get, StudyCredentials::send, Clock.systemUTC()); }
    StudyOAuth(Get get, StudyCredentials.Transport post, Clock clock) {
        this.get=get; this.post=post; this.clock=clock;
    }
    static String random() {
        byte[] value=new byte[32];new SecureRandom().nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
    static Attempt attempt(Provider provider,String clientId,String secret,String hostId,URI redirect,Map<String,Object> previous) {
        return new Attempt(provider,clientId,secret,hostId,random(),random(),random(),redirect,previous);
    }
    static URI authorization(Attempt a) throws IOException {
        var q=new LinkedHashMap<String,Object>();
        q.put("client_id",a.clientId());q.put("response_type","code");q.put("redirect_uri",a.redirect());
        q.put("scope",a.provider().scopes());q.put("state",a.state());q.put("nonce",a.nonce());
        q.put("code_challenge_method","S256");
        try { q.put("code_challenge",Base64.getUrlEncoder().withoutPadding().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(a.verifier().getBytes(StandardCharsets.US_ASCII)))); }
        catch (GeneralSecurityException e) { throw new IOException("Required PKCE hashing is unavailable"); }
        if(a.provider()==OPENAI) {
            q.put("resource","https://api.openai.com/v1");q.put("ext_agent_host_id",a.hostId());
            if(a.clientId().equals("dynamic_agent_client"))q.put("agent_name_hint","OmicsMatic");
        } else {
            q.put("access_type","offline");q.put("prompt","consent select_account");
        }
        return URI.create(a.provider().authorize()+"?"+form(q));
    }
    Map<String,Object> finish(Attempt a,Map<String,String> callback) throws IOException,InterruptedException {
        if(!equal(a.state(),callback.get("state")))throw new IOException("OAuth callback state mismatch");
        if(callback.containsKey("error"))throw new IOException("OAuth authorization was declined or could not complete; no credentials saved");
        if(callback.containsKey("iss")&&!a.provider().issuer().equals(callback.get("iss")))
            throw new IOException("OAuth callback issuer mismatch");
        String code=required(callback,"code"),client=a.clientId();
        if(a.provider()==OPENAI&&client.equals("dynamic_agent_client")) {
            client=required(callback,"client_id");
            if(!client.matches("[A-Za-z0-9_-]{4,256}")||client.equals("dynamic_agent_client"))
                throw new IOException("OpenAI did not issue a usable client ID");
        } else if(callback.containsKey("client_id")&&!client.equals(callback.get("client_id")))
            throw new IOException("OAuth callback client ID mismatch");
        var request=new LinkedHashMap<String,Object>();
        request.put("grant_type","authorization_code");request.put("client_id",client);
        request.put("code",code);request.put("code_verifier",a.verifier());request.put("redirect_uri",a.redirect());
        if(a.provider()==OPENAI)request.put("resource","https://api.openai.com/v1");
        else if(!a.clientSecret().isBlank())request.put("client_secret",a.clientSecret());
        var reply=post.send(URI.create(a.provider().token()),"application/x-www-form-urlencoded",form(request));
        Instant received=clock.instant();
        if(reply.status()!=200) {
            try {
                var error=StudyCredentialVault.object(SimpleJson.parse(reply.body()));
                if("invalid_grant".equals(error.get("error")))throw new RetryAuthorization(client);
            } catch(RetryAuthorization retry) {throw retry;}
            catch(IOException|RuntimeException malformed) { /* Never expose provider error text. */ }
            throw new IOException("OAuth token exchange rejected (HTTP "+reply.status()+"); no credentials saved. Start a fresh login");
        }
        Map<String,Object> tokens;
        try { tokens=StudyCredentialVault.object(SimpleJson.parse(reply.body())); }
        catch(IOException|RuntimeException e) {throw new IOException("Invalid OAuth token response; no credentials saved");}
        String access=required(tokens,"access_token"),refresh=required(tokens,"refresh_token");
        Object seconds=tokens.get("expires_in");
        if(!"Bearer".equalsIgnoreCase(text(tokens.get("token_type")))||!(seconds instanceof Number n)
                ||n.doubleValue()!=n.longValue()||n.longValue()<60||n.longValue()>31_536_000)
            throw new IOException("OAuth response needs a bearer token, refresh token and valid expiry");
        Set<String> scopes=scopeSet(tokens.get("scope"));
        // OAuth permits scope omission when the granted scope equals the requested scope.
        if(scopes.isEmpty()&&a.provider()==GOOGLE&&!tokens.containsKey("scope"))scopes=scopeSet(GOOGLE.scopes());
        requireScopes(a.provider(),scopes);
        var identity=identity(a.provider(),required(tokens,"id_token"),client,a.nonce());
        if(a.previous()!=null && (!Objects.equals(identity.get("subject"),a.previous().get("subject"))
                ||!Objects.equals(identity.get("issuer"),a.previous().get("issuer"))))
            throw new IOException("The signed-in account differs from the selected saved account; no credentials saved");
        var record=new LinkedHashMap<String,Object>(identity);
        record.put("provider",a.provider().name());record.put("auth","oauth");
        record.put("renewal",a.provider()==OPENAI?"openai":"google");
        record.put("endpoint",StudyCredentials.endpoint(a.provider().name(),""));
        record.put("client_id",client);record.put("access_token",access);record.put("refresh_token",refresh);
        record.put("id_token",tokens.get("id_token"));record.put("scopes",new ArrayList<>(scopes));
        record.put("identity_verified",true);record.put("saved_at",received.toString());
        record.put("expires_at",received.plusSeconds(((Number)seconds).longValue()).toString());
        if(a.provider()==OPENAI)record.put("ext_agent_host_id",a.hostId());
        else if(!a.clientSecret().isBlank())record.put("client_secret",a.clientSecret());
        if(tokens.containsKey("earliest_refresh_at")) {
            try {
                Object raw=tokens.get("earliest_refresh_at");
                record.put("earliest_refresh_at",(raw instanceof Number refreshTime?Instant.ofEpochSecond(refreshTime.longValue()):Instant.parse(text(raw))).toString());
            } catch(RuntimeException bad) {throw new IOException("Invalid OAuth refresh scheduling metadata");}
        }
        StudyCredentials.validate(record);
        return record;
    }
    Map<String,Object> identity(Provider provider,String token,String audience,String nonce) throws IOException,InterruptedException {
        // Discovery and JWKS are fetched only from fixed provider URLs, never JWT headers or user input.
        Map<String,Object> discovery=StudyCredentialVault.object(SimpleJson.parse(
            get.read(URI.create(provider.issuer()+"/.well-known/openid-configuration"))));
        if(!provider.issuer().equals(discovery.get("issuer"))||!provider.authorize().equals(discovery.get("authorization_endpoint"))
                ||!provider.token().equals(discovery.get("token_endpoint"))||!provider.jwks().equals(discovery.get("jwks_uri")))
            throw new IOException("OAuth provider discovery does not match the supported endpoints");
        String jwks=get.read(URI.create(provider.jwks()));
        return verifyIdentity(provider,token,audience,nonce,jwks,discovery.get("id_token_signing_alg_values_supported"),clock);
    }
    static Map<String,Object> verifyIdentity(Provider provider,String token,String audience,String nonce,
            String jwks,Object advertised,Clock clock) throws IOException {
        try {
            if(token.length()>32768||jwks.length()>131072)throw new IOException();
            String[] parts=token.split("\\.",-1);
            if(parts.length!=3)throw new IOException();
            // Reject duplicate JSON fields before the JOSE library parses them.
            for(int i=0;i<2;i++)StudyCredentialVault.object(SimpleJson.parse(
                new String(Base64.getUrlDecoder().decode(parts[i]),StandardCharsets.UTF_8)));
            SignedJWT jwt=SignedJWT.parse(token);
            JWSAlgorithm alg=jwt.getHeader().getAlgorithm();
            if(!Set.of("RS256","PS256","ES256").contains(alg.getName())||!(advertised instanceof List<?> list)
                    ||!list.contains(alg.getName())||jwt.getHeader().getKeyID()==null
                    ||jwt.getHeader().getCriticalParams()!=null&&!jwt.getHeader().getCriticalParams().isEmpty())
                throw new IOException();
            JWKSet keys=JWKSet.parse(jwks);
            if(keys.getKeys().size()>64)throw new IOException();
            boolean valid=false;
            for(JWK key:keys.getKeys()) {
                if(!jwt.getHeader().getKeyID().equals(key.getKeyID())||key.getKeyUse()!=null&&!KeyUse.SIGNATURE.equals(key.getKeyUse())
                        ||key.getAlgorithm()!=null&&!alg.equals(key.getAlgorithm())
                        ||key.getKeyOperations()!=null&&!key.getKeyOperations().contains(KeyOperation.VERIFY))continue;
                if(key instanceof RSAKey rsa&&Set.of("RS256","PS256").contains(alg.getName())&&rsa.size()>=2048)
                    valid=jwt.verify(new RSASSAVerifier(rsa.toRSAPublicKey()));
                else if(key instanceof ECKey ec&&alg.equals(JWSAlgorithm.ES256)&&ec.getCurve().equals(Curve.P_256))
                    valid=jwt.verify(new ECDSAVerifier(ec.toECPublicKey()));
                if(valid)break;
            }
            if(!valid)throw new IOException();
            var claims=jwt.getJWTClaimsSet();Instant now=clock.instant();
            boolean issuer=provider.issuer().equals(claims.getIssuer())
                ||provider==GOOGLE&&"accounts.google.com".equals(claims.getIssuer());
            if(!issuer||!claims.getAudience().contains(audience)||claims.getSubject()==null||claims.getSubject().isBlank()
                    ||claims.getExpirationTime()==null||!claims.getExpirationTime().toInstant().isAfter(now.minusSeconds(30))
                    ||claims.getIssueTime()==null||claims.getIssueTime().toInstant().isAfter(now.plusSeconds(30))
                    ||claims.getNotBeforeTime()!=null&&claims.getNotBeforeTime().toInstant().isAfter(now.plusSeconds(30))
                    ||!equal(nonce,claims.getStringClaim("nonce"))
                    ||claims.getAudience().size()>1&&!audience.equals(claims.getStringClaim("azp"))
                    ||claims.getClaim("azp")!=null&&!audience.equals(claims.getStringClaim("azp")))throw new IOException();
            var result=new LinkedHashMap<String,Object>();
            result.put("issuer",provider.issuer());result.put("subject",claims.getSubject());
            if(Boolean.TRUE.equals(claims.getBooleanClaim("email_verified"))&&claims.getStringClaim("email")!=null)
                result.put("email",claims.getStringClaim("email"));
            return result;
        } catch(Exception invalid) {throw new IOException("OAuth ID-token signature or identity validation failed; no credentials saved");}
    }
    static Set<String> scopeSet(Object raw) {
        var result=new LinkedHashSet<String>();
        if(raw instanceof String s&&!s.isBlank())result.addAll(Arrays.asList(s.strip().split("\\s+")));
        else if(raw instanceof List<?> list)for(Object item:list)if(item instanceof String s)result.add(s);
        return result;
    }
    static void requireScopes(Provider p,Set<String> scopes) throws IOException {
        Set<String> required=p==OPENAI?Set.of("openid","offline_access","resource.invoke","chatgpt.tokens.use.direct")
            :Set.of("openid","https://www.googleapis.com/auth/cloud-platform");
        if(!scopes.containsAll(required))throw new IOException("OAuth grant lacks required model-access permissions; authorize again");
    }
    static String form(Map<String,?> values) {
        return String.join("&",values.entrySet().stream().map(e->URLEncoder.encode(e.getKey(),StandardCharsets.UTF_8)
            +"="+URLEncoder.encode(String.valueOf(e.getValue()),StandardCharsets.UTF_8)).toList());
    }
    static Map<String,String> query(String raw) throws IOException {
        var result=new LinkedHashMap<String,String>();
        if(raw==null||raw.length()>16384)throw new IOException("Invalid OAuth callback");
        try {
            for(String item:raw.split("&",-1)) {
                String[] pair=item.split("=",2);
                String key=URLDecoder.decode(pair[0],StandardCharsets.UTF_8);
                String value=URLDecoder.decode(pair.length==2?pair[1]:"",StandardCharsets.UTF_8);
                if(key.isBlank()||result.putIfAbsent(key,value)!=null)throw new IOException("Duplicate OAuth callback field");
            }
        } catch(IllegalArgumentException bad) {throw new IOException("Invalid OAuth callback encoding");}
        return result;
    }
    static boolean equal(String a,String b) {
        return a!=null&&b!=null&&MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8),b.getBytes(StandardCharsets.UTF_8));
    }
    static String required(Map<?,?> values,String key) throws IOException {
        String s=text(values.get(key));
        if(s.isBlank()||s.length()>32768||s.chars().anyMatch(Character::isISOControl))
            throw new IOException("Required OAuth field missing or invalid: "+key);
        return s;
    }
    private static String text(Object value) {return value instanceof String s?s:"";}
    private static String get(URI uri) throws IOException,InterruptedException {
        var client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.NEVER).build();
        var request=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30)).GET().build();
        var response=client.send(request,ignored->new StudyAssistance.BoundedBody());
        if(response.statusCode()!=200||response.body().length>131072)throw new IOException("OAuth discovery or signing keys unavailable");
        return new String(response.body(),StandardCharsets.UTF_8);
    }
}
