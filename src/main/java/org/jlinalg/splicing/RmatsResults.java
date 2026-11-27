/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.splicing;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Positional import and explicit post-fit raw-P/absolute-PSI selection for rMATS
 * statistical result tables. No fitting, identifier repair, imputation, sorting,
 * FDR substitution or rounded-PSI recomputation is performed. Original field
 * tokens and either one or two ID columns remain intact.
 * @see <a href="https://github.com/Xinglab/rmats-turbo/blob/v4.1.2/README.md#output">rMATS 4.1.2 output</a>
 */
public final class RmatsResults {
    private RmatsResults() { }
    private static final Pattern NUMBER=Pattern.compile("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?");
    private static final Pattern INTEGER=Pattern.compile("[0-9]+");
    private static final List<String> PREFIX=List.of("ID","GeneID","geneSymbol","chr","strand");
    private static final List<String> SUFFIX=List.of("IJC_SAMPLE_1","SJC_SAMPLE_1","IJC_SAMPLE_2","SJC_SAMPLE_2","IncFormLen","SkipFormLen","PValue","FDR","IncLevel1","IncLevel2","IncLevelDifference");

    public enum Event {
        SE("exonStart_0base","exonEnd","upstreamES","upstreamEE","downstreamES","downstreamEE"),
        MXE("1stExonStart_0base","1stExonEnd","2ndExonStart_0base","2ndExonEnd","upstreamES","upstreamEE","downstreamES","downstreamEE"),
        A3SS("longExonStart_0base","longExonEnd","shortES","shortEE","flankingES","flankingEE"),
        A5SS("longExonStart_0base","longExonEnd","shortES","shortEE","flankingES","flankingEE"),
        RI("riExonStart_0base","riExonEnd","upstreamES","upstreamEE","downstreamES","downstreamEE");
        private final List<String> coordinates;
        Event(String... values){coordinates=List.of(values);}
        /** Exact ordered result schema; the second ID remains positional. */
        public List<String> header(boolean secondId){var result=new ArrayList<>(PREFIX);result.addAll(coordinates);if(secondId)result.add("ID");result.addAll(SUFFIX);return List.copyOf(result);}
    }
    public enum Decision { SELECTED, EXCLUDED, UNASSESSABLE }
    public record Limits(int rows,int lineCharacters,int group1Replicates,int group2Replicates) {
        public Limits {if(rows<0||lineCharacters<1||group1Replicates<1||group2Replicates<1)throw new IllegalArgumentException("Declare nonnegative row capacity and positive line/replicate capacities");}
    }
    /** Raw P is strictly below the maximum; absolute delta PSI includes its boundary. */
    public record Threshold(BigDecimal maximumRawP,BigDecimal minimumAbsolutePsi) {
        public Threshold {unit(maximumRawP,"maximum raw P");unit(minimumAbsolutePsi,"minimum absolute delta PSI");}
        public Decision classify(Row row){Objects.requireNonNull(row);if(row.rawP==null||row.deltaPsi==null)return Decision.UNASSESSABLE;return row.rawP.compareTo(maximumRawP)<0&&row.deltaPsi.abs().compareTo(minimumAbsolutePsi)>=0?Decision.SELECTED:Decision.EXCLUDED;}
    }
    public static final class Row {
        private final int line;
        private final String rawLine;
        private final List<String> cells;
        private final BigDecimal rawP,deltaPsi;
        private final Map<String,Long> missing;
        private Row(int line,String rawLine,List<String> cells,BigDecimal rawP,BigDecimal deltaPsi,Map<String,Long> missing){this.line=line;this.rawLine=rawLine;this.cells=List.copyOf(cells);this.rawP=rawP;this.deltaPsi=deltaPsi;this.missing=Map.copyOf(missing);}
        public int sourceLine(){return line;}
        public String rawLine(){return rawLine;}
        public List<String> cells(){return cells;}
        /** Null denotes the original literal NA, never a replacement numeric value. */
        public BigDecimal rawP(){return rawP;}
        public BigDecimal deltaPsi(){return deltaPsi;}
        public Map<String,Long> missingFields(){return missing;}
    }
    public record Table(String rawHeader,List<String> header,List<Integer> idPositions,List<Row> rows,Map<String,Long> missingFields) {
        public Table {header=List.copyOf(header);idPositions=List.copyOf(idPositions);rows=List.copyOf(rows);missingFields=Map.copyOf(missingFields);}
    }

