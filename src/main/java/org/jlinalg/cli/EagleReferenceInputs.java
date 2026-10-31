/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jlinalg.genetics.EagleGeneticMap;
import org.jlinalg.genetics.EagleGenotypes;
import org.jlinalg.genetics.EagleReferenceJoin;
import org.jlinalg.genetics.VcfCsiIndex;

/** Complete bounded reference VCF admission, original-order stages and source-ordered output plan. */
final class EagleReferenceInputs {
    private EagleReferenceInputs() { }
    enum Role { REFERENCE, TARGET, EXCLUSION }
    enum Unmatched { REFUSE, DROP_WITH_WARNING, KEEP_UNPHASED }
    enum Expansion { REFUSE, ALLOW_ALT_EXPANSION, ALLOW_SOURCE_REF_REPLACEMENT }
    enum ReferenceFallback { REFUSE, ALLOW_ORIGINAL_WITH_WARNING }
    enum SampleOverlap { REFUSE, ALLOW_WITH_WARNING }
    record Spec(String assembly, String referenceReview, String contig, int chromosome, int chromosomeX,
            int start, int end, EagleGenotypes.Options genotypes, boolean allowReferenceAltSwap,
            Unmatched unmatched, Expansion expansion, ReferenceFallback referenceFallback, SampleOverlap sampleOverlap,
            EagleCohortInputs.PartialMissing partialMissing, EagleGeneticMap.Outside outsideMap,
            EagleGeneticMap.Limits mapLimits, EagleVcf.Limits vcfLimits, EagleReferenceJoin.Limits joinLimits,
            long retainedBytes, long ledgerBytes, long workspaceBytes) {
        Spec {
            declaration(assembly,128); declaration(referenceReview,4096); Objects.requireNonNull(genotypes,"genotypes");
            Objects.requireNonNull(unmatched,"unmatched"); Objects.requireNonNull(expansion,"expansion");
            Objects.requireNonNull(referenceFallback,"referenceFallback"); Objects.requireNonNull(sampleOverlap,"sampleOverlap");
            Objects.requireNonNull(partialMissing,"partialMissing"); Objects.requireNonNull(outsideMap,"outsideMap");
            Objects.requireNonNull(mapLimits,"mapLimits"); Objects.requireNonNull(vcfLimits,"vcfLimits"); Objects.requireNonNull(joinLimits,"joinLimits");
            if(chromosome<1||chromosomeX<1||start<1||end<start||genotypes.mode()!=EagleGenotypes.Mode.REFERENCE
                    ||genotypes.sexChromosome()!=(chromosome==chromosomeX)||retainedBytes<1||ledgerBytes<1||ledgerBytes>1000000000000L||workspaceBytes<1)
                throw new IllegalArgumentException("Invalid explicit Eagle reference mode, chromosome, region or capacities");
            Set<String> names=chromosome==chromosomeX?Set.of("X","chrX"):Set.of(Integer.toString(chromosome),"chr"+chromosome);
            if(!names.contains(contig))throw new IllegalArgumentException("Literal contig must agree with original reference-mode chromosome regions; X uses X or chrX, without alias rewriting");
            if(baseStorage(vcfLimits,mapLimits,joinLimits)>=retainedBytes)throw new IllegalArgumentException("Estimated Eagle reference header/row/map/join storage leaves no locus budget; review explicit capacities");
        }
        boolean selected(EagleVcf.Row row){return row.contig().equals(contig)&&row.position()>=start&&row.position()<=end;}
        long locusBudget(){return retainedBytes-baseStorage(vcfLimits,mapLimits,joinLimits);}
    }
    record Summary(long phasedRows,long unphasedRows,long droppedTargetRows,long referenceMissingCalls,
            long referenceUnphasedCalls,long targetFullyMissingCalls,long targetPartialCalls,long targetObservedAllelesIgnored,
            long allHaploidTargetRows,long missingTargetCallsBypassed,
            long referenceSwapRows,long expandedOutputRows,long changedTargetReferenceRows,long extrapolatedRows,
            Map<EagleReferenceJoin.Reason,Long> events) {
        Summary {events=Map.copyOf(events);}
    }
    /** exclusion is null when the complete selected exclusion population is empty. */
    record Prepared(Spec spec,Path work,Path target,Path reference,Path exclusion,Path map,Path outputPlan,
            List<String> targetSamples,List<String> referenceSamples,Map<Role,EagleVcf.Report> inputs,
            Map<Role,EagleVcf.Report> stages,EagleGeneticMap.Summary mapSummary,Summary summary,
            List<EagleCohortInputs.Snapshot> snapshots,Map<Path,String> ownedSha256,List<String> warnings) {
        Prepared {targetSamples=List.copyOf(targetSamples);referenceSamples=List.copyOf(referenceSamples);inputs=Map.copyOf(inputs);stages=Map.copyOf(stages);snapshots=List.copyOf(snapshots);ownedSha256=Map.copyOf(ownedSha256);warnings=List.copyOf(warnings);}
        void verify(Runnable check)throws IOException {
            for(var snapshot:snapshots)if(!EagleCohortInputs.digest(snapshot.original(),spec.vcfLimits().text().inputBytes(),check).equals(snapshot.sha256())
                    ||!EagleCohortInputs.digest(snapshot.copy(),spec.vcfLimits().text().inputBytes(),check).equals(snapshot.sha256()))throw new IOException("Eagle immutable reference-workflow source/snapshot changed: "+snapshot.original());
            for(var entry:ownedSha256.entrySet())if(!EagleCohortInputs.digest(entry.getKey(),spec.workspaceBytes(),check).equals(entry.getValue()))throw new IOException("Eagle admitted reference stage/plan changed: "+entry.getKey());
            EagleCohortInputs.workspace(work,spec.workspaceBytes());check.run();
        }
    }
    private record Located(EagleVcf.Row row,long stagedOrdinal) { }
    private static final class Counts {
        long phased,unphased,dropped,refMissing,refUnphased,targetFull,targetPartial,ignored,allHaploid,bypassedMissing,swaps,expanded,changedRef,extrapolated;
        int firstPosition,lastPosition;double firstMorgans,lastMorgans;
        final Map<EagleReferenceJoin.Reason,Long> events=new EnumMap<>(EagleReferenceJoin.Reason.class);
        Counts(){for(var reason:EagleReferenceJoin.Reason.values())events.put(reason,0L);}
        Summary summary(){return new Summary(phased,unphased,dropped,refMissing,refUnphased,targetFull,targetPartial,ignored,allHaploid,bypassedMissing,swaps,expanded,changedRef,extrapolated,events);}
    }
    private static final class Memory {
        private final long maximum;private final int maximumRecords;private long bytes;private int records;
        Memory(Spec spec){maximum=spec.locusBudget();maximumRecords=spec.joinLimits().maximumRecords();}
        void retain(EagleVcf.Row row)throws IOException {
            if(records++==maximumRecords)throw new IOException("Complete same-position Eagle row capacity exceeded before buffering a locus");
            long chars=row.contig().length()+(long)row.id().length()+row.reference().length()+row.alternate().length()+row.info().length()+row.quality().length()+row.filter().length();
            for(String value:row.originalSampleFields())chars=Math.addExact(chars,value.length());for(String value:row.formats())chars=Math.addExact(chars,value.length());
            bytes=Math.addExact(bytes,Math.addExact(512L+320L*row.calls().size(),Math.multiplyExact(8L,chars)));
            if(bytes>maximum)throw new IOException("Estimated retained same-position Eagle row storage exceeds declared capacity; review limits or resolve the locus explicitly");
        }
    }
    private static final class Cursor implements AutoCloseable {
        final Role role;final EagleVcf.Reader reader;final BufferedWriter stage;final Spec spec;final BufferedWriter ledger;final Runnable check;
        EagleVcf.Row next;long staged;
        Cursor(Role role,Path source,Path destination,Spec spec,EagleCohortInputs.Budget budget,BufferedWriter ledger,Runnable check)throws IOException {
            this.role=role;this.spec=spec;this.ledger=ledger;this.check=check;
            reader=role==Role.EXCLUSION?EagleVcf.openSites(source,spec.vcfLimits(),check):EagleVcf.open(source,spec.vcfLimits(),check);
            BufferedWriter opened=null;
            try {opened=EagleCohortInputs.writer(destination,spec.vcfLimits().text().stagedBytes(),true,budget);for(String h:reader.header()){opened.write(h);opened.write('\n');}stage=opened;next=nextSelected();}
            catch(IOException|RuntimeException failure){try{reader.close();}catch(IOException close){failure.addSuppressed(close);}if(opened!=null)try{opened.close();}catch(IOException close){failure.addSuppressed(close);}throw failure;}
        }
        private EagleVcf.Row nextSelected()throws IOException {
            EagleVcf.Row row;
            while((row=reader.next())!=null) {
                check.run();boolean selected=spec.selected(row);
                String reason=selected?"EXPLICIT_REGION":row.contig().equals(spec.contig())?"OUTSIDE_REGION":"OUTSIDE_LITERAL_CONTIG";
                ledger.write(role+"\t"+row.row()+"\t"+row.identity()+"\t"+reason+"\n");
                if(selected)return row;
            }
            return null;
        }
        List<Located> group(int position,Memory memory)throws IOException {
            var rows=new ArrayList<Located>();
            while(next!=null&&next.position()==position) {
                memory.retain(next);stage.write(EagleCohortInputs.text(next));stage.write('\n');rows.add(new Located(next,++staged));next=nextSelected();
            }
            return rows;
        }
        @Override public void close()throws IOException {try{stage.close();}finally{reader.close();}}
    }

