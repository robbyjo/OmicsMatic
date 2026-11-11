/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import org.jlinalg.sequence.FastpReadFilter;

/** Closed typed decoders shared by retained-request auditing and future recipe admission. */
final class FastpChoices {
    private FastpChoices() { }
    static FastpParameters.Options options(Object value)throws IOException {
        Map<?,?> m=map(value,Set.of("threads","adapters","global","cutting","filters","polyG","polyGMinimum","polyX","polyXMinimum","correction","duplication","overlap","storeUnpaired"));
        Map<?,?> a=map(m.get("adapters"),Set.of("enabled","detectPaired","read1","read2")),g=map(m.get("global"),Set.of("front1","tail1","front2","tail2","maximum1","maximum2")),c=map(m.get("cutting"),Set.of("front","right","tail")),d=map(m.get("duplication"),Set.of("evaluate","deduplicate","accuracy")),o=map(m.get("overlap"),Set.of("minimumLength","maximumDifferences","maximumDifferencePercent"));
        try{return new FastpParameters.Options(i(m,"threads"),new FastpParameters.Adapters(b(a,"enabled"),b(a,"detectPaired"),text(a,"read1"),text(a,"read2")),new FastpParameters.Global(i(g,"front1"),i(g,"tail1"),i(g,"front2"),i(g,"tail2"),i(g,"maximum1"),i(g,"maximum2")),new FastpParameters.Cutting(cut(c.get("front")),cut(c.get("right")),cut(c.get("tail"))),filters(m.get("filters")),FastpParameters.PolyG.valueOf(text(m,"polyG")),i(m,"polyGMinimum"),b(m,"polyX"),i(m,"polyXMinimum"),b(m,"correction"),new FastpParameters.Duplication(b(d,"evaluate"),b(d,"deduplicate"),i(d,"accuracy")),new FastpParameters.Overlap(i(o,"minimumLength"),i(o,"maximumDifferences"),i(o,"maximumDifferencePercent")),b(m,"storeUnpaired"));}
        catch(IllegalArgumentException invalid){throw new IOException("Invalid explicit fastp option value",invalid);}
    }
    static FastpReadFilter.Filters filters(Object value)throws IOException {
        Map<?,?> m=map(value,Set.of("qualityEnabled","qualifiedPhred","unqualifiedPercent","minimumAveragePhred","maximumNs","lengthEnabled","minimumLength","maximumLength","complexityEnabled","minimumComplexityPercent"));
        return new FastpReadFilter.Filters(b(m,"qualityEnabled"),i(m,"qualifiedPhred"),i(m,"unqualifiedPercent"),i(m,"minimumAveragePhred"),i(m,"maximumNs"),b(m,"lengthEnabled"),i(m,"minimumLength"),i(m,"maximumLength"),b(m,"complexityEnabled"),i(m,"minimumComplexityPercent"));
    }
    static FastpReadFilter.Cut cut(Object value)throws IOException {Map<?,?> m=map(value,Set.of("enabled","window","meanPhred"));return new FastpReadFilter.Cut(b(m,"enabled"),i(m,"window"),i(m,"meanPhred"));}
    static FastpFastq.Limits fastq(Object value)throws IOException {Map<?,?> m=map(value,Set.of("encodedBytes","decodedCharacters","reads","bases","readCharacters","headerCharacters"));return new FastpFastq.Limits(n(m,"encodedBytes"),n(m,"decodedCharacters"),n(m,"reads"),n(m,"bases"),i(m,"readCharacters"),i(m,"headerCharacters"));}
    static FastpProcess.Limits process(Object value)throws IOException {Map<?,?> m=map(value,Set.of("wallMillis","addressSpaceBytes","cpuSeconds","fileBytes","openFiles","workspaceBytes","fileCount","processes","threads","sampledRssBytes","monitorMillis"));return new FastpProcess.Limits(n(m,"wallMillis"),n(m,"addressSpaceBytes"),n(m,"cpuSeconds"),n(m,"fileBytes"),i(m,"openFiles"),n(m,"workspaceBytes"),i(m,"fileCount"),i(m,"processes"),i(m,"threads"),n(m,"sampledRssBytes"),i(m,"monitorMillis"));}
    static FastpOriginalStage.Limits stage(Object value)throws IOException {Map<?,?> m=map(value,Set.of("fastq","process","bundleBytes","bundleFiles"));return new FastpOriginalStage.Limits(fastq(m.get("fastq")),process(m.get("process")),n(m,"bundleBytes"),i(m,"bundleFiles"));}
    static Map<?,?> map(Object value,Set<String> keys)throws IOException {Map<?,?> m=FastpRuntime.object(value);FastpMetrics.exactKeys(m,keys,"typed choices");return m;}
    static String text(Map<?,?> m,String key)throws IOException {return FastpRuntime.text(m.get(key));}
    static long n(Map<?,?> m,String key)throws IOException {return FastpRuntime.integer(m.get(key),0,Long.MAX_VALUE);}
    static int i(Map<?,?> m,String key)throws IOException {return Math.toIntExact(FastpRuntime.integer(m.get(key),0,Integer.MAX_VALUE));}
    static boolean b(Map<?,?> m,String key)throws IOException {if(!(m.get(key) instanceof Boolean value))throw new IOException("Require explicit fastp boolean: "+key);return value;}
}
