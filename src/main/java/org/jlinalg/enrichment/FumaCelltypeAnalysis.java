/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.enrichment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Independent interpretation of classic FUMA cell-type MAGMA output. This class
 * does not fit MAGMA models, calculate expression or resolve gene IDs. Complete
 * marginal, within-dataset pair, cross-dataset pair and conditional-average fits
 * are supplied by the caller. Source correction families and conditional branch
 * order are retained; source numerical ambiguities are reported explicitly.
 */
public final class FumaCelltypeAnalysis {
    private FumaCelltypeAnalysis() { }
    /** R p.adjust choices; NONE is an explicit extension beyond the FUMA form. */
    public enum Correction { BONFERRONI, BH, BY, HOLM, HOCHBERG, HOMMEL, NONE }
    /** Population and computation capacities, not a JVM heap quota. */
    public record Limits(int maximumTests, int maximumModels, long maximumOperations) {
        public Limits {
            if(maximumTests<1||maximumTests>1000000||maximumModels<1||maximumModels>1000000||maximumOperations<1)
                throw new IllegalArgumentException("Invalid cell-type analysis capacities");
        }
    }
    /** Exact dataset and cell labels; Average is a conditioning variable. */
    public record Cell(String dataset,String label) {
        public Cell { token(dataset);token(label);if(label.equals("Average"))throw new IllegalArgumentException("Average is not a tested cell type"); }
    }
    /** One original fit, or an all-missing original collinearity row. */
    public record Fit(int genes,double beta,double standardizedBeta,double se,double p) {
        public Fit {
            boolean missing=Double.isNaN(beta)&&Double.isNaN(standardizedBeta)&&Double.isNaN(se)&&Double.isNaN(p);
            if(genes < -1||(!missing&&(genes<1||!Double.isFinite(beta)||!Double.isFinite(standardizedBeta)||!Double.isFinite(se)||se<=0||!Double.isFinite(p)||p<0||p>1)))
                throw new IllegalArgumentException("Require a complete finite fit or an all-missing source row");
        }
        /** Whether the original coefficient, uncertainty and P are all missing. */
        public boolean missing(){return Double.isNaN(p);}
    }
    /** Complete original marginal result, before any correction or selection. */
    public record MarginalInput(Cell cell,Fit fit) {
        public MarginalInput { if(cell==null||fit==null||fit.missing())throw new IllegalArgumentException("Marginal fits must be complete"); }
    }
    /** Source row index and the two distinct source correction families. */
    public record Marginal(int sourceRow,Cell cell,Fit fit,double adjustedPerDataset,double adjustedAcrossDatasets) { }
    /** An original two-variable joint fit; row order is retained for ties. */
    public record Pair(int model,Cell first,Fit firstFit,Cell second,Fit secondFit) {
        public Pair {
            if(model<1||first==null||second==null||first.equals(second)||firstFit==null||secondFit==null)
                throw new IllegalArgumentException("Invalid source pair model");
            if(firstFit.missing()!=secondFit.missing())throw new IllegalArgumentException("Partially missing pair has undefined original conditional decisions");
        }
    }
    /** Source marginal-P row ordering and direct proportional-significance ratios. */
    public record PairAudit(Pair pair,double firstPS,double secondPS) { }
    /** Labels and partners retain source append/overwrite order. */
    public record Summary(Marginal marginal,List<String> states,List<Cell> partners,boolean entersCrossDataset) {
        public Summary { states=List.copyOf(states);partners=List.copyOf(partners); }
    }
    /** Every significant marginal and every requested within-dataset fit. */
    public record WithinResult(boolean sourceSummaryWritten,List<Summary> summary,List<PairAudit> pairs,List<Pair> retainedPairs,List<String> diagnostics) {
        public WithinResult { summary=List.copyOf(summary);pairs=List.copyOf(pairs);retainedPairs=List.copyOf(retainedPairs);diagnostics=List.copyOf(diagnostics); }
    }
    /** A cell conditioned on both dataset averages, identified by the other dataset. */
    public record ConditionalAverage(Cell cell,String otherDataset,Fit fit) {
        public ConditionalAverage { if(cell==null||fit==null)throw new IllegalArgumentException("Missing conditional-average fit");token(otherDataset);if(cell.dataset.equals(otherDataset))throw new IllegalArgumentException("Conditional-average row requires a different dataset"); }
    }
    /** One final step-3 row; within-dataset rows have no conditional-average fit. */
    public record CrossRow(int model,Cell cell,Fit conditional,String otherDataset,Fit conditionalAverage,
                           double marginalP,double ps,double averagePS,boolean withinDataset,boolean undefinedRatio) { }
    /** Complete step-3 output and explicit source gate state. */
    public record CrossResult(boolean sourceGateOpened,List<CrossRow> rows) {
        public CrossResult { rows=List.copyOf(rows); }
    }
    private record Key(Cell first,Cell second) { }
    private record AverageKey(Cell cell,String other) { }
    private static final class Work {
        final Limits limits;final FumaDegEnrichment.Check check;long used;
        Work(Limits limits,FumaDegEnrichment.Check check){if(limits==null||check==null)throw new IllegalArgumentException("Require limits and callback");this.limits=limits;this.check=check;}
        void tick(){check.run();if(++used>limits.maximumOperations)throw new IllegalArgumentException("Complete cell-type computation budget exceeded");}
    }

