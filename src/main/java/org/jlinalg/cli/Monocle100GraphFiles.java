/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlinalg.cli.Monocle100NeighborsContracts.Caps;
import static org.jlinalg.cli.Monocle100NeighborsFiles.*;
import static org.jlinalg.cli.Monocle100GraphContracts.GRAPH_PROFILE;

/** Source-bound graph capture using the shared complete-file/process primitives. */
final class Monocle100GraphFiles {
    private Monocle100GraphFiles() { }
    static final String GRAPH_ENGINE="monocle100-original-graph-adapter-v1";
    static byte[] graphResource(String name) throws IOException {
        try(var input=Monocle100GraphFiles.class.getResourceAsStream("/org/jlinalg/omics/monocle100-graph/"+name)) {
            require(input!=null,"Missing packaged original graph resource: "+name);byte[] raw=input.readNBytes(8388609);require(raw.length<=8388608,"Graph resource capacity exceeded");return raw;
        }
    }
    static Map<String,Object> graphProfile() throws IOException {return object(NextflowParameters.parse(NextflowFiles.utf8(graphResource("PROFILE.json")),1048576,100000));}
    static List<String> graphPackages() throws IOException {return List.copyOf(object(graphProfile().get("packages")).keySet());}
    static void graphSources(Path root,boolean compare) throws IOException {
        var profile=graphProfile();require(GRAPH_PROFILE.equals(profile.get("profile")),"Foreign packaged original graph profile");var members=new LinkedHashMap<String,byte[]>();members.put("PROFILE.json",graphResource("PROFILE.json"));
        for(var row:object(profile.get("files")).entrySet()) {
            NextflowFiles.member(row.getKey());byte[] raw=graphResource(row.getKey());var wanted=pin(row.getValue(),8388608);require(raw.length==wanted.bytes()&&NextflowFiles.sha(raw).equals(wanted.sha256()),"Changed original graph reference: "+row.getKey());members.put(row.getKey(),raw);
        }
        for(String name:List.of("worker.R","NOTICE.txt"))members.put(name,graphResource(name));
        byte[] supervisor=resource("supervisor.py");var wanted=pin(profile.get("shared_supervisor"),8388608);require(supervisor.length==wanted.bytes()&&NextflowFiles.sha(supervisor).equals(wanted.sha256()),"Shared supervisor differs from selected graph profile");members.put("supervisor.py",supervisor);
        for(var row:members.entrySet()) {
            Path path=root.resolve("source").resolve(row.getKey());
            if(compare)require(Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)&&java.util.Arrays.equals(NextflowFiles.bytes(path,8388608),row.getValue()),"Changed captured graph worker/reference: "+row.getKey());
            else{Files.createDirectories(path.getParent());Files.write(path,row.getValue(),StandardOpenOption.CREATE_NEW);}
        }
    }
    static void graphManifest(Path root,Caps caps) throws IOException {json(root.resolve("manifest.json"),Map.of("schema_version",1,"engine",GRAPH_ENGINE,"files",snapshot(root,caps.maximumFile(),caps.files())));}
    static void verifyGraphManifest(Path root,Caps caps) throws IOException {
        var saved=read(root.resolve("manifest.json"),Math.min(caps.jsonBytes(),67108864));keys(saved,Set.of("schema_version","engine","files"),Set.of("schema_version","engine","files"));integer(saved.get("schema_version"),1,1);require(GRAPH_ENGINE.equals(saved.get("engine")),"Foreign original graph capture engine");var wanted=object(saved.get("files"));var actual=snapshot(root,caps.maximumFile(),caps.files());require(wanted.keySet().equals(actual.keySet()),"Complete graph capture population differs");for(var row:wanted.entrySet())require(pin(row.getValue(),caps.maximumFile()).equals(pin(actual.get(row.getKey()),caps.maximumFile())),"Changed complete graph capture member: "+row.getKey());
    }
    static void graphOutputLimits(Path root,Caps caps,long pendingBytes,int pendingFiles) throws IOException {
        outputLimits(root,caps,pendingBytes,pendingFiles);for(String name:List.of("pseudotime.tsv","principal-nodes.tsv")){Path table=root.resolve("results").resolve(name);if(Files.exists(table))require(Files.size(table)<=caps.jsonBytes(),"Complete graph/ordering table exceeds its verification read capacity");}
    }
    static void graphModelMembers(Path root,Map<String,Object> parameters,Caps caps) throws IOException {
        var names=new ArrayList<>(List.of("inputs/source-cds.rds","worker/input-invariants.rds","worker/input-scientific-state.rds","worker/output-cds.rds","worker/output-invariants.rds","worker/output-scientific-state.rds","worker/original-principal-graph.rds","worker/RNG.rds"));
        if(Monocle100GraphContracts.learns(parameters))names.add("worker/learned-cds.rds");
        else require(!Files.exists(root.resolve("worker/learned-cds.rds")),"ORDER cannot claim a newly learned model");
        if(Monocle100GraphContracts.orders(parameters))names.addAll(List.of("worker/original-ordering.rds","worker/original-projected-graph.rds"));
        else for(String name:List.of("worker/original-ordering.rds","worker/original-projected-graph.rds"))require(!Files.exists(root.resolve(name)),"LEARN cannot claim an original ordering artifact");
        for(String name:names){Path path=regular(root.resolve(name));long size=Files.size(path);require(size>0&&size<=caps.maximumFile(),"Complete nonempty original model member required within capacity: "+name);}
    }
    static void graphCompare(Map<String,Object> wanted,Map<String,Object> actual) throws IOException {
        require(wanted.keySet().equals(actual.keySet()),"Graph audit field population differs");
        for(var row:wanted.entrySet()) {
            Object expected=row.getValue(),got=actual.get(row.getKey());
            if(expected instanceof Number number)require(Double.doubleToRawLongBits(number.doubleValue())==Double.doubleToRawLongBits(finite(got)),"Graph numerical audit field differs: "+row.getKey());
            else require(java.util.Objects.equals(expected,got),"Graph audit field differs: "+row.getKey());
        }
    }
    static List<String> graphLabels(Object value) throws IOException {
        var result=new ArrayList<String>();var seen=new java.util.HashSet<String>();for(Object raw:list(value)){String name=label(raw);require(seen.add(name),"Duplicate complete source graph axis label");result.add(name);}require(!result.isEmpty(),"Empty complete source graph axis");return List.copyOf(result);
    }
}
