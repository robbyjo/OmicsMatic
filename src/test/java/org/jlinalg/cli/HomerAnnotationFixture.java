package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

final class HomerAnnotationFixture {
    private HomerAnnotationFixture() { }
    static Map<String,String> options(Path root)throws IOException{
        Files.createDirectories(root);Path peaks=root.resolve("peaks.bed"),gtf=root.resolve("genes.gtf"),lengths=root.resolve("lengths.tsv");
        Files.writeString(peaks,"chr1\t10010\t10080\tpα\t7.5\t+\nchr2\t10\t30\tunknown\t0\t-\n",StandardCharsets.UTF_8);
        Files.writeString(gtf,"chr1\tx\texon\t10001\t10100\t.\t+\t.\tgene_id \"GA\"; transcript_id \"A\"; gene_type \"protein_coding\";\nchr1\tx\texon\t10301\t10400\t.\t+\t.\tgene_id \"GA\"; transcript_id \"A\"; gene_type \"protein_coding\";\n");
        Files.writeString(lengths,"chr1\t100000\nchr2\t100000\n");
        var o=new LinkedHashMap<String,String>();o.putAll(Map.of("home",root.resolve("missing-home").toString(),"profile","linux-x86_64-annotation-5.1","out",root.resolve("output").toString(),"peaks",peaks.toString(),"gtf",gtf.toString(),"reference-lengths",lengths.toString(),"peak-format","bed6","reference-build","synthetic","annotation-release","synthetic-v1"));
        o.putAll(Map.of("gtf-id","transcript","gtf-unknown-strand","error","peak-unknown-strand","error","duplicate-ids","error","tss-ties","retain-original","missing-gtf-contig","retain","annotation-stats","omit"));return o;
    }
    static String[] args(Map<String,String> o){return o.entrySet().stream().flatMap(e->java.util.stream.Stream.of("--"+e.getKey(),e.getValue())).toArray(String[]::new);}
    static HomerAnnotationData.Data stage(Map<String,String> o,Path work)throws Exception{Files.createDirectory(work);return HomerAnnotationData.stage(o,work,HomerAnnotationData.Limits.read(o));}
    static void output(Path work){
        try{
            String header="PeakID (cmd=annotatePeaks.pl stage-peaks.pos none)\tChr\tStart\tEnd\tStrand\tPeak Score\tFocus Ratio/Region Size\tAnnotation\tDetailed Annotation\tDistance to TSS\tNearest PromoterID\tEntrez ID\tNearest Unigene\tNearest Refseq\tNearest Ensembl\tGene Name\tGene Alias\tGene Description\tGene Type\n";
            Files.writeString(work.resolve("original.stdout"),header+"H000000001\tchr1\t10011\t10080\t+\t7.5\tNA\tpromoter-TSS (A)\tprotein_coding-promoter-TSS (A)\t44\tA\tGA\tA\t\t\tGA_symbol\t\t\tprotein_coding\nH000000002\tchr2\t11\t30\t-\t0\tNA\tNA\tNA\tNA\tNA\t\t\t\t\t\t\t\t\n");Files.writeString(work.resolve("original.stderr"),"Done annotating peaks file\n");
        }catch(IOException failure){throw new java.io.UncheckedIOException(failure);}
    }
}