    /**
     * Apply direct-double R corrections, including exact zero/one boundaries.
     * @param values complete finite probabilities, possibly an empty family
     * @param method explicit correction choice
     * @param limits full population/work bounds
     * @param check cancellation/deadline callback
     * @return adjusted values in original input order
     */
    public static double[] adjust(double[] values,Correction method,Limits limits,FumaDegEnrichment.Check check) {
        var work=new Work(limits,check);return adjust(values,method,work);
    }
    private static double[] adjust(double[] values,Correction method,Work work) {
        if(values==null||method==null||values.length>work.limits.maximumTests)throw new IllegalArgumentException("Invalid complete cell-type correction family");
        int n=values.length;Integer[] order=new Integer[n];for(int i=0;i<n;i++){work.tick();if(!Double.isFinite(values[i])||values[i]<0||values[i]>1)throw new IllegalArgumentException("Invalid source P");order[i]=i;}
        if(n==0)return new double[0];Arrays.sort(order,Comparator.comparingDouble(i->values[i]));double[] p=new double[n];for(int i=0;i<n;i++)p[i]=values[order[i]];double[] a=p.clone();
        switch(method) {
            case NONE -> { }
            case BONFERRONI -> {for(int i=0;i<n;i++){work.tick();a[i]=Math.min(1,n*p[i]);}}
            case HOLM -> {double previous=0;for(int i=0;i<n;i++){work.tick();previous=Math.max(previous,(n-i)*p[i]);a[i]=Math.min(1,previous);}}
            case HOCHBERG -> {double next=1;for(int i=n-1;i>=0;i--){work.tick();next=Math.min(next,(n-i)*p[i]);a[i]=next;}}
            case BH,BY -> {double factor=1;if(method==Correction.BY){factor=0;for(int i=1;i<=n;i++){work.tick();factor+=1.0/i;}}double next=1;for(int i=n-1;i>=0;i--){work.tick();next=Math.min(next,(factor*n/(i+1))*p[i]);a[i]=next;}}
            case HOMMEL -> {
                if((long)n*n>work.limits.maximumOperations-work.used)throw new IllegalArgumentException("Hommel computation budget exceeded before quadratic work");
                double smallest=1;for(int i=0;i<n;i++){work.tick();smallest=Math.min(smallest,n*p[i]/(i+1));}Arrays.fill(a,smallest);double[] q=a.clone();
                for(int size=n-1;size>=2;size--){double local=1;for(int i=n-size+1;i<n;i++){work.tick();local=Math.min(local,size*p[i]/(i-(n-size)+1));}for(int i=0;i<=n-size;i++){work.tick();q[i]=Math.min(size*p[i],local);}for(int i=n-size+1;i<n;i++){work.tick();q[i]=q[n-size];}for(int i=0;i<n;i++){work.tick();a[i]=Math.max(a[i],q[i]);}}
                for(int i=0;i<n;i++){work.tick();a[i]=Math.min(1,Math.max(a[i],p[i]));}
            }
        }
        double[] result=new double[n];for(int i=0;i<n;i++)result[order[i]]=a[i];return result;
    }

