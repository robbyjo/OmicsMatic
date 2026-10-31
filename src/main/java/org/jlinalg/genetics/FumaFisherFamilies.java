/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;

/** Complete declared Fisher families, with explicit missing and empty-group policies. */
public final class FumaFisherFamilies {
    private FumaFisherFamilies() { }
    /** No correction, or an explicitly selected complete-family correction. */
    public enum Correction { NONE, BH, BY, HOLM, BONFERRONI }
    /** Adjustment arithmetic uses original binary64 probabilities or finite natural logs. */
    public enum Encoding { P_VALUE, NATURAL_LOG }
    /** Missing complete count tables are refused or retained and excluded from adjustment. */
    public enum Missing { ERROR, EXCLUDE }
    /** Empty original rows are refused or retained with their source-style mathematical fit. */
    public enum Empty { ERROR, RETAIN_SOURCE }
    /** Every planned test ID is declared independently of its availability or later selection. */
    public record Family(String id,List<String> expectedTests,Correction correction,Encoding encoding,
            Missing missing,Empty empty,int pseudocount,double confidenceLevel) {
        public Family {
            text(id);expectedTests=List.copyOf(expectedTests);for(String test:expectedTests)text(test);
            if(correction==null||encoding==null||missing==null||empty==null||pseudocount<0||!Double.isFinite(confidenceLevel)||confidenceLevel<=0||confidenceLevel>=1)throw new IllegalArgumentException("Declare Fisher correction,encoding,missing/empty policies,pseudocount and confidence level");
        }
    }
    /** One planned test. Null counts mean unavailable data, never four observed zeros. */
    public record Table(String family,String id,String trait,String context,FumaFisherExact.Counts counts) {
        public Table {text(family);text(id);text(trait);text(context);}
    }
    /** Input, cumulative support and measured work are bounded across the whole run. */
    public record Limits(int maximumInputRows,int maximumPopulation,int maximumSupportPerTable,
            long maximumSupportStates,long maximumOperations,int maximumRootIterations) {
        public Limits {if(maximumInputRows<1||maximumPopulation<1||maximumSupportPerTable<1||maximumSupportStates<1||maximumOperations<1||maximumRootIterations<1||maximumRootIterations>100000)throw new IllegalArgumentException("Require positive bounded Fisher family capacities");}
        /** Conservative capacities; limits refuse the complete result rather than truncating. */
        public static Limits defaults(){return new Limits(100_000,10_000_000,200_000,2_000_000,1_000_000_000L,1000);}
    }
    /** Inclusive adjusted cutoff; all families are fitted and corrected before selection. */
    public record Options(double cutoff,Limits limits) {
        public Options {if(!Double.isFinite(cutoff)||cutoff<=0||cutoff>1||limits==null)throw new IllegalArgumentException("Declare Fisher cutoff in (0,1] and capacities");}
    }
    /** Availability and empty-data provenance remain independent of statistical selection. */
    public enum Status { AVAILABLE, EMPTY_ORIGINAL_GROUP_RETAINED, MISSING_COUNTS }
    /** Every input table and fit remains visible; missing tests retain null adjusted/selection fields. */
    public record Assessed(int inputRow,Table table,Family family,Status status,FumaFisherExact.Result fit,
            Double adjustedP,Double adjustedLogP,Boolean selected) { }
    /** Full declared population, with missing rows separate from the computed denominator. */
    public record FamilyAudit(String family,int expectedRows,int actualRows,int availableRows,
            int adjustmentSize,int missingRows,int emptyOriginalGroups,int selectedRows,int adjustedProbabilityUnderflows) { }
    /** Complete tables/family evidence; no display filter, historical default or grouping inference. */
    public record Result(List<Assessed> tables,List<FamilyAudit> families,List<String> warnings,
            long supportStates,long operations) {
        public Result {tables=List.copyOf(tables);families=List.copyOf(families);warnings=List.copyOf(warnings);}
    }
    private static final class Work {
        final Limits limits;final Runnable check;long operations,support;
        Work(Limits limits,Runnable check){this.limits=limits;this.check=check;}
        void add(long n){if(n>limits.maximumOperations()-operations)throw new IllegalArgumentException("Complete Fisher family operation budget exceeded;no tests were truncated");long before=operations;operations+=n;if((before>>>10)!=(operations>>>10))check.run();}
    }
    private static final class State {
        final Family family;final HashSet<String> expected=new HashSet<>(),observed=new HashSet<>();final List<Integer> rows=new ArrayList<>();int missing,empty;
        State(Family family){this.family=family;}
    }
    /**
     * Fit all declared tables, correct complete available families and select only
     * afterward. Missing and empty-row choices are explicit; pseudocounts never
     * erase the original empty-group status.
     * @param families explicit complete test registries and scientific policies
     * @param tables all planned rows, including null-count placeholders
     * @param options inclusive cutoff and complete-run capacities
     * @param cancellation cooperative cancellation/deadline callback
     * @return every test/family, adjusted values, root evidence and warnings
     */
    public static Result fit(List<Family> families,List<Table> tables,Options options,Runnable cancellation){
        if(families==null||families.isEmpty()||tables==null||options==null||cancellation==null)throw new IllegalArgumentException("Require Fisher families,tables,options and cancellation");var work=new Work(options.limits(),cancellation);cancellation.run();long input=(long)families.size()+tables.size();var states=new LinkedHashMap<String,State>();
        for(var family:families){work.add(1);if(family==null)throw new IllegalArgumentException("Null Fisher family");input+=family.expectedTests().size();if(input>options.limits().maximumInputRows())throw new IllegalArgumentException("Combined Fisher family input row budget exceeded");var state=new State(family);if(states.putIfAbsent(family.id(),state)!=null)throw new IllegalArgumentException("Duplicate Fisher family ID");for(String id:family.expectedTests()){work.add(1);if(!state.expected.add(id))throw new IllegalArgumentException("Duplicate planned test ID in Fisher family");}}
        for(int i=0;i<tables.size();i++){
            work.add(1);var table=tables.get(i);if(table==null)throw new IllegalArgumentException("Null Fisher test row");var state=states.get(table.family());if(state==null||!state.expected.contains(table.id()))throw new IllegalArgumentException("Undeclared Fisher family/test ID");if(!state.observed.add(table.id()))throw new IllegalArgumentException("Duplicate Fisher test ID within family");state.rows.add(i);
            if(table.counts()==null){state.missing++;if(state.family.missing()==Missing.ERROR)throw new IllegalArgumentException("Missing Fisher counts require explicit EXCLUDE policy");continue;}
            var counts=table.counts();counts.total();boolean empty=empty(counts);if(empty){state.empty++;if(state.family.empty()==Empty.ERROR)throw new IllegalArgumentException("Empty original Fisher group requires explicit RETAIN_SOURCE policy;adding pseudocounts does not create observed associations");}
            long add=state.family.pseudocount();var tested=new FumaFisherExact.Counts(Math.addExact(counts.finerNovel(),add),Math.addExact(counts.finerKnown(),add),Math.addExact(counts.comparatorNovel(),add),Math.addExact(counts.comparatorKnown(),add));if(tested.total()>options.limits().maximumPopulation())throw new IllegalArgumentException("Tested Fisher population including pseudocounts exceeds maximumPopulation");long first=tested.finerNovel()+tested.comparatorNovel(),second=tested.finerKnown()+tested.comparatorKnown(),row=tested.finerNovel()+tested.finerKnown(),support=Math.min(first,row)-Math.max(0,row-second)+1;
            if(support>options.limits().maximumSupportPerTable()||support>options.limits().maximumSupportStates()-work.support)throw new IllegalArgumentException("Complete Fisher family support budget exceeded;no states were truncated");work.support+=support;
        }
        for(var state:states.values()){work.add(1);if(!state.observed.equals(state.expected))throw new IllegalArgumentException("Incomplete declared Fisher family "+state.family.id()+": expected "+state.expected.size()+",observed "+state.observed.size());}
        var fits=new FumaFisherExact.Result[tables.size()];
        for(int i=0;i<tables.size();i++){
            work.add(1);var table=tables.get(i);if(table.counts()==null)continue;var family=states.get(table.family()).family;long remaining=options.limits().maximumOperations()-work.operations;if(remaining<1)throw new IllegalArgumentException("Complete Fisher family operation budget exceeded");
            fits[i]=FumaFisherExact.test(table.counts(),new FumaFisherExact.Options(family.pseudocount(),family.confidenceLevel(),new FumaFisherExact.Limits(options.limits().maximumPopulation(),options.limits().maximumSupportPerTable(),remaining,options.limits().maximumRootIterations())),cancellation);work.add(fits[i].operations());
        }
        Double[] p=new Double[tables.size()],log=new Double[tables.size()];Boolean[] selected=new Boolean[tables.size()];var audits=new ArrayList<FamilyAudit>();int adjacent=0;
        for(var state:states.values()){
            var available=new ArrayList<Integer>();for(int row:state.rows){work.add(1);if(fits[row]!=null)available.add(row);}boolean logarithmic=state.family.encoding()==Encoding.NATURAL_LOG;
            available.sort((a,b)->{work.add(1);int compare=Double.compare(logarithmic?fits[a].logPValue():fits[a].pValue(),logarithmic?fits[b].logPValue():fits[b].pValue());return compare==0?Integer.compare(a,b):compare;});int n=available.size(),underflows=0,significant=0;double factor=1;
            if(state.family.correction()==Correction.BY){factor=0;double compensation=0;for(int i=1;i<=n;i++){work.add(1);double term=1.0/i-compensation,next=factor+term;compensation=(next-factor)-term;factor=next;}}
            double previous=logarithmic?Double.NEGATIVE_INFINITY:0,next=logarithmic?0:1;
            for(int step=0;step<n;step++){
                work.add(1);boolean reverse=state.family.correction()==Correction.BH||state.family.correction()==Correction.BY;int rank=reverse?n-step-1:step,row=available.get(rank);double value=logarithmic?fits[row].logPValue():fits[row].pValue();double multiplier=switch(state.family.correction()){case NONE->1;case BH,BY->factor*n/(rank+1.0);case HOLM->n-rank;case BONFERRONI->n;};double q=logarithmic?value+Math.log(multiplier):value*multiplier;
                if(reverse){next=Math.min(next,q);q=next;}else if(state.family.correction()==Correction.HOLM){previous=Math.max(previous,q);q=previous;}
                if(logarithmic){q=Math.min(0,q);log[row]=q==0?0:q;p[row]=Math.exp(q);selected[row]=q<=Math.log(options.cutoff());}else{q=Math.min(1,q);p[row]=q==0?0:q;log[row]=q==0?Double.NEGATIVE_INFINITY:Math.log(q);selected[row]=q<=options.cutoff();}
                if(selected[row])significant++;if(p[row]==0&&Double.isFinite(log[row]))underflows++;if(Double.isFinite(log[row])&&Math.abs(log[row]-Math.log(options.cutoff()))<=16*Math.ulp(Math.log(options.cutoff())))adjacent++;
            }
            audits.add(new FamilyAudit(state.family.id(),state.expected.size(),state.rows.size(),n,n,state.missing,state.empty,significant,underflows));
        }
        var assessed=new ArrayList<Assessed>();for(int i=0;i<tables.size();i++){work.add(1);var table=tables.get(i);assessed.add(new Assessed(i+1,table,states.get(table.family()).family,table.counts()==null?Status.MISSING_COUNTS:empty(table.counts())?Status.EMPTY_ORIGINAL_GROUP_RETAINED:Status.AVAILABLE,fits[i],p[i],log[i],selected[i]));}
        var warnings=new ArrayList<String>();warnings.add("COMPLETE FISHER FAMILY REVIEW: expected IDs are investigator assertions;all available tests are corrected before inclusive selection. Required action: verify every planned ID,table orientation,original/tested counts,pseudocount,confidence level,group independence and correction family. No historical Bonferroni/BH or grouping default is inferred.");
        warnings.add("ADJUSTMENT REPRESENTATION: probability families correct source-style binary64 P;natural-log families retain finite log probabilities through underflow. Required action: preserve both raw P/log-P and declared adjustment encoding;probability underflow is not an exact zero or absent test.");
        if(audits.stream().anyMatch(a->a.missingRows()>0))warnings.add("MISSING COUNTS EXCLUDED: declared missing tables remain visible but do not enter the computed correction denominator. Required action: resolve missingness or explicitly interpret the reduced available family;missing data are not four observed zeros.");
        if(audits.stream().anyMatch(a->a.emptyOriginalGroups()>0))warnings.add("EMPTY ORIGINAL GROUPS RETAINED: explicit RETAIN_SOURCE preserves their mathematical fits,including pseudocount effects. Required action: retain empty-group provenance;selected statistics do not establish an observed biological comparison.");
        if(audits.stream().anyMatch(a->a.availableRows()==0))warnings.add("EMPTY AVAILABLE FAMILY: no available Fisher tests exist in at least one declared family. Required action: inspect planned/missing rows;an empty family is not evidence of no enrichment.");
        if(families.stream().anyMatch(f->f.correction()==Correction.NONE))warnings.add("UNCORRECTED FAMILY DECLARED: NONE preserves raw statistical values. Required action: justify the absence of multiple-testing correction and retain the complete planned family.");
        if(adjacent>0)warnings.add("CUTOFF ROUNDING REVIEW: "+adjacent+" adjusted values are near the inclusive cutoff. Required action: inspect unrounded probability/log values and native arithmetic/root boundaries before interpreting threshold-sensitive findings.");
        cancellation.run();return new Result(assessed,audits,warnings,work.support,work.operations);
    }
    private static boolean empty(FumaFisherExact.Counts counts){return Math.addExact(counts.finerNovel(),counts.finerKnown())==0||Math.addExact(counts.comparatorNovel(),counts.comparatorKnown())==0;}
    private static void text(String value){if(value==null||value.isBlank()||value.length()>1024||value.codePoints().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Require nonblank bounded control-free Fisher identities");}
}
