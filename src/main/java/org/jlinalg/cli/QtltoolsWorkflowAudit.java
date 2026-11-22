/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;

/** Complete raw-export scans with explicit edition schemas and retained undefined metrics. */
final class QtltoolsWorkflowAudit {
    private QtltoolsWorkflowAudit() { }
    static Map<String,Object> outputs(Path root,Map<String,Object> r)throws IOException {
        String operation=text(r,"operation"),edition=text(r,"edition");
        var result=new LinkedHashMap<String,Object>();result.put("operation",operation);result.put("edition",edition);
        var exports=new TreeMap<String,Object>();var names=PeerWorkflowRuntime.footprint(root.resolve("outputs")).keySet();
        if(names.isEmpty())throw new IOException("No complete original export was produced");
        if(operation.startsWith("CIS_")){requireNames(names,Set.of("result"));exports.put("result",cis(root.resolve("outputs/result"),r));}
        else if(operation.startsWith("TRANS_")) {
            requireNames(names,Set.of("result.hits.txt.gz","result.bins.txt.gz","result.best.txt.gz"));
            for(String kind:List.of("hits","bins","best"))exports.put("result."+kind+".txt.gz",trans(root.resolve("outputs/result."+kind+".txt.gz"),r,kind));
        }else if(operation.equals("MBV")){requireNames(names,Set.of("result"));exports.put("result",mbv(root.resolve("outputs/result"),r));}
        else if(operation.equals("QUAN"))quan(root,r,names,exports);
        else {
            requireNames(names,Set.of("result"));long[] rows={0};long window=((Number)object(r.get("parameters"),"parameters").get("window")).longValue(),bin=((Number)object(r.get("parameters"),"parameters").get("bin")).longValue();
            scan(root.resolve("outputs/result"),r,f->{width(f,3);long start=integer(f[0]),end=integer(f[1]);nonnegativeInteger(f[2]);if(start!=-window+rows[0]*bin||end!=start+bin-1)throw new IOException("Incomplete or reordered fdensity bins");rows[0]++;});
            if(rows[0]!=(2*window+bin-1)/bin)throw new IOException("Incomplete fdensity bin population");exports.put("result",Map.of("rows",rows[0]));
            if(edition.equals("1.3.1")) {
                var expected=QtltoolsWorkflowFiles.nativeCalculate(root,r);
                if(!java.util.Arrays.equals(Files.readAllBytes(root.resolve("outputs/result")),expected.output().getBytes(StandardCharsets.UTF_8)))throw new IOException("fdensity output differs from complete native recomputation");
                result.put("fdensity_metadata",expected.metadata());
            }
        }
        String kind="model-result";
        if(operation.startsWith("CIS_")){var table=object(exports.get("result"),"cis export");long rows=((Number)table.get("rows")).longValue();if(rows==0)kind="null-selection";else if(operation.equals("CIS_PERMUTE")&&((Number)table.get("zero_variant_NA_rows")).longValue()==rows)kind="null-no-cis-variants";}
        else if(operation.equals("MBV")){var table=object(exports.get("result"),"MBV export");if(((Number)table.get("undefined_source_percentage_cells")).longValue()==2*((Number)table.get("rows")).longValue())kind="null-no-covered-genotype-concordance";}
        else if(operation.equals("QUAN")){boolean any=false;for(var entry:exports.entrySet())if(entry.getKey().contains(".gene.count.bed"))any=((Number)object(entry.getValue(),"gene counts").get("nonzero_count_cells")).longValue()>0;if(!any)kind="null-no-quantified-counts";}
        result.put("result_kind",kind);result.put("exports",exports);var pins=new TreeMap<String,Object>();for(String name:names)pins.put(name,FollowupSupport.hash(root.resolve("outputs/"+name)));result.put("raw_export_sha256",pins);
        return result;
    }
    @FunctionalInterface private interface Row { void accept(String[] fields)throws IOException; }
    private static void scan(Path path,Map<String,Object> r,Row consumer)throws IOException {
        var limits=new LinkedHashMap<>(object(r.get("limits"),"limits"));limits.put("maximum_input_bytes",limits.get("maximum_output_bytes"));var bounded=new LinkedHashMap<>(r);bounded.put("limits",limits);
        QtltoolsWorkflowFiles.lines(path,bounded,line->{if(line.isBlank())throw new IOException("Blank original export row");consumer.accept(line.strip().split("\\s+"));return true;});
    }
    private static Map<String,Object> cis(Path path,Map<String,Object> r)throws IOException {
        var p=object(r.get("parameters"),"parameters");String op=text(r,"operation"),group=text(p,"grouping");boolean modern=text(r,"edition").equals("1.3.1"),se=Boolean.TRUE.equals(p.get("standard_error"));int offset=group.equals("none")?0:group.equals("mean")?1:2;
        int count=op.equals("CIS_NOMINAL")?(modern?15:14):op.equals("CIS_PERMUTE")?(modern?20:19):(modern?22:20);count+=offset+(se?(op.equals("CIS_CONDITIONAL")?2:1):0);final int expected=count;long[] rows={0},nulls={0};
        scan(path,r,f->{
            width(f,expected);identity(f[0]);identity(f[1]);positiveInteger(f[2]);positiveInteger(f[3]);strand(f[4]);
            if(group.equals("best")){if(!f[5].equals("NA"))identity(f[5]);positiveInteger(f[6]);}
            else if(group.equals("pca1")){probability(f[5]);positiveInteger(f[6]);}else if(group.equals("mean"))positiveInteger(f[5]);
            long variants=nonnegativeInteger(f[5+offset]);
            if(op.equals("CIS_PERMUTE")&&variants==0){for(int i=6+offset;i<f.length;i++)if(!f[i].equals("NA"))throw new IOException("Zero-variant cis row must retain its complete source NA tail");nulls[0]++;rows[0]++;return;}
            if(variants==0)throw new IOException("Unexpected zero-variant fitted cis row");integer(f[6+offset]);identity(f[7+offset]);identity(f[8+offset]);positiveInteger(f[9+offset]);positiveInteger(f[10+offset]);
            if(op.equals("CIS_NOMINAL")){int i=11+offset;probability(f[i++]);if(modern)probability(f[i++]);number(f[i++]);if(se)nonnegative(f[i++]);bit(f[i]);}
            else if(op.equals("CIS_PERMUTE")){int i=11+offset;positive(f[i++]);positive(f[i++]);positive(f[i++]);positive(f[i++]);probability(f[i++]);if(modern)probability(f[i++]);number(f[i++]);if(se)nonnegative(f[i++]);probability(f[i++]);probability(f[i]);}
            else {int i=11+offset;nonnegativeInteger(f[i++]);for(int direction=0;direction<2;direction++){probability(f[i++]);if(modern)probability(f[i++]);number(f[i++]);if(se)nonnegative(f[i++]);bit(f[i++]);bit(f[i++]);}}
            rows[0]++;
        });
        if(op.equals("CIS_PERMUTE")&&rows[0]==0)throw new IOException("No complete cis permutation row");
        return Map.of("rows",rows[0],"zero_variant_NA_rows",nulls[0],"empty_selection",rows[0]==0,"schema_columns",expected);
    }
    private static Map<String,Object> trans(Path path,Map<String,Object> r,String kind)throws IOException {
        var p=object(r.get("parameters"),"parameters");boolean adjusted=text(r,"operation").equals("TRANS_ADJUST"),old=text(r,"edition").equals("1.1");long[] rows={0},nulls={0};
        scan(path,r,f->{
            if(kind.equals("hits")){width(f,9);identity(f[0]);identity(f[1]);positiveInteger(f[2]);identity(f[3]);identity(f[4]);positiveInteger(f[5]);probability(f[6]);adjusted(f[7],adjusted);double corr=number(f[8]);if(Math.abs(corr)>1)throw new IOException("Invalid exported correlation");}
            else if(kind.equals("bins")){width(f,6);if(integer(f[0])!=rows[0])throw new IOException("Missing,reordered or duplicated trans bin");double a=nonnegative(f[1]),b=nonnegative(f[2]);if(a>b||b>1)throw new IOException("Invalid correlation-bin endpoints");probability(f[3]);probability(f[4]);nonnegativeInteger(f[5]);}
            else {if(f.length!=(old?3:4)){if(!old&&f.length==3&&number(f[2])==1){nulls[0]++;}else throw new IOException("Incomplete edition-specific trans best row");}identity(f[0]);adjusted(f[1],adjusted);probability(f[2]);if(!old&&f.length==4)identity(f[3]);}
            rows[0]++;
        });
        if(kind.equals("bins")&&rows[0]!=((Number)p.get("bins")).longValue())throw new IOException("Incomplete trans bin population");
        if(kind.equals("best")&&(rows[0]==0||text(r,"operation").equals("TRANS_SAMPLE")&&rows[0]!=((Number)p.get("sample")).longValue()))throw new IOException("Incomplete trans best/sample population");
        return Map.of("rows",rows[0],"null_best_rows",nulls[0],"unadjusted_sentinel_minus_one",!adjusted);
    }
    private static void adjusted(String value,boolean adjusted)throws IOException {if(adjusted)probability(value);else if(number(value)!=-1)throw new IOException("Original unadjusted trans sentinel must remain -1");}
    private static Map<String,Object> mbv(Path path,Map<String,Object> r)throws IOException {
        String header="SampleID n_geno_missing n_het_total n_hom_total n_het_covered n_hom_covered n_het_consistent n_hom_consistent perc_het_consistent perc_hom_consistent n_het_in_ase";
        long[] rows={0},undefined={0};var samples=new HashSet<String>();
        scan(path,r,f->{
            width(f,11);if(rows[0]++==0){if(!String.join(" ",f).equals(header))throw new IOException("Unexpected MBV header");return;}
            identity(f[0]);if(!samples.add(f[0]))throw new IOException("Repeated MBV sample");long[] count=new long[11];for(int i=1;i<=7;i++)count[i]=nonnegativeInteger(f[i]);count[10]=nonnegativeInteger(f[10]);
            if(count[4]>count[2]||count[5]>count[3]||count[6]>count[4]||count[7]>count[5]||count[10]>count[6])throw new IOException("Inconsistent original MBV counters");
            for(int i=8;i<=9;i++){
                long denominator=count[i-4],numerator=count[i-2];
                if(denominator==0){if(text(r,"edition").equals("1.1")||!f[i].equalsIgnoreCase("nan")&&!f[i].equalsIgnoreCase("-nan"))throw new IOException("Undefined MBV percentage differs from original edition behavior");undefined[0]++;}
                else {double expected=text(r,"edition").equals("1.1")?(double)(numerator/denominator):numerator*1.0/denominator;if(Math.abs(probability(f[i])-expected)>1.1e-6)throw new IOException("MBV percentage differs from retained counts and exact edition division semantics");}
            }
        });
        if(samples.isEmpty())throw new IOException("No MBV sample result");
        return Map.of("rows",samples.size(),"undefined_source_percentage_cells",undefined[0],"raw_NaN_retained",undefined[0]>0,"legacy_integer_division",text(r,"edition").equals("1.1"));
    }
    private static void quan(Path root,Map<String,Object> r,Set<String> names,Map<String,Object> exports)throws IOException {
        var p=object(r.get("parameters"),"parameters");var sampleNames=list(p.get("sample_names"),"sample_names");var wanted=new HashSet<String>(Set.of("gene.count","exon.count"));if(Boolean.TRUE.equals(p.get("rpkm")))wanted.addAll(Set.of("gene.rpkm","exon.rpkm"));if(Boolean.TRUE.equals(p.get("tpm")))wanted.addAll(Set.of("gene.tpm","exon.tpm"));
        var seen=new HashSet<String>();int[] stats={0};String prefix=null;
        for(String name:names) {
            String currentPrefix=name.replaceFirst("\\.(?:gene|exon)\\.(?:count|rpkm|tpm)\\.bed$|\\.stats$","");
            if(prefix==null)prefix=currentPrefix;else if(!prefix.equals(currentPrefix))throw new IOException("Quan exports have inconsistent option/GTF hashes");
            boolean unhashed=text(r,"edition").equals("1.1")||text(p,"filename_hash").equals("none");if(unhashed?!currentPrefix.equals("result"):!currentPrefix.matches("result\\.[A-Za-z0-9]{1,11}"))throw new IOException("Quan output hash presence differs from selected edition/options");
            var match=java.util.regex.Pattern.compile("result(?:\\.[A-Za-z0-9_-]+)?\\.(gene|exon)\\.(count|rpkm|tpm)\\.bed").matcher(name);
            if(match.matches()){
                String kind=match.group(1)+"."+match.group(2);if(!wanted.contains(kind)||!seen.add(kind))throw new IOException("Unexpected or repeated quan table");long[] rows={0},nonzero={0};
                scan(root.resolve("outputs/"+name),r,f->{width(f,6+sampleNames.size());if(rows[0]++==0){String fifth=kind.startsWith("gene")?(text(r,"edition").equals("1.1")?"length":"info"):"geneID";String fourth=kind.startsWith("gene")?"gene":"exon";if(!String.join(" ",java.util.Arrays.copyOf(f,6)).equals("#chr start end "+fourth+" "+fifth+" strand"))throw new IOException("Unexpected edition-specific quan header");for(int i=0;i<sampleNames.size();i++)if(!f[i+6].equals(sampleNames.get(i)))throw new IOException("Quan sample labels/order differ");return;}identity(f[0]);nonnegativeInteger(f[1]);positiveInteger(f[2]);identity(f[3]);identity(f[4]);strand(f[5]);for(int i=6;i<f.length;i++)if(nonnegative(f[i])>0)nonzero[0]++;});
                if(rows[0]==0)throw new IOException("No complete quan table header");exports.put(name,Map.of("rows",rows[0]-1,"sample_columns",sampleNames.size(),"nonzero_count_cells",nonzero[0]));
            }else if(name.matches("result(?:\\.[A-Za-z0-9_-]+)?\\.stats")) {
                stats[0]++;exports.put(name,quanStats(root.resolve("outputs/"+name),r,sampleNames));
            }else throw new IOException("Undeclared quan output file: "+name);
        }
        if(!seen.equals(wanted)||stats[0]!=1)throw new IOException("Incomplete quan table/stats population");
    }
    private static Map<String,Object> quanStats(Path path,Map<String,Object> r,List<?> samples)throws IOException {
        if(text(r,"edition").equals("1.1")){
            long[] rows={0};var columns=new ArrayList<String>();var seen=new HashSet<String>();
            scan(path,r,f->{width(f,10);if(rows[0]++==0){if(!f[0].equals("sample")||!f[1].equals("unmmaped_in_genic_regions")||!f[2].equals("duplicate_reads_in_genic_regions")||!f[4].equals("unpaired_in_genic_regions")||!f[6].equals("good_reads_in_genic_regions")||!f[7].equals("not_exonic_in_genic_regions")||!f[8].equals("exonic_in_genic_regions")||!f[9].equals("total_reads_in_genic_regions"))throw new IOException("Unexpected legacy1.1 quan stats header");var parameters=object(r.get("parameters"),"parameters");if(!f[3].equals("fails_mapQ_in_genic_regions:"+parameters.get("mapping_quality")))throw new IOException("Legacy quan mapQ label differs");String[] mismatch=f[5].split(":");if(mismatch.length!=3||!mismatch[0].equals("fails_mismatch_in_genic_regions"))throw new IOException("Legacy mismatch header differs");printedSetting(mismatch[1],parameters.get("mismatch"));printedSetting(mismatch[2],parameters.get("mismatch_total"));columns.addAll(List.of(f));return;}if(!samples.contains(f[0])||!seen.add(f[0]))throw new IOException("Unexpected legacy quan sample");for(int i=1;i<f.length;i++)nonnegativeInteger(f[i]);});
            if(!seen.equals(new HashSet<>(samples)))throw new IOException("Incomplete legacy quan stats samples");return Map.of("sample_rows",seen.size(),"header",columns,"legacy_wide_stats",true);
        }
        var values=new LinkedHashMap<String,String[]>();scan(path,r,f->{width(f,1+samples.size());if(values.put(f[0],java.util.Arrays.copyOfRange(f,1,f.length))!=null)throw new IOException("Repeated quan statistic");});
        var expected=new HashSet<String>(List.of("sample","total_reads","total_good","total_exonic","total_exonic_multi_counting","total_exonic_multi_counting_after_merge_(used_for_rpkm)","total_merged_reads","good_over_total","exonic_over_total","exonic_over_good"));var parameters=object(r.get("parameters"),"parameters");boolean modern=text(r,"edition").equals("1.3.1");
        expected.addAll(modern?List.of("filtered_secondary_alignments_(does_not_count_towards_total_reads)","filtered_unmapped","filtered_failqc","filtered_duplicate","filtered_notpaired","filtered_unmatched_mate_pairs"):List.of("total_secondary_alingments","total_unmapped","total_failqc","total_duplicate","total_notpaired"));
        expected.add((modern?"filtered":"total")+"_mapQ_less_than_"+parameters.get("mapping_quality"));String mismatchPrefix=(modern?"filtered":"total")+"_mismatches_greater_than_";String mismatchKey=null;for(String key:values.keySet())if(key.startsWith(mismatchPrefix)){if(mismatchKey!=null)throw new IOException("Repeated mismatch statistic");mismatchKey=key;}
        if(mismatchKey==null)throw new IOException("Missing source mismatch statistic");String[] settings=mismatchKey.substring(mismatchPrefix.length()).split("_");if(settings.length!=2)throw new IOException("Invalid mismatch statistic label");printedSetting(settings[0],parameters.get("mismatch"));printedSetting(settings[1],parameters.get("mismatch_total"));expected.add(mismatchKey);if(!values.keySet().equals(expected))throw new IOException("Incomplete or undeclared edition-specific quan statistics");
        if(!values.containsKey("sample")||!values.containsKey("total_reads"))throw new IOException("Incomplete quan stats");for(int i=0;i<samples.size();i++)if(!samples.get(i).equals(values.get("sample")[i]))throw new IOException("Quan stats sample labels differ");
        long undefined=0;for(var entry:values.entrySet()){if(entry.getKey().equals("sample"))continue;for(int i=0;i<samples.size();i++){String value=entry.getValue()[i];if(value.equalsIgnoreCase("nan")||value.equalsIgnoreCase("-nan")){String denominator=switch(entry.getKey()){case "good_over_total","exonic_over_total"->"total_reads";case "exonic_over_good"->"total_good";default->throw new IOException("Unexpected undefined quan statistic");};if(!values.containsKey(denominator)||number(values.get(denominator)[i])!=0)throw new IOException("Unexplained undefined quan ratio");undefined++;}else nonnegative(value);}}
        return Map.of("statistic_rows",values.size(),"undefined_source_diagnostic_ratio_cells",undefined);
    }
    static void provenance(Path root,Map<String,Object> r,Map<String,Object> manifest)throws IOException {
        if(text(r,"engine").equals("native")){if(manifest.containsKey("process_exit_code")||Files.exists(root.resolve("worker/owned.json")))throw new IOException("Native capture has unexpected original-process provenance");return;}
        if(!Long.valueOf(0).equals(manifest.get("process_exit_code"))&&!Integer.valueOf(0).equals(manifest.get("process_exit_code")))throw new IOException("Original did not exit successfully");
        boolean[] complete={false};QtltoolsWorkflowFiles.lines(root.resolve("worker/stdout.log"),outputLimits(r),line->{if(line.matches("Running time: [0-9]+(?:\\.[0-9]+)? seconds\\s*"))complete[0]=true;return true;});if(!complete[0])throw new IOException("Original lacks its complete execution marker;exit0 parse/help errors are not results");
        var closure=read(root.resolve("worker/owned.closed.json"));if(!Boolean.TRUE.equals(closure.get("complete_cleanup"))||!list(closure.get("live_writers_after"),"live writers").isEmpty())throw new IOException("Owned original group remains live");
        if(!Boolean.TRUE.equals(closure.get("original_wait_completed"))||!same(closure.get("original_exit_code"),0L))throw new IOException("Original child wait/exit record contradicts the admitted successful wrapper status");
        var supervisor=read(root.resolve("worker/supervisor.closed.json"));if(!Boolean.TRUE.equals(supervisor.get("supervisor_finished"))||!Boolean.FALSE.equals(supervisor.get("cancellation_requested"))||Files.exists(root.resolve("worker/cancel.request.json")))throw new IOException("Owned supervisor did not finish normally or cancellation was requested");
        var spec=read(root.resolve("worker/spec.json"));var ownership=read(root.resolve("worker/owned.json"));var supervisorOwner=read(root.resolve("worker/supervisor.owned.json"));if(!Objects.equals(spec.get("token"),ownership.get("token"))||!Objects.equals(spec.get("token"),supervisor.get("token"))||!Objects.equals(spec.get("token"),supervisorOwner.get("token"))||!same(ownership.get("pid"),ownership.get("group"))||!same(ownership.get("pid"),ownership.get("session"))||!same(supervisorOwner.get("pid"),supervisor.get("pid"))||!same(supervisorOwner.get("start"),supervisor.get("start")))throw new IOException("Owned original/supervisor identity binding differs");
        SeuratDifferentialContracts.integer(ownership,"pid",0,2,Integer.MAX_VALUE);SeuratDifferentialContracts.integer(supervisorOwner,"pid",0,2,Integer.MAX_VALUE);
        if(!same(ownership.get("launcher_pid"),supervisorOwner.get("pid"))||!same(supervisorOwner.get("pid"),supervisorOwner.get("group"))||!same(supervisorOwner.get("pid"),supervisorOwner.get("session"))||!same(closure.get("owned_group"),ownership.get("group"))||!Objects.equals(spec.get("worker"),spec.get("working_directory")))throw new IOException("Owned child,parent,closure or working-directory binding differs");
        for(Object value:list(closure.get("before"),"owned closure members")){var member=object(value,"owned member");if(!same(member.get("group"),ownership.get("group"))||!same(member.get("session"),ownership.get("session")))throw new IOException("Closure receipt contains a foreign process group/session");}
        var before=read(root.resolve("worker/runtime-capture.json"));var after=read(root.resolve("worker/runtime-after.json"));
        if(!Boolean.TRUE.equals(after.get("unchanged"))||!same(before.get("executable"),after.get("executable"))||!same(before.get("libraries"),after.get("libraries")))throw new IOException("Runtime/library identity changed");
        var profile=object(object(QtltoolsWorkflowRuntime.profiles().get("profiles"),"profiles").get(text(r,"edition")),"profile");var binary=object(before.get("executable"),"executable");if(!profile.get("sha256").equals(binary.get("sha256"))||!profile.get("bytes").equals(binary.get("bytes")))throw new IOException("Original binary differs from selected profile");
        var environment=object(before.get("environment"),"original environment");for(var entry:Map.of("LANG","C","LC_ALL","C","OMP_NUM_THREADS","1","OPENBLAS_NUM_THREADS","1","MKL_NUM_THREADS","1").entrySet())if(!entry.getValue().equals(environment.get(entry.getKey())))throw new IOException("Original declared locale/thread environment differs");
        var directories=list(spec.get("library_directories"),"library directories");String libraryPath=String.join(":",directories.stream().map(Object::toString).toList());if(directories.isEmpty()?environment.containsKey("LD_LIBRARY_PATH"):!libraryPath.equals(environment.get("LD_LIBRARY_PATH")))throw new IOException("Original library-directory binding differs");
        for(Object sha:list(profile.get("critical_library_sha256"),"critical libraries")){boolean found=false;for(Object library:list(before.get("libraries"),"libraries"))if(sha.equals(object(library,"library").get("sha256")))found=true;if(!found)throw new IOException("Missing qualified scientific-library identity");}
        var command=read(root.resolve("worker/command.json"));var args=list(command.get("original_arguments"),"arguments");if(args.isEmpty()||!same(args.subList(1,args.size()),QtltoolsWorkflowRuntime.arguments(r)))throw new IOException("Original arguments differ from typed request");
        if(!same(spec.get("command"),args)||!same(ownership.get("command"),args)||!same(spec.get("profile"),profile))throw new IOException("Owned original command/profile binding differs");
        if(!text(r,"edition").equals(command.get("edition"))||!text(r,"operation").equals(command.get("operation")))throw new IOException("Original command edition/operation differs");
    }
    private static void requireNames(Set<String> actual,Set<String> expected)throws IOException {if(!actual.equals(expected))throw new IOException("Incomplete or undeclared original output file population");}
    private static Map<String,Object> outputLimits(Map<String,Object> r)throws IOException {var limits=new LinkedHashMap<>(object(r.get("limits"),"limits"));limits.put("maximum_input_bytes",limits.get("maximum_output_bytes"));var bounded=new LinkedHashMap<>(r);bounded.put("limits",limits);return bounded;}
    private static void printedSetting(String value,Object requested)throws IOException {double actual=number(value),expected=((Number)requested).doubleValue();if(Math.abs(actual-expected)>Math.abs(expected)*1e-6+1e-15)throw new IOException("Original printed filter setting differs from declared request");}
    private static void width(String[] f,int n)throws IOException {if(f.length!=n)throw new IOException("Original output schema requires "+n+" columns;found "+f.length);}
    private static void strand(String value)throws IOException {if(!value.equals("+")&&!value.equals("-"))throw new IOException("Invalid original strand");}
    private static long integer(String value)throws IOException {try{return Long.parseLong(value);}catch(NumberFormatException invalid){throw new IOException("Invalid original integer",invalid);}}
    private static long nonnegativeInteger(String value)throws IOException {long n=integer(value);if(n<0)throw new IOException("Negative original counter/coordinate");return n;}
    private static long positiveInteger(String value)throws IOException {long n=integer(value);if(n<=0)throw new IOException("Nonpositive original count/coordinate");return n;}
    private static double number(String value)throws IOException {try{double n=Double.parseDouble(value);if(!Double.isFinite(n))throw new IOException("Nonfinite original fitted result;retain raw exports;no complete result admitted");return n;}catch(NumberFormatException invalid){throw new IOException("Invalid original numeric export",invalid);}}
    private static double nonnegative(String value)throws IOException {double n=number(value);if(n<0)throw new IOException("Negative original nonnegative statistic");return n;}
    private static double positive(String value)throws IOException {double n=number(value);if(n<=0)throw new IOException("Nonpositive original fitted parameter");return n;}
    private static double probability(String value)throws IOException {double n=nonnegative(value);if(n>1)throw new IOException("Original probability/proportion outside[0,1]");return n;}
    private static void bit(String value)throws IOException {if(!value.equals("0")&&!value.equals("1"))throw new IOException("Invalid original boolean indicator");}
}