    /**
     * Correct all marginal tests per dataset and across datasets before filtering.
     * Dataset order is first appearance; rows within each dataset use stable P order.
     * @param inputs complete marginal population, without significance selection
     * @param correction explicit source correction method
     * @param limits full input/work capacities
     * @param check cancellation/deadline callback
     * @return immutable complete correction families in source display order
     */
    public static List<Marginal> marginal(List<MarginalInput> inputs,Correction correction,Limits limits,FumaDegEnrichment.Check check) {
        var work=new Work(limits,check);if(inputs==null||inputs.isEmpty()||inputs.size()>limits.maximumTests)throw new IllegalArgumentException("Empty or oversized complete marginal population");
        var groups=new LinkedHashMap<String,List<Integer>>();var seen=new HashSet<Cell>();for(int i=0;i<inputs.size();i++){work.tick();var row=inputs.get(i);if(!seen.add(row.cell))throw new IllegalArgumentException("Repeated dataset/cell marginal");groups.computeIfAbsent(row.cell.dataset,k->new ArrayList<>()).add(i);}
        var result=new ArrayList<Marginal>();for(var indices:groups.values()){indices.sort(Comparator.comparingDouble(i->inputs.get(i).fit.p));double[] p=indices.stream().mapToDouble(i->inputs.get(i).fit.p).toArray(),a=adjust(p,correction,work);for(int i=0;i<indices.size();i++){work.tick();int original=indices.get(i);var row=inputs.get(original);result.add(new Marginal(original+1,row.cell,row.fit,a[i],Double.NaN));}}
        double[] all=adjust(result.stream().mapToDouble(r->r.fit.p).toArray(),correction,work);for(int i=0;i<result.size();i++){var row=result.get(i);result.set(i,new Marginal(row.sourceRow,row.cell,row.fit,row.adjustedPerDataset,all[i]));}return List.copyOf(result);
    }

