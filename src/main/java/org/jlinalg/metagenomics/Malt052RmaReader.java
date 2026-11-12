/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.metagenomics;

import java.io.*;
import java.lang.reflect.*;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.jar.JarFile;

/** Isolated adapter to the original MALT0.5.2 upstream RMA6 reader.
 * This is not a new binary-format parser. Runtime artifacts remain external.
 * Reader selection is the source profile minScore0/maxExpected Float.MAX_VALUE.
 * Callbacks are provisional until read() returns a verified complete report.
 */
public final class Malt052RmaReader {
    private Malt052RmaReader(){ }
    private static final Signature PROGRAM=new Signature(218319,"b2683d6c7306fdec55b8a7b634aa409197799e3a7c339e5be9b2797ac3a06928");
    private static final Signature DATA=new Signature(39305470,"b43c06b4503536ef2811380ea2589c7c6a641148bba920d72a7de846216219ec");
    private static final Signature TREE=new Signature(17422320,"056726e083f3d04b2326a12aa9cc5005498d67584ea4b901dc084c6059d5ca57");
    private static final Signature JLODA=new Signature(1486889,"00b68ab83e81f11a0c62c9fb7c6566ee57e9d4ae2772f587208920c878c2c475");
    private static final Class<?>[] NONE=new Class<?>[0];
    public record Signature(long bytes,String sha256){ }
    public record Profile(float minimumScore,float maximumExpected,boolean sequence){ }
    public static final Profile RAW=new Profile(0f,Float.MAX_VALUE,true);
    public static final Profile SERIAL_PROCESSOR=new Profile(0f,10f,false);
    public record Limits(long inputBytes,int reads,long matches,int matchesPerRead,int textCharacters,int classifications) {
        public Limits {if(inputBytes<1 || reads<1 || matches<1 || matchesPerRead<1 || textCharacters<1 || classifications<1)throw new IllegalArgumentException("Positive complete-input capacities required");}
    }
    public record Match(int index,int taxon,float bitScore,float expected,float percentIdentity,boolean ignored,
            String reference,String text,int alignedStart,int alignedEnd,int referenceLength) {
        public Malt052Assignment.Match assignmentMatch(){return new Malt052Assignment.Match(taxon,bitScore,expected,percentIdentity,ignored);}
    }
    public record Read(long uid,String name,String header,String sequence,int length,int weight,
            int totalMatches,List<Match> matches,int storedTaxon) {
        public Read {matches=List.copyOf(matches);}
    }
    public record Classification(int taxon,int reads,float weightedReads){ }
    public record Report(Signature input,Signature program,Signature data,Signature jloda,Profile profile,long reads,long storedMatches,
            long availableMatches,Map<Integer,Classification> classifications,List<String> warnings) {
        public Report {classifications=Collections.unmodifiableMap(new TreeMap<>(classifications));warnings=List.copyOf(warnings);}
    }
    public static final class MissingRuntimeException extends IOException {
        private static final long serialVersionUID=1L;
        private MissingRuntimeException(String message,Throwable cause){super("SKIP3 UPSTREAM MALT RUNTIME: "+message+" Required action: supply the permitted MALT0.5.2 jars/malt.jar, jars/megan.jar and jars/jloda.jar provider with the documented SHA256 values and a compatible Java17+ environment. If installation is unavailable or forbidden, skip this dependent RMA analysis. Do not substitute another runtime or treat a skip as a biological finding.",cause);}
    }
    private static IOException failure(String text){return new IOException(text+" Required action: retain inputs and partial outputs, resolve the named format, integrity or complete-input capacity problem, then retry a NEW analysis directory; no partial result is admitted.");}
    private static MessageDigest digest(){try{return MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException error){throw new AssertionError(error);}}
    private static Signature pin(Path path,long maximum)throws IOException {
        if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS) || Files.size(path)>maximum)throw failure("Missing/nonregular file or byte capacity exceeded: "+path);
        MessageDigest hash=digest();long size=0;byte[] buffer=new byte[1048576];
        try(InputStream input=Files.newInputStream(path)){for(int n;(n=input.read(buffer))!=-1;){size=Math.addExact(size,n);if(size>maximum)throw failure("Complete file exceeds byte capacity: "+path);hash.update(buffer,0,n);}}
        return new Signature(size,HexFormat.of().formatHex(hash.digest()));
    }
    private static void runtime(Path directory)throws MissingRuntimeException {
        try {if(!pin(directory.resolve("jars/malt.jar"),PROGRAM.bytes()).equals(PROGRAM) || !pin(directory.resolve("jars/megan.jar"),DATA.bytes()).equals(DATA) || !pin(directory.resolve("jars/jloda.jar"),JLODA.bytes()).equals(JLODA))throw new IOException("Unrecognized original MALT provider bytes");}
        catch(IOException error){throw new MissingRuntimeException("Missing, unreadable or unrecognized provider at "+directory,error);}
    }
    /** Load the pinned physical tree plus the original loader's four ordered synthetic root leaves.
     * Original0.5.2 adds -1,-2,-3,-6; this never rewrites a database or source resource.
     */
    public static MaltTaxonomy defaultTaxonomy(Path directory,MaltTaxonomy.Limits limits)throws IOException {
        Objects.requireNonNull(directory);Objects.requireNonNull(limits);runtime(directory);byte[] raw;
        try(JarFile archive=new JarFile(directory.resolve("jars/megan.jar").toFile())) {
            var entry=archive.getJarEntry("megan/resources/files/ncbi.tre");if(entry==null||entry.getSize()!=TREE.bytes())throw failure("Original bundled numeric taxonomy is absent/incomplete");
            try(var input=archive.getInputStream(entry)){raw=input.readNBytes(Math.toIntExact(TREE.bytes()+1));}
        }
        if(raw.length!=TREE.bytes()||!HexFormat.of().formatHex(digest().digest(raw)).equals(TREE.sha256()))throw failure("Complete original physical taxonomy differs");
        String physical=StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(raw)).toString();
        if(!physical.endsWith(")1;\n"))throw failure("Unexpected original root serialization");
        String effective=physical.substring(0,physical.length()-4)+",-1,-2,-3,-6)1;\n";
        MaltTaxonomy tree=MaltTaxonomy.read(new StringReader(effective),limits);
        if(tree.size()!=2259893||tree.root()!=1||!tree.contains(-6))throw failure("Complete original0.5.2 effective taxonomy differs");runtime(directory);return tree;
    }
    public static Report read(Path directory,Path input,Limits limits,Consumer<Read> consumer)throws IOException {
        return read(directory,input,limits,RAW,consumer);
    }
    /** Explicit getter profile, including the original serial processor's0/10/no-sequence selection.
     * Stored class membership is always read with the raw profile.
     */
    public static Report read(Path directory,Path input,Limits limits,Profile profile,Consumer<Read> consumer)throws IOException {
        Objects.requireNonNull(directory);Objects.requireNonNull(input);Objects.requireNonNull(limits);Objects.requireNonNull(consumer);
        Objects.requireNonNull(profile);
        runtime(directory);Signature before=pin(input,limits.inputBytes());var classes=new TreeMap<Integer,Classification>();var membership=new HashMap<Long,Integer>();var seen=new HashSet<Long>();var warnings=new ArrayList<String>();long reads=0,total=0,available=0,nonpositiveWeights=0;
        try(var loader=new URLClassLoader(new java.net.URL[]{directory.resolve("jars/malt.jar").toUri().toURL(),directory.resolve("jars/megan.jar").toUri().toURL(),directory.resolve("jars/jloda.jar").toUri().toURL()},ClassLoader.getPlatformClassLoader())) {
            Class<?> connectorType=loader.loadClass("megan.rma6.RMA6Connector"),readType=loader.loadClass("megan.data.IReadBlock"),matchType=loader.loadClass("megan.data.IMatchBlock"),classType=loader.loadClass("megan.data.IClassificationBlock");
            Object connector=connectorType.getConstructor(String.class).newInstance(input.toAbsolutePath().toString());
            long reportedReads=number(call(connectorType,connector,"getNumberOfReads",NONE)).longValue(),reportedMatches=number(call(connectorType,connector,"getNumberOfMatches",NONE)).longValue();
            if(reportedReads<0 || reportedReads>limits.reads() || reportedMatches<0 || reportedMatches>limits.matches())throw failure("Complete RMA header population exceeds declared capacity or is invalid");
            Object block=call(connectorType,connector,"getClassificationBlock",new Class<?>[]{String.class},"Taxonomy");if(block==null&&reportedReads!=0)throw failure("Input has no stored Taxonomy classification; supply a classified RMA6 file");
            Object keys=block==null?Set.of():call(classType,block,"getKeySet",NONE);if(!(keys instanceof Collection<?> ids) || ids.size()>limits.classifications())throw failure("Stored classification groups exceed capacity or are invalid");
            var sorted=new TreeSet<Integer>();for(Object id:ids)if(!sorted.add(number(id).intValue()))throw failure("Duplicate stored classification id");
            for(int id:sorted) {
                int count=number(call(classType,block,"getSum",new Class<?>[]{Integer.class},id)).intValue();float weight=number(call(classType,block,"getWeightedSum",new Class<?>[]{Integer.class},id)).floatValue();
                if(count<0 || count>limits.reads())throw failure("Invalid/over-capacity stored class population");long found=0;
                try(var handle=new Handle(call(connectorType,connector,"getReadsIterator",new Class<?>[]{String.class,int.class,float.class,float.class,boolean.class,boolean.class},"Taxonomy",id,0f,Float.MAX_VALUE,true,true))) {
                    while(handle.iterator.hasNext()) {
                        Object read=handle.iterator.next();long uid=number(call(readType,read,"getUId",NONE)).longValue();
                        if(membership.size()>=limits.reads() || membership.putIfAbsent(uid,id)!=null)throw failure("Duplicate or over-capacity original class/read membership");found++;
                    }
                }
                if(found!=count)throw failure("Stored classification count differs from complete iteration");classes.put(id,new Classification(id,count,weight));
            }
            try(var handle=new Handle(call(connectorType,connector,"getAllReadsIterator",new Class<?>[]{float.class,float.class,boolean.class,boolean.class},profile.minimumScore(),profile.maximumExpected(),profile.sequence(),true))) {
                while(handle.iterator.hasNext()) {
                    Object read=handle.iterator.next();if(reads==limits.reads())throw failure("Complete RMA read count exceeds capacity");reads++;
                    long uid=number(call(readType,read,"getUId",NONE)).longValue();Integer stored=membership.get(uid);if(!seen.add(uid) || stored==null)throw failure("Duplicate read UID or missing stored Taxonomy membership");
                    String name=text(readType,read,"getReadName",limits),header=text(readType,read,"getReadHeader",limits),sequence=text(readType,read,"getReadSequence",limits);
                    int length=integer(readType,read,"getReadLength"),weight=integer(readType,read,"getReadWeight");if(weight<=0)nonpositiveWeights++;
                    int count=number(call(readType,read,"getNumberOfMatches",NONE)).intValue(),have=number(call(readType,read,"getNumberOfAvailableMatchBlocks",NONE)).intValue();
                    if(count<0 || have<0 || have>count || have>limits.matchesPerRead())throw failure("Invalid/incomplete-capacity original match population");total=Math.addExact(total,count);available=Math.addExact(available,have);if(total>limits.matches())throw failure("Complete stored match count exceeds capacity");
                    var matches=new ArrayList<Match>(have);
                    for(int index=0;index<have;index++) {
                        Object match=call(readType,read,"getMatchBlock",new Class<?>[]{int.class},index);
                        matches.add(new Match(index,integer(matchType,match,"getTaxonId"),floating(matchType,match,"getBitScore"),floating(matchType,match,"getExpected"),floating(matchType,match,"getPercentIdentity"),(Boolean)call(matchType,match,"isIgnore",NONE),text(matchType,match,"getRefSeqId",limits),text(matchType,match,"getText",limits),integer(matchType,match,"getAlignedQueryStart"),integer(matchType,match,"getAlignedQueryEnd"),integer(matchType,match,"getRefLength")));
                    }
                    consumer.accept(new Read(uid,name,header,sequence,length,weight,count,matches,stored));
                }
            }
            if(reads!=reportedReads || total!=reportedMatches || !seen.equals(membership.keySet()))throw failure("Complete original RMA header, read, match or classification populations differ");
        } catch(ClassNotFoundException|NoSuchMethodException|InstantiationException|IllegalAccessException|LinkageError error){throw new MissingRuntimeException("Original RMA reader is unavailable/incompatible in this environment",error);}
        catch(InvocationTargetException error){throw failure("Original RMA connector construction failed: "+error.getCause());}
        if(!pin(input,limits.inputBytes()).equals(before))throw failure("Original RMA changed during reading");runtime(directory);
        if(available<total)warnings.add("UNAVAILABLE_MATCH_BLOCKS: "+(total-available)+" stored matches were unavailable under the explicit getter profile "+profile+". Retain total/available counts and original diagnostics; do not claim complete raw-match access or silently reduce a dependent analysis.");
        if(nonpositiveWeights>0)warnings.add("NONPOSITIVE_READ_WEIGHTS: "+nonpositiveWeights+" original weights were retained. Resolve their source meaning before count-based scientific admission.");
        if(reads==0)warnings.add("EMPTY_STORED_RMA: no stored reads; this does not establish absence among original input queries or biological absence.");
        return new Report(before,PROGRAM,DATA,JLODA,profile,reads,total,available,classes,warnings);
    }
    private static Number number(Object value)throws IOException {if(value instanceof Number number)return number;throw failure("Unexpected original numeric API result");}
    private static int integer(Class<?> api,Object target,String name)throws IOException{return number(call(api,target,name,NONE)).intValue();}
    private static float floating(Class<?> api,Object target,String name)throws IOException{return number(call(api,target,name,NONE)).floatValue();}
    private static String text(Class<?> api,Object target,String name,Limits limits)throws IOException {Object value=call(api,target,name,NONE);if(value==null)return null;if(!(value instanceof String text) || text.length()>limits.textCharacters())throw failure("Unexpected/over-capacity original text field: "+name);return text;}
    private static Object call(Class<?> api,Object target,String name,Class<?>[] types,Object...values)throws IOException {
        try {return api.getMethod(name,types).invoke(target,values);}
        catch(InvocationTargetException error){Throwable cause=error.getCause();if(cause instanceof IOException io)throw io;if(cause instanceof LinkageError linkage)throw linkage;throw failure("Original RMA API failed at "+name+": "+cause);}
        catch(ReflectiveOperationException error){throw failure("Original RMA API binding failed at "+name+": "+error);}
    }
    private static final class Handle implements Closeable {
        private final Object raw;private final Iterator<?> iterator;
        private Handle(Object raw)throws IOException {this.raw=raw;if(!(raw instanceof Iterator<?> items) || !(raw instanceof AutoCloseable))throw failure("Unexpected original closeable iterator");iterator=items;}
        @Override public void close()throws IOException {try{((AutoCloseable)raw).close();}catch(InterruptedException error){Thread.currentThread().interrupt();throw new IOException("Interrupted closing original RMA iterator",error);}catch(Exception error){throw new IOException("Failed closing original RMA iterator",error);}}
    }
}
