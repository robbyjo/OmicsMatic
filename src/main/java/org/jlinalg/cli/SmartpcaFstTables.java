/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import org.jlinalg.genetics.SmartpcaFst;
import org.jlinalg.gprofiler.GprofilerJson;
import static org.jlinalg.cli.GprofilerWorkflowContracts.*;
import static org.jlinalg.cli.SmartpcaWorkflowFiles.*;

/** Complete streaming source contributions and result populations; literal identifiers stay literal. */
final class SmartpcaFstTables {
    private SmartpcaFstTables() { }
    interface Lines extends AutoCloseable {void line(String line)throws IOException;@Override void close()throws IOException;}
    @FunctionalInterface interface Target {Lines open(String name)throws IOException;}
    static final List<String> TABLES=List.of("pairs.tsv","inbreeding.tsv","markers.tsv","blocks.tsv","input-samples.tsv");
    static String numeric(double value){return SmartpcaWorkflowTables.numberCell(value);}
    static Lines output(Path root,String name,SmartpcaWorkflowTables.Budget budget)throws IOException {
        var sink=budget.text(root.resolve(name));return new Lines(){public void line(String line)throws IOException{if(Thread.currentThread().isInterrupted())throw new java.io.InterruptedIOException("Native FST output interrupted");sink.line(line);}public void close()throws IOException{sink.close();}};
    }
    static final class LocusSink implements AutoCloseable {
        private final Lines out;private final SmartpcaWorkflowData.Prepared data;private final List<String> names;private final MessageDigest digest;private long bytes;
        LocusSink(Path root,SmartpcaWorkflowData.Prepared data,List<String> names,SmartpcaWorkflowTables.Budget budget)throws IOException {
            this.data=data;this.names=names;out=budget==null?new Comparison(root.resolve("locus-contributions.tsv"),"locus-contributions.tsv"):output(root,"locus-contributions.tsv",budget);
            try{digest=MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException error){throw new AssertionError(error);}
            line("kind\toriginal_marker_ordinal\tmarker_id\tblock\tfirst_population\tsecond_population\tnumerator\tdenominator\tratio");
        }
        private void line(String value)throws IOException{byte[] raw=(value+"\n").getBytes(StandardCharsets.UTF_8);digest.update(raw);bytes+=raw.length;out.line(value);}
        void accept(SmartpcaFst.Locus locus)throws IOException {
            String marker=locus.markerOrdinal()+"\t"+data.markers().get(locus.markerOrdinal()).id()+"\t"+locus.block()+"\t";int pair=0;
            double[] n=locus.numerator(),d=locus.denominator();
            for(int a=0;a<names.size();a++)for(int b=a+1;b<names.size();b++){line("PAIR_FST\t"+marker+names.get(a)+"\t"+names.get(b)+"\t"+numeric(n[pair])+"\t"+numeric(d[pair])+"\t"+(d[pair]>0?numeric(n[pair]/d[pair]):"-"));pair++;}
            n=locus.inbreedingNumerator();d=locus.inbreedingDenominator();for(int a=0;a<n.length;a++)line("INBREEDING\t"+marker+names.get(a)+"\t"+names.get(a)+"\t"+numeric(n[a])+"\t"+numeric(d[a])+"\t"+(d[a]>0?numeric(n[a]/d[a]):"-"));
        }
        Map<String,Object> identity(){return map("bytes",bytes,"sha256",java.util.HexFormat.of().formatHex(digest.digest()));}
        @Override public void close()throws IOException{out.close();}
    }
    static LocusSink loci(Path root,SmartpcaWorkflowData.Prepared data,List<String> names,SmartpcaWorkflowTables.Budget budget)throws IOException{return new LocusSink(root,data,names,budget);}
    static void writeOutputs(Path root,SmartpcaFstEvaluation.Evaluated value,SmartpcaWorkflowTables.Budget budget)throws IOException {
        budget.json(root.resolve("native-result.json"),value.result());tables(value,name->output(root,name,budget));
    }
    static void verifyOutputs(Path root,SmartpcaFstEvaluation.Evaluated value)throws IOException {
        SmartpcaWorkflowEvaluation.compare(read(root.resolve("native-result.json")),value.result(),"native-result");
        require(equal(read(root.resolve("input-summary.json")),value.inputSummary()),"FST input summary differs from complete inputs");
        tables(value,name->new Comparison(root.resolve(name),name));
        if(value.locusWrittenIdentity()!=null)require(equal(pin(root.resolve("locus-contributions.tsv")),value.locusWrittenIdentity()),"Locus contribution bytes changed after the actual calculation");
    }
    private static void tables(SmartpcaFstEvaluation.Evaluated value,Target target)throws IOException {
        estimateTable(value.analysis().pairs(),value.populations(),target,"pairs.tsv");estimateTable(value.analysis().inbreeding(),value.populations(),target,"inbreeding.tsv");
        try(var out=target.open("blocks.tsv")){out.line("zero_based_block\tglobal_marker_count_weight");int[] counts=value.analysis().blockMarkerCounts();for(int i=0;i<counts.length;i++)out.line(i+"\t"+counts[i]);}
        try(var out=target.open("markers.tsv")) {
            out.line("original_marker_ordinal\tmarker_id\tchromosome\tgenetic_position_token\tphysical_position_token\treference_allele_annotation\tother_allele_annotation\tsource_status\teffective_morgans\tblock\tselected_observed_samples");
            for(var marker:value.data().markers()) {
                var state=value.analysis().markers().get(marker.ordinal());out.line(marker.ordinal()+"\t"+marker.id()+"\t"+marker.chromosome()+"\t"+marker.geneticPosition()+"\t"+marker.physicalPosition()+"\t"+(marker.referenceAllele()==null?"":marker.referenceAllele())+"\t"+(marker.otherAllele()==null?"":marker.otherAllele())+"\t"+state.status()+"\t"+numeric(state.effectiveGeneticPosition())+"\t"+state.block()+"\t"+(marker.autosomal()?state.selectedObservedSamples():"-"));
            }
        }
        try(var out=target.open("input-samples.tsv")) {
            out.line("original_sample_ordinal\tsample_id\tsex_annotation\tpopulation\tselected\tcontributes_retained_autosomal_calls");
            for(var sample:value.data().samples()){int i=sample.ordinal();out.line(i+"\t"+sample.id()+"\t"+sample.sex()+"\t"+sample.population()+"\t"+(value.samplePopulation()[i]>=0)+"\t"+value.contributing()[i]);}
        }
    }
    private static void estimateTable(List<SmartpcaFst.Estimate> values,List<String> names,Target target,String file)throws IOException {
        try(var out=target.open(file)) {
            out.line("first_population_ordinal\tsecond_population_ordinal\tfirst_population\tsecond_population\testimate\tstandard_error\tsource_status\tfields_admitted\tmarker_order_ratio\tblock_order_ratio\ttotal_numerator\ttotal_denominator\tvalid_loci\tpositive_denominator_loci");
            for(var e:values)out.line(e.firstPopulation()+"\t"+e.secondPopulation()+"\t"+names.get(e.firstPopulation())+"\t"+names.get(e.secondPopulation())+"\t"+numeric(e.estimate())+"\t"+numeric(e.standardError())+"\t"+e.admission()+"\t"+(e.admission()==SmartpcaFst.Admission.COMPUTED)+"\t"+numeric(e.markerOrderRatio())+"\t"+numeric(e.blockOrderRatio())+"\t"+numeric(e.totalNumerator())+"\t"+numeric(e.totalDenominator())+"\t"+e.validLoci()+"\t"+e.positiveDenominatorLoci());
        }
    }
    static void compareTables(Path parent,Path child)throws IOException {
        for(String name:java.util.stream.Stream.concat(TABLES.stream(),java.util.stream.Stream.of("locus-contributions.tsv")).toList()) {
            try(var actual=new Comparison(parent.resolve(name),name);var source=new Comparison(child.resolve(name),name)) {
                for(String line;(line=source.next())!=null;)actual.line(line);
            }
        }
    }
    private static final class Comparison implements Lines {
        private final java.io.BufferedInputStream in;private final String name;private int row;
        Comparison(Path path,String name)throws IOException{in=new java.io.BufferedInputStream(Files.newInputStream(path),65536);this.name=name;}
        @Override public void line(String expected)throws IOException {
            String actual=next();require(actual!=null,"Missing FST table row: "+name+"/"+row);
            if(row++==0){require(actual.equals(expected),"FST table header differs: "+name);return;}
            String[] a=actual.split("\t",-1),b=expected.split("\t",-1);require(a.length==b.length,"FST table width differs: "+name+"/"+row);
            for(int c=0;c<a.length;c++) {
                int kind=kind(name,c);
                if(kind==0||java.util.Set.of("","-","NaN","Inf","-Inf").contains(b[c]))require(a[c].equals(b[c]),"Literal FST identifier/status/special cell differs: "+name+"/"+row+"/"+c);
                else try {
                    if(kind==1)require(new java.math.BigDecimal(a[c]).compareTo(new java.math.BigDecimal(b[c]))==0,"Integer FST table field differs: "+name+"/"+row+"/"+c);
                    else SmartpcaWorkflowEvaluation.compare(new GprofilerJson.Numeric(a[c]),Double.parseDouble(b[c]),name+"/"+row+"/"+c);
                }catch(IllegalArgumentException error){throw new IOException("Invalid scientific FST table number: "+name+"/"+row+"/"+c,error);}
            }
        }
        private String next()throws IOException {
            var bytes=new java.io.ByteArrayOutputStream();boolean any=false;
            for(int c;(c=in.read())>=0;){any=true;if(c=='\n')break;require(bytes.size()<1_048_576,"FST table line capacity exceeded");bytes.write(c);}
            if(!any)return null;byte[] raw=bytes.toByteArray();int n=raw.length;if(n>0&&raw[n-1]=='\r')n--;
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(raw,0,n)).toString();
        }
        @Override public void close()throws IOException{try{require(next()==null,"Extra FST table rows: "+name);}finally{in.close();}}
        private static int kind(String name,int c) {
            return switch(name) {
                case "locus-contributions.tsv" -> c==1||c==3?1:c>=6?2:0;
                case "pairs.tsv","inbreeding.tsv" -> c<=1||c>=12?1:c==4||c==5||c>=8&&c<=11?2:0;
                case "markers.tsv" -> c==0||c==2||c==9||c==10?1:c==8?2:0;
                case "blocks.tsv" -> 1;
                case "input-samples.tsv" -> c==0?1:0;
                default -> 0;
            };
        }
    }
}
