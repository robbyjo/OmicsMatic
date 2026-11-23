/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlinalg.genetics.SmartpcaAnalysis;
import org.jlinalg.genetics.SmartpcaOutliers;
import org.jlinalg.genetics.SmartpcaPrepared;
import static org.jlinalg.cli.GprofilerWorkflowContracts.*;

/** Complete bounded EIGENSTRAT hard calls, literal identities and explicit reference populations. */
final class SmartpcaWorkflowData {
    private SmartpcaWorkflowData() { }
    static final Map<String,String> NAMES=Map.of("genotypes","genotypes.geno","markers","markers.snp","samples","samples.ind");
    record Sample(int ordinal,String id,String sex,String population) { }
    record Marker(int ordinal,String id,int chromosome,String geneticPosition,String physicalPosition,
            String referenceAllele,String otherAllele,boolean autosomal) { }
    record Prepared(List<Sample> samples,List<Marker> markers,int[] activeMarkerIndices,
            byte[][] calls,int[] basisIndices,Map<String,Object> summary) {
        Prepared { samples=List.copyOf(samples);markers=List.copyOf(markers);activeMarkerIndices=activeMarkerIndices.clone();basisIndices=basisIndices.clone(); }
        @Override public int[] activeMarkerIndices(){return activeMarkerIndices.clone();}
        @Override public int[] basisIndices(){return basisIndices.clone();}
    }
    static Prepared read(Path root,Map<String,Object> request)throws IOException {
        long maxLine=SmartpcaWorkflowContracts.limit(request,"maximum_line_bytes"),maxSamples=SmartpcaWorkflowContracts.limit(request,"maximum_samples"),maxMarkers=SmartpcaWorkflowContracts.limit(request,"maximum_markers");
        long totalBytes=0;
        for(String name:SmartpcaWorkflowContracts.INPUTS) {
            Path path=root.resolve("inputs").resolve(NAMES.get(name));require(Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS),"Missing/nonregular captured "+name);
            long size=Files.size(path);require(size<=SmartpcaWorkflowContracts.limit(request,"maximum_input_bytes"),"Captured input exceeds complete-file byte capacity");totalBytes=Math.addExact(totalBytes,size);
        }
        require(totalBytes<=SmartpcaWorkflowContracts.limit(request,"maximum_total_input_bytes"),"Complete captured inputs exceed combined byte capacity");
        var samples=new ArrayList<Sample>();var sampleIds=new HashSet<String>();
        lines(root.resolve("inputs/samples.ind"),maxLine,true,(line,ordinal)->{
            String[] cells=line.trim().split("\\s+");require(cells.length==3,"EIGENSTRAT .ind requires exactly ID,sex,population at line "+ordinal);
            String id=SmartpcaWorkflowContracts.label(cells[0],"sample ID",39),sex=SmartpcaWorkflowContracts.label(cells[1],"sex annotation",16),population=SmartpcaWorkflowContracts.label(cells[2],"population",39);
            require(!population.equals("Ignore"),"The source-reserved Ignore population is not qualified in this native file profile; retain the input and use a separately qualified original-source branch. Do not rename or silently remove it.");
            require(samples.size()<maxSamples&&sampleIds.add(id),"Sample capacity exceeded or duplicate literal sample ID: "+id);
            samples.add(new Sample(samples.size(),id,sex,population));
        });
        require(samples.size()>=2,"At least two declared individuals required");
        var markers=new ArrayList<Marker>();var markerIds=new HashSet<String>();
        lines(root.resolve("inputs/markers.snp"),maxLine,true,(line,ordinal)->{
            String[] cells=line.trim().split("\\s+");require(cells.length==4||cells.length==6,"EIGENSTRAT .snp requires four fields or six including both alleles at line "+ordinal);
            String id=SmartpcaWorkflowContracts.label(cells[0],"marker ID",39);int chromosome;
            try {require(cells[1].matches("0|[1-9][0-9]*"),"Use source EIGENSTRAT numeric chromosome codes; no inferred chr-prefix conversion");chromosome=Integer.parseInt(cells[1]);}
            catch(NumberFormatException bad){throw new IOException("Unrepresentable chromosome at line "+ordinal,bad);}
            numericToken(cells[2],"genetic position",ordinal);numericToken(cells[3],"physical position",ordinal);
            String reference=cells.length==6?SmartpcaWorkflowContracts.label(cells[4],"reference allele annotation",64):null;
            String other=cells.length==6?SmartpcaWorkflowContracts.label(cells[5],"other allele annotation",64):null;
            require(markers.size()<maxMarkers&&markerIds.add(id),"Marker capacity exceeded or duplicate literal marker ID: "+id);
            if(!markers.isEmpty()) {
                Marker previous=markers.get(markers.size()-1);
                require(chromosome>previous.chromosome()||chromosome==previous.chromosome()&&Double.parseDouble(cells[3])>=Double.parseDouble(previous.physicalPosition()),
                    "Marker rows must be consistently ordered by numeric chromosome/physical position. Preserve the inputs and prepare any paired genotype/marker reordering explicitly; no implicit row sorting.");
            }
            markers.add(new Marker(markers.size(),id,chromosome,cells[2],cells[3],reference,other,chromosome>=1&&chromosome<=22));
        });
        require(!markers.isEmpty(),"No declared markers");
        long cells=Math.multiplyExact((long)markers.size(),samples.size());
        require(cells<=SmartpcaWorkflowContracts.limit(request,"maximum_matrix_cells"),"Complete input matrix exceeds declared cell capacity; no implicit population reduction");
        long estimated=Math.addExact(cells,Math.addExact(Math.multiplyExact(256L,markers.size()),Math.multiplyExact(256L,samples.size())));
        require(estimated<=SmartpcaWorkflowContracts.limit(request,"maximum_retained_bytes"),"Complete input metadata/matrix exceeds declared retained storage estimate");
        int[] active=markers.stream().filter(Marker::autosomal).mapToInt(Marker::ordinal).toArray();require(active.length>0,"No declared human-autosomal markers; no PCA result");
        byte[][] calls=new byte[active.length][];int[] row={0},selected={0};
        lines(root.resolve("inputs/genotypes.geno"),maxLine,false,(line,ordinal)->{
            require(row[0]<markers.size(),"Extra genotype row without marker metadata at line "+ordinal);
            require(line.length()==samples.size(),"Genotype row length differs from the complete .ind population at line "+ordinal);
            byte[] values=markers.get(row[0]).autosomal()?new byte[samples.size()]:null;
            for(int i=0;i<line.length();i++) {
                char value=line.charAt(i);require(value=='0'||value=='1'||value=='2'||value=='9',"EIGENSTRAT hard calls must be literal0/1/2/9; no dosage coercion at genotype line "+ordinal);
                if(values!=null)values[i]=(byte)(value-'0');
            }
            if(values!=null)calls[selected[0]++]=values;row[0]++;
        });
        require(row[0]==markers.size()&&selected[0]==active.length,"Missing complete genotype rows or marker binding mismatch");
        var input=object(request.get("inputs"));Object declared=input.get("reference_populations");Set<String> reference=null;
        if(declared!=null) {
            reference=new HashSet<>();for(Object name:list(declared))reference.add(text(name,"reference population"));
            var available=new HashSet<String>();for(Sample sample:samples)available.add(sample.population());
            require(available.containsAll(reference),"Declared reference population is absent; no silent empty-group omission");
        }
        var basis=new ArrayList<Integer>();for(Sample sample:samples)if(reference==null||reference.contains(sample.population()))basis.add(sample.ordinal());
        require(basis.size()>=2,"Reference population contains fewer than two fitting individuals");
        int[] indices=basis.stream().mapToInt(Integer::intValue).toArray();
        int declaredGroups=reference==null?(int)samples.stream().map(Sample::population).distinct().count():reference.size();
        require(!(Boolean.TRUE.equals(object(request.get("options")).get("shrinkmode"))&&declaredGroups<2),
            "This original-source shrinkage file profile requires at least two declared fitting populations; the single-population source output branch is not admitted. Preserve the inputs; do not invent or rename populations to bypass it.");
        return new Prepared(samples,markers,active,calls,indices,map("input_samples",samples.size(),"input_markers",markers.size(),"input_cells",cells,
            "autosomal_markers",active.length,"excluded_non_autosomal_markers",markers.size()-active.length,"initial_basis_samples",basis.size(),
            "chromosome_scope","EXPLICIT_HUMAN_AUTOSOMES_1_TO_22","genome_build",input.get("genome_build"),"reference_populations",declared,
            "literal_identifiers_preserved",true,"genotypes_recoded",false,"source_input_format","EIGENSTRAT_TEXT_0_1_2_9"));
    }
    static SmartpcaAnalysis.Options options(Map<String,Object> request)throws IOException {
        var o=object(request.get("options"));var bounds=new SmartpcaPrepared.Limits(SmartpcaWorkflowContracts.limit(request,"maximum_estimated_work"),SmartpcaWorkflowContracts.limit(request,"maximum_retained_bytes"));
        var pca=new SmartpcaPrepared.Options(Math.toIntExact(number(o,"numoutevec")),(Boolean)o.get("usenorm"),(Boolean)o.get("altnormstyle"),
            Math.toIntExact(number(o,"minallelecnt")),Math.toIntExact(number(o,"maxmissing")),(Boolean)o.get("lsqproject")?SmartpcaPrepared.Projection.LEAST_SQUARES:SmartpcaPrepared.Projection.ORTHOGONAL,bounds);
        return new SmartpcaAnalysis.Options(pca,Math.toIntExact(number(o,"numoutlieriter")),Math.toIntExact(number(o,"numoutlierevec")),decimal(o.get("outliersigmathresh")).doubleValue(),
            SmartpcaOutliers.Mode.values()[Math.toIntExact(number(o,"outliermode"))],(Boolean)o.get("shrinkmode"));
    }
    private static void numericToken(String token,String label,long line)throws IOException {
        try {require(token.length()<=128,"Position token exceeds capacity");double value=Double.parseDouble(token);require(Double.isFinite(value)&&value>=0,"Nonfinite/negative "+label+" at line "+line);}
        catch(NumberFormatException invalid){throw new IOException("Invalid "+label+" at line "+line,invalid);}
    }
    @FunctionalInterface private interface LineConsumer { void accept(String line,long ordinal)throws IOException; }
    private static void lines(Path path,long maximum,boolean comments,LineConsumer consumer)throws IOException {
        require(Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS),"Captured EIGENSTRAT input is missing or nonregular: "+path);
        try(var stream=new BufferedInputStream(Files.newInputStream(path,LinkOption.NOFOLLOW_LINKS),65536)) {
            var line=new ByteArrayOutputStream();long ordinal=0;boolean pending=false;
            for(int value;(value=stream.read())>=0;) {
                if((line.size()&4095)==0&&Thread.currentThread().isInterrupted())throw new java.io.InterruptedIOException("Native EIGENSTRAT reading interrupted; no complete result");
                pending=true;
                if(value=='\n'){consume(line.toByteArray(),++ordinal,comments,consumer);line.reset();pending=false;}
                else{require(line.size()<maximum,"Complete input line exceeds declared byte capacity: "+path);line.write(value);}
            }
            if(pending)consume(line.toByteArray(),++ordinal,comments,consumer);
        }
    }
    private static void consume(byte[] bytes,long ordinal,boolean comments,LineConsumer consumer)throws IOException {
        int length=bytes.length;if(length>0&&bytes[length-1]=='\r')length--;
        String text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes,0,length)).toString();
        if(comments&&(text.isBlank()||text.stripLeading().startsWith("#")))return;
        consumer.accept(text,ordinal);
    }
}
