/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Complete cell-ID joins with explicit column roles; learned model/category encodings are not inferred. */
final class ScviCovariates {
    private ScviCovariates() { }
    enum Labels { NONE, REFERENCE, UNLABELED_QUERY, KNOWN_REFERENCE_QUERY }
    record Spec(String batch, List<String> categorical, List<String> continuous, String labels,
                String unlabeledCategory, Labels labelUse, List<String> knownReferenceLabels,
                ScviMatrix.Precision continuousPrecision, int maximumColumns) {
        Spec {
            categorical=List.copyOf(categorical);continuous=List.copyOf(continuous);knownReferenceLabels=List.copyOf(knownReferenceLabels);
            Objects.requireNonNull(labelUse);Objects.requireNonNull(continuousPrecision);
            if(maximumColumns<1||maximumColumns>4096)throw new IllegalArgumentException("Require 1..4096 metadata columns");
            var used=new HashSet<String>();if(batch!=null)column(batch,used);
            categorical.forEach(s->column(s,used));continuous.forEach(s->column(s,used));
            if(labelUse==Labels.NONE){if(labels!=null||unlabeledCategory!=null||!knownReferenceLabels.isEmpty())throw new IllegalArgumentException("No-label model cannot declare label semantics");}
            else {column(labels,used);if(unlabeledCategory==null||unlabeledCategory.isBlank()||invalidField(unlabeledCategory))throw new IllegalArgumentException("Explicit nonblank unlabeled category required");}
            if(labelUse==Labels.KNOWN_REFERENCE_QUERY){if(knownReferenceLabels.isEmpty()||new HashSet<>(knownReferenceLabels).size()!=knownReferenceLabels.size())throw new IllegalArgumentException("Known-label query requires unique admitted reference labels");for(String s:knownReferenceLabels)if(s.isBlank()||invalidField(s)||s.equals(unlabeledCategory))throw new IllegalArgumentException("Invalid admitted reference label");}
            else if(!knownReferenceLabels.isEmpty())throw new IllegalArgumentException("Known reference labels apply only to explicit known-label queries");
        }
        List<String> categoricalColumns(){var all=new ArrayList<String>();if(batch!=null)all.add(batch);all.addAll(categorical);if(labels!=null)all.add(labels);return List.copyOf(all);}
    }
    record Prepared(Path directory, ScviMatrix.Prepared matrix, ScviText.Snapshot source, Spec spec,
                    List<String> columns, long roundedCovariates, long underflowCovariates,
                    Map<String,Map<String,Long>> observedCategoryCounts, Map<String,String> fileHashes) {
        Prepared {
            columns=List.copyOf(columns);var copy=new LinkedHashMap<String,Map<String,Long>>();observedCategoryCounts.forEach((k,v)->copy.put(k,Map.copyOf(v)));observedCategoryCounts=Map.copyOf(copy);fileHashes=Map.copyOf(fileHashes);
        }
        void verify(Runnable check)throws IOException {matrix.verify(check);source.verify(matrix.request().limits().inputBytes(),check);if(!ScviMatrix.inventory(directory,matrix.request().limits(),check).equals(fileHashes))throw new IOException("Prepared scVI metadata bytes or complete member population changed");}
    }
    private static final class Bytes {
        private final long maximum;private long used;
        Bytes(long used,long maximum)throws IOException{this.maximum=maximum;add(used);}
        void add(long n)throws IOException{used=Math.addExact(used,n);if(used>maximum)throw new IOException("Combined scVI matrix/metadata preparation workspace capacity exceeded");}
        void release(long n){if(n<0||n>used)throw new IllegalArgumentException("Invalid workspace release");used-=n;}
    }
    static Prepared prepare(ScviMatrix.Prepared matrix, ScviText.Source metadata, Spec spec, Path output, Runnable check)throws IOException {
        Objects.requireNonNull(spec);Objects.requireNonNull(check);matrix.verify(check);var limits=matrix.request().limits();Path root=output.toAbsolutePath().normalize();
        if(root.startsWith(matrix.directory())||matrix.directory().startsWith(root)||root.getParent()==null||!Files.isDirectory(root.getParent(),LinkOption.NOFOLLOW_LINKS)||!root.getParent().toRealPath().equals(root.getParent()))throw new IOException("Metadata needs a NEW canonical directory separate from prepared matrix inputs");
        long encoded=matrix.sources().stream().mapToLong(ScviText.Snapshot::bytes).sum(),matrixBytes=0;
        for(var e:matrix.fileHashes().entrySet())if(!e.getValue().equals("DIRECTORY"))matrixBytes=Math.addExact(matrixBytes,Files.size(matrix.directory().resolve(e.getKey())));
        long decodedRemaining=limits.decodedBytes()-matrix.summary().decodedBytes();if(decodedRemaining<1)throw new IOException("No decoded capacity remains for complete scVI metadata");
        if(matrix.fileHashes().size()+7>limits.files())throw new IOException("Combined scVI preparation member capacity cannot cover metadata");
        long retained=matrix.summary().reservedRetainedBytes()+92L*matrix.cells().size()+8L*limits.lineCharacters()+2_097_152L;
        if(retained>limits.retainedBytes())throw new IOException("Combined scVI retained capacity cannot cover complete cell-ID join");
        Files.createDirectory(root);var source=ScviText.copy(metadata,root.resolve("metadata.input"),Math.min(limits.inputBytes()-encoded,limits.workspaceBytes()-matrixBytes),check);
        var bytes=new Bytes(Math.addExact(matrixBytes,source.bytes()),limits.workspaceBytes());var decoded=new ScviText.DecodedBudget(decodedRemaining);
        var cellIndex=new HashMap<String,Integer>();for(int i=0;i<matrix.cells().size();i++)cellIndex.put(matrix.cells().get(i),i);
        long[] offsets=new long[matrix.cells().size()];Arrays.fill(offsets,-1);int[] lengths=new int[offsets.length];
        List<String> columns;var observed=new LinkedHashMap<String,Map<String,Long>>();long rounded=0,underflow=0,rows=0;Path body=root.resolve("metadata-body.tmp");Files.createFile(body);
        try(var input=new ScviText.Lines(new ScviText.Source(source.copy(),source.encoding()),decoded,limits.lineCharacters(),check);
            var temporary=new RandomAccessFile(body.toFile(),"rw");var conversion=Files.newOutputStream(root.resolve("covariate-conversion.tsv"),StandardOpenOption.CREATE_NEW)) {
            String first=input.next();if(first==null)throw new IOException("Missing scVI metadata header");columns=List.of(first.split("\t",-1));
            if(columns.size()>spec.maximumColumns()||!columns.get(0).equals("cell_id")||new HashSet<>(columns).size()!=columns.size())throw new IOException("Metadata requires unique columns starting with cell_id within declared capacity");
            for(String name:columns)if(name.isBlank()||invalidField(name)||name.startsWith("_scvi"))throw new IOException("Invalid or upstream-reserved scVI metadata column: "+name);
            for(String key:spec.categoricalColumns()){if(!columns.contains(key))throw new IOException("Missing declared categorical/batch/label column: "+key);observed.put(key,new LinkedHashMap<>());}
            int[] continuous=new int[spec.continuous().size()];for(int i=0;i<continuous.length;i++){continuous[i]=columns.indexOf(spec.continuous().get(i));if(continuous[i]<0)throw new IOException("Missing continuous scVI column: "+spec.continuous().get(i));}
            byte[] auditHeader="cell_id\tcolumn\tsource_float64\tmodel_float32\tchange\n".getBytes(StandardCharsets.UTF_8);bytes.add(auditHeader.length);conversion.write(auditHeader);String line;
            while((line=input.next())!=null){
                check.run();String[] fields=line.split("\t",-1);if(fields.length!=columns.size())throw new IOException("scVI metadata row width differs from its header");Integer index=cellIndex.get(fields[0]);
                if(index==null||offsets[index]>=0)throw new IOException("Unknown or duplicate scVI metadata cell: "+fields[0]);
                for(String field:fields)if(invalidField(field))throw new IOException("Control character in scVI metadata value");
                for(var e:observed.entrySet()){
                    String value=fields[columns.indexOf(e.getKey())];if(value.isBlank())throw new IOException("Missing categorical/batch/label value for cell "+fields[0]+",column "+e.getKey());
                    if(!e.getValue().containsKey(value)){retained=Math.addExact(retained,160L+4L*value.length());if(retained>limits.retainedBytes())throw new IOException("Complete scVI metadata category census exceeds retained capacity");}
                    e.getValue().merge(value,1L,Math::addExact);
                }
                if(spec.labels()!=null){String label=fields[columns.indexOf(spec.labels())];if(spec.labelUse()==Labels.UNLABELED_QUERY&&!label.equals(spec.unlabeledCategory()))throw new IOException("Explicit unlabeled-only query has a labeled cell: "+fields[0]);if(spec.labelUse()==Labels.KNOWN_REFERENCE_QUERY&&!label.equals(spec.unlabeledCategory())&&!spec.knownReferenceLabels().contains(label))throw new IOException("Query label is outside admitted reference categories: "+label);}
                for(int col:continuous){
                    double value=number(fields[col]);float model=(float)value;if(!Float.isFinite(model))throw new IOException("Continuous scVI covariate overflows model float32: "+columns.get(col));
                    if((double)model!=value){
                        if(spec.continuousPrecision()==ScviMatrix.Precision.EXACT_FLOAT32)throw new IOException("Continuous covariate requires explicitly reviewed float32 rounding: "+columns.get(col));
                        rounded++;boolean zero=model==0&&value!=0;if(zero)underflow++;
                        byte[] entry=(fields[0]+"\t"+columns.get(col)+"\t"+value+"\t"+(double)model+"\t"+(zero?"UNDERFLOW_TO_ZERO":"ROUNDED_FLOAT32")+"\n").getBytes(StandardCharsets.UTF_8);bytes.add(entry.length);conversion.write(entry);
                    }
                    fields[col]=Double.toString((double)model);
                }
                byte[] row=(String.join("\t",fields)+"\n").getBytes(StandardCharsets.UTF_8);bytes.add(row.length);offsets[index]=temporary.getFilePointer();lengths[index]=row.length;temporary.write(row);rows++;
            }
            if(rows!=matrix.cells().size())for(int i=0;i<offsets.length;i++)if(offsets[i]<0)throw new IOException("Missing scVI metadata for complete matrix cell: "+matrix.cells().get(i));
            if(spec.labelUse()==Labels.REFERENCE&&observed.get(spec.labels()).keySet().stream().allMatch(x->x.equals(spec.unlabeledCategory())))throw new IOException("Reference scANVI input has no labeled cells");
            byte[] header=(String.join("\t",columns)+"\n").getBytes(StandardCharsets.UTF_8);bytes.add(header.length+temporary.length());
            try(var out=Files.newOutputStream(root.resolve("metadata.tsv"),StandardOpenOption.CREATE_NEW)){
                out.write(header);for(int i=0;i<offsets.length;i++){check.run();temporary.seek(offsets[i]);byte[] row=new byte[lengths[i]];temporary.readFully(row);out.write(row);}
            }
        }catch(IllegalArgumentException|ArithmeticException invalid){throw new IOException("Invalid scVI metadata numeric value/capacity: "+invalid.getMessage(),invalid);}
        long bodyBytes=Files.size(body);Files.delete(body);bytes.release(bodyBytes);
        var categorySizes=new LinkedHashMap<String,Integer>();
        try(var out=Files.newOutputStream(root.resolve("category-counts.tsv"),StandardOpenOption.CREATE_NEW)){
            byte[] header="column\tobserved_value\tcells\n".getBytes(StandardCharsets.UTF_8);bytes.add(header.length);out.write(header);
            for(var column:observed.entrySet()){categorySizes.put(column.getKey(),column.getValue().size());for(var entry:column.getValue().entrySet()){check.run();byte[] row=(column.getKey()+"\t"+entry.getKey()+"\t"+entry.getValue()+"\n").getBytes(StandardCharsets.UTF_8);bytes.add(row.length);out.write(row);}}
        }
        var audit=new LinkedHashMap<String,Object>();audit.put("component_status","PREPARED_COVARIATES_ONLY");audit.put("model_analysis_completed",false);audit.put("cells",matrix.cells().size());audit.put("columns",columns);audit.put("batch",spec.batch());audit.put("categorical",spec.categorical());audit.put("continuous",spec.continuous());audit.put("labels",spec.labels());audit.put("label_use",spec.labelUse().name());audit.put("unlabeled_category",spec.unlabeledCategory());audit.put("observed_metadata_category_counts","category-counts.tsv");audit.put("category_cardinalities",categorySizes);audit.put("model_class_order_not_inferred",true);audit.put("rounded_covariates",rounded);audit.put("underflow_covariates",underflow);audit.put("metadata_decoded_bytes",decoded.bytes());audit.put("source",Map.of("original",source.original().toString(),"copy","metadata.input","encoding",source.encoding().name(),"bytes",source.bytes(),"sha256",source.sha256()));
        byte[] record=(RegenieRuntime.json(audit)+"\n").getBytes(StandardCharsets.UTF_8);bytes.add(record.length);Files.write(root.resolve("admission.json"),record,StandardOpenOption.CREATE_NEW);
        source.verify(limits.inputBytes(),check);matrix.verify(check);Map<String,String> files=ScviMatrix.inventory(root,limits,check);
        if(files.size()+matrix.fileHashes().size()>limits.files())throw new IOException("Combined scVI preparation member capacity exceeded");
        return new Prepared(root,matrix,source,spec,columns,rounded,underflow,observed,files);
    }
    private static void column(String name,Set<String> used){if(name==null||name.isBlank()||invalidField(name)||name.equals("cell_id")||name.startsWith("_scvi")||!used.add(name))throw new IllegalArgumentException("Covariate roles need distinct nonblank nonreserved column names");}
    private static boolean invalidField(String value){return value.codePoints().anyMatch(Character::isISOControl);}
    private static double number(String value)throws IOException{if(value.length()>128)throw new IOException("Excessive continuous covariate token");BigDecimal n=new BigDecimal(value);double d=n.doubleValue();if(!Double.isFinite(d)||d==0&&n.signum()!=0)throw new IOException("Nonfinite or float64-underflowing continuous scVI covariate");return d;}
}
