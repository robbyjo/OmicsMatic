/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Supervised xsv subprocess for the unmodified offline FAVOR R script. Only its
 * exact internally staged join/cat argument plan is admitted. Stdout is inherited
 * unchanged for the original shell CSV redirection; diagnostics go to stderr and
 * every admitted attempt receives a separate terminal JSON event.
 */
public final class FavorXsvSupervisor {
    private FavorXsvSupervisor() { }
    static final String XSV_SHA256="b57364e384b7e2321215bc9b200d8afe6931a450ac4425df8220067a93959925";
    private static final Set<String> KEYS=Set.of("schema_version","work","xsv","chromosome","shards","timeout_ms");
    record Spec(Path request,String digest,Path work,Path xsv,int chromosome,int shards,long timeoutMillis) { }
    private static final class Unavailable extends IOException {
        private static final long serialVersionUID=1L;
        Unavailable(String message){super(message);}
    }

    /**
     * Entry point used by the original R script's configured xsv command.
     * @param args --request FILE --request-sha256 SHA256 -- exact_original_xsv_arguments
     */
    public static void main(String[] args){System.exit(run(args,System.err));}

    static List<List<String>> plan(int chromosome,int shards)throws IOException {
        if(chromosome<1||chromosome>22||shards<2||shards>4096)throw new IOException("FAVOR supervisor requires one autosome and2..4096 selected shards");
        var out=new ArrayList<List<String>>();var cat=new ArrayList<String>(List.of("cat","rows"));
        for(int index=1;index<=shards;index++) {
            String base="./output/chr"+chromosome+"/";
            out.add(List.of("join","--left","VarInfo",base+"VarInfo_chr"+chromosome+"_"+index+".csv","variant_vcf","./database/chr"+chromosome+"_"+index+".csv"));
            cat.add(base+"Anno_chr"+chromosome+"_"+index+".csv");
        }
        out.add(List.copyOf(cat));return List.copyOf(out);
    }
    static Spec request(String[] args,Path actualWork)throws Exception {
        if(args.length<6||!args[0].equals("--request")||!args[2].equals("--request-sha256")||!args[4].equals("--")||!args[3].matches("[0-9a-f]{64}"))throw new IOException("Expected --request FILE --request-sha256 SHA256 -- original_xsv_arguments");
        Path file=Path.of(args[1]).toAbsolutePath().normalize();byte[] raw=FumaAssociationInputs.carrier(file);
        if(!AdmixtureInputs.digest(raw).equals(args[3]))throw new IOException("FAVOR supervisor request SHA-256 differs");
        Map<?,?> data=AdmixtureInputs.decode(raw);
        if(!data.keySet().equals(KEYS)||!(data.get("schema_version") instanceof Number schema)||schema.doubleValue()!=1)throw new IOException("Unsupported FAVOR supervisor request schema/keys");
        for(String key:KEYS)if(!key.equals("schema_version")&&!(data.get(key) instanceof String))throw new IOException("FAVOR supervisor request values must be strings: "+key);
        Path declared=Path.of((String)data.get("work"));Path binary=Path.of((String)data.get("xsv"));
        if(!declared.isAbsolute()||!binary.isAbsolute()||!declared.normalize().equals(declared)||!binary.normalize().equals(binary)||Files.isSymbolicLink(declared))throw new IOException("Require absolute normalized nonlinked working and executable paths");
        Path work=declared.toRealPath();if(!work.equals(actualWork.toRealPath()))throw new IOException("FAVOR supervisor working directory differs from its exact request");
        if(!work.resolve("supervisor.json").equals(file)||!work.toString().codePoints().allMatch(c->c<128&&!Character.isWhitespace(c)))throw new IOException("Use the internally staged ASCII work/supervisor.json request");
        int chromosome=Math.toIntExact(TranscriptSiteInputs.whole((String)data.get("chromosome"))),shards=Math.toIntExact(TranscriptSiteInputs.whole((String)data.get("shards")));plan(chromosome,shards);
        long timeout=TranscriptSiteInputs.whole((String)data.get("timeout_ms"));if(timeout<1||timeout>3_600_000)throw new IOException("FAVOR xsv timeout_ms must be1..3600000");
        return new Spec(file,args[3],work,binary,chromosome,shards,timeout);
    }
    static int run(String[] args,PrintStream errors) {
        Path event=null;Process child=null;Thread hook=null;var record=new LinkedHashMap<String,Object>();int code=2;
        try {
            Spec spec=request(args,Path.of("").toRealPath());List<String> original=List.of(java.util.Arrays.copyOfRange(args,5,args.length));
            Path events=spec.work().resolve("xsv-events");if(Files.exists(events,LinkOption.NOFOLLOW_LINKS)){if(!Files.isDirectory(events,LinkOption.NOFOLLOW_LINKS)||Files.isSymbolicLink(events))throw new IOException("FAVOR xsv event path is not an owned directory");}else Files.createDirectory(events);
            try(var files=Files.list(events)){if(files.count()>=10_000)throw new IOException("FAVOR xsv event population capacity exceeded");}
            event=events.resolve("event-"+UUID.randomUUID()+".json");record.put("schema_version",1);record.put("request_sha256",spec.digest());record.put("started_at",Instant.now().toString());record.put("supervisor_pid",ProcessHandle.current().pid());record.put("arguments",original);record.put("status","started");
            Files.writeString(event,RegenieRuntime.json(record)+"\n",StandardOpenOption.CREATE_NEW);
            List<List<String>> plan=plan(spec.chromosome(),spec.shards());int planned=plan.indexOf(original);
            if(planned<0)throw new IOException("FAVOR xsv arguments differ from the exact staged join/cat plan");record.put("planned_step",planned+1);record.put("xsv",spec.xsv().toString());record.put("xsv_sha256",XSV_SHA256);
            if(!System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("linux")||!List.of("amd64","x86_64").contains(System.getProperty("os.arch")))throw new Unavailable("Pinned FAVOR xsv profile requires Linux x86_64;run the Linux Java workflow on a permitted host");
            verify(spec,true);validateInputs(spec,original);
            var command=new ArrayList<String>();command.add(spec.xsv().toString());command.addAll(original);record.put("command",command);
            Path scratch=spec.work().resolve("xsv-tmp");if(Files.exists(scratch,LinkOption.NOFOLLOW_LINKS)){if(!Files.isDirectory(scratch,LinkOption.NOFOLLOW_LINKS)||Files.isSymbolicLink(scratch))throw new IOException("Invalid FAVOR xsv temporary directory");}else Files.createDirectory(scratch);
            var builder=new ProcessBuilder(command).directory(spec.work().toFile()).inheritIO();var environment=builder.environment();environment.clear();environment.put("PATH","/usr/bin:/bin");environment.put("HOME",spec.work().toString());environment.put("TMPDIR",scratch.toString());environment.put("LANG","C.UTF-8");environment.put("LC_ALL","C.UTF-8");
            try{child=builder.start();}catch(IOException unavailable){throw new Unavailable("Admitted xsv could not start: "+unavailable.getMessage());}
            record.put("child_pid",child.pid());Process owned=child;hook=new Thread(()->{try{stop(owned);}catch(Exception ignored){/* Parent also requires a terminal event and audits owned children. */}},"favor-owned-xsv-shutdown");Runtime.getRuntime().addShutdownHook(hook);
            if(!child.waitFor(spec.timeoutMillis(),TimeUnit.MILLISECONDS)){record.put("timed_out",true);stop(child);throw new IOException("FAVOR xsv command timed out;owned child was stopped");}
            record.put("child_exit_code",child.exitValue());verify(spec,false);
            if(child.exitValue()!=0)throw new IOException("Original xsv command failed with exit "+child.exitValue());
            code=0;record.put("status","completed");
        } catch(Unavailable unavailable) {
            code=3;record.put("status","unavailable");record.put("cause",unavailable.toString());errors.println("FAVOR XSV UNAVAILABLE: "+unavailable.getMessage()+"\nRequired action: provide the pinned permitted Linux xsv runtime and re-admit a NEW workflow. No command installs software. The parent must reject or skip this affected analysis,never infer completion from R exit0.");
        } catch(Exception failure) {
            code=2;record.put("status","failed");record.put("cause",failure.toString());errors.println("FAVOR XSV FAILED: "+failure.getMessage()+"\nRequired action: inspect the supervisor event and native logs;correct the named request,input,execution or timeout cause and rerun to a NEW output. The parent must reject this workflow even if R exits0.");
            if(failure instanceof InterruptedException)Thread.currentThread().interrupt();
        } finally {
            if(child!=null&&child.isAlive())try{stop(child);}catch(Exception failure){code=2;record.put("cleanup_error",failure.toString());record.put("status","failed");errors.println("FAVOR XSV CLEANUP FAILED. Required action: stop the recorded owned child and retain this analysis as failed: "+failure);}
            if(hook!=null)try{Runtime.getRuntime().removeShutdownHook(hook);}catch(IllegalStateException shuttingDown){/* The shutdown hook owns child termination. */}
            record.put("exit_code",code);record.put("finished_at",Instant.now().toString());if(child!=null){record.put("owned_child_alive",child.isAlive());if(!child.isAlive())record.put("child_exit_code",child.exitValue());}
            if(event!=null)try{Path temporary=event.resolveSibling(event.getFileName()+".tmp");Files.writeString(temporary,RegenieRuntime.json(record)+"\n",StandardOpenOption.CREATE_NEW);Files.move(temporary,event,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(IOException failed){code=2;errors.println("FAVOR XSV EVENT PUBLICATION FAILED: "+failed+"\nRequired action: retain this workflow as failed;inspect started/temporary event files and do not infer completion.");}
        }
        return code;
    }
    private static void validateInputs(Spec spec,List<String> args)throws IOException {
        List<String> paths=args.get(0).equals("join")?List.of(args.get(3),args.get(5)):args.subList(2,args.size());
        for(String value:paths){Path path=spec.work().resolve(value).normalize();if(!path.startsWith(spec.work())||!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||Files.isSymbolicLink(path)||!path.toRealPath().equals(path))throw new IOException("Missing,linked or nonregular staged xsv input: "+value);}
    }
    private static void verify(Spec spec,boolean admission)throws IOException {
        if(!Files.isRegularFile(spec.request(),LinkOption.NOFOLLOW_LINKS)||Files.isSymbolicLink(spec.request())||!FollowupSupport.hash(spec.request()).equals(spec.digest()))throw new IOException("FAVOR supervisor request changed during execution");
        if(!Files.isRegularFile(spec.xsv(),LinkOption.NOFOLLOW_LINKS)||Files.isSymbolicLink(spec.xsv())||!Files.isExecutable(spec.xsv())||!FollowupSupport.hash(spec.xsv()).equals(XSV_SHA256)) {
            String message="Pinned FAVOR xsv binary is unavailable,forbidden,linked or changed";if(admission)throw new Unavailable(message);throw new IOException(message+" during execution");
        }
    }
    private static void stop(Process child)throws IOException {
        boolean interrupted=Thread.interrupted();try {
            child.descendants().forEach(ProcessHandle::destroyForcibly);if(child.isAlive())child.destroyForcibly();
            try{if(!child.waitFor(10,TimeUnit.SECONDS))throw new IOException("Owned xsv process did not terminate: "+child.pid());}catch(InterruptedException failure){interrupted=true;throw new IOException("Interrupted while confirming owned xsv termination",failure);}
        } finally {if(interrupted)Thread.currentThread().interrupt();}
    }
}
