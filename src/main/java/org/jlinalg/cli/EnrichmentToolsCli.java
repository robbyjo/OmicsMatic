/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.enrichment.GseaStatistics;
import static org.jlinalg.cli.FollowupSupport.*;

/** Native running scores and explicit original enrichment engine workflows. */
final class EnrichmentToolsCli {
    private EnrichmentToolsCli() { }
    private static final Set<String> COMMON=Set.of("method","memberships","set-format","term-names","annotation-id","duplicate-membership","unmapped-members","min-size","max-size","empty","max-bytes","max-genes","max-memberships","max-terms","out");
    private static final Set<String> RANK=Set.of("ranking","ranking-format","ties","exponent","tail","zero-weight");
    private static final Set<String> EXTERNAL=Set.of("engine","rscript","r-library","dose-library","seed","timeout","p-adjust","p-threshold","q-threshold","undefined");
    private static final Set<String> SAMPLING=Set.of("algorithm","permutations","simple-permutations","sample-size","eps");
    private static final Set<String> ORA=Set.of("selected","universe","universe-policy","query-unmapped");
    record Table(List<String> header,List<String[]> rows) {
        int column(String name)throws IOException {int i=header.indexOf(name);if(i<0)throw new IOException("Original table lacks column: "+name);return i;}
    }
    private record Ranking(List<String> genes,double[] values,int tied) { }
    private record Prepared(LinkedHashMap<String,List<String>> sets,Map<String,String> names,
            Ranking ranking,List<String> universe,List<String> query,Map<String,String> disposition,
            List<String[]> membershipAudit,int expectedQuerySize) { }
    static int run(String[] arguments,PrintStream out,PrintStream err) {
        if(Arrays.asList(arguments).contains("--help")){out.println(help());return 0;}
        try {
            var allowed=new HashSet<>(COMMON);for(var set:List.of(RANK,EXTERNAL,SAMPLING,ORA,Set.of("score-convention","max-score-cells")))allowed.addAll(set);
            var o=options(arguments,allowed);String method=choice(o,"method",Set.of("score","ora","gsea"));
            var applicable=new HashSet<>(COMMON);
            if(!method.equals("ora"))applicable.addAll(RANK);else applicable.addAll(ORA);
            if(method.equals("score"))applicable.addAll(Set.of("score-convention","max-score-cells"));else applicable.addAll(EXTERNAL);
            if(method.equals("gsea"))applicable.addAll(SAMPLING);
            for(String key:o.keySet())if(!applicable.contains(key))throw new IllegalArgumentException("--"+key+" does not apply to "+method);
            for(String key:List.of("memberships","annotation-id","out","min-size","max-size","duplicate-membership","unmapped-members","empty"))required(o,key);
            choice(o,"duplicate-membership",Set.of("error","collapse"));choice(o,"unmapped-members",Set.of("error","drop"));choice(o,"empty",Set.of("error","retain"));
            o.putIfAbsent("set-format","tsv");choice(o,"set-format",Set.of("tsv","gmt"));
            if(o.get("set-format").equals("gmt")&&o.containsKey("term-names"))throw new IllegalArgumentException("GMT already supplies descriptions; --term-names applies only to TSV memberships");
            int min=integer(o,"min-size",10,1,1_000_000),max=integer(o,"max-size",500,min,1_000_000);
            int maxBytes=integer(o,"max-bytes",268_435_456,1,1_073_741_824),maxGenes=integer(o,"max-genes",1_000_000,2,2_000_000),maxLinks=integer(o,"max-memberships",5_000_000,1,20_000_000),maxTerms=integer(o,"max-terms",100_000,1,1_000_000);
            if(!method.equals("ora")) {
                for(String key:List.of("ranking","ties","exponent","tail","zero-weight"))required(o,key);
                o.putIfAbsent("ranking-format","tsv");choice(o,"ranking-format",Set.of("tsv","rnk"));choice(o,"ties",Set.of("error","input","gene-id"));choice(o,"tail",Set.of("std","pos","neg"));choice(o,"zero-weight",Set.of("error","original"));
                if(number(o,"exponent",1)<0)throw new IllegalArgumentException("--exponent must be nonnegative");
            }
            if(method.equals("score")) {
                choice(o,"score-convention",Set.of("fgsea","enrichit-running","enrichit-multilevel"));
                if(o.containsKey("max-score-cells")&&!o.get("score-convention").equals("enrichit-running"))throw new IllegalArgumentException("--max-score-cells applies only to dense enrichit-running scores");
                if(o.get("score-convention").equals("enrichit-running")&&!o.get("tail").equals("std"))throw new IllegalArgumentException("enrichit-running reproduces the standard two-sided helper only");
            } else {
                choice(o,"engine",Set.of("clusterprofiler","fgsea","dose"));
                for(String key:List.of("seed","p-adjust","p-threshold","undefined"))required(o,key);
                integer(o,"seed",0,0,Integer.MAX_VALUE);choice(o,"p-adjust",Set.of("BH","BY","holm","bonferroni","none"));choice(o,"undefined",Set.of("error","retain"));probability(o,"p-threshold",.05);
                if(o.containsKey("q-threshold")){probability(o,"q-threshold",.2);if(o.get("engine").equals("fgsea"))throw new IllegalArgumentException("fgsea provides BH-adjusted P-values,not Storey q-values; --q-threshold is unavailable for this engine");}
                if(o.containsKey("dose-library")&&!o.get("engine").equals("dose"))throw new IllegalArgumentException("--dose-library applies only to the explicit legacy DOSE engine");
                integer(o,"timeout",3600,1,86400);
                if(method.equals("ora")) {
                    if(o.get("engine").equals("fgsea"))throw new IllegalArgumentException("ORA requires clusterprofiler or dose");
                    for(String key:List.of("selected","universe","universe-policy","query-unmapped"))required(o,key);
                    choice(o,"universe-policy",Set.of("supplied","annotated"));choice(o,"query-unmapped",Set.of("error","drop","original"));
                } else validateSampling(o);
            }
            int[] code={0};externalTransaction(o,"enrichment-tools-"+method,(dir,manifest)->{
                var session=new LinkedHashMap<String,Object>();var sources=new LinkedHashMap<Path,String>();var warnings=new ArrayList<String>();
                session.put("choices",o);session.put("annotation_id",text(o.get("annotation-id"),"Annotation identity"));session.put("scope","Explicit supplied gene/term identities and original serial engines. Native scores alone provide no permutation inference. No online annotation or AI interface is requested.");
                try {
                    EnrichmentRuntime runtime=method.equals("score")?null:new EnrichmentRuntime(o,dir,session);
                    Prepared data=prepare(o,dir,sources,warnings,maxBytes,maxGenes,maxLinks,maxTerms,min,max);
                    table(dir.resolve("membership-audit.tsv"),List.of("term","gene","status"),data.membershipAudit());
                    var audits=new ArrayList<String[]>();for(var entry:data.disposition().entrySet())audits.add(new String[]{entry.getKey(),Integer.toString(data.sets().get(entry.getKey()).size()),entry.getValue()});
                    table(dir.resolve("term-audit.tsv"),List.of("term","effective_size","disposition"),audits);
                    long eligible=data.disposition().values().stream().filter("eligible"::equals).count();
                    if(eligible==0&&o.get("empty").equals("error"))throw new IOException("No eligible terms remain under the declared universe/size rules; inspect term-audit.tsv and revise source inputs or explicitly retain an empty analysis");
                    if(eligible==0&&!method.equals("score")){EnrichmentResultAudit.empty(dir,data.disposition(),warnings);session.put("upstream_not_run","no eligible hypotheses under the declared size/universe protocol");}
                    else if(method.equals("score"))score(o,dir,data,warnings);
                    else {
                        var request=new LinkedHashMap<String,Object>();request.put("schema","jlinalg-enrichment-request-v1");request.put("engine",o.get("engine"));request.put("method",method);request.put("options",o);
                        if(runtime.doseLibrary()!=null)request.put("dose_library",runtime.doseLibrary());
                        Path script=dir.resolve("enrichment-engines.R");try(var input=EnrichmentToolsCli.class.getResourceAsStream("/org/jlinalg/omics/enrichment_engines.R")){if(input==null)throw new IOException("Missing packaged enrichment engine interface");Files.copy(input,script);}
                        session.put("interface_sha256",hash(script));Path requestPath=dir.resolve("request.json");Files.writeString(requestPath,RegenieRuntime.json(request)+"\n");
                        runtime.run(script,requestPath,dir,integer(o,"timeout",3600,1,86400));
                        audit(o,dir,data,warnings,maxBytes,maxLinks);runtime.verify();
                    }
                    for(var e:sources.entrySet())if(!e.getValue().equals(hash(e.getKey())))throw new IOException("Input changed during analysis: "+e.getKey());
                    manifest.put("analysis_status",eligible==0?"completed-no-tested-terms":warnings.isEmpty()?"completed":"completed-with-warnings");
                } catch(Exception failure) {
                    code[0]=failure instanceof EnrichmentRuntime.Unavailable?3:2;manifest.put("analysis_status",code[0]==3?"skipped-dependency-unavailable":"failed-analysis");manifest.put("error",String.valueOf(failure.getMessage()));
                    warnings.add((code[0]==3?"SKIPPED DEPENDENCY: ":"ANALYSIS FAILED: ")+failure.getMessage());
                    if(Files.exists(dir.resolve("results.tsv"))||Files.exists(dir.resolve("upstream"))) {
                        Path partial=dir.resolve("partial");Files.createDirectories(partial);
                        for(String name:List.of("results.tsv","upstream"))if(Files.exists(dir.resolve(name)))Files.move(dir.resolve(name),partial.resolve(name));
                    }
                } finally {
                    session.put("source_sha256",sources);session.put("warnings",warnings);Files.writeString(dir.resolve("session.json"),RegenieRuntime.json(session)+"\n");
                    if(!warnings.isEmpty())Files.writeString(dir.resolve("WARNINGS.md"),"# ENRICHMENT "+(code[0]==3?"ANALYSIS SKIPPED":code[0]==2?"ANALYSIS FAILED":"WARNINGS REQUIRE REVIEW")+"\n\n"+String.join("\n\n",warnings)+"\n\nRequired action: "+(code[0]==3?EnrichmentRuntime.setup():"Inspect declared gene IDs,annotation/universe/selection,engine/tie/zero-weight and tested-family choices. Preserve original outputs and partial diagnostics. Undefined statistics and Storey q-values must not be replaced by adjusted P-values or zeros. Correct the named issue and rerun into a new directory.")+"\n");
                    for(String warning:warnings)err.println("WARNING: "+warning);
                }
            });return code[0];
        } catch(Exception failure){err.println("ERROR: "+failure.getMessage());return 2;}
    }
    private static void validateSampling(Map<String,String> o) {
        String engine=o.get("engine"),algorithm=choice(o,"algorithm",switch(engine){case "clusterprofiler"->Set.of("multilevel","permute","sample");case "dose"->Set.of("multilevel","simple","dose");default->Set.of("multilevel","simple");});
        boolean multilevel=algorithm.equals("multilevel");
        if(multilevel) {
            for(String key:List.of("eps","sample-size","simple-permutations"))required(o,key);
            probability(o,"eps",0);int sample=integer(o,"sample-size",101,3,100_001);if(sample%2==0)throw new IllegalArgumentException("--sample-size must be odd; no upstream rounding is hidden");integer(o,"simple-permutations",1000,1,10_000_000);
            if(o.containsKey("permutations"))throw new IllegalArgumentException("Multilevel uses --simple-permutations and --sample-size,not --permutations");
        } else {
            required(o,"permutations");integer(o,"permutations",1000,1,10_000_000);
            for(String key:List.of("eps","sample-size","simple-permutations"))if(o.containsKey(key))throw new IllegalArgumentException("--"+key+" applies only to multilevel inference");
            if(!o.get("tail").equals("std")&&(engine.equals("clusterprofiler")||algorithm.equals("dose")))throw new IllegalArgumentException("This original permutation engine supports the standard two-sided score only");
        }
    }
    private static Prepared prepare(Map<String,String> o,Path dir,Map<Path,String> sources,List<String> warnings,int bytes,int maxGenes,int maxLinks,int maxTerms,int minimum,int maximum)throws Exception {
        var sets=new LinkedHashMap<String,List<String>>();var names=new LinkedHashMap<String,String>();var pairs=new HashSet<String>();int duplicate=0;
        Path memberships=source(o.get("memberships"),sources,bytes);
        if(o.get("set-format").equals("gmt")) {
            try(var reader=Files.newBufferedReader(memberships,StandardCharsets.UTF_8)){String line;int links=0;while((line=reader.readLine())!=null){String[] fields=line.split("\t",-1);if(fields.length<3)throw new IOException("GMT needs term,description and at least one gene per row");String term=text(fields[0],"Term ID");if(sets.containsKey(term))throw new IOException("Duplicate GMT term: "+term);sets.put(term,new ArrayList<>());names.put(term,text(fields[1],"Term description"));for(int i=2;i<fields.length;i++){String gene=gene(fields[i]);if(!pairs.add(term+"\u0000"+gene)){duplicate++;continue;}sets.get(term).add(gene);if(++links>maxLinks)throw new IOException("Membership count exceeds --max-memberships");}if(sets.size()>maxTerms)throw new IOException("Term count exceeds --max-terms");}}
        } else {
            Table input=read(memberships,bytes,maxLinks);header(input,"term","gene");for(String[] row:input.rows()){String term=text(row[0],"Term ID"),gene=gene(row[1]);if(!pairs.add(term+"\u0000"+gene)){duplicate++;continue;}sets.computeIfAbsent(term,k->new ArrayList<>()).add(gene);if(sets.size()>maxTerms)throw new IOException("Term count exceeds --max-terms");}
            if(o.containsKey("term-names")){Table descriptions=read(source(o.get("term-names"),sources,bytes),bytes,maxTerms);header(descriptions,"term","name");for(String[] row:descriptions.rows()){String term=text(row[0],"Term ID");if(names.putIfAbsent(term,text(row[1],"Term description"))!=null)throw new IOException("Duplicate term description: "+term);if(!sets.containsKey(term))throw new IOException("Description refers to absent term: "+term);}}
        }
        if(duplicate>0){if(o.get("duplicate-membership").equals("error"))throw new IOException("Duplicate term/gene membership requires explicit --duplicate-membership collapse");warnings.add("DUPLICATE MEMBERSHIPS: collapsed "+duplicate+" repeated term/gene pairs; original annotation is preserved.");}
        for(String term:sets.keySet())names.putIfAbsent(term,term);
        Set<String> annotated=new HashSet<>();sets.values().forEach(annotated::addAll);
        Ranking ranking=null;List<String> universe,query=List.of();int expectedQuery=0;
        if(o.get("method").equals("ora")) {
            List<String> supplied=genes(read(source(o.get("universe"),sources,bytes),bytes,maxGenes)),selected=genes(read(source(o.get("selected"),sources,bytes),bytes,maxGenes));
            Set<String> raw=new HashSet<>(supplied);if(!raw.containsAll(selected))throw new IOException("Selected genes must be a subset of the explicitly supplied universe");
            universe=supplied.stream().filter(g->o.get("universe-policy").equals("supplied")||annotated.contains(g)).toList();
            Set<String> effective=new HashSet<>(universe);List<String> missing=selected.stream().filter(g->!effective.contains(g)||!annotated.contains(g)).toList();
            if(!missing.isEmpty()&&o.get("query-unmapped").equals("error"))throw new IOException("Selected genes lack effective annotation/universe membership: "+missing.subList(0,Math.min(10,missing.size()))+"; choose a declared drop/original policy after review");
            query=o.get("query-unmapped").equals("drop")?selected.stream().filter(g->effective.contains(g)&&annotated.contains(g)).toList():selected;
            if(!missing.isEmpty())warnings.add("QUERY MAPPING: "+missing.size()+" selected genes lack effective annotation; policy="+o.get("query-unmapped")+". Legacy DOSE counts only annotated query genes; current clusterProfiler counts query genes in its effective universe. Read query-audit.tsv.");
            var rows=new ArrayList<String[]>();Set<String> retained=new HashSet<>(query);for(String g:selected)rows.add(new String[]{g,retained.contains(g)?annotated.contains(g)&&effective.contains(g)?"retained-annotated":"retained-original-unannotated":"explicitly-dropped"});table(dir.resolve("query-audit.tsv"),List.of("gene","status"),rows);
            expectedQuery=(int)query.stream().filter(effective::contains).filter(g->!o.get("engine").equals("dose")||annotated.contains(g)).count();
            table(dir.resolve("selected.tsv"),List.of("gene"),query.stream().map(g->new String[]{g}).toList());table(dir.resolve("universe.tsv"),List.of("gene"),universe.stream().map(g->new String[]{g}).toList());
        } else {
            Table table=read(source(o.get("ranking"),sources,bytes),bytes,maxGenes,o.get("ranking-format").equals("rnk"));header(table,"gene","statistic");List<String[]> rows=table.rows();
            var values=new LinkedHashMap<String,Double>();for(String[] row:rows){if(row.length!=2)throw new IOException("Ranking requires exactly gene and statistic columns");String g=gene(row[0]);double value=Double.parseDouble(row[1]);if(!Double.isFinite(value)||values.putIfAbsent(g,value)!=null)throw new IOException("Nonfinite/duplicate ranked gene: "+g);}
            if(values.size()<2||values.size()>maxGenes)throw new IOException("Ranking needs2..max-genes unique features");
            var order=new ArrayList<>(values.keySet());order.sort((a,b)->values.get(a).doubleValue()==values.get(b).doubleValue()?o.get("ties").equals("gene-id")?a.compareTo(b):0:Double.compare(values.get(b),values.get(a)));
            int tied=0;for(int i=0;i<order.size();){int end=i+1;while(end<order.size()&&values.get(order.get(end)).doubleValue()==values.get(order.get(i)).doubleValue())end++;if(end-i>1)tied+=end-i;i=end;}
            if(tied>0&&o.get("ties").equals("error"))throw new IOException("Tied ranking statistics require an explicit --ties input or gene-id protocol");
            if(tied>0)warnings.add("RANK TIES: "+tied+" genes use the declared "+o.get("ties")+" tie order,including zero ties. Leading edges and inference can change with this order.");
            ranking=new Ranking(List.copyOf(order),order.stream().mapToDouble(values::get).toArray(),tied);universe=ranking.genes();var staged=new ArrayList<String[]>();for(int i=0;i<order.size();i++)staged.add(new String[]{order.get(i),Double.toString(ranking.values()[i])});table(dir.resolve("ranking.tsv"),List.of("gene","statistic"),staged);
        }
        if(universe.isEmpty())throw new IOException("Effective background/ranking is empty; revise reference,annotation or universe policy");
        Set<String> effective=new HashSet<>(universe);var membershipAudit=new ArrayList<String[]>();int omitted=0;var disposition=new LinkedHashMap<String,String>();
        for(var entry:sets.entrySet()) {
            var kept=new ArrayList<String>();for(String g:entry.getValue()){boolean present=effective.contains(g);membershipAudit.add(new String[]{entry.getKey(),g,present?"retained":"outside-effective-universe"});if(present)kept.add(g);else omitted++;}
            entry.setValue(kept);int size=kept.size();String state=size<minimum?"below-minimum":size>maximum?"above-maximum":!o.get("method").equals("ora")&&size==universe.size()?"complete-ranking-no-complement":"eligible";
            if(state.equals("eligible")&&o.get("method").equals("ora")&&Collections.disjoint(kept,query))state="zero-overlap-excluded-by-original";disposition.put(entry.getKey(),state);
        }
        if(omitted>0&&o.get("unmapped-members").equals("error"))throw new IOException(omitted+" annotation memberships are outside the effective universe; inspect matching IDs or explicitly choose --unmapped-members drop");
        if(omitted>0)warnings.add("ANNOTATION MEMBERSHIP: explicitly omitted "+omitted+" out-of-universe term/gene links; every original link/status is in membership-audit.tsv.");
        var mapping=new ArrayList<String[]>();for(var entry:sets.entrySet())if(!disposition.get(entry.getKey()).equals("complete-ranking-no-complement"))for(String g:entry.getValue())mapping.add(new String[]{entry.getKey(),g});
        table(dir.resolve("memberships.tsv"),List.of("term","gene"),mapping);table(dir.resolve("term-names.tsv"),List.of("term","name"),sets.keySet().stream().map(t->new String[]{t,names.get(t)}).toList());
        return new Prepared(sets,names,ranking,List.copyOf(universe),query,disposition,membershipAudit,expectedQuery);
    }
    private static void score(Map<String,String> o,Path dir,Prepared data,List<String> warnings)throws Exception {
        double[] weights=o.get("score-convention").equals("enrichit-running")?rawWeights(data.ranking().values(),number(o,"exponent",1)):GseaStatistics.integerWeights(data.ranking().values(),number(o,"exponent",1));
        if(o.get("score-convention").equals("enrichit-running")){long limit=Long.parseLong(o.getOrDefault("max-score-cells","100000000"));if(limit<1||limit>2_000_000_000L)throw new IOException("--max-score-cells requires1..2000000000");long work=(long)weights.length*data.disposition().values().stream().filter("eligible"::equals).count();if(work>limit)throw new IOException("Complete dense raw-score workload exceeds --max-score-cells; inspect the declared annotation/size protocol or raise the bound on a suitable host");}
        var prepared=GseaStatistics.prepareWeights(weights);var positions=new HashMap<String,Integer>();for(int i=0;i<data.ranking().genes().size();i++)positions.put(data.ranking().genes().get(i),i);
        var rows=new ArrayList<String[]>();var edges=new ArrayList<String[]>();
        for(var entry:data.sets().entrySet())if(data.disposition().get(entry.getKey()).equals("eligible")) {
            int[] selected=entry.getValue().stream().mapToInt(positions::get).sorted().toArray();var walk=o.get("score-convention").equals("enrichit-running")?prepared.rawWalk(selected):prepared.walk(selected,tail(o),o.get("score-convention").equals("fgsea")?GseaStatistics.ExtremumTie.ZERO:GseaStatistics.ExtremumTie.POSITIVE);
            checkZero(o,entry.getKey(),walk,warnings);rows.add(new String[]{entry.getKey(),data.names().get(entry.getKey()),Integer.toString(selected.length),Double.toString(walk.score()),Double.toString(walk.maximum()),Double.toString(walk.minimum()),Integer.toString(walk.extremum()+1),walk.zeroHitWeight()?"zero-hit-weight-original-rule":"scored"});
            for(int rank:walk.leadingEdge())edges.add(new String[]{entry.getKey(),data.ranking().genes().get(rank),Integer.toString(rank+1)});
        }
        table(dir.resolve("results.tsv"),List.of("term","description","set_size","enrichment_score","positive_extremum","negative_extremum","extremum_rank","status"),rows);table(dir.resolve("leading-edges.tsv"),List.of("term","gene","rank"),edges);
        warnings.add("NATIVE RUNNING SCORE ONLY: no normalized enrichment score,P-value,permutation null or differential-expression model is estimated. Score conventions are explicitly selected; near-balanced signs and zero-hit rules differ between original programs.");
    }
    private static void audit(Map<String,String> o,Path dir,Prepared data,List<String> warnings,int bytes,int maxLinks)throws Exception {
        // Implemented below as an independent identity,arithmetic and family audit.
        EnrichmentResultAudit.validate(o,dir,data.sets(),data.names(),data.ranking()==null?null:data.ranking().genes(),data.ranking()==null?null:data.ranking().values(),data.universe(),data.query(),data.disposition(),data.expectedQuerySize(),warnings,bytes,maxLinks);
    }
    static GseaStatistics.Tail tail(Map<String,String> o){return switch(o.get("tail")){case "pos"->GseaStatistics.Tail.POSITIVE;case "neg"->GseaStatistics.Tail.NEGATIVE;default->GseaStatistics.Tail.STANDARD;};}
    static void checkZero(Map<String,String> o,String term,GseaStatistics.Walk value,List<String> warnings)throws IOException {
        if(value.zeroHitWeight()){if(o.get("zero-weight").equals("error"))throw new IOException("Zero selected power-weight mass for term "+term+"; revise the ranking/model or explicitly review --zero-weight original");warnings.add("ZERO HIT WEIGHT: term "+term+" follows the selected original rule. fgsea uses uniform hit weights; enrichit running scores add no hit mass. This is not observed evidence of regulation.");}
    }
    static double[] rawWeights(double[] stats,double exponent)throws IOException {double[] result=new double[stats.length];double sum=0;for(int i=0;i<stats.length;i++){result[i]=Math.pow(Math.abs(stats[i]),exponent);sum+=result[i];}if(!Double.isFinite(sum)||!(sum>0))throw new IOException("Rank power weights have zero/nonfinite total; revise the declared ranking/exponent");return result;}
    static int[] positions(List<String> universe,List<String> members){var index=new HashMap<String,Integer>();for(int i=0;i<universe.size();i++)index.put(universe.get(i),i);return members.stream().mapToInt(index::get).sorted().toArray();}
    static Table read(Path path,int bytes,int maxRows)throws IOException {
        return read(path,bytes,maxRows,false);
    }
    private static Table read(Path path,int bytes,int maxRows,boolean headerless)throws IOException {
        if(!Files.isRegularFile(path)||Files.size(path)>bytes)throw new IOException("Missing/oversized TSV: "+path);
        try(var reader=Files.newBufferedReader(path,StandardCharsets.UTF_8)){String line=reader.readLine();if(line==null)throw new IOException("Empty TSV: "+path);List<String> header=headerless?List.of("gene","statistic"):List.of(line.split("\t",-1));if(new HashSet<>(header).size()!=header.size())throw new IOException("Duplicate TSV header: "+path);var rows=new ArrayList<String[]>();if(headerless){String[] row=line.split("\t",-1);if(row.length!=2)throw new IOException("RNK requires exactly gene and statistic columns");rows.add(row);}while((line=reader.readLine())!=null){String[] row=line.split("\t",-1);if(row.length!=header.size())throw new IOException("TSV width differs from header: "+path);rows.add(row);if(rows.size()>maxRows)throw new IOException("TSV rows exceed resource bound: "+path);}return new Table(header,rows);}
    }
    private static void header(Table table,String... names)throws IOException {if(!table.header().equals(List.of(names)))throw new IOException("Expected exactly these TSV columns: "+String.join(",",names));}
    private static List<String> genes(Table table)throws IOException {header(table,"gene");var out=new ArrayList<String>();var seen=new HashSet<String>();for(String[] row:table.rows()){String gene=gene(row[0]);if(!seen.add(gene))throw new IOException("Duplicate gene ID: "+gene);out.add(gene);}return out;}
    private static Path source(String name,Map<Path,String> sources,int bytes)throws IOException {Path path=Path.of(name).toAbsolutePath().normalize();if(!Files.isRegularFile(path)||Files.size(path)>bytes)throw new IOException("Missing/oversized input: "+path);String digest=hash(path),old=sources.putIfAbsent(path,digest);if(old!=null&&!old.equals(digest))throw new IOException("Repeated source changed between uses: "+path);return path;}
    static String text(String value,String label)throws IOException {if(value==null||value.isBlank()||!value.equals(value.strip())||value.chars().anyMatch(c->c<32))throw new IOException(label+" must be nonblank,literal and free of control/edge whitespace");return value;}
    private static String gene(String value)throws IOException {text(value,"Gene ID");if(value.contains("/"))throw new IOException("Gene IDs containing '/' cannot be represented unambiguously in original core-enrichment fields; supply a bijective explicit alias map before analysis");return value;}
    private static String choice(Map<String,String> o,String name,Set<String> values){String value=required(o,name);if(!values.contains(value))throw new IllegalArgumentException("--"+name+" requires one of "+values);return value;}
    private static double probability(Map<String,String> o,String name,double fallback){double value=number(o,name,fallback);if(value<0||value>1)throw new IllegalArgumentException("--"+name+" must be in [0,1]");return value;}
    private static String help(){return """
        enrichment-tools --method score|ora|gsea --memberships FILE --set-format tsv|gmt
          --annotation-id ID --min-size N --max-size N --duplicate-membership error|collapse
          --unmapped-members error|drop --empty error|retain --out NEW_DIRECTORY
        score/gsea: --ranking FILE --ranking-format tsv|rnk --ties error|input|gene-id
          --exponent NONNEGATIVE --tail std|pos|neg --zero-weight error|original
        score: --score-convention fgsea|enrichit-running|enrichit-multilevel
        ora/gsea: --engine clusterprofiler|dose|fgsea --rscript EXE --r-library LIBRARY
          --seed N --p-adjust BH|BY|holm|bonferroni|none --p-threshold P
          --undefined error|retain [--q-threshold Q] [--dose-library LEGACY_LIBRARY]
        ora: --selected FILE --universe FILE --universe-policy supplied|annotated
          --query-unmapped error|drop|original; fgsea does not perform ORA.
        gsea: --algorithm multilevel|simple|dose|permute|sample (engine-specific)
          multilevel: --eps P --sample-size ODD --simple-permutations N
          other supported algorithms: --permutations N
        TSV schemas: memberships term,gene; term-names term,name; ranking gene,statistic;
          selected/universe gene. GMT contains term,description,genes; RNK is headerless.
        Native score requires no R. Original serial inference never installs dependencies.
        Missing/forbidden/changed dependencies skip3; analysis/input failures exit2 with
          original outputs retained under partial/. Algorithms/versions are not interchangeable.
        Dense raw score bound: --max-score-cells 100000000 (hard maximum2000000000).
        Optional bounds: --timeout 3600 --max-bytes 268435456 --max-genes 1000000
          --max-memberships 5000000 --max-terms 100000. Provide values as separate arguments.
        """;}
}
