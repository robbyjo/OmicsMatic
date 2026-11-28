/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.splicing;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Stream complete statistical tables through the qualified positional row parser.
 * Row field tokens are retained; memory grows with the original ID census, not
 * with complete rows or selected output. The caller owns the Reader and sink.
 */
public final class RmatsResultScanner {
    private RmatsResultScanner() { }
    public record Observed(long sourceLine,String rawLine,List<String> cells,java.math.BigDecimal rawP,java.math.BigDecimal deltaPsi,Map<String,Long> missingFields,RmatsResults.Decision decision){public Observed{cells=List.copyOf(cells);missingFields=Map.copyOf(missingFields);}}
    @FunctionalInterface public interface Sink {void accept(Observed row)throws IOException;}
    public record Summary(String rawHeader,List<Integer> idPositions,long rows,Map<String,Long> decisions,Map<String,Long> missingFields){public Summary{idPositions=List.copyOf(idPositions);decisions=Map.copyOf(decisions);missingFields=Map.copyOf(missingFields);}}
    public static Summary scan(Reader source,RmatsResults.Event event,RmatsResults.Limits limits,RmatsResults.Threshold threshold,Sink sink)throws IOException {
        if(source==null||event==null||limits==null||threshold==null||sink==null)throw new NullPointerException("Require complete scanner inputs");String header=line(source,limits.lineCharacters());if(header==null)throw new IOException("Missing original rMATS header");var schema=RmatsResults.read(new StringReader(header+"\n"),event,new RmatsResults.Limits(0,limits.lineCharacters(),limits.group1Replicates(),limits.group2Replicates()));var ids=new HashSet<List<String>>();var counts=new TreeMap<String,Long>();for(var state:RmatsResults.Decision.values())counts.put(state.name(),0L);var missing=new TreeMap<String,Long>();long rows=0;String raw;
        while((raw=line(source,limits.lineCharacters()))!=null){if(Thread.currentThread().isInterrupted())throw new IOException("Streamed rMATS check interrupted; no complete result admitted");if(rows>=limits.rows())throw new IOException("Complete rMATS streamed row capacity exceeded at source line "+(rows+2));
            RmatsResults.Row row;try{row=RmatsResults.read(new StringReader(header+"\n"+raw+"\n"),event,new RmatsResults.Limits(1,limits.lineCharacters(),limits.group1Replicates(),limits.group2Replicates())).rows().get(0);}catch(IOException failure){throw new IOException("Invalid complete rMATS stream at source line "+(rows+2)+": "+failure.getMessage(),failure);}
            var tuple=new ArrayList<String>();for(int position:schema.idPositions())tuple.add(row.cells().get(position));if(!ids.add(List.copyOf(tuple)))throw new IOException("Repeated original rMATS ID tuple at source line "+(rows+2)+"; no merge inferred");var decision=threshold.classify(row);counts.merge(decision.name(),1L,Math::addExact);row.missingFields().forEach((key,value)->missing.merge(key,value,Math::addExact));sink.accept(new Observed(rows+2,row.rawLine(),row.cells(),row.rawP(),row.deltaPsi(),row.missingFields(),decision));rows++;
        }
        return new Summary(header,schema.idPositions(),rows,counts,missing);
    }
    /** Bounded original physical lines; no numeric/identifier normalization. */
    private static String line(Reader reader,int maximum)throws IOException{var out=new StringBuilder();int value;boolean any=false;while((value=reader.read())!=-1){any=true;if(value=='\n')break;if(out.length()>=maximum+1L)throw new IOException("Streamed rMATS line capacity exceeded");out.append((char)value);}if(!any)return null;int length=out.length();if(length>0&&out.charAt(length-1)=='\r')out.setLength(--length);if(length>maximum)throw new IOException("Streamed rMATS line capacity exceeded");return out.toString();}
}
