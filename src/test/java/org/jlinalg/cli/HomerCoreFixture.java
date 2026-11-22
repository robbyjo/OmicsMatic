package org.jlinalg.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

final class HomerCoreFixture {
    private HomerCoreFixture() { }
    static final HomerCoreData.Limits LIMITS=new HomerCoreData.Limits(1000000,1000000,100000,100,10000,100,1000,1000000,1000,16777216,10000);
    static Map<String,String> options(Path root,String mode)throws Exception{
        Files.createDirectories(root);
        Path targets=root.resolve("targets.fa"),background=root.resolve("background.fa"),motifs=root.resolve("motifs.motif");
        Files.writeString(targets,">α source header\nAACCTT\n>second\nCC\n");
        Files.writeString(background,">background1\nGG\n>background2\nTT\n");
        Files.writeString(motifs,">AA\tmodel α\t2.6\t-7\tsource-only\n.97\t.01\t.01\t.01\n.97\t.01\t.01\t.01\n");
        var o=new LinkedHashMap<String,String>();o.put("targets",targets.toString());o.put("motifs",motifs.toString());o.put("strands","both");o.put("alphabet","strict-dna");o.put("duplicate-ids","error");
        if(mode.equals("known")){o.put("background",background.toString());o.put("overlap-policy","error");o.put("statistic","hypergeometric");o.put("normalization","none");}else o.put("offset","0");return o;
    }
    static HomerCoreData.Data data(Path root,String mode)throws Exception{
        var options=options(root.resolve("source"),mode);Path work=root.resolve("work");Files.createDirectory(work);return HomerCoreData.stage(options,mode,work,LIMITS);
    }
    static String knownHeader(){return "Motif Name\tConsensus\tp-value\tLog p-value\tLog2 enrichment ratio\t# target sequences with Motif(total=2)\t% of target sequences with Motif\t# background sequences with Motif(total=2)\t% of background sequences with Motif\n";}
    static String knownRow(){return "M000000001\tN\t1e0\t-0.182\t0.000\t1\t50.00%\t1\t50.00%\n";}
    static String findRows(){return "T000000001\t0\tAA\tM000000001\t+\t2.711670\nT000000001\t5\tTT\tM000000001\t-\t2.711670\n";}
}
