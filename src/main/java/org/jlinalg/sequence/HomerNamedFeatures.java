/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Streaming checks of original HOMER named-genome feature annotation resources.
 * Original prioritization is already represented in the resource. Remaining
 * equal-geometry choices are retained as compatible candidates. This class does
 * not reconstruct resource construction, enrichment P values or gene metadata.
 */
public final class HomerNamedFeatures {
    private HomerNamedFeatures() { }

    /** A bounded one-based inclusive input region; strand does not affect feature assignment. */
    public record Query(String id,String contig,int start1,int end1,char strand) {
        public Query {
            identity(id);identity(contig);
            if(start1<1||end1<start1||end1>1_000_000_000||strand!='+'&&strand!='-')throw new IllegalArgumentException("Invalid bounded one-based feature query");
        }
        /** Original positive-integer midpoint. */
        public int midpoint1(){return (int)(((long)start1+end1)/2);}
    }
    /** An unchanged resource row, including its recorded construction priority. */
    public record Feature(long sourceOrdinal,String name,String contig,int start1,int end1,
                          char strand,String category,int constructionPriority) {
        /** Original annotatePeaks display cleanup; the unmodified name remains separate. */
        public String originalDisplayName(){return name.replaceFirst("-+\\d+$","").replaceFirst("-HOMER\\d+$","");}
    }
    /** All candidates at the original comparator's first containing geometry. */
    public record Match(Query query,List<Feature> compatible) {
        public Match {compatible=List.copyOf(compatible);}
        /** Whether an original displayed feature name is compatible with the resource. */
        public boolean acceptsDisplayName(String original){return compatible.stream().anyMatch(f->f.originalDisplayName().equals(original));}
    }
    /** Exact resource properties and assignment candidates; background sizes are not interval unions. */
    public record Summary(long resourceRecords,List<Match> matches,Map<String,Long> queryContigBackgroundBases,
                          long negativeStartRecords,long endBeyondReferenceRecords,long missingReferenceRecords) {
        public Summary {matches=List.copyOf(matches);queryContigBackgroundBases=Map.copyOf(queryContigBackgroundBases);}
        public long unassignedQueries(){return matches.stream().filter(m->m.compatible.isEmpty()).count();}
        public long geometryTiedQueries(){return matches.stream().filter(m->m.compatible.size()>1).count();}
    }
    /** Caller-declared capacities and explicit retention of original resource boundary irregularities. */
    public record Limits(long records,int queries,int candidateRows,int categories,int lineCharacters,
                         long operations,boolean retainOriginalResourceBounds) {
        public Limits {
            if(records<1||records>100_000_000||queries<1||queries>1_000_000||candidateRows<1||candidateRows>10_000_000||categories<1||categories>100_000||lineCharacters<32||lineCharacters>1_048_576||operations<1)throw new IllegalArgumentException("Invalid bounded feature-audit capacities");
        }
    }
    /** A caller-supplied deadline, cancellation or immutable-resource check. */
    @FunctionalInterface public interface Check {void run()throws IOException;}
    private record Indexed(Query query,int ordinal) { }
    private static final class Choice {
        int start,end;char strand;final List<Feature> rows=new ArrayList<>();
    }

