/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.assembly;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;

/**
 * Complete bounded parser for hifiasm's H/S/L/A graph dialect and explicit
 * segment-to-FASTA conversion. All source fields, ordering, directed links and
 * gap/virtual-read annotations are retained. This is not graph assembly,
 * consensus correction, phase inference or a generic GFA path interpreter.
 */
public final class HifiasmGfa {
    private HifiasmGfa(){ }
    public record Limits(long bytes,int lineBytes,int records,int segments,int links,int placements,long bases,long coordinate){
        public Limits{if(bytes<1||lineBytes<1||records<1||segments<1||links<1||placements<1||bases<0||coordinate<0)throw new IllegalArgumentException("Declare positive bounded complete graph capacities");}
    }
    /** Literal source value, including explicit asterisk placeholders where the source emits them. */
    public record Tag(String name,char type,String value){public Tag{Objects.requireNonNull(name);Objects.requireNonNull(value);if(!name.matches("[A-Za-z][A-Za-z0-9]")||"AifZJHB".indexOf(type)<0)throw new IllegalArgumentException("Invalid source tag name/type");}}
    public record Segment(String id,String sequence,long length,List<Tag> tags){
        public Segment{identity(id);Objects.requireNonNull(sequence);tags=List.copyOf(tags);if(length<0||!sequence.equals("*")&&(sequence.length()!=length||!sequence.matches("[A-Za-z=.]*")))throw new IllegalArgumentException("Declared segment length/alphabet differs from complete source sequence");}
        public boolean hasSequence(){return !sequence.equals("*");}
    }
    public record Link(String from,char fromStrand,String to,char toStrand,String cigar,OptionalLong overlap,List<Tag> tags){public Link{identity(from);identity(to);strand(fromStrand);strand(toStrand);Objects.requireNonNull(cigar);Objects.requireNonNull(overlap);tags=List.copyOf(tags);if(cigar.equals("*")){if(overlap.isPresent())throw new IllegalArgumentException("Unknown source overlap has no inferred number");}else if(!cigar.matches("[0-9]+M")||overlap.isEmpty()||overlap.getAsLong()<0||Long.parseLong(cigar.substring(0,cigar.length()-1))!=overlap.getAsLong())throw new IllegalArgumentException("Original M-only overlap state differs");}}
    public record Placement(String segment,long offset,char strand,String read,OptionalLong readStart,OptionalLong readEnd,boolean gap,List<Tag> tags){
        public Placement{identity(segment);Objects.requireNonNull(read);Objects.requireNonNull(readStart);Objects.requireNonNull(readEnd);tags=List.copyOf(tags);if(offset<0)throw new IllegalArgumentException("Negative original placement offset");if(gap){if(strand!='*'||!read.equals("*")||readStart.isPresent()||readEnd.isPresent())throw new IllegalArgumentException("Original gap has no inferred strand/read/interval");}else{HifiasmGfa.strand(strand);identity(read);if(readStart.isEmpty()||readEnd.isEmpty()||readStart.getAsLong()<0||readEnd.getAsLong()<readStart.getAsLong())throw new IllegalArgumentException("Original read interval is invalid");}}
    }
    public record Statistics(int segments,int sequenceBearingSegments,int missingSequences,int links,int placements,long declaredBases,long sequenceBases,OptionalLong maximumLength,OptionalLong n50,OptionalLong l50){ }
    public record FastaReport(List<String> written,List<String> omitted,long sequenceBases){public FastaReport{written=List.copyOf(written);omitted=List.copyOf(omitted);}}
    public record Graph(Map<String,Segment> segments,List<Link> links,List<Placement> placements,List<String> headers,List<String> sourceLines,long sourceBytes,List<String> diagnostics){
        public Graph{segments=Collections.unmodifiableMap(new LinkedHashMap<>(segments));links=List.copyOf(links);placements=List.copyOf(placements);headers=List.copyOf(headers);sourceLines=List.copyOf(sourceLines);diagnostics=List.copyOf(diagnostics);if(sourceBytes<0)throw new IllegalArgumentException("Negative source byte count");for(var e:segments.entrySet())if(!e.getKey().equals(e.getValue().id()))throw new IllegalArgumentException("Segment map identity differs");for(Link link:links)if(!segments.containsKey(link.from())||!segments.containsKey(link.to()))throw new IllegalArgumentException("Link refers to an absent source segment");for(Placement placement:placements)if(!segments.containsKey(placement.segment()))throw new IllegalArgumentException("Placement refers to an absent source segment");}
        /** Source LN lengths, independently summarized per graph, including sequence-omitted views. */
        public Statistics statistics(){
            long declared=0,sequence=0;int present=0;var lengths=new ArrayList<Long>();for(var s:segments.values()){declared=Math.addExact(declared,s.length());lengths.add(s.length());if(s.hasSequence()){present++;sequence=Math.addExact(sequence,s.sequence().length());}}
            lengths.sort(Collections.reverseOrder());OptionalLong maximum=lengths.isEmpty()?OptionalLong.empty():OptionalLong.of(lengths.get(0)),n50=OptionalLong.empty(),l50=OptionalLong.empty();if(declared>0){long accumulated=0,target=declared/2+declared%2;for(int i=0;i<lengths.size();i++){accumulated=Math.addExact(accumulated,lengths.get(i));if(accumulated>=target){n50=OptionalLong.of(lengths.get(i));l50=OptionalLong.of(i+1L);break;}}}
            return new Statistics(segments.size(),present,segments.size()-present,links.size(),placements.size(),declared,sequence,maximum,n50,l50);
        }
    }