    static Prepared prepare(Path targetSource,Path referenceSource,Path exclusionSource,Path mapSource,Path newWork,Spec spec,Runnable check)throws IOException {
        Objects.requireNonNull(spec,"spec");Objects.requireNonNull(check,"check");check.run();Path work=newWork.toAbsolutePath().normalize();
        if(work.getParent()==null||!work.getParent().toRealPath().equals(work.getParent()))throw new IOException("Eagle reference preparation requires a canonical parent and a NEW owned output directory");
        Files.createDirectory(work);Path snapshots=Files.createDirectory(work.resolve("input-snapshots"));var budget=new EagleCohortInputs.Budget(spec.workspaceBytes());
        var protectedSources=new ArrayList<EagleCohortInputs.Snapshot>();var sources=new EnumMap<Role,EagleCohortInputs.Snapshot>(Role.class);
        sources.put(Role.TARGET,EagleCohortInputs.snapshot(targetSource,snapshots.resolve("target.vcf-source"),spec.vcfLimits().text().inputBytes(),budget,check));
        sources.put(Role.REFERENCE,EagleCohortInputs.snapshot(referenceSource,snapshots.resolve("reference.vcf-source"),spec.vcfLimits().text().inputBytes(),budget,check));
        if(exclusionSource!=null)sources.put(Role.EXCLUSION,EagleCohortInputs.snapshot(exclusionSource,snapshots.resolve("exclusion.vcf-source"),spec.vcfLimits().text().inputBytes(),budget,check));
        protectedSources.addAll(sources.values());Path originalMap=mapSource.toAbsolutePath().normalize();MinimacVcf.regular(originalMap,spec.vcfLimits().text().inputBytes());boolean gzip=EagleCohortInputs.gzip(originalMap);
        var protectedMap=EagleCohortInputs.snapshot(originalMap,snapshots.resolve(gzip?"genetic-map-source.txt.gz":"genetic-map-source.txt"),spec.vcfLimits().text().inputBytes(),budget,check);protectedSources.add(protectedMap);
        if(EagleCohortInputs.gzip(protectedMap.copy())!=gzip)throw new IOException("Reference genetic-map encoding changed while protecting its snapshot");
        EagleGeneticMap map;try(Reader decoded=EagleCohortInputs.decoded(protectedMap.copy())){map=EagleGeneticMap.read(decoded,spec.chromosome(),spec.outsideMap(),spec.mapLimits(),check);}
        Path target=work.resolve("target.vcf.gz"),reference=work.resolve("reference.vcf.gz"),exclusion=exclusionSource==null?null:work.resolve("exclusion.vcf.gz"),plan=work.resolve("expected-output-plan.tsv");
        var counts=new Counts();var inputs=new EnumMap<Role,EagleVcf.Report>(Role.class);List<String> targetSamples,referenceSamples;long overlap;
        try(var admission=EagleCohortInputs.writer(work.resolve("input-admission.tsv"),spec.ledgerBytes(),false,budget);
                var joins=EagleCohortInputs.writer(work.resolve("variant-join.tsv"),spec.ledgerBytes(),false,budget);
                var outputPlan=EagleCohortInputs.writer(plan,spec.ledgerBytes(),false,budget)) {
            admission.write("role\tsource_row\tcontig\tposition\tid\tref\talt\tselection\n");
            joins.write("position\treference_row\ttarget_row\texclusion_row\treason\treference_swap\texpanded\texpected_ref\texpected_alt\toutput_action\n");
            outputPlan.write("stage_row\tsource_row\tcontig\tposition\taction\treason\texpected_ref\texpected_alt\n");
            try(var ref=new Cursor(Role.REFERENCE,sources.get(Role.REFERENCE).copy(),reference,spec,budget,admission,check);
                    var tgt=new Cursor(Role.TARGET,sources.get(Role.TARGET).copy(),target,spec,budget,admission,check);
                    var exc=exclusion==null?null:new Cursor(Role.EXCLUSION,sources.get(Role.EXCLUSION).copy(),exclusion,spec,budget,admission,check)) {
                targetSamples=tgt.reader.samples();referenceSamples=ref.reader.samples();var targetSet=new HashSet<>(targetSamples);overlap=referenceSamples.stream().filter(targetSet::contains).count();
                if(overlap!=0&&spec.sampleOverlap()==SampleOverlap.REFUSE)throw new IOException("Reference/target sample labels overlap; require reviewed ALLOW_WITH_WARNING or correct the declared panel");
                for(;;) {
                    check.run();int position=Integer.MAX_VALUE;boolean found=false;
                    for(Cursor cursor:new Cursor[]{ref,tgt,exc})if(cursor!=null&&cursor.next!=null){position=Math.min(position,cursor.next.position());found=true;}
                    if(!found)break;var memory=new Memory(spec);var rr=ref.group(position,memory);var tr=tgt.group(position,memory);var er=exc==null?List.<Located>of():exc.group(position,memory);
                    var referenceRows=byOrdinal(rr);var targetRows=byOrdinal(tr);EagleReferenceJoin.Result joined;
                    try{joined=EagleReferenceJoin.join(markers(rr),markers(tr),markers(er),spec.allowReferenceAltSwap(),spec.joinLimits(),check);}
                    catch(IllegalArgumentException invalid){throw new IOException("Eagle complete reference join failed at "+spec.contig()+":"+position+": "+invalid.getMessage(),invalid);}
                    for(var event:joined.events()) {
                        var decision=EagleReferenceJoin.decide(event,spec.allowReferenceAltSwap());counts.events.merge(decision.reason(),1L,Long::sum);
                        var action=decision.phased()?EagleVcfAudit.Action.PHASED:spec.unmatched()==Unmatched.KEEP_UNPHASED?EagleVcfAudit.Action.UNPHASED_TARGET_ONLY:EagleVcfAudit.Action.EXCLUDED;
                        if(event.target()!=null) {
                            Located located=targetRows.get(event.target().ordinal());var before=located.row();
                            if(!decision.phased()&&spec.unmatched()==Unmatched.REFUSE)throw new IOException("Unmatched/rejected target site requires an explicit drop or keep-unphased policy; source row "+before.row()+" reason "+decision.reason());
                            if(action!=EagleVcfAudit.Action.EXCLUDED&&decision.monomorphicTargetExpanded()) {
                                if(spec.expansion()==Expansion.REFUSE)throw new IOException("Monomorphic target allele expansion requires an explicit reviewed policy; source row "+before.row());
                                if(!before.reference().equals(decision.expectedTarget().reference())&&spec.expansion()!=Expansion.ALLOW_SOURCE_REF_REPLACEMENT)
                                    throw new IOException("Original Eagle would replace target REF; explicit ALLOW_SOURCE_REF_REPLACEMENT required at source row "+before.row());
                                counts.expanded++;if(!before.reference().equals(decision.expectedTarget().reference()))counts.changedRef++;
                            }
                            if(decision.phased()) {
                                admitTarget(before,spec,counts);admitReference(referenceRows.get(event.reference().ordinal()).row(),spec,counts);
                                EagleGeneticMap.Estimate genetic;try{genetic=map.estimate(position);}catch(IllegalArgumentException invalid){throw new IOException("Selected reference-mode map position refused: "+invalid.getMessage(),invalid);}
                                if(counts.phased==0){counts.firstPosition=position;counts.firstMorgans=genetic.morgans();}counts.lastPosition=position;counts.lastMorgans=genetic.morgans();counts.phased++;
                                if(genetic.region()==EagleGeneticMap.Region.BEFORE_FIRST_ROW||genetic.region()==EagleGeneticMap.Region.AFTER_LAST_ROW)counts.extrapolated++;
                                if(decision.referenceAltSwap())counts.swaps++;
                            }else if(action==EagleVcfAudit.Action.UNPHASED_TARGET_ONLY)counts.unphased++;else counts.dropped++;
                            String expectedRef=decision.expectedTarget().reference(),expectedAlt=decision.expectedTarget().alternate();
                            String line=located.stagedOrdinal()+"\t"+before.row()+"\t"+before.contig()+"\t"+position+"\t"+action+"\t"+decision.reason()+"\t"+expectedRef+"\t"+expectedAlt;
                            if(line.length()>spec.vcfLimits().text().lineCharacters())throw new IOException("Complete expected-output plan line exceeds declared text capacity");outputPlan.write(line+"\n");
                        }
                        var expected=decision.expectedTarget();joins.write(position+"\t"+ordinal(event.reference())+"\t"+ordinal(event.target())+"\t"+ordinal(event.exclusion())+"\t"+decision.reason()+"\t"+decision.referenceAltSwap()+"\t"+decision.monomorphicTargetExpanded()+"\t"+(expected==null?".":expected.reference())+"\t"+(expected==null?".":expected.alternate())+"\t"+(event.target()==null?"NO_TARGET":action)+"\n");
                    }
                }
                for(Cursor cursor:new Cursor[]{ref,tgt,exc})if(cursor!=null){if(!cursor.reader.report().complete())throw new IOException("Incomplete Eagle reference input EOF");inputs.put(cursor.role,cursor.reader.report());}
            }
        }
        if(counts.phased<2||counts.lastPosition<=counts.firstPosition||!(counts.lastMorgans>counts.firstMorgans))throw new IOException("Original reference phasing requires at least two selected markers with positive physical and genetic spans; review the region and map");
        var stages=new EnumMap<Role,EagleVcf.Report>(Role.class);var owned=new LinkedHashMap<Path,String>();Path engineExclusion=exclusion;
        for(Role role:sources.keySet()) {
            Path stage=role==Role.TARGET?target:role==Role.REFERENCE?reference:exclusion;String stageHash=EagleCohortInputs.digest(stage,spec.workspaceBytes(),check);
            var stageReport=verifyStage(sources.get(role).copy(),stage,role,spec,check);
            if(!EagleCohortInputs.digest(stage,spec.workspaceBytes(),check).equals(stageHash))throw new IOException("Eagle stage changed during complete source/stage comparison");
            stages.put(role,stageReport);owned.put(stage,stageHash);
            if(role==Role.EXCLUSION&&stageReport.variants()==0){engineExclusion=null;continue;}
            Path index=stage.resolveSibling(stage.getFileName()+".csi");VcfCsiIndex.create(stage,index);budget.add(Files.size(index));owned.put(index,EagleCohortInputs.digest(index,spec.workspaceBytes(),check));
        }
        var warnings=warnings(spec,counts,inputs,stages,overlap);Path warningFile=work.resolve("INPUT_WARNINGS.md");try(var writer=EagleCohortInputs.writer(warningFile,spec.ledgerBytes(),false,budget)){writer.write("# Eagle reference input warnings\n\n"+String.join("\n\n",warnings)+"\n");}
        for(Path path:List.of(plan,work.resolve("input-admission.tsv"),work.resolve("variant-join.tsv"),warningFile))owned.put(path,EagleCohortInputs.digest(path,spec.workspaceBytes(),check));
        var prepared=new Prepared(spec,work,target,reference,engineExclusion,protectedMap.copy(),plan,targetSamples,referenceSamples,inputs,stages,map.summary(),counts.summary(),protectedSources,owned,warnings);
        prepared.verify(check);return prepared;
    }

