/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.jlinalg.shrinkage.MashSharing;
import org.jlinalg.shrinkage.MashSignScores;
import org.jlinalg.shrinkage.MashCompositeScores;

/** Standalone public command for replay-admitted summaries of a complete native mash bundle. */
public final class MashSummaryCli {
    private MashSummaryCli() { }
    static final String TOOL="mash-summary",ALGORITHM="retained-native-posterior-sharing-marginal-products-v1";
    static final String ACTION="Required action: require exit0, top-level COMPLETED.json and successful --verify before dependent analysis. Read WARNINGS.md and native/WARNINGS.md. EMPTY_SELECTED_POPULATION and UNDEFINED_SELECTED_RATIO are NA sharing estimates, never zero or biological nonreplication. Marginal sign-score products are not joint probabilities, Pr(nonzero), or proof of equal effect directions. Unsupported formal tests are SKIP3: skip that requested analysis; use an explicitly supported mode or a separately validated method. FAIL2 means invalid input, integrity, numerical, capacity, cancellation or finalization failure: preserve inputs, partial outputs and diagnostics, correct the named cause or explicitly increase permitted limits/heap, then retry into NEW paths. No missing values, P values or method choices are inferred. SHA-256 establishes integrity, not authenticity; no biological accuracy or whole-paper reproduction is certified.";
    private static final int JSON=8_388_608,LINE=1_048_576;
    private record Settings(double threshold,MashSharing.Boundary boundary,double factor,MashSharing.Transform transform,boolean composite,double nominal,int minimum,double score,boolean proxy,MashSharing.Boundary scoreBoundary) { }
    private record Native(MashFiles.Pin manifest,int rows,int conditions) { }
    record Counts(int rows,int conditions,int groups,int selectedRows,int emptyPairs,int undefinedPairs,int underflowScores) { }
    private record ProxyCounts(int patterns,long missingMatrixCells,long roundedZeroScores,long underflowScores,long exactZeroScores,long numericalLossScores,long repeatedSelectorScoreCells,long overlappingScoreCells) { }
    private record Computed(Counts counts,Settings settings,ProxyCounts proxyCounts) { }
    record Verification(Path scratch,long numericLogsCompared) { }

    public static void main(String[] args){System.exit(run(args,System.out,System.err));}
    public static int run(String[] args,PrintStream out,PrintStream err){return run(args,out,err,()->{});}
    static int run(String[] args,PrintStream out,PrintStream err,Runnable afterCompletion){
        Path root=null;boolean owns=false;Runnable check=()->{if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException("Mash summary interrupted");};
        try {
            if(args.length==1&&args[0].equals("--help")){emit(out,help());return 0;}
            if(args.length==1&&args[0].equals("--default-limits")){emit(out,MashJson.json(MashSummaryLimits.defaults()));return 0;}
            var options=new LinkedHashMap<String,String>();
            for(int i=0;i<args.length;i+=2)if(i+1>=args.length||!Set.of("--native-bundle","--input","--out","--verify","--export-template","--limits").contains(args[i])||options.putIfAbsent(args[i],args[i+1])!=null)throw new IOException("Require unique complete mash summary value options; use --help");
            String raw=options.containsKey("--limits")?MashFiles.text(path(options.get("--limits")),262144):MashJson.json(MashSummaryLimits.defaults())+"\n";
            MashSummaryLimits limits=MashSummaryLimits.parse(SimpleJson.parse(raw));
            if(options.containsKey("--verify")){
                if(!Set.of("--verify","--limits").containsAll(options.keySet()))throw new IOException("Verification accepts only --verify and optional --limits");
                var v=verify(path(options.get("--verify")),"COMPLETED.json",limits,check);emit(out,"Mash summary verified by native-source and complete summary replay; retained scratch "+v.scratch()+"\n"+ACTION);return 0;
            }
            boolean export=options.containsKey("--export-template");
            Set<String> allowed=export?Set.of("--export-template","--out","--limits"):Set.of("--native-bundle","--input","--out","--limits");
            if(!allowed.containsAll(options.keySet())||!options.containsKey("--out")||!options.containsKey(export?"--export-template":"--native-bundle")||!export&&!options.containsKey("--input"))throw new IOException("Require --native-bundle, --input, --out; or --export-template, --out");
            Path source=path(options.get(export?"--export-template":"--native-bundle"));root=path(options.get("--out"));
            disjoint(root,source);if(options.containsKey("--limits"))disjoint(root,path(options.get("--limits")));scratchOutside(root,source);
            if(export){export(source,root,limits,check);emit(out,"Mash summary request template exported; no summary analysis admitted.\n"+ACTION);return 0;}
            Path request=path(options.get("--input"));disjoint(root,request);disjoint(source,request);scratchOutside(request);
            MashFiles.create(root);owns=true;bounded(root,"limits.json",MashJson.json(limits)+"\n",limits,check);bounded(root,"limits-request.json",raw,limits,check);
            copy(request,root.resolve("request"),limits.requestFiles(),limits,check);Settings settings=settings(root.resolve("request/settings.tsv"));requestGraph(root.resolve("request"),settings,limits,check);
            copy(source,root.resolve("native"),limits.nativeReplay(),limits,check);MashFiles.inventory(root,limits.files(),check);
            Native nativeResult=source(root.resolve("native"),limits,check);Computed computed=compute(root.resolve("native"),root.resolve("request"),root.resolve("results"),settings,nativeResult,limits,check);
            bounded(root,"session.json",MashJson.json(session(nativeResult,computed))+"\n",limits,check);bounded(root,"WARNINGS.md",completeWarnings(computed),limits,check);
            var data=MashFiles.inventory(root,limits.files(),check);
            bounded(root,"manifest.json",MashJson.json(Map.of("schema_version",1,"tool",TOOL,"algorithm",ALGORITHM,"files",new TreeMap<>(data.files()),"directories",new java.util.TreeSet<>(data.directories())))+"\n",limits,check);
            bounded(root,"COMPLETION-PROVISIONAL.json",MashJson.json(marker(MashFiles.pin(root.resolve("manifest.json"),JSON,check)))+"\n",limits,check);
            var marker=MashFiles.pin(root.resolve("COMPLETION-PROVISIONAL.json"),262144,check);var manifest=MashFiles.pin(root.resolve("manifest.json"),JSON,check);
            var verified=verify(root,"COMPLETION-PROVISIONAL.json",limits,check);Files.move(root.resolve("COMPLETION-PROVISIONAL.json"),root.resolve("COMPLETED.json"));afterCompletion.run();
            var current=MashFiles.inventory(root,limits.files(),check);var actual=new TreeMap<>(current.files());actual.remove("COMPLETED.json");actual.remove("manifest.json");
            if(!actual.equals(data.files())||!current.directories().equals(data.directories())||!marker.equals(current.files().get("COMPLETED.json"))||!manifest.equals(current.files().get("manifest.json")))throw new IOException("Summary evidence changed during finalization");
            emit(out,"Mash summary scoped result admitted: "+root+"; replay scratch "+verified.scratch()+"\n"+ACTION);
            if(computed.counts().emptyPairs()+computed.counts().undefinedPairs()+computed.counts().underflowScores()>0)emit(err,"Mash summary WARNING: empty pairs="+computed.counts().emptyPairs()+", undefined pairs="+computed.counts().undefinedPairs()+", underflow scores="+computed.counts().underflowScores()+". Preserve their explicit states; read WARNINGS.md.\n"+ACTION);
            if(computed.proxyCounts()!=null)emit(err,proxyWarnings(computed));return 0;
        }catch(Exception failure){
            int code=failure instanceof Unavailable?3:2;String label=code==3?"SKIP3 — ANALYSIS SKIPPED":"FAIL2 — NO RESULT ADMITTED";String detail=failure.toString();if(detail.length()>16384)detail=detail.substring(0,16384)+" [bounded diagnostic]";emit(err,label+": "+detail+"\n"+ACTION);
            if(owns)try {
                if(Files.exists(root.resolve("COMPLETED.json")))Files.move(root.resolve("COMPLETED.json"),root.resolve("REJECTED-COMPLETION.json"));
                MashFiles.write(root.resolve("FAILURE.json"),Map.of("tool",TOOL,"exit_code",code,"scoped_result_admitted",false,"error",detail,"required_action",ACTION));Files.writeString(root.resolve("WARNINGS.md"),"\n# "+label+"\n\n"+detail+"\n\n"+ACTION+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.APPEND);
            }catch(IOException diagnostic){emit(err,"Failure diagnostics incomplete: "+diagnostic+"\n"+ACTION);}return code;
        }
    }

