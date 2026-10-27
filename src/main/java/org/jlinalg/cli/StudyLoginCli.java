/*
 * Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.jlinalg.cli;

import java.awt.Desktop;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

/** Interactive first login, separate from scientific execution and consent to disclose study evidence. */
final class StudyLoginCli {
    interface Browser { void open(URI uri) throws Exception; }
    private StudyLoginCli() { }
    static int run(String[] args,PrintStream output) throws IOException {
        return run(args,output,new StudyOAuth(),StudyLoginCli::browse);
    }
    static int run(String[] args,PrintStream output,StudyOAuth oauth,Browser browser) throws IOException {
        return run(args,output,oauth,browser,StudyCredentials::password);
    }
    static int run(String[] args,PrintStream output,StudyOAuth oauth,Browser browser,StudyCredentials.Passwords passwords) throws IOException {
        var options=parse(args);
        String provider=options.getOrDefault("provider","openai");
        provider=switch(provider) {case "claude"->"anthropic";case "grok"->"xai";default->provider;};
        if(!Set.of("openai","gemini","anthropic","xai").contains(provider))
            throw new IOException("Login provider must be openai, gemini, anthropic (claude), or xai (grok)");
        String auth=options.getOrDefault("auth",Set.of("openai","gemini").contains(provider)?"oauth":"api_key");
        if(!Set.of("oauth","api_key","wif").contains(auth))throw new IOException("Login auth must be oauth, api_key or wif");
        Path file=options.containsKey("out")?Path.of(options.get("out")):
            Path.of(System.getProperty("user.home"),".omicsmatic","credentials",provider+(auth.equals("api_key")?"":"-oauth")+".credentials.json");
        if(!file.isAbsolute())throw new IOException("--out must be an absolute private file path");
        file=file.normalize();
        if(Files.exists(file,LinkOption.NOFOLLOW_LINKS))throw new IOException("Credential output already exists. Choose a new --out file; use --from to reauthorize a saved OAuth account");
        if(auth.equals("oauth")&&provider.equals("anthropic"))
            throw new IOException("Claude.ai subscription OAuth is not permitted in third-party apps. Use --auth api_key or --auth wif for organization-configured workload identity. See https://code.claude.com/docs/en/legal-and-compliance");
        if(auth.equals("oauth")&&provider.equals("xai"))
            throw new IOException("xAI does not document a public Grok API OAuth login for this client. Use --auth api_key; https://docs.x.ai/developers/quickstart");
        if(auth.equals("wif")&&!provider.equals("anthropic"))throw new IOException("Workload identity setup is supported only for anthropic");
        if(!auth.equals("oauth")) {
            Set<String> allowed=auth.equals("wif")?Set.of("provider","auth","out","password-env","identity-token-file","federation-rule-id","organization-id","service-account-id","workspace-id","no-browser")
                :Set.of("provider","auth","out","password-env","token-env","no-browser");
            checkKeys(options,allowed);
            var forwarded=new ArrayList<String>(List.of("--provider",provider,"--out",file.toString(),"--auth",auth.equals("wif")?"oauth":"api_key"));
            if(auth.equals("wif")) {
                output.println("Configure Claude workload federation. Your organization and identity provider must already be configured.");
                forwarded.addAll(List.of("--renewal","anthropic_wif"));
            } else {
                String url=switch(provider) {
                    case "openai"->"https://platform.openai.com/api-keys";
                    case "gemini"->"https://aistudio.google.com/api-keys";
                    case "anthropic"->"https://platform.claude.com/settings/keys";
                    default->"https://console.x.ai/";
                };
                output.println("API-key setup: create an approved API key in the provider console, then enter it at the hidden prompt.");
                open(URI.create(url),options,output,browser);
            }
            for(String key:allowed)if(options.containsKey(key)&&!Set.of("provider","auth","out","no-browser").contains(key))
                forwarded.addAll(List.of("--"+key,options.get(key)));
            int code=StudyCredentialCli.run("credentials-save",forwarded.toArray(String[]::new),output);
            if(auth.equals("wif"))output.println("Saved federation configuration. The first permitted API operation exchanges the current identity JWT; upstream identity login/rotation remains with your IdP.");
            yaml(output,provider,auth.equals("wif")?"oauth":"api_key",file);
            return code;
        }
        checkKeys(options,Set.of("provider","auth","out","password-env","client-json","client-id","from","host-id-file","port","timeout-seconds","no-browser"));
        if(provider.equals("openai")&&options.containsKey("client-json")
                ||provider.equals("gemini")&&(options.containsKey("client-id")||options.containsKey("host-id-file")))
            throw new IOException("Use --client-json for Gemini; --client-id and --host-id-file are OpenAI options");
        int port=integer(options,"port",0,0,65535),seconds=integer(options,"timeout-seconds",300,1,900);
        Map<String,Object> previous=null;
        if(options.containsKey("from")) {
            Path source=Path.of(options.get("from")).toAbsolutePath().normalize();
            char[] oldPassword=passwords.read(source,options.get("password-env"));
            try {previous=StudyCredentialVault.read(source,oldPassword);}
            finally {Arrays.fill(oldPassword,'\0');}
            StudyCredentials.validate(previous);
            if(!provider.equals(previous.get("provider"))||!"oauth".equals(previous.get("auth"))
                    ||!Boolean.TRUE.equals(previous.get("identity_verified"))||!previous.containsKey("subject"))
                throw new IOException("--from must select an identity-verified OAuth record for the same provider");
        }
        String client=provider.equals("openai")?options.getOrDefault("client-id","dynamic_agent_client"):"",secret="";
        if(previous!=null) {
            client=StudyOAuth.required(previous,"client_id");
            if(options.containsKey("client-id")&&!client.equals(options.get("client-id")))throw new IOException("Client ID differs from selected saved account");
        }
        if(provider.equals("gemini")) {
            if(options.containsKey("client-json")) {
                var registration=googleClient(Path.of(options.get("client-json")));
                String selected=StudyOAuth.required(registration,"client_id");
                if(previous!=null&&!selected.equals(client))throw new IOException("Google client differs from selected saved account");
                client=selected;secret=String.valueOf(registration.getOrDefault("client_secret",""));
            } else if(previous!=null)secret=String.valueOf(previous.getOrDefault("client_secret",""));
            else throw new IOException("Gemini login requires --client-json pointing to your Google Desktop app client registration");
        }
        if(client.isBlank()||client.length()>1024||client.chars().anyMatch(Character::isISOControl))throw new IOException("Invalid OAuth client ID");
        char[] password=passwords.read(file,options.get("password-env"));
        try {
            if(password.length<12||password.length>1024)throw new IOException("Use a credential-file passphrase of 12 to 1024 characters");
            if(System.console()!=null&&!options.containsKey("password-env")) {
                char[] confirm=System.console().readPassword("Confirm new credential-file passphrase: ");
                try {if(!Arrays.equals(password,confirm))throw new IOException("Passphrases do not match");}
                finally {if(confirm!=null)Arrays.fill(confirm,'\0');}
            }
            String host=provider.equals("openai")?hostId(options.containsKey("host-id-file")?Path.of(options.get("host-id-file")):
                Path.of(System.getProperty("user.home"),".omicsmatic","openai-host.json")):"";
            // Bind the listener before constructing or opening the authorization URL.
            boolean saved=false;
            for(int attemptNumber=0;attemptNumber<2&&!saved;attemptNumber++) {
                String state=StudyOAuth.random();
                try(var callback=new StudyOAuthCallback(port,state,provider.equals("gemini")?"/":"/auth/callback")) {
                    var initial=StudyOAuth.attempt(provider.equals("openai")?StudyOAuth.OPENAI:StudyOAuth.GOOGLE,client,secret,host,callback.redirect(),previous);
                    var attempt=new StudyOAuth.Attempt(initial.provider(),client,secret,host,state,initial.nonce(),initial.verifier(),callback.redirect(),previous);
                    output.println(provider.equals("openai")?"Continue with ChatGPT":"Continue with Google");
                    output.println("Review the provider's account and permissions. This login sends no study data and makes no inference request.");
                    output.println("Waiting for callback on "+callback.redirect()+" (timeout "+seconds+" seconds).");
                    open(StudyOAuth.authorization(attempt),options,output,browser);
                    Map<String,Object> record=oauth.finish(attempt,callback.await(Duration.ofSeconds(seconds)));
                    StudyCredentialVault.create(file,record,password);
                    saved=true;
                } catch(StudyOAuth.RetryAuthorization retry) {
                    client=retry.clientId;
                    if(attemptNumber==1)throw new IOException(provider.equals("openai")
                        ?"OAuth code exchange could not complete. Retry a fresh login with --client-id "+client+"; no credentials saved"
                        :"OAuth code exchange could not complete. Retry a fresh login with the same Desktop client registration; no credentials saved");
                    output.println("Authorization code was rejected or expired. Starting one fresh authorization with the same issued client registration.");
                } catch(InterruptedException interrupted) {
                    Thread.currentThread().interrupt();throw new IOException("OAuth login interrupted; no credentials saved");
                }
            }
            output.println("OAuth identity and granted scopes verified. Saved password-encrypted credentials: "+file);
            yaml(output,provider,"oauth",file);
            return 0;
        } finally {Arrays.fill(password,'\0');}
    }
    static Map<String,String> parse(String[] args) throws IOException {
        var result=new LinkedHashMap<String,String>();
        Set<String> allowed=Set.of("provider","auth","out","password-env","client-json","client-id","from","host-id-file","port","timeout-seconds",
            "no-browser","token-env","identity-token-file","federation-rule-id","organization-id","service-account-id","workspace-id");
        for(int i=0;i<args.length;i++) {
            String option=args[i];
            if(!option.startsWith("--")||!allowed.contains(option.substring(2)))throw new IOException("Unknown login option; see study help");
            String key=option.substring(2),value="true";
            if(!key.equals("no-browser")) {
                if(i+1>=args.length||args[i+1].startsWith("--"))throw new IOException("Missing login option value");
                value=args[++i];
            }
            if(result.putIfAbsent(key,value)!=null)throw new IOException("Duplicate login option");
        }
        return result;
    }
    private static void checkKeys(Map<String,String> options,Set<String> allowed) throws IOException {
        if(!allowed.containsAll(options.keySet()))throw new IOException("An option is not supported for the selected provider/authentication method");
    }
    static Map<String,Object> googleClient(Path file) throws IOException {
        if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||Files.size(file)>16384)throw new IOException("Google Desktop client JSON is missing, linked or too large");
        try(var input=Files.newInputStream(file,LinkOption.NOFOLLOW_LINKS)) {
            var document=StudyCredentialVault.object(SimpleJson.parse(new String(input.readNBytes(16385),StandardCharsets.UTF_8)));
            var client=StudyCredentialVault.object(document.get("installed"));
            StudyOAuth.required(client,"client_id");
            if(client.containsKey("client_secret"))StudyOAuth.required(client,"client_secret");
            return client;
        } catch(IOException|RuntimeException invalid) {throw new IOException("Provide a Google Desktop app client JSON with an installed client registration");}
    }
    static String hostId(Path file) throws IOException {
        if(!file.isAbsolute())throw new IOException("--host-id-file must be absolute");
        if(!Files.exists(file,LinkOption.NOFOLLOW_LINKS)) {
            Files.createDirectories(file.getParent());
            try(var out=Files.newOutputStream(file,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)) {
                StudyCredentialVault.restrict(file);
                out.write(("{\"host_id\":\"urn:uuid:"+UUID.randomUUID()+"\"}\n").getBytes(StandardCharsets.UTF_8));
            } catch(FileAlreadyExistsException race) { /* Read the host created by the other login. */ }
        }
        if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||Files.size(file)>1024)throw new IOException("Invalid host-ID file");
        Map<String,Object> record;
        try(var input=Files.newInputStream(file,LinkOption.NOFOLLOW_LINKS)) {
            record=StudyCredentialVault.object(SimpleJson.parse(new String(input.readNBytes(1025),StandardCharsets.UTF_8)));
        }
        String id=StudyOAuth.required(record,"host_id");
        if(!id.matches("urn:uuid:[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}"))
            throw new IOException("Invalid persistent OpenAI host ID");
        return id;
    }
    private static int integer(Map<String,String> options,String key,int fallback,int min,int max) throws IOException {
        try {int value=Integer.parseInt(options.getOrDefault(key,String.valueOf(fallback)));
            if(value<min||value>max)throw new NumberFormatException();return value;}
        catch(NumberFormatException bad) {throw new IOException("Invalid login "+key);}
    }
    private static void open(URI uri,Map<String,String> options,PrintStream output,Browser browser) {
        output.println(uri);
        if(!options.containsKey("no-browser"))try {browser.open(uri);}
        catch(Exception unavailable) {output.println("Browser could not open automatically. Open the URL above on this computer, or use the documented SSH tunnel.");}
    }
    private static void browse(URI uri) throws Exception {
        if(!Desktop.isDesktopSupported()||!Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))throw new IOException();
        Desktop.getDesktop().browse(uri);
    }
    private static void yaml(PrintStream out,String provider,String auth,Path file) {
        out.println("In study.yaml, merge into your assistance mapping (replace the model and keep your disclosure policy):");
        out.println("  provider: "+provider+"\n  auth: "+auth+"\n  model: APPROVED_MODEL_ID\n  credential_file: "+StudyAssistance.json(file.toString().replace('\\','/')));
        out.println("Choose credential_file instead of api_key_env/oauth_token_env. Login does not approve an analysis or a data disclosure.");
    }
}
