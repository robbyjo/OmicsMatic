/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.gprofiler.GprofilerJson;
import static org.jlinalg.cli.GprofilerWorkflowContracts.*;
import static org.jlinalg.cli.ScenicWorkflowFiles.*;

/** Original SCENIC+ cisTarget/DEM and merged cistrome workflow; family admission is pending. */
public final class ScenicPlusMotifsCli {
    private ScenicPlusMotifsCli(){ }
    static final String PROFILE="scenicplus-1.0a2-motifs-v1";
    static final String COMPLETE="COMPLETE_SCOPED_SCENICPLUS_MOTIFS";
    static final String RESOURCE="/org/jlinalg/omics/scenicplus-motifs/";
    static final List<String> SOURCES=List.of("worker.py","r2g_runtime.py","SOURCE-PROFILE.json");
    static final Map<String,String> INPUT_FILES=Map.of("prepared","prepared.h5mu","region_sets","region-sets.tsv","motif_annotations","motif-annotations.tbl","rankings","database.regions_vs_motifs.rankings.feather","scores","database.regions_vs_motifs.scores.feather","genome_annotation","genome-annotation.tsv");
    static final String ACTION="PRIVATE LICENSED CAPTURE: retain source-bearing traces and the unmodified runtime privately under accepted internal academic terms. Explicit local motif annotation and prepared ranking/score databases are required; no genome scan, genome assembly choice or scientific reference is inferred. ClusterBuster scanning remains SKIP3 outside this profile: supply permission for an exact runtime or retain that skipped analysis. Original DEM ignores maximum background size when promoter balancing is off; actual foreground/background counts and warnings are recorded, never silently repaired. Empty and nonfinite source outcomes remain visible and do not establish biological validity. SKIP3: restore the exact permitted runtime/input or retain skipped analysis. FAIL2: preserve raw/partial/failed evidence, correct the named cause and retry NEW output. Read MOTIF-WARNINGS.md, worker/report.json and worker/out/field-ledger.tsv. No complete SCENIC+ family, catalog credit or whole-paper qualification is implied. SHA256 establishes integrity, not execution authentication.";
    public static void main(String[] args){int code=run(args,System.out,System.err);if(code!=0)System.exit(code);}
    static int run(String[] args,PrintStream out,PrintStream error){
        Path root=null;
        try{
            if(Arrays.equals(args,new String[]{"--help"})){out.println("Original SCENIC+ cisTarget/DEM and cistrome preparation: --request JSON | --inspect CAPTURE. Schema1 RUN: profile="+PROFILE+", operation=MOTIFS, python, NEW out, inputs{prepared,region_sets,motif_annotations,rankings and/or scores, optional genome_annotation}, options{species,annotation_version,analyses,...}. Analyses explicitly declare CISTARGET foreground region set or DEM foreground/background sets and seed. Complete typed enrichment, mapping, motif hits and merged direct/extended Boolean cistromes are retained. VERIFY/REPLAY run fresh original scientific APIs in NEW disjoint witnesses; --inspect checks saved closure only. Requires complete source-built JAR and permitted unchanged Linux runtime.\n"+ACTION);return 0;}
            if(args.length==2&&args[0].equals("--inspect")){inspect(Path.of(args[1]));out.println("Saved unpaired-preparation closure checked; no new original computation executed. "+ACTION);return 0;}
            require(args.length==2&&args[0].equals("--request"),"Use --request JSON, --inspect CAPTURE or --help");
            Path requestFile=Path.of(args[1]).toAbsolutePath().normalize();byte[] raw=bytes(requestFile,16777216);var original=object(GprofilerJson.read(raw));
            String mode=text(original.get("mode"),"mode");Path parent=null;Map<String,Object> parentSeal=null;Map<String,Object> request;
            if(Set.of("VERIFY","REPLAY").contains(mode)){
                keys(original,Set.of("schema_version","mode","analysis","out","python"));integer(original.get("schema_version"),1,1);
                parent=resolve(requestFile,text(original.get("analysis"),"analysis"));request=new LinkedHashMap<>(inspect(parent));parentSeal=pin(parent.resolve("manifest.json"));
                request.put("out",resolve(requestFile,text(original.get("out"),"out")).toString());
                if(original.containsKey("python"))request.put("python",resolve(requestFile,text(original.get("python"),"python")).toString());
                Path target=Path.of(request.get("out").toString());require(!target.startsWith(parent)&&!parent.startsWith(target),"Witness and read-only parent must be disjoint");
            }else request=validate(original,requestFile);
            root=newOutput(Path.of(request.get("out").toString()));Files.createDirectory(root.resolve("worker"));
            Files.write(root.resolve("source-request.json"),raw,StandardOpenOption.CREATE_NEW);write(root.resolve("canonical-request.json"),request);
            write(root.resolve("producer.json"),producer());ScenicWorkflowFiles.text(root.resolve("WARNINGS.md"),"# PRIVATE SCENIC+ development capture\n\n"+ACTION+"\n");
            Files.createDirectory(root.resolve("inputs"));var inputs=map();var captures=map();
            for(String role:object(request.get("inputs")).keySet()){
                Path source=parent==null?Path.of(text(object(request.get("inputs")).get(role),role)):parent.resolve("inputs/"+INPUT_FILES.get(role));
                capture(source,root.resolve("inputs/"+INPUT_FILES.get(role)),request,captures,"inputs/"+INPUT_FILES.get(role));inputs.put(role,"inputs/"+INPUT_FILES.get(role));
            }
            write(root.resolve("input-inventory.json"),captures);Files.createDirectory(root.resolve("source"));var sources=map();
            for(String name:SOURCES){byte[] body=resource(name);Files.write(root.resolve("source/"+name),body,StandardOpenOption.CREATE_NEW);sources.put(name,pin(body));}
            write(root.resolve("source-inventory.json"),sources);write(root.resolve("worker/request.json"),map("options",request.get("options"),"limits",request.get("limits"),"parent_analysis",parent==null?null:parent.toString()));
            var process=map();ScenicPlusMotifsRuntime.execute(root,request,process);
            write(root.resolve("worker-process.json"),process);
            var summary=read(root.resolve("worker/out/SUMMARY.json"));
            String outcome="Empty enrichment analyses: "+number(summary,"empty_analyses")+"; nonfinite numeric output fields: "+number(summary,"nonfinite_numerical_fields")+"; source background-cap warnings: "+number(summary,"background_cap_warnings")+". Read worker/out/results.json and field-ledger.tsv; no result is inferred for empty/unreportable fields.";
            ScenicWorkflowFiles.text(root.resolve("MOTIF-WARNINGS.md"),"# Original motif and cistrome outcomes\n\n"+outcome+"\n\n"+ACTION+"\n");
            if(number(summary,"empty_analyses")>0||number(summary,"nonfinite_numerical_fields")>0||number(summary,"background_cap_warnings")>0)out.println("WARNING: "+outcome);
            if(parent!=null){
                require(same(parentSeal,pin(parent.resolve("manifest.json"))),"Read-only parent manifest changed during refit");inspect(parent);
                for(String name:List.of("worker/out/scientific.jsonl","worker/out/results.json","worker/out/SUMMARY.json","worker/out/invocation.json","worker/out/comparison-files.json","runtime-before.json"))
                    require(Files.mismatch(parent.resolve(name),root.resolve(name))==-1,"Fresh "+mode+" comparison differs: "+name);
                for(Object value:list(read(root.resolve("worker/out/comparison-files.json")).get("files"))){String name="worker/out/"+relative(text(value,"scientific export"));require(Files.mismatch(parent.resolve(name),root.resolve(name))==-1,"Fresh "+mode+" comparison differs: "+name);}

            }
            var manifest=map("schema_version",1,"status",COMPLETE,"profile",PROFILE,"mode",mode,"summary",summary,"private_capture",true,"new_family_credit",0,"new_reviews",0,"whole_goal_complete",false);
            manifest.put("inventory",inventory(root,request));write(root.resolve("manifest.json"),manifest);write(root.resolve("COMPLETED.json"),map("status",COMPLETE,"manifest",pin(root.resolve("manifest.json"))));
            inspect(root);out.println(COMPLETE+" "+root+"\n"+ACTION);return 0;
        }catch(Exception failure){
            int code=failure instanceof ScenicWorkflowContracts.Unavailable?3:2;
            if(root!=null)try{
                if(Files.exists(root.resolve("COMPLETED.json")))Files.move(root.resolve("COMPLETED.json"),root.resolve("UNADMITTED-COMPLETED.json"));
                write(root.resolve("analysis-status.json"),map("status",code==3?"SKIP3_DEPENDENT_ANALYSIS_SKIPPED":"FAIL2_NO_COMPLETE_RESULT","cause",failure.toString(),"private_capture",true,"required_action",ACTION));
                ScenicWorkflowFiles.text(root.resolve("FAILURE.md"),"# "+(code==3?"SKIP3: dependent analysis skipped":"FAIL2: no complete result")+"\n\n"+failure+"\n\n"+ACTION+"\n");
            }catch(Exception secondary){failure.addSuppressed(secondary);}
            error.println((code==3?"SKIP3: ":"FAIL2: ")+failure+"\n"+ACTION);return code;
        }
    }
    static Path resolve(Path source,String value){return source.getParent().resolve(value).toAbsolutePath().normalize();}
    static Map<String,Object> validate(Map<String,Object> raw,Path source)throws IOException{
        keys(raw,Set.of("schema_version","mode","profile","operation","python","out","inputs","options","limits"));integer(raw.get("schema_version"),1,1);
        require("RUN".equals(raw.get("mode"))&&PROFILE.equals(raw.get("profile"))&&"MOTIFS".equals(raw.get("operation")),"Require schema1 RUN MOTIFS and exact profile");
        var result=new LinkedHashMap<>(raw);for(String key:List.of("python","out"))result.put(key,resolve(source,text(raw.get(key),key)).toString());
        var input=object(raw.get("inputs"));keys(input,INPUT_FILES.keySet());require(input.keySet().containsAll(Set.of("prepared","region_sets","motif_annotations")),"Require prepared multiome, region_sets and literal motif_annotations");var normalized=map();
        for(String key:input.keySet())normalized.put(key,resolve(source,text(input.get(key),key)).toString());result.put("inputs",normalized);
        var options=object(raw.get("options"));keys(options,Set.of("species","annotation_version","annotations_to_use","direct_annotation","extended_annotation","motif_similarity_fdr","orthologous_identity_threshold","fraction_overlap","analyses","export_network_inputs"));
        String species=text(options.get("species"),"species"),version=text(options.get("annotation_version"),"annotation_version");require(Set.of("homo_sapiens","mus_musculus","drosophila_melanogaster").contains(species),"Unknown original species; no assembly/reference inference");
        var analyses=new ArrayList<Object>();var seen=new HashSet<String>();
        for(Object value:list(options.get("analyses"))){
            var a=object(value);keys(a,Set.of("name","method","foreground","background","ctx","dem"));String name=text(a.get("name"),"analysis name"),method=text(a.get("method"),"method");
            require(name.matches("[A-Za-z0-9_-]{1,128}")&&seen.add(name),"Require unique simple analysis names for original HDF5 groups");require(Set.of("CISTARGET","DEM").contains(method),"Unknown original enrichment method");
            var foreground=labels(a.get("foreground"));require(!foreground.isEmpty(),"Declare foreground sets");var row=map("name",name,"method",method,"foreground",foreground);
            if(method.equals("CISTARGET")){
                require(input.containsKey("rankings")&&foreground.size()==1&&!a.containsKey("background")&&!a.containsKey("dem"),"cisTarget requires rankings and exactly one named foreground; no DEM options");
                var c=a.containsKey("ctx")?object(a.get("ctx")):map();keys(c,Set.of("auc_threshold","nes_threshold","rank_threshold"));double auc=fraction(c.getOrDefault("auc_threshold",.005)),rank=fraction(c.getOrDefault("rank_threshold",.05)),nes=decimal(c.getOrDefault("nes_threshold",3)).doubleValue();require(auc>0&&rank>0&&Double.isFinite(nes),"Finite original thresholds required");row.put("ctx",map("auc_threshold",auc,"nes_threshold",nes,"rank_threshold",rank));
            }else{
                require(input.containsKey("scores")&&!a.containsKey("ctx"),"DEM requires score database and no cisTarget options");var background=labels(a.get("background"));require(!background.isEmpty(),"Declare background sets");row.put("background",background);
                var d=object(a.get("dem"));keys(d,Set.of("seed","max_bg_regions","balance_number_of_promoters","promoter_space","adjpval_thr","log2fc_thr","mean_fg_thr","motif_hit_thr"));require(d.containsKey("seed")&&d.containsKey("balance_number_of_promoters"),"Declare explicit DEM seed and promoter balancing; author settings are not inferred");
                boolean balance=ScenicWorkflowContracts.bool(d,"balance_number_of_promoters",false);require(!balance||input.containsKey("genome_annotation"),"Promoter balancing requires explicit local genome_annotation");
                Object maximum=d.get("max_bg_regions"),hit=d.get("motif_hit_thr");if(maximum!=null)maximum=integer(maximum,1,1000000000);if(hit!=null){double h=decimal(hit).doubleValue();require(Double.isFinite(h),"Finite original motif-hit threshold required");hit=h;}
                double fc=decimal(d.getOrDefault("log2fc_thr",1)).doubleValue(),mean=decimal(d.getOrDefault("mean_fg_thr",0)).doubleValue();require(Double.isFinite(fc)&&Double.isFinite(mean),"Finite DEM thresholds required");
                row.put("dem",map("seed",integer(d.get("seed"),0,0xffffffffL),"max_bg_regions",maximum,"balance_number_of_promoters",balance,"promoter_space",integer(d.getOrDefault("promoter_space",1000),0,1000000000),"adjpval_thr",fraction(d.getOrDefault("adjpval_thr",.05)),"log2fc_thr",fc,"mean_fg_thr",mean,"motif_hit_thr",hit));
            }analyses.add(row);
        }
        require(!analyses.isEmpty()&&analyses.size()<=10000,"Declare 1..10000 explicit analyses");
        var annotations=annotations(options.getOrDefault("annotations_to_use",List.of("Direct_annot","Orthology_annot")));
        var direct=annotations(options.getOrDefault("direct_annotation",List.of("Direct_annot")));
        var extended=annotations(options.getOrDefault("extended_annotation",List.of("Orthology_annot")));
        require(!annotations.isEmpty()&&(!direct.isEmpty()||!extended.isEmpty()),"Declare original annotation groups");
        result.put("options",map("species",species,"annotation_version",version,"annotations_to_use",annotations,"direct_annotation",direct,"extended_annotation",extended,"motif_similarity_fdr",fraction(options.getOrDefault("motif_similarity_fdr",.001)),"orthologous_identity_threshold",fraction(options.getOrDefault("orthologous_identity_threshold",0)),"fraction_overlap",fraction(options.getOrDefault("fraction_overlap",.4)),"analyses",analyses,"export_network_inputs",ScenicWorkflowContracts.bool(options,"export_network_inputs",false)));
        var limits=raw.containsKey("limits")?object(raw.get("limits")):map();
        var defaults=map("maximum_input_bytes",2147483648L,"maximum_member_bytes",2147483648L,"maximum_output_bytes",8589934592L,"maximum_files",50000L,"timeout_seconds",1800L,"maximum_cells",1000000L,"maximum_genes",1000000L,"maximum_regions",10000000L,"maximum_pairs",10000000L,"maximum_matrix_values",100000000L);
        var ceilings=map("maximum_input_bytes",68719476736L,"maximum_member_bytes",34359738368L,"maximum_output_bytes",274877906944L,"maximum_files",1000000L,"timeout_seconds",86400L,"maximum_cells",10000000L,"maximum_genes",10000000L,"maximum_regions",100000000L,"maximum_pairs",1000000000L,"maximum_matrix_values",10000000000L);
        keys(limits,defaults.keySet());var bound=map();for(var entry:defaults.entrySet())bound.put(entry.getKey(),integer(limits.getOrDefault(entry.getKey(),entry.getValue()),1,Long.parseLong(ceilings.get(entry.getKey()).toString())));result.put("limits",bound);return result;
    }
    static List<String> labels(Object value)throws IOException{var result=new ArrayList<String>();for(Object v:list(value)){String s=text(v,"literal set/annotation label");require(!result.contains(s)&&s.chars().noneMatch(c->c<32||c==127),"Duplicate/control-containing literal label");result.add(s);}return result;}
    static List<String> annotations(Object value)throws IOException{var result=labels(value);require(Set.of("Direct_annot","Orthology_annot","Motif_similarity_annot","Motif_similarity_and_Orthology_annot").containsAll(result),"Unknown source annotation column");return result;}
    static double fraction(Object value)throws IOException{double v=decimal(value).doubleValue();require(Double.isFinite(v)&&v>=0&&v<=1,"Require an original threshold in [0,1]");return v;}
    static byte[] resource(String name)throws IOException{
        try(var stream=ScenicPlusMotifsCli.class.getResourceAsStream(RESOURCE+name)){require(stream!=null,"Missing packaged SCENIC+ caller/profile: "+name);return stream.readAllBytes();}
    }
    static Map<String,Object> producer()throws IOException{
        try{
            Path jar=Path.of(ScenicPlusMotifsCli.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();
            if(!Files.isRegularFile(jar)||!jar.toString().endsWith(".jar"))throw new ScenicWorkflowContracts.Unavailable("Use a complete source-built project JAR; loose/mixed classes are unqualified");
            var seen=new HashSet<Class<?>>();for(Class<?> type:List.of(ScenicPlusMotifsCli.class,ScenicPlusMotifsRuntime.class,ScenicWorkflowProcess.class,ScenicWorkflowFiles.class,ScenicWorkflowContracts.class,GprofilerWorkflowContracts.class,GprofilerJson.class,OwnedProcesses.class,MrPressoWorkflowContracts.class))origins(type,jar,seen);
            return map("kind","EXACT_PACKAGED_JAR","identity",pin(jar),"profile",PROFILE,"profile_identity",pin(resource("SOURCE-PROFILE.json")),"authentication",false);
        }catch(URISyntaxException failure){throw new IOException("Cannot resolve exact producer",failure);}
    }
    static void origins(Class<?> type,Path jar,Set<Class<?>> seen)throws IOException,URISyntaxException{
        if(!seen.add(type))return;require(jar.equals(Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath()),"Mixed controller/runtime class origin: "+type.getName());for(Class<?> nested:type.getNestMembers())origins(nested,jar,seen);
    }
    static Map<String,Object> inspect(Path folder)throws IOException{
        Path root=folder.toAbsolutePath().normalize();require(Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS)&&root.toRealPath().equals(root),"Require a physical existing capture");
        var seal=read(root.resolve("COMPLETED.json"));require(COMPLETE.equals(seal.get("status"))&&same(seal.get("manifest"),pin(root.resolve("manifest.json"))),"Missing/changed completed manifest");
        var manifest=read(root.resolve("manifest.json"));require(COMPLETE.equals(manifest.get("status"))&&PROFILE.equals(manifest.get("profile"))&&Boolean.TRUE.equals(manifest.get("private_capture")),"Wrong capture kind/status");
        var request=read(root.resolve("canonical-request.json"));require(same(request,validate(request,root.resolve("canonical-request.json")))&&root.toString().equals(request.get("out")),"Noncanonical request/output binding");
        require(same(manifest.get("inventory"),inventory(root,request)),"Saved capture bytes/population changed");require(same(producer(),read(root.resolve("producer.json"))),"Restore the exact complete producer JAR; no hash refresh");
        for(String name:SOURCES)require(Arrays.equals(resource(name),bytes(root.resolve("source/"+name),67108864)),"Saved caller/profile differs from producer");
        require(same(manifest.get("summary"),read(root.resolve("worker/out/SUMMARY.json"))),"Changed scientific summary");return request;
    }
}
