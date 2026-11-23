/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jlinalg.gprofiler.GprofilerJson;
import static org.jlinalg.cli.GprofilerWorkflowContracts.*;
import static org.jlinalg.cli.SmartpcaWorkflowFiles.*;

/** Complete output populations, bounded streaming TSV and portable coordinate chunks. */
final class SmartpcaWorkflowTables {
    private SmartpcaWorkflowTables() { }
    interface LineSink extends AutoCloseable { void line(String text)throws IOException;@Override void close()throws IOException; }
    @FunctionalInterface interface Target { LineSink open(String name)throws IOException; }
    static final class Budget {
        private final long maximum;private long written;
        Budget(long maximum){this.maximum=maximum;}
        synchronized void claim(long count)throws IOException{require(count>=0&&count<=maximum-written,"Complete numerical output exceeds declared byte capacity; no truncated result admitted");written+=count;}
        void json(Path path,Object value)throws IOException{byte[] raw=SmartpcaWorkflowFiles.json(value);claim(raw.length);Files.write(path,raw,StandardOpenOption.CREATE_NEW);}
        Sink text(Path path)throws IOException{return new Sink(Files.newOutputStream(path,StandardOpenOption.CREATE_NEW),this);}
        long written(){return written;}
    }
    static final class Sink implements LineSink {
        private final OutputStream stream;private final Budget budget;
        Sink(OutputStream stream,Budget budget){this.stream=stream;this.budget=budget;}
        @Override public void line(String text)throws IOException{byte[] raw=(text+"\n").getBytes(StandardCharsets.UTF_8);budget.claim(raw.length);stream.write(raw);}
        @Override public void close()throws IOException{stream.close();}
    }
    static void inputSamples(Path root,SmartpcaWorkflowData.Prepared data,Budget budget)throws IOException {
        try(var out=budget.text(root.resolve("input-samples.tsv"))) {
            out.line("original_sample_ordinal\tsample_id\tsex_annotation\tpopulation\tinitial_reference");
            boolean[] basis=new boolean[data.samples().size()];for(int index:data.basisIndices())basis[index]=true;
            for(var sample:data.samples())out.line(sample.ordinal()+"\t"+sample.id()+"\t"+sample.sex()+"\t"+sample.population()+"\t"+basis[sample.ordinal()]);
        }
    }
    static Map<String,Object> chunk(SmartpcaWorkflowEvaluation.Evaluated value,int first) {
        int end=Math.min(value.data().samples().size(),first+500);var rows=new ArrayList<Object>();var status=new ArrayList<Object>();var admitted=new ArrayList<Object>();var observed=new ArrayList<Object>();
        int[] calls=value.analysis().observedUsedMarkers();
        for(int i=first;i<end;i++){rows.add(SmartpcaWorkflowEvaluation.cells(value.coordinates()[i]));status.add(value.analysis().sampleStatus().get(i).name());admitted.add(value.coordinateAdmission().get(i));observed.add(calls[i]);}
        return map("first_sample_ordinal",first,"coordinates",rows,"source_status",status,"coordinate_fields_admitted",admitted,"observed_used_markers",observed);
    }
    static void writeOutputs(Path root,SmartpcaWorkflowEvaluation.Evaluated value,Budget budget)throws IOException {
        budget.json(root.resolve("native-result.json"),value.result());
        for(int first=0;first<value.data().samples().size();first+=500)budget.json(root.resolve(String.format(java.util.Locale.ROOT,"coordinates/%06d.json",first/500)),chunk(value,first));
        tables(value,name->budget.text(root.resolve(name)));
    }
    static void tables(SmartpcaWorkflowEvaluation.Evaluated value,Target target)throws IOException {
        try(var out=target.open("eigenvalues.tsv")) {
            out.line("one_based_axis\teigenvalue\trequested_axis");double[] values=value.analysis().eigenvalues();int k=value.coordinates()[0].length;
            for(int i=0;i<values.length;i++)out.line((i+1)+"\t"+numberCell(values[i])+"\t"+(i<k));
        }
        try(var out=target.open("coordinates.tsv")) {
            var header=new StringBuilder("original_sample_ordinal\tsample_id\tpopulation\tsource_status\tcoordinate_fields_admitted\tfinal_basis\tobserved_used_markers");
            for(int c=0;c<value.coordinates()[0].length;c++)header.append("\tPC").append(c+1);out.line(header.toString());
            boolean[] basis=new boolean[value.data().samples().size()];for(int index:value.analysis().basisIndices())basis[index]=true;int[] observed=value.analysis().observedUsedMarkers();
            for(var sample:value.data().samples()) {
                int i=sample.ordinal();var row=new StringBuilder().append(i).append('\t').append(sample.id()).append('\t').append(sample.population()).append('\t').append(value.analysis().sampleStatus().get(i))
                    .append('\t').append(value.coordinateAdmission().get(i)).append('\t').append(basis[i]).append('\t').append(observed[i]);
                for(double coordinate:value.coordinates()[i])row.append('\t').append(numberCell(coordinate));out.line(row.toString());
            }
        }
        try(var out=target.open("outliers.tsv")) {
            out.line("original_sample_ordinal\tsample_id\titeration\tzero_based_axis\tsigned_deviation\tdeviation_finite");
            for(var removal:value.analysis().removals())out.line(removal.originalSampleIndex()+"\t"+value.data().samples().get(removal.originalSampleIndex()).id()+"\t"+removal.iteration()+"\t"+removal.zeroBasedAxis()+"\t"+numberCell(removal.signedDeviation())+"\t"+Double.isFinite(removal.signedDeviation()));
        }
        markerTable(value,target);
        try(var out=target.open("shrinkage.tsv")) {
            out.line("zero_based_axis\tfinal_basis_ordinal\toriginal_sample_ordinal\tsample_id\teigenvalue\tdelta\tmultiplier\toriginal_bad_delta_branch");
            if(value.analysis().shrinkage()!=null)for(var p:value.analysis().shrinkage().perturbations()) {
                int sample=value.analysis().basisIndices()[p.basisOrdinal()];
                out.line(p.zeroBasedAxis()+"\t"+p.basisOrdinal()+"\t"+sample+"\t"+value.data().samples().get(sample).id()+"\t"+numberCell(p.eigenvalue())+"\t"+numberCell(p.delta())+"\t"+numberCell(p.multiplier())+"\t"+p.originalBadDeltaBranch());
            }
        }
    }
    private static void markerTable(SmartpcaWorkflowEvaluation.Evaluated value,Target target)throws IOException {
        var finalRows=new HashMap<Integer,Integer>();int[] active=value.data().activeMarkerIndices(),selected=value.analysis().markerIndices();
        for(int row=0;row<selected.length;row++)finalRows.put(active[selected[row]],row);
        try(var out=target.open("markers.tsv")) {
            out.line("original_marker_ordinal\tmarker_id\tchromosome\tgenetic_position\tphysical_position\treference_allele_annotation\tother_allele_annotation\tfinal_panel_status\tfinal_basis_observed\tfinal_basis_missing\tmean\tmultiplier");
            for(var marker:value.data().markers()) {
                String prefix=marker.ordinal()+"\t"+marker.id()+"\t"+marker.chromosome()+"\t"+marker.geneticPosition()+"\t"+marker.physicalPosition()+"\t"+(marker.referenceAllele()==null?"":marker.referenceAllele())+"\t"+(marker.otherAllele()==null?"":marker.otherAllele())+"\t";
                Integer row=finalRows.get(marker.ordinal());
                if(row==null)out.line(prefix+(marker.autosomal()?"PERMANENTLY_EXCLUDED_BEFORE_FINAL_PASS":"SOURCE_EXCLUDED_NON_AUTOSOMAL")+"\t\t\t\t");
                else {var state=value.analysis().finalBasis().markers().get(row);out.line(prefix+state.status()+"\t"+state.observed()+"\t"+state.missing()+"\t"+numberCell(state.mean())+"\t"+numberCell(state.multiplier()));}
            }
        }
    }
    static void verifyTables(Path root,SmartpcaWorkflowEvaluation.Evaluated value)throws IOException {
        tables(value,name->new Comparison(root.resolve(name),name));
        try(var comparison=new Comparison(root.resolve("input-samples.tsv"),"input-samples.tsv")) {
            comparison.line("original_sample_ordinal\tsample_id\tsex_annotation\tpopulation\tinitial_reference");
            boolean[] basis=new boolean[value.data().samples().size()];for(int index:value.data().basisIndices())basis[index]=true;
            for(var sample:value.data().samples())comparison.line(sample.ordinal()+"\t"+sample.id()+"\t"+sample.sex()+"\t"+sample.population()+"\t"+basis[sample.ordinal()]);
        }
    }
    private static final class Comparison implements LineSink {
        private final java.io.BufferedInputStream input;private final String name;private int row;
        Comparison(Path path,String name)throws IOException{this.input=new java.io.BufferedInputStream(Files.newInputStream(path),65536);this.name=name;}
        @Override public void line(String expected)throws IOException {
            String actual=next();require(actual!=null,"Missing complete table row: "+name+"/"+row);
            if(row++==0){require(actual.equals(expected),"Scientific table header differs: "+name);return;}
            String[] a=actual.split("\t",-1),b=expected.split("\t",-1);require(a.length==b.length,"Scientific table width differs: "+name+"/"+row);
            for(int c=0;c<b.length;c++) {
                int kind=numeric(name,c);
                if(kind==0||b[c].isEmpty()||b[c].equals("NaN")||b[c].equals("Inf")||b[c].equals("-Inf"))require(a[c].equals(b[c]),"Literal table identity/status/special cell differs: "+name+"/"+row+"/"+c);
                else try {
                    if(kind==1)require(new java.math.BigDecimal(a[c]).compareTo(new java.math.BigDecimal(b[c]))==0,"Integer table field differs: "+name+"/"+row+"/"+c);
                    else SmartpcaWorkflowEvaluation.compare(new GprofilerJson.Numeric(a[c]),Double.parseDouble(b[c]),name+"/"+row+"/"+c);
                }catch(IllegalArgumentException invalid){throw new IOException("Malformed scientific table number: "+name+"/"+row+"/"+c,invalid);}
            }
        }
        private String next()throws IOException {
            var bytes=new java.io.ByteArrayOutputStream();boolean any=false;
            for(int value;(value=input.read())>=0;){any=true;if(value=='\n')break;require(bytes.size()<1_048_576,"Scientific table line exceeds fixed capacity");bytes.write(value);}
            if(!any)return null;byte[] raw=bytes.toByteArray();int n=raw.length;if(n>0&&raw[n-1]=='\r')n--;
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(raw,0,n)).toString();
        }
        @Override public void close()throws IOException{try{require(next()==null,"Extra scientific table row: "+name);}finally{input.close();}}
        private static int numeric(String name,int column) {
            return switch(name) {
                case "eigenvalues.tsv" -> column==0?1:column==1?2:0;
                case "coordinates.tsv" -> column==0||column==6?1:column>=7?2:0;
                case "outliers.tsv" -> column==0||column==2||column==3?1:column==4?2:0;
                case "markers.tsv" -> column==0||column==2||column==8||column==9?1:column==10||column==11?2:0;
                case "shrinkage.tsv" -> column<=2?1:column>=4&&column<=6?2:0;
                case "input-samples.tsv" -> column==0?1:0;
                default -> 0;
            };
        }
    }
    static String numberCell(double value){return Double.isFinite(value)?Double.toString(value):Double.isNaN(value)?"NaN":value>0?"Inf":"-Inf";}
}
