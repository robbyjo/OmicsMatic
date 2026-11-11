/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import java.util.*;
import java.util.function.BooleanSupplier;

/** Native original AnnotationPlot selection, exon/body/arrow geometry and source lane assignment. */
public final class SignacCoverageAnnotations {
    private SignacCoverageAnnotations() { }
    public record Annotation(String chromosome,int start,int end,String strand,String geneName,String transcriptId,String biotype,String type) {
        public Annotation {for(String n:List.of(chromosome,geneName,transcriptId,biotype,type))SignacFragments.identity(n);SignacGeneActivity.strand(strand);if(start<1||end<start||end>536_870_912)throw new IllegalArgumentException("Require positive closed source annotation coordinates within TBI domain");}
    }
    public record Segment(String chromosome,double start,double end,String strand,String geneName,String transcriptId,String biotype,String type,int dodge) { }
    public record Label(Segment body,double width,double position) { }
    public record Layout(List<Label> labels,List<Segment> exons,List<Segment> plus,List<Segment> minus,List<String> warnings) {
        public Layout{labels=List.copyOf(labels);exons=List.copyOf(exons);plus=List.copyOf(plus);minus=List.copyOf(minus);warnings=List.copyOf(warnings);}
    }
    private record Body(Annotation first,int start,int end,String key) { }
    /** Gene mode groups by literal gene_name; transcript mode groups by literal tx_id, without inferred IDs. */
    public static Layout layout(List<Annotation> annotation,SignacFragments.Region region,boolean collapseTranscripts,int maximumAnnotations,int maximumSegments,BooleanSupplier cancel) {
        if(annotation.size()>maximumAnnotations||maximumAnnotations<1||maximumSegments<1)throw new IllegalArgumentException("Annotation input exceeds explicit capacity");
        var keep=new HashSet<String>();for(Annotation a:annotation){SignacFragments.check(cancel);if(a.chromosome.equals(region.chromosome())&&a.start<=region.end()&&a.end>=region.start())keep.add(key(a,collapseTranscripts));}
        List<Annotation> exons=annotation.stream().filter(a->keep.contains(key(a,collapseTranscripts))&&a.type.equals("exon")).toList();
        if(keep.isEmpty())return new Layout(List.of(),List.of(),List.of(),List.of(),List.of());
        if(exons.isEmpty())throw new IllegalArgumentException("Original annotation reformat has no exon bodies after selecting the overlapping gene/transcript; no substitute model");
        if(region.end()==region.start())throw new IllegalArgumentException("Original split_body divides by a zero-width plotting span");
        Map<String,List<Annotation>> grouped=new TreeMap<>(SignacCoverage.C_ORDER);for(Annotation a:exons)grouped.computeIfAbsent(key(a,collapseTranscripts),n->new ArrayList<>()).add(a);
        List<Body> bodies=new ArrayList<>();List<String> warnings=new ArrayList<>();
        for(var e:grouped.entrySet()){
            var list=e.getValue();var first=list.get(0);int start=Math.max(region.start(),list.stream().mapToInt(Annotation::start).min().orElseThrow()),end=Math.min(region.end(),list.stream().mapToInt(Annotation::end).max().orElseThrow());
            if(end<start)throw new IllegalArgumentException("Original annotation body has invalid clipped coordinates; no repair");
            if(list.stream().anyMatch(a->!a.chromosome.equals(first.chromosome)||!a.strand.equals(first.strand)))warnings.add("SOURCE_GROUPED_ANNOTATION_IDENTITY: "+e.getKey()+" combines exons with differing chromosome/strand; original body metadata uses the first exon. Review annotation identities before biological interpretation.");
            bodies.add(new Body(first,start,end,e.getKey()));
        }
        List<String> levels=SignacSequenceOrder.levels(bodies.stream().map(b->b.first.chromosome).toList());List<Body> ordered=new ArrayList<>(bodies);
        ordered.sort(Comparator.comparingInt((Body b)->levels.indexOf(b.first.chromosome)).thenComparingInt(Body::start).thenComparingInt(Body::end).thenComparingInt(bodies::indexOf));
        Map<String,Integer> lanes=new HashMap<>();String chromosome=null;long end=-1;int lane=0;
        for(Body b:ordered){SignacFragments.check(cancel);if(!b.first.chromosome.equals(chromosome)||b.start>end+1000){lane=0;end=-1;chromosome=b.first.chromosome;}lanes.put(b.key,++lane);end=Math.max(end,b.end);}
        List<Label> labels=new ArrayList<>();List<Segment> outExons=new ArrayList<>(),plus=new ArrayList<>(),minus=new ArrayList<>();double tick=(region.end()-region.start())/50.0;int segments=0;
        for(Body b:bodies){SignacFragments.check(cancel);if(++segments>maximumSegments)throw new IllegalArgumentException("Complete annotation body population exceeds capacity");int dodge=lanes.get(b.key);Segment body=segment(b.first,b.start,b.end,"body",dodge);labels.add(new Label(body,b.end-b.start,b.start+(b.end-b.start)/2.0));
            List<Segment> target=b.first.strand.equals("-")?minus:plus;target.add(body);double nbreak=(b.end-b.start)/tick;
            if(nbreak>1){for(int step=0;step<=nbreak;step++){if(++segments>maximumSegments)throw new IllegalArgumentException("Complete annotation arrow population exceeds capacity");double x=tick*step+b.start;if(x>b.end)throw new IllegalArgumentException("Original split_body contains a floating-point terminal break beyond the source body");target.add(segment(b.first,x,x+1,"arrow",dodge));}}
            else{if(++segments>maximumSegments)throw new IllegalArgumentException("Complete annotation arrow population exceeds capacity");target.add(segment(b.first,b.end,b.end+1.0,"arrow",dodge));}
        }
        for(Annotation a:exons){if(++segments>maximumSegments)throw new IllegalArgumentException("Complete annotation segment population exceeds capacity");outExons.add(segment(a,a.start,a.end,a.type,lanes.get(key(a,collapseTranscripts))));}
        return new Layout(labels,outExons,plus,minus,warnings);
    }
    private static String key(Annotation a,boolean collapse){return collapse?a.geneName:a.transcriptId;}
    private static Segment segment(Annotation a,double start,double end,String type,int dodge){return new Segment(a.chromosome,start,end,a.strand,a.geneName,a.transcriptId,a.biotype,type,dodge);}
}
