/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.metagenomics;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/** Native serial/unpaired/naive/readCount reassignment over an isolated upstream RMA reader.
 * Orchestration follows original January2021 RMA6Writer/DataProcessor: getter0/10,
 * readCount weight1, available-match no-hit normalization and serial support selection.
 * Weighted/long-read LCA, paired/contaminant/reference-cover/rank-identity modes
 * are not selected by this profile. Original RMA files are never rewritten.
 */
public final class Malt052Reassignment {
    private Malt052Reassignment(){ }
    public record Options(float minimumScore,float topPercent,float maximumExpected,float minimumIdentity,
            float coverage,int minimumSupport,float supportPercent,Set<Integer> disabled) {
        public Options {disabled=Set.copyOf(disabled);}
    }
    public record Row(long uid,String name,int sourceWeight,int storedTaxon,int initialTaxon,int finalTaxon,
            int totalMatches,int availableMatches,List<Integer> activeIndices) {
        public Row {activeIndices=List.copyOf(activeIndices);}
    }
    public record Result(Malt052RmaReader.Report source,int selectedMinimum,List<Row> rows,
            Map<Integer,Float> directWeights,Map<Integer,Integer> relocations,List<String> warnings) {
        public Result {rows=List.copyOf(rows);directWeights=Collections.unmodifiableMap(new TreeMap<>(directWeights));relocations=Collections.unmodifiableMap(new TreeMap<>(relocations));warnings=List.copyOf(warnings);}
    }
    public static Result reassign(Path runtime,Path input,Options options,Malt052RmaReader.Limits readLimits,
            MaltTaxonomy.Limits treeLimits)throws IOException {
        return new Session(runtime,treeLimits).reassign(input,options,readLimits);
    }
    /** Reuse the immutable source taxonomy/topology across independent RMA files or profiles. */
    public static final class Session {
      private final Path runtime;private final MaltTaxonomy tree;private final MaltSupport support;
      public Session(Path runtime,MaltTaxonomy.Limits limits)throws IOException {
        this.runtime=Objects.requireNonNull(runtime);tree=Malt052RmaReader.defaultTaxonomy(runtime,limits);support=new MaltSupport(tree);
      }
      /** Immutable complete taxonomy for validation of every index reference before fitting. */
      public MaltTaxonomy taxonomy(){return tree;}
      public Result reassign(Path input,Options options,Malt052RmaReader.Limits readLimits)throws IOException {
        Objects.requireNonNull(options);var initial=new ArrayList<Row>();var weights=new HashMap<Integer,Float>();
        var source=Malt052RmaReader.read(runtime,input,readLimits,Malt052RmaReader.SERIAL_PROCESSOR,read->{
            var matches=read.matches().stream().map(Malt052RmaReader.Match::assignmentMatch).toList();
            var assignment=Malt052Assignment.assign(tree,read.totalMatches(),matches,
                    new Malt052Assignment.Filter(options.minimumScore(),options.topPercent(),options.maximumExpected(),options.minimumIdentity()),options.disabled(),options.coverage(),true);
            int taxon=assignment.taxon();if(taxon<=0 && matches.isEmpty())taxon=-1;else if(!tree.contains(taxon))taxon=-2;
            // Original RMA6Writer selects readCount; ReadAssignmentCalculator returns1,
            // even if another raw reader/header reports a different source weight.
            if(taxon!=0)weights.merge(taxon,1f,(a,b)->a+b);
            initial.add(new Row(read.uid(),read.name(),read.weight(),read.storedTaxon(),taxon,taxon,read.totalMatches(),matches.size(),assignment.active().stream().boxed().toList()));
        });
        int minimum=MaltSupportThreshold.serial(options.supportPercent(),options.minimumSupport(),source.reads());
        Map<Integer,Integer> relocations=minimum>0 || !options.disabled().isEmpty()?support.relocate(weights,minimum,options.disabled()):Map.of();
        var rows=new ArrayList<Row>(initial.size());for(var row:initial)rows.add(new Row(row.uid(),row.name(),row.sourceWeight(),row.storedTaxon(),row.initialTaxon(),relocations.getOrDefault(row.initialTaxon(),row.initialTaxon()),row.totalMatches(),row.availableMatches(),row.activeIndices()));
        var warnings=new ArrayList<String>(source.warnings());warnings.add("PROFILE: original serial, unpaired, naive LCA with readCount weight1; no rank-identity, contaminant or reference-cover filtering. This recomputes stored RMA reads only and makes no biological admission or statement about omitted input queries.");
        return new Result(source,minimum,rows,weights,relocations,warnings);
      }
    }
}