    /** Audit every planned target in HTS order without changing original staged target order. */
    static EagleGenotypes.Report audit(Prepared prepared,Path actualOutput,Runnable check)throws IOException {
        prepared.verify(check);Spec spec=prepared.spec();var limits=stageLimits(spec.vcfLimits());
        try(var target=EagleVcf.open(prepared.target(),limits,check);var output=EagleVcf.open(actualOutput,limits,check);var plan=new MinimacVcf.Lines(prepared.outputPlan(),planLimits(spec))) {
            if(!"stage_row\tsource_row\tcontig\tposition\taction\treason\texpected_ref\texpected_alt".equals(plan.next()))throw new IOException("Invalid complete Eagle output-plan header");
            var audit=new EagleGenotypes.Audit(target.samples(),output.samples(),spec.genotypes(),limits.text().variants(),limits.text().genotypeCells());
            EagleVcf.Row next=target.next();String nextPlan=plan.next();long expected=0,phased=0,unphased=0,dropped=0;
            while(next!=null) {
                int position=next.position();var rows=new HashMap<Long,EagleVcf.Row>();var memory=new Memory(spec);
                while(next!=null&&next.position()==position){memory.retain(next);if(rows.size()==spec.joinLimits().maximumRecords())throw new IOException("Complete Eagle audit locus record capacity exceeded");rows.put(next.row(),next);next=target.next();}
                var seen=new HashSet<Long>();
                while(nextPlan!=null) {
                    check.run();String[] f=nextPlan.split("\t",-1);if(f.length!=8)throw new IOException("Invalid complete Eagle output-plan width");
                    long ordinal=positive(f[0]);int planPosition=Math.toIntExact(positive(f[3]));if(planPosition!=position)break;
                    var before=rows.get(ordinal);if(before==null||!seen.add(ordinal)||!before.contig().equals(f[2]))throw new IOException("Output plan does not identify each complete staged target exactly once");
                    positive(f[1]);EagleVcfAudit.Action action;try{action=EagleVcfAudit.Action.valueOf(f[4]);EagleReferenceJoin.Reason.valueOf(f[5]);}catch(IllegalArgumentException invalid){throw new IOException("Invalid expected-output plan action/reason",invalid);}
                    if(action==EagleVcfAudit.Action.EXCLUDED)dropped++;
                    else {
                        var after=output.next();if(after==null)throw new IOException("Eagle reference output ended before its complete planned population");
                        String identity=before.contig()+"\t"+before.position()+"\t"+before.id()+"\t"+f[6]+"\t"+f[7];
                        try{audit.acceptDeclaredRepresentation(before.identity(),before.calls(),identity,after.identity(),after.calls(),action==EagleVcfAudit.Action.PHASED?EagleGenotypes.Disposition.PHASED:EagleGenotypes.Disposition.UNPHASED_TARGET_ONLY);}
                        catch(IllegalArgumentException|IllegalStateException invalid){throw new IOException("Eagle planned reference output contract failed: "+invalid.getMessage(),invalid);}
                        expected++;if(action==EagleVcfAudit.Action.PHASED)phased++;else unphased++;
                    }
                    nextPlan=plan.next();
                }
                if(seen.size()!=rows.size())throw new IOException("Eagle output plan omitted staged target rows at a complete locus");
            }
            if(nextPlan!=null||output.next()!=null||!target.report().complete()||!output.report().complete()||phased!=prepared.summary().phasedRows()||unphased!=prepared.summary().unphasedRows()||dropped!=prepared.summary().droppedTargetRows())
                throw new IOException("Eagle reference output or plan population differs from complete admission");
            var result=audit.finish(expected);prepared.verify(check);return result;
        }
    }

