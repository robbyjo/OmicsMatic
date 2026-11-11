/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Native annotation union, TSS extraction and whole-fragment gene activity contracts. */
public final class SignacGeneActivity {
    private SignacGeneActivity() { }
    public record Annotation(String chromosome,int start,int end,String strand,String geneId,String geneName,String biotype) {
        public Annotation {SignacFragments.identity(chromosome);SignacFragments.identity(geneId);SignacFragments.identity(biotype);if(geneName!=null&&!geneName.isEmpty())SignacFragments.identity(geneName);SignacGeneActivity.strand(strand);if(start<1||end<start||end>536_870_912)throw new IllegalArgumentException("Require positive closed annotation coordinates within the TBI domain");}
    }
    public record Transcript(String chromosome,int start,int end,String strand,String geneId,String geneName,String biotype) { }
    public record Options(List<String> features,List<String> biotypes,Double maxWidth,int upstream,int downstream,int processN,boolean geneIds) {
        public Options {features=features==null?null:List.copyOf(features);biotypes=biotypes==null?null:List.copyOf(biotypes);if(maxWidth!=null&&(!Double.isFinite(maxWidth)||maxWidth<0)||upstream<0||downstream<0||upstream>1_000_000||downstream>1_000_000||processN<1)throw new IllegalArgumentException("Require finite nonnegative width/extension bounds and positive process_n");}
        public static Options defaults(){return new Options(null,List.of("protein_coding"),500000.0,2000,0,2000,false);}
    }
    public record Result(SignacFragments.Matrix matrix,List<Transcript> transcripts,List<SignacFragments.Warning> warnings) {
        public Result {transcripts=List.copyOf(transcripts);warnings=List.copyOf(warnings);}
    }
    static void strand(String strand){if(!Set.of("+","-","*").contains(strand))throw new IllegalArgumentException("Annotation/TSS strand must be +, - or *");}
    /** Source union uses first metadata and all rows, not an independently selected longest transcript. */
    public static List<Transcript> collapse(List<Annotation> annotations,boolean replaceMissingNames,BooleanSupplier cancelled){
        SignacFragments.check(cancelled);if(annotations.size()>2_000_000)throw new IllegalArgumentException("Excessive annotation rows");
        LinkedHashMap<String,List<Annotation>> groups=new LinkedHashMap<>();for(var row:annotations){SignacFragments.check(cancelled);groups.computeIfAbsent(row.geneId(),k->new ArrayList<>()).add(row);}
        List<Transcript> rows=new ArrayList<>();
        for(var group:groups.values()){
            SignacFragments.check(cancelled);var first=group.get(0);int start=Integer.MAX_VALUE,end=0;Set<String> chromosomes=new LinkedHashSet<>();for(var row:group){start=Math.min(start,row.start());end=Math.max(end,row.end());chromosomes.add(row.chromosome());}
            String name=first.geneName();if(replaceMissingNames&&(name==null||name.isEmpty()))name=first.geneId();
            for(String chromosome:chromosomes)rows.add(new Transcript(chromosome,start,end,first.strand().equals("*")?"+":first.strand(),first.geneId(),name,first.biotype()));
        }
        List<String> names=makeUnique(rows.stream().map(Transcript::geneName).toList());List<Transcript> result=new ArrayList<>();
        for(int i=0;i<rows.size();i++){var r=rows.get(i);result.add(new Transcript(r.chromosome(),r.start(),r.end(),r.strand(),r.geneId(),names.get(i),r.biotype()));}return List.copyOf(result);
    }
    /** R make.unique reserves existing suffix names before assigning generated suffixes; NA is distinct. */
    public static List<String> makeUnique(List<String> names){
        Set<String> reserved=new HashSet<>(names),seen=new HashSet<>();Map<String,Integer> next=new HashMap<>();List<String> result=new ArrayList<>();
        for(String name:names){if(seen.add(name)){result.add(name);continue;}String prefix=name==null?"NA":name,candidate;int suffix=next.getOrDefault(name,1);do{candidate=prefix+"."+suffix++;}while(reserved.contains(candidate));next.put(name,suffix);reserved.add(candidate);result.add(candidate);}return Collections.unmodifiableList(result);
    }
    /** Default GetTSSPositions filters coding rows before union and drops original sequence-length metadata. */
    public static List<SignacTssEnrichment.Position> tssPositions(List<Annotation> annotations,boolean proteinCodingOnly,BooleanSupplier cancelled){
        List<Annotation> selected=proteinCodingOnly?annotations.stream().filter(r->r.biotype().equals("protein_coding")).toList():annotations;
        List<SignacTssEnrichment.Position> positions=new ArrayList<>();for(var row:collapse(selected,false,cancelled)){int p=row.strand().equals("-")?row.end():row.start();positions.add(new SignacTssEnrichment.Position(row.chromosome(),p,p,row.strand(),null));}return List.copyOf(positions);
    }
    public static Result geneActivity(List<SignacFragments.Source> sources,List<Annotation> annotations,List<String> objectCells,Options options,Path scratch,SignacFragments.Limits limits,BooleanSupplier cancelled)throws IOException {
        SignacFragments.check(cancelled);Objects.requireNonNull(options);if(annotations.isEmpty()||annotations.size()>limits.features())throw new IOException("No gene annotations or excessive annotation rows");if(options.features()!=null&&options.features().isEmpty())throw new IOException("Empty gene feature list");
        List<Transcript> collapsed=collapse(annotations,true,cancelled),selected=new ArrayList<>();
        Set<String> biotypes=options.biotypes()==null?null:new HashSet<>(options.biotypes()),features=options.features()==null?null:new HashSet<>(options.features());
        for(var row:collapsed){SignacFragments.check(cancelled);String name=options.geneIds()?row.geneId():row.geneName();if(biotypes!=null&&!biotypes.contains(row.biotype())||features!=null&&!features.contains(name)||options.maxWidth()!=null&&(long)row.end()-row.start()+1>=options.maxWidth())continue;selected.add(new Transcript(row.chromosome(),row.start(),row.end(),row.strand(),row.geneId(),name,row.biotype()));}
        if(selected.isEmpty())throw new IOException("No genes remain after biotype, name/ID and strict max.width filtering");
        List<SignacFragments.Warning> warnings=new ArrayList<>();warnings.add(new SignacFragments.Warning("ANNOTATION_UNION_AND_FIRST_METADATA","Gene ranges union all annotation starts/ends per gene ID, retain first strand/biotype/name and discard original sequence lengths, matching the source helper","Review transcript provenance and metadata order; this is not selection of a longest isoform. Biotype filtering occurs after union in GeneActivity and before union in GetTSSPositions."));
        LinkedHashMap<String,SignacFragments.Region> unique=new LinkedHashMap<>();List<String> keys=new ArrayList<>();Map<String,String> firstNames=new HashMap<>();List<String> labels=new ArrayList<>();boolean aliases=false,negative=false;
        for(var row:selected){SignacFragments.check(cancelled);long start=(long)row.start()-(row.strand().equals("-")?options.downstream():options.upstream()),end=(long)row.end()+(row.strand().equals("-")?options.upstream():options.downstream());if(end>536_870_912)throw new IOException("Extended gene exceeds the native TBI coordinate domain");negative|=start<1;
            String original=row.chromosome()+"-"+start+"-"+end;String prior=firstNames.putIfAbsent(original,row.geneName());if(prior!=null)aliases=true;labels.add(firstNames.get(original));
            var region=new SignacFragments.Region(row.chromosome(),(int)Math.max(1,start),(int)end);keys.add(region.name());unique.putIfAbsent(region.name(),region);
        }
        if(negative)warnings.add(new SignacFragments.Warning("NEGATIVE_PROMOTER_QUERY_CLIPPED","Source promoter coordinates extend below1 without sequence-length metadata; the physical fragment query is clipped at the chromosome origin","The original annotation/promoter declaration is retained. Do not treat this as reference sequence-length validation."));
        if(aliases)warnings.add(new SignacFragments.Warning("IDENTICAL_COORDINATES_USE_FIRST_GENE_NAME","Different selected transcripts share the same extended coordinate key; source row naming selects the first gene name for all aliases","Inspect duplicate coordinates and duplicate output names; do not silently rename/reassign counts to a different gene."));
        var base=SignacFragments.featureMatrix(sources,new ArrayList<>(unique.values()),objectCells,false,options.processN(),scratch,limits,cancelled);warnings.addAll(base.warnings());
        Map<String,Integer> baseRows=new HashMap<>();for(int i=0;i<base.features().size();i++)baseRows.put(base.features().get(i),i);List<List<Integer>> expanded=new ArrayList<>();for(int i=0;i<base.features().size();i++)expanded.add(new ArrayList<>());List<String> names=new ArrayList<>();
        for(int i=0;i<selected.size();i++){Integer row=baseRows.get(keys.get(i));if(row!=null){expanded.get(row).add(names.size());names.add(labels.get(i));}}
        int[] oldPointers=base.pointers(),oldRows=base.rows();long[] oldValues=base.values();long size=0;for(int row:oldRows)size+=expanded.get(row).size();if(size>limits.nonzeroValues())throw new IOException("Expanded gene sparse matrix exceeds its nonzero bound");
        int[] pointers=new int[base.cells().size()+1],indices=new int[(int)size];long[] values=new long[(int)size];int at=0;
        for(int cell=0;cell<base.cells().size();cell++){SignacFragments.check(cancelled);List<Entry> entries=new ArrayList<>();for(int k=oldPointers[cell];k<oldPointers[cell+1];k++)for(int row:expanded.get(oldRows[k]))entries.add(new Entry(row,oldValues[k]));entries.sort(Comparator.comparingInt(Entry::row));for(var entry:entries){indices[at]=entry.row();values[at++]=entry.value();}pointers[cell+1]=at;}
        var matrix=new SignacFragments.Matrix(names,base.cells(),pointers,indices,values,base.scans(),warnings);return new Result(matrix,selected,warnings);
    }
    private record Entry(int row,long value) { }
}
