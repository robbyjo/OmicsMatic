/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Exact-ID annotation join corresponding to the gene-score portion of FUMA geneMap.R. */
public final class FumaGeneScoreAnnotations {
    private FumaGeneScoreAnnotations() { }
    /** Reject every duplicate valid resource ID, or explicitly select its first data row as R match does. */
    public enum Duplicates { ERROR, FIRST }
    /** Reject an unmatched target gene, or retain a correctly shaped all-NA annotation row. */
    public enum Missing { ERROR, RETAIN }
    /** Unquoted TSV content; source positions1..4 are metadata and positions5 onward are copied payload. */
    public record Resource(String identity,List<String> header,List<List<String>> rows) {
        public Resource {
            if(!GeneWindowMapping.token(identity))throw new IllegalArgumentException("Declare a whitespace-free gene-score resource identity/release");
            header=List.copyOf(header);if(header.size()<5||!header.contains("ensg")||new HashSet<>(header).size()!=header.size())throw new IllegalArgumentException("Gene-score resource requires at least5 distinct columns,including exact ensg key");
            for(String name:header)if(name.isBlank()||!cell(name))throw new IllegalArgumentException("Empty or control-containing gene-score column name");
            var copy=new ArrayList<List<String>>();for(var row:rows){if(row.size()!=header.size())throw new IllegalArgumentException("Gene-score resource row width differs from header");for(String value:row)if(!cell(value))throw new IllegalArgumentException("Gene-score fields must be control-free text");copy.add(List.copyOf(row));}rows=List.copyOf(copy);
        }
    }
    /** One target row; sourceLine is the one-based physical TSV line,or zero when unmatched. */
    public record Joined(int targetRow,String ensg,int sourceLine,String status,List<String> values) {
        public Joined {values=List.copyOf(values);}
    }
    /** Every source row is retained in the audit,including unused,invalid-key and ignored duplicate rows. */
    public record ResourceDisposition(int sourceLine,String ensg,String status,int firstSourceLine,int occurrences) { }
    /** Payload headers are namespaced to avoid colliding with mapping/statistical fields. */
    public record Result(List<String> outputColumns,List<Joined> joined,List<ResourceDisposition> resourceRows,List<String> warnings) {
        public Result {outputColumns=List.copyOf(outputColumns);joined=List.copyOf(joined);resourceRows=List.copyOf(resourceRows);warnings=List.copyOf(warnings);}
    }
    /** Copy raw annotation values without computing,normalizing or ranking any score.
     * @param targets exact Ensembl-like IDs,in output order; repeated target IDs remain separate
     * @param resource explicitly identified score table
     * @param duplicates whole-resource duplicate policy
     * @param missing unmatched target policy
     * @param maxCells maximum emitted payload cells,including NA fills
     * @return raw annotation values and complete join/resource ledgers
     */
    public static Result join(List<String> targets,Resource resource,Duplicates duplicates,Missing missing,long maxCells) {
        if(duplicates==null||missing==null||maxCells<0)throw new IllegalArgumentException("Require explicit gene-score duplicate/missing policies and nonnegative cell budget");
        int width=resource.header().size()-4,key=resource.header().indexOf("ensg");if((long)targets.size()*width>maxCells)throw new IllegalArgumentException("Gene-score payload cell budget exceeded;review --max-score-cells before increasing it");
        var first=new LinkedHashMap<String,Integer>();var counts=new HashMap<String,Integer>();int invalid=0,repeated=0;
        for(int i=0;i<resource.rows().size();i++){
            String id=resource.rows().get(i).get(key);if(!id(id)){invalid++;continue;}
            counts.merge(id,1,Integer::sum);if(first.putIfAbsent(id,i)!=null){repeated++;if(duplicates==Duplicates.ERROR)throw new IllegalArgumentException("Duplicate gene-score ID "+id+" at resource line "+(i+2)+";clean the resource or explicitly choose first-row policy");}
        }
        var joined=new ArrayList<Joined>();var used=new HashSet<Integer>();int unmatched=0;long missingValues=0;
        for(int i=0;i<targets.size();i++){
            String target=targets.get(i);if(!id(target))throw new IllegalArgumentException("Target gene-score IDs must be nonempty,non-NA whitespace-free tokens");Integer row=first.get(target);
            if(row==null){unmatched++;if(missing==Missing.ERROR)throw new IllegalArgumentException("Gene-score resource has no exact ID "+target+";check assembly/release/ID versions or explicitly retain unmatched NA annotations");joined.add(new Joined(i+1,target,0,"unmatched-filled-NA",Collections.nCopies(width,"NA")));missingValues+=width;}
            else {used.add(row);var values=resource.rows().get(row).subList(4,resource.header().size());long absent=values.stream().filter(v->v.isEmpty()||v.equals("NA")).count();missingValues+=absent;joined.add(new Joined(i+1,target,row+2,absent==0?"matched":"matched-with-missing-values",values));}
        }
        var dispositions=new ArrayList<ResourceDisposition>();for(int i=0;i<resource.rows().size();i++){
            String id=resource.rows().get(i).get(key);Integer index=first.get(id);String status=!id(id)?"unmatchable-ID-ignored":index!=i?"duplicate-ignored-first":used.contains(i)?"matched-first":"unused-first";dispositions.add(new ResourceDisposition(i+2,id,status,index==null?0:index+2,counts.getOrDefault(id,0)));
        }
        var columns=resource.header().subList(4,resource.header().size()).stream().map(name->"score."+name).toList();var warnings=new ArrayList<String>();
        warnings.add("GENE-SCORE ANNOTATIONS ONLY: copied columns5 onward from declared resource "+resource.identity()+" using exact case/version-sensitive ensg matches. Values remain raw text and do not calculate,validate,recalibrate or rank any score. Required action: confirm resource assembly/release,column definitions,units and original missingness;read both join ledgers. Names use score. prefix to avoid overwriting statistical fields.");
        if(repeated>0)warnings.add("FIRST GENE-SCORE ROW SELECTED: "+repeated+" duplicate valid-ID resource rows were ignored as explicitly requested,including duplicates for unused genes. Required action: review source-line/first-line/occurrence ledgers and confirm this order-dependent R-match policy;choose error to require unique IDs.");
        if(invalid>0)warnings.add("UNMATCHABLE GENE-SCORE IDs: "+invalid+" blank,NA or non-token resource IDs were not indexed. Required action: inspect the full resource ledger and explicitly clean malformed keys;no ID trimming,case folding or version stripping is inferred.");
        if(unmatched>0)warnings.add("MISSING GENE-SCORE ANNOTATIONS: "+unmatched+" target rows have all-NA payloads under the explicit retain policy. Required action: inspect exact IDs and resource coverage;missing annotations do not mean a score of zero or absence of gene association.");
        if(missingValues>0)warnings.add("MISSING SCORE CELLS: "+missingValues+" emitted cells are raw empty/NA values or unmatched fills. Required action: retain missingness;do not replace it with zero or reinterpret copied text as validated numeric statistics.");
        return new Result(columns,joined,dispositions,warnings);
    }
    private static boolean id(String value){return value!=null&&!value.equals("NA")&&GeneWindowMapping.token(value);}
    private static boolean cell(String value){return value!=null&&value.codePoints().noneMatch(Character::isISOControl);}
}
