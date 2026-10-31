package org.jlinalg.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

final class HomerGenomeFixture {
    private HomerGenomeFixture() { }
    static Map<String,String> options(Path root)throws Exception {
        Files.createDirectories(root);var o=new LinkedHashMap<String,String>();
        Files.writeString(root.resolve("genome α.fa"),">chrα original header with spaces\n"+"ACGT".repeat(5000)+"\n");
        Files.writeString(root.resolve("targets.bed"),"chrα\t1000\t1120\tp α\t1\t+\nchrα\t1500\t1620\tminus\t1\t-\n");
        Files.writeString(root.resolve("background.bed"),"chrα\t5000\t5120\tb1\t1\t+\nchrα\t5500\t5620\tb2\t1\t-\nchrα\t6000\t6120\tb3\t1\t+\nchrα\t6500\t6620\tb4\t1\t+\n");
        Files.writeString(root.resolve("model.motif"),">ACGTAC\tmodel α\t5\n.97\t.01\t.01\t.01\n.01\t.97\t.01\t.01\n.01\t.01\t.97\t.01\n.01\t.01\t.01\t.97\n.97\t.01\t.01\t.01\n.01\t.97\t.01\t.01\n");
        for(var pair:new String[][]{{"home",root.resolve("not-installed").toString()},{"profile","linux-x86_64-genome-5.1"},{"out",root.resolve("output").toString()},{"genome",root.resolve("genome α.fa").toString()},{"reference-build","synthetic exact"},{"peaks",root.resolve("targets.bed").toString()},{"background",root.resolve("background.bed").toString()},{"known-motifs",root.resolve("model.motif").toString()},{"comparison-motifs",root.resolve("model.motif").toString()},{"peak-format","bed6"},{"background-format","bed6"},{"background-mode","supplied"},{"size","given"},{"unknown-strand","error"},{"duplicate-ids","error"},{"alphabet","strict-dna"},{"mask-lowercase","no"},{"background-overlap","retain"},{"weighting","none"},{"strands","both"},{"statistic","binomial"},{"normalization-order","0"},{"motifs-to-find","1"},{"motif-length","6"},{"mismatches","0"}})o.put(pair[0],pair[1]);return o;
    }
    static HomerGenomeData.Data stage(Map<String,String> o,Path work)throws Exception{Files.createDirectory(work);return HomerGenomeData.stage(o,HomerGenomeCli.Plan.read(o),HomerGenomeData.Limits.read(o),new HomerGenomeRuntime.Context(work,"test","5.1","test",Map.of(),Map.of()),work);}
}
