/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;

/** Streamed reference intake and literal instrument rows, without population repair. */
final class TwoSampleMrClumpFiles {
    private TwoSampleMrClumpFiles() { }
    record Intake(TwoSampleMrWorkflowFiles.Table instruments,String pcolumn,Map<String,Long> referenceOccurrences,long samples,long founders,long variants) { }
    static TwoSampleMrWorkflowFiles.Table table(Path path,Map<String,Object> r)throws IOException {
        try(var input=Files.newBufferedReader(path,StandardCharsets.UTF_8)) {
            String header=input.readLine();if(header==null)throw new IOException("Require canonical TSV header");
            var columns=List.of(header.split("\t",-1));if(columns.size()>((Number)r.get("maximum_columns")).longValue()||new HashSet<>(columns).size()!=columns.size())throw new IOException("Duplicate columns or maximum_columns exceeded");
            for(String name:columns){if(name.isBlank()||name.length()>4094||name.codePoints().anyMatch(Character::isISOControl))throw new IOException("Invalid literal column name");}
            var rows=new ArrayList<List<String>>();String line;
            while((line=input.readLine())!=null) {
                if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Instrument intake interrupted");
                if(rows.size()>=((Number)r.get("maximum_rows")).longValue()||(long)(rows.size()+1)*columns.size()>((Number)r.get("maximum_entries")).longValue())throw new IOException("Instrument capacity exceeded; no truncation");
                var row=List.of(line.split("\t",-1));if(row.size()!=columns.size())throw new IOException("Instrument TSV width differs at row "+(rows.size()+1));
                for(int j=0;j<row.size();j++) {
                    String value=row.get(j),column=columns.get(j);
                    if(value.length()>4094||value.codePoints().anyMatch(Character::isISOControl))throw new IOException("Instrument TSV cell contains controls or exceeds capacity");
                    if(TwoSampleMrWorkflowFiles.numeric(column))TwoSampleMrWorkflowFiles.number(value);
                    else if(TwoSampleMrWorkflowFiles.logical(column)&&!Set.of("TRUE","FALSE","NA").contains(value))throw new IOException("Logical metadata requires TRUE/FALSE/NA");
                }
                rows.add(row);
            }
            return new TwoSampleMrWorkflowFiles.Table(columns,List.copyOf(rows));
        }
    }
    static Intake prepare(Path root,Map<String,Object> r,boolean compare)throws IOException {
        var t=table(root.resolve(text(r,"instruments")),r);if(!t.columns().contains("SNP"))throw new IOException("Missing literal SNP column");
        boolean ids=t.columns().contains("id.exposure");String pcolumn=t.columns().contains("pval.exposure")?"pval.exposure":t.columns().contains("pval.outcome")?"pval.outcome":"";
        var defaults=object(r.get("allow_source_defaults"),"allow_source_defaults");
        if(!ids&&!Boolean.TRUE.equals(defaults.get("generated_ids")))throw new IOException("Missing id.exposure; explicitly opt in to original random group generation or supply the column");
        if(pcolumn.isEmpty()&&!Boolean.TRUE.equals(defaults.get("pvalues")))throw new IOException("Missing P-value column; explicitly opt in to original .99 P values or supply the source column");
        var occurrences=new TreeMap<String,Long>();long missingP=0;var groups=new LinkedHashMap<String,Long>();
        for(var row:t.rows()) {
            String snp=t.cell(row,"SNP");if(snp.isBlank()||snp.equals("NA")||snp.codePoints().anyMatch(Character::isWhitespace))throw new IOException("SNP must be a nonmissing literal token without whitespace; no identifier repair");
            occurrences.put(snp,0L);String group=ids?t.cell(row,"id.exposure"):"SOURCE_GENERATED_GROUP";
            if(group.isBlank()||group.equals("NA"))throw new IOException("Missing id.exposure; no group imputation");groups.merge(group,1L,Long::sum);
            double p=pcolumn.isEmpty()?.99:TwoSampleMrWorkflowFiles.number(t.cell(row,pcolumn));
            if(Double.isNaN(p)){missingP++;if(!Boolean.TRUE.equals(defaults.get("pvalues")))throw new IOException("Missing P values require explicit original-source-default opt-in");}
            else if(!Double.isFinite(p)||p<0||p>1)throw new IOException("Clumping P values require [0,1] or explicit opted-in NA/NaN; no clamping");
        }
        long samples=0,founders=0;var identities=new HashSet<List<String>>();
        try(var reader=Files.newBufferedReader(root.resolve(text(r,"reference_fam")),StandardCharsets.UTF_8)) {
            String line;while((line=reader.readLine())!=null) {
                checkInterrupt();String[] fields=fields(line,"FAM");if(++samples>((Number)r.get("maximum_reference_samples")).longValue())throw new IOException("maximum_reference_samples exceeded");
                if(!identities.add(List.of(fields[0],fields[1])))throw new IOException("Duplicate FID/IID in reference FAM; no sample selection or repair");
                if(fields[2].equals("0")&&fields[3].equals("0"))founders++;
            }
        }
        if(samples==0)throw new IOException("Reference FAM has no samples");
        long variants=0;
        try(var reader=Files.newBufferedReader(root.resolve(text(r,"reference_bim")),StandardCharsets.UTF_8)) {
            String line;while((line=reader.readLine())!=null) {
                checkInterrupt();String[] fields=fields(line,"BIM");if(++variants>((Number)r.get("maximum_reference_variants")).longValue())throw new IOException("maximum_reference_variants exceeded");
                if(occurrences.containsKey(fields[1]))occurrences.compute(fields[1],(key,count)->count+1);
                try{double cm=Double.parseDouble(fields[2]);long bp=Long.parseLong(fields[3]);if(!Double.isFinite(cm)||bp<0)throw new NumberFormatException();}catch(NumberFormatException error){throw new IOException("BIM requires finite genetic position and nonnegative integral base-pair position; no conversion",error);}
            }
        }
        if(variants==0)throw new IOException("Reference BIM has no variants");
        for(var entry:occurrences.entrySet())if(entry.getValue()>1)throw new IOException("Instrument SNP has ambiguous duplicate BIM identities: "+entry.getKey());
        long genotypes=Math.multiplyExact(samples,variants),expected=Math.addExact(3,Math.multiplyExact(variants,(samples+3)/4));
        if(genotypes>((Number)r.get("maximum_genotype_entries")).longValue())throw new IOException("maximum_genotype_entries exceeded; no reference subsetting");
        Path bed=root.resolve(text(r,"reference_bed"));if(Files.size(bed)!=expected)throw new IOException("BED length differs from complete paired BIM/FAM dimensions");
        try(var input=Files.newInputStream(bed)){if(input.read()!=0x6c||input.read()!=0x1b||input.read()!=1)throw new IOException("Require original SNP-major PLINK BED magic and mode; no transposition");}
        long pairRows=0;for(long n:groups.values())pairRows=Math.addExact(pairRows,Math.multiplyExact(n,n));long work=Math.multiplyExact(pairRows,samples);
        if(work>((Number)r.get("maximum_work")).longValue())throw new IOException("maximum_work exceeded; choose explicit capacities, no candidate/reference pruning");
        var summary=new LinkedHashMap<String,Object>();summary.put("instrument_columns",t.columns());summary.put("instrument_rows",t.rows().size());summary.put("source_pvalue_column",pcolumn);summary.put("missing_source_pvalues",missingP);summary.put("source_generates_id",!ids);summary.put("source_group_rows",groups);
        summary.put("reference_samples",samples);summary.put("reference_founders",founders);summary.put("reference_variants",variants);summary.put("reference_genotype_entries",genotypes);summary.put("reference_population_declared",r.get("reference_population"));summary.put("reference_population_verified",false);summary.put("reference_candidate_occurrences",occurrences);summary.put("conservative_work",work);
        var pins=new TreeMap<String,String>();for(String key:TwoSampleMrClumpContracts.INPUTS)pins.put(key,FollowupSupport.hash(root.resolve(text(r,key))));summary.put("input_sha256",pins);
        byte[] bytes=RsemJson.bytes(summary);Path target=root.resolve("prepared.json");if(compare){if(!Arrays.equals(bytes,Files.readAllBytes(target)))throw new IOException("Literal clumping intake/reference summary differs");}else Files.write(target,bytes,StandardOpenOption.CREATE_NEW);
        return new Intake(t,pcolumn,Collections.unmodifiableMap(occurrences),samples,founders,variants);
    }
    private static String[] fields(String line,String kind)throws IOException {
        if(line.length()>32768||line.isBlank())throw new IOException(kind+" requires bounded nonblank six-field rows");String[] fields=line.trim().split("\\s+");
        if(fields.length!=6)throw new IOException(kind+" requires exactly six whitespace-delimited fields");for(String field:fields)if(field.length()>4094||field.codePoints().anyMatch(Character::isISOControl))throw new IOException("Invalid "+kind+" token");return fields;
    }
    private static void checkInterrupt()throws InterruptedIOException {if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Reference intake interrupted");}
}
