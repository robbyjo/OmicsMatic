/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlinalg.stats.DunnComparisons;

/** Native independent-sample Dunn comparisons from complete explicitly prepared observations. */
public final class DunnComparisonsCli {
    private static final int MAX_BYTES=64<<20;
    private static final String PROFILE="native-dunn-independent-v1";
    private static final String SCOPE="Independent-sample two-sided Dunn z comparisons on pooled midranks of ALL declared groups, with a tie-corrected rank variance and explicit comparison/adjustment family. Normal-reference probabilities are asymptotic, including at small sample sizes; no exact test or continuity correction is inferred. Kruskal-Wallis is reported without using its significance to select comparisons. No paired/repeated-measures, nested/mixed or covariate-adjusted model, missing-row removal, data transformation or automatic test selection. Dunn is not pairwise Mann-Whitney: ranks retain the complete declared population. The test is not universally a test of medians. Native Java needs no R/Prism installation.";
    private static final String ACTION="Require exit 0, COMPLETED.json and analysis.json result_admitted=true. SKIP3: dependent analysis skipped for an unavailable/forbidden prerequisite or explicitly unsupported design. Supply permitted accessible inputs or a separately qualified implementation of the required design and retry NEW output. FAIL2: no complete comparison family admitted. Preserve input/partial output, read FAILURE.txt and WARNINGS.md, correct the named format, all-tied variance, comparison-family, capacity or integrity problem and retry NEW output. Never drop rows/groups, repair labels or replace nonfinite values to force success. Windows launchers may lose Unicode arguments: use an ASCII request pathname and UTF-8 JSON paths.";
    private record Capture(Path source,Path copy,NextflowFiles.Pin pin,int maximum) { }
    private record Observations(List<String> labels,double[][] values,boolean bom) { }
    private static final class UnsupportedDesign extends IOException {
        private static final long serialVersionUID=1L;
        UnsupportedDesign(String cause){super(cause);}
    }
    private DunnComparisonsCli() { }
    public static void main(String[] args){System.exit(run(args,System.out,System.err));}
    public static int run(String[] args,PrintStream output,PrintStream error) {
        Path owned=null;
        try {
            if(args.length==1&&args[0].equals("--help")) {
                output.println("posthoc-tools dunn --request UTF8_JSON\nRequired request: schema_version=1, input (UTF-8 group/value TSV), out NEW, design=independent, adjustment=NONE|BONFERRONI|HOLM|SIDAK|HOLM_SIDAK|BH|BY, family=all|control|specified, rank_population_declaration, data_provenance. family=control also requires control with its exact group label; family=specified instead requires comparisons pointing to UTF-8 first_group/second_group TSV. Other family-specific fields must be absent. Paths resolve against the request. Group labels retain first appearance; both TSVs are literal/unquoted and forbid control characters. No omitted, blank or nonfinite values.\nWhole-input refusal bounds: 64MiB observations,16MiB selected comparisons,2million observations,256 groups; no prefix admitted. Outputs preserve complete inputs/request, ranks/counts, contrasts, z/raw/adjusted/log probabilities, omnibus diagnostics, warnings and checksummed completion.\n"+SCOPE+"\n"+ACTION);output.flush();return output.checkError()?2:0;
            }
            require(args.length==2&&args[0].equals("--request"),"Use dunn --request JSON or --help");
            Path request=regular(Path.of(args[1]));var requestPin=NextflowFiles.pin(request,1048576,()->{});byte[] raw=NextflowFiles.bytes(request,1048576);require(requestPin.equals(new NextflowFiles.Pin(raw.length,NextflowFiles.sha(raw))),"Request changed during capture");
            var m=NextflowFiles.object(NextflowParameters.parse(NextflowFiles.utf8(raw),1048576,100000));Set<String> allowed=Set.of("schema_version","input","out","design","adjustment","family","control","comparisons","rank_population_declaration","data_provenance");require(m.keySet().stream().allMatch(allowed::contains),"Unknown request setting; no inferred design or contrast family");NextflowFiles.integer(m.get("schema_version"),1,1);
            String design=NextflowFiles.text(m.get("design"));
            if(Set.of("paired","repeated-measures","nested","mixed").contains(design))throw new UnsupportedDesign("Requested "+design+" model is outside independent Dunn; dependent analysis skipped");
            require(design.equals("independent"),"Declare design=independent explicitly");
            var adjustment=DunnComparisons.Adjustment.valueOf(NextflowFiles.text(m.get("adjustment")));String family=NextflowFiles.text(m.get("family"));require(Set.of("all","control","specified").contains(family),"Declare all, control or specified comparison family");
            require(m.containsKey("control")==family.equals("control")&&m.containsKey("comparisons")==family.equals("specified"),"Declare exactly the selected family's control/comparisons settings");
            String population=label(NextflowFiles.text(m.get("rank_population_declaration"))),provenance=label(NextflowFiles.text(m.get("data_provenance")));
            var inputs=new LinkedHashMap<String,Path>();inputs.put("observations",regular(resolve(request.getParent(),m.get("input"))));if(family.equals("specified"))inputs.put("comparisons",regular(resolve(request.getParent(),m.get("comparisons"))));
            var protectedInputs=new ArrayList<>(inputs.values());protectedInputs.add(request);Path dest=destination(resolve(request.getParent(),m.get("out")),protectedInputs);Files.createDirectories(dest.getParent());Files.createDirectory(dest);owned=dest;
            write(owned.resolve("WARNINGS.md"),"# Scope, warnings and required actions\n\n"+SCOPE+"\n\n"+ACTION+"\n\nMultiplicity control depends on the declared family and assumptions. Bonferroni/Holm control familywise error under arbitrary dependence; Sidak/Holm-Sidak and BH need their respective dependence assumptions, while BY addresses arbitrary dependence. No correction is inferred from a software-name mention. This profile does not establish licensed Prism, whole-package dunn.test, a paper-specific workflow or the full 250-paper goal.\n");
            Files.write(owned.resolve("source-request.json"),raw,StandardOpenOption.CREATE_NEW);var captures=new ArrayList<Capture>();var captured=new LinkedHashMap<String,Path>();
            for(var entry:inputs.entrySet()) {int max=entry.getKey().equals("observations")?MAX_BYTES:16<<20;var before=NextflowFiles.pin(entry.getValue(),max,()->{});byte[] bytes=NextflowFiles.bytes(entry.getValue(),max);require(before.equals(new NextflowFiles.Pin(bytes.length,NextflowFiles.sha(bytes))),"Input changed during complete capture");Path copy=owned.resolve("source-"+entry.getKey()+".tsv");Files.write(copy,bytes,StandardOpenOption.CREATE_NEW);captures.add(new Capture(entry.getValue(),copy,before,max));captured.put(entry.getKey(),copy);}
            Observations observations=observations(captured.get("observations"));DunnComparisons.Result result;
            if(family.equals("all"))result=DunnComparisons.allPairs(observations.values(),adjustment);
            else if(family.equals("control")) {String control=NextflowFiles.text(m.get("control"));int index=observations.labels().indexOf(control);require(index>=0,"Exact control group is absent; no label normalization");result=DunnComparisons.versusControl(observations.values(),index,adjustment);}
            else result=DunnComparisons.compare(observations.values(),comparisons(captured.get("comparisons"),observations.labels()),adjustment);
            var groups=new StringBuilder("index\tgroup\tn\tmean_rank\n");for(var group:result.groups())groups.append(group.index()).append('\t').append(quoted(observations.labels().get(group.index()))).append('\t').append(group.size()).append('\t').append(group.meanRank()).append('\n');write(owned.resolve("groups.tsv"),groups.toString());
            var table=new StringBuilder("first_group\tsecond_group\tmean_rank_difference_second_minus_first\tstandard_error\tz\tp\tlog_p\tadjusted_p\tlog_adjusted_p\n");boolean zero=result.omnibusPValue()==0;
            for(var c:result.comparisons()) {table.append(quoted(observations.labels().get(c.first()))).append('\t').append(quoted(observations.labels().get(c.second()))).append('\t').append(c.meanRankDifference()).append('\t').append(c.standardError()).append('\t').append(c.z()).append('\t').append(c.pValue()).append('\t').append(logToken(c.logPValue())).append('\t').append(c.adjustedPValue()).append('\t').append(logToken(c.logAdjustedPValue())).append('\n');zero|=c.pValue()==0||c.adjustedPValue()==0;}write(owned.resolve("comparisons.tsv"),table.toString());
            var report=new LinkedHashMap<String,Object>();report.put("profile",PROFILE);report.put("result_admitted",true);report.put("design",design);report.put("adjustment",adjustment.name());report.put("family",family);report.put("control",m.get("control"));report.put("rank_population_declaration",population);report.put("data_provenance",provenance);report.put("observations",result.observations());report.put("groups",observations.labels());report.put("comparisons",result.comparisons().size());report.put("rank_variance",result.rankVariance());report.put("tie_correction",result.tieCorrection());report.put("tied_value_groups",result.tiedValueGroups());report.put("largest_tie",result.largestTie());report.put("kruskal_wallis",result.kruskalWallis());report.put("omnibus_df",result.groups().size()-1);report.put("omnibus_p",result.omnibusPValue());report.put("log_omnibus_p",logToken(result.logOmnibusPValue()));report.put("probability_method","two-sided normal approximation, pooled midranks with ties, no continuity correction; omnibus chi-square approximation");report.put("omnibus_significance_gate_applied",false);report.put("utf8_bom_present",observations.bom());report.put("numeric_zero_probability_observed",zero);report.put("whole_vendor_tool_qualified",false);report.put("whole_goal_complete",false);
            if(zero)write(owned.resolve("NUMERIC-WARNING.txt"),"Some probabilities reach zero at floating-point resolution. This does not establish exactly zero mathematical probability. Retain log_p, log_adjusted_p and z; -Inf explicitly records an unrepresentable log probability and is not repaired.\n");
            for(Capture c:captures)require(c.pin().equals(NextflowFiles.pin(c.source(),c.maximum(),()->{}))&&c.pin().equals(NextflowFiles.pin(c.copy(),c.maximum(),()->{})),"Source or captured input changed during analysis; no complete family");
            require(requestPin.equals(NextflowFiles.pin(request,1048576,()->{}))&&requestPin.equals(NextflowFiles.pin(owned.resolve("source-request.json"),1048576,()->{})),"Request changed during analysis");NextflowFiles.writeJson(owned.resolve("analysis.json"),report);
            var manifest=new LinkedHashMap<String,Object>();try(var files=Files.walk(owned)) {for(Path p:files.filter(Files::isRegularFile).sorted().toList())manifest.put(owned.relativize(p).toString().replace('\\','/'),NextflowFiles.pin(p,128L<<20,()->{}));}NextflowFiles.writeJson(owned.resolve("manifest.json"),manifest);
            output.println("PASS native Dunn: "+result.observations()+" observations, "+result.groups().size()+" groups, "+result.comparisons().size()+" declared comparisons. READ "+owned.resolve("WARNINGS.md")+(zero?" and NUMERIC-WARNING.txt":""));output.flush();error.flush();require(!output.checkError()&&!error.checkError(),"Console recording failed; no complete family");NextflowFiles.writeJson(owned.resolve("COMPLETED.json"),Map.of("status","PASS_NATIVE_DUNN_COMPLETE_FAMILY","profile",PROFILE,"manifest",NextflowFiles.pin(owned.resolve("manifest.json"),1048576,()->{})));return 0;
        } catch(Exception failure){return failed(owned,failure,error);}
        catch(OutOfMemoryError failure){return failed(owned,new IOException("Java heap exhausted; no partial comparison family admitted. Preserve inputs and retry NEW output with suitable resources",failure),error);}
    }
    private static Observations observations(Path path)throws IOException {
        String text=NextflowFiles.utf8(NextflowFiles.bytes(path,MAX_BYTES));boolean bom=text.startsWith("\ufeff");if(bom)text=text.substring(1);var grouped=new LinkedHashMap<String,ArrayList<Double>>();int count=0;
        try(var lines=text.lines()) {var iterator=lines.iterator();require(iterator.hasNext()&&iterator.next().equals("group\tvalue"),"Require exact group<TAB>value header");while(iterator.hasNext()) {String[] fields=iterator.next().split("\t",-1);require(fields.length==2,"Require two literal fields in every observation row; empty rows are not dropped");String name=label(fields[0]);require(++count<=DunnComparisons.MAXIMUM_OBSERVATIONS,"Complete rank population exceeds two million rows");grouped.computeIfAbsent(name,k->new ArrayList<>()).add(decimal(fields[1]));require(grouped.size()<=256,"Complete rank population exceeds 256 groups");}}
        List<String> labels=List.copyOf(grouped.keySet());double[][] values=new double[labels.size()][];for(int i=0;i<values.length;i++)values[i]=grouped.get(labels.get(i)).stream().mapToDouble(Double::doubleValue).toArray();return new Observations(labels,values,bom);
    }
    private static List<DunnComparisons.Contrast> comparisons(Path path,List<String> labels)throws IOException {
        String text=NextflowFiles.utf8(NextflowFiles.bytes(path,16<<20));if(text.startsWith("\ufeff"))text=text.substring(1);var pairs=new ArrayList<DunnComparisons.Contrast>();
        try(var lines=text.lines()) {var it=lines.iterator();require(it.hasNext()&&it.next().equals("first_group\tsecond_group"),"Require exact first_group<TAB>second_group header");while(it.hasNext()) {String[] fields=it.next().split("\t",-1);require(fields.length==2,"Incomplete comparison row; no implicit pairs");int first=labels.indexOf(fields[0]),second=labels.indexOf(fields[1]);require(first>=0&&second>=0,"Comparison label is absent from the complete rank population");pairs.add(new DunnComparisons.Contrast(first,second));require(pairs.size()<=DunnComparisons.MAXIMUM_COMPARISONS,"Complete correction family exceeds one million pairs");}}
        return List.copyOf(pairs);
    }
    private static int failed(Path owned,Exception cause,PrintStream error) {
        int code=cause instanceof NoSuchFileException||cause instanceof AccessDeniedException||cause instanceof SecurityException||cause instanceof UnsupportedDesign?3:2;String message=(code==3?"SKIP3: DEPENDENT ANALYSIS SKIPPED. ":"FAIL2: NO COMPLETE COMPARISON FAMILY ADMITTED. ")+cause+"\nREQUIRED ACTION: "+ACTION+"\n";
        if(owned!=null)try {Files.deleteIfExists(owned.resolve("COMPLETED.json"));if(Files.exists(owned.resolve("analysis.json")))Files.move(owned.resolve("analysis.json"),owned.resolve("unadmitted-analysis.json"));NextflowFiles.writeJson(owned.resolve("analysis.json"),Map.of("result_admitted",false,"status",code==3?"SKIP3_DEPENDENT_ANALYSIS_SKIPPED":"FAIL2_NO_RESULT_ADMITTED","cause",cause.toString(),"required_action",ACTION));write(owned.resolve("FAILURE.txt"),message);}catch(IOException recording){message+="Failure recording also failed: "+recording+"\n";}
        error.print(message);error.flush();return code;
    }
    private static Object logToken(double x){return Double.isFinite(x)?x:"-Inf";}
    private static double decimal(String x)throws IOException {require(x.length()<=256&&x.matches("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?"),"Require complete finite decimal observations");double value=Double.parseDouble(x);require(Double.isFinite(value),"Nonfinite observation; no inferred rank");return value;}
    private static String label(String x)throws IOException {require(!x.isBlank()&&x.length()<=32768&&x.chars().noneMatch(c->c<32||c==127),"Require literal nonblank labels/provenance without control characters");return x;}
    private static String quoted(String x){return "\""+x.replace("\"","\"\"")+"\"";}
    private static Path resolve(Path parent,Object value)throws IOException {Path p=Path.of(label(NextflowFiles.text(value)));return(p.isAbsolute()?p:parent.resolve(p)).toAbsolutePath().normalize();}
    private static Path regular(Path p)throws IOException {p=p.toAbsolutePath().normalize();if(!Files.exists(p,LinkOption.NOFOLLOW_LINKS))throw new NoSuchFileException(p.toString());require(Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS),"Require a regular unlinked input");return p.toRealPath();}
    private static Path destination(Path absolute,List<Path> inputs)throws IOException {require(!Files.exists(absolute,LinkOption.NOFOLLOW_LINKS),"Output exists; preserve it and select NEW output");Path parent=absolute.getParent();require(parent!=null,"Output needs a parent directory");while(!Files.exists(parent,LinkOption.NOFOLLOW_LINKS))parent=parent.getParent();Path resolved=parent.toRealPath().resolve(parent.relativize(absolute)).normalize();for(Path input:inputs)require(!input.startsWith(resolved)&&!resolved.startsWith(input),"Output overlaps a protected input");return resolved;}
    private static void write(Path p,String text)throws IOException {Files.writeString(p,text,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);}
    private static void require(boolean condition,String message)throws IOException {if(!condition)throw new IOException(message);}
}