    private static Map<String,Object> marker(MashFiles.Pin manifest){return Map.of("schema_version",1,"tool",TOOL,"algorithm",ALGORITHM,"manifest",manifest,"scoped_result_admitted",true,"native_source_replayed",true,"joint_probability_inferred",false,"whole_paper_reproduced",false,"biological_accuracy_certified",false);}
    private static Map<String,Object> session(Native source,Computed result){Settings s=result.settings();var value=new TreeMap<String,Object>(Map.of("schema_version",1,"tool",TOOL,"algorithm",ALGORITHM,"status","audited-provisional","scoped_result_admitted",false,"native_manifest",source.manifest(),"posterior_source","retained tables after complete native semantic verification","counts",result.counts(),"settings",Map.of("threshold",s.threshold(),"boundary",s.boundary().name(),"factor",s.factor(),"transform",s.transform().name(),"composite",s.composite(),"nominal",s.nominal(),"minimum",s.minimum(),"score",s.score())));if(s.proxy()){value.put("proxy_method",Map.of("method","dreamlet_independent_marginal_proxy_v1","assumption","independent_marginal_proxies","input_scale","retained_LFSR","missing_policy","reject","score_boundary",s.scoreBoundary().name(),"diagnostics",result.proxyCounts()));}return value;}
    private static String warnings(Computed r){return "# Verified native mash posterior summaries\n\nSignificance counts use the explicitly requested "+r.settings().boundary()+" boundary. Pairwise source sharing always selects LFSR strictly below threshold, uses strict ratio inequalities, and mirrors the upper triangle.\n\nThese summaries use retained posterior tables after full native replay. Native replay has its own documented numerical tolerances; it does not authenticate execution history. Read native/WARNINGS.md and every retained fit warning, including residual rollback.\n\nAll summary identities, discrete states, counts, fractions and rounded products replay exactly. Only finite log-product values use absolute1e-12 plus relative1e-12 tolerance; nonpositive log domain and exact-zero states remain enforced.\n\nMarginal sign products enabled: "+r.settings().composite()+". Nominal P values and group membership are explicit independent inputs. Products can be high for opposite estimated effect directions. Exact-zero log products are -Inf; underflow has a separate state and finite log score.\n\nEmpty pairwise cells: "+r.counts().emptyPairs()+"; undefined selected-ratio cells: "+r.counts().undefinedPairs()+"; underflow scores: "+r.counts().underflowScores()+". NA fractions are retained without dropping their denominator rows.\n\n"+ACTION+"\n";}
    private static Path path(String text){return Path.of(text).toAbsolutePath().normalize();}
    private static String completeWarnings(Computed r){return warnings(r)+(r.settings().proxy()?proxyWarnings(r):"");}
    private static String proxyWarnings(Computed r){return "\nWARNING — INDEPENDENT MARGINAL PROXY METHOD: "+r.proxyCounts()+"\nThis explicitly requested dreamlet-style proxy uses p=1-LFSR with ALL or AT_LEAST_ONE inclusion and all exclusion complements. It assumes independent marginal proxies; it does not integrate the joint posterior or establish biological absence, specificity, a dependence-robust bound or a calibrated P value. Native retained LFSR must be complete and finite; missing probabilities are rejected. Repeated selectors and include/exclude overlap retain literal source multiplication and are reported. Empty ALL/ANY inclusion has factor1/0. No regex expansion or implicit exclusion is performed.\nSource scores and exact threshold decisions are separate from stable-log diagnostics; rounded zeros, exact zeros, underflow and numerical loss must remain visible. Score boundary="+r.settings().scoreBoundary()+", threshold="+r.settings().score()+". Never replace the source score with a finite-log diagnostic silently.\nRequired action: inspect every independent-marginal-proxies.tsv row and the captured patterns/selectors. Supply actual paper groups/contrasts and unresolved fit settings. P024 >=0.05 and its downstream >=0.01 removal stage are distinct; P104's exact boundary at0.8 remains unestablished. Require exit0, COMPLETED.json and --verify before dependent analysis. Invalid input, integrity, capacity and cancellation fail without admission.\n";}
    private static void disjoint(Path a,Path b)throws IOException {if(a.startsWith(b)||b.startsWith(a))throw new IOException("Source, request, limits and output trees must be disjoint");}
    private static void scratchOutside(Path... protectedPaths)throws IOException {Path temp=Path.of(System.getProperty("java.io.tmpdir")).toRealPath();for(Path p:protectedPaths)if(temp.startsWith(p))throw new IOException("Java temporary directory must be outside source/request/output evidence");}
    private static final class Unavailable extends IOException {private static final long serialVersionUID=1;Unavailable(String detail){super(detail);}}
    private static void emit(PrintStream stream,String text){stream.writeBytes((text+"\n").getBytes(StandardCharsets.UTF_8));}

