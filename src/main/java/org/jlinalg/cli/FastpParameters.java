/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jlinalg.sequence.FastpReadFilter;

/** Typed original flags and explicit duplicate-memory admission; no free-form options or command text. */
final class FastpParameters {
    private FastpParameters() { }
    enum PolyG { SOURCE_AUTO, ENABLE, DISABLE }
    record Adapters(boolean enabled,boolean detectPaired,String read1,String read2) {
        Adapters {
            Objects.requireNonNull(read1);Objects.requireNonNull(read2);
            for(String value:List.of(read1,read2))if(!value.isEmpty()&&!value.equals("auto")&&(value.length()<4||value.length()>10000||!value.matches("[ACGT]+")))throw new IllegalArgumentException("fastp adapters require auto,empty,or4..10000 uppercase A/C/G/T bases");
            if(!enabled&&(detectPaired||!read1.isEmpty()||!read2.isEmpty()))throw new IllegalArgumentException("Disabled fastp adapter trimming cannot silently ignore supplied adapter choices");
        }
        static Adapters defaults(){return new Adapters(true,false,"","");}
    }
    record Global(int front1,int tail1,int front2,int tail2,int maximum1,int maximum2) {
        Global {
            if(front1<0||front1>30||front2<0||front2>30||tail1<0||tail1>100||tail2<0||tail2>100||maximum1<0||maximum2<0)throw new IllegalArgumentException("Invalid explicit fastp global trimming");
        }
        static Global defaults(){return new Global(0,0,0,0,0,0);}
    }
    record Cutting(FastpReadFilter.Cut front,FastpReadFilter.Cut right,FastpReadFilter.Cut tail) {
        Cutting {Objects.requireNonNull(front);Objects.requireNonNull(right);Objects.requireNonNull(tail);}
        static Cutting defaults(){return new Cutting(FastpReadFilter.Cut.disabled(),FastpReadFilter.Cut.disabled(),FastpReadFilter.Cut.disabled());}
    }
    record Duplication(boolean evaluate,boolean deduplicate,int accuracy) {
        Duplication {if(accuracy<0||accuracy>6||!evaluate&&deduplicate||!evaluate&&accuracy!=0)throw new IllegalArgumentException("fastp duplication requires explicit consistent evaluation/deduplication and accuracy0(default)..6");}
        static Duplication defaults(){return new Duplication(true,false,0);}
    }
    record Overlap(int minimumLength,int maximumDifferences,int maximumDifferencePercent) {
        Overlap {if(minimumLength<1||minimumLength>10_000_000||maximumDifferences<0||maximumDifferences>10_000_000||maximumDifferencePercent<0||maximumDifferencePercent>100)throw new IllegalArgumentException("Invalid fastp overlap constraints");}
        static Overlap defaults(){return new Overlap(30,5,20);}
    }
    record Options(int threads,Adapters adapters,Global global,Cutting cutting,FastpReadFilter.Filters filters,
                   PolyG polyG,int polyGMinimum,boolean polyX,int polyXMinimum,boolean correction,
                   Duplication duplication,Overlap overlap,boolean storeUnpaired) {
        Options {
            if(threads<1||threads>16||polyGMinimum<1||polyGMinimum>10000||polyXMinimum<1||polyXMinimum>10000)throw new IllegalArgumentException("fastp requires1..16 explicit threads and1..10000 poly-tail minimum lengths");
            Objects.requireNonNull(adapters);Objects.requireNonNull(global);Objects.requireNonNull(cutting);Objects.requireNonNull(filters);Objects.requireNonNull(polyG);Objects.requireNonNull(duplication);Objects.requireNonNull(overlap);
        }
        static Options defaults(int threads){return new Options(threads,Adapters.defaults(),Global.defaults(),Cutting.defaults(),FastpReadFilter.Filters.defaults(),PolyG.SOURCE_AUTO,10,false,10,false,Duplication.defaults(),Overlap.defaults(),false);}
    }
    record Memory(long bloomBufferBytes,long minimumAddressSpaceBytes,long minimumSampledRssBytes,String interpretation) { }
    static Memory memory(String version,Duplication duplication)throws IOException {
        if(!List.of("0.22.0","0.23.2","0.25.0","1.0.1").contains(version))throw new FastpRuntime.Unavailable("Unknown fastp edition; its memory policy cannot be inferred");
        if(version.equals("0.22.0")&&duplication.accuracy()!=0)throw new FastpRuntime.Unavailable("fastp0.22.0 has no configurable dup_calc_accuracy;do not silently ignore or substitute the requested accuracy");
        long buffer=0;
        if(duplication.evaluate()){
            int level=duplication.accuracy()==0?(duplication.deduplicate()?3:1):duplication.accuracy();
            int gib=version.equals("0.22.0")?(duplication.deduplicate()?4:1):new int[]{0,1,2,4,8,16,24}[level];buffer=gib*1_073_741_824L;
        }
        return new Memory(buffer,Math.addExact(buffer,1_073_741_824L),Math.addExact(buffer,536_870_912L),"Source Bloom buffer plus a minimum explicit overhead reservation;this is not an upper bound or a whole-host-memory guarantee. Hard/process and sampled aggregate checks still apply.");
    }
    static void requireCapacity(String version,Options options,FastpProcess.Limits capacity)throws IOException {
        Memory memory=memory(version,options.duplication());
        if(capacity.addressSpaceBytes()<memory.minimumAddressSpaceBytes()||capacity.sampledRssBytes()<memory.minimumSampledRssBytes())throw new FastpRuntime.Unavailable("Skip fastp analysis:requested duplicate settings require "+memory.bloomBufferBytes()+" Bloom-buffer bytes plus overhead,above the explicit AS/RSS capacities. Increase permitted capacities/use an adequate host;never silently lower accuracy.");
        if(capacity.threads()<options.threads()+4)throw new IOException("fastp worker plus reader/writer/monitor thread capacity is insufficient");
    }
    /** Only internal owned paths and typed fields enter this fixed flag vocabulary. */
    static List<String> arguments(String version,boolean paired,FastpFastq.Encoding encoding,Options options,String input1,String input2)throws IOException {
        Objects.requireNonNull(encoding);memory(version,options.duplication());
        if(!paired&&(options.correction()||options.adapters().detectPaired()||!options.adapters().read2().isEmpty()||options.storeUnpaired()))throw new IOException("Paired-only fastp choices cannot silently execute on single-end input");
        var result=new ArrayList<String>(List.of("--in1",input1,"--out1","read1.fastq","--failed_out","failed.fastq","--json","fastp.json","--html","fastp.html","--dont_overwrite","--thread",Integer.toString(options.threads())));
        if(paired){result.addAll(List.of("--in2",input2,"--out2","read2.fastq"));if(options.storeUnpaired())result.addAll(List.of("--unpaired1","unpaired1.fastq","--unpaired2","unpaired2.fastq"));}
        if(encoding==FastpFastq.Encoding.PHRED64)result.add("--phred64");
        if(!options.adapters().enabled())result.add("--disable_adapter_trimming");
        if(options.adapters().detectPaired())result.add("--detect_adapter_for_pe");
        if(!options.adapters().read1().isEmpty())flag(result,"adapter_sequence",options.adapters().read1());if(paired&&!options.adapters().read2().isEmpty())flag(result,"adapter_sequence_r2",options.adapters().read2());
        var g=options.global();flag(result,"trim_front1",g.front1());flag(result,"trim_tail1",g.tail1());flag(result,"max_len1",g.maximum1());
        if(paired){flag(result,"trim_front2",g.front2());flag(result,"trim_tail2",g.tail2());flag(result,"max_len2",g.maximum2());}
        for(var row:List.of(new CutFlag("front",options.cutting().front()),new CutFlag("right",options.cutting().right()),new CutFlag("tail",options.cutting().tail()))){
            if(row.cut().enabled())result.add("--cut_"+row.name());flag(result,"cut_"+row.name()+"_window_size",row.cut().window());flag(result,"cut_"+row.name()+"_mean_quality",row.cut().meanPhred());
        }
        var f=options.filters();if(!f.qualityEnabled())result.add("--disable_quality_filtering");if(!f.lengthEnabled())result.add("--disable_length_filtering");if(f.complexityEnabled())result.add("--low_complexity_filter");
        flag(result,"qualified_quality_phred",f.qualifiedPhred());flag(result,"unqualified_percent_limit",f.unqualifiedPercent());flag(result,"average_qual",f.minimumAveragePhred());flag(result,"n_base_limit",f.maximumNs());flag(result,"length_required",f.minimumLength());flag(result,"length_limit",f.maximumLength());flag(result,"complexity_threshold",f.minimumComplexityPercent());
        if(options.polyG()==PolyG.ENABLE)result.add("--trim_poly_g");if(options.polyG()==PolyG.DISABLE)result.add("--disable_trim_poly_g");flag(result,"poly_g_min_len",options.polyGMinimum());
        if(options.polyX())result.add("--trim_poly_x");flag(result,"poly_x_min_len",options.polyXMinimum());if(options.correction())result.add("--correction");
        flag(result,"overlap_len_require",options.overlap().minimumLength());flag(result,"overlap_diff_limit",options.overlap().maximumDifferences());flag(result,"overlap_diff_percent_limit",options.overlap().maximumDifferencePercent());
        if(!options.duplication().evaluate())result.add("--dont_eval_duplication");if(options.duplication().deduplicate())result.add("--dedup");if(options.duplication().accuracy()>0)flag(result,"dup_calc_accuracy",options.duplication().accuracy());
        return List.copyOf(result);
    }
    private record CutFlag(String name,FastpReadFilter.Cut cut) { }
    private static void flag(List<String> args,String name,Object value){args.add("--"+name);args.add(value.toString());}
}
