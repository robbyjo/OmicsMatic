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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlinalg.genetics.FumaLocusDefinition;
import static org.jlinalg.cli.FollowupSupport.*;

/** Explicit local LD/MAF/GWAS locus construction, with corrected complete memberships. */
final class FumaLociCli {
    private FumaLociCli() { }
    private static final Set<String> ALLOWED=Set.of("method","gwas","reference","ld","reference-id","ld-floor",
        "lead-p","candidate-p","maf","independent-r2","lead-r2","merge-distance-bp","merge-boundary",
        "include-reference","mhc","regions","predefined","add-independent","ties","unmatched-reference",
        "empty","max-bytes","max-gwas","max-reference","max-pairs","max-work","out","locus-options-file");
    private static final String SCOPE="NATIVE LOCUS MODULE ONLY: supplied-reference LD/MAF selection, independent variants, lead SNPs and consistent genomic-risk-locus components. No LD estimation, resource download, liftover, annotation, ANNOVAR, MAGMA, expression or hosted FUMA analysis is performed. Required action: declare the exact reference assembly/population/release and complete sparse-LD floor; do not use this module as a full SNP2GENE workflow.";
    static int run(String[] args,PrintStream out,PrintStream err) {
        if (Arrays.asList(args).contains("--help")) {out.println(help());return 0;}
        try {
            Map<String,String> o=parse(args);if (!required(o,"method").equals("loci")) throw new IllegalArgumentException("--method must be loci");
            for (String name:List.of("gwas","reference","ld","reference-id","ld-floor","lead-p","candidate-p","maf","independent-r2","lead-r2","merge-distance-bp","merge-boundary","include-reference","mhc","add-independent","ties","unmatched-reference","empty","out")) required(o,name);
            int[] code={0};transaction(o,"fuma-loci",(directory,manifest)->{
                Map<String,Object> session=new LinkedHashMap<>();Map<Path,String> sources=new LinkedHashMap<>();List<String> warnings=new ArrayList<>();warnings.add(SCOPE);
                try {
                    if (o.containsKey("locus-options-file")) {Path p=Path.of(o.get("locus-options-file")).toAbsolutePath().normalize();if (!hash(p).equals(o.get("options-sha256"))) throw new IOException("Options carrier changed before analysis");sources.put(p,o.get("options-sha256"));}
                    int maxBytes=integer(o,"max-bytes",268435456,1,1073741824),maxGwas=integer(o,"max-gwas",1000000,1,2000000),maxReference=integer(o,"max-reference",2000000,1,10000000),maxPairs=integer(o,"max-pairs",2000000,0,20000000);
                    long merge=Long.parseLong(o.get("merge-distance-bp")),work=Long.parseLong(o.getOrDefault("max-work","100000000"));
                    String boundary=choice(o,"merge-boundary",Set.of("strict","inclusive")),ties=choice(o,"ties",Set.of("error","position"));choice(o,"unmatched-reference",Set.of("error","exclude"));choice(o,"empty",Set.of("error","retain"));
                    var gwasTable=read(Path.of(o.get("gwas")),sources,maxBytes,maxGwas);gwasTable.require("chr","pos","non_effect_allele","effect_allele","rsID","p");List<FumaLocusDefinition.Gwas> gwas=new ArrayList<>();Map<String,String> literalP=new HashMap<>();Map<String,FumaLocusDefinition.Gwas> gwasByUid=new HashMap<>();
                    for (String[] row:gwasTable.rows()) {
                        var g=new FumaLocusDefinition.Gwas(gwasTable.integer(row,"chr"),gwasTable.integer(row,"pos"),gwasTable.value(row,"non_effect_allele"),gwasTable.value(row,"effect_allele"),gwasTable.value(row,"rsID"),finite(gwasTable.value(row,"p"),"GWAS P"));gwas.add(g);literalP.put(g.uid(),gwasTable.value(row,"p"));gwasByUid.put(g.uid(),g);
                    }
                    var refTable=read(Path.of(o.get("reference")),sources,maxBytes,maxReference);refTable.require("chr","pos","A1","A2","rsID","MAF");List<FumaLocusDefinition.ReferenceVariant> variants=new ArrayList<>();
                    for (String[] row:refTable.rows()) variants.add(new FumaLocusDefinition.ReferenceVariant(refTable.integer(row,"chr"),refTable.integer(row,"pos"),refTable.value(row,"A1"),refTable.value(row,"A2"),refTable.value(row,"rsID"),finite(refTable.value(row,"MAF"),"reference MAF")));
                    var ldTable=read(Path.of(o.get("ld")),sources,maxBytes,maxPairs);ldTable.require("chr","pos1","pos2","r2");List<FumaLocusDefinition.LdPair> pairs=new ArrayList<>();for (String[] row:ldTable.rows()) pairs.add(new FumaLocusDefinition.LdPair(ldTable.integer(row,"chr"),ldTable.integer(row,"pos1"),ldTable.integer(row,"pos2"),finite(ldTable.value(row,"r2"),"LD r2")));
                    List<FumaLocusDefinition.Interval> regions=new ArrayList<>();
                    if (o.containsKey("regions")) {var input=read(Path.of(o.get("regions")),sources,maxBytes,100000);input.require("chr","start","end");for (String[] row:input.rows()) regions.add(new FumaLocusDefinition.Interval(input.integer(row,"chr"),input.integer(row,"start"),input.integer(row,"end")));if (regions.isEmpty()) throw new IOException("Empty predefined-region table");}
                    List<String> seeds=new ArrayList<>();if (o.containsKey("predefined")) {var input=read(Path.of(o.get("predefined")),sources,maxBytes,maxGwas);input.require("uid");for (String[] row:input.rows()) seeds.add(input.value(row,"uid"));if (seeds.isEmpty()) throw new IOException("Empty predefined-UID table");}
                    boolean add=bool(o,"add-independent",true);if (seeds.isEmpty()&&!add) throw new IllegalArgumentException("Without predefined UIDs, --add-independent must be true");
                    FumaLocusDefinition.Interval mhc=null;
                    if (!o.get("mhc").equals("none")) {String[] values=o.get("mhc").split(":",-1);if (values.length!=3||!values[0].equals("6")) throw new IllegalArgumentException("--mhc must be none or 6:START:END in the declared assembly");mhc=new FumaLocusDefinition.Interval(6,Integer.parseInt(values[1]),Integer.parseInt(values[2]));}
                    var reference=new FumaLocusDefinition.Reference(o.get("reference-id"),variants,pairs,number(o,"ld-floor",0));
                    var protocol=new FumaLocusDefinition.Options(number(o,"lead-p",5e-8),number(o,"candidate-p",.05),number(o,"maf",0),number(o,"independent-r2",.6),number(o,"lead-r2",.1),merge,bool(o,"include-reference",true),mhc,regions,seeds,add,ties.equals("error")?FumaLocusDefinition.Ties.ERROR:FumaLocusDefinition.Ties.POSITION,boundary.equals("strict")?FumaLocusDefinition.MergeBoundary.SOURCE_STRICT:FumaLocusDefinition.MergeBoundary.INCLUSIVE,work);
                    var result=FumaLocusDefinition.define(gwas,reference,protocol);warnings.addAll(result.warnings());
                    table(directory.resolve("input-gwas.tsv"),gwasTable.header(),gwasTable.rows());
                    table(directory.resolve("gwas-disposition.tsv"),List.of("uid","original_decimal_p","parsed_p","disposition"),result.dispositions().stream().map(d->new String[]{d.uid(),literalP.get(d.uid()),Double.toString(gwasByUid.get(d.uid()).pValue()),d.status()}).toList());
                    if (o.get("unmatched-reference").equals("error")&&result.dispositions().stream().anyMatch(d->d.status().equals("absent-or-allele-mismatched-reference"))) throw new IOException("Unmatched GWAS/reference variants; review the disposition ledger and correct assembly/alleles/reference coverage, or explicitly choose --unmatched-reference exclude");
                    if (result.independent().isEmpty()&&o.get("empty").equals("error")) throw new IOException("No independent variant remained; review reference/MAF/MHC/region/P/seed eligibility, or explicitly retain an empty analysis");
                    Map<String,Integer> locus=new HashMap<>();for (var l:result.loci()) for (String uid:l.independentUids()) locus.put(uid,l.index());Map<String,FumaLocusDefinition.Candidate> byUid=new HashMap<>();for (var c:result.candidates()) byUid.put(c.uid(),c);
                    List<String[]> candidates=new ArrayList<>();
                    for (var c:result.candidates()) {var r=c.reference();var g=c.gwas();candidates.add(new String[]{c.uid(),g==null?r.rsId():g.rsId(),""+r.chromosome(),""+r.position(),g==null?r.a2():g.nonEffectAllele(),g==null?r.a1():g.effectAllele(),""+r.maf(),g==null?"NA":literalP.get(c.uid()),g==null?"NA":""+g.pValue(),c.originalInput()==null?"NA":literalP.get(c.uid()),Boolean.toString(g==null),c.bestIndependentUid(),""+c.maximumR2(),""+c.locus()});}
                    table(directory.resolve("candidates.tsv"),List.of("uid","rsID","chr","pos","non_effect_allele","effect_allele","MAF","gwasP","parsed_gwasP","original_input_p","reference_only","best_independent_uid","r2","locus"),candidates);
                    List<String[]> independent=new ArrayList<>(),links=new ArrayList<>();int index=0;
                    for (var ind:result.independent()) {var g=ind.variant();independent.add(new String[]{""+(++index),""+locus.get(g.uid()),g.uid(),g.rsId(),""+g.chromosome(),""+g.position(),literalP.get(g.uid()),""+ind.candidateUids().size(),""+ind.gwasCandidates(),""+ind.sourceNewCandidates(),""+ind.sourceNewGwasCandidates(),Boolean.toString(ind.predefined()),""+ind.processingOrder()});for (String uid:ind.candidateUids()) links.add(new String[]{g.uid(),uid,""+reference.storedRSquared(g.uid(),uid).orElseThrow(),Boolean.toString(byUid.get(uid).gwas()!=null)});}
                    table(directory.resolve("independent.tsv"),List.of("no","locus","uid","rsID","chr","pos","p","all_linked_candidates","all_linked_gwas_candidates","source_new_candidates","source_new_gwas_candidates","predefined","processing_order"),independent);
                    table(directory.resolve("ld-memberships.tsv"),List.of("independent_uid","candidate_uid","r2","gwas_candidate"),links);
                    List<String[]> leads=new ArrayList<>();index=0;for (var lead:result.leads()) {var g=lead.variant();leads.add(new String[]{""+(++index),""+locus.get(g.uid()),g.uid(),g.rsId(),""+g.chromosome(),""+g.position(),literalP.get(g.uid()),""+lead.independentUids().size(),String.join(";",lead.independentUids())});}
                    table(directory.resolve("leads.tsv"),List.of("no","locus","uid","rsID","chr","pos","p","n_independent","independent_uids"),leads);
                    List<String[]> loci=new ArrayList<>();for (var l:result.loci()) loci.add(new String[]{""+l.index(),l.top().uid(),l.top().rsId(),""+l.chromosome(),""+l.top().position(),literalP.get(l.top().uid()),""+l.start(),""+l.end(),""+l.candidateUids().size(),""+l.gwasCandidates(),""+l.independentUids().size(),String.join(";",l.independentUids()),""+l.leadUids().size(),String.join(";",l.leadUids())});
                    table(directory.resolve("loci.tsv"),List.of("locus","top_uid","top_rsID","chr","top_pos","p","start","end","n_candidates","n_gwas_candidates","n_independent","independent_uids","n_leads","lead_uids"),loci);
                    session.put("reference_id",reference.identity());session.put("reference_variants",reference.variantCount());session.put("ld_floor",reference.storedMinimumR2());session.put("gwas_rows",gwas.size());session.put("candidate_rows",result.candidates().size());session.put("independent_variants",result.independent().size());session.put("lead_variants",result.leads().size());session.put("risk_loci",result.loci().size());session.put("work",result.work());session.put("locus_rule","complete-transitive-interval-components");session.put("merge_boundary",boundary);session.put("p_parser","Java full-precision finite double;original decimal strings retained");session.put("options",o);
                    warnings.add("LD COVERAGE AND LOCUS RULE REQUIRE REVIEW: unlisted within-chromosome pairs are declared strictly below --ld-floor; a zero floor requires all pairs. The module cannot prove omitted LD or reference ancestry/build from a sparse file. Complete interval components correct observed original backward-bridge defects; legacy count columns and complete block counts are distinct. Inspect every declared resource and ld-memberships.tsv/independent.tsv/loci.tsv before downstream use.");
                    for (var e:sources.entrySet()) if (!hash(e.getKey()).equals(e.getValue())) throw new IOException("Input changed during analysis: "+e.getKey());
                    manifest.put("analysis_status",result.independent().isEmpty()?"completed-empty-with-review-warnings":"completed-with-review-warnings");
                } catch (Exception failure) {code[0]=2;manifest.put("analysis_status","failed");session.put("error",failure.toString());RnaToolRuntime.quarantine(directory);session.put("retained_files_location","partial/");warnings.add(0,"ANALYSIS FAILED: "+failure.getMessage()+". Required action: inspect session.json and partial/ audits, correct the named input/parameter/resource-budget issue, and rerun to a new directory. No partial table is admitted as a completed analysis.");}
                Map<String,String> hashes=new LinkedHashMap<>();for (var e:sources.entrySet()) hashes.put(e.getKey().toString(),e.getValue());session.put("input_sha256",hashes);manifest.put("input_sha256",hashes);session.put("status",manifest.get("analysis_status"));session.put("warnings",warnings);Files.writeString(directory.resolve("session.json"),RegenieRuntime.json(session)+"\n");Files.writeString(directory.resolve("analysis-status.json"),RegenieRuntime.json(Map.of("status",manifest.get("analysis_status")))+"\n");Files.writeString(directory.resolve("WARNINGS.md"),"# FUMA LOCUS "+(code[0]==0?"WARNINGS REQUIRE REVIEW":"ANALYSIS FAILED")+"\n\n"+String.join("\n\n",warnings)+"\n");
            });
            err.println((code[0]==0?"ANALYSIS WARNINGS: ":"ANALYSIS FAILED: ")+Path.of(o.get("out"),"WARNINGS.md"));if (code[0]==0) out.println("Native supplied-reference FUMA locus module completed: "+o.get("out"));return code[0];
        } catch (Exception failure) {err.println("ANALYSIS FAILED: "+failure.getMessage()+"\nRequired action: correct the named option/file and choose a new writable --out directory.\n"+SCOPE);return 2;}
    }
    private static Map<String,String> parse(String[] args) throws Exception {
        Map<String,String> direct=options(args,ALLOWED);if (!direct.containsKey("locus-options-file")) return direct;Path path=Path.of(direct.get("locus-options-file")).toAbsolutePath().normalize();byte[] raw=AdmixtureInputs.raw(path,1048576);Map<?,?> values=AdmixtureInputs.decode(raw);Map<String,String> result=new LinkedHashMap<>();
        for (var entry:values.entrySet()) {String key=entry.getKey().toString();if (!ALLOWED.contains(key)||key.equals("locus-options-file")||!(entry.getValue() instanceof String value)) throw new IOException("Unknown, recursive or non-string locus-options-file option");result.put(key,value);}
        result.putAll(direct);result.put("options-sha256",AdmixtureInputs.digest(raw));return result;
    }
    private record Data(List<String> header,List<String[]> rows) {
        void require(String... names) throws IOException {for (String name:names) if (!header.contains(name)) throw new IOException("Missing TSV column: "+name);}
        String value(String[] row,String name) throws IOException {int i=header.indexOf(name);if (i<0) throw new IOException("Missing TSV column: "+name);return row[i];}
        int integer(String[] row,String name) throws IOException {return Integer.parseInt(value(row,name));}
    }
    private static Data read(Path path,Map<Path,String> sources,int maxBytes,int maxRows) throws Exception {
        path=path.toAbsolutePath().normalize();byte[] raw=AdmixtureInputs.raw(path,maxBytes);String h=AdmixtureInputs.digest(raw);String prior=sources.putIfAbsent(path,h);if (prior!=null&&!prior.equals(h)) throw new IOException("Input changed during analysis: "+path);String text=StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(raw)).toString();List<String> lines=text.lines().toList();if (lines.isEmpty()) throw new IOException("Empty TSV: "+path);List<String> header=Arrays.asList(lines.get(0).split("\t",-1));if (header.stream().anyMatch(String::isBlank)||new HashSet<>(header).size()!=header.size()) throw new IOException("Empty/duplicate TSV headers: "+path);List<String[]> rows=new ArrayList<>();
        for (int i=1;i<lines.size();i++) {String line=lines.get(i);if (line.length()>1048576||line.indexOf('\0')>=0) throw new IOException("Oversized or NUL TSV row: "+path);String[] row=line.split("\t",-1);if (row.length!=header.size()) throw new IOException("TSV row width mismatch at "+path+":"+(i+1));rows.add(row);if (rows.size()>maxRows) throw new IOException("TSV row budget exceeded: "+path);}return new Data(header,rows);
    }
    private static String choice(Map<String,String> o,String name,Set<String> values) {String value=required(o,name);if (!values.contains(value)) throw new IllegalArgumentException("--"+name+" must be one of "+values);return value;}
    private static String help() {return """
        Native supplied-reference FUMA locus module (Java only; broader SNP2GENE unfinished).
          fuma-tools --method loci --gwas GWAS.tsv --reference REFERENCE.tsv --ld LD.tsv
            --reference-id ASSEMBLY_POPULATION_RELEASE --ld-floor 0.05
            --lead-p 5e-8 --candidate-p 0.05 --maf 0 --independent-r2 0.6 --lead-r2 0.1
            --merge-distance-bp 250000 --merge-boundary strict|inclusive
            --include-reference true|false --mhc none|6:START:END
            [--regions REGIONS.tsv] [--predefined UIDS.tsv] --add-independent true|false
            --ties error|position --unmatched-reference error|exclude --empty error|retain
            --out NEW_DIRECTORY
        Required GWAS headers: chr,pos,non_effect_allele,effect_allele,rsID,p.
        Reference: chr,pos,rsID,A1,A2,MAF. LD: chr,pos1,pos2,r2 (undirected; omit self).
        Regions: chr,start,end (inclusive); predefined: uid (chr:pos:sorted-alleles).
        All inputs plain UTF-8 TSV, numeric chromosomes, one-based positive coordinates.
        Unlisted LD pairs must be strictly below the explicit floor; zero requires all pairs.
        One reference variant per coordinate is required by the source's positional LD model.
        Native complete interval components fix observed original backward-merge defects.
        Strict merge matches source code distance; inclusive is an explicit alternate boundary.
        GWAS/lead P tests are strict; MAF/LD inclusive. Native full-precision parsing may differ
          from old pandas at strict boundaries. Original decimal P and separate legacy-new
          versus complete-block counts remain in outputs. Read every WARNINGS.md and audit.
        Predefined seeds bypass their own P cutoff and first-stage independence check;
          they still require reference/MAF/MHC/region eligibility. MHC filters variants,
          not every base between outer locus bounds. Position ties can differ from old quicksort.
        --locus-options-file ASCII_PATH.json accepts string-valued options for Unicode paths;
          direct options override that carrier and are recorded. Analysis never installs.
        Optional limits: --max-bytes 268435456 --max-gwas 1000000 --max-reference 2000000
          --max-pairs 2000000 --max-work 100000000. Exit 0 completed with warnings;2 failed.
        """;}
}