    private static long bytes(MashFiles.Inventory inventory){long sum=0;for(var file:inventory.files().values())sum=Math.addExact(sum,file.bytes());return sum;}
    private static void bounded(Path root,String name,String text,MashSummaryLimits limits,Runnable check)throws IOException {byte[] raw=text.getBytes(StandardCharsets.UTF_8);if(Math.addExact(bytes(MashFiles.inventory(root,limits.files(),check)),raw.length)>limits.bundleBytes())throw new IOException("Complete summary evidence byte capacity exceeded before write");Files.write(root.resolve(name),raw,StandardOpenOption.CREATE_NEW);}
    private static void copy(Path input,Path target,MashLimits limits,MashSummaryLimits parent,Runnable check)throws IOException {
        var before=MashFiles.inventory(input,limits,check);if(Math.addExact(bytes(before),bytes(MashFiles.inventory(target.getParent(),parent.files(),check)))>parent.bundleBytes())throw new IOException("Complete capture exceeds current output byte capacity before copying");MashFiles.create(target);
        for(String directory:new java.util.TreeSet<>(before.directories()))if(!directory.isEmpty())Files.createDirectories(target.resolve(directory));
        for(String name:new java.util.TreeSet<>(before.files().keySet())){check.run();long copied=0;byte[] buffer=new byte[65536];try(var source=Files.newInputStream(input.resolve(name));var output=Files.newOutputStream(target.resolve(name),StandardOpenOption.CREATE_NEW)){for(int n;(n=source.read(buffer))!=-1;){check.run();copied=Math.addExact(copied,n);if(copied>before.files().get(name).bytes())throw new IOException("Captured input grew during bounded copy");output.write(buffer,0,n);}}}
        if(!before.equals(MashFiles.inventory(input,limits,check))||!before.equals(MashFiles.inventory(target,limits,check)))throw new IOException("Complete capture changed while copying");
    }
    private static Native source(Path root,MashSummaryLimits limits,Runnable check)throws IOException {
        var manifest=MashFiles.json(root.resolve("manifest.json"),JSON);if(!(manifest instanceof Map<?,?> map))throw new IOException("Missing native manifest object");MashLimits declared=MashLimits.parse(map.get("limits"));
        MashSummaryLimits.within(SimpleJson.parse(MashJson.json(declared)),SimpleJson.parse(MashJson.json(limits.nativeReplay())));MashFiles.inventory(root,limits.nativeReplay(),check);
        var verified=MashBundle.verify(root,"COMPLETED.json",check);Object session=MashFiles.json(root.resolve("session.json"),262144);
        if(!(session instanceof Map<?,?> s)||!(s.get("result") instanceof Map<?,?> result))throw new IOException("Native result metadata unavailable");
        int rows=MashLimits.i(result,"targetRows"),conditions=MashLimits.i(result,"conditions");if(rows>limits.summary().rows()||conditions>limits.summary().conditions())throw new IOException("Complete native target dimensions exceed current summary capacities");return new Native(verified.manifest(),rows,conditions);
    }
    private static Settings settings(Path file)throws IOException {
        String text=MashFiles.text(file,262144);var lines=text.lines().toList();if(lines.isEmpty()||!lines.get(0).equals("key\tvalue"))throw new IOException("Require complete key/value summary settings");var p=new LinkedHashMap<String,String>();
        for(String line:lines.subList(1,lines.size())){String[] row=line.split("\t",-1);if(row.length!=2||p.putIfAbsent(row[0],row[1])!=null)throw new IOException("Duplicate/ragged summary settings");}
        if(!"1".equals(p.get("schema_version")))throw new IOException("Require summary settings schema_version=1");String mode=p.get("composite_mode");
        if(mode!=null&&Set.of("formal_composite","joint_nonzero","posterior_samples").contains(mode))throw new Unavailable("Requested formal joint/sampled method is not implemented by these summaries. No marginal-product substitution was selected.");
        if(!"off".equals(mode)&&!"marginal_sign_product".equals(mode)&&!"independent_marginal_proxy".equals(mode))throw new IOException("Unknown explicit composite_mode");boolean composite=mode.equals("marginal_sign_product"),proxy=mode.equals("independent_marginal_proxy");
        var keys=new HashSet<>(Set.of("schema_version","lfsr_threshold","significance_boundary","sharing_factor","sharing_transform","composite_mode"));if(composite)keys.addAll(Set.of("nominal_p_threshold","minimum_nominal","score_threshold"));if(proxy)keys.addAll(Set.of("proxy_assumption","score_threshold","score_boundary"));if(!p.keySet().equals(keys))throw new IOException("Incomplete/unknown/unconsumed summary settings");
        if(proxy&&!"independent_marginal_proxies".equals(p.get("proxy_assumption")))throw new IOException("Declare proxy_assumption=independent_marginal_proxies explicitly");
        MashSharing.Boundary scoreBoundary=proxy?switch(p.get("score_boundary")){case "strict"->MashSharing.Boundary.STRICT;case "inclusive"->MashSharing.Boundary.INCLUSIVE;default->throw new IOException("Explicit proxy score boundary required");}:MashSharing.Boundary.STRICT;
        MashSharing.Boundary boundary=switch(p.get("significance_boundary")){case "strict"->MashSharing.Boundary.STRICT;case "inclusive"->MashSharing.Boundary.INCLUSIVE;default->throw new IOException("Explicit significance boundary required");};
        MashSharing.Transform transform=switch(p.get("sharing_transform")){case "identity"->MashSharing.Transform.IDENTITY;case "absolute"->MashSharing.Transform.ABSOLUTE;default->throw new IOException("Explicit sharing transform required");};
        int minimum=0;if(composite){String value=p.get("minimum_nominal");if(!value.matches("0|[1-9][0-9]{0,8}"))throw new IOException("Require a canonical nonnegative minimum_nominal integer");minimum=Integer.parseInt(value);}
        return new Settings(probability(p.get("lfsr_threshold")),boundary,probability(p.get("sharing_factor")),transform,composite,composite?probability(p.get("nominal_p_threshold")):0,minimum,composite||proxy?probability(p.get("score_threshold")):0,proxy,scoreBoundary);
    }
    private static double probability(String text)throws IOException {double value=number(text);if(value<0||value>1)throw new IOException("Require a probability in [0,1]");return value;}
    private static double number(String text)throws IOException {if(text==null||!text.matches("[+-]?([0-9]+(\\.[0-9]*)?|\\.[0-9]+)([eE][+-]?[0-9]+)?"))throw new IOException("Require explicit finite decimal data, no missing values");double v=Double.parseDouble(text);if(!Double.isFinite(v))throw new IOException("Nonfinite complete summary input");return v;}
    private static void requestGraph(Path root,Settings s,MashSummaryLimits limits,Runnable check)throws IOException {
        var expected=new HashSet<>(Set.of("settings.tsv","significance-conditions.tsv"));if(s.composite())expected.addAll(Set.of("groups.tsv","nominal-p.tsv"));if(s.proxy())expected.addAll(Set.of("patterns.tsv","selectors.tsv"));var actual=MashFiles.inventory(root,limits.requestFiles(),check);
        if(!actual.files().keySet().equals(expected)||!actual.directories().equals(Set.of("")))throw new IOException("Missing/extra/unconsumed request files or directories");
        long structural=0;for(String name:expected)if(!name.equals("nominal-p.tsv"))structural=Math.addExact(structural,actual.files().get(name).bytes());if(structural>1_048_576||structural*16>Runtime.getRuntime().maxMemory()/8)throw new IOException("Complete selector/group metadata exceeds capacity");
    }
    private static String[] row(String line,int columns)throws IOException {if(line==null||line.length()>LINE)throw new IOException("Missing/oversized complete summary row");String[] cells=line.split("\t",-1);if(cells.length!=columns)throw new IOException("Complete summary width differs");return cells;}
    private static void token(String value)throws IOException {if(value.isBlank()||value.chars().anyMatch(Character::isISOControl))throw new IOException("Blank/control-character summary identity");}
    private static List<String> ids(Path path,int expected,Runnable check)throws IOException {var result=new ArrayList<String>();var seen=new HashSet<String>();try(var reader=Files.newBufferedReader(path,StandardCharsets.UTF_8)){for(int i=0;i<expected;i++){check.run();String line=reader.readLine();if(line==null||line.length()>65536)throw new IOException("Truncated/oversized native identity");token(line);if(!seen.add(line))throw new IOException("Duplicate native identity");result.add(line);}if(reader.readLine()!=null)throw new IOException("Extra native identity");}return List.copyOf(result);}
    private static double[][] matrix(Path path,int rows,int conditions,List<String> features,List<String> names,boolean header,Runnable check)throws IOException {
        double[][] values=new double[rows][conditions];try(var reader=Files.newBufferedReader(path,StandardCharsets.UTF_8)){
            if(header){String[] h=row(reader.readLine(),conditions+1);if(!h[0].equals("feature"))throw new IOException("Nominal-P header requires feature");for(int c=0;c<conditions;c++)if(!h[c+1].equals(names.get(c)))throw new IOException("Complete ordered nominal-P conditions differ");}
            for(int r=0;r<rows;r++){check.run();String[] line=row(reader.readLine(),conditions+(header?1:0));if(header&&!line[0].equals(features.get(r)))throw new IOException("Complete ordered nominal-P feature IDs differ");for(int c=0;c<conditions;c++)values[r][c]=header?probability(line[c+1]):number(line[c]);}if(reader.readLine()!=null)throw new IOException("Extra complete matrix rows");
        }return values;
    }
    private static int[] selection(Path file,Map<String,Integer> columns,Runnable check)throws IOException {
        var result=new ArrayList<Integer>();var seen=new HashSet<String>();try(var reader=Files.newBufferedReader(file,StandardCharsets.UTF_8)){if(!"condition".equals(reader.readLine()))throw new IOException("Require condition selection header");for(String line;(line=reader.readLine())!=null;){check.run();token(line);if(!columns.containsKey(line)||!seen.add(line))throw new IOException("Unknown/duplicate selected condition");result.add(columns.get(line));}}if(result.isEmpty())throw new IOException("Require at least one selected condition");return result.stream().mapToInt(Integer::intValue).toArray();
    }
    private record Groups(List<String> names,List<int[]> columns,long work,long members) { }
    private static Groups groups(Path path,Map<String,Integer> columns,Runnable check)throws IOException {
        var groups=new LinkedHashMap<String,List<Integer>>();var pairs=new HashSet<List<String>>();long members=0;
        try(var reader=Files.newBufferedReader(path,StandardCharsets.UTF_8)){if(!"group\tcondition".equals(reader.readLine()))throw new IOException("Require group/condition header");for(String line;(line=reader.readLine())!=null;){check.run();String[] r=row(line,2);token(r[0]);token(r[1]);if(!columns.containsKey(r[1])||!pairs.add(List.of(r[0],r[1])))throw new IOException("Unknown or repeated grouped condition");groups.computeIfAbsent(r[0],x->new ArrayList<>()).add(columns.get(r[1]));members++;}}
        if(groups.isEmpty())throw new IOException("Require nonempty explicit groups");var values=new ArrayList<int[]>();long work=0;for(var g:groups.values()){values.add(g.stream().mapToInt(Integer::intValue).toArray());work=Math.addExact(work,Math.multiplyExact(g.size()+1L,g.size()+1L));}return new Groups(List.copyOf(groups.keySet()),List.copyOf(values),work,members);
    }
    private record ProxyDeclaration(MashCompositeScores.Mode mode,List<Integer> include,List<Integer> exclude) { }
    private record ProxyFamily(List<MashCompositeScores.Pattern> patterns,long work,long members) { }
    private static ProxyFamily proxyFamily(Path request,Map<String,Integer> columns,int n,int c,long cells,long reserved,MashSummaryLimits limits,Runnable check)throws IOException {
        var declared=new LinkedHashMap<String,ProxyDeclaration>();long members=0;
        try(var reader=Files.newBufferedReader(request.resolve("patterns.tsv"),StandardCharsets.UTF_8)){
            if(!"pattern\tmode".equals(reader.readLine()))throw new IOException("Require pattern/mode header");
            for(String line;(line=reader.readLine())!=null;){
                check.run();String[] r=row(line,2);token(r[0]);if(declared.containsKey(r[0]))throw new IOException("Duplicate proxy pattern declaration");
                MashCompositeScores.Mode mode=switch(r[1]){case "all"->MashCompositeScores.Mode.ALL;case "at_least_one"->MashCompositeScores.Mode.AT_LEAST_ONE;default->throw new IOException("Unknown explicit proxy inclusion mode");};
                long size=declared.size()+1L;admission(n,c,Math.addExact(cells,size),0,Math.addExact(reserved,Math.multiplyExact(512L+160L*n,size)),limits);
                declared.put(r[0],new ProxyDeclaration(mode,new ArrayList<>(),new ArrayList<>()));
            }
        }
        if(declared.isEmpty())throw new IOException("Require at least one explicitly declared proxy pattern");
        try(var reader=Files.newBufferedReader(request.resolve("selectors.tsv"),StandardCharsets.UTF_8)){
            if(!"pattern\trole\tcondition".equals(reader.readLine()))throw new IOException("Require pattern/role/condition selector header");
            for(String line;(line=reader.readLine())!=null;){
                check.run();String[] r=row(line,3);token(r[0]);token(r[2]);ProxyDeclaration p=declared.get(r[0]);Integer column=columns.get(r[2]);
                if(p==null||column==null)throw new IOException("Unknown proxy pattern or exact condition; regexes are not expanded");
                List<Integer> selected=switch(r[1]){case "include"->p.include();case "exclude"->p.exclude();default->throw new IOException("Proxy role must be include or exclude");};
                members=Math.addExact(members,1);long size=declared.size();admission(n,c,Math.addExact(cells,Math.addExact(size,members)),0,Math.addExact(reserved,Math.addExact(Math.multiplyExact(512L+160L*n,size),64L*members)),limits);selected.add(column);
            }
        }
        var result=new ArrayList<MashCompositeScores.Pattern>();long work=0;
        for(var entry:declared.entrySet()){
            check.run();ProxyDeclaration p=entry.getValue();long size=Math.addExact(p.include().size(),(long)p.exclude().size());work=Math.addExact(work,Math.multiplyExact(size+1,size+1));
            result.add(new MashCompositeScores.Pattern(entry.getKey(),p.include().stream().mapToInt(Integer::intValue).toArray(),p.exclude().stream().mapToInt(Integer::intValue).toArray(),p.mode()));
        }return new ProxyFamily(List.copyOf(result),work,members);
    }

