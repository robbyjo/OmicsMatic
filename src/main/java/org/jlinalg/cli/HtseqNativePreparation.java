/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
 * Pairing/GTF rules derive from HTSeq2.1.2 (GPL-3.0), whose pinned original
 * sources and license are retained in omics/htseq-intervals and htseq-read-count. */
package org.jlinalg.cli;

import htsjdk.samtools.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.jlinalg.sequence.HtseqFeatureAssignment.Feature;
import org.jlinalg.sequence.HtseqFeatureAssignment.Interval;
import org.jlinalg.sequence.HtseqReadCounter.Alignment;

/** Native parsing and original HTSeq2.1.2 name/position mate preparation. */
final class HtseqNativePreparation {
    private HtseqNativePreparation() { }
    private static final Pattern ATTRIBUTE=Pattern.compile("\\s*([^\\s=]+)[\\s=]+(.*)",Pattern.UNICODE_CHARACTER_CLASS);
    static List<Feature> features(Path gtf,String featureType,String attribute,boolean stranded,int maximum)throws IOException {
        var result=new ArrayList<Feature>();int lineNumber=0;
        try(var reader=Files.newBufferedReader(gtf)) {
            String line;
            while((line=reader.readLine())!=null) {
                NativeToolSupport.check();lineNumber++;if(line.isEmpty()||line.startsWith("#"))continue;
                if(line.length()>1048576)throw new IOException("GTF line exceeds1MiB at line"+lineNumber);
                String[] f=line.split("\t",9);if(f.length!=9)throw new IOException("GTF requires9fields at line"+lineNumber);
                long start=Math.subtractExact(Long.parseLong(f[3].strip()),1),end=Long.parseLong(f[4].strip());
                if(f[6].length()!=1)throw new IOException("Invalid GTF strand at line"+lineNumber);
                var interval=new Interval(f[0],start,end,f[6].charAt(0));var attributes=attributes(f[8]);
                // Parse source score/frame fields even when this feature type is ignored.
                if(!f[5].equals("."))number(f[5]);if(!f[7].equals("."))new java.math.BigInteger(f[7].strip());
                if(!f[2].equals(featureType))continue;
                if(!attributes.containsKey(attribute))throw new IOException("GTF feature lacks explicit "+attribute+" at line"+lineNumber);
                if(stranded&&interval.strand()=='.')throw new IOException("GTF feature lacks strand information in stranded mode at line"+lineNumber);
                if(result.size()==maximum)throw new IOException("Native GTF feature capacity exceeded");result.add(new Feature(interval,attributes.get(attribute)));
            }
        }
        return result;
    }
    private static double number(String raw) {
        String text=raw.strip().toLowerCase(java.util.Locale.ROOT);return switch(text){case "nan","+nan","-nan"->Double.NaN;case "inf","+inf","infinity","+infinity"->Double.POSITIVE_INFINITY;case "-inf","-infinity"->Double.NEGATIVE_INFINITY;default->Double.parseDouble(text);};
    }
    static Map<String,String> attributes(String text)throws IOException {
        var fields=new ArrayList<String>();int first=0;boolean quoted=false;
        for(int i=0;i<text.length();i++) {
            char c=text.charAt(i);if(c==0)throw new IOException("NUL is outside the native GTF contract");
            if(c=='\"')quoted=!quoted;else if(c==';'&&!quoted){fields.add(text.substring(first,i));first=i+1;}
        }
        if(quoted)throw new IOException("Unmatched GTF attribute quote");fields.add(text.substring(first));var result=new LinkedHashMap<String,String>();
        for(String field:fields) {
            if(field.isBlank())continue;long quotes=field.chars().filter(c->c=='\"').count();if(quotes!=0&&quotes!=2)throw new IOException("Mismatched GTF attribute quotes");
            var match=ATTRIBUTE.matcher(field);if(!match.matches())throw new IOException("Invalid GTF attribute field: "+field);
            String value=match.group(2);if(value.startsWith("\"")&&value.endsWith("\""))value=value.substring(1,value.length()-1);
            result.put(match.group(1),value); // source dict retains the last duplicate attribute
        }
        return result;
    }
    static Alignment alignment(SAMRecord record,int maximumBlocks)throws IOException {
        if(record==null)return null;boolean aligned=!record.getReadUnmappedFlag();var blocks=new ArrayList<Interval>();
        if(aligned) {
            if(record.getReferenceIndex()<0||record.getAlignmentStart()<1||record.getCigar().isEmpty())throw new IOException("Mapped alignment requires reference, positive position and CIGAR");
            long position=record.getAlignmentStart()-1L;char strand=record.getReadNegativeStrandFlag()?'-':'+';
            for(var element:record.getCigar().getCigarElements()) {
                var operation=element.getOperator();int length=element.getLength();
                if((operation==CigarOperator.M||operation==CigarOperator.EQ||operation==CigarOperator.X)&&length>0) {
                    if(blocks.size()==maximumBlocks)throw new IOException("Alignment block capacity exceeded");blocks.add(new Interval(record.getReferenceName(),position,Math.addExact(position,length),strand));
                }
                if(operation.consumesReferenceBases())position=Math.addExact(position,length);
            }
        }
        Object nh=record.getAttribute("NH");Integer count=null;
        if(nh!=null) {if(!(nh instanceof Number n)||n.longValue()<Integer.MIN_VALUE||n.longValue()>Integer.MAX_VALUE||n.doubleValue()!=n.longValue())throw new IOException("Native NH contract requires an exact32-bit integer value; no rounding or string parsing");count=n.intValue();}
        return new Alignment(aligned,record.isSecondaryAlignment(),record.getSupplementaryAlignmentFlag(),record.getMappingQuality(),count,blocks);
    }
    @FunctionalInterface interface PairConsumer {void accept(SAMRecord first,SAMRecord second)throws IOException;}
    static final class Pairer {
        private record Key(String name,int which,Integer reference,Integer position,Integer mateReference,Integer matePosition,Long insert) { }
        private final String order;
        private final int maximum;
        private final PairConsumer consumer;
        private final List<SAMRecord> group=new ArrayList<>();
        private final Map<Key,ArrayDeque<SAMRecord>> buffer=new LinkedHashMap<>();
        private String name;
        private int buffered;
        long missing,ambiguous;
        Pairer(String order,int maximum,PairConsumer consumer){this.order=order;this.maximum=maximum;this.consumer=consumer;}
        static void validate(SAMRecord read)throws IOException {
            if((read.getFlags()&1)==0||(read.getFlags()&192)==0)throw new IOException("HTSeq paired stream requires paired records with first/second status");
        }
        void add(SAMRecord read)throws IOException {
            if(order.equals("name")) {
                if(name!=null&&!name.equals(read.getReadName()))flushGroup();name=read.getReadName();
                if(group.size()==maximum)throw new IOException("Name-group alignment capacity exceeded");group.add(read);
            }else {
                var key=key(read,true);var candidates=buffer.get(key);
                if(candidates!=null) {
                    if(candidates.size()>1)ambiguous++;SAMRecord mate=candidates.removeFirst();buffered--;if(candidates.isEmpty())buffer.remove(key);emit(read,mate);
                }else {
                    if(buffered==maximum)throw new IOException("Position-pairing alignment buffer capacity exceeded");buffer.computeIfAbsent(key(read,false),ignored->new ArrayDeque<>()).addLast(read);buffered++;
                }
            }
        }
        private static Key key(SAMRecord r,boolean mate) {
            boolean aligned=(r.getFlags()&4)==0,mateAligned=(r.getFlags()&8)==0;
            Integer reference=aligned?r.getReferenceIndex():null,position=aligned?r.getAlignmentStart():null;
            Integer mateReference=mateAligned?r.getMateReferenceIndex():null,matePosition=mateAligned?r.getMateAlignmentStart():null;
            Long insert=aligned&&mateAligned?(long)r.getInferredInsertSize():null;int which=(r.getFlags()&64)!=0?1:2;
            return mate?new Key(r.getReadName(),3-which,mateReference,matePosition,reference,position,insert==null?null:-insert):new Key(r.getReadName(),which,reference,position,mateReference,matePosition,insert);
        }
        private static boolean mates(SAMRecord a,SAMRecord b) {
            if(((a.getFlags()&64)!=0)==((b.getFlags()&64)!=0))return false;
            boolean am=(a.getFlags()&4)==0,bm=(b.getFlags()&4)==0;
            if(am!=((b.getFlags()&8)==0)||bm!=((a.getFlags()&8)==0))return false;
            return !(am&&bm)||a.getReferenceIndex().equals(b.getMateReferenceIndex())&&a.getAlignmentStart()==b.getMateAlignmentStart()&&b.getReferenceIndex().equals(a.getMateReferenceIndex())&&b.getAlignmentStart()==a.getMateAlignmentStart();
        }
        private void emit(SAMRecord a,SAMRecord b)throws IOException {if((a.getFlags()&64)!=0)consumer.accept(a,b);else consumer.accept(b,a);}
        private void flushGroup()throws IOException {
            var pending=new ArrayList<>(group);group.clear();
            while(!pending.isEmpty()) {
                NativeToolSupport.check();var a=pending.remove(0);SAMRecord b=null;
                for(int i=0;i<pending.size();i++)if(mates(a,pending.get(i))){b=pending.remove(i);break;}
                if(b==null&&(a.getFlags()&8)==0)missing++;emit(a,b);
            }
        }
        void finish()throws IOException {
            if(order.equals("name"))flushGroup();
            else {for(var queue:buffer.values())for(var r:queue){NativeToolSupport.check();missing++;emit(r,null);}buffer.clear();buffered=0;}
        }
    }
}