    /** Bounded import; the caller retains ownership of the Reader. Header-only tables are valid. */
    public static Table read(Reader input,Event event,Limits limits)throws IOException {
        Objects.requireNonNull(input);Objects.requireNonNull(event);Objects.requireNonNull(limits);
        String first=line(input,limits.lineCharacters);if(first==null)throw new IOException("Missing rMATS header");
        List<String> header=Arrays.asList(first.split("\t",-1));boolean second=header.equals(event.header(true));if(!second&&!header.equals(event.header(false)))throw new IOException("Unexpected positional rMATS "+event+" statistical-result schema; do not rename/merge fields");
        var positions=new LinkedHashMap<String,Integer>();for(int i=0;i<header.size();i++)if(!header.get(i).equals("ID"))positions.put(header.get(i),i);
        List<Integer> ids=second?List.of(0,PREFIX.size()+event.coordinates.size()):List.of(0);var identities=new HashSet<List<String>>();var rows=new ArrayList<Row>();var totalMissing=new LinkedHashMap<String,Long>();String raw;
        while((raw=line(input,limits.lineCharacters))!=null){
            if(Thread.currentThread().isInterrupted())throw new IOException("rMATS import interrupted; no complete table admitted");
            int number=rows.size()+2;if(rows.size()>=limits.rows)throw new IOException("rMATS row capacity exceeded at source line "+number);
            List<String> cells=Arrays.asList(raw.split("\t",-1));if(cells.size()!=header.size())throw new IOException("Ragged rMATS row at source line "+number);
            try {
                var identity=new ArrayList<String>();for(int pos:ids){String value=cells.get(pos);if(value.isEmpty())throw new IllegalArgumentException("Empty original ID");identity.add(value);}if(!identities.add(List.copyOf(identity)))throw new IllegalArgumentException("Repeated original ID tuple; no merge inferred");
                if(cells.get(1).isEmpty()||cells.get(3).isEmpty()||!List.of("+","-").contains(cells.get(4)))throw new IllegalArgumentException("Missing gene/chromosome or invalid strand");
                for(int i=0;i<event.coordinates.size();i+=2){long start=count(cells.get(PREFIX.size()+i)),end=count(cells.get(PREFIX.size()+i+1));if(end<=start)throw new IllegalArgumentException("Nonpositive original coordinate interval");}
                var missing=new LinkedHashMap<String,Long>();
                for(String key:List.of("IJC_SAMPLE_1","SJC_SAMPLE_1","IJC_SAMPLE_2","SJC_SAMPLE_2"))vector(cells.get(positions.get(key)),key.endsWith("1")?limits.group1Replicates:limits.group2Replicates,true,key,missing);
                for(String key:List.of("IncFormLen","SkipFormLen"))if(count(cells.get(positions.get(key)))==0)throw new IllegalArgumentException("Nonpositive source form length");
                BigDecimal p=decimal(cells.get(positions.get("PValue")),false,"PValue",missing);decimal(cells.get(positions.get("FDR")),false,"FDR",missing);
                vector(cells.get(positions.get("IncLevel1")),limits.group1Replicates,false,"IncLevel1",missing);vector(cells.get(positions.get("IncLevel2")),limits.group2Replicates,false,"IncLevel2",missing);
                BigDecimal psi=decimal(cells.get(positions.get("IncLevelDifference")),true,"IncLevelDifference",missing);
                missing.forEach((key,value)->totalMissing.merge(key,value,Math::addExact));rows.add(new Row(number,raw,cells,p,psi,missing));
            }catch(IllegalArgumentException|ArithmeticException error){throw new IOException("Invalid rMATS source line "+number+": "+error.getMessage(),error);}
        }
        return new Table(first,header,ids,rows,totalMissing);
    }
    private static void vector(String raw,int expected,boolean counts,String name,Map<String,Long> missing){String[] values=raw.split(",",-1);if(values.length!=expected)throw new IllegalArgumentException("Replicate count differs for "+name);for(String value:values)if(counts)count(value);else decimal(value,false,name,missing);}
    private static long count(String value){if(!INTEGER.matcher(value).matches())throw new IllegalArgumentException("Invalid nonnegative source integer: "+value);return Long.parseLong(value);}
    private static BigDecimal decimal(String raw,boolean signed,String name,Map<String,Long> missing){
        if(raw.equals("NA")){missing.merge(name,1L,Math::addExact);return null;}
        if(raw.length()>128||!NUMBER.matcher(raw).matches())throw new IllegalArgumentException("Invalid finite source decimal for "+name+": "+raw);
        BigDecimal value=new BigDecimal(raw);if(value.scale()<-10000||value.scale()>10000||value.compareTo(signed?BigDecimal.ONE.negate():BigDecimal.ZERO)<0||value.compareTo(BigDecimal.ONE)>0)throw new IllegalArgumentException("Out-of-domain source decimal for "+name);return value;
    }
    private static void unit(BigDecimal value,String name){Objects.requireNonNull(value,name);if(value.compareTo(BigDecimal.ZERO)<0||value.compareTo(BigDecimal.ONE)>0||Math.abs((long)value.scale())>10000)throw new IllegalArgumentException(name+" must be a finite decimal in [0,1] within scale capacity");}
    private static String line(Reader reader,int maximum)throws IOException {
        var result=new StringBuilder();int ch;boolean any=false;
        while((ch=reader.read())!=-1){any=true;if(ch=='\n')break;if(result.length()>=maximum+1L)throw new IOException("rMATS line character capacity exceeded");result.append((char)ch);}
        if(!any)return null;int n=result.length();if(n>0&&result.charAt(n-1)=='\r')result.setLength(--n);if(n>maximum)throw new IOException("rMATS line character capacity exceeded");if(result.indexOf("\r")>=0||result.indexOf("\u0000")>=0)throw new IOException("Unexpected control byte in rMATS line");return result.toString();
    }
}
