/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import static org.jlinalg.cli.SnakemakeOriginalFiles.*;
import static org.jlinalg.cli.SnakemakeOriginalRecorded.absolute;

/** Independent portable audit of propagated installation decisions; not workflow admission. */
final class SnakemakeOriginalInstallationPolicy {
    private static final String LOCAL="c76bd06604c9d75ad3b079b75fbefa4cb89a507015df25414c1d1a9cd3b35d3e";
    private static final Map<String,String> ORIGINAL=Map.of("snakemake.deployment.conda","ad43a8b87ed5c04739bda1233945e64291372718cb27e12b3bceed3325526798","snakemake.deployment.singularity","225a2de2b3e6e2b91a24de7e902f3542ae7471223147410ad2ea9bb81e30d80b");
    private static final String ACTION="SKIP3: installation is forbidden and the original deployer needs missing/incomplete software. Skip this dependent analysis; restore a complete qualified environment/image at the original selected location, or enable installation only where permitted and retry NEW output. Do not relocate an installed Conda prefix, silently substitute software or refresh historical hashes.";
    record Audit(Set<Long> processes,int deploymentCalls,int spawnCalls,int blockedCalls,int originalFailures,boolean allowInstall,Pin configuration,Map<String,Pin> files) {
        Audit {processes=Set.copyOf(processes);files=Map.copyOf(files);}
    }
    private SnakemakeOriginalInstallationPolicy() { }
    static Audit audit(Path suppliedRecords,Path suppliedConfiguration,long controller,Set<Long> ownedProcesses,String expectedRun,boolean expectedAllow,String expectedPython,String condaPrefix,String imagePrefix,int maxProcesses,int maxRows,int maxBytes,long maxTotalBytes,Runnable check)throws IOException {
        if(maxProcesses<1||maxProcesses>4096||maxRows<1||maxBytes<1024||maxBytes>67_108_864||maxTotalBytes<maxBytes||controller<1||!ownedProcesses.contains(controller))throw new IOException("Invalid installation-policy ownership/capacity contract");
        absolute(condaPrefix);absolute(imagePrefix);Path directory=directory(suppliedRecords.toAbsolutePath().normalize()),configuration=suppliedConfiguration.toAbsolutePath().normalize();
        Pin configPin=pin(configuration,maxBytes,check);Map<?,?> config=object(json(configuration,maxBytes));keys(config,"schema_version","run_id","allow_install","records_directory","logger");integer(config.get("schema_version"),1,1);
        String run=text(config.get("run_id")),logger=text(config.get("logger")),producerDirectory=absolute(text(config.get("records_directory")));
        if(!run.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")||!Set.of("omicsmanaged","omicsmatic").contains(logger))throw new IOException("Unqualified installation-policy run/logger");
        boolean allow=flag(config.get("allow_install"));if(!run.equals(expectedRun)||allow!=expectedAllow)throw new IOException("Installation-policy run/permission differs from the declared invocation");absolute(expectedPython);var files=new TreeMap<String,Pin>();var pids=new HashSet<Long>();long totalBytes=configPin.bytes();int calls=0,spawns=0,blockedCalls=0,originalFailures=0;String producerConfig=null;
        var names=new HashSet<String>();try(var stream=Files.list(directory)){for(Path path:stream.toList()){
            check.run();String name=path.getFileName().toString();if(!name.matches("policy-[1-9][0-9]*\\.(?:OPENED\\.)?json")||!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||names.size()>=maxProcesses*2)throw new IOException("Unexpected, failed or excessive installation-policy record");names.add(name);Pin value=pin(path,maxBytes,check);files.put(name,value);totalBytes=Math.addExact(totalBytes,value.bytes());if(totalBytes>maxTotalBytes)throw new IOException("Complete installation-policy bytes exceed the declared capacity");
        }}
        for(String name:names.stream().filter(n->!n.endsWith(".OPENED.json")).sorted().toList()) {
            check.run();long pid;try{pid=Long.parseLong(name.substring(7,name.length()-5));}catch(NumberFormatException error){throw new IOException("Invalid policy process identity",error);}
            if(!ownedProcesses.contains(pid)||!pids.add(pid)||!names.contains("policy-"+pid+".OPENED.json"))throw new IOException("Policy process is unowned, repeated or lacks its opening record");
            Map<?,?> value=object(json(directory.resolve(name),maxBytes));keys(value,"schema_version","status","pid","parent_pid","run_id","config_file","config","allow_install","entered","closed","blocked","records","spawn_arguments","local_executor_source_sha256","bindings_restored","arguments","executable","working_directory","user_analysis_admitted");
            integer(value.get("schema_version"),1,1);integer(value.get("pid"),pid,pid);integer(value.get("parent_pid"),1,Long.MAX_VALUE);
            if(!"INSTALLATION_POLICY_CLOSED_AFTER_ORIGINAL_EXECUTION_NOT_ANALYSIS".equals(value.get("status"))||!Boolean.TRUE.equals(value.get("entered"))||!Boolean.TRUE.equals(value.get("closed"))||!Boolean.TRUE.equals(value.get("bindings_restored"))||!Boolean.FALSE.equals(value.get("user_analysis_admitted"))||!run.equals(value.get("run_id"))||flag(value.get("allow_install"))!=allow||!LOCAL.equals(value.get("local_executor_source_sha256"))||!configPin.equals(strictPin(value.get("config"),maxBytes)))throw new IOException("Closed installation policy/configuration/source binding differs");
            String configFile=absolute(text(value.get("config_file"))),executable=absolute(text(value.get("executable")));absolute(text(value.get("working_directory")));if(strings(value.get("arguments")).isEmpty())throw new IOException("Missing actual policy-process arguments");
            if(!expectedPython.equals(executable))throw new IOException("Policy process used a different selected interpreter");if(producerConfig==null)producerConfig=configFile;else if(!producerConfig.equals(configFile))throw new IOException("Engine processes used different policies");
            if(!producerDirectory.equals(configFile.substring(0,configFile.lastIndexOf('/'))+"/policy-records"))throw new IOException("Policy record directory is not bound to its producer configuration");
            Map<?,?> opened=object(json(directory.resolve("policy-"+pid+".OPENED.json"),maxBytes));keys(opened,"schema_version","status","pid","parent_pid","run_id","config_file","config","allow_install","local_executor_source_sha256","arguments","executable","working_directory");integer(opened.get("schema_version"),1,1);
            if(!"INSTALLATION_POLICY_OPENED_NOT_ANALYSIS".equals(opened.get("status")))throw new IOException("Missing original policy-opening declaration");
            for(String field:Set.of("pid","parent_pid","run_id","config_file","config","allow_install","local_executor_source_sha256","arguments","executable","working_directory"))SnakemakeOriginalRecorded.equal(opened.get(field),value.get(field),"opened/closed installation-policy "+field);
            int processBlocked=0,sequence=0;
            for(Object row:list(value.get("records"))) {
                if(++calls>maxRows)throw new IOException("Complete deployment policy calls exceed event_rows");Map<?,?> decision=object(row);integer(decision.get("sequence"),++sequence,sequence);boolean denied=flag(decision.get("blocked")),needed=flag(decision.get("installation_required")),dryrun=flag(decision.get("dryrun"));
                if(flag(decision.get("allow_install"))!=allow||denied!=(needed&&!allow)||flag(decision.get("upstream_method_called"))==denied||dryrun&&needed)throw new IOException("Deployment permission decision contradicts the explicit installation policy");
                String kind=text(decision.get("kind")),module=text(decision.get("upstream_module"));
                if(!ORIGINAL.containsKey(module)||!ORIGINAL.get(module).equals(decision.get("upstream_file_sha256")))throw new IOException("Deployment decision used a different original source");
                var expected=new HashSet<>(Set.of("kind","sequence","allow_install","dryrun","installation_required","blocked","upstream_method_called","upstream_module","upstream_function","upstream_file_sha256"));
                if(kind.equals("conda-environment")) {
                    expected.addAll(Set.of("address","setup_done_flag","externally_managed","embedded_container_environment","container_url"));if(!module.equals("snakemake.deployment.conda")||!"Env.create".equals(decision.get("upstream_function")))throw new IOException("Conda decision/original entrypoint differs");
                    boolean external=flag(decision.get("externally_managed")),embedded=flag(decision.get("embedded_container_environment"));if((external||embedded)&&needed)throw new IOException("Externally managed/embedded Conda software cannot require original creation");
                    if(external){if(embedded||decision.get("address")!=null||decision.get("setup_done_flag")!=null||decision.get("container_url")!=null)throw new IOException("External Conda creation metadata differs from the original entrypoint");}
                    else {String address=absolute(text(decision.get("address")));if(embedded){if(decision.get("setup_done_flag")!=null||decision.get("container_url")==null)throw new IOException("Embedded Conda policy metadata differs");}
                        else if(!address.startsWith(condaPrefix+'/')||!address.substring(condaPrefix.length()+1).matches("(?:[a-f0-9]{8}|[a-f0-9]{32}_?)")||!(address+".env_setup_done").equals(decision.get("setup_done_flag")))throw new IOException("Conda decision prefix/setup flag differs from the original deployment binding");
                        if(decision.get("container_url")!=null)text(decision.get("container_url"));}
                }else if(kind.equals("container-image")) {
                    expected.addAll(Set.of("address","url","local"));if(!module.equals("snakemake.deployment.singularity")||!"Image.pull".equals(decision.get("upstream_function")))throw new IOException("Container decision/original entrypoint differs");String address=text(decision.get("address"));text(decision.get("url"));boolean local=flag(decision.get("local"));
                    if(local!=selectedLinuxLocalImage(text(decision.get("url"))))throw new IOException("Image local/remote declaration contradicts the selected original URI classification");
                    if(local){if(needed)throw new IOException("A local image does not require an upstream pull");if(address.startsWith("/"))absolute(address);else member(address);}
                    else if(!absolute(address).equals(imagePrefix+'/'+imageHash(text(decision.get("url")))+".simg"))throw new IOException("Container image decision differs from the selected original URL/cache hash");
                }else throw new IOException("Unknown deployment decision kind");
                if(denied){expected.add("required_action");if(!ACTION.equals(decision.get("required_action")))throw new IOException("Missing or changed required installation recovery action");processBlocked++;blockedCalls++;}
                else {expected.add("upstream_returned");if(!flag(decision.get("upstream_returned"))){expected.add("upstream_error_type");text(decision.get("upstream_error_type"));originalFailures++;}}
                if(kind.equals("conda-environment")&&flag(decision.get("externally_managed"))&&(!Boolean.FALSE.equals(decision.get("upstream_returned"))||!"builtins.AssertionError".equals(decision.get("upstream_error_type"))))throw new IOException("Original external Conda create must retain its file-based assertion failure");
                if(!decision.keySet().equals(expected))throw new IOException("Incomplete or unexpected deployment-decision fields");
            }
            if(flag(value.get("blocked"))!=(processBlocked>0))throw new IOException("Process-level installation skip contradicts its complete decisions");
            int spawnSequence=0;for(Object row:list(value.get("spawn_arguments"))) {
                if(++spawns>maxRows)throw new IOException("Complete propagated spawn count exceeds event_rows");Map<?,?> spawn=object(row);keys(spawn,"sequence","original_arguments","propagated_arguments","logger");integer(spawn.get("sequence"),++spawnSequence,spawnSequence);
                if(!"--quiet progress rules host".equals(spawn.get("original_arguments"))||!("--quiet progress rules host --logger "+logger).equals(spawn.get("propagated_arguments"))||!logger.equals(spawn.get("logger")))throw new IOException("Original local-executor arguments or propagated recorder selection differ");
            }
        }
        if(!pids.contains(controller)||names.size()!=pids.size()*2||pids.size()!=spawns+1)throw new IOException("Incomplete original local spawn/open/close policy census");
        var afterNames=new HashSet<String>();try(var stream=Files.list(directory)){for(Path path:stream.toList())afterNames.add(path.getFileName().toString());}if(!afterNames.equals(names)||!pin(configuration,maxBytes,check).equals(configPin))throw new IOException("Installation-policy census/configuration changed during verification");
        for(var row:files.entrySet())if(!pin(directory.resolve(row.getKey()),maxBytes,check).equals(row.getValue()))throw new IOException("Installation-policy evidence changed during complete verification");
        return new Audit(pids,calls,spawns,blockedCalls,originalFailures,allow,configPin,files);
    }
    private static boolean flag(Object value)throws IOException {if(!(value instanceof Boolean result))throw new IOException("Require explicit installation-policy booleans");return result;}
    private static Pin strictPin(Object value,int maximum)throws IOException {keys(object(value),"bytes","sha256");return fingerprint(value,maximum);}
    private static String imageHash(String url) {try{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("MD5").digest(url.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}}
    private static boolean selectedLinuxLocalImage(String url) {
        // Selected Python 3.12 urlsplit removes leading C0/space and embedded
        // tab/CR/LF before recognizing an ASCII URI scheme. ON_WINDOWS is false
        // for this producer profile even when the portable reader runs on Windows.
        int start=0;while(start<url.length()&&url.charAt(start)<=0x20)start++;String normalized=url.substring(start).replace("\t","").replace("\r","").replace("\n","");
        var scheme=java.util.regex.Pattern.compile("^([A-Za-z][A-Za-z0-9+.-]*):").matcher(normalized);return !scheme.find()||scheme.group(1).equalsIgnoreCase("file");
    }
}