    /**
     * Interpret every within-dataset pair of globally significant cells ({@code P.adj < 0.05}).
     * Pair completeness is required; no missing model is silently independent.
     * @param marginals complete, already corrected step-1 output
     * @param pairs all within-dataset two-variable fits requested by that selection
     * @param limits complete populations and classification work bounds
     * @param check cancellation/deadline callback
     * @return source conditional labels, pair audits and retained step-3 eligibility
     */
    public static WithinResult within(List<Marginal> marginals,List<Pair> pairs,Limits limits,FumaDegEnrichment.Check check) {
        var work=new Work(limits,check);var all=index(marginals,limits);var significant=new LinkedHashMap<Cell,Marginal>();var groups=new LinkedHashMap<String,List<Cell>>();
        for(var row:marginals){work.tick();if(row.adjustedAcrossDatasets<.05){significant.put(row.cell,row);groups.computeIfAbsent(row.cell.dataset,k->new ArrayList<>()).add(row.cell);}}
        if(pairs==null||pairs.size()>limits.maximumModels)throw new IllegalArgumentException("Complete within-model capacity exceeded");long expected=0;for(var group:groups.values())expected+=(long)group.size()*(group.size()-1)/2;if(expected>limits.maximumModels||pairs.size()!=expected)throw new IllegalArgumentException("Require every requested within-dataset pair, with no extra models");
        var byKey=new HashMap<Key,Pair>();var models=new HashSet<String>();for(var pair:pairs){work.tick();if(!pair.first.dataset.equals(pair.second.dataset)||!significant.containsKey(pair.first)||!significant.containsKey(pair.second)||byKey.put(key(pair.first,pair.second),pair)!=null||!models.add(pair.first.dataset+":"+pair.model))throw new IllegalArgumentException("Duplicate, unselected or cross-dataset within model");}
        var states=new HashMap<Cell,List<String>>();var partners=new HashMap<Cell,List<Cell>>();var audit=new ArrayList<PairAudit>();var diagnostics=new ArrayList<String>();var names=new ArrayList<>(groups.keySet());names.sort(String::compareTo);
        for(String dataset:names) {
            List<Cell> cells=groups.get(dataset);if(cells.size()==1){replace(states,partners,cells.get(0),"single",null);continue;}
            var active=new LinkedHashMap<Integer,PairAudit>();var requested=pairs.stream().filter(p->p.first.dataset.equals(dataset)).sorted(Comparator.comparingInt(Pair::model)).toList();
            for(var pair:requested){work.tick();Pair ordered=all.get(pair.first).fit.p>all.get(pair.second).fit.p?swap(pair):pair;double left=ratio(ordered.firstFit.p,all.get(ordered.first).fit.p),right=ratio(ordered.secondFit.p,all.get(ordered.second).fit.p);if(!ordered.firstFit.missing()&&(Double.isNaN(left)||Double.isNaN(right)))throw new IllegalArgumentException("Source conditional decision undefined after zero-P underflow; no log significance can be reconstructed");var row=new PairAudit(ordered,left,right);active.put(ordered.model,row);audit.add(row);}
            var checked=new ArrayList<Cell>();while(checked.size()<cells.size()) {
                work.tick();Cell top=cells.stream().filter(c->!checked.contains(c)).findFirst().orElseThrow();var drivers=new HashSet<Cell>();
                for(var row:active.values())if(contains(row.pair,top)&&!row.pair.firstFit.missing()&&row.secondPS>=.2&&row.firstPS<.2)drivers.add(row.pair.second);
                if(!drivers.isEmpty())top=cells.stream().filter(drivers::contains).findFirst().orElseThrow();checked.add(top);
                Cell selected=top;for(var row:new ArrayList<>(active.values()).stream().filter(r->contains(r.pair,selected)).toList()) {
                    work.tick();boolean reverse=!row.pair.first.equals(top);Pair p=reverse?swap(row.pair):row.pair;double a=reverse?row.secondPS:row.firstPS,b=reverse?row.firstPS:row.secondPS;String topState=null,otherState=null;boolean drop=false,classified=true;
                    if(p.firstFit.missing()){topState="colinear";otherState="colinear-drop";drop=true;}
                    else if(a>=.8&&b>=.8){/* Source leaves both states unchanged. */}
                    else if(p.firstFit.p>=.05&&p.secondFit.p>=.05){topState="joint";otherState="joint-drop";drop=true;}
                    else if(a<.2&&b<.2){topState="partial-joint";otherState="joint-drop";drop=true;}
                    else if(a>=.5&&p.secondFit.p>=.05){topState="main";otherState="drop";drop=true;}
                    else if(a>=.8&&b<.2){topState="main";otherState="partial-drop";drop=true;}
                    else if(a>=.5&&b>=.5){topState="partial-joint";otherState="partial-joint";}
                    else if(a>=.2&&b>=.2){topState="partial-joint";otherState="partial-joint-drop";drop=true;}
                    else if(a>=.2&&p.secondFit.p>=.05){topState="partial-joint";otherState="joint-drop";drop=true;}
                    else if(a>=.2&&b<.2){topState="partial-joint";otherState="partial-joint-drop";drop=true;}
                    else {classified=false;diagnostics.add("Source unclassified conditional branch: "+dataset+" model "+p.model);}
                    if(topState!=null){append(states,partners,top,topState,p.second);replace(states,partners,p.second,otherState,top);}if(drop)checked.add(p.second);if(classified)active.remove(p.model);
                }
                active.entrySet().removeIf(e->checked.contains(e.getValue().pair.first)||checked.contains(e.getValue().pair.second));
            }
            if(new HashSet<>(checked).size()<cells.size())diagnostics.add("Source checked-list multiplicity ended selection before every unique cell: "+dataset);
        }
        var summary=new ArrayList<Summary>();var retained=new HashSet<Cell>();for(var row:significant.values()){work.tick();var labels=states.getOrDefault(row.cell,List.of("indep"));boolean eligible=labels.stream().noneMatch(v->v.contains("drop"));if(eligible)retained.add(row.cell);summary.add(new Summary(row,labels,partners.getOrDefault(row.cell,List.of()),eligible));}
        return new WithinResult(significant.size()>1,summary,audit,audit.stream().map(PairAudit::pair).filter(p->retained.contains(p.first)&&retained.contains(p.second)).toList(),diagnostics);
    }

