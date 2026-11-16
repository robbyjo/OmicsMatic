/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import org.jlinalg.alignment.SambambaStreamingBamAudit;
import static org.jlinalg.cli.SambambaStreamingWorkflowFiles.*;

/** Complete v2 streaming bundle/request/process coherence and recorded BAM transition checks. */
final class SambambaStreamingWorkflowBundle {
    static final Set<String> CAPTURE_KEYS=Set.of("profile","kind","request_path_requested","request_directory","request_sha256",
        "bam_requested","bam_request_absolute","bam_resolved","binary_requested","binary_request_absolute","binary_resolved",
        "input_sha256","execution_root","output_requested","capture_source","parent_bundle","parent_marker_sha256","jvm_pid");
    static final Set<String> MARKER_KEYS=Set.of("profile","status","kind","selected_bam","bai_independently_qualified","new_catalog_credit");
    record Bundle(Path root,Config config,Properties capture,Properties runtime,String markerSha,Map<String,String> members) {
        Bundle{members=Map.copyOf(members);}
    }
    private SambambaStreamingWorkflowBundle(){ }
    static String selected(Config c){return c.remove()?"deduplicated.bam":"marked.bam";}
    static Path resolve(Path base,String name){Path p=Path.of(name);return(p.isAbsolute()?p:base.resolve(p)).toAbsolutePath().normalize();}
    static void coherent(Path root,Config c,Properties capture)throws Exception {
        Config original=config(load(root.resolve("request.properties")));scientificCoherence(original,c);
        if(!capture.stringPropertyNames().equals(CAPTURE_KEYS)||!PROFILE.equals(capture.getProperty("profile")))throw new IOException("Complete capture fields/profile differ");
        if(!Set.of("analysis","verification").contains(capture.getProperty("kind")))throw new IOException("Capture kind differs");
        if(!sha(root.resolve("request.properties")).equals(capture.getProperty("request_sha256"))||!sha(root.resolve("input.bam")).equals(capture.getProperty("input_sha256")))
            throw new IOException("Original request/captured input identity differs");
        Path base=Path.of(capture.getProperty("request_directory"));
        if(!base.isAbsolute()||!base.normalize().equals(base))throw new IOException("Recorded request directory is not absolute/normalized");
        if(!original.get("bam").equals(capture.getProperty("bam_requested"))||!original.get("sambamba").equals(capture.getProperty("binary_requested"))
                ||!resolve(base,original.get("bam")).toString().equals(capture.getProperty("bam_request_absolute"))
                ||!resolve(base,original.get("sambamba")).toString().equals(capture.getProperty("binary_request_absolute"))
                ||!c.get("sambamba").equals(capture.getProperty("binary_resolved")))throw new IOException("Original/effective path provenance contradicts the captured request");
        for(String key:List.of("bam_resolved","binary_resolved","execution_root","capture_source")){
            Path p=Path.of(capture.getProperty(key));if(!p.isAbsolute()||!p.normalize().equals(p))throw new IOException("Nonabsolute or nonnormalized recorded path: "+key);
        }
        if(capture.getProperty("kind").equals("analysis")){
            if(!capture.getProperty("parent_bundle").isEmpty()||!capture.getProperty("parent_marker_sha256").isEmpty()||!capture.getProperty("capture_source").equals(capture.getProperty("bam_resolved")))
                throw new IOException("Original-run capture provenance differs");
        }else{
            Path parent=Path.of(capture.getProperty("parent_bundle"));
            if(!parent.isAbsolute()||!parent.normalize().equals(parent)||!parent.resolve("input.bam").toString().equals(capture.getProperty("capture_source"))
                    ||!capture.getProperty("parent_marker_sha256").matches("[0-9a-f]{64}"))throw new IOException("Replay capture provenance differs");
        }
        positive(capture.getProperty("jvm_pid"),"jvm_pid");
    }
    static List<String> phases(Config c){var result=new ArrayList<>(SambambaStreamingWorkflowRuntime.PROBES);result.add("mark");if(c.remove())result.add("remove");return result;}
    static List<String> sourceCommand(String phase,Config c,Properties capture){
        Path binary=Path.of(c.get("sambamba")),historical=Path.of(capture.getProperty("execution_root"));
        return switch(phase){
            case "version"->List.of(binary.toString(),"--version");
            case "ldd-source"->List.of("/usr/bin/ldd",binary.toString());
            case "ldd-setpriv"->List.of("/usr/bin/ldd","/usr/bin/setpriv");
            case "ldd-prlimit"->List.of("/usr/bin/ldd","/usr/bin/prlimit");
            case "ldd-bash"->List.of("/usr/bin/ldd","/usr/bin/bash");
            case "mark"->command(binary,c,historical,false);
            case "remove"->command(binary,c,historical,true);
            default->throw new IllegalArgumentException("Unknown recorded phase");
        };
    }
    static void process(Path root,String phase,Config c,Properties capture)throws Exception {
        boolean analysis=phase.equals("mark")||phase.equals("remove");long wall=analysis?c.number("wall_ms"):10_000,address=analysis?c.addressBytes():512L<<20;
        List<String> source=sourceCommand(phase,c,capture),launch=new ArrayList<>(List.of("/usr/bin/setpriv","--pdeathsig","KILL","/usr/bin/prlimit","--as="+address,"--"));launch.addAll(source);
        Properties pid=load(root.resolve(phase+".pid.properties"));Set<String> expected=new TreeSet<>(Set.of("pid","parent_pid","start_instant","parent_death_signal_requested","address_space_limit_method","source_argc","launch_argc","linux_start_ticks"));
        checkArgs(pid,"source",source,expected);checkArgs(pid,"launch",launch,expected);
        if(!pid.stringPropertyNames().equals(expected)||!"KILL".equals(pid.getProperty("parent_death_signal_requested"))||!"prlimit_RLIMIT_AS".equals(pid.getProperty("address_space_limit_method"))
                ||!capture.getProperty("jvm_pid").equals(pid.getProperty("parent_pid")))throw new IOException("Complete original launch/process provenance differs: "+phase);
        positive(pid.getProperty("pid"),"pid");
        if(!pid.getProperty("start_instant").equals("exited-before-observation"))Instant.parse(pid.getProperty("start_instant"));
        if(!pid.getProperty("linux_start_ticks").equals("exited-before-observation"))positive(pid.getProperty("linux_start_ticks"),"linux_start_ticks");
        Properties result=load(root.resolve(phase+".process.properties"));
        if(!result.stringPropertyNames().equals(Set.of("phase","exit_code","process_elapsed_ms","elapsed_ms","wall_limit_ms","address_space_limit_bytes","peak_observed_rss_bytes","rss_samples","work_limits_are_sampled_not_os_quota","owned_descendant_pids","owned_processes_running_after_cleanup")))
            throw new IOException("Complete successful process receipt fields differ: "+phase);
        int exit=phase.equals("ldd-source")&&c.get("edition").equals("0.8.2")?1:0;
        if(!phase.equals(result.getProperty("phase"))||!Integer.toString(exit).equals(result.getProperty("exit_code"))||!Long.toString(wall).equals(result.getProperty("wall_limit_ms"))
                ||!Long.toString(address).equals(result.getProperty("address_space_limit_bytes"))||!"false".equals(result.getProperty("owned_processes_running_after_cleanup"))
                ||!"true".equals(result.getProperty("work_limits_are_sampled_not_os_quota")))throw new IOException("Successful process status/limits differ: "+phase);
        long elapsed=nonnegative(result.getProperty("process_elapsed_ms"),"process_elapsed_ms");
        if(elapsed>wall||nonnegative(result.getProperty("elapsed_ms"),"elapsed_ms")<elapsed)throw new IOException("Recorded process exceeded its declared wall bound");
        nonnegative(result.getProperty("peak_observed_rss_bytes"),"peak_observed_rss_bytes");nonnegative(result.getProperty("rss_samples"),"rss_samples");
        if(!result.getProperty("owned_descendant_pids").matches("\\[(?:[0-9]+(?:, [0-9]+)*)?\\]"))throw new IOException("Malformed owned descendant receipt");
    }
    private static void checkArgs(Properties p,String prefix,List<String> values,Set<String> keys)throws IOException {
        if(!Integer.toString(values.size()).equals(p.getProperty(prefix+"_argc")))throw new IOException("Recorded argument count differs: "+prefix);
        for(int i=0;i<values.size();i++){String key=prefix+"_argv."+i;keys.add(key);if(!values.get(i).equals(p.getProperty(key)))throw new IOException("Recorded original/effective command contradicts request: "+key);}
    }
    private static long nonnegative(String value,String name)throws IOException{
        try{long n=Long.parseLong(value);if(n<0||!Long.toString(n).equals(value))throw new NumberFormatException();return n;}catch(RuntimeException e){throw new IOException("Invalid recorded "+name,e);}
    }
    private static void positive(String value,String name)throws IOException{if(nonnegative(value,name)<1)throw new IOException("Invalid positive "+name);}

