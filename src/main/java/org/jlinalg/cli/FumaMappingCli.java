/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlinalg.genetics.FumaVariantGeneMapping;
import org.jlinalg.genetics.FumaChromatinMapping;
import org.jlinalg.genetics.FumaGeneScoreAnnotations;
import static org.jlinalg.cli.FollowupSupport.*;

/** Explicit supplied-resource FUMA positional, eQTL and chromatin mapping, with retained evidence. */
final class FumaMappingCli {
    private FumaMappingCli() { }
    private static final Set<String> ALLOWED=Set.of("method","mapping-options-file","genes","candidates","annotations","consequences","eqtls","annotation-id","biotypes","mhc","mhc-extension","positional","window-bp","consequence-regex","eqtl","eqtl-p","eqtl-fdr","eqtl-tissues","pos-cadd","pos-rdb","pos-states","pos-state-method","pos-state-max","pos-binary","pos-binary-method","eqtl-cadd","eqtl-rdb","eqtl-states","eqtl-state-method","eqtl-state-max","eqtl-binary","eqtl-binary-method","chromatin","interactions","ci-membership","ci-coordinates","ci-enhancers","ci-promoters","ci-cadd","ci-rdb","ci-states","ci-state-method","ci-state-max","ci-binary","ci-binary-method","ci-max-overlaps","gene-scores","gene-score-id","score-duplicates","score-missing","max-score-cells","max-score-columns","empty","max-bytes","max-rows","max-evidence","out");
    private static final String SCOPE="NATIVE SUPPLIED-RESOURCE POSITIONAL/eQTL/CHROMATIN MAPPING AND OPTIONAL SCORE ANNOTATIONS ONLY. Required action: supply and review gene/candidate/annotation/LD identities, upstream eQTL significance/alignment and chromatin/promoter/score resources. No annotation fetching, association/interaction testing, gene-score calculation, MAGMA or full FUMA pipeline is performed.";
    static int run(String[] args,PrintStream out,PrintStream err) {
        if(Arrays.asList(args).contains("--help")){out.println(help());return 0;}
        try {
            var o=parse(args);if(!required(o,"method").equals("map-genes"))throw new IllegalArgumentException("--method must be map-genes");
            for(String key:List.of("genes","candidates","annotations","annotation-id","biotypes","mhc","mhc-extension","positional","eqtl","empty","out"))required(o,key);
            int[] code={0};transaction(o,"fuma-map-genes",(directory,manifest)->{
                var sources=new LinkedHashMap<Path,String>();var session=new LinkedHashMap<String,Object>();var warnings=new ArrayList<String>();warnings.add(SCOPE);
                try {
                    if(o.containsKey("mapping-options-file")){Path p=Path.of(o.get("mapping-options-file")).toAbsolutePath().normalize();if(!hash(p).equals(o.get("options-sha256")))throw new IOException("Options carrier changed before analysis");sources.put(p,o.get("options-sha256"));}
                    int maxBytes=integer(o,"max-bytes",268435456,1,1073741824),maxRows=integer(o,"max-rows",1000000,1,2000000),maxEvidence=integer(o,"max-evidence",1000000,1,10000000);
                    String pos=choice(o,"positional",Set.of("none","window","consequence")),eq=choice(o,"eqtl",Set.of("none","supplied"));boolean positional=!pos.equals("none"),eqtl=eq.equals("supplied");choice(o,"empty",Set.of("error","retain"));
                    String ciMethod=o.getOrDefault("chromatin","none");if(!Set.of("none","supplied").contains(ciMethod))throw new IllegalArgumentException("--chromatin must be none or supplied");boolean ciEnabled=ciMethod.equals("supplied");
                    if(!positional&&!eqtl&&!ciEnabled)throw new IllegalArgumentException("Enable at least one mapping module");
                    if(!ciEnabled)for(String key:o.keySet())if(key.startsWith("ci-")||key.equals("interactions"))throw new IllegalArgumentException("Chromatin options require --chromatin supplied");
                    if(!o.containsKey("gene-scores"))for(String key:o.keySet())if(key.equals("gene-score-id")||key.startsWith("score-")||key.startsWith("max-score-"))throw new IllegalArgumentException("Score options require --gene-scores resource");
                    if(!pos.equals("window")&&o.containsKey("window-bp")||!pos.equals("consequence")&&(o.containsKey("consequence-regex")||o.containsKey("consequences"))||!eqtl&&(o.containsKey("eqtls")||o.containsKey("eqtl-p")||o.containsKey("eqtl-fdr")||o.containsKey("eqtl-tissues")))throw new IllegalArgumentException("Mapping inputs/cutoffs must belong to an enabled module");
                    String mhc=choice(o,"mhc",Set.of("keep","source-endpoints","full-overlap"));Integer low=null,high=null;
                    if(!o.get("mhc-extension").equals("none")){String[] fields=o.get("mhc-extension").split(":",-1);if(fields.length!=2)throw new IllegalArgumentException("--mhc-extension must be none or START:END");low=Integer.valueOf(fields[0]);high=Integer.valueOf(fields[1]);}
                    var genes=new ArrayList<FumaVariantGeneMapping.Gene>();var geneTable=read(Path.of(o.get("genes")),sources,maxBytes,maxRows);geneTable.require("ensembl_gene_id","external_gene_name","chromosome_name","start_position","end_position","strand","gene_biotype");
                    for(var row:geneTable.rows())genes.add(new FumaVariantGeneMapping.Gene(geneTable.v(row,"ensembl_gene_id"),geneTable.v(row,"external_gene_name"),chromosome(geneTable.v(row,"chromosome_name")),geneTable.i(row,"start_position"),geneTable.i(row,"end_position"),geneTable.v(row,"strand"),geneTable.v(row,"gene_biotype")));
                    var candidates=new ArrayList<FumaVariantGeneMapping.Variant>();var input=read(Path.of(o.get("candidates")),sources,maxBytes,maxRows);input.require("uid","rsID","chr","pos","gwasP","CADD","RDB","locus","independent_rsIDs");
                    for(var row:input.rows())candidates.add(new FumaVariantGeneMapping.Variant(input.v(row,"uid"),input.v(row,"rsID"),chromosome(input.v(row,"chr")),input.i(row,"pos"),numeric(input.v(row,"gwasP")),numeric(input.v(row,"CADD")),numeric(input.v(row,"RDB")),input.v(row,"locus"),tokens(input.v(row,"independent_rsIDs"),"NA",";")));
                    var annotations=new ArrayList<FumaVariantGeneMapping.Annotation>();var anno=read(Path.of(o.get("annotations")),sources,maxBytes,maxRows);anno.require("uid","CADD","RDB");
                    for(var row:anno.rows()){var states=new LinkedHashMap<String,Double>();var binary=new LinkedHashMap<String,Double>();for(String h:anno.header())if(h.startsWith("state."))states.put(h.substring(6),numeric(anno.v(row,h)));else if(h.startsWith("binary."))binary.put(h.substring(7),numeric(anno.v(row,h)));annotations.add(new FumaVariantGeneMapping.Annotation(anno.v(row,"uid"),numeric(anno.v(row,"CADD")),numeric(anno.v(row,"RDB")),states,binary));}
                    var consequences=new ArrayList<FumaVariantGeneMapping.Consequence>();
                    if(pos.equals("consequence")){var data=read(Path.of(required(o,"consequences")),sources,maxBytes,maxRows);data.require("uid","gene","annot");for(var row:data.rows())consequences.add(new FumaVariantGeneMapping.Consequence(data.v(row,"uid"),data.v(row,"gene"),data.v(row,"annot")));}
                    var eqtls=new ArrayList<FumaVariantGeneMapping.Eqtl>();
                    if(eqtl){var data=read(Path.of(required(o,"eqtls")),sources,maxBytes,maxRows);data.require("uid","gene","db","tissue","p","FDR","alignedDirection");for(var row:data.rows())eqtls.add(new FumaVariantGeneMapping.Eqtl(data.v(row,"uid"),data.v(row,"gene"),data.v(row,"db"),data.v(row,"tissue"),numeric(data.v(row,"p")),numeric(data.v(row,"FDR")),data.v(row,"alignedDirection").equals("NA")?"":data.v(row,"alignedDirection")));}
                    FumaChromatinMapping.Inputs ciInput=null;
                    if(ciEnabled){
                        var data=read(Path.of(required(o,"interactions")),sources,maxBytes,maxRows);data.require("interaction_id","region1","region2","genes","rsIDs","tissue");var interactions=new ArrayList<FumaChromatinMapping.Interaction>();
                        for(var row:data.rows())interactions.add(new FumaChromatinMapping.Interaction(data.v(row,"interaction_id"),new FumaChromatinMapping.Region(data.v(row,"region1")),new FumaChromatinMapping.Region(data.v(row,"region2")),tokens(data.v(row,"genes"),"NA",":"),tokens(data.v(row,"rsIDs"),"NA",";"),data.v(row,"tissue")));
                        Set<String> enhancers=null;String enhancer=required(o,"ci-enhancers");if(!enhancer.equals("none")){var inputEnh=read(Path.of(enhancer),sources,maxBytes,maxRows);inputEnh.require("uid");var ids=new HashSet<String>();for(var row:inputEnh.rows())if(!ids.add(inputEnh.v(row,"uid")))throw new IOException("Duplicate enhancer candidate UID");enhancers=ids;}
                        var promoters=new ArrayList<FumaChromatinMapping.Promoter>();String promoter=required(o,"ci-promoters");if(!promoter.equals("none")){var inputProm=read(Path.of(promoter),sources,maxBytes,maxRows);inputProm.require("region2","genes");for(var row:inputProm.rows())promoters.add(new FumaChromatinMapping.Promoter(new FumaChromatinMapping.Region(inputProm.v(row,"region2")),tokens(inputProm.v(row,"genes"),"NA",":")));}
                        ciInput=new FumaChromatinMapping.Inputs(interactions,filter(o,"ci",true),enhancers,!promoter.equals("none"),promoters,choice(o,"ci-membership",Set.of("source-global","per-interaction")).equals("source-global")?FumaChromatinMapping.Membership.SOURCE_GLOBAL:FumaChromatinMapping.Membership.PER_INTERACTION,choice(o,"ci-coordinates",Set.of("source-closed","bed")).equals("source-closed")?FumaChromatinMapping.Coordinates.SOURCE_CLOSED:FumaChromatinMapping.Coordinates.BED,integer(o,"ci-max-overlaps",1000000,1,10000000));
                    }
                    var protocol=new FumaVariantGeneMapping.Options(o.get("annotation-id"),Set.copyOf(tokens(o.get("biotypes"),"all",":")),switch(mhc){case "keep"->FumaVariantGeneMapping.Mhc.KEEP;case "source-endpoints"->FumaVariantGeneMapping.Mhc.SOURCE_ENDPOINTS;default->FumaVariantGeneMapping.Mhc.FULL_OVERLAP;},low,high,positional,pos.equals("window")?Integer.valueOf(required(o,"window-bp")):null,pos.equals("consequence")?required(o,"consequence-regex"):null,filter(o,"pos",positional),eqtl,filter(o,"eqtl",eqtl),new FumaVariantGeneMapping.EqtlSelection(eqtl?Set.copyOf(tokens(required(o,"eqtl-tissues"),"all",":")):Set.of(),eqtl?cutoff(required(o,"eqtl-p")):Double.NaN,eqtl?cutoff(required(o,"eqtl-fdr")):Double.NaN),maxEvidence);
                    var result=FumaVariantGeneMapping.map(genes,candidates,annotations,consequences,eqtls,protocol,ciInput);warnings.addAll(result.warnings());
                    FumaGeneScoreAnnotations.Result scoreJoin=null;var scoreRows=new LinkedHashMap<String,FumaGeneScoreAnnotations.Joined>();
                    if(o.containsKey("gene-scores")){
                        var scores=read(Path.of(o.get("gene-scores")),sources,maxBytes,maxRows);int maxColumns=integer(o,"max-score-columns",256,1,10000);if(scores.header().size()-4>maxColumns)throw new IOException("Gene-score column budget exceeded;review --max-score-columns");long maxCells=Long.parseLong(o.getOrDefault("max-score-cells","1000000"));
                        var scoreResource=new FumaGeneScoreAnnotations.Resource(required(o,"gene-score-id"),scores.header(),scores.rows().stream().map(r->Arrays.asList(r)).toList());var duplicatePolicy=choice(o,"score-duplicates",Set.of("error","first")).equals("error")?FumaGeneScoreAnnotations.Duplicates.ERROR:FumaGeneScoreAnnotations.Duplicates.FIRST;var missingPolicy=choice(o,"score-missing",Set.of("error","retain")).equals("error")?FumaGeneScoreAnnotations.Missing.ERROR:FumaGeneScoreAnnotations.Missing.RETAIN;
                        scoreJoin=FumaGeneScoreAnnotations.join(result.genes().stream().map(g->g.gene().id()).toList(),scoreResource,duplicatePolicy,missingPolicy,maxCells);warnings.addAll(scoreJoin.warnings());for(var row:scoreJoin.joined())scoreRows.put(row.ensg(),row);
                        table(directory.resolve("gene-score-join.tsv"),List.of("target_row","gene","source_line","status"),scoreJoin.joined().stream().map(r->new String[]{""+r.targetRow(),r.ensg(),""+r.sourceLine(),r.status()}).toList());table(directory.resolve("gene-score-resource.tsv"),List.of("source_line","ensg","status","first_source_line","occurrences"),scoreJoin.resourceRows().stream().map(r->new String[]{""+r.sourceLine(),r.ensg(),r.status(),""+r.firstSourceLine(),""+r.occurrences()}).toList());
                        session.put("gene_score_resource_id",scoreResource.identity());session.put("gene_score_columns",scoreJoin.outputColumns());session.put("gene_score_resource_rows",scoreResource.rows().size());session.put("gene_score_payload_policy","raw-text columns5 onward;exact ensg;score. prefix;no score calculation");
                    }
                    var candidateHeader=new ArrayList<>(List.of("uid","positional_filter","positional_mapped","eqtl_filter","eqtl_mapped"));var candidateRows=new ArrayList<String[]>();var ciCandidates=new LinkedHashMap<String,FumaChromatinMapping.CandidateDisposition>();for(var c:result.chromatin().candidates())ciCandidates.put(c.uid(),c);
                    if(ciEnabled)candidateHeader.addAll(List.of("chromatin_filtered_pool","chromatin_pool_flag","chromatin_mapped"));for(var v:result.variants()){var row=new ArrayList<>(List.of(v.variant().uid(),""+v.positionalFilter(),""+v.positionalMapped(),""+v.eqtlFilter(),""+v.eqtlMapped()));if(ciEnabled){var c=ciCandidates.get(v.variant().uid());row.addAll(List.of(""+c.filteredPool(),""+c.retainedPoolFlag(),""+c.mapped()));}candidateRows.add(row.toArray(String[]::new));}
                    table(directory.resolve("candidate-disposition.tsv"),candidateHeader,candidateRows);
                    table(directory.resolve("excluded-genes.tsv"),List.of("gene"),result.excludedGenes().stream().map(v->new String[]{v}).toList());
                    var ciGenes=new LinkedHashMap<String,FumaChromatinMapping.GeneMapping>();for(var c:result.chromatin().genes())ciGenes.put(c.gene(),c);
                    if(ciEnabled){
                        table(directory.resolve("chromatin-evidence.tsv"),List.of("interaction_id","uid","gene","tissue","listed_in_interaction"),result.chromatin().evidence().stream().map(c->new String[]{c.interactionId(),c.uid(),c.gene(),c.tissue(),""+c.listedInInteraction()}).toList());
                        var interactions=new ArrayList<String[]>();for(var d:result.chromatin().interactions()){var c=d.interaction();interactions.add(new String[]{c.id(),c.region1().label(),c.region2().label(),String.join(":",c.genes()),String.join(":",d.effectiveGenes()),String.join(":",d.eligibleGenes()),String.join(";",c.rsIds()),c.tissue(),String.join(";",d.admittingUids()),String.join(";",d.finalOverlapUids()),""+d.retained(),d.status()});}
                        table(directory.resolve("chromatin-interactions.tsv"),List.of("interaction_id","region1","region2","original_genes","effective_genes","eligible_genes","declared_rsIDs","tissue","admitting_uids","final_overlap_uids","retained","status"),interactions);
                        session.put("chromatin_membership",o.get("ci-membership"));session.put("chromatin_coordinates",o.get("ci-coordinates"));session.put("chromatin_overlap_hits",result.chromatin().overlapHits());session.put("chromatin_evidence_rows",result.chromatin().evidence().size());session.put("chromatin_retained_interactions",result.chromatin().interactions().stream().filter(FumaChromatinMapping.InteractionDisposition::retained).count());
                    }
                    long supportedGenes=result.genes().stream().filter(g->g.positionalVariants()>0||g.eqtlVariants()>0||ciGenes.containsKey(g.gene().id())&&!ciGenes.get(g.gene().id()).variantUids().isEmpty()).count();
                    session.put("retained_gene_rows",result.genes().size());session.put("mapped_genes",supportedGenes);session.put("declared_only_gene_rows",result.genes().size()-supportedGenes);
                    if(supportedGenes==0&&o.get("empty").equals("error"))throw new IOException("No gene has actual positional/eQTL/chromatin links; retained chromatin declarations alone are unsupported. Inspect disposition/evidence audits and correct inputs, or explicitly choose --empty retain");
                    table(directory.resolve("positional-evidence.tsv"),List.of("uid","gene","consequence"),result.positional().stream().map(p->new String[]{p.uid(),p.gene(),p.consequence()}).toList());
                    table(directory.resolve("eqtl-evidence.tsv"),List.of("uid","gene","db","tissue","p","FDR","alignedDirection"),result.eqtls().stream().map(e->new String[]{e.uid(),e.gene(),e.database(),e.tissue(),n(e.p()),n(e.fdr()),e.alignedDirection().isEmpty()?"NA":e.alignedDirection()}).toList());
                    var rows=new ArrayList<String[]>();for(var r:result.genes()){var g=r.gene();var row=new ArrayList<>(List.of(g.id(),g.symbol(),""+g.chromosome(),""+g.start(),""+g.end(),g.strand(),g.biotype(),""+r.positionalVariants(),""+r.positionalEvidenceRows(),n(r.maxCadd()),""+r.eqtlVariants(),n(r.minEqtlP()),n(r.minEqtlFdr()),String.join(":",r.tissues()),r.direction().isEmpty()?"NA":r.direction(),n(r.minGwasP()),String.join(";",r.independentRsIds()),String.join(":",r.loci())));if(ciEnabled){var c=ciGenes.get(g.id());row.addAll(List.of(""+(c!=null),c==null?"0":""+c.variantUids().size(),c==null?"0":""+c.evidenceRows(),c==null?"":String.join(":",c.tissues())));}if(scoreJoin!=null)row.addAll(scoreRows.get(g.id()).values());rows.add(row.toArray(String[]::new));}
                    var geneHeader=new ArrayList<>(List.of("gene","symbol","chr","start","end","strand","biotype","positional_variants","positional_evidence_rows","positional_max_cadd","eqtl_variants","min_eqtl_p","min_eqtl_fdr","eqtl_tissues","eqtl_direction","min_gwas_p","independent_rsIDs","loci"));if(ciEnabled)geneHeader.addAll(List.of("chromatin_declared","chromatin_variants","chromatin_evidence_rows","chromatin_tissues"));if(scoreJoin!=null)geneHeader.addAll(scoreJoin.outputColumns());table(directory.resolve("genes.tsv"),geneHeader,rows);
                    warnings.add("NATIVE SUMMARY POLICY: missing maximum CADD remains NA; unique variant counts, deduplicated tissue labels, and unique independent-SNP/locus lists are reported separately from retained association/consequence rows. Legacy geneMap.R can count multiple consequence rows as multiple SNPs, substitutes zero for absent CADD and can repeat formatted tissues or compound LD labels. Required action: retain evidence tables and do not claim byte-identical legacy summary behavior.");
                    session.put("annotation_id",o.get("annotation-id"));session.put("gene_rows",genes.size());session.put("candidate_rows",candidates.size());session.put("positional_evidence_rows",result.positional().size());session.put("eqtl_evidence_rows",result.eqtls().size());session.put("options",o);
                    for(var entry:sources.entrySet())if(!hash(entry.getKey()).equals(entry.getValue()))throw new IOException("Input changed during analysis: "+entry.getKey());
                    manifest.put("analysis_status",supportedGenes==0?"completed-empty-with-review-warnings":"completed-with-review-warnings");
                } catch(Exception failure){code[0]=2;manifest.put("analysis_status","failed");session.put("error",failure.toString());RnaToolRuntime.quarantine(directory);Files.createDirectories(directory.resolve("partial"));session.put("retained_files_location","partial/");warnings.add(0,"ANALYSIS FAILED: "+failure.getMessage()+". Required action: inspect session.json and any retained partial/ audits, correct the named input, option or resource bound, and rerun to a new directory. No completed result is admitted.");}
                var hashes=new LinkedHashMap<String,String>();sources.forEach((p,h)->hashes.put(p.toString(),h));manifest.put("input_sha256",hashes);session.put("input_sha256",hashes);session.put("warnings",warnings);session.put("status",manifest.get("analysis_status"));Files.writeString(directory.resolve("session.json"),RegenieRuntime.json(session)+"\n");Files.writeString(directory.resolve("analysis-status.json"),RegenieRuntime.json(Map.of("status",manifest.get("analysis_status")))+"\n");Files.writeString(directory.resolve("WARNINGS.md"),"# FUMA GENE MAPPING "+(code[0]==0?"WARNINGS REQUIRE REVIEW":"ANALYSIS FAILED")+"\n\n"+String.join("\n\n",warnings)+"\n");
            });
            err.println((code[0]==0?"ANALYSIS WARNINGS: ":"ANALYSIS FAILED: ")+Path.of(o.get("out"),"WARNINGS.md"));if(code[0]==0)out.println("Native supplied-resource FUMA gene mapping completed: "+o.get("out"));return code[0];
        }catch(Exception failure){err.println("ANALYSIS FAILED: "+failure.getMessage()+"\nRequired action: correct the named option/file and choose a new writable --out directory.\n"+SCOPE);return 2;}
    }
    private static FumaVariantGeneMapping.Filter filter(Map<String,String> o,String prefix,boolean enabled){
        if(!enabled){for(String key:o.keySet())if(key.startsWith(prefix+"-")&&(key.endsWith("cadd")||key.endsWith("rdb")||key.contains("state")||key.contains("binary")))throw new IllegalArgumentException("Filter supplied for disabled "+prefix+" module");return new FumaVariantGeneMapping.Filter(0,Double.NaN,List.of(),FumaVariantGeneMapping.States.ANY,7,List.of(),FumaVariantGeneMapping.Binary.ANY);}
        double cadd=number(o,prefix+"-cadd",Double.parseDouble(required(o,prefix+"-cadd"))),rdb=cutoffScore(required(o,prefix+"-rdb"));var states=tokens(required(o,prefix+"-states"),"none",":");var binary=tokens(required(o,prefix+"-binary"),"none",":");
        if(states.isEmpty()&&(o.containsKey(prefix+"-state-method")||o.containsKey(prefix+"-state-max"))||binary.isEmpty()&&o.containsKey(prefix+"-binary-method"))throw new IllegalArgumentException("Aggregation options require selected annotation tracks");
        return new FumaVariantGeneMapping.Filter(cadd,rdb,states,states.isEmpty()?FumaVariantGeneMapping.States.ANY:switch(choice(o,prefix+"-state-method",Set.of("any","mode","all"))){case "any"->FumaVariantGeneMapping.States.ANY;case "mode"->FumaVariantGeneMapping.States.MODE;default->FumaVariantGeneMapping.States.ALL;},states.isEmpty()?7:Integer.parseInt(required(o,prefix+"-state-max")),binary,binary.isEmpty()?FumaVariantGeneMapping.Binary.ANY:FumaVariantGeneMapping.Binary.valueOf(choice(o,prefix+"-binary-method",Set.of("any","majority","all")).toUpperCase(java.util.Locale.ROOT)));
    }
    private static List<String> tokens(String value,String empty,String separator){if(value.equals(empty))return List.of();var result=Arrays.asList(value.split(java.util.regex.Pattern.quote(separator),-1));if(result.stream().anyMatch(String::isBlank)||new HashSet<>(result).size()!=result.size())throw new IllegalArgumentException("Empty/duplicate list entries: "+value);return result;}
    private static String choice(Map<String,String> o,String name,Set<String> values){String value=required(o,name);if(!values.contains(value))throw new IllegalArgumentException("--"+name+" must be one of "+values);return value;}
    private static double numeric(String value){if(value.equals("NA"))return Double.NaN;double number=Double.parseDouble(value);if(!Double.isFinite(number))throw new IllegalArgumentException("Require a finite decimal or explicit NA: "+value);return number;}
    private static double cutoffScore(String value){return value.equals("none")?Double.NaN:numeric(value.equals("NA")?"invalid-cutoff":value);}
    private static double cutoff(String value){double v=cutoffScore(value);if(v<0||v>1)throw new IllegalArgumentException("eQTL cutoffs must be none or in [0,1]");return v;}
    private static int chromosome(String value){return value.equals("X")?23:Integer.parseInt(value);}
    private static String n(double value){return Double.isNaN(value)?"NA":Double.toString(value);}
    private static Map<String,String> parse(String[] args)throws Exception{var direct=options(args,ALLOWED);if(!direct.containsKey("mapping-options-file"))return direct;Path p=Path.of(direct.get("mapping-options-file")).toAbsolutePath().normalize();byte[] raw=AdmixtureInputs.raw(p,1048576);var result=new LinkedHashMap<String,String>();for(var e:AdmixtureInputs.decode(raw).entrySet()){String key=e.getKey().toString();if(!ALLOWED.contains(key)||key.equals("mapping-options-file")||!(e.getValue() instanceof String value))throw new IOException("Unknown, recursive or non-string mapping option");result.put(key,value);}result.putAll(direct);result.put("options-sha256",AdmixtureInputs.digest(raw));return result;}
    private record Data(List<String> header,List<String[]> rows){void require(String... names)throws IOException{for(String name:names)if(!header.contains(name))throw new IOException("Missing TSV column: "+name);}String v(String[] row,String name)throws IOException{int i=header.indexOf(name);if(i<0)throw new IOException("Missing TSV column: "+name);return row[i];}int i(String[] row,String name)throws IOException{return Integer.parseInt(v(row,name));}}
    private static Data read(Path path,Map<Path,String> sources,int bytes,int rows)throws Exception{
        path=path.toAbsolutePath().normalize();byte[] raw=AdmixtureInputs.raw(path,bytes);String digest=AdmixtureInputs.digest(raw),old=sources.putIfAbsent(path,digest);if(old!=null&&!old.equals(digest))throw new IOException("Input changed: "+path);String text=StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(raw)).toString();var lines=text.lines().toList();if(lines.isEmpty()||lines.size()-1>rows)throw new IOException("Empty or oversized TSV: "+path);var header=Arrays.asList(lines.get(0).split("\t",-1));if(header.stream().anyMatch(String::isBlank)||new HashSet<>(header).size()!=header.size())throw new IOException("Empty/duplicate TSV headers: "+path);var result=new ArrayList<String[]>();for(int i=1;i<lines.size();i++){String line=lines.get(i);String[] row=line.split("\t",-1);if(line.length()>1048576||line.indexOf('\0')>=0||row.length!=header.size())throw new IOException("Malformed TSV row "+(i+1)+": "+path);result.add(row);}return new Data(header,result);
    }
    static String help(){return """
        fuma-tools --method map-genes --mapping-options-file OPTIONS.json
        Required: --genes GENES.tsv --candidates CANDIDATES.tsv --annotations ANNOTATIONS.tsv
          --annotation-id BUILD/RELEASE --biotypes all|TYPE:TYPE --mhc keep|source-endpoints|full-overlap
          --mhc-extension none|START:END --positional none|window|consequence --eqtl none|supplied
          --empty error|retain --out NEW_DIRECTORY
        Window: --window-bp N. Consequence: --consequences TSV --consequence-regex REGEX.
        Each enabled module: --pos-cadd N --pos-rdb none|N --pos-states none|S1:S2
          --pos-binary none|B1:B2 (use eqtl- prefix for eQTL filters).
        Selected states: --pos-state-method any|mode|all --pos-state-max N.
        Selected binary tracks: --pos-binary-method any|majority|all (eqtl- prefix likewise).
        eQTL: --eqtls TSV --eqtl-p none|P --eqtl-fdr none|Q --eqtl-tissues all|TISSUE:TISSUE.
        Chromatin (optional, default none): --chromatin supplied --interactions TSV
          --ci-membership source-global|per-interaction --ci-coordinates source-closed|bed
          --ci-enhancers none|UID_TSV --ci-promoters none|REGION_GENE_TSV
          --ci-cadd N --ci-rdb none|N --ci-states none|S1:S2 --ci-binary none|B1:B2.
        Selected state/binary aggregation uses the ci- prefix; --ci-max-overlaps 1000000
          bounds stored region hits. Pool flags and retained gene declarations are distinct
          from actual chromatin-evidence.tsv links; inspect every interaction disposition.
        Missing table numbers use NA. All carrier values are strings; direct options override carrier.
        Optional score annotations: --gene-scores TSV --gene-score-id RESOURCE_RELEASE
          --score-duplicates error|first --score-missing error|retain. Exact ensg matching;
          columns5 onward are copied as text,with score. headers; no score is calculated.
          --max-score-cells 1000000 --max-score-columns 256. Read both score ledgers.
        Limits: --max-bytes 268435456 --max-rows 1000000 --max-evidence 1000000.
        Exit0 completed with review warnings;2 failed with explicit required action and partial quarantine.
        """+SCOPE;
    }
}