    private static Computed compute(Path nativeRoot,Path request,Path output,Settings s,Native source,MashSummaryLimits limits,Runnable check)throws IOException {
        requestGraph(request,s,limits,check);int n=source.rows(),c=source.conditions();long cells=Math.multiplyExact(n*(s.composite()?3L:2L),c);
        long identityBytes=Math.addExact(Files.size(nativeRoot.resolve("execution/target-ids.tsv")),Files.size(nativeRoot.resolve("execution/conditions.tsv")));
        long structural=Files.size(request.resolve("settings.tsv"))+Files.size(request.resolve("significance-conditions.tsv"))+(s.composite()?Files.size(request.resolve("groups.tsv")):0)+(s.proxy()?Files.size(request.resolve("patterns.tsv"))+Files.size(request.resolve("selectors.tsv")):0);
        long base=Math.addExact(8_388_608,Math.addExact(Math.multiplyExact(identityBytes,8),Math.multiplyExact(structural,16)));
        admission(n,c,cells,0,Math.addExact(base,Math.multiplyExact((long)n,256L+32L*c)),limits);
        List<String> names=ids(nativeRoot.resolve("execution/conditions.tsv"),c,check),features=ids(nativeRoot.resolve("execution/target-ids.tsv"),n,check);var index=new HashMap<String,Integer>();for(int i=0;i<c;i++)index.put(names.get(i),i);
        int[] selection=selection(request.resolve("significance-conditions.tsv"),index,check);Groups groups=s.composite()?groups(request.resolve("groups.tsv"),index,check):new Groups(List.of(),List.of(),0,0);
        long reserved=Math.addExact(base,Math.addExact(Math.multiplyExact((long)n,256L+32L*c+128L*groups.names().size()),128L*c*c));
        ProxyFamily family=s.proxy()?proxyFamily(request,index,n,c,cells,reserved,limits,check):new ProxyFamily(List.of(),0,0);
        long work=Math.addExact(Math.addExact(cells,Math.multiplyExact((long)n,64L+selection.length)),Math.addExact(Math.multiplyExact((long)n,c*(c+1L)/2),Math.multiplyExact(n+1L,Math.addExact(groups.work(),family.work()))));
        long bytes=Math.addExact(reserved,Math.addExact(Math.multiplyExact(512L+160L*n,family.patterns().size()),64L*family.members()));admission(n,c,Math.addExact(cells,selection.length+groups.members()+family.members()+family.patterns().size()),work,bytes,limits);
        double[][] means=matrix(nativeRoot.resolve("execution/posterior/mean.tsv"),n,c,features,names,false,check),lfsr=matrix(nativeRoot.resolve("execution/posterior/lfsr.tsv"),n,c,features,names,false,check);
        double[][] nominal=s.composite()?matrix(request.resolve("nominal-p.tsv"),n,c,features,names,true,check):null;
        var significance=MashSharing.significance(lfsr,c,selection,s.threshold(),s.boundary(),limits.summary(),check);var pairs=MashSharing.pairwise(means,lfsr,c,s.factor(),s.threshold(),s.transform(),limits.summary(),check);
        var score=s.composite()?MashSignScores.calculate(lfsr,nominal,c,groups.columns(),s.nominal(),s.minimum(),s.score(),limits.summary(),check):null;
        var proxies=s.proxy()?MashCompositeScores.calculate(lfsr,c,family.patterns(),MashCompositeScores.Scale.LFSR,MashCompositeScores.Missing.REJECT,MashCompositeScores.Assumption.INDEPENDENT_MARGINAL_PROXIES,limits.summary(),check):null;
        long available=limits.bundleBytes()-bytes(MashFiles.inventory(output.getParent(),limits.files(),check));MashFiles.create(output);Sink sink=new Sink(output,available,check);int[] selected=significance.selectedRows(),counts=significance.significantConditions();double[] minimum=significance.minimumSignificance();boolean[] chosen=new boolean[n];for(int r:selected)chosen[r]=true;
        try(var file=sink.file("significance.tsv")){sink.line(file,"feature\tminimum_lfsr\tsignificant_conditions\tselected");for(int r=0;r<n;r++)sink.line(file,features.get(r)+"\t"+minimum[r]+"\t"+counts[r]+"\t"+chosen[r]);}
        try(var file=sink.file("significant-ids.tsv")){sink.line(file,"rank\tfeature");for(int i=0;i<selected.length;i++)sink.line(file,(i+1)+"\t"+features.get(selected[i]));}
        int empty=0,undefined=0,underflow=0;
        try(var file=sink.file("pairwise.tsv")){sink.line(file,"condition_1\tcondition_2\tselected_rows\tshared_rows\tundefined_ratios\tstatus\tfraction");for(int i=0;i<c;i++)for(int j=0;j<c;j++){var p=pairs.cell(i,j);if(p.status()==MashSharing.Status.EMPTY_SELECTED_POPULATION)empty++;if(p.status()==MashSharing.Status.UNDEFINED_SELECTED_RATIO)undefined++;sink.line(file,names.get(i)+"\t"+names.get(j)+"\t"+p.selectedRows()+"\t"+p.sharedRows()+"\t"+p.undefinedRatios()+"\t"+p.status()+"\t"+(p.status()==MashSharing.Status.DEFINED?Double.toString(p.fraction()):"NA"));}}
        if(score!=null)try(var file=sink.file("marginal-sign-products.tsv")){sink.line(file,"feature\tgroup\tproduct\tlog_product\tproduct_state\tnominally_significant\teligible\texceeds_threshold");for(int r=0;r<n;r++)for(int g=0;g<groups.names().size();g++){var p=score.score(r,g);if(p.underflowToZero())underflow++;String state=p.underflowToZero()?"UNDERFLOW_TO_ZERO":p.logProduct()==Double.NEGATIVE_INFINITY?"EXACT_ZERO":"FINITE";sink.line(file,features.get(r)+"\t"+groups.names().get(g)+"\t"+p.product()+"\t"+(p.logProduct()==Double.NEGATIVE_INFINITY?"-Inf":Double.toString(p.logProduct()))+"\t"+state+"\t"+p.nominallySignificant()+"\t"+p.eligible()+"\t"+p.exceedsThreshold());}}
        ProxyCounts proxyCounts=null;
        if(proxies!=null){
            long rounded=0,tiny=0,zero=0,loss=0,repeated=0,overlap=0;
            try(var file=sink.file("independent-marginal-proxies.tsv")){
                sink.line(file,"feature\tpattern\tsource_score\tsource_inclusion\tsource_exclusion\tstable_log_score\tstate\tpasses_threshold\tmissing_selected_occurrences\trounded_marginal_occurrences\trepeated_selector_occurrences\toverlapping_conditions\tnumerical_loss");
                for(int r=0;r<n;r++)for(int k=0;k<family.patterns().size();k++){
                    var p=proxies.score(r,k);switch(p.state()){case SOURCE_ROUNDING_TO_ZERO->rounded++;case UNDERFLOW_TO_ZERO->tiny++;case EXACT_ZERO->zero++;default->{ }}
                    if(p.numericalLoss())loss++;if(p.repeatedSelectorOccurrences()>0)repeated++;if(p.overlappingConditions()>0)overlap++;
                    sink.line(file,features.get(r)+"\t"+family.patterns().get(k).name()+"\t"+p.sourceScore()+"\t"+p.sourceInclusion()+"\t"+p.sourceExclusion()+"\t"+(p.stableLogScore()==Double.NEGATIVE_INFINITY?"-Inf":Double.toString(p.stableLogScore()))+"\t"+p.state()+"\t"+p.passes(s.score(),s.scoreBoundary())+"\t"+p.missingSelectedOccurrences()+"\t"+p.roundedMarginalOccurrences()+"\t"+p.repeatedSelectorOccurrences()+"\t"+p.overlappingConditions()+"\t"+p.numericalLoss());
                }
            }proxyCounts=new ProxyCounts(family.patterns().size(),proxies.missingMatrixCells(),rounded,tiny,zero,loss,repeated,overlap);
        }
        check.run();return new Computed(new Counts(n,c,groups.names().size(),selected.length,empty,undefined,underflow),s,proxyCounts);
    }
    private static void admission(int n,int c,long cells,long work,long bytes,MashSummaryLimits limits)throws IOException {var l=limits.summary();if(n>l.rows()||c>l.conditions()||cells>l.inputCells()||work>l.work()||bytes>l.ownedBytes()||bytes>Runtime.getRuntime().maxMemory()/2)throw new IOException("Complete summary data/results exceed declared dimensions/cells/work/storage or half the current heap; no prefix returned");}
    private static final class Sink {
        private final Path root;private final long limit;private final Runnable check;private long bytes;
        Sink(Path root,long limit,Runnable check){this.root=root;this.limit=limit;this.check=check;}
        BufferedWriter file(String name)throws IOException {return Files.newBufferedWriter(root.resolve(name),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);}
        void line(BufferedWriter output,String text)throws IOException {check.run();bytes=Math.addExact(bytes,text.getBytes(StandardCharsets.UTF_8).length+1L);if(bytes>limit)throw new IOException("Complete summary result byte capacity exceeded");output.write(text);output.write('\n');}
    }

