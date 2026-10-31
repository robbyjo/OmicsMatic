package org.jlinalg.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class HomerNamedFixture {
    private HomerNamedFixture() { }
    static Map<String,String> options(Path root)throws Exception {
        Path peaks=root.resolve("peaks.bed");Files.writeString(peaks,"chr1\t104\t115\tpα\t1\t+\n");var o=new LinkedHashMap<String,String>();o.put("home",root.resolve("missing-home").toString());o.put("profile",HomerNamedRuntime.profile());o.put("out",root.resolve("output").toString());o.put("peaks",peaks.toString());o.put("genome","mm10");o.put("reference-build","mm10");o.put("annotation-release","v7.0");o.put("peak-format","bed6");o.put("peak-unknown-strand","error");o.put("duplicate-ids","error");o.put("resource-bounds","error");o.put("tss-resource-anchors","error");o.put("feature-ties","error");o.put("tss-ties","error");o.put("missing-tss-contig","error");o.put("annotation-stats","original-query-contigs");return o;
    }
    static String[] args(Map<String,String> o){var a=new java.util.ArrayList<String>();o.forEach((k,v)->a.addAll(List.of("--"+k,v)));return a.toArray(String[]::new);}
    static HomerNamedRuntime.Context runtime(Path root)throws Exception {
        Path home=root.resolve("fixture-home"),resources=root.resolve("fixture-resources"),g=resources.resolve("data/genomes/mm10");Files.createDirectories(home);Files.createDirectories(g);Files.writeString(home.resolve(HomerNamedRuntime.MARKER),"fixture-marker");Files.writeString(g.resolve("chrom.sizes"),"chr1\t1000\nchr2\t1000\n");Files.writeString(g.resolve("mm10.basic.annotation"),"exon--1\tchr1\t1\t200\t+\tE\t3\nother\tchr1\t201\t1000\t+\tN\t4\nother2\tchr2\t1\t1000\t+\tN\t4\n");Files.writeString(g.resolve("mm10.full.annotation"),"repeat-HOMER3\tchr1\t1\t200\t-\tLINE\t99\nother\tchr1\t201\t1000\t+\tN\t4\nother2\tchr2\t1\t1000\t+\tN\t4\n");Files.writeString(g.resolve("mm10.tss"),"NM_1.7-HOMER1\tchr1\t-1900\t2100\t0\n");return new HomerNamedRuntime.Context(home,resources,HomerNamedRuntime.profile(),FollowupSupport.hash(home.resolve(HomerNamedRuntime.MARKER)),Map.of(),Map.of(),Map.of());
    }
    static HomerNamedData.Data stage(Map<String,String> o,Path work,HomerNamedRuntime.Context runtime)throws Exception{Files.createDirectory(work);return HomerNamedData.stage(o,work,HomerNamedData.Limits.read(o),runtime);}
    static void output(Path work)throws Exception {
        var labels=List.of("Chr","Start","End","Strand","Peak Score","Focus Ratio/Region Size","Annotation","Detailed Annotation","Distance to TSS","Nearest PromoterID","Entrez ID","Nearest Unigene","Nearest Refseq","Nearest Ensembl","Gene Name","Gene Alias","Gene Description","Gene Type");Files.writeString(work.resolve("original.stdout"),"PeakID (cmd=annotatePeaks.pl stage-peaks.pos mm10 -size given)\t"+String.join("\t",labels)+"\nN000000001\tchr1\t105\t115\t+\t1\tNA\texon\trepeat\t10\tNM_1.7\t998\tNA\tNM_1.7\tNA\tsource-symbol\tsource-alias\tsource-description\tprotein-coding\n");Files.writeString(work.resolve("original.stderr"),"Done annotating peaks file\n");Files.writeString(work.resolve("original-annotation-stats.tsv"),"Annotation\tNumber of peaks\tTotal size\tP\nExon\t1.0\t200\t0\nIntergenic\t0.0\t800\t0\n\nAnnotation\tNumber of peaks\tTotal size\tP\nLINE\t1.0\t200\t0\nIntergenic\t0.0\t800\t0\n");
    }
}
