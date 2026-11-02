/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/** Complete bounded CLUSTAL protein blocks and literal '#' column selections.
 * Owns the supplied reader; EOF and successful close are mandatory. Sequence IDs,
 * order and block widths must agree across blocks. No alignment data is inferred
 * from a truncated block, consensus line, row counter or unrecognized footer.
 */
public final class Pal2NalClustal {
    private Pal2NalClustal() { }
    public record Statistics(long decodedCharacters,long cells,long estimatedRetainedBytes,int blocks) { }
    public record Data(List<Pal2Nal.Sequence> rows,String selection,Statistics statistics) {
        public Data { rows=List.copyOf(rows); }
    }
    private static final Pattern ROW=Pattern.compile("^([^\\s]+)([ \\t]+)([A-Za-z0-9*_.\\\\/\\-]+)(?:[ \\t]+([0-9]+))?[ \\t]*$");
    public static Data read(Reader source,MafftSequences.Limits limits,Runnable cancellation)throws IOException {
        Objects.requireNonNull(source);Objects.requireNonNull(limits);Objects.requireNonNull(cancellation);
        Builder builder=new Builder(limits,cancellation);
        try(var reader=new BufferedReader(source)) {
            StringBuilder line=new StringBuilder();int value;boolean previousCr=false;
            while((value=reader.read())>=0) {
                builder.character();
                if(value=='\n'&&previousCr){previousCr=false;continue;}
                previousCr=value=='\r';
                if(value=='\r'||value=='\n'){builder.line(line.toString());line.setLength(0);continue;}
                if(line.length()>=limits.lineCharacters())throw new IOException("Complete CLUSTAL line capacity exceeded");
                builder.longest=Math.max(builder.longest,line.length()+1L);builder.retained();line.append((char)value);
            }
            if(!line.isEmpty())builder.line(line.toString());
            builder.finishBlock();builder.check.run();
        }
        cancellation.run();return builder.result();
    }
    private static final class Builder {
        final MafftSequences.Limits limits;final Runnable check;
        final LinkedHashMap<String,StringBuilder> rows=new LinkedHashMap<>();
        final LinkedHashMap<String,String> block=new LinkedHashMap<>();
        final LinkedHashMap<String,Long> residueCounts=new LinkedHashMap<>();
        final StringBuilder mask=new StringBuilder();List<String> order;
        long decoded,cells,headers,longest;int blocks,width,sequenceStart;boolean header;String consensus;
        Builder(MafftSequences.Limits limits,Runnable check){this.limits=limits;this.check=check;}
        void character()throws IOException {
            if((decoded&1023)==0)check.run();if(++decoded>limits.decodedCharacters())throw new IOException("Complete CLUSTAL decoded-character capacity exceeded");
        }
        long retained()throws IOException {
            long estimate=Math.addExact(65536,Math.addExact(Math.multiplyExact(cells,8),Math.addExact(Math.multiplyExact(rows.size(),1024L),Math.multiplyExact(8,Math.addExact(Math.addExact(headers,longest),mask.length())))));
            if(estimate>limits.estimatedRetainedBytes())throw new IOException("Complete CLUSTAL retained-storage capacity exceeded");return estimate;
        }
        void line(String line)throws IOException {
            check.run();
            if(line.isBlank()){finishBlock();return;}
            if(!header){
                if(!line.matches("CLUSTAL(?:[ \\t].*)?"))throw new IOException("Require an explicit CLUSTAL header; format is not guessed");
                header=true;return;
            }
            if(line.charAt(0)==' '||line.charAt(0)=='\t') {
                if(block.isEmpty()||consensus!=null)throw new IOException("Orphan or repeated CLUSTAL consensus/selection line");
                for(int i=0;i<Math.min(sequenceStart,line.length());i++)if(line.charAt(i)!=' '&&line.charAt(i)!='\t')throw new IOException("Misplaced CLUSTAL selection prefix");
                String body=line.length()<=sequenceStart?"":line.substring(sequenceStart);
                if(body.length()>width&&!body.substring(width).isBlank())throw new IOException("CLUSTAL consensus extends beyond the block");
                body=body.substring(0,Math.min(width,body.length()));
                if(body.chars().anyMatch(c->" *:.#".indexOf(c)<0))throw new IOException("Unknown CLUSTAL consensus/selection character");
                consensus=body+" ".repeat(width-body.length());finishBlock();return;
            }
            var match=ROW.matcher(line);if(!match.matches())throw new IOException("Malformed CLUSTAL sequence row or unrecognized footer");
            String id=match.group(1),segment=match.group(3);
            if(id.length()>limits.headerCharacters()||id.isBlank()||id.codePoints().anyMatch(Character::isISOControl))throw new IOException("Invalid CLUSTAL ID");
            for(int i=0;i<id.length();i++){
                char c=id.charAt(i);if(Character.isHighSurrogate(c)){if(++i==id.length()||!Character.isLowSurrogate(id.charAt(i)))throw new IOException("Unpaired CLUSTAL ID surrogate");}
                else if(Character.isLowSurrogate(c))throw new IOException("Unpaired CLUSTAL ID surrogate");
            }
            if(block.putIfAbsent(id,segment)!=null)throw new IOException("Duplicate CLUSTAL ID in a block: "+id);
            if(block.size()==1){width=segment.length();sequenceStart=match.start(3);}
            if(segment.length()!=width||match.start(3)!=sequenceStart)throw new IOException("CLUSTAL block widths or sequence-column starts differ");
            if(!rows.containsKey(id)){
                if(order!=null||rows.size()>=limits.sequences())throw new IOException("Unexpected CLUSTAL ID or sequence capacity exceeded");
                headers=Math.addExact(headers,id.length());rows.put(id,new StringBuilder());residueCounts.put(id,0L);
            }
            StringBuilder sequence=rows.get(id);
            if((long)sequence.length()+segment.length()>limits.sequenceCharacters())throw new IOException("Complete CLUSTAL row capacity exceeded");
            cells=Math.addExact(cells,segment.length());if(cells>limits.cells())throw new IOException("Complete CLUSTAL cell capacity exceeded");retained();
            sequence.append(segment);
            long residues=Math.addExact(residueCounts.get(id),segment.chars().filter(c->c!='-'&&c!='.').count());residueCounts.put(id,residues);
            if(match.group(4)!=null){
                long declared;try{declared=Long.parseLong(match.group(4));}catch(NumberFormatException e){throw new IOException("CLUSTAL cumulative row counter overflow",e);}
                if(declared!=residues)throw new IOException("CLUSTAL cumulative row counter disagrees with complete data for "+id);
            }
        }
        void finishBlock()throws IOException {
            if(block.isEmpty())return;
            List<String> actual=new ArrayList<>(block.keySet());
            if(order==null)order=List.copyOf(actual);else if(!order.equals(actual))throw new IOException("Missing, extra or reordered CLUSTAL block rows");
            String value=consensus==null?" ".repeat(width):consensus;
            for(int i=0;i<value.length();i++)mask.append(value.charAt(i)=='#'?'#':' ');
            retained();block.clear();consensus=null;blocks++;
        }
        Data result()throws IOException {
            if(!header||rows.isEmpty()||blocks==0)throw new IOException("CLUSTAL has no complete protein alignment");
            var result=new ArrayList<Pal2Nal.Sequence>();int columns=-1;
            for(var entry:rows.entrySet()){
                String sequence=entry.getValue().toString();if(columns<0)columns=sequence.length();
                if(sequence.isEmpty()||sequence.length()!=columns||mask.length()!=columns)throw new IOException("Incomplete CLUSTAL row/selection population");
                result.add(new Pal2Nal.Sequence(entry.getKey(),sequence));
            }
            return new Data(result,mask.toString(),new Statistics(decoded,cells,retained(),blocks));
        }
    }
}