    static Verification verify(Path root,String completion,MashSummaryLimits ceiling,Runnable check)throws IOException {
        if(!Set.of("COMPLETED.json","COMPLETION-PROVISIONAL.json").contains(completion))throw new IOException("Unknown summary completion stage");MashFiles.canonical(root);scratchOutside(root);
        Object retained=MashFiles.json(root.resolve("limits.json"),262144);MashSummaryLimits limits=MashSummaryLimits.parse(retained);MashSummaryLimits.within(retained,SimpleJson.parse(MashJson.json(ceiling)));
        if(!SimpleJson.parse(MashJson.json(limits)).equals(retained)||!limits.equals(MashSummaryLimits.parse(MashFiles.json(root.resolve("limits-request.json"),262144))))throw new IOException("Requested and normalized summary limits differ");
        var before=MashFiles.inventory(root,limits.files(),check);var markerPin=MashFiles.pin(root.resolve(completion),262144,check);var manifestPin=MashFiles.pin(root.resolve("manifest.json"),JSON,check);
        if(!SimpleJson.parse(MashJson.json(marker(manifestPin))).equals(MashFiles.json(root.resolve(completion),262144)))throw new IOException("No admitted scoped summary completion");
        var manifest=MashLimits.keys(MashFiles.json(root.resolve("manifest.json"),JSON),Set.of("schema_version","tool","algorithm","files","directories"));if(MashLimits.n(manifest,"schema_version")!=1||!TOOL.equals(manifest.get("tool"))||!ALGORITHM.equals(manifest.get("algorithm")))throw new IOException("Summary schema/tool/algorithm changed");
        var data=new TreeMap<>(before.files());data.remove(completion);data.remove("manifest.json");if(!SimpleJson.parse(MashJson.json(data)).equals(manifest.get("files"))||!(manifest.get("directories") instanceof List<?> dirs)||dirs.size()!=before.directories().size()||!new HashSet<>(dirs).equals(before.directories())||!MashFiles.directories(before.files().keySet()).equals(before.directories()))throw new IOException("Missing/extra/changed summary files or directory graph");
        Set<String> fixed=Set.of("limits.json","limits-request.json","session.json","WARNINGS.md");var top=new HashSet<String>();for(String name:data.keySet())if(!name.contains("/"))top.add(name);if(!top.equals(fixed)||data.keySet().stream().anyMatch(n->!fixed.contains(n)&&!n.startsWith("native/")&&!n.startsWith("request/")&&!n.startsWith("results/")))throw new IOException("Unexpected summary semantic file graph");
        Settings settings=settings(root.resolve("request/settings.tsv"));requestGraph(root.resolve("request"),settings,limits,check);Native nativeResult=source(root.resolve("native"),limits,check);
        Path scratch=Files.createTempDirectory("omicsmatic-mash-summary-verify-").toRealPath();
        try {
            Computed replay=compute(root.resolve("native"),root.resolve("request"),scratch.resolve("results"),settings,nativeResult,limits,check);long compared=compare(root.resolve("results"),scratch.resolve("results"),limits,check);
            if(!SimpleJson.parse(MashJson.json(session(nativeResult,replay))).equals(MashFiles.json(root.resolve("session.json"),262144))||!completeWarnings(replay).equals(MashFiles.text(root.resolve("WARNINGS.md"),65536)))throw new IOException("Summary method claims or warnings differ from full replay");
            if(!before.equals(MashFiles.inventory(root,limits.files(),check))||!markerPin.equals(MashFiles.pin(root.resolve(completion),262144,check)))throw new IOException("Summary source changed during replay");
            MashFiles.write(scratch.resolve("VERIFIED.json"),Map.of("tool",TOOL,"status","passed-native-and-summary-replay","source_unchanged",true,"numeric_logs_compared",compared));return new Verification(scratch,compared);
        }catch(IOException|RuntimeException failure){boolean unchanged=before.equals(MashFiles.inventory(root,limits.files(),()->{}));MashFiles.write(scratch.resolve("FAILURE.json"),Map.of("error",failure.toString(),"source_unchanged",unchanged,"required_action",ACTION));throw new IOException("Summary replay failed; retain scratch "+scratch+"; source_unchanged="+unchanged+"; "+failure,failure);}
    }
    private static long compare(Path recorded,Path replay,MashSummaryLimits limits,Runnable check)throws IOException {
        var a=MashFiles.inventory(recorded,limits.files(),check);var b=MashFiles.inventory(replay,limits.files(),check);if(!a.files().keySet().equals(b.files().keySet())||!a.directories().equals(b.directories()))throw new IOException("Complete summary result graph differs");long values=0;
        for(String name:a.files().keySet()){
            boolean proxy=name.equals("independent-marginal-proxies.tsv");
            if(!name.equals("marginal-sign-products.tsv")&&!proxy){if(!a.files().get(name).equals(b.files().get(name)))throw new IOException("Exact summary result differs: "+name);continue;}
            try(var x=Files.newBufferedReader(recorded.resolve(name),StandardCharsets.UTF_8);var y=Files.newBufferedReader(replay.resolve(name),StandardCharsets.UTF_8)){
                int width=proxy?13:8,log=proxy?5:3;
                if(!java.util.Objects.equals(x.readLine(),y.readLine()))throw new IOException("Marginal product header changed");for(;;){check.run();String p=x.readLine(),q=y.readLine();if(p==null||q==null){if(p!=q)throw new IOException("Truncated marginal-product result");break;}String[] left=row(p,width),right=row(q,width);for(int i=0;i<width;i++)if(i!=log||left[i].equals("-Inf")||right[i].equals("-Inf")){if(!left[i].equals(right[i]))throw new IOException("Exact marginal product/identity/gate/state changed");}else {double v=number(left[i]),w=number(right[i]);if(v>0||w>0||Math.abs(v-w)>1e-12+1e-12*Math.abs(w))throw new IOException("Marginal log score replay differs");values++;}}
            }
        }return values;
    }
    private static void export(Path source,Path root,MashSummaryLimits limits,Runnable check)throws IOException {
        Native verified=source(source,limits,check);long bytes=Files.size(source.resolve("execution/conditions.tsv"));if(bytes>1_048_576||verified.conditions()>limits.summary().conditions())throw new IOException("Template condition metadata exceeds capacity");List<String> names=ids(source.resolve("execution/conditions.tsv"),verified.conditions(),check);
        MashFiles.create(root);Files.createDirectory(root.resolve("input"));Files.writeString(root.resolve("input/settings.tsv"),"key\tvalue\nschema_version\t1\nlfsr_threshold\t0.05\nsignificance_boundary\tstrict\nsharing_factor\t0.5\nsharing_transform\tidentity\ncomposite_mode\toff\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);Files.writeString(root.resolve("input/significance-conditions.tsv"),"condition\n"+String.join("\n",names)+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
        Files.writeString(root.resolve("README.md"),"# Mash summary request template\n\nRun MashSummaryCli --native-bundle VERIFIED_NATIVE_BUNDLE --input THIS_DIRECTORY/input --out NEW_OUTPUT. The request explicitly selects all native conditions; review the list and thresholds. To enable marginal sign products, set composite_mode=marginal_sign_product, add nominal_p_threshold, minimum_nominal and score_threshold to settings.tsv, and supply groups.tsv (group/condition pairs) and nominal-p.tsv (feature then every ordered native condition, every target row). Supply real nominal P values; no values were invented by this export. Do not put this README inside input.\n\n"+ACTION+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
    }
    static String help(){return """
        java -cp JAR org.jlinalg.cli.MashSummaryCli --native-bundle NATIVE_BUNDLE --input REQUEST --out NEW_OUTPUT [--limits JSON]
        java -cp JAR org.jlinalg.cli.MashSummaryCli --verify SUMMARY_BUNDLE [--limits CURRENT_CEILINGS]
        java -cp JAR org.jlinalg.cli.MashSummaryCli --export-template NATIVE_BUNDLE --out NEW_DIRECTORY [--limits JSON]
        --default-limits | --help
        Captures and replay-verifies the complete native source. Explicit significance,
        posterior-mean sharing and optional nominal-gated marginal sign products.
        composite_mode=independent_marginal_proxy additionally requires explicit
        proxy_assumption=independent_marginal_proxies, score_threshold, score_boundary,
        patterns.tsv (pattern/mode) and selectors.tsv (pattern/role/condition).
        Modes: all or at_least_one. Roles: include or exclude; exact native names.
        Empty selectors, repeats and overlap retain the declared source identities.
        Source scores, stable logs and every numerical/selector diagnostic are retained.
        This explicitly named proxy is not joint posterior integration. The older
        formal_composite/joint_nonzero/posterior_samples requests still skip with3.
        Retained source tables define the summaries. Pairwise significance is strict;
        inclusive count boundaries never silently change pairwise source semantics.
        Creation performs two native/summary replays; verify performs one.
        Successful and failed summary replay scratch is retained. Java only; no global
        user/project configuration, external R process or software installation.
        """+ACTION;}
}