    /** Parse every source record before returning a graph; partial families are not returned. */
    public static Graph read(InputStream supplied,Limits limits)throws IOException{
        Objects.requireNonNull(supplied);Objects.requireNonNull(limits);var input=new Lines(supplied,limits);var segments=new LinkedHashMap<String,Segment>();var links=new ArrayList<Link>();var placements=new ArrayList<Placement>();var headers=new ArrayList<String>();var sourceLines=new ArrayList<String>();var diagnostics=new ArrayList<String>();long bases=0;int records=0;
        try{
            for(String line;(line=input.next())!=null;){if(++records>limits.records())throw new IOException("Complete original graph record capacity exceeded");sourceLines.add(line);if(line.isEmpty()||line.startsWith("#")){diagnostics.add("Original blank/comment record retained at line "+records);continue;}String[] f=line.split("\t",-1);
                switch(f[0]){
                    case "H"->{tags(f,1,false,diagnostics);headers.add(line);}
                    case "S"->{if(f.length<3||segments.size()>=limits.segments())throw new IOException("Complete segment family missing or exceeds capacity");String id=f[1],sequence=f[2];identity(id);if(!sequence.equals("*")&&!sequence.matches("[A-Za-z=.]*"))throw new IOException("Unsupported original segment alphabet; no repair is performed");var tags=tags(f,3,false,diagnostics);Tag lengthTag=find(tags,"LN");if(lengthTag==null||lengthTag.type()!='i')throw new IOException("Original hifiasm segment requires explicit LN:i length");long length=coordinate(lengthTag.value(),limits);if(!sequence.equals("*")&&sequence.length()!=length)throw new IOException("Original LN differs from complete source sequence length");bases=Math.addExact(bases,length);if(bases>limits.bases())throw new IOException("Complete graph declared-base capacity exceeded");if(segments.put(id,new Segment(id,sequence,length,tags))!=null)throw new IOException("Duplicate original segment identity");if(length==0)diagnostics.add("Original zero-length segment retained: "+id);}
                    case "L"->{if(f.length<6||links.size()>=limits.links())throw new IOException("Complete original link family missing or exceeds capacity");char from=orientation(f[2]),to=orientation(f[4]);OptionalLong overlap;if(f[5].equals("*")){overlap=OptionalLong.empty();diagnostics.add("Original unknown link overlap retained: "+f[1]+" -> "+f[3]);}else{if(!f[5].matches("[0-9]+M"))throw new IOException("Outside the original hifiasm M-only overlap profile; preserve this source record");overlap=OptionalLong.of(coordinate(f[5].substring(0,f[5].length()-1),limits));}links.add(new Link(f[1],from,f[3],to,f[5],overlap,tags(f,6,false,diagnostics)));}
                    case "A"->{if(f.length<7||placements.size()>=limits.placements())throw new IOException("Complete source placement family missing or exceeds capacity");long offset=coordinate(f[2],limits);boolean gap=f[3].equals("*")&&f[4].equals("*")&&f[5].equals("*")&&f[6].equals("*");var tags=tags(f,7,gap,diagnostics);if(gap){placements.add(new Placement(f[1],offset,'*',"*",OptionalLong.empty(),OptionalLong.empty(),true,tags));diagnostics.add("Original gap placement retained without fabricated read/sequence: "+f[1]+"@"+offset);}else{long start=coordinate(f[5],limits),end=coordinate(f[6],limits);placements.add(new Placement(f[1],offset,orientation(f[3]),f[4],OptionalLong.of(start),OptionalLong.of(end),false,tags));Tag hg=find(tags,"HG");if(f[4].equals("FAKE")&&hg!=null&&hg.value().equals("*"))diagnostics.add("Original FAKE/HG:* source annotation retained; not inferred to be a raw input read: "+f[1]+"@"+offset);}}
                    default->throw new IOException("Unsupported original graph record "+f[0]+" at line "+records+"; no record is silently dropped");
                }
            }
            for(Link link:links){Segment from=segments.get(link.from()),to=segments.get(link.to());if(from==null||to==null)throw new IOException("Original link refers to an absent segment");if(link.overlap().isPresent()&&(link.overlap().getAsLong()>from.length()||link.overlap().getAsLong()>to.length()))diagnostics.add("Original overlap exceeds a source segment length; preserve without clamping: "+link.from()+" -> "+link.to());}
            var last=new LinkedHashMap<String,Long>();for(Placement placement:placements){Segment segment=segments.get(placement.segment());if(segment==null)throw new IOException("Original placement refers to an absent segment");if(placement.offset()>segment.length())diagnostics.add("Original placement offset exceeds source segment length: "+placement.segment()+"@"+placement.offset());Long prior=last.put(placement.segment(),placement.offset());if(prior!=null&&placement.offset()<prior)diagnostics.add("Original placement order decreases; preserve source/gap arithmetic without sorting: "+placement.segment());}
            return new Graph(segments,links,placements,headers,sourceLines,input.used,diagnostics);
        }catch(IllegalArgumentException|ArithmeticException error){throw new IOException("Invalid/unsupported complete original graph state",error);}
    }

