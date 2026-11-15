/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.FilterOutputStream;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jlinalg.sequence.TpmCalculator;
import org.jlinalg.sequence.TpmCalculator.Metrics;

/** Original table layout plus complete, unambiguous raw and semantic native state. */
final class TpmCalculatorProducts {
    private TpmCalculatorProducts() { }
    static final String GENE_HEADER="Gene_Id\tChr\tStart\tEnd\tLength\tReads\tTPM\tExonLength\tExonReads\tExonTPM\tIntronLength\tIntronReads\tIntronTPM\tUniqueLength\tUniqueReads\tUniqueTPM\tUniqueExonLength\tUniqueExonReads\tUniqueExonTPM\tUniqueIntronLength\tUniqueIntronReads\tUniqueIntronTPM";
    static final String TX_HEADER="Gene_Id\tTranscript_Id\tChr\tStart\tEnd\tLength\tReads\tTPM\tExonLength\tExonReads\tExonTPM\tIntronLength\tIntronReads\tIntronTPM";
    static final String FEATURE_HEADER="Gene_Id\tChr\tType\tType_Number\tstart\tend\tLength\tReads\tTPM";
    static final String TX_FEATURE_HEADER="Gene_Id\tTranscript_Id\tChr\tType\tType_Number\tstart\tend\tLength\tReads\tTPM";
    private static final MathContext PRECISION=new MathContext(6,RoundingMode.HALF_EVEN);
    static Map<String,Object> schema(){return Map.of("schema",1,"columns",List.of("kind","sample","chromosome","gene_id","transcript_id","ordinal","type","strand",
        "start","end","length","exon_length","intron_length","unique_length","unique_exon_length","unique_intron_length","processed","data_present",
        "reads","TPM","exon_reads","exon_TPM","intron_reads","intron_TPM","unique_reads","unique_TPM","unique_exon_reads","unique_exon_TPM","unique_intron_reads","unique_intron_TPM","bridge_reads","bridge_TPM"),
        "raw_float_encoding","16 lowercase hexadecimal IEEE754 bits at odd columns19 through31, zero based",
        "semantic_float_encoding","exact hexadecimal finite values and infinities; NaN classification with raw payload/sign retained in raw state");}
    /** C-locale defaultfloat precision6, including source rounded large summary counts. */
    static String sourceNumber(double value) {
        boolean negative=Double.doubleToRawLongBits(value)<0;
        if(Double.isNaN(value))return negative?"-nan":"nan";
        if(Double.isInfinite(value))return negative?"-inf":"inf";
        if(value==0)return negative?"-0":"0";
        BigDecimal n=new BigDecimal(value).round(PRECISION).stripTrailingZeros();int exponent=n.precision()-n.scale()-1;
        if(exponent>=6||exponent< -4)return n.movePointLeft(exponent).toPlainString()+"e"+(exponent<0?"-":"+")+String.format(Locale.ROOT,"%02d",Math.abs(exponent));
        return n.toPlainString();
    }
    private static String tableTpm(double value){return Double.isNaN(value)?"-nan":sourceNumber(value);}
    static final class Budget {
        final long maximum;long used;
        Budget(long maximum){this.maximum=maximum;}
        void reserve(int n)throws IOException {
            if(Thread.currentThread().isInterrupted())throw new IOException("TPMCalculator output recording interrupted");
            if(n>maximum-used)throw new IOException("TPMCalculator product byte capacity exceeded; no complete result");used+=n;
        }
    }
    static BufferedWriter writer(Path file,Budget budget)throws IOException {
        OutputStream stream=new FilterOutputStream(Files.newOutputStream(file,StandardOpenOption.CREATE_NEW)) {
            @Override public void write(int b)throws IOException{budget.reserve(1);out.write(b);}
            @Override public void write(byte[] bytes,int off,int n)throws IOException{budget.reserve(n);out.write(bytes,off,n);}
        };
        return new BufferedWriter(new java.io.OutputStreamWriter(stream,StandardCharsets.UTF_8));
    }
    private static void row(BufferedWriter out,Object... values)throws IOException {
        for(int k=0;k<values.length;k++){if(k>0)out.write('\t');out.write(values[k].toString());}out.write('\n');
    }
    static final class State implements AutoCloseable {
        final BufferedWriter raw,semantic;
        long rows,nonfinite,nonfiniteNaN,infinities;boolean controlIdentifiers;
        State(Path folder,Budget budget)throws IOException {
            raw=writer(folder.resolve("state-raw.jsonl"),budget);
            try{semantic=writer(folder.resolve("state.jsonl"),budget);}catch(IOException e){try{raw.close();}catch(IOException close){e.addSuppressed(close);}throw e;}
        }
        void add(String kind,String sample,String chr,String gene,String tx,int ordinal,String type,char strand,long start,long end,long length,
                 long exon,long intron,long unique,long uniqueExon,long uniqueIntron,boolean processed,boolean present,Metrics m)throws IOException {
            for(String value:List.of(sample,chr,gene,tx))if(value.chars().anyMatch(c->c<32||c==127))controlIdentifiers=true;
            var base=new ArrayList<Object>(List.of(kind,sample,chr,gene,tx,ordinal,type,String.valueOf(strand),start,end,length,exon,intron,unique,uniqueExon,uniqueIntron,processed,present));
            long[] counts={m.reads(),m.exonReads(),m.intronReads(),m.uniqueReads(),m.uniqueExonReads(),m.uniqueIntronReads(),m.bridgeReads()};
            double[] values={m.tpm(),m.exonTpm(),m.intronTpm(),m.uniqueTpm(),m.uniqueExonTpm(),m.uniqueIntronTpm(),m.bridgeTpm()};
            var exact=new ArrayList<>(base);var canonical=new ArrayList<>(base);
            for(int k=0;k<counts.length;k++) {
                exact.add(counts[k]);canonical.add(counts[k]);long bits=Double.doubleToRawLongBits(values[k]);
                exact.add(HexFormat.of().toHexDigits(bits));canonical.add(Double.isNaN(values[k])?"NaN":Double.toHexString(values[k]));
                if(!Double.isFinite(values[k])){nonfinite++;if(Double.isNaN(values[k]))nonfiniteNaN++;else infinities++;}
            }
            raw.write(NextflowJson.json(exact));raw.write('\n');semantic.write(NextflowJson.json(canonical));semantic.write('\n');rows++;
        }
        void features(String kind,String sample,String chr,String gene,String tx,List<TpmCalculator.Feature> features)throws IOException {
            int n=0;for(var f:features)add(kind,sample,chr,gene,tx,++n,f.type(),f.strand(),f.start(),f.end(),f.length(),0,0,0,0,0,false,f.dataPresent(),f.metrics());
        }
        @Override public void close()throws IOException {try{raw.close();}finally{semantic.close();}}
    }
    static Map<String,Object> write(TpmCalculator.Result result,TpmCalculatorRequest.Request request,Path folder)throws IOException {
        Files.createDirectory(folder);var budget=new Budget(request.limits().bundleBytes());var samples=new ArrayList<Object>();
        try(var output=writer(folder.resolve("STATE-SCHEMA.json"),budget)){output.write(NextflowJson.json(schema()));output.write('\n');}
        try(var state=new State(folder,budget)) {
            int index=0;
            for(var sample:result.samples()) {
                String member=String.format(Locale.ROOT,"sample-%06d",++index);Path dir=Files.createDirectory(folder.resolve(member));
                tables(sample,request,dir,budget);
                for(var g:sample.genes()) {
                    state.add("gene",sample.name(),g.chromosome(),g.id(),"-",0,"gene",g.strand(),g.start(),g.end(),g.length(),g.exonLength(),g.intronLength(),g.uniqueLength(),g.uniqueExonLength(),g.uniqueIntronLength(),g.processed(),g.dataPresent(),g.metrics());
                    state.features("gene-feature",sample.name(),g.chromosome(),g.id(),"-",g.features());state.features("gene-unique",sample.name(),g.chromosome(),g.id(),"-",g.uniqueFeatures());
                    for(var t:g.transcripts()) {
                        state.add("transcript",sample.name(),g.chromosome(),g.id(),t.id(),0,"isoform",t.strand(),t.start(),t.end(),t.length(),t.exonLength(),t.intronLength(),0,0,0,t.processed(),t.dataPresent(),t.metrics());
                        state.features("transcript-feature",sample.name(),g.chromosome(),g.id(),t.id(),t.features());
                    }
                }
                samples.add(Map.of("name",sample.name(),"products",member,"processed_alignments",sample.processedAlignments(),
                    "annotation_missing_chromosome_alignments",sample.ignoredAnnotationChromosomeAlignments(),"model_genes",sample.genes().size()));
            }
            var conditions=new LinkedHashMap<String,Object>();conditions.put("excluded_antisense_gene_ids",result.excludedAntisenseGeneIds());conditions.put("samples",samples);
            conditions.put("nonfinite_fields",state.nonfinite);conditions.put("NaN_fields",state.nonfiniteNaN);conditions.put("infinite_fields",state.infinities);
            conditions.put("source_table_control_identifiers",state.controlIdentifiers);conditions.put("state_rows",state.rows);
            conditions.put("raw_state_fp64_encoding","16 hex digits of every native returned double's raw IEEE754 bits");
            conditions.put("semantic_state_fp64_encoding","exact Java hexadecimal finite values; infinities; NaN classification, with raw payload retained separately");
            conditions.put("source_table_precision",6);conditions.put("raw_identifiers_rewritten",false);conditions.put("paper_reproduced",false);
            conditions.put("source_table_NaN_token","-nan; canonical GNU comparison token. Raw native NaN payload/sign is preserved separately");
            try(var out=writer(folder.resolve("CONDITIONS.json"),budget)){out.write(NextflowJson.json(conditions));out.write('\n');}
            return conditions;
        }
    }
    private static void features(BufferedWriter out,String gene,String chr,String transcript,List<TpmCalculator.Feature> features)throws IOException {
        int ordinal=0;for(var f:features) {
            String tpm=f.dataPresent()?tableTpm(f.metrics().tpm()):"0.000000";
            if(transcript==null)row(out,gene,chr,f.type(),++ordinal,f.start(),f.end(),f.length(),f.metrics().reads(),tpm);
            else row(out,gene,transcript,chr,f.type(),++ordinal,f.start(),f.end(),f.length(),f.metrics().reads(),tpm);
        }
    }
    private static void tables(TpmCalculator.SampleResult sample,TpmCalculatorRequest.Request request,Path folder,Budget budget)throws IOException {
        try(var gene=writer(folder.resolve("genes.out"),budget);var feature=writer(folder.resolve("genes.ent"),budget);var unique=writer(folder.resolve("genes.uni"),budget);
            var tx=request.extended()?writer(folder.resolve("transcripts.out"),budget):null;var txFeature=request.extended()?writer(folder.resolve("transcripts.ent"),budget):null) {
            row(gene,GENE_HEADER);row(feature,FEATURE_HEADER);row(unique,FEATURE_HEADER);if(tx!=null){row(tx,TX_HEADER);row(txFeature,TX_FEATURE_HEADER);}
            for(var g:sample.genes())if(request.all()||g.processed()) {
                Metrics m=g.metrics();row(gene,g.id(),g.chromosome(),g.start(),g.end(),g.length(),sourceNumber(m.reads()),tableTpm(m.tpm()),
                    g.exonLength(),sourceNumber(m.exonReads()),tableTpm(m.exonTpm()),g.intronLength(),sourceNumber(m.intronReads()),tableTpm(m.intronTpm()),
                    g.uniqueLength(),sourceNumber(m.uniqueReads()),tableTpm(m.uniqueTpm()),g.uniqueExonLength(),sourceNumber(m.uniqueExonReads()),tableTpm(m.uniqueExonTpm()),
                    g.uniqueIntronLength(),sourceNumber(m.uniqueIntronReads()),tableTpm(m.uniqueIntronTpm()));
                features(feature,g.id(),g.chromosome(),null,g.features());features(unique,g.id(),g.chromosome(),null,g.uniqueFeatures());
                if(tx!=null)for(var t:g.transcripts())if(t.processed()) {
                    m=t.metrics();row(tx,g.id(),t.id(),g.chromosome(),t.start(),t.end(),t.length(),sourceNumber(m.reads()),tableTpm(m.tpm()),
                        t.exonLength(),sourceNumber(m.exonReads()),tableTpm(m.exonTpm()),t.intronLength(),sourceNumber(m.intronReads()),tableTpm(m.intronTpm()));
                    features(txFeature,g.id(),g.chromosome(),t.id(),t.features());
                }
            }
        }
    }
}
