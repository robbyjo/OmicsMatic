/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Additional original-operation health and utility admission for generated backgrounds. */
final class HomerGeneratedRuntime {
    private HomerGeneratedRuntime() { }
    private static final String MARKER=".jlinalg-homer-generated.json";
    private record Lock(Map<?,?> data,String hash) { }
    private static final Lock LOCK=load();
    record Context(HomerGenomeRuntime.Context base,Path admission,String mode,String markerHash,
                   Map<String,HomerRuntime.External> additional) {
        Context {additional=Map.copyOf(additional);}
        void verify()throws IOException{
            try{if(!equals(open(base,admission,mode)))throw new IOException("Generated operation admission changed");}
            catch(HomerRuntime.Unavailable changed){throw new IOException("Generated operation dependency changed;preserve diagnostics and re-admit NEW paths",changed);}
        }
        void marker()throws IOException{
            if(!markerHash.equals(FollowupSupport.hash(admission.resolve(MARKER))))throw new IOException("Generated operation marker changed during execution");
        }
        void record(Map<String,Object> session){session.put("generated_admission",admission.toString());session.put("generated_admission_sha256",markerHash);session.put("generated_lock_sha256",LOCK.hash);var files=new TreeMap<String,Object>();additional.forEach((name,value)->files.put(name,Map.of("real_path",value.realPath(),"bytes",value.bytes(),"sha256",value.sha256())));session.put("generated_additional_files",files);}
    }
    private static Lock load(){
        try(var stream=HomerGeneratedRuntime.class.getResourceAsStream("/org/jlinalg/omics/homer-generated-lock.json")){
            if(stream==null)throw new IOException("Missing generated operation lock");byte[] raw=stream.readNBytes(1048577);if(raw.length>1048576)throw new IOException("Generated operation lock oversized");return new Lock(AdmixtureInputs.decode(raw),AdmixtureInputs.digest(raw));
        }catch(Exception failure){throw new ExceptionInInitializerError(failure);}
    }
    static Context open(HomerGenomeRuntime.Context base,Path supplied,String mode)throws HomerRuntime.Unavailable{
        try{
            base.verify();Map<?,?> modePin=AdmixtureInputs.map(AdmixtureInputs.map(LOCK.data.get("modes")).get(mode));
            if(!(modePin.get("versions") instanceof java.util.List<?> versions)||!versions.contains(base.version()))throw new IOException("Requested generated algorithm unavailable in "+base.version());
            Path admission=supplied.toAbsolutePath().normalize();if(!Files.isDirectory(admission,LinkOption.NOFOLLOW_LINKS)||!admission.toRealPath().equals(admission))throw new IOException("Generated admission missing or linked");Path marker=admission.resolve(MARKER);HomerFasta.regular(marker,1048576);byte[] raw=AdmixtureInputs.raw(marker,1048576);Map<?,?> meta=AdmixtureInputs.decode(raw);
            if(AdmixtureInputs.whole(meta,"schema_version",1,1)!=1||!LOCK.data.get("tool").equals(meta.get("tool"))||!LOCK.hash.equals(meta.get("lock_sha256"))||!LOCK.data.get("base_lock_sha256").equals(meta.get("base_lock_sha256"))||!base.home().toString().equals(meta.get("home"))||!admission.toString().equals(meta.get("admission"))||!base.profile().equals(meta.get("profile"))||!base.version().equals(meta.get("version"))||!mode.equals(meta.get("mode"))||!base.markerHash().equals(meta.get("base_marker_sha256")))throw new IOException("Generated admission mode/version/path/base binding differs");
            Map<?,?> host=AdmixtureInputs.map(meta.get("host"));if(!"Linux".equals(host.get("os"))||!"x86_64".equals(host.get("architecture"))||!System.getProperty("os.version").equals(host.get("kernel")))throw new IOException("Generated admission host changed");
            Map<?,?> health=AdmixtureInputs.map(meta.get("health")),expected=AdmixtureInputs.map(LOCK.data.get("health"));
            if(!expected.get("status").equals(health.get("status"))||AdmixtureInputs.whole(health,"targets",64,64)!=64||AdmixtureInputs.whole(health,"known_models",2,2)!=2||AdmixtureInputs.whole(health,"selected_background",2,1000000)<2||!Boolean.TRUE.equals(health.get("learned_and_comparison_reports"))||AdmixtureInputs.whole(AdmixtureInputs.map(health.get("original_call")),"exit_code",0,0)!=0)throw new IOException("Actual original generated operation health missing");
            for(String key:Set.of("genome_sha256","targets_sha256","models_sha256"))if(!expected.get(key).equals(health.get(key)))throw new IOException("Generated health fixture differs");
            Map<?,?> files=AdmixtureInputs.map(meta.get("additional_files"));var additional=new TreeMap<String,HomerRuntime.External>();
            if(files.size()>32||mode.equals("generated-old")&&!files.containsKey("/usr/bin/chmod")||mode.equals("generated-new")&&!files.isEmpty())throw new IOException("Generated utility closure incomplete");
            for(var entry:files.entrySet()){
                String name=entry.getKey().toString();if(!name.matches("/(usr|lib|lib64|bin)/[A-Za-z0-9_./+-]+"))throw new IOException("Unsupported generated host binding");Map<?,?> value=AdmixtureInputs.map(entry.getValue());Path path=Path.of(name),real=path.toRealPath();HomerFasta.regular(real,100000000);long size=Files.size(real);String digest=FollowupSupport.hash(real);
                if(!real.toString().equals(value.get("real_path"))||size!=AdmixtureInputs.whole(value,"bytes",1,100000000)||!digest.equals(value.get("sha256")))throw new IOException("Generated host binding changed: "+name);
                additional.put(name,new HomerRuntime.External(real.toString(),size,digest));
            }
            return new Context(base,admission,mode,AdmixtureInputs.digest(raw),additional);
        }catch(Exception failure){throw new HomerRuntime.Unavailable("Original generated-background dependency unavailable: "+failure.getMessage()+". "+recovery(),failure);}
    }
    static String recovery(){return "Required action: use a separately admitted original genomic home,then run python3 tools/literature/admit_homer_generated.py --home PRIVATE_GENOMIC_HOME --profile linux-x86_64-genome-5.1 --mode generated-old --out NEW_ADMISSION. Choose generated-new only for5.1 and separate paths per mode/version. Old preparsing needs /usr/bin/chmod and its libraries. If unavailable or forbidden,SKIP the affected generated analysis,exit3;supplied-background analyses remain independent. Admission never installs,downloads or patches scientific source. Preserve failed reports;correct the named cause and use NEW paths.";}
}