    /**
     * Read every unchanged HOMER7 annotation row and check complete query populations.
     * Source rows may overlap or extend outside the reference; retaining the latter
     * requires explicit permission. The source comparator orders start,end,strand;
     * equal geometries retain all compatible names rather than inventing a tie rule.
     * Source background sizes sum inclusive resource rows on query chromosomes,
     * exclude negative starts and ends beyond 1.4 billion, and include other
     * overlaps and retained right overhangs. Original 1.5-billion sentinel ends
     * remain in assignment candidates without entering those size totals.
     * No enrichment probability or assembly-wide denominator is inferred.
     *
     * @param source annotation reader, owned and closed by the caller
     * @param referenceLengths exact contig lengths, with no aliases inferred
     * @param queries complete input query list, with unique audit identities
     * @param limits capacities and resource-boundary policy
     * @param check caller's fast deadline or immutable-marker check
     * @return complete resource census and compatible assignments in input order
     * @throws IOException on malformed data, capacity/policy failure or caller check failure
     */
    public static Summary annotate(Reader source,Map<String,Long> referenceLengths,List<Query> queries,
                                   Limits limits,Check check)throws IOException {
        if(source==null||referenceLengths==null||referenceLengths.isEmpty()||queries==null||limits==null||queries.isEmpty()||queries.size()>limits.queries()||check==null)throw new IllegalArgumentException("Require source,reference,queries and bounded audit controls");
        for(var e:referenceLengths.entrySet()){identity(e.getKey());if(e.getValue()==null||e.getValue()<1||e.getValue()>1_000_000_000)throw new IllegalArgumentException("Invalid bounded reference length");}
        var groups=new HashMap<String,List<Indexed>>();var identities=new HashSet<String>();long held=0;
        for(int i=0;i<queries.size();i++){
            Query q=queries.get(i);Long length=referenceLengths.get(q.contig());
            if(length==null||q.end1()>length||!identities.add(q.id()))throw new IOException("Query outside exact reference or duplicate query identity");
            held=Math.addExact(held,512L+2L*(q.id().length()+q.contig().length()));groups.computeIfAbsent(q.contig(),k->new ArrayList<>()).add(new Indexed(q,i));
        }
        if(held>Runtime.getRuntime().maxMemory()/4)throw new IOException("Feature query population exceeds heap capacity");
        for(var list:groups.values())list.sort(Comparator.comparingInt((Indexed q)->q.query.midpoint1()).thenComparingInt(Indexed::ordinal));
        Choice[] choices=new Choice[queries.size()];var sizes=new LinkedHashMap<String,Long>();var categories=new HashSet<String>();long categoryCharacters=0,rows=0,negative=0,overhang=0,missing=0,operations=0,candidateBytes=0;int retained=0;
        var lines=new Lines(source,limits.lineCharacters());check.run();
        for(String line;(line=lines.next())!=null;){
            if(line.isBlank()||line.startsWith("#"))continue;
            if(++rows>limits.records())throw new IOException("Feature annotation record capacity exceeded");
            String[] f=line.split("\t",-1);if(f.length!=7)throw new IOException("Require unchanged HOMER7 annotation resource rows");identity(f[0]);identity(f[1]);identity(f[5]);
            int start=integer(f[2],Integer.MIN_VALUE,Integer.MAX_VALUE),end=integer(f[3],Integer.MIN_VALUE,Integer.MAX_VALUE),priority=integer(f[6],0,Integer.MAX_VALUE);char strand=switch(f[4]){case "+","0"->'+';case "-","1"->'-';default->throw new IOException("Invalid annotation resource strand");};
            if(end<start)throw new IOException("Reversed annotation resource interval");
            if((long)start+end<Integer.MIN_VALUE||(long)start+end>Integer.MAX_VALUE)throw new IOException("Annotation resource exceeds safe original32-bit midpoint arithmetic");
            if(categories.add(f[5])){categoryCharacters=Math.addExact(categoryCharacters,f[5].length());if(categories.size()>limits.categories()||categoryCharacters>4_194_304)throw new IOException("Annotation category capacity exceeded");}
            Long length=referenceLengths.get(f[1]);boolean outside=start<0||length==null||end>length;
            if(start<0)negative++;if(length==null)missing++;else if(end>length)overhang++;
            if(outside&&!limits.retainOriginalResourceBounds())throw new IOException("Original annotation resource row "+rows+" ("+f[0]+","+f[1]+":"+start+"-"+end+") has negative,out-of-reference or missing-contig coordinates;review exact source rows and choose explicit retained-resource policy only where appropriate");
            List<Indexed> group=groups.get(f[1]);
            if(group!=null){
                if(start>=0&&end<=1_400_000_000)sizes.merge(f[5],(long)end-start+1,Math::addExact);
                int first=lower(group,start),last=upper(group,end);
                for(int j=first;j<last;j++){
                    if(++operations>limits.operations())throw new IOException("Feature candidate-audit operation capacity exceeded");int ordinal=group.get(j).ordinal();Choice choice=choices[ordinal];int comparison=choice==null?-1:compare(start,end,strand,choice);
                    if(comparison>0)continue;
                    if(choice==null){choice=new Choice();choices[ordinal]=choice;}
                    if(comparison<0){for(var old:choice.rows)candidateBytes-=bytes(old);retained-=choice.rows.size();choice.rows.clear();choice.start=start;choice.end=end;choice.strand=strand;}
                    var feature=new Feature(rows,f[0],f[1],start,end,strand,f[5],priority);candidateBytes=Math.addExact(candidateBytes,bytes(feature));
                    if(++retained>limits.candidateRows()||candidateBytes+held>Runtime.getRuntime().maxMemory()/4)throw new IOException("Retained feature candidate row/heap capacity exceeded");choice.rows.add(feature);
                }
            }
            if((rows&4095)==0)check.run();
        }
        if(rows==0)throw new IOException("Annotation resource has no usable records");var result=new ArrayList<Match>();for(int i=0;i<queries.size();i++)result.add(new Match(queries.get(i),choices[i]==null?List.of():choices[i].rows));check.run();return new Summary(rows,result,sizes,negative,overhang,missing);
    }
    private static long bytes(Feature f){return 256L+2L*(f.name().length()+f.contig().length()+f.category().length());}
    private static int compare(int start,int end,char strand,Choice choice){int c=Integer.compare(start,choice.start);if(c==0)c=Integer.compare(end,choice.end);if(c==0)c=Character.compare(strand,choice.strand);return c;}
    private static int lower(List<Indexed> group,int value){int a=0,b=group.size();while(a<b){int mid=(a+b)>>>1;if(group.get(mid).query().midpoint1()<value)a=mid+1;else b=mid;}return a;}
    private static int upper(List<Indexed> group,int value){int a=0,b=group.size();while(a<b){int mid=(a+b)>>>1;if(group.get(mid).query().midpoint1()<=value)a=mid+1;else b=mid;}return a;}
    private static int integer(String text,int min,int max)throws IOException{try{int first=text.startsWith("-")?1:0;if(first==text.length())throw new NumberFormatException();for(int i=first;i<text.length();i++)if(text.charAt(i)<'0'||text.charAt(i)>'9')throw new NumberFormatException();long n=Long.parseLong(text);if(n<min||n>max)throw new NumberFormatException();return(int)n;}catch(NumberFormatException failure){throw new IOException("Invalid bounded annotation integer: "+text,failure);}}
    private static void identity(String value){if(value==null||value.isBlank())throw new IllegalArgumentException("Require nonempty control-free annotation identity/category");for(int i=0;i<value.length();i++)if(Character.isISOControl(value.charAt(i)))throw new IllegalArgumentException("Require nonempty control-free annotation identity/category");}
    /** Buffered bounded lines without a synchronized Reader.read call per character. */
    private static final class Lines {
        private final Reader source;private final int maximum;private final char[] buffer=new char[8192];private int position,available;private boolean eof;
        Lines(Reader source,int maximum){this.source=source;this.maximum=maximum;}
        String next()throws IOException{
            if(eof)return null;var line=new StringBuilder(Math.min(256,maximum));boolean any=false;
            for(;;){
                if(position==available){available=source.read(buffer);position=0;if(available<0){eof=true;return any?finish(line):null;}if(available==0)continue;}
                int start=position;while(position<available&&buffer[position]!='\n')position++;int count=position-start;
                if(line.length()+count>maximum)throw new IOException("Oversized annotation resource line");line.append(buffer,start,count);any|=count>0;
                if(position<available){position++;return finish(line);}
            }
        }
        private static String finish(StringBuilder line)throws IOException{if(!line.isEmpty()&&line.charAt(line.length()-1)=='\r')line.setLength(line.length()-1);for(int i=0;i<line.length();i++)if(line.charAt(i)<32&&line.charAt(i)!='\t')throw new IOException("Control character in annotation resource line");return line.toString();}
    }
}
