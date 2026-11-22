/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jlinalg.genetics.FumaKnownAssociations.Decision;
import org.jlinalg.genetics.FumaKnownAssociations.Key;
import org.jlinalg.genetics.FumaKnownAssociations.Matching;

/** Declared group/key unions and explicit overlap rules for novel-versus-known tables. */
public final class FumaFisherGroups {
    private FumaFisherGroups() { }
    /** Missing targets or selected unassessable keys are refused or visibly excluded. */
    public enum Unassessable { ERROR, EXCLUDE }
    /** Shared selected keys are refused, retained in both rows, or removed from finer only. */
    public enum Overlap { ERROR, RETAIN_BOTH, FINER_ONLY }
    /** One original target row, with the classification key already used by its known-association analysis. */
    public record Observation(int inputRow,String source,String trait,String context,Key key,Decision decision) {
        public Observation {if(inputRow<1||key==null||decision==null)throw new IllegalArgumentException("Require positive source row,key and target decision");text(source);text(trait);text(context);text(key.gene());if(key.trait()!=null){text(key.trait());if(!key.trait().equals(trait))throw new IllegalArgumentException("Observation matching trait differs from its original trait");}}
    }
    /** Exact input domain and expected number of all target rows, including missing/unselected rows. */
    public record Member(String source,String trait,String context,int expectedRows) {
        public Member {text(source);text(trait);text(context);if(expectedRows<0)throw new IllegalArgumentException("Expected group member rows cannot be negative");}
    }
    /** Group membership is independent of matching-key projection and later Fisher correction families. */
    public record Group(String id,List<Member> members) {
        public Group {text(id);members=List.copyOf(members);if(members.isEmpty())throw new IllegalArgumentException("Declare group domains,including explicit zero-row members");}
    }
    /** Trait/context are output labels, not filters; only the explicit groups determine counted rows. */
    public record Comparison(String family,String id,String trait,String context,String finerGroup,
            String comparatorGroup,Overlap overlap) {
        public Comparison {text(family);text(id);text(trait);text(context);text(finerGroup);text(comparatorGroup);if(finerGroup.equals(comparatorGroup)||overlap==null)throw new IllegalArgumentException("Declare distinct finer/comparator groups and explicit overlap policy");}
    }
    /** Combined input/evidence populations and measured work are bounded, without truncation. */
    public record Limits(int maximumInputRows,long maximumEvidence,long maximumOperations) {
        public Limits {if(maximumInputRows<1||maximumEvidence<1||maximumOperations<1)throw new IllegalArgumentException("Require positive group input/evidence/work capacities");}
        /** Conservative admission capacities, not operating-system memory quotas. */
        public static Limits defaults(){return new Limits(1_000_000,2_000_000,100_000_000);}
    }
    /** Matching must equal the source classification key mode; it is never silently reprojected. */
    public record Options(Matching matching,Unassessable unassessable,Limits limits) {
        public Options {if(matching==null||unassessable==null||limits==null)throw new IllegalArgumentException("Declare matching,unassessable policy and group capacities");}
    }
    /** Original per-row evidence for every requested group, including exclusions. */
    public record Disposition(String group,Observation observation,String disposition) { }
    /** Every selected group key and all original rows contributing to it, including excluded unassessable keys. */
    public record GroupKey(String group,Key key,Decision decision,List<Integer> inputRows,boolean counted) {
        public GroupKey {inputRows=List.copyOf(inputRows);}
    }
    /** Member counts retain the complete source domain, not merely selected discoveries. */
    public record MemberAudit(String group,Member member,int actualRows) { }
    /** Group unions count each eligible key once and retain repeated source-row provenance. */
    public record GroupAudit(String group,int expectedRows,int actualRows,int selectedRows,
            int knownKeys,int novelKeys,int unassessableKeys,int missingTargetRows,int unselectedRows) { }
    /** Every shared selected key remains visible under every overlap policy. */
    public record SharedKey(String family,String comparison,Key key,List<Integer> finerRows,
            List<Integer> comparatorRows,Overlap policy,boolean excludedFromFiner) {
        public SharedKey {finerRows=List.copyOf(finerRows);comparatorRows=List.copyOf(comparatorRows);}
    }
    /** Observations extracted from a known-association result, with measured bridge work. */
    public record SourceObservations(List<Observation> observations,long operations) {
        public SourceObservations {observations=List.copyOf(observations);}
    }
    /** Complete grouping evidence and unmodified integer tables, before pseudocounts or correction. */
    public record Result(List<FumaFisherFamilies.Table> tables,List<GroupAudit> groups,
            List<MemberAudit> members,List<GroupKey> keys,List<Disposition> dispositions,
            List<SharedKey> sharedKeys,List<Observation> unassigned,List<String> warnings,long evidence,long operations) {
        public Result {tables=List.copyOf(tables);groups=List.copyOf(groups);members=List.copyOf(members);keys=List.copyOf(keys);dispositions=List.copyOf(dispositions);sharedKeys=List.copyOf(sharedKeys);unassigned=List.copyOf(unassigned);warnings=List.copyOf(warnings);}
    }
    private record Domain(String source,String trait,String context) { }
    private record TestId(String family,String id) { }
    private static final class Work {
        final Limits limits;final Runnable check;long operations,evidence;
        Work(Limits limits,Runnable check){this.limits=limits;this.check=check;}
        void add(long n){if(n>limits.maximumOperations()-operations)throw new IllegalArgumentException("Complete Fisher grouping work budget exceeded;no rows were truncated");long before=operations;operations+=n;if((before>>>10)!=(operations>>>10))check.run();}
        void evidence(long n){add(1);if(n>limits.maximumEvidence()-evidence)throw new IllegalArgumentException("Complete Fisher grouping evidence budget exceeded;no contributors or overlaps were truncated");evidence+=n;}
    }
    private static final class State {
        final Group group;final Map<Key,GroupKey> keys=new LinkedHashMap<>();
        State(Group group){this.group=group;}
    }
    /**
     * Preserve every target row and its original matching key from a native
     * known-association result. No regrouping, reclassification or P correction occurs.
     * @param source complete native known-association result
     * @param limits bridge admission/work bounds
     * @param cancellation cooperative cancellation/deadline callback
     * @return all target observations and measured extraction work
     */
    public static SourceObservations fromKnown(FumaKnownAssociations.Result source,Limits limits,Runnable cancellation){
        if(source==null||limits==null||cancellation==null)throw new IllegalArgumentException("Require known-association result,bridge limits and cancellation");cancellation.run();if((long)source.tests().size()+source.targets().size()>limits.maximumInputRows())throw new IllegalArgumentException("Known-association bridge input row budget exceeded");var work=new Work(limits,cancellation);var targetRows=new HashSet<Integer>();
        for(int i=0;i<source.tests().size();i++){work.add(1);var row=source.tests().get(i);if(row.inputRow()!=i+1)throw new IllegalArgumentException("Known-association input-row provenance is not complete and ordered");if(row.source().role()==FumaKnownAssociations.Role.TARGET)targetRows.add(row.inputRow());}
        var observations=new ArrayList<Observation>();var seen=new HashSet<Integer>();
        for(var decision:source.targets()){
            work.add(1);if(decision==null||!targetRows.contains(decision.inputRow())||!seen.add(decision.inputRow()))throw new IllegalArgumentException("Duplicate/non-target known-association decision row");var assessed=source.tests().get(decision.inputRow()-1);var test=assessed.test();if(decision.key()==null||!test.gene().equals(decision.key().gene()))throw new IllegalArgumentException("Known-association gene/key provenance differs");
            boolean missing=test.value()==null,selected=assessed.selected();if(decision.decision()==Decision.MISSING_TARGET_VALUE?!missing||selected:decision.decision()==Decision.TARGET_NOT_SELECTED?missing||selected:missing||!selected)throw new IllegalArgumentException("Known-association selection and decision provenance differ");
            observations.add(new Observation(decision.inputRow(),assessed.source().id(),test.trait(),test.context(),decision.key(),decision.decision()));
        }
        if(!seen.equals(targetRows))throw new IllegalArgumentException("Missing target decision rows in known-association bridge");cancellation.run();return new SourceObservations(observations,work.operations);
    }
    /**
     * Union selected keys within declared groups, retain exclusions/contributors,
     * and build original integer 2-by-2 tables under explicit overlap policies.
     * @param observations complete original target rows and classification keys
     * @param groups explicit domains and expected complete row counts
     * @param comparisons all planned finer/comparator group comparisons
     * @param options original matching mode,missingness policy and capacities
     * @param cancellation cooperative cancellation/deadline callback
     * @return original count tables and every source/group/key/overlap disposition
     */
    public static Result count(List<Observation> observations,List<Group> groups,List<Comparison> comparisons,Options options,Runnable cancellation){
        if(observations==null||groups==null||groups.isEmpty()||comparisons==null||options==null||cancellation==null)throw new IllegalArgumentException("Require observations,declared groups,comparisons,options and cancellation");cancellation.run();var work=new Work(options.limits(),cancellation);long input=(long)observations.size()+groups.size()+comparisons.size();var domains=new LinkedHashMap<Domain,List<Observation>>();var rowIds=new HashSet<Integer>();
        if(input>options.limits().maximumInputRows())throw new IllegalArgumentException("Combined Fisher grouping input row budget exceeded");
        for(var observation:observations){work.add(1);if(observation==null||!rowIds.add(observation.inputRow()))throw new IllegalArgumentException("Null/duplicate original grouping input row");if((observation.key().trait()==null)!=(options.matching()==Matching.GENE_ACROSS_TRAITS))throw new IllegalArgumentException("Grouping matching mode differs from the source classification keys;recompute known associations explicitly before changing projection");domains.computeIfAbsent(new Domain(observation.source(),observation.trait(),observation.context()),ignored->new ArrayList<>()).add(observation);}
        var states=new LinkedHashMap<String,State>();var audits=new ArrayList<GroupAudit>();var memberAudits=new ArrayList<MemberAudit>();var dispositions=new ArrayList<Disposition>();var allKeys=new ArrayList<GroupKey>();var usedRows=new HashSet<Integer>();
        for(var group:groups){
            work.add(1);if(group==null)throw new IllegalArgumentException("Null Fisher group");input+=group.members().size();if(input>options.limits().maximumInputRows())throw new IllegalArgumentException("Combined Fisher grouping input row budget exceeded");var state=new State(group);if(states.putIfAbsent(group.id(),state)!=null)throw new IllegalArgumentException("Duplicate Fisher group ID");var declared=new HashSet<Domain>();var decisions=new LinkedHashMap<Key,Decision>();var contributors=new LinkedHashMap<Key,List<Integer>>();int expected=0,actual=0,selected=0,missing=0,unselected=0;
            for(var member:group.members()){
                work.add(1);var domain=new Domain(member.source(),member.trait(),member.context());if(!declared.add(domain))throw new IllegalArgumentException("Duplicate declared Fisher group domain");var rows=domains.getOrDefault(domain,List.of());if(rows.size()!=member.expectedRows())throw new IllegalArgumentException("Incomplete declared group "+group.id()+" domain "+domain+": expected "+member.expectedRows()+",observed "+rows.size());expected=Math.addExact(expected,member.expectedRows());actual=Math.addExact(actual,rows.size());work.evidence(1);memberAudits.add(new MemberAudit(group.id(),member,rows.size()));
                for(var observation:rows){
                    work.add(1);usedRows.add(observation.inputRow());Decision decision=observation.decision();boolean unavailable=decision==Decision.MISSING_TARGET_VALUE||decision==Decision.UNASSESSABLE_BASELINE;
                    if(unavailable&&options.unassessable()==Unassessable.ERROR)throw new IllegalArgumentException("Unavailable target/baseline evidence requires explicit EXCLUDE group policy");
                    String disposition;if(decision==Decision.TARGET_NOT_SELECTED){unselected++;disposition="not-selected";}else if(decision==Decision.MISSING_TARGET_VALUE){missing++;disposition="missing-target-excluded";}else{
                        selected++;Decision previous=decisions.putIfAbsent(observation.key(),decision);if(previous!=null&&previous!=decision)throw new IllegalArgumentException("Conflicting selected novelty classifications within a group key;do not silently relabel known/novel/unassessable observations");work.evidence(1);contributors.computeIfAbsent(observation.key(),ignored->new ArrayList<>()).add(observation.inputRow());disposition=decision==Decision.UNASSESSABLE_BASELINE?"unassessable-key-excluded":"selected-key-contributor";
                    }
                    work.evidence(1);dispositions.add(new Disposition(group.id(),observation,disposition));
                }
            }
            int known=0,novel=0,unassessable=0;for(var entry:contributors.entrySet()){
                work.add(1);Decision decision=decisions.get(entry.getKey());boolean counted=decision!=Decision.UNASSESSABLE_BASELINE;var key=new GroupKey(group.id(),entry.getKey(),decision,entry.getValue(),counted);state.keys.put(entry.getKey(),key);work.evidence(1);allKeys.add(key);if(decision==Decision.KNOWN)known++;else if(decision==Decision.NOVEL_RELATIVE_TO_BASELINES)novel++;else unassessable++;
            }
            audits.add(new GroupAudit(group.id(),expected,actual,selected,known,novel,unassessable,missing,unselected));
        }
        var tables=new ArrayList<FumaFisherFamilies.Table>();var shared=new ArrayList<SharedKey>();var ids=new HashSet<TestId>();
        for(var comparison:comparisons){
            work.add(1);if(comparison==null||!ids.add(new TestId(comparison.family(),comparison.id())))throw new IllegalArgumentException("Null/duplicate Fisher group comparison ID");var finer=states.get(comparison.finerGroup());var comparator=states.get(comparison.comparatorGroup());if(finer==null||comparator==null)throw new IllegalArgumentException("Unknown finer/comparator group");long finerNovel=0,finerKnown=0,comparatorNovel=0,comparatorKnown=0;
            for(var key:finer.keys.values()){
                work.add(1);var other=comparator.keys.get(key.key());if(other!=null){if(key.decision()!=other.decision())throw new IllegalArgumentException("Conflicting shared-key novelty classifications across compared groups");if(comparison.overlap()==Overlap.ERROR)throw new IllegalArgumentException("Shared selected keys require explicit RETAIN_BOTH or FINER_ONLY overlap policy");work.evidence(1L+key.inputRows().size()+other.inputRows().size());shared.add(new SharedKey(comparison.family(),comparison.id(),key.key(),key.inputRows(),other.inputRows(),comparison.overlap(),comparison.overlap()==Overlap.FINER_ONLY));}
                if(!key.counted()||other!=null&&comparison.overlap()==Overlap.FINER_ONLY)continue;if(key.decision()==Decision.KNOWN)finerKnown++;else finerNovel++;
            }
            for(var key:comparator.keys.values()){work.add(1);if(!key.counted())continue;if(key.decision()==Decision.KNOWN)comparatorKnown++;else comparatorNovel++;}
            tables.add(new FumaFisherFamilies.Table(comparison.family(),comparison.id(),comparison.trait(),comparison.context(),new FumaFisherExact.Counts(finerNovel,finerKnown,comparatorNovel,comparatorKnown)));
        }
        var unassigned=new ArrayList<Observation>();for(var observation:observations){work.add(1);if(!usedRows.contains(observation.inputRow())){work.evidence(1);unassigned.add(observation);}}
        var warnings=new ArrayList<String>();warnings.add("DECLARED GROUP REVIEW: member counts include every original target row,including missing and unselected tests. Each selected matching key counts once per group and retains all contributing rows. Required action: verify domain membership,original classification key mode and complete planned comparisons;matching/group labels do not alter upstream association correction families.");
        warnings.add("OVERLAP POLICY REVIEW: FINER_ONLY removes selected comparator keys from the finer group;RETAIN_BOTH keeps shared selected keys in both rows. Required action: justify this choice and inspect every shared-key contributor;overlapping groups do not establish independent observations or historical paper equivalence.");
        if(options.unassessable()==Unassessable.EXCLUDE)warnings.add("UNAVAILABLE EVIDENCE EXCLUDED EXPLICITLY: missing target rows and selected unassessable keys remain in evidence and are excluded from original novel/known counts. Required action: review the resulting assessable population;missing or unassessable observations are never classified novel or converted to observed zeros.");
        if(audits.stream().anyMatch(a->a.knownKeys()+a.novelKeys()==0))warnings.add("EMPTY ELIGIBLE GROUP: at least one group has no assessable selected keys. Required action: preserve its empty status and declare the later Fisher empty-group policy;adding pseudocounts cannot create observed associations.");
        if(!unassigned.isEmpty())warnings.add("UNASSIGNED SOURCE ROWS: "+unassigned.size()+" original target rows fall outside every declared group and remain visible. Required action: verify intended source/trait/context membership;unassigned rows are not evidence of nonsignificance.");
        cancellation.run();return new Result(tables,audits,memberAudits,allKeys,dispositions,shared,unassigned,warnings,work.evidence,work.operations);
    }
    private static void text(String value){if(value==null||value.isBlank()||value.length()>1024||value.codePoints().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Require nonblank bounded control-free Fisher group identities");}
}
