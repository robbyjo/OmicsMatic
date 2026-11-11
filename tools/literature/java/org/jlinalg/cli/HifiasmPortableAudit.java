/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.jlinalg.cli.Monocle3WorkflowRequest.*;

/** Typed scientific-request admission, exact source export and forbidden-install handling on portable Java. */
public final class HifiasmPortableAudit {
    private HifiasmPortableAudit(){ }
    private static int rejected;
    @FunctionalInterface private interface Mutation{void apply(Map<String,Object> request)throws Exception;}
    private static Map<String,Object> copy(Map<String,Object> original)throws IOException{return object(NextflowParameters.parse(NextflowJson.json(original),16777216,1000000));}
    private static Map<String,Object> analysis(Map<String,Object> root)throws IOException{var rows=new ArrayList<Object>(list(root.get("analyses")));var selected=object(rows.get(0));rows.set(0,selected);root.put("analyses",rows);return selected;}
    private static Map<String,Object> input(Map<String,Object> root,String name)throws IOException{var inputs=object(root.get("inputs"));var selected=object(inputs.get(name));inputs.put(name,selected);root.put("inputs",inputs);return selected;}
    private static void reject(Path home,Map<String,Object> original,Mutation mutation)throws Exception{var request=copy(original);mutation.apply(request);Path file=home.resolve("rejection-"+rejected+".json");NextflowFiles.writeJson(file,request);boolean failed=false;try{HifiasmWorkflowRequest.read(file);}catch(IOException|IllegalArgumentException expected){failed=true;}require(failed,"Invalid scientific request accepted: "+file);rejected++;}
    public static void main(String[] args)throws Exception{
        require(args.length==3,"Require request directory, repository root and NEW output");Path requests=Path.of(args[0]).toAbsolutePath().normalize(),root=Path.of(args[1]).toAbsolutePath().normalize(),out=Path.of(args[2]).toAbsolutePath().normalize();Files.createDirectory(out);var valid=new ArrayList<Map<String,Object>>();
        try(var paths=Files.list(requests)){for(Path source:paths.filter(p->p.toString().endsWith(".json")).sorted().toList()){var request=GseapyExecutionRuntime.read(source,16777216);request.put("out",out.resolve("never-created-result").toString());request.put("runtime",out.resolve("not-required-by-parser.json").toString());Path file=out.resolve("valid-"+valid.size()+".json");NextflowFiles.writeJson(file,request);var parsed=HifiasmWorkflowRequest.read(file);require(parsed.analyses().size()==list(request.get("analyses")).size(),"Complete typed analysis population differs");HifiasmWorkflowRequest.available(parsed);valid.add(request);}}
        require(valid.size()==6,"Require all six original edition request families");var original=valid.get(0);
        for(Mutation mutation:List.<Mutation>of(r->r.put("unknown_option",true),r->r.put("schema_version",2),r->r.put("edition","0.19.5"),r->r.put("out",out.toString()),r->r.put("limits_profile","guess"),r->analysis(r).put("threads",0),r->analysis(r).put("bloom_bits",1),r->analysis(r).put("bloom_bits",40),r->analysis(r).put("purge_level",4),r->analysis(r).put("unknown_flag","--anything"),r->analysis(r).put("reads",List.of()),r->analysis(r).put("reads",List.of("unknown")),r->analysis(r).put("ultralong",List.of("hifi")),r->analysis(r).put("mode","trio"),r->analysis(r).put("paternal","parent1"),r->r.put("limits",Map.of("wall_seconds",0)),r->r.put("limits",Map.of("maximum_files",1)),r->r.put("limits",Map.of("unbounded",true)),r->r.put("analyses",List.of(analysis(r),analysis(r))),r->input(r,"hifi").put("kind","guessed"),r->input(r,"hifi").put("sha256","bad"),r->analysis(r).put("id","../escape"))){reject(out,original,mutation);}
        Path kit=out.resolve("source-kit");require(HifiasmSetupCli.run(new String[]{"--out",kit.toString()})==0,"Exact portable source export failed");var profile=HifiasmRuntime.profile();for(var member:object(profile.get("files")).entrySet())require(NextflowFiles.fingerprint(member.getValue(),33554432).equals(NextflowFiles.pin(kit.resolve(NextflowFiles.member(member.getKey())),33554432,()->{})),"Exported original archive changed");
        Path forbidden=out.resolve("forbidden-install.json"),skipped=out.resolve("forbidden-setup");NextflowFiles.writeJson(forbidden,Map.of("schema_version",1,"mode","BUILD_REFERENCE_ONLY","allow_install",false,"edition","0.14.2-r315","source",out.resolve("absent-source").toString(),"out",skipped.toString(),"compiler_threads",1));require(HifiasmSetupCli.run(new String[]{"--build-request",forbidden.toString()})==3,"Forbidden install did not skip dependent setup");var skippedReport=GseapyExecutionRuntime.read(skipped.resolve("COMPLETED.json"),1048576);require(text(skippedReport.get("status")).startsWith("SKIP3_")&&!bool(skippedReport.get("analysis_admitted"))&&!Files.exists(skipped.resolve("compile")),"Forbidden setup ran a compiler or admitted analysis");require(!Files.exists(out.resolve("never-created-result")),"Request parsing started analysis");
        NextflowFiles.writeJson(out.resolve("COMPLETED.json"),Map.of("status","passed-six-typed-hifiasm-request-families-and-portable-setup-contracts-only","valid_edition_families",valid.size(),"invalid_requests_rejected",rejected,"source_archives_exported",object(profile.get("files")).size(),"forbidden_install_exit_code",3,"analysis_admitted",false,"implementation_credit",false));System.out.println("Passed six complete edition request families, "+rejected+" rejections, source export and forbidden setup skip");
    }
}