    private static void admitTarget(EagleVcf.Row row,Spec spec,Counts counts)throws IOException {
        final EagleGenotypes.Encoding encoding;try{encoding=EagleGenotypes.encodeTargets(row.calls(),spec.genotypes());}catch(IllegalArgumentException invalid){throw new IOException("Invalid selected reference-mode target GT: "+invalid.getMessage(),invalid);}
        if(encoding.partiallyMissingCalls()!=0&&spec.partialMissing()==EagleCohortInputs.PartialMissing.REFUSE)throw new IOException("Partial target GT requires explicit whole-call-missing inference policy");
        counts.targetFull+=encoding.fullyMissingCalls();counts.targetPartial+=encoding.partiallyMissingCalls();counts.ignored+=encoding.observedAllelesIgnoredByInference();
        if(encoding.framePloidy()==1){counts.allHaploid++;counts.bypassedMissing+=encoding.fullyMissingCalls();}
    }
    private static void admitReference(EagleVcf.Row row,Spec spec,Counts counts)throws IOException {
        int frame=1;for(var call:row.calls())frame=Math.max(frame,call.ploidy());if(frame==1&&!spec.genotypes().sexChromosome())throw new IOException("Original reference row requires diploid storage except on declared X");
        long missing=0,unphased=0;
        for(var call:row.calls()) {
            if(call.missing()){missing++;continue;}
            if(call.ploidy()==1&&!spec.genotypes().sexChromosome())throw new IOException("Observed haploid reference requires a correctly declared X chromosome");
            if(call.ploidy()==2&&!call.phased())unphased++;
        }
        if((missing!=0||unphased!=0)&&spec.referenceFallback()==ReferenceFallback.REFUSE)throw new IOException("Missing/unphased matched reference GT requires explicit ALLOW_ORIGINAL_WITH_WARNING or a corrected phased panel");
        counts.refMissing+=missing;counts.refUnphased+=unphased;
    }
    private static List<EagleReferenceJoin.Marker> markers(List<Located> rows){return rows.stream().map(r->new EagleReferenceJoin.Marker(r.row().row(),r.row().id(),r.row().reference(),r.row().alternate())).toList();}
    private static Map<Long,Located> byOrdinal(List<Located> rows){var result=new HashMap<Long,Located>();for(var row:rows)result.put(row.row().row(),row);return result;}
    private static String ordinal(EagleReferenceJoin.Marker row){return row==null?".":Long.toString(row.ordinal());}
    private static long positive(String text)throws IOException {try{long n=Long.parseLong(text);if(n>0)return n;}catch(NumberFormatException invalid){/* classified below */}throw new IOException("Invalid positive output-plan ordinal/position");}
    private static EagleVcf.Limits stageLimits(EagleVcf.Limits l){var t=l.text();return new EagleVcf.Limits(new MinimacVcf.Limits(t.samples(),t.variants(),t.genotypeCells(),t.stagedBytes(),t.decodedCharacters(),t.lineCharacters(),t.stagedBytes(),t.identityRows()),l.headerLines(),l.headerCharacters(),l.formatFields(),l.alternateAlleles(),l.positionKeyCharacters());}
    private static MinimacVcf.Limits planLimits(Spec spec){var t=spec.vcfLimits().text();return new MinimacVcf.Limits(t.samples(),t.variants(),t.genotypeCells(),spec.ledgerBytes(),spec.ledgerBytes(),t.lineCharacters(),t.stagedBytes(),t.identityRows());}
    private static EagleVcf.Report verifyStage(Path source,Path stage,Role role,Spec spec,Runnable check)throws IOException {
        try(var before=role==Role.EXCLUSION?EagleVcf.openSites(source,spec.vcfLimits(),check):EagleVcf.open(source,spec.vcfLimits(),check);
                var after=role==Role.EXCLUSION?EagleVcf.openSites(stage,stageLimits(spec.vcfLimits()),check):EagleVcf.open(stage,stageLimits(spec.vcfLimits()),check)) {
            if(!before.header().equals(after.header())||!before.samples().equals(after.samples()))throw new IOException("Eagle reference staging changed original headers/sample identities");
            EagleVcf.Row row;while((row=before.next())!=null)if(spec.selected(row)){var actual=after.next();if(actual==null||!EagleCohortInputs.text(row).equals(EagleCohortInputs.text(actual)))throw new IOException("Eagle reference staging changed original fields or GT at source row "+row.row());}
            if(after.next()!=null||!before.report().complete()||!after.report().complete())throw new IOException("Incomplete original-order Eagle reference stage population");return after.report();
        }
    }
    private static long baseStorage(EagleVcf.Limits v,EagleGeneticMap.Limits m,EagleReferenceJoin.Limits j){
        try{long result=0;for(long n:new long[]{Math.multiplyExact(16L,v.text().lineCharacters()),Math.multiplyExact(18L,v.headerCharacters()),Math.multiplyExact(1536L,v.text().samples()),Math.multiplyExact(32L,m.maximumSelectedRows()),Math.multiplyExact(4L,j.maximumCharacters()),Math.multiplyExact(256L,j.maximumRecords())})result=Math.addExact(result,n);return result;}
        catch(ArithmeticException overflow){throw new IllegalArgumentException("Eagle reference capacity arithmetic overflows signed64",overflow);}
    }
    private static void declaration(String text,int maximum){if(text==null||text.isBlank()||text.length()>maximum||text.codePoints().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Require explicit bounded assembly/reference review declarations");}
    private static List<String> warnings(Spec spec,Counts c,Map<Role,EagleVcf.Report> inputs,Map<Role,EagleVcf.Report> stages,long overlap) {
        var result=new ArrayList<String>();result.add("INPUT PREPARATION ONLY: no phasing process was run by preparation. Required action: review input-admission.tsv, variant-join.tsv, expected-output-plan.tsv and protected snapshots before running the admitted original engine.");
        if(c.expanded!=0)result.add("TARGET ALLELES EXPANDED: "+c.expanded+" output rows acquire reference-panel allele declarations, including "+c.changedRef+" rows whose REF changes under the explicit policy. Required action: inspect every transformation; preserved GT indices do not establish preserved nucleotide alleles.");
        if(c.refMissing!=0||c.refUnphased!=0)result.add("ORIGINAL REFERENCE FALLBACKS: "+c.refMissing+" matched reference calls contain missing alleles and "+c.refUnphased+" are unphased. Original Eagle replaces an entire missing call with reference alleles and applies its phase fallback; unphased counts include homozygotes. Required action: confirm that the explicitly allowed fallback panel is suitable, or supply a corrected complete phased panel.");
        if(c.targetPartial!=0)result.add("PARTIAL TARGET GT: "+c.targetPartial+" calls contain "+c.ignored+" supplied alleles ignored during original whole-call-missing inference. Required action: retain original calls and review missingness, imputation and source output contracts.");
        if(c.targetFull!=0||c.targetPartial!=0)result.add(spec.genotypes().missing()==EagleGenotypes.Missing.KEEP
                ?"REFERENCE NO-IMPUTATION POLICY: original diploid missing/partial target calls are cleared to fully missing output. Required action: review the complete original/output call ledger; this differs from cohort-mode partial-call retention."
                :"TARGET MISSING-CALL IMPUTATION: original inference can replace an entire partially missing call, including its supplied allele. Required action: retain observed input calls and review imputed calls separately.");
        if(c.allHaploid!=0)result.add("ALL-HAPLOID X TARGET ROWS: "+c.allHaploid+" rows are passed through unchanged by original reference output, including "+c.bypassedMissing+" missing calls even when imputation is requested. Required action: review the bypass and remaining-missing counts; these rows do not update original per-sample ploidy history.");
        if(c.dropped!=0||c.unphased!=0)result.add("UNPHASED OR EXCLUDED TARGETS: "+c.unphased+" rows are expected as original unphased output and "+c.dropped+" selected-region target rows are dropped. Required action: review the complete plan, source mode and exclusion pairing; do not treat retained unphased rows as phased results.");
        if(stages.containsKey(Role.EXCLUSION)&&stages.get(Role.EXCLUSION).variants()==0)result.add("NO SELECTED EXCLUSIONS: the supplied exclusion file contributes no sites in the declared region. Its complete original and empty stage are retained; no empty CSI or exclusion argument is sent to the engine. Required action: confirm that the contig/region and empty exclusion selection are intended.");
        if(overlap!=0)result.add("REFERENCE/TARGET LABEL OVERLAP: "+overlap+" reference labels also occur in the target under the explicitly allowed policy. Required action: verify participant eligibility and independence; label checks cannot establish biological independence.");
        if(c.extrapolated!=0)result.add("ORIGINAL MAP BOUNDARIES: "+c.extrapolated+" phased positions require the explicitly allowed original extrapolation arithmetic. Required action: review the supplied map and region or require bracketing.");
        long outside=inputs.values().stream().mapToLong(EagleVcf.Report::variants).sum()-stages.values().stream().mapToLong(EagleVcf.Report::variants).sum();
        if(outside!=0)result.add("EXPLICIT REGION EXCLUSIONS: "+outside+" original role rows lie outside the declared literal contig/region and remain in protected snapshots. Required action: review every original row in input-admission.tsv before accepting the chosen region.");
        result.add("SOURCE AND METADATA REVIEW: supplied assembly="+spec.assembly()+" and reference review="+spec.referenceReview()+" are declarations, not inferred biological validation. Original per-role row order, GT separators and opaque non-GT fields remain unchanged in stages. Required action: verify map/build, normalization, sample eligibility and any stale phase-set, likelihood, dosage or INFO annotations separately.");
        return List.copyOf(result);
    }
}
