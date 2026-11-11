/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Portable declared-runtime/setup evidence; never re-admits or executes the producer host. */
final class FastpRecordedRuntime {
    private FastpRecordedRuntime() { }
    static void capture(Path root,FastpRuntime.Context runtime,Runnable check)throws IOException {
        Files.createDirectory(root.resolve("runtime-evidence"));Files.createDirectory(root.resolve("runtime-evidence/setup"));var receipts=new ArrayList<FastpFiles.Capture>();
        for(String name:List.of("LICENSE",FastpRuntime.MARKER))receipts.add(FastpFiles.capture(root,runtime.home().resolve(name),"payload-"+name,"runtime-evidence/"+name,16_777_216,check));
        for(String name:new java.util.TreeSet<>(runtime.setup().keySet()))receipts.add(FastpFiles.capture(root,runtime.setupReport().resolve(name),"setup-"+name,"runtime-evidence/setup/"+name,16_777_216,check));
        FastpFiles.writeJson(root.resolve("runtime-captures.json"),receipts);runtime.verify(check);
    }
    static Set<String> verify(Path root,Map<?,?> runtime,Runnable check)throws IOException {
        FastpChoices.map(runtime,Set.of("home","profile","version","setupReport","kernel","payload","setup","helpers"));
        String profile=FastpChoices.text(runtime,"profile"),version=FastpChoices.text(runtime,"version"),home=absolute(runtime,"home"),setupPath=absolute(runtime,"setupReport");Map<?,?> locked=FastpRuntime.locked(profile);
        if(!version.equals(locked.get("version"))||FastpChoices.text(runtime,"kernel").isBlank())throw new IOException("Recorded fastp edition/kernel differs");
        Map<?,?> payload=FastpChoices.map(runtime.get("payload"),Set.of("fastp","LICENSE",FastpRuntime.MARKER));if(!identityPin(payload.get("fastp")).equals(FastpRuntime.fingerprint(FastpRuntime.object(locked.get("binary")))))throw new IOException("Recorded fastp program is not the locked original");
        var expectedFiles=new HashSet<String>();var receipts=FastpMetrics.array(FastpFiles.json(root.resolve("runtime-captures.json"),1_048_576));var expectedReceipts=new java.util.TreeMap<String,Object>();
        for(String name:List.of("LICENSE",FastpRuntime.MARKER))bind(root,"runtime-evidence/"+name,home+"/"+name,"payload-"+name,payload.get(name),expectedFiles,expectedReceipts,check);
        byte[] notice;try(var in=FastpRecordedRuntime.class.getResourceAsStream("/org/jlinalg/omics/fastp-MIT-NOTICE.txt")){if(in==null)throw new IOException("Missing fastp MIT notice");notice=in.readNBytes(65537);}
        if(!FastpFiles.sha(notice).equals("52e18866a994ee9839aa6d15696bc1fe425e6466631d3a57a75c089dd84ae88e")||!java.util.Arrays.equals(notice,FastpFiles.bytes(root.resolve("runtime-evidence/LICENSE"),65536)))throw new IOException("Recorded original fastp license differs");
        Object marker=Map.of("schema_version",1L,"profile",profile,"version",version,"lock_sha256",FastpRuntime.LOCK_SHA256,"files",Map.of("fastp",identityPin(payload.get("fastp")),"LICENSE",identityPin(payload.get("LICENSE"))),"status","PINNED_DEVELOPMENT_RUNTIME_ONLY","user_analysis_admitted",false,"source_to_binary_reproduction_verified",false);
        if(!SimpleJson.parse(FastpJson.json(marker)).equals(FastpFiles.json(root.resolve("runtime-evidence/"+FastpRuntime.MARKER),65536)))throw new IOException("Recorded fastp managed marker differs");
        Map<?,?> setup=FastpRuntime.object(runtime.get("setup"));if(setup.size()>66||!setup.keySet().containsAll(Set.of("manifest.json","COMPLETED.json")))throw new IOException("Incomplete recorded fastp setup");
        for(var entry:setup.entrySet()){String name=FastpFiles.member(FastpRuntime.text(entry.getKey()));if(name.contains("/"))throw new IOException("Nested fastp setup file");bind(root,"runtime-evidence/setup/"+name,setupPath+"/"+name,"setup-"+name,entry.getValue(),expectedFiles,expectedReceipts,check);}
        if(receipts.size()!=expectedReceipts.size())throw new IOException("fastp runtime capture receipt count differs");var found=new HashSet<String>();
        for(Object receipt:receipts){Map<?,?> row=FastpChoices.map(receipt,Set.of("role","originalPath","identity","snapshot"));String snapshot=FastpChoices.text(row,"snapshot");if(!found.add(snapshot)||!SimpleJson.parse(FastpJson.json(expectedReceipts.get(snapshot))).equals(row))throw new IOException("Recorded fastp runtime capture receipt differs");}
        Path report=root.resolve("runtime-evidence/setup");Map<?,?> manifest=FastpChoices.map(FastpFiles.json(report.resolve("manifest.json"),1_048_576),Set.of("schema_version","files"));FastpRuntime.integer(manifest.get("schema_version"),1,1);Map<?,?> files=FastpRuntime.object(manifest.get("files"));var census=new HashSet<Object>(setup.keySet());census.remove("manifest.json");census.remove("COMPLETED.json");
        if(!census.equals(files.keySet())||!census.containsAll(Set.of("setup.json","reads.fq","processed.fq","fastp.json","fastp.html","version.stdout","version.stderr","version.command.json","health.stdout","health.stderr","health.command.json")))throw new IOException("Recorded fastp setup census differs");
        for(var entry:files.entrySet())if(!FastpRuntime.fingerprint(FastpRuntime.object(entry.getValue())).equals(identityPin(setup.get(entry.getKey()))))throw new IOException("Recorded fastp setup manifest fingerprint differs");
        Map<?,?> completion=FastpChoices.map(FastpFiles.json(report.resolve("COMPLETED.json"),65536),Set.of("status","manifest","user_analysis_admitted"));if(!"COMPLETE_SETUP_ONLY".equals(completion.get("status"))||!Boolean.FALSE.equals(completion.get("user_analysis_admitted"))||!identityPin(setup.get("manifest.json")).equals(FastpRuntime.fingerprint(FastpRuntime.object(completion.get("manifest")))))throw new IOException("Recorded fastp setup-only completion differs");
        Map<?,?> installed=FastpRuntime.object(FastpFiles.json(report.resolve("setup.json"),65536));if(!"passed-development-runtime-and-tiny-synthetic-health-only".equals(installed.get("status"))||!home.equals(installed.get("home"))||!profile.equals(installed.get("profile"))||!Boolean.FALSE.equals(installed.get("user_analysis_admitted"))||!SimpleJson.parse(FastpJson.json(marker)).equals(installed.get("runtime_identity")))throw new IOException("Recorded fastp setup identity differs");
        String diagnostic=FastpFiles.utf8(FastpFiles.bytes(report.resolve("version.stdout"),65536))+FastpFiles.utf8(FastpFiles.bytes(report.resolve("version.stderr"),65536));if(!diagnostic.equals("fastp "+version+"\n"))throw new IOException("Recorded fastp version diagnostic differs");
        command(report.resolve("version.command.json"),List.of(home+"/fastp","--version"));command(report.resolve("health.command.json"),List.of(home+"/fastp","--in1","reads.fq","--out1","processed.fq","--json","fastp.json","--html","fastp.html","--thread","1","--dont_overwrite"));
        String sequence="ACGCTAGCATCGACTGATCGACTAGCATAGCTACGATCGATGCTAGCTAGCATGCATCGATGCTAGCATCGATGCATCGATGCTAGCATCGATGCATCGATG";var fixture=new StringBuilder();for(int i=1;i<=32;i++)fixture.append("@fixture:").append(i).append('\n').append(sequence).append("\n+\n").append("I".repeat(sequence.length())).append('\n');
        for(String name:List.of("reads.fq","processed.fq"))if(!fixture.toString().equals(FastpFiles.utf8(FastpFiles.bytes(report.resolve(name),65536))))throw new IOException("Recorded fastp complete tiny setup population differs");
        var fq=new FastpFastq.Limits(65536,65536,32,65536,1000,1000);var model=FastpMetrics.collect(report.resolve("reads.fq"),fq,FastpFastq.Encoding.PHRED33,FastpFastq.Alphabet.UPPER_ACGTN,FastpFastq.Plus.EMPTY_ONLY,new FastpMetrics.Limits(1000,262144),false,check);Map<?,?> json=FastpRuntime.object(FastpFiles.json(report.resolve("fastp.json"),1_048_576));
        for(String stage:List.of("before_filtering","after_filtering")){model.verifyDetail(FastpRuntime.object(json.get("read1_"+stage)),version);FastpMetrics.verifySummary(FastpRuntime.object(FastpRuntime.object(json.get("summary")).get(stage)),model,null);}
        Map<?,?> helpers=FastpChoices.map(runtime.get("helpers"),Set.of("/usr/bin/prlimit","/usr/bin/setsid"));for(var entry:helpers.entrySet()){Map<?,?> helper=FastpChoices.map(entry.getValue(),Set.of("requested","realPath","identity","mode"));if(!entry.getKey().equals(helper.get("requested")))throw new IOException("Recorded fastp helper path differs");absolute(helper,"realPath");identityPin(helper.get("identity"));long mode=FastpRuntime.integer(helper.get("mode"),0,07777);if((mode&0111)==0)throw new IOException("Recorded fastp helper lacks executable mode");}
        return Set.copyOf(expectedFiles);
    }
    private static void bind(Path root,String member,String original,String role,Object identity,Set<String> files,Map<String,Object> receipts,Runnable check)throws IOException {
        if(!FastpFiles.pin(root.resolve(member),16_777_216,check).equals(identityPin(identity)))throw new IOException("Recorded fastp runtime evidence changed: "+member);files.add(member);receipts.put(member,Map.of("role",role,"originalPath",original,"identity",identity,"snapshot",member));
    }
    private static FastpFiles.Pin identityPin(Object value)throws IOException {Map<?,?> identity=FastpChoices.map(value,Set.of("pin","key","modified"));FastpChoices.text(identity,"key");FastpChoices.text(identity,"modified");return FastpRuntime.fingerprint(FastpRuntime.object(identity.get("pin")));}
    private static String absolute(Map<?,?> map,String key)throws IOException {String path=FastpChoices.text(map,key);if(!path.startsWith("/")||!FastpRecipe.originalInput("/recipe",path).equals(path))throw new IOException("Noncanonical recorded fastp producer path");return path;}
    private static void command(Path path,List<String> command)throws IOException {Map<?,?> row=FastpRuntime.object(FastpFiles.json(path,65536));if(!command.equals(row.get("command"))||FastpRuntime.integer(row.get("exit_code"),0,0)!=0||FastpRuntime.integer(row.get("address_space_bytes"),0,Long.MAX_VALUE)!=3L<<30||FastpRuntime.integer(row.get("cpu_seconds"),0,120)!=60||FastpRuntime.integer(row.get("output_file_bytes"),0,Long.MAX_VALUE)!=16_777_216||FastpRuntime.integer(row.get("wall_seconds"),0,120)!=120)throw new IOException("Recorded fastp setup command/capacities differ");}
}