    /**
     * Assemble all cross-dataset conditional models, then retained within models.
     * Direct source PS ratios remain NaN when zero/zero or missing, with a flag.
     * @param within step-2 selection and complete original pair audits
     * @param crossPairs complete between-dataset pairs of retained cells
     * @param averages complete fits conditioned on the two dataset averages
     * @param limits complete model/population/work capacities
     * @param check cancellation/deadline callback
     * @return complete source step-3 rows; no significance or direction filtering
     */
    public static CrossResult cross(WithinResult within,List<Pair> crossPairs,List<ConditionalAverage> averages,Limits limits,FumaDegEnrichment.Check check) {
        var work=new Work(limits,check);if(within==null||crossPairs==null||averages==null||crossPairs.size()>limits.maximumModels||averages.size()>limits.maximumTests)throw new IllegalArgumentException("Invalid complete cross-dataset inputs");
        var eligible=new LinkedHashMap<Cell,Marginal>();var groups=new LinkedHashMap<String,List<Cell>>();for(var row:within.summary)if(row.entersCrossDataset){work.tick();eligible.put(row.marginal.cell,row.marginal);groups.computeIfAbsent(row.marginal.cell.dataset,k->new ArrayList<>()).add(row.marginal.cell);}
        if(groups.size()<2){if(!crossPairs.isEmpty()||!averages.isEmpty())throw new IllegalArgumentException("Cross-dataset gate is closed; no models should have been requested");return new CrossResult(false,List.of());}
        long expected=0,expectedAverage=(long)eligible.size()*(groups.size()-1);var datasets=new ArrayList<>(groups.keySet());for(int i=0;i<datasets.size();i++)for(int j=i+1;j<datasets.size();j++)expected+=(long)groups.get(datasets.get(i)).size()*groups.get(datasets.get(j)).size();
        if(expected>limits.maximumModels||crossPairs.size()!=expected||averages.size()!=expectedAverage||(long)crossPairs.size()+within.retainedPairs.size()>limits.maximumModels)throw new IllegalArgumentException("Require complete cross-dataset and conditional-average model populations");
        var seen=new HashSet<Key>();for(var p:crossPairs){work.tick();if(p.first.dataset.equals(p.second.dataset)||!eligible.containsKey(p.first)||!eligible.containsKey(p.second)||!seen.add(key(p.first,p.second)))throw new IllegalArgumentException("Duplicate, unselected or same-dataset cross model");}
        var mean=new HashMap<AverageKey,Fit>();for(var row:averages){work.tick();if(!eligible.containsKey(row.cell)||!groups.containsKey(row.otherDataset)||mean.put(new AverageKey(row.cell,row.otherDataset),row.fit)!=null)throw new IllegalArgumentException("Duplicate or unselected conditional-average row");}
        var ordered=new ArrayList<Pair>();for(int i=0;i<datasets.size();i++)for(int j=i+1;j<datasets.size();j++){work.tick();String left=datasets.get(i),right=datasets.get(j);var selected=crossPairs.stream().filter(p->Set.of(p.first.dataset,p.second.dataset).equals(Set.of(left,right))).sorted(Comparator.comparingInt(Pair::model)).toList();var models=new HashSet<Integer>();for(var p:selected){if(!models.add(p.model))throw new IllegalArgumentException("Repeated source model ID within a dataset pair");ordered.add(p);}}
        for(String dataset:datasets)ordered.addAll(within.retainedPairs.stream().filter(p->p.first.dataset.equals(dataset)).toList());var rows=new ArrayList<CrossRow>();int model=0;for(var p:ordered){work.tick();model++;for(int side=0;side<2;side++){Cell cell=side==0?p.first:p.second,other=side==0?p.second:p.first;Fit fit=side==0?p.firstFit:p.secondFit;boolean same=cell.dataset.equals(other.dataset);Fit avg=same?null:mean.get(new AverageKey(cell,other.dataset));if(!same&&avg==null)throw new IllegalArgumentException("Missing conditional-average identity");double marginal=eligible.get(cell).fit.p,denominator=avg==null||avg.missing()?marginal:avg.p,ps=ratio(fit.p,denominator),psAverage=avg==null?Double.NaN:ratio(avg.p,marginal);rows.add(new CrossRow(model,cell,fit,other.dataset,avg,marginal,ps,psAverage,same,Double.isNaN(ps)||!same&&Double.isNaN(psAverage)));}}
        return new CrossResult(true,rows);
    }
    private static Map<Cell,Marginal> index(List<Marginal> rows,Limits limits) {
        if(rows==null||rows.isEmpty()||rows.size()>limits.maximumTests)throw new IllegalArgumentException("Require complete marginal results");var result=new LinkedHashMap<Cell,Marginal>();for(var row:rows)if(row==null||row.cell==null||row.fit==null||row.fit.missing()||!Double.isFinite(row.adjustedAcrossDatasets)||row.adjustedAcrossDatasets<0||row.adjustedAcrossDatasets>1||!Double.isFinite(row.adjustedPerDataset)||row.adjustedPerDataset<0||row.adjustedPerDataset>1||result.put(row.cell,row)!=null)throw new IllegalArgumentException("Invalid or repeated corrected marginal row");return result;
    }
    private static double ratio(double conditional,double marginal){return -Math.log10(conditional)/-Math.log10(marginal);}
    private static boolean contains(Pair pair,Cell cell){return pair.first.equals(cell)||pair.second.equals(cell);}
    private static Pair swap(Pair p){return new Pair(p.model,p.second,p.secondFit,p.first,p.firstFit);}
    private static Key key(Cell a,Cell b){return (a.dataset+":"+a.label).compareTo(b.dataset+":"+b.label)<0?new Key(a,b):new Key(b,a);}
    private static void append(Map<Cell,List<String>> states,Map<Cell,List<Cell>> partners,Cell cell,String state,Cell partner){states.computeIfAbsent(cell,k->new ArrayList<>()).add(state);partners.computeIfAbsent(cell,k->new ArrayList<>()).add(partner);}
    private static void replace(Map<Cell,List<String>> states,Map<Cell,List<Cell>> partners,Cell cell,String state,Cell partner){states.put(cell,new ArrayList<>(List.of(state)));partners.put(cell,new ArrayList<>(partner==null?List.of():List.of(partner)));}
    private static void token(String value){if(value==null||value.isBlank()||value.length()>1024||value.codePoints().anyMatch(c->Character.isWhitespace(c)||Character.isISOControl(c)||c==':'||c==','))throw new IllegalArgumentException("Require exact cell/dataset labels without whitespace, colon, comma or controls");}
}