    /**
     * Export literal S sequences in source order. Missing sequences require an
     * explicit omit choice and complete omission ledger; no Ns are invented.
     * Missing-data failure is checked before writing any record. Buffer a file
     * stream at the call site; the caller owns its lifetime and finalization.
     */
    public static FastaReport writeFasta(Graph graph,OutputStream output,int wrap,boolean omitMissing)throws IOException{
        Objects.requireNonNull(graph);Objects.requireNonNull(output);if(wrap<1||wrap>1000000)throw new IllegalArgumentException("Declare line wrap1..1000000");if(!omitMissing&&graph.segments().values().stream().anyMatch(s->!s.hasSequence()))throw new IOException("Original graph omits sequence; supply the sequence-bearing graph or explicitly retain an omission ledger");var written=new ArrayList<String>();var omitted=new ArrayList<String>();long bases=0;
        for(Segment segment:graph.segments().values()){if(!segment.hasSequence()){omitted.add(segment.id());continue;}identity(segment.id());output.write(('>'+segment.id()+"\n").getBytes(StandardCharsets.UTF_8));String sequence=segment.sequence();if(sequence.isEmpty())output.write('\n');for(int start=0;start<sequence.length();){int end=(int)Math.min((long)sequence.length(),(long)start+wrap);output.write(sequence.substring(start,end).getBytes(StandardCharsets.US_ASCII));output.write('\n');start=end;}written.add(segment.id());bases=Math.addExact(bases,sequence.length());}
        return new FastaReport(written,omitted,bases);
    }
    private static void identity(String id){Objects.requireNonNull(id);if(id.isEmpty()||id.equals("*"))throw new IllegalArgumentException("Missing literal graph identity");for(int i=0;i<id.length();i++){char c=id.charAt(i);if(c<=32||c==127)throw new IllegalArgumentException("ASCII whitespace/control in graph identity");if(Character.isHighSurrogate(c)){if(i+1>=id.length()||!Character.isLowSurrogate(id.charAt(i+1)))throw new IllegalArgumentException("Unpaired surrogate in graph identity");i++;}else if(Character.isLowSurrogate(c))throw new IllegalArgumentException("Unpaired surrogate in graph identity");}}
    private static void strand(char strand){if(strand!='+'&&strand!='-')throw new IllegalArgumentException("Original orientation must be + or -");}
    private static char orientation(String value)throws IOException{if(value.length()!=1||value.charAt(0)!='+'&&value.charAt(0)!='-')throw new IOException("Original orientation is unsupported or missing");return value.charAt(0);}
    private static long coordinate(String value,Limits limits)throws IOException{if(!value.matches("[+]?[0-9]+"))throw new IOException("Original coordinate is negative, missing or noninteger; no repair");try{long n=Long.parseLong(value);if(n>limits.coordinate())throw new IOException("Original coordinate exceeds declared complete capacity");return n;}catch(NumberFormatException error){throw new IOException("Original coordinate exceeds signed64-bit audit domain",error);}}
    private static Tag find(List<Tag> tags,String name){for(Tag tag:tags)if(tag.name().equals(name))return tag;return null;}
    private static List<Tag> tags(String[] fields,int start,boolean placeholders,List<String> diagnostics)throws IOException{
        var result=new ArrayList<Tag>();var seen=new HashSet<String>();for(int i=start;i<fields.length;i++){String raw=fields[i];if(raw.length()<5||raw.charAt(2)!=':'||raw.charAt(4)!=':'||!raw.substring(0,2).matches("[A-Za-z][A-Za-z0-9]"))throw new IOException("Malformed original optional graph tag");String name=raw.substring(0,2),value=raw.substring(5);char type=raw.charAt(3);if(!seen.add(name)||"AifZJHB".indexOf(type)<0)throw new IOException("Duplicate or unsupported original tag type");if(type=='i'&&!value.matches("[+-]?[0-9]+")){if(!placeholders||!value.equals("*"))throw new IOException("Malformed original integer graph tag");diagnostics.add("Original integer-tag asterisk placeholder retained: "+name);}if(type=='A'&&value.codePointCount(0,value.length())!=1)throw new IOException("Original character tag is incomplete");if(type=='f'){try{double number=Double.parseDouble(value);if(!Double.isFinite(number))diagnostics.add("Original nonfinite float tag retained literally: "+raw);}catch(NumberFormatException error){if(!SetLike.nonfinite(value))throw new IOException("Malformed original floating tag",error);diagnostics.add("Original nonfinite float tag retained literally: "+raw);}}result.add(new Tag(name,type,value));}return List.copyOf(result);
    }
    private static final class SetLike{static boolean nonfinite(String value){return List.of("nan","inf","+inf","-inf","infinity","+infinity","-infinity").contains(value.toLowerCase(java.util.Locale.ROOT));}}
    private static final class Lines{
        final java.io.BufferedInputStream input;final Limits limits;long used;Lines(InputStream input,Limits limits){this.input=new java.io.BufferedInputStream(input,65536);this.limits=limits;}
        String next()throws IOException{var line=new ByteArrayOutputStream();boolean read=false;for(;;){int b=input.read();if(b<0)break;if(++used>limits.bytes())throw new IOException("Complete original graph byte capacity exceeded");read=true;if(b=='\n')break;if(line.size()>=limits.lineBytes())throw new IOException("Complete original graph line capacity exceeded");line.write(b);}if(!read)return null;byte[] bytes=line.toByteArray();int length=bytes.length;if(length>0&&bytes[length-1]=='\r')length--;try{return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes,0,length)).toString();}catch(java.nio.charset.CharacterCodingException error){throw new IOException("Original graph is not complete valid UTF-8; no replacement characters are inserted",error);}}
    }
}
