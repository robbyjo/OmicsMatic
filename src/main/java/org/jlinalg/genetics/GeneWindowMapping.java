/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/** Independent positional variant-to-gene mapping with explicit strand-aware windows. */
public final class GeneWindowMapping {
    private GeneWindowMapping() { }
    /** Unique variant identity with a positive, one-based position. */
    public record Variant(String id,String chromosome,int position) {
        public Variant {if(!token(id)||!token(chromosome)||position<1)throw new IllegalArgumentException("Require variant ID/chromosome and positive one-based position");}
    }
    /** Gene coordinates are one-based closed; strand must be explicit for asymmetric windows. */
    public record Gene(String id,String chromosome,int start,int end,String strand) {
        public Gene {if(!token(id)||!token(chromosome)||start<1||end<start||!List.of("+","-",".").contains(strand))throw new IllegalArgumentException("Require gene ID/chromosome,1<=start<=end,strand +|-|.");}
    }
    /** Expanded bounds are closed; zero is the lower clipping sentinel used by MAGMA, not a BED start. */
    public record Assignment(Gene gene,int windowStart,int windowEnd,List<Variant> variants) {
        public Assignment {variants=List.copyOf(variants);}
    }
    /** Token format used in upstream text data, without control/space or leading comment marker. */
    public static boolean token(String s){return s!=null&&!s.isEmpty()&&!s.startsWith("#")&&s.codePoints().noneMatch(c->Character.isWhitespace(c)||Character.isISOControl(c));}
    /** Canonical MAGMA chromosome code; no chr-prefix stripping or assembly inference. */
    public static String chromosome(String s,boolean nonhuman){
        if(List.of("X","Y").contains(s)||nonhuman&&List.of("W","Z").contains(s))return s;
        try{int n=Integer.parseInt(s);if(!Integer.toString(n).equals(s)||n<1||n>(nonhuman?2_000_000:24))throw new NumberFormatException();return !nonhuman&&n==23?"X":!nonhuman&&n==24?"Y":s;}catch(NumberFormatException e){throw new IllegalArgumentException("Invalid explicit "+(nonhuman?"nonhuman":"human")+" chromosome code: "+s,e);}
    }
    /** Read a headered native gene table: gene_id,chromosome,start,end,strand (tabs). */
    public static List<Gene> genes(Path file,boolean nonhuman)throws IOException{
        var result=new ArrayList<Gene>();var ids=new HashSet<String>();try(var in=Files.newBufferedReader(file)){if(!"gene_id\tchromosome\tstart\tend\tstrand".equals(in.readLine()))throw new IOException("Gene table requires gene_id/chromosome/start/end/strand header");String line;long n=1;while((line=in.readLine())!=null){n++;String[] f=line.split("\t",-1);try{if(f.length!=5||!ids.add(f[0]))throw new IllegalArgumentException("Duplicate gene or wrong field count");result.add(new Gene(f[0],chromosome(f[1],nonhuman),Integer.parseInt(f[2]),Integer.parseInt(f[3]),f[4]));}catch(IllegalArgumentException e){throw new IOException("Gene row "+n+": "+e.getMessage(),e);}}}if(result.isEmpty())throw new IOException("No genes supplied");return List.copyOf(result);
    }
    /** Read variant_id/chromosome/position TSV or an explicitly selected, unheadered six-column BIM. */
    public static List<Variant> variants(Path file,boolean bim,boolean nonhuman)throws IOException{
        var result=new ArrayList<Variant>();var ids=new HashSet<String>();try(var in=Files.newBufferedReader(file)){if(!bim&&!"variant_id\tchromosome\tposition".equals(in.readLine()))throw new IOException("SNP table requires variant_id/chromosome/position header");String line;long n=bim?0:1;while((line=in.readLine())!=null){n++;String[] f=bim?line.trim().split("\\s+"):line.split("\t",-1);try{if(f.length!=(bim?6:3))throw new IllegalArgumentException("Wrong field count");String id=f[bim?1:0];if(!ids.add(id))throw new IllegalArgumentException("Duplicate variant ID "+id);result.add(new Variant(id,chromosome(f[bim?0:1],nonhuman),Integer.parseInt(f[bim?3:2])));}catch(IllegalArgumentException e){throw new IOException("SNP row "+n+": "+e.getMessage(),e);}}}if(result.isEmpty())throw new IOException("No SNPs supplied");return List.copyOf(result);
    }
    /** Find every closed-interval membership, in input gene/variant order; empty genes remain visible. */
    public static List<Assignment> assign(List<Variant> variants,List<Gene> genes,int upstream,int downstream,boolean ignoreStrand){
        if(upstream<0||downstream<0)throw new IllegalArgumentException("Window sizes must be nonnegative integer bases");
        record Indexed(Variant variant,int order) { }
        var grouped=new HashMap<String,List<Indexed>>();var ids=new HashSet<String>();for(int i=0;i<variants.size();i++){var v=variants.get(i);if(!ids.add(v.id()))throw new IllegalArgumentException("Duplicate variant ID");grouped.computeIfAbsent(v.chromosome(),k->new ArrayList<>()).add(new Indexed(v,i));}
        grouped.values().forEach(list->list.sort(Comparator.comparingInt(a->a.variant().position())));ids.clear();var output=new ArrayList<Assignment>();
        for(var gene:genes){
            if(!ids.add(gene.id()))throw new IllegalArgumentException("Duplicate gene ID");if(upstream!=downstream&&!ignoreStrand&&gene.strand().equals("."))throw new IllegalArgumentException("Asymmetric windows require explicit strand or ignore-strand");boolean reverse=!ignoreStrand&&gene.strand().equals("-");int low=Math.max(0,gene.start()-(reverse?downstream:upstream)),high=Math.addExact(gene.end(),reverse?upstream:downstream);var list=grouped.getOrDefault(gene.chromosome(),List.of());int left=0,right=list.size();while(left<right){int m=(left+right)>>>1;if(list.get(m).variant().position()<low)left=m+1;else right=m;}var hits=new ArrayList<Indexed>();for(int i=left;i<list.size()&&list.get(i).variant().position()<=high;i++)hits.add(list.get(i));hits.sort(Comparator.comparingInt(Indexed::order));output.add(new Assignment(gene,low,high,hits.stream().map(Indexed::variant).toList()));
        }
        return List.copyOf(output);
    }
    /** Write MAGMA's interchange format; empty genes remain in the caller's separate status table. */
    public static void writeMagma(Path file,List<Assignment> assignments,int upstream,int downstream,boolean nonhuman)throws IOException{
        try(var out=Files.newBufferedWriter(file)){out.write("# window_up = "+upstream+"\n# window_down = "+downstream+"\n");if(nonhuman)out.write("# nonhuman = 1\n");for(var a:assignments){if(a.variants().isEmpty())continue;out.write(a.gene().id()+"\t"+a.gene().chromosome()+":"+a.windowStart()+":"+a.windowEnd());for(var v:a.variants())out.write("\t"+v.id());out.write('\n');}}
    }
    /** Parse a program-produced annotation and audit every gene, window and exact SNP membership. */
    public static void verifyMagma(Path file,List<Assignment> expected)throws IOException{
        var by=new HashMap<String,Assignment>();for(var a:expected)if(!a.variants().isEmpty())by.put(a.gene().id(),a);var seen=new HashSet<String>();try(var in=Files.newBufferedReader(file)){String line;while((line=in.readLine())!=null){if(line.startsWith("#")||line.isBlank())continue;String[] f=line.trim().split("\\s+");var a=by.get(f[0]);if(a==null||!seen.add(f[0])||f.length!=a.variants().size()+2||!f[1].equals(a.gene().chromosome()+":"+a.windowStart()+":"+a.windowEnd()))throw new IOException("Unexpected annotation gene, coordinate or membership count: "+f[0]);var wanted=new HashSet<String>();a.variants().forEach(v->wanted.add(v.id()));var got=new HashSet<String>();for(int i=2;i<f.length;i++)if(!wanted.contains(f[i])||!got.add(f[i]))throw new IOException("Unexpected/duplicate annotated variant: "+f[i]);}}if(!seen.equals(by.keySet()))throw new IOException("Upstream annotation omitted expected genes");
    }
    /** Exact chromosome/position concordance, permitting an explicitly larger reference panel. */
    public static void verifyVariantLocations(List<Variant> annotation,List<Variant> reference){
        Map<String,Variant> by=new HashMap<>();reference.forEach(v->by.put(v.id(),v));for(var v:annotation){var found=by.get(v.id());if(found!=null&&(!found.chromosome().equals(v.chromosome())||found.position()!=v.position()))throw new IllegalArgumentException("Variant build/position differs between annotation and reference: "+v.id());}
    }
}
