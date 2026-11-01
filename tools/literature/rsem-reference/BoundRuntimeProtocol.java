/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipInputStream;
import org.jlinalg.sequence.RsemAbundance;
import org.jlinalg.sequence.RsemCounts;
import org.jlinalg.sequence.RsemModel;
import org.jlinalg.sequence.RsemReference;
import org.jlinalg.sequence.RsemResults;

/** Development-only exact-original method controls, not a public study fitting command. */
public final class BoundRuntimeProtocol {
    private BoundRuntimeProtocol() { }
    private static final String INPUTS="742c4d5feb9118d5bed6b9c6e2b6f5ae89b7924f083be490823f6045fe123338";
    private static final Set<String> PROCESS=Set.of("process.stdout","process.stderr","process.json","launch.json","WARNINGS.md");
    private static final RsemProcess.Limits FIT=new RsemProcess.Limits(180000,3221225472L,180,67108864,256,268435456,4096,8,64,2147483648L,25);
    private static String hash(Path path)throws IOException{return RepeatInputs.hash(path,1073741824,()->{});}
    private static Map<String,String> files(Path root)throws IOException{
        var result=new TreeMap<String,String>();try(var paths=Files.walk(root)){
            for(Path path:paths.toList()){
                if(Files.isSymbolicLink(path))throw new IOException("Linked control member: "+path);
                if(Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))result.put(root.relativize(path).toString().replace('\\','/'),hash(path));
            }
        }return result;
    }
    private static void same(Path root,Map<?,?> expected,boolean allowProcess)throws IOException{
        var actual=new TreeMap<>(files(root));if(allowProcess)PROCESS.forEach(actual::remove);
        if(!actual.equals(expected)){
            var changed=new ArrayList<String>();for(var e:expected.entrySet())if(!e.getValue().equals(actual.get(e.getKey())))changed.add(e.getKey().toString());
            throw new IOException("Complete original scientific output population differs: "+root+" changed="+changed+" expected="+expected.size()+" actual="+actual.size());
        }
    }
    private static void require(boolean value,String message)throws IOException{if(!value)throw new IOException(message);}
    private static void write(Path path,Object value)throws IOException{Files.write(path,RsemJson.bytes(value),StandardOpenOption.CREATE_NEW);}
    /**
     * @param args release, original built source, exact source-linked decoder, declared library directory, NEW private directory
     * @throws Exception any changed input, failed original control, altered output or ownership failure
     */
    public static void main(String[] args)throws Exception{
        if(args.length!=5)throw new IllegalArgumentException("release ORIGINAL_SOURCE_BUILD SOURCE_LINKED_DECODER LIBRARY_DIRECTORY NEW_PRIVATE_DIRECTORY");
        String version=args[0];Path base=Path.of(args[4]);RsemPayload.originalPath(base);Files.createDirectory(base);
        Path input=Files.createDirectory(base.resolve("inputs")),shadow=Files.createDirectory(base.resolve("lookup-shadow"));
        byte[] raw;try(var stream=BoundRuntimeProtocol.class.getResourceAsStream("/org/jlinalg/omics/rsem-health/inputs.zip")){
            if(stream==null)throw new IOException("Missing pinned original control inputs");raw=stream.readNBytes(1000001);
        }
        require(AdmixtureInputs.digest(raw).equals(INPUTS),"Original control ZIP checksum differs");
        var names=new HashSet<String>();
        try(var zip=new ZipInputStream(new java.io.ByteArrayInputStream(raw))){
            java.util.zip.ZipEntry entry;while((entry=zip.getNextEntry())!=null){
                require(entry.getName().matches("[A-Za-z0-9_.-]+")&&!entry.isDirectory()&&names.add(entry.getName()),"Unexpected/repeated control input member");
                byte[] bytes=zip.readNBytes(1000001);require(bytes.length<=1000000,"Control input capacity exceeded");Files.write(input.resolve(entry.getName()),bytes,StandardOpenOption.CREATE_NEW);
            }
        }
        require(names.size()==8,"Incomplete original control input population");
        var inputPins=files(input);RsemProcess.Check inputCheck=()->same(input,inputPins,false);
        Map<?,?> golden=RepeatBundle.object(Files.readAllBytes(input.resolve("expected-science.json")));
        Map<?,?> cases=(Map<?,?>)golden.get("cases"),references=(Map<?,?>)golden.get("references");
        var payload=RsemPayload.stage(Path.of(args[1]),version,Path.of(args[2]),List.of(shadow,Path.of(args[3])),base.resolve("payload"),()->{});
        var hostLimits=new RsemHost.Limits(new RepeatHostFiles.Limits(536870912,8589934592L,100000,128,67108864),
                new RsemProcess.Limits(60000,1073741824,60,16777216,256,1073741824,100000,8,64,536870912,25),1024);
        System.out.println(version+" binding actual original scripts and declared host");
        var context=RsemRuntime.bind(payload,base.resolve("binding"),hostLimits,600000,inputCheck,()->{});
        Path preparation=base.resolve("prepare"),reference=preparation.resolve("reference");
        context.executeOnly(RsemRuntime.Program.PREPARE,List.of("--transcript-to-gene-map",input.resolve("gene-map.tsv").toString(),input.resolve("transcripts.fa").toString(),reference.toString()),
                preparation,FIT,300000,inputCheck,()->{},List.of(input));
        same(preparation,(Map<?,?>)references.get(version),true);var referencePins=files(preparation);
        RsemProcess.Check scientificInputs=()->{inputCheck.verify();same(preparation,referencePins,false);};
        var ref=RsemReference.read(reference,new RsemReference.Limits(100,100,100000,1000,1000000,1000000));
        var release=RsemAbundance.Release.valueOf("V"+version.replace('.','_'));var outcomes=new ArrayList<Map<String,Object>>();int compared=0;
        for(String name:List.of("paired-em","reverse-em","single-em","paired-posterior","noise")){
            Path work=base.resolve(name),sample=work.resolve("sample");var command=new ArrayList<String>(List.of("--alignments","--no-bam-output","--keep-intermediate-files","--seed","1618","--num-threads","1"));
            String sam=name.equals("single-em")?"single.sam":name.equals("reverse-em")?"reverse.sam":name.equals("noise")?"noise.sam":"paired.sam";
            if(name.equals("single-em"))command.addAll(List.of("--fragment-length-mean","200","--fragment-length-sd","20"));else command.add("--paired-end");
            if(name.equals("reverse-em"))command.addAll(version.equals("1.2.28")?List.of("--forward-prob","0"):List.of("--strandedness","reverse"));
            if(name.equals("paired-posterior"))command.addAll(List.of("--calc-pme","--calc-ci","--estimate-rspd"));
            command.addAll(List.of(input.resolve(sam).toString(),reference.toString(),sample.toString()));
            boolean expectedFailure=!cases.containsKey(version+"/"+name);System.out.println(version+" "+name+" original execution");
            try{
                context.executeOnly(RsemRuntime.Program.QUANTIFY,command,work,FIT,300000,scientificInputs,()->{},List.of(input,preparation));
                require(!expectedFailure,"Historical original zero-alignment failure disappeared");
            }catch(IOException failure){
                if(!expectedFailure)throw failure;Map<?,?> ledger=RepeatBundle.object(Files.readAllBytes(work.resolve("process.json")));
                require("FAIL2-retained-no-result".equals(ledger.get("status"))&&ledger.get("exit_code") instanceof Number code&&code.intValue()!=0
                        &&List.of().equals(ledger.get("owned_live_after_cleanup")),"Expected original failure lacks complete exit/cleanup evidence");
                require(!Files.exists(work.resolve("COMPLETED.json"))&&!Files.exists(work.resolve("ADMITTED.json")),"Failed original control published a completion marker");
                outcomes.add(Map.of("case",name,"status","retained-expected-original-failure","exit_code",ledger.get("exit_code")));continue;
            }
            Map<?,?> expected=(Map<?,?>)cases.get(version+"/"+name);same(work,expected,true);compared+=expected.size();
            var counts=RsemCounts.read(work.resolve("sample.stat/sample.cnt"),1000,10000,1000,100000,100000);
            var fit=RsemModel.readFitState(work.resolve("sample.stat/sample.model"),work.resolve("sample.stat/sample.theta"),counts,ref.transcripts().size(),release,
                    new RsemModel.Limits(100,10000,10000,10000,100000,1000000,1000000));
            var audit=RsemResults.audit(work.resolve("sample.isoforms.results"),work.resolve("sample.genes.results"),ref,counts,fit,release,
                    name.equals("paired-posterior")?RsemResults.Posterior.INTERVAL:RsemResults.Posterior.NONE,false,new RsemResults.Limits(100,100,100,10000,100000,1000000,1000000));
            require(audit.transcripts()==6&&audit.genes()==4,"Complete source result identity census differs");
            outcomes.add(Map.of("case",name,"status","passed-exact-original-scientific-population-and-native-audit","scientific_files",expected.size(),"numeric_fields",audit.numericCells(),"warnings",audit.warnings()));
        }
        Path controls=Files.createDirectory(base.resolve("controls"));
        var refused=new ArrayList<String>(List.of("--star","/input","/reference","/output"));
        try{context.executeOnly(RsemRuntime.Program.QUANTIFY,refused,controls.resolve("undeclared-aligner"),FIT,300000,scientificInputs,()->{},List.of(input));throw new IOException("Undeclared provider was accepted");}
        catch(IOException expected){require(!Files.exists(controls.resolve("undeclared-aligner")),"Undeclared mode created a process directory");}
        var versionWork=controls.resolve("changed-input");
        try{context.executeOnly(RsemRuntime.Program.VERSION,List.of(),versionWork,FIT,300000,()->{throw new IOException("Controlled changed scientific input");},()->{},List.of(input));throw new IOException("Changed input was accepted");}
        catch(IOException expected){require(!Files.exists(versionWork),"Changed input launched a process");}
        String candidate=shadow.resolve("libstdc++.so.6").toString();
        require(context.host().snapshot().files().files().containsKey(candidate),"Declared shadow SONAME absence was not captured");
        Path injected=Files.writeString(shadow.resolve("libstdc++.so.6"),"controlled invalid library candidate\n",StandardOpenOption.CREATE_NEW);
        try{context.executeOnly(RsemRuntime.Program.VERSION,List.of(),controls.resolve("shadow-library"),FIT,300000,scientificInputs,()->{},List.of(input));throw new IOException("New library shadow was accepted");}
        catch(IOException expected){require(!Files.exists(controls.resolve("shadow-library")),"Changed lookup candidate launched a process");write(controls.resolve("shadow-library-refusal.json"),Map.of("error",expected.toString(),"candidate",candidate,"sha256",hash(injected)));}
        Files.move(injected,controls.resolve("injected-library-retained"));context.verify(()->{});
        var negative=RsemPayload.stage(Path.of(args[1]),version,Path.of(args[2]),List.of(Path.of(args[3])),base.resolve("negative-payload"),()->{});
        Path script=negative.home().resolve("bin/rsem_perl_utils.pm");Files.writeString(script,"\n# controlled source drift\n",StandardOpenOption.APPEND);
        IOException payloadRefusal=null;try{negative.verify(()->{});}catch(IOException failure){payloadRefusal=failure;}
        require(payloadRefusal!=null,"Changed payload script was accepted");
        write(controls.resolve("changed-payload-refusal.json"),Map.of("error",payloadRefusal.toString(),"script",script,"changed_sha256",hash(script)));
        scientificInputs.verify();context.verify(()->{});
        Path candidateJar=Path.of(RsemRuntime.class.getProtectionDomain().getCodeSource().getLocation().toURI());require(Files.isRegularFile(candidateJar),"Protocol must run the exact packaged candidate");
        var report=new LinkedHashMap<String,Object>();report.put("status","passed-bound-original-RSEM-reference-fit-and-runtime-controls");report.put("version",version);report.put("observed_source_version",payload.source().reportedVersion());
        report.put("candidate_sha256",hash(candidateJar));report.put("host_capture_sha256",context.host().snapshotSha256());report.put("host_probes",context.host().probes().size());report.put("host_members",context.host().snapshot().files().members());
        report.put("ELF_images",context.host().snapshot().images().size());report.put("complete_reference_files",((Map<?,?>)references.get(version)).size());report.put("complete_scientific_files",compared);report.put("outcomes",outcomes);
        report.put("undeclared_provider_refused_before_launch",true);report.put("changed_input_refused_before_launch",true);report.put("new_library_shadow_refused_before_launch",true);report.put("changed_payload_refused",true);
        report.put("all_positive_contexts_currently_reverified",true);report.put("scientific_runtime_admitted",false);report.put("source_to_build_provenance_verified",false);report.put("public_fitting_workflow_available",false);report.put("biological_or_whole_paper_reproduction_certified",false);
        write(base.resolve("observed.json"),report);System.out.println(RsemJson.json(report));
    }
}