    /** Incremental decision output/comparison with a digest tied to the actual audited BAM streams. */
    private static final class Ledger implements AutoCloseable {
        final java.io.OutputStream output;final java.io.InputStream expected;final java.security.MessageDigest digest;
        final long maximum;long bytes;String hash;
        Ledger(Path path,boolean compare,long maximum)throws Exception {
            this.maximum=maximum;digest=java.security.MessageDigest.getInstance("SHA-256");
            if(compare&&!Files.isRegularFile(path,java.nio.file.LinkOption.NOFOLLOW_LINKS))throw new IOException("Require the regular recorded decision ledger");
            expected=compare?new java.io.BufferedInputStream(Files.newInputStream(path)):null;
            output=compare?null:new java.io.BufferedOutputStream(Files.newOutputStream(path,java.nio.file.StandardOpenOption.CREATE_NEW));
        }
        void line(String line)throws IOException {
            byte[] raw=line.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            if(bytes>maximum-raw.length)throw new IOException("Decision ledger exceeds the declared workspace capacity");
            if(expected!=null){if(!Arrays.equals(expected.readNBytes(raw.length),raw))throw new IOException("Complete native decision ledger differs");}
            else output.write(raw);
            bytes+=raw.length;digest.update(raw);
        }
        @Override public void close()throws IOException {
            try{if(expected!=null&&expected.read()!=-1)throw new IOException("Unexpected trailing native decision ledger bytes");}
            finally{if(expected!=null)expected.close();if(output!=null)output.close();}
            hash=java.util.HexFormat.of().formatHex(digest.digest());
        }
    }
    static Map<String,String> audit(Path root,Config c,Properties capture,boolean compare)throws Exception {
        String mark=headerCommand(sourceCommand("mark",c,capture)),remove=c.remove()?headerCommand(sourceCommand("remove",c,capture)):null;
        Map<String,String> audited=new java.util.TreeMap<>();SambambaStreamingBamAudit.Summary result;
        long available=compare?c.workBytes():c.workBytes()-inventory(root,c,false).bytes();
        Ledger ledger=new Ledger(root.resolve("decisions.tsv"),compare,available);
        try(ledger){
            ledger.line("ordinal\tname\toriginal_flag\tmarked_flag\tscored_by_source\tquality_score\tfive_prime_source_coordinate\n");
            result=SambambaStreamingBamAudit.audit(root.resolve("input.bam"),root.resolve("marked.bam"),c.remove()?root.resolve("deduplicated.bam"):null,c.get("edition"),mark,remove,c.auditLimits(),row->{
                ledger.line(row.ordinal()+"\t"+row.name()+"\t"+row.originalFlag()+"\t"+row.markedFlag()+"\t"+row.scoredBySource()+"\t"
                    +(row.scoredBySource()?Long.toString(row.sourceQualityScore()):"NA_not_scored")+"\t"
                    +(row.scoredBySource()?Integer.toString(row.sourceFivePrimeCoordinate()):"NA_not_scored")+"\n");
            });
        }
        if(!result.input().sha256().equals(capture.getProperty("input_sha256")))throw new IOException("Actual audited input stream differs from the captured identity");
        audited.put("input.bam",result.input().sha256());audited.put("marked.bam",result.marked().sha256());
        if(c.remove())audited.put("deduplicated.bam",result.removed().sha256());audited.put("decisions.tsv",ledger.hash);
        String summary="metric\tvalue\ninput_records\t"+result.records()+"\nmarked_duplicates\t"+result.markedDuplicates()+"\nnewly_marked\t"+result.newlyMarked()+"\ncleared\t"+result.cleared()+"\nretained_after_removal\t"+result.retainedAfterRemoval()
            +"\nselected_bam\t"+selected(c)+"\nindependent_duplicate_classifier\tfalse\nbai_independently_qualified\tfalse\n"
            +"input_stream_sha256\t"+result.input().sha256()+"\nmarked_stream_sha256\t"+result.marked().sha256()+"\nremoved_stream_sha256\t"+(c.remove()?result.removed().sha256():"not_requested")+"\n";
        byte[] raw=summary.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if(compare){
            try(var in=Files.newInputStream(root.resolve("audit.tsv"))){if(!Arrays.equals(in.readNBytes(raw.length+1),raw))throw new IOException("Complete native audit summary differs");}
        }else write(root.resolve("audit.tsv"),raw);
        audited.put("audit.tsv",sha(raw));return Map.copyOf(audited);
    }
    private static void auditedIdentities(Map<String,String> inventory,Map<String,String> audited)throws IOException {
        for(var row:audited.entrySet())if(!row.getValue().equals(inventory.get(row.getKey())))throw new IOException("Actual audited stream/ledger changed before admission: "+row.getKey());
    }
    static Set<String> requiredFiles(Config c,boolean verification){
        Set<String> result=new TreeSet<>(Set.of("WARNINGS.md","request.properties","capture.properties","effective.properties","implementation.properties","runtime-before.properties","runtime-after.properties","audit.tsv","decisions.tsv","input.bam","marked.bam","marked.bam.bai"));
        for(String phase:phases(c))for(String suffix:List.of(".stdout",".stderr",".pid.properties",".process.properties"))result.add(phase+suffix);
        if(c.remove()){result.add("deduplicated.bam");result.add("deduplicated.bam.bai");}
        if(verification)result.add("comparison.properties");return result;
    }
    static Map<String,String> payload(Path root,Config c,Properties capture)throws Exception {
        coherent(root,c,capture);
        if(!implementation().equals(load(root.resolve("implementation.properties"))))throw new IOException("Exact adapter/auditor implementation classes differ; use the pinned build");
        if(!Files.readString(root.resolve("WARNINGS.md")).equals(SambambaStreamingWorkflowCli.NOTICE))throw new IOException("Complete profile warnings differ");
        Properties before=load(root.resolve("runtime-before.properties")),after=load(root.resolve("runtime-after.properties"));
        if(!before.equals(after)||!BINARIES.get(c.get("edition")).equals(before.getProperty("binary_sha256"))||!c.get("sambamba").equals(before.getProperty("binary_path")))throw new IOException("Original before/after runtime provenance differs");
        for(String phase:phases(c))process(root,phase,c,capture);
        Map<String,String> audited=audit(root,c,capture,true);
        boolean verification=capture.getProperty("kind").equals("verification");
        if(verification){
            Properties p=load(root.resolve("comparison.properties"));
            if(!p.stringPropertyNames().equals(Set.of("parent_bundle","parent_marker_sha256","marked_records_and_headers_equal","removed_records_and_headers_equal"))
                    ||!capture.getProperty("parent_bundle").equals(p.getProperty("parent_bundle"))||!capture.getProperty("parent_marker_sha256").equals(p.getProperty("parent_marker_sha256"))
                    ||!"true".equals(p.getProperty("marked_records_and_headers_equal"))||!(c.remove()?"true":"not_requested").equals(p.getProperty("removed_records_and_headers_equal")))throw new IOException("Original/replay comparison receipt contradicts capture");
        }
        Inventory inventory=inventory(root,c,false);Set<String> files=new TreeSet<>();
        for(var entry:inventory.members().entrySet()){
            if(entry.getValue().equals("DIRECTORY")){
                if(!Set.of("tmp-mark","tmp-remove").contains(entry.getKey())||(!c.remove()&&entry.getKey().equals("tmp-remove")))throw new IOException("Unqualified residual workspace directory: "+entry.getKey());
            }else if(!entry.getKey().equals("COMPLETED.properties"))files.add(entry.getKey());
        }
        if(!files.equals(requiredFiles(c,verification)))throw new IOException("Complete workflow payload has unexpected/missing files");
        return audited;
    }
    static Bundle read(Path root)throws Exception {
        Config c=config(load(root.resolve("effective.properties")));Properties marker=load(root.resolve("COMPLETED.properties")),capture=load(root.resolve("capture.properties"));
        Inventory actual=inventory(root,c,true);Map<String,String> members=new java.util.TreeMap<>(actual.members());members.remove("COMPLETED.properties");
        checkReceipt(marker,c,capture,members);auditedIdentities(members,payload(root,c,capture));
        return new Bundle(root,c,capture,load(root.resolve("runtime-before.properties")),sha(root.resolve("COMPLETED.properties")),members);
    }
    static void checkReceipt(Properties marker,Config c,Properties capture,Map<String,String> members)throws IOException {
        Set<String> keys=new TreeSet<>(MARKER_KEYS);members.keySet().forEach(n->keys.add("member."+n));
        if(!marker.stringPropertyNames().equals(keys)||!PROFILE.equals(marker.getProperty("profile"))||!"completed".equals(marker.getProperty("status"))
                ||!capture.getProperty("kind").equals(marker.getProperty("kind"))||!selected(c).equals(marker.getProperty("selected_bam"))
                ||!"false".equals(marker.getProperty("bai_independently_qualified"))||!"0".equals(marker.getProperty("new_catalog_credit")))throw new IOException("Complete workflow completion receipt differs");
        for(var row:members.entrySet())if(!row.getValue().equals(marker.getProperty("member."+row.getKey())))throw new IOException("Complete workflow member identity differs: "+row.getKey());
    }
    static void complete(Path root,Config c,Properties capture)throws Exception {
        Map<String,String> audited=payload(root,c,capture);Inventory inventory=inventory(root,c,true);auditedIdentities(inventory.members(),audited);Properties marker=new Properties();
        marker.setProperty("profile",PROFILE);marker.setProperty("status","completed");marker.setProperty("kind",capture.getProperty("kind"));marker.setProperty("selected_bam",selected(c));
        marker.setProperty("bai_independently_qualified","false");marker.setProperty("new_catalog_credit","0");inventory.members().forEach((n,h)->marker.setProperty("member."+n,h));
        byte[] raw=bytes(marker);
        if(inventory.entries()+1>c.number("max_files")||inventory.bytes()+raw.length>c.workBytes())throw new IOException("Prospective completion receipt exceeds the declared workspace capacity");
        write(root.resolve("COMPLETED.properties"),raw);
        Inventory finalInventory=inventory(root,c,true);Map<String,String> payload=new java.util.TreeMap<>(finalInventory.members());payload.remove("COMPLETED.properties");
        if(!Arrays.equals(Files.readAllBytes(root.resolve("COMPLETED.properties")),raw))throw new IOException("Completion receipt readback differs");
        checkReceipt(marker,c,capture,payload);auditedIdentities(payload,audited);
    }
}
