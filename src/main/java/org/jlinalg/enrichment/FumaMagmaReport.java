/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.enrichment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * Supplied MAGMA gene/set report postprocessing from FUMA magma_gene.R.
 * This does not fit MAGMA models. Pbon is the source's name for Holm-adjusted P,
 * with R's default family excluding missing values. Source padding and missing
 * logical-index rows are explicit report placeholders, never statistical tests.
 */
public final class FumaMagmaReport {
    private FumaMagmaReport() { }
    /** Required report fields, in the source's final display order. */
    public static final List<String> REPORT_COLUMNS=List.of("FULL_NAME","NGENES","BETA","BETA_STD","SE","P","Pbon");
    /** Repeated annotation keys require an explicit first-row choice. */
    public enum Duplicates { ERROR, SOURCE_FIRST }
    /** Missing P values require an explicit source behavior choice. */
    public enum MissingP { ERROR, SOURCE_NA }
    /** Artificial missing report rows require an explicit source shape choice. */
    public enum Placeholders { ERROR, SOURCE_ROWS }
    /** Exact annotation key and first symbol; null/NA and empty are distinct. */
    public record Gene(String id,String symbol) {
        public Gene { identifier(id);if("NA".equals(symbol))symbol=null;if(symbol!=null)field(symbol); }
    }
    /** Immutable rectangular supplied table; numeric formatting is retained. */
    public record Table(List<String> columns,List<List<String>> rows) {
        public Table {
            columns=List.copyOf(columns);if(columns.isEmpty()||new HashSet<>(columns).size()!=columns.size())throw new IllegalArgumentException("Require distinct report columns");
            for(String name:columns){field(name);if(name.isBlank())throw new IllegalArgumentException("Empty report column");}
            var copy=new ArrayList<List<String>>();for(var row:rows){if(row.size()!=columns.size())throw new IllegalArgumentException("Inconsistent report row width");var cells=List.copyOf(row);for(String value:cells)field(value);copy.add(cells);}rows=List.copyOf(copy);
        }
    }
    /** One gene-output row and its exact first annotation match, using one-based rows. */
    public record GeneJoin(int sourceRow,String gene,int annotationRow,String symbol,String status) { }
    /** Every original set row, including missing P, before any display selection. */
    public record SetTest(int sourceRow,Double p,Double holm,boolean significant) { }
    /** Display row provenance; row zero denotes padding beyond the source population. */
    public record DisplayRow(int displayRow,int sourceRow,String status) { }
    /** Complete input/output and actual work bounds, not JVM heap quotas. */
    public record Limits(int maximumRows,long maximumCells,long maximumOutputRows,long maximumOperations) {
        public Limits {
            if(maximumRows<1||maximumRows>10000000||maximumCells<1||maximumOutputRows<1||maximumOperations<1)
                throw new IllegalArgumentException("Invalid supplied MAGMA report capacities");
        }
    }
    /** All result populations are retained independently of the selected report. */
    public record Result(Table genes,List<GeneJoin> geneJoins,Table allSets,List<SetTest> allTests,
                         Table displayedSets,List<DisplayRow> displayRows,int correctionFamilySize,
                         int missingPValues,int significantSets,int duplicateAnnotationRows,
                         int missingSymbols,int emptySymbols,int placeholderRows,String displayBranch,
                         long operations) {
        public Result { geneJoins=List.copyOf(geneJoins);allTests=List.copyOf(allTests);displayRows=List.copyOf(displayRows); }
    }
    private record Match(int row,String symbol) { }
    private static final class Work {
        final Limits limits;final FumaDegEnrichment.Check check;long used;
        Work(Limits limits,FumaDegEnrichment.Check check){this.limits=limits;this.check=check;check.run();}
        void tick(){if(++used>limits.maximumOperations)throw new IllegalArgumentException("Complete MAGMA report operation capacity exceeded");if((used&1023)==0)check.run();}
    }
    /**
     * Annotate supplied gene rows and compute the full source Holm family before
     * choosing top-ten or significant report rows. Invalid P and empty set
     * populations are rejected even where the original source accepted them.
     * Only P is interpreted statistically; other fit columns are copied from
     * supplied tables and do not certify a valid original model.
     * @param geneResults supplied gene results with an exact GENE column
     * @param annotation complete first-symbol resource in original order
     * @param setResults supplied set results with the six source display columns
     * @param duplicates deliberate annotation-key multiplicity policy
     * @param missing deliberate missing-P policy
     * @param placeholders deliberate source report-shape policy
     * @param limits complete populations and work limits
     * @param check cancellation, deadline or source-identity callback
     * @return annotated genes, complete Holm tests and an auditable source report
     */
    public static Result report(Table geneResults,List<Gene> annotation,Table setResults,
                                Duplicates duplicates,MissingP missing,Placeholders placeholders,
                                Limits limits,FumaDegEnrichment.Check check) {
        if(geneResults==null||annotation==null||setResults==null||duplicates==null||missing==null||placeholders==null||limits==null||check==null)
            throw new IllegalArgumentException("Require supplied MAGMA report inputs and explicit policies");
        var work=new Work(limits,check);int genes=geneResults.rows.size(),sets=setResults.rows.size();
        if(genes>limits.maximumRows||sets>limits.maximumRows||annotation.size()>limits.maximumRows)throw new IllegalArgumentException("Complete MAGMA report input row capacity exceeded");
        long cells=Math.addExact(Math.multiplyExact((long)genes+1,geneResults.columns.size()),Math.multiplyExact((long)sets+1,setResults.columns.size()));cells=Math.addExact(cells,2L*annotation.size());
        if(cells>limits.maximumCells)throw new IllegalArgumentException("Complete MAGMA report input cell capacity exceeded");
        if(sets==0)throw new IllegalArgumentException("Empty set population: original source produces ten all-missing rows, not evidence of tested sets");
        int geneColumn=column(geneResults,"GENE");for(String name:REPORT_COLUMNS.subList(0,6))column(setResults,name);int pColumn=column(setResults,"P");
        Map<String,Match> lookup=new HashMap<>();int repeated=0;
        for(int i=0;i<annotation.size();i++){work.tick();Gene gene=annotation.get(i);if(lookup.putIfAbsent(gene.id,new Match(i+1,gene.symbol))!=null){repeated++;if(duplicates==Duplicates.ERROR)throw new IllegalArgumentException("Repeated annotation gene ID; choose explicit source-first or correct resource: "+gene.id);}}
        var geneColumns=new ArrayList<>(geneResults.columns);int symbolColumn=geneColumns.indexOf("SYMBOL");if(symbolColumn<0){symbolColumn=geneColumns.size();geneColumns.add("SYMBOL");}
        var annotated=new ArrayList<List<String>>();var joins=new ArrayList<GeneJoin>();int missingSymbols=0,emptySymbols=0;
        for(int i=0;i<genes;i++){
            work.tick();var row=new ArrayList<>(geneResults.rows.get(i));String id=row.get(geneColumn);identifier(id);Match match=lookup.get(id);String symbol=match==null?null:match.symbol;
            if(symbol==null)missingSymbols++;else if(symbol.isEmpty())emptySymbols++;if(row.size()==symbolColumn)row.add(symbol==null?"NA":symbol);else row.set(symbolColumn,symbol==null?"NA":symbol);annotated.add(row);
            joins.add(new GeneJoin(i+1,id,match==null?0:match.row,symbol,match==null?"unmatched":symbol==null?"matched-missing-symbol":symbol.isEmpty()?"matched-empty-symbol":"matched-symbol"));
        }
        Double[] p=new Double[sets],adjusted=new Double[sets];var order=new ArrayList<Integer>();int missingCount=0;
        for(int i=0;i<sets;i++){work.tick();String value=setResults.rows.get(i).get(pColumn);if(value.equals("NA")){missingCount++;if(missing==MissingP.ERROR)throw new IllegalArgumentException("Missing set P requires explicit source-na policy");}else p[i]=probability(value);order.add(i);}
        order.sort((a,b)->{work.tick();if(p[a]==null)return p[b]==null?0:1;if(p[b]==null)return -1;return p[a]<p[b]?-1:p[a]>p[b]?1:0;});
        int familySize=sets-missingCount,significant=0;double previous=0;
        for(int i=0;i<familySize;i++){work.tick();int row=order.get(i);previous=Math.max(previous,(familySize-i)*p[row]);adjusted[row]=Math.min(1,previous);if(adjusted[row]<0.05)significant++;}
        // R's lazy default n=length(p) is evaluated after its missing-P removal.
        String branch=significant<10?"source-top-ten":"source-significant";int displayCount=significant<10?10:significant+missingCount;
        if(2L*genes+2L*sets+2L*displayCount>limits.maximumOutputRows)throw new IllegalArgumentException("Complete MAGMA report tables and evidence row capacity exceeded");
        var allColumns=new ArrayList<>(setResults.columns);int adjustedColumn=allColumns.indexOf("Pbon");if(adjustedColumn<0){adjustedColumn=allColumns.size();allColumns.add("Pbon");}
        var complete=new ArrayList<List<String>>();var tests=new ArrayList<SetTest>();
        for(int i=0;i<sets;i++){work.tick();var row=new ArrayList<>(setResults.rows.get(i));String value=adjusted[i]==null?"NA":Double.toString(adjusted[i]);if(row.size()==adjustedColumn)row.add(value);else row.set(adjustedColumn,value);complete.add(row);tests.add(new SetTest(i+1,p[i],adjusted[i],adjusted[i]!=null&&adjusted[i]<0.05));}
        var displayed=new ArrayList<List<String>>();var displayEvidence=new ArrayList<DisplayRow>();int placeholderCount=0;
        if(significant<10){
            for(int i=0;i<10;i++){work.tick();if(i>=order.size()){displayed.add(missingRow());displayEvidence.add(new DisplayRow(i+1,0,"source-top-ten-padding"));placeholderCount++;}else{int row=order.get(i);displayed.add(project(complete.get(row),allColumns,work));displayEvidence.add(new DisplayRow(i+1,row+1,p[row]==null?"source-row-missing-P":"source-row"));}}
        }else{
            for(int row:order){work.tick();if(adjusted[row]==null){displayed.add(missingRow());displayEvidence.add(new DisplayRow(displayed.size(),row+1,"source-significant-branch-NA-index"));placeholderCount++;}else if(adjusted[row]<0.05){displayed.add(project(complete.get(row),allColumns,work));displayEvidence.add(new DisplayRow(displayed.size(),row+1,"source-row"));}}
        }
        if(placeholderCount>0&&placeholders==Placeholders.ERROR)throw new IllegalArgumentException("Source report contains artificial all-missing rows; choose explicit source-rows policy or inspect complete tests");
        check.run();return new Result(new Table(geneColumns,annotated),joins,new Table(allColumns,complete),tests,new Table(REPORT_COLUMNS,displayed),displayEvidence,familySize,missingCount,significant,repeated,missingSymbols,emptySymbols,placeholderCount,branch,work.used);
    }
    private static List<String> project(List<String> row,List<String> columns,Work work){var out=new ArrayList<String>();for(String name:REPORT_COLUMNS){work.tick();out.add(row.get(columns.indexOf(name)));}return out;}
    private static List<String> missingRow(){return List.of("NA","NA","NA","NA","NA","NA","NA");}
    private static int column(Table table,String name){int index=table.columns.indexOf(name);if(index<0)throw new IllegalArgumentException("Missing supplied MAGMA report column: "+name);return index;}
    private static double probability(String value){if(!value.matches("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?"))throw new IllegalArgumentException("Require finite decimal set P or exact NA");double p=Double.parseDouble(value);if(!Double.isFinite(p)||p<0||p>1)throw new IllegalArgumentException("Supplied set P must lie in [0,1]; source acceptance is not a valid probability");return p;}
    private static void identifier(String value){field(value);if(value.isBlank()||value.equals("NA"))throw new IllegalArgumentException("Require an exact nonmissing gene ID");}
    private static void field(String value){if(value==null||value.codePoints().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Invalid supplied report text field");}
}
