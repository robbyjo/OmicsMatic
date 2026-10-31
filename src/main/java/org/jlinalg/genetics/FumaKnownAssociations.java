/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Declared complete-family adjustment, baseline association unions and target review.
 * Families are supplied independently of the gene/trait matching mode. No trait or
 * gene ID conversion, effect-direction replication, two-sided P conversion, paper
 * default, or independently certified family completeness is inferred.
 */
public final class FumaKnownAssociations {
    private FumaKnownAssociations() { }
    /** Baseline sources define the known union; targets are reviewed after their own adjustment. */
    public enum Role { BASELINE, TARGET }
    /** Input values are probabilities or natural logs, with no conversion of test direction. */
    public enum Encoding { P_VALUE, NATURAL_LOG }
    /** Explicit correction, or already adjusted input used unchanged with source provenance. */
    public enum Adjustment { BH, BY, HOLM, BONFERRONI, SUPPLIED_ADJUSTED }
    /** Missing values are either rejected or explicitly excluded from the adjustment denominator. */
    public enum Missing { ERROR, EXCLUDE }
    /** Reported zero probabilities need explicit admission; lost precision is never reconstructed. */
    public enum ReportedZero { ERROR, ALLOW_REPORTED }
    /** Repeated gene/trait/context hypotheses within a family are rejected or explicitly counted. */
    public enum Duplicates { ERROR, RETAIN }
    /** Exact gene/trait pairs, or explicit gene unions across all supplied traits. */
    public enum Matching { GENE_TRAIT, GENE_ACROSS_TRAITS }
    /** Whether absent baseline tests may support reference-relative absence. Missing tests never do. */
    public enum Absence { REQUIRE_BASELINE_TESTS, SELECTED_REFERENCE }
    /** Target review states retain selection failures and incomplete baseline evidence. */
    public enum Decision { MISSING_TARGET_VALUE, TARGET_NOT_SELECTED, KNOWN,
        NOVEL_RELATIVE_TO_BASELINES, UNASSESSABLE_BASELINE }
    /** Identified analysis source; model, resource and release identities belong in its supplied ID. */
    public record Source(String id,Role role) {
        public Source {text(id);if(role==null)throw new IllegalArgumentException("Declare baseline or target source role");}
    }
    /** Exact trait/context population and expected number of test rows, including missing values. */
    public record Member(String trait,String context,int expectedRows) {
        public Member {text(trait);text(context);if(expectedRows<0)throw new IllegalArgumentException("Expected member rows cannot be negative");}
    }
    /**
     * Complete declared correction family. Members are exact trait/context groups;
     * no Cartesian gene universe or pooling across sources is inferred.
     * probabilityDefinition describes the original raw test or supplied adjustment.
     */
    public record Family(String id,String source,List<Member> members,Encoding encoding,
            Adjustment adjustment,Missing missing,ReportedZero reportedZero,Duplicates duplicates,
            String probabilityDefinition) {
        public Family {
            text(id);text(source);text(probabilityDefinition);members=List.copyOf(members);
            if(members.isEmpty()||encoding==null||adjustment==null||missing==null||reportedZero==null||duplicates==null)throw new IllegalArgumentException("Declare family members, encoding, adjustment and missing/zero/duplicate policies");
        }
    }
    /** One identified test. A null value is explicitly missing, not a null association. */
    public record Test(String family,String id,String gene,String trait,String context,Double value) {
        public Test {text(family);text(id);text(gene);text(trait);text(context);}
    }
    /** Complete input rows, union pointers/coverage/match evidence, and measured work are bounded. */
    public record Limits(int maximumInputRows,int maximumEvidence,long maximumOperations) {
        public Limits {if(maximumInputRows<1||maximumEvidence<1||maximumOperations<1)throw new IllegalArgumentException("Require positive known-association capacities");}
        /** Conservative capacities; exhaustion refuses the complete result rather than truncating. */
        public static Limits defaults(){return new Limits(1_000_000,2_000_000,100_000_000L);}
    }
    /** Inclusive adjusted-value cutoff. Selection uses each family's declared input representation. */
    public record Options(double cutoff,Matching matching,Absence absence,Limits limits) {
        public Options {if(!Double.isFinite(cutoff)||cutoff<=0||cutoff>1||matching==null||absence==null||limits==null)throw new IllegalArgumentException("Declare cutoff in (0,1], matching, absence and capacities");}
    }
    /** Matching key; trait is null only in explicit GENE_ACROSS_TRAITS mode. */
    public record Key(String gene,String trait) { }
    /** Every input test, with its original row and source, adjusted values and duplicate provenance. */
    public record Assessed(int inputRow,Test test,Source source,Family family,Double adjustedP,
            Double adjustedLogP,boolean selected,int duplicateOfInputRow,String disposition) { }
    /**
     * Full family counts before matching or union. adjustmentSize is null for
     * SUPPLIED_ADJUSTED: importing values does not certify their original denominator.
     */
    public record FamilyAudit(String family,String source,int expectedRows,int actualRows,
            int availableRows,Integer adjustmentSize,int missingRows,int reportedZeros,int duplicateRows,int selectedRows,
            int adjustedProbabilityUnderflows) { }
    /** One known key and every significant baseline input row contributing to it. */
    public record KnownUnion(Key key,List<Integer> baselineRows) {
        public KnownUnion {baselineRows=List.copyOf(baselineRows);}
    }
    /** One selected target's evidence from each declared baseline source. */
    public record BaselineCoverage(int targetRow,String source,int matchingRows,int availableRows,
            int missingRows,int selectedRows,int sourceAvailableRows) { }
    /** Every target test remains visible. matchedKnownTests is null when the target was not assessed. */
    public record TargetDecision(int inputRow,Key key,Decision decision,Integer matchedKnownTests) { }
    /** All selected baseline-to-target links, using one-based original test rows. */
    public record Link(int targetRow,int baselineRow) { }
    /** Immutable complete populations; no effect direction or biological replication is established. */
    public record Result(List<Assessed> tests,List<FamilyAudit> families,List<KnownUnion> known,
            List<TargetDecision> targets,List<BaselineCoverage> coverage,List<Link> links,
            List<String> warnings,long operations) {
        public Result {tests=List.copyOf(tests);families=List.copyOf(families);known=List.copyOf(known);targets=List.copyOf(targets);coverage=List.copyOf(coverage);links=List.copyOf(links);warnings=List.copyOf(warnings);}
    }
    private record Domain(String trait,String context) { }
    private record Cell(String gene,String trait,String context) { }
    private static final class State {
        final Family family;
        final Source source;
        final Map<Domain,Integer> expected=new LinkedHashMap<>(),observed=new LinkedHashMap<>();
        final Map<Cell,Integer> first=new LinkedHashMap<>();
        final HashSet<String> ids=new HashSet<>();
        final List<Integer> rows=new ArrayList<>();
        int totalExpected,missing,zeros,duplicates;
        State(Family family,Source source){this.family=family;this.source=source;}
    }
    private static final class Counts {int total,finite,missing,selected;}
    private static final class Work {
        final Limits limits;
        final Runnable check;
        long operations,evidence;
        Work(Limits limits,Runnable check){this.limits=limits;this.check=check;}
        void tick(){check.run();if(operations>=limits.maximumOperations())throw new IllegalArgumentException("Known-association operation budget exceeded; no rows were truncated");operations++;}
        void evidence(long count){tick();if(count>limits.maximumEvidence()-evidence)throw new IllegalArgumentException("Complete known-association evidence budget exceeded; no links were truncated");evidence+=count;}
    }
    /**
     * Correct each complete declared family, construct the significant baseline
     * union, and review every target. Member row counts include missing tests.
     * Explicit exclusion of missing values changes the correction denominator;
     * matching missing baseline values still make absence unassessable. A known
     * match is sufficient evidence even if another baseline is unavailable.
     *
     * @param sources distinct identified baseline and target analyses
     * @param families complete explicitly partitioned probability families
     * @param tests all input tests, in provenance order
     * @param options inclusive cutoff and explicit matching/absence contracts
     * @param cancellation cooperative cancellation/deadline callback
     * @return complete adjusted tests, family/union/target evidence and warnings
     */
    public static Result review(List<Source> sources,List<Family> families,List<Test> tests,
            Options options,Runnable cancellation) {
        if(sources==null||families==null||tests==null||options==null||cancellation==null||sources.isEmpty()||families.isEmpty())throw new IllegalArgumentException("Require identified sources, families, tests, options and cancellation check");
        Work work=new Work(options.limits(),cancellation);long inputRows=(long)sources.size()+families.size()+tests.size();
        if(inputRows>options.limits().maximumInputRows())throw new IllegalArgumentException("Combined known-association input row budget exceeded");
        for(Family f:families){work.tick();if(f==null)throw new IllegalArgumentException("Null family");inputRows+=f.members().size();if(inputRows>options.limits().maximumInputRows())throw new IllegalArgumentException("Combined known-association input row budget exceeded");}
        var sourceIndex=new LinkedHashMap<String,Source>();var sourceFamilies=new LinkedHashMap<String,Integer>();
        for(Source source:sources){work.tick();if(source==null||sourceIndex.putIfAbsent(source.id(),source)!=null)throw new IllegalArgumentException("Null or duplicate analysis source");sourceFamilies.put(source.id(),0);}
        if(sources.stream().noneMatch(s->s.role()==Role.BASELINE)||sources.stream().noneMatch(s->s.role()==Role.TARGET))throw new IllegalArgumentException("Declare both baseline and target source roles");
        var states=new LinkedHashMap<String,State>();
        for(Family family:families){
            work.tick();Source source=sourceIndex.get(family.source());if(source==null)throw new IllegalArgumentException("Unknown family source: "+family.source());State state=new State(family,source);if(states.putIfAbsent(family.id(),state)!=null)throw new IllegalArgumentException("Duplicate correction family ID");sourceFamilies.put(source.id(),sourceFamilies.get(source.id())+1);
            long expected=0;
            for(Member member:family.members()){work.tick();Domain key=new Domain(member.trait(),member.context());if(state.expected.putIfAbsent(key,member.expectedRows())!=null)throw new IllegalArgumentException("Duplicate declared family trait/context member");expected+=member.expectedRows();if(expected>options.limits().maximumInputRows())throw new IllegalArgumentException("Declared complete family row budget exceeded");}
            state.totalExpected=(int)expected;
        }
        if(sourceFamilies.values().stream().anyMatch(n->n==0))throw new IllegalArgumentException("Every declared source requires an explicit family, including empty sources");
        int[] duplicate=new int[tests.size()];
        for(int i=0;i<tests.size();i++){
            work.tick();Test test=tests.get(i);if(test==null)throw new IllegalArgumentException("Null test row");State state=states.get(test.family());if(state==null)throw new IllegalArgumentException("Unknown test family: "+test.family());
            if(!state.ids.add(test.id()))throw new IllegalArgumentException("Duplicate test ID within a correction family");Domain domain=new Domain(test.trait(),test.context());if(!state.expected.containsKey(domain))throw new IllegalArgumentException("Undeclared trait/context in family "+test.family());state.observed.merge(domain,1,Integer::sum);state.rows.add(i);
            Integer first=state.first.putIfAbsent(new Cell(test.gene(),test.trait(),test.context()),i+1);if(first!=null){if(state.family.duplicates()==Duplicates.ERROR)throw new IllegalArgumentException("Repeated gene/trait/context hypothesis requires explicit RETAIN policy");duplicate[i]=first;state.duplicates++;}
            if(test.value()==null){state.missing++;if(state.family.missing()==Missing.ERROR)throw new IllegalArgumentException("Missing value in declared ERROR family: "+test.family());}
            else {
                double value=test.value();boolean zero=state.family.encoding()==Encoding.P_VALUE?value==0:value==Double.NEGATIVE_INFINITY;
                if(Double.isNaN(value)||state.family.encoding()==Encoding.P_VALUE&&(!Double.isFinite(value)||value<0||value>1)||state.family.encoding()==Encoding.NATURAL_LOG&&value>0)throw new IllegalArgumentException("Invalid probability or natural-log probability in family "+test.family());
                if(zero){if(state.family.reportedZero()==ReportedZero.ERROR)throw new IllegalArgumentException("Reported zero requires explicit ALLOW_REPORTED policy");state.zeros++;}
            }
        }
        for(State state:states.values())for(var member:state.expected.entrySet()){work.tick();if(state.observed.getOrDefault(member.getKey(),0).intValue()!=member.getValue())throw new IllegalArgumentException("Incomplete declared family "+state.family.id()+" member "+member.getKey()+": expected "+member.getValue()+", observed "+state.observed.getOrDefault(member.getKey(),0));}
        Double[] adjusted=new Double[tests.size()],logs=new Double[tests.size()];boolean[] selected=new boolean[tests.size()];var audits=new ArrayList<FamilyAudit>();int nearCutoff=0,underflows=0;double logCutoff=Math.log(options.cutoff());
        for(State state:states.values()){
            work.tick();List<Integer> finite=new ArrayList<>();for(int row:state.rows){work.tick();if(tests.get(row).value()!=null)finite.add(row);}
            finite.sort((a,b)->{work.tick();int c=Double.compare(tests.get(a).value(),tests.get(b).value());return c==0?Integer.compare(a,b):c;});
            double factor=1;if(state.family.adjustment()==Adjustment.BY){factor=0;double compensation=0;for(int i=1;i<=finite.size();i++){work.tick();double term=1.0/i-compensation,nextSum=factor+term;compensation=(nextSum-factor)-term;factor=nextSum;}}
            int n=finite.size();double previous=state.family.encoding()==Encoding.P_VALUE?0:Double.NEGATIVE_INFINITY,next=state.family.encoding()==Encoding.P_VALUE?1:0;
            for(int step=0;step<n;step++){
                work.tick();boolean reverse=state.family.adjustment()==Adjustment.BH||state.family.adjustment()==Adjustment.BY;int rank=reverse?n-step-1:step,row=finite.get(rank);double value=tests.get(row).value(),multiplier=switch(state.family.adjustment()){case BH,BY->factor*n/(rank+1.0);case HOLM->n-rank;case BONFERRONI->n;case SUPPLIED_ADJUSTED->1;};
                double q=state.family.encoding()==Encoding.P_VALUE?value*multiplier:value+Math.log(multiplier);
                if(reverse){next=Math.min(next,q);q=next;}else if(state.family.adjustment()==Adjustment.HOLM){previous=Math.max(previous,q);q=previous;}
                if(state.family.encoding()==Encoding.P_VALUE){q=Math.min(1,q);if(q==0)q=0;adjusted[row]=q;logs[row]=q==0?Double.NEGATIVE_INFINITY:Math.log(q);selected[row]=q<=options.cutoff();}
                else {q=Math.min(0,q);if(q==0)q=0;logs[row]=q;adjusted[row]=Math.exp(q);selected[row]=q<=logCutoff;}
                if(Double.isFinite(logs[row])&&Math.abs(logs[row]-logCutoff)<=16*Math.ulp(logCutoff))nearCutoff++;
            }
            int familyUnderflows=0,significant=0;for(int row:finite){work.tick();if(selected[row])significant++;if(adjusted[row]==0&&Double.isFinite(logs[row]))familyUnderflows++;}
            underflows+=familyUnderflows;audits.add(new FamilyAudit(state.family.id(),state.source.id(),state.totalExpected,state.rows.size(),n,state.family.adjustment()==Adjustment.SUPPLIED_ADJUSTED?null:n,state.missing,state.zeros,state.duplicates,significant,familyUnderflows));
        }
        var assessed=new ArrayList<Assessed>();var knownRows=new LinkedHashMap<Key,List<Integer>>();var baseline=new LinkedHashMap<String,Map<Key,Counts>>();var sourceFinite=new LinkedHashMap<String,Integer>();
        for(Source source:sources)if(source.role()==Role.BASELINE){baseline.put(source.id(),new LinkedHashMap<>());sourceFinite.put(source.id(),0);}
        for(int i=0;i<tests.size();i++){
            work.tick();Test test=tests.get(i);State state=states.get(test.family());assessed.add(new Assessed(i+1,test,state.source,state.family,adjusted[i],logs[i],selected[i],duplicate[i],test.value()==null?"missing-excluded":selected[i]?"selected":"not-selected"));
            if(state.source.role()==Role.BASELINE){Key key=key(test,options.matching());Counts count=baseline.get(state.source.id()).computeIfAbsent(key,ignored->new Counts());count.total++;if(test.value()==null)count.missing++;else{count.finite++;sourceFinite.put(state.source.id(),sourceFinite.get(state.source.id())+1);}if(selected[i]){count.selected++;work.evidence(1);knownRows.computeIfAbsent(key,ignored->new ArrayList<>()).add(i+1);}}
        }
        var union=new ArrayList<KnownUnion>();for(var entry:knownRows.entrySet()){work.tick();union.add(new KnownUnion(entry.getKey(),entry.getValue()));}
        var targets=new ArrayList<TargetDecision>();var coverage=new ArrayList<BaselineCoverage>();var links=new ArrayList<Link>();
        for(Assessed test:assessed)if(test.source().role()==Role.TARGET){
            work.tick();Key key=key(test.test(),options.matching());
            if(test.test().value()==null){targets.add(new TargetDecision(test.inputRow(),key,Decision.MISSING_TARGET_VALUE,null));continue;}
            if(!test.selected()){targets.add(new TargetDecision(test.inputRow(),key,Decision.TARGET_NOT_SELECTED,null));continue;}
            boolean resolved=true;List<Integer> hits=knownRows.getOrDefault(key,List.of());
            for(var entry:baseline.entrySet()){
                work.evidence(1);Counts count=entry.getValue().getOrDefault(key,new Counts());int totalFinite=sourceFinite.get(entry.getKey());coverage.add(new BaselineCoverage(test.inputRow(),entry.getKey(),count.total,count.finite,count.missing,count.selected,totalFinite));
                if(totalFinite==0||count.missing>0||options.absence()==Absence.REQUIRE_BASELINE_TESTS&&count.finite==0)resolved=false;
            }
            for(int row:hits){work.evidence(1);links.add(new Link(test.inputRow(),row));}
            targets.add(new TargetDecision(test.inputRow(),key,!hits.isEmpty()?Decision.KNOWN:resolved?Decision.NOVEL_RELATIVE_TO_BASELINES:Decision.UNASSESSABLE_BASELINE,hits.size()));
        }
        var warnings=new ArrayList<String>();warnings.add("DECLARED FAMILY AND REFERENCE REVIEW: member counts are supplied completeness assertions, not independent certification of every paper hypothesis. Adjustment precedes selection and union. Matching mode does not change any correction family. Required action: verify source/model identities, every trait/context member and missing/zero/duplicate policy; no trait aliases, gene version stripping, effect-direction criterion or two-sided P conversion is inferred.");
        warnings.add("ENCODING AND CUTOFF: probability families use direct probability arithmetic and inclusive q<=cutoff; natural-log families use log arithmetic and inclusive log(q)<=log(cutoff). Both are retained, including finite logs below probability underflow. Required action: preserve original precision/encoding and inspect threshold-adjacent rows before interpreting rounded values.");
        if(families.stream().anyMatch(f->f.adjustment()==Adjustment.SUPPLIED_ADJUSTED))warnings.add("SUPPLIED ADJUSTED VALUES: selected families use input adjusted values unchanged. Required action: verify the original adjustment and complete family from the source; this mode does not certify its original computation.");
        if(audits.stream().anyMatch(f->f.missingRows()>0))warnings.add("MISSING FAMILY VALUES: missing rows remain in declared member counts but are excluded from the correction denominator only by explicit EXCLUDE policy. Matching missing baseline values cannot establish absence. Required action: review every missing row and its source model; missing tests are not null findings.");
        if(audits.stream().anyMatch(f->f.reportedZeros()>0)||underflows>0)warnings.add("ZERO/PRECISION REVIEW: reported input zeros and negative-infinite logs are admitted only explicitly; finite adjusted logs with displayed probability zero="+underflows+". Required action: inspect original precision and retained logs; lost log values are never reconstructed.");
        if(nearCutoff>0)warnings.add("CUTOFF ROUNDING REVIEW: "+nearCutoff+" adjusted values are within16log-scale ulps of the cutoff. Required action: inspect both representations and original source precision; no absolute tolerance or rounding is added to the inclusive selection rule.");
        if(audits.stream().anyMatch(f->f.duplicateRows()>0))warnings.add("REPEATED HYPOTHESES RETAINED: explicit RETAIN counts repeated gene/trait/context tests separately before correction and preserves their first-row provenance. Required action: justify this family definition; duplicate associations are not independent discoveries.");
        if(options.absence()==Absence.SELECTED_REFERENCE)warnings.add("SELECTED-REFERENCE ABSENCE: absent baseline keys may support reference-relative novelty only by explicit choice; matching missing values and a baseline source with no available tests remain unassessable. Required action: review baseline eligibility/universes; this is not universal novelty or proof of a null effect.");
        long unresolved=targets.stream().filter(t->t.decision()==Decision.UNASSESSABLE_BASELINE).count();
        if(unresolved>0)warnings.add("UNASSESSABLE BASELINE: "+unresolved+" selected target rows have unavailable matching baseline values, a source with no available tests, or required baseline tests absent. Required action: resolve the declared baseline population and missingness or retain these rows as unassessable; never relabel them novel.");
        if(audits.stream().anyMatch(f->f.availableRows()==0))warnings.add("EMPTY AVAILABLE CORRECTION FAMILY: at least one declared family has no available values and supplies no adjusted evidence. Required action: inspect its complete member and missing-value counts; an empty family is not evidence of absent associations.");
        if(targets.stream().noneMatch(t->t.decision()!=Decision.TARGET_NOT_SELECTED&&t.decision()!=Decision.MISSING_TARGET_VALUE))warnings.add("NO SELECTED TARGET TESTS: the baseline union is retained, but no target novelty comparison is eligible. Required action: review target families and cutoff; do not interpret an empty eligible population as absent biological associations.");
        return new Result(assessed,audits,union,targets,coverage,links,warnings,work.operations);
    }
    private static Key key(Test test,Matching matching){return new Key(test.gene(),matching==Matching.GENE_TRAIT?test.trait():null);}
    private static void text(String value){if(value==null||value.isBlank()||value.length()>1024||value.codePoints().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Require bounded nonblank control-free source/gene/trait/context identities");}
}
