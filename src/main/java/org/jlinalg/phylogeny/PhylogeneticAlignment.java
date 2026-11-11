/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.phylogeny;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * Complete aligned DNA, protein or standard-code codon data with exact identities.
 * Parsing is explicit about header interpretation and PHYLIP layout; it never
 * guesses a type, samples columns, shortens names, merges rows or drops sites.
 * Sequence whitespace is removed but state characters and their case are retained.
 * The supplied reader is owned and must reach EOF and close successfully before
 * a result is returned. Plain UTF-8 decoding is strict. Other transport/format and
 * genetic-code contracts must be implemented separately, not silently converted.
 */
public final class PhylogeneticAlignment {
    public enum Kind { DNA, AMINO_ACID, CODON_STANDARD }
    public enum HeaderPolicy { WHOLE_HEADER, FIRST_FIELD }
    public enum Layout { SEQUENTIAL, INTERLEAVED }
    public enum NameField { RELAXED, FIXED_TEN_COLUMNS }
    public record Limits(int taxa, int columns, long cells, long characters,
                         int lineCharacters, int labelCharacters) {
        public Limits {
            if (taxa<1 || columns<1 || cells<1 || characters<1 || lineCharacters<1 || labelCharacters<1)
                throw new IllegalArgumentException("Alignment capacities must be positive");
        }
        public static Limits defaults() { return new Limits(1_000_000,10_000_000,100_000_000,1_073_741_824,16_777_216,1_048_576); }
    }
    public record Row(String taxon, String originalHeader, String sequence) { }
    /**
     * Raw cells count characters. Missing/ambiguous cells count model sites
     * (triplets for codons); partial codons follow the source whole-codon unknown
     * rule. These are source-state counts, not quality filters or accuracy claims.
     */
    public record Statistics(long rawCells, long lowercaseCells, long missingCells,
                             long ambiguousCells, long partialCodonCellsAsUnknown,
                             Map<Character,Long> symbols) {
        public Statistics { symbols=Collections.unmodifiableMap(new TreeMap<>(symbols)); }
    }
    private static final String DNA="ACGTURYSWKMBDHVNX?-~.!";
    private static final String AA="ACDEFGHIKLMNPQRSTVWYBXZJUO*?-~.!";
    private static final String AA_BASES="ACDEFGHIKLMNPQRSTVWY";
    private static final Set<String> OLD=Set.of("1.6.12","2.1.2","2.1.4-beta","2.2.0.3");
    private static final Set<String> NEW=Set.of("2.3.1","2.3.5","2.3.6","2.4.0");
    private final Kind kind;
    private final List<Row> rows;
    private final Map<String,String> sequences, originalToOpaque, opaqueToOriginal;
    private final int columns;
    private final Statistics statistics;

    private PhylogeneticAlignment(Kind kind,List<Row> input,Limits limits) {
        this.kind=Objects.requireNonNull(kind);Objects.requireNonNull(limits);
        if (input.isEmpty()) throw new IllegalArgumentException("Alignment must contain at least one sequence");
        if (input.size()>limits.taxa()) throw new IllegalArgumentException("Complete alignment taxon capacity exceeded");
        columns=input.get(0).sequence().length();
        if (columns<1 || columns>limits.columns()) throw new IllegalArgumentException("Complete alignment column capacity exceeded or empty sequence");
        if (kind==Kind.CODON_STANDARD && columns%3!=0) throw new IllegalArgumentException("Standard-code codon alignment length must be a multiple of three");
        long cells=Math.multiplyExact((long)input.size(),columns);
        if(cells>limits.cells()) throw new IllegalArgumentException("Complete alignment cell capacity exceeded");
        Map<String,String> data=new LinkedHashMap<>(),forward=new LinkedHashMap<>(),inverse=new LinkedHashMap<>();
        Map<Character,Long> census=new TreeMap<>();long lowercase=0,missing=0,ambiguous=0,partial=0;int ordinal=0;
        for(Row row:input) {
            label(row.taxon(),limits);Objects.requireNonNull(row.originalHeader());Objects.requireNonNull(row.sequence());
            if(row.sequence().length()!=columns) throw new IllegalArgumentException("Unequal aligned sequence length for taxon "+row.taxon());
            if(data.putIfAbsent(row.taxon(),row.sequence())!=null) throw new IllegalArgumentException("Duplicate alignment taxon identity: "+row.taxon());
            String opaque=String.format(Locale.ROOT,"T%09d",++ordinal);forward.put(row.taxon(),opaque);inverse.put(opaque,row.taxon());
            for(int i=0;i<columns;i++) {
                char raw=row.sequence().charAt(i),upper=upper(raw);state(upper,kind);
                census.merge(upper,1L,Long::sum);if(raw>='a' && raw<='z')lowercase++;
                if(kind!=Kind.CODON_STANDARD) {
                    int mask=state(upper,kind),full=kind==Kind.DNA?15:1048575;
                    if(mask==full)missing++;else if(Integer.bitCount(mask)>1)ambiguous++;
                }
            }
            if(kind==Kind.CODON_STANDARD) for(int i=0;i<columns;i+=3) {
                int a=state(upper(row.sequence().charAt(i)),kind),b=state(upper(row.sequence().charAt(i+1)),kind),c=state(upper(row.sequence().charAt(i+2)),kind);
                if(Integer.bitCount(a)==1 && Integer.bitCount(b)==1 && Integer.bitCount(c)==1) {
                    if(a==8 && (b==1 && (c==1 || c==4) || b==4 && c==1))
                        throw new IllegalArgumentException("Standard-code stop codon "+dnaBase(a)+dnaBase(b)+dnaBase(c)+" for taxon "+row.taxon()+" at nucleotide column "+(i+1));
                } else { missing++;if(a!=15 || b!=15 || c!=15) { partial++;ambiguous++; } }
            }
        }
        rows=List.copyOf(input);sequences=Collections.unmodifiableMap(data);originalToOpaque=Collections.unmodifiableMap(forward);opaqueToOriginal=Collections.unmodifiableMap(inverse);
        statistics=new Statistics(cells,lowercase,missing,ambiguous,partial,census);
    }
    public Kind kind() { return kind; }
    public int columns() { return columns; }
    public int modelSites() { return kind==Kind.CODON_STANDARD?columns/3:columns; }
    public List<Row> rows() { return rows; }
    public Map<String,String> sequences() { return sequences; }
    public Set<String> taxa() { return sequences.keySet(); }
    public Statistics statistics() { return statistics; }
    public Map<String,String> originalToOpaque() { return originalToOpaque; }
    public Map<String,String> opaqueToOriginal() { return opaqueToOriginal; }
    /** Compare admitted source emission states without changing either original row. */
    public boolean sourceEquivalent(String first,String second) {
        return sourceEquivalent(first,this,second);
    }
    /** Compare admitted rows from two complete alignments with the same alphabet/dimensions. */
    public boolean sourceEquivalent(String first,PhylogeneticAlignment other,String second) {
        if(kind!=other.kind||columns!=other.columns)throw new IllegalArgumentException("Source-equivalence alphabets/dimensions differ");
        String a=sequences.get(first),b=other.sequences.get(second);
        if(a==null||b==null)throw new IllegalArgumentException("Unknown exact alignment taxon in source-equivalence check");
        if(kind!=Kind.CODON_STANDARD) {
            for(int i=0;i<columns;i++)if(state(upper(a.charAt(i)),kind)!=state(upper(b.charAt(i)),kind))return false;
        } else for(int i=0;i<columns;i+=3) {
            boolean missingA=false,missingB=false;int codeA=0,codeB=0;
            for(int j=0;j<3;j++) {
                int x=state(upper(a.charAt(i+j)),kind),y=state(upper(b.charAt(i+j)),kind);
                missingA|=Integer.bitCount(x)!=1;missingB|=Integer.bitCount(y)!=1;codeA=(codeA<<4)|x;codeB=(codeB<<4)|y;
            }
            if(missingA!=missingB||!missingA&&codeA!=codeB)return false;
        }
        return true;
    }

    /** This check is based on retained real probes of the eight exact releases. */
    public void requireIqtreeVersion(String version) {
        if(!OLD.contains(version) && !NEW.contains(version)) throw new IllegalArgumentException("Unvalidated IQ-TREE alignment-state edition: "+version+"; do not substitute another release");
        if(OLD.contains(version) && statistics.symbols().containsKey('!'))
            throw new IllegalArgumentException("IQ-TREE "+version+" rejects ! in alignments; retain the input and resolve its source encoding rather than silently replacing the marker");
    }

    /** A separate JC69 input view; original source symbols remain in sequences(). */
    public Map<String,String> jc69Sequences() {
        if(kind!=Kind.DNA) throw new IllegalStateException("JC69 input view requires DNA, not protein/codon data");
        Map<String,String> result=new LinkedHashMap<>();
        for(Row row:rows) {
            StringBuilder value=new StringBuilder(columns);
            for(int i=0;i<columns;i++) { char c=upper(row.sequence().charAt(i));value.append(c=='U'?'T':state(c,kind)==15?'N':c); }
            result.put(row.taxon(),value.toString());
        }
        return Collections.unmodifiableMap(result);
    }

    /** Writes all rows/sites. The caller owns the writer and final output admission. */
    public void writeFasta(Writer writer,boolean opaqueNames,int wrapColumns) throws IOException {
        Objects.requireNonNull(writer);if(wrapColumns<1)throw new IllegalArgumentException("FASTA wrap width must be positive");
        for(Row row:rows) {
            writer.write('>');writer.write(opaqueNames?originalToOpaque.get(row.taxon()):row.taxon());writer.write('\n');
            for(int at=0;at<columns;) { int end=(int)Math.min(columns,(long)at+wrapColumns);writer.write(row.sequence(),at,end-at);writer.write('\n');at=end; }
        }
    }

    public static PhylogeneticAlignment of(Map<String,String> sequences,Kind kind,Limits limits) {
        List<Row> rows=new ArrayList<>();
        for(var entry:Objects.requireNonNull(sequences).entrySet()) {
            if(rows.size()==limits.taxa())throw new IllegalArgumentException("Complete alignment taxon capacity exceeded");
            rows.add(new Row(entry.getKey(),entry.getKey(),entry.getValue()));
        }
        return new PhylogeneticAlignment(kind,rows,limits);
    }

    public static PhylogeneticAlignment readFasta(Path path,Kind kind,HeaderPolicy policy,Limits limits) throws IOException {
        Objects.requireNonNull(kind);Objects.requireNonNull(policy);Objects.requireNonNull(limits);
        return readFasta(utf8(path),kind,policy,limits);
    }
    public static PhylogeneticAlignment readFasta(Reader source,Kind kind,HeaderPolicy policy,Limits limits) throws IOException {
        Objects.requireNonNull(kind);Objects.requireNonNull(policy);Objects.requireNonNull(limits);
        PhylogeneticAlignment result;
        try(Lines lines=new Lines(source,limits)) {
            List<Row> rows=new ArrayList<>();String header=null,name=null;StringBuilder sequence=new StringBuilder();long cells=0;
            for(String line=lines.next();line!=null;line=lines.next()) {
                if(line.startsWith(">")) {
                    if(header!=null)rows.add(new Row(name,header,sequence.toString()));
                    if(rows.size()==limits.taxa())throw new IllegalArgumentException("Complete alignment taxon capacity exceeded");
                    header=line.substring(1);String trimmed=trim(header);name=policy==HeaderPolicy.WHOLE_HEADER?trimmed:firstField(trimmed);label(name,limits);sequence=new StringBuilder();
                } else if(!trim(line).isEmpty()) {
                    if(header==null)throw new IllegalArgumentException("FASTA sequence before its first header");
                    int added=append(sequence,line,kind,limits.columns());cells=Math.addExact(cells,added);
                    if(cells>limits.cells())throw new IllegalArgumentException("Complete alignment cell capacity exceeded");
                }
            }
            if(header!=null)rows.add(new Row(name,header,sequence.toString()));
            result=new PhylogeneticAlignment(kind,rows,limits);
        }
        return result;
    }

    /** Continuation rows are named only when explicitly declared; their order is checked. */
    public static PhylogeneticAlignment readPhylip(Reader source,Kind kind,Layout layout,NameField names,
                                                  boolean namedContinuation,Limits limits) throws IOException {
        Objects.requireNonNull(kind);Objects.requireNonNull(layout);Objects.requireNonNull(names);Objects.requireNonNull(limits);
        if(layout==Layout.SEQUENTIAL && namedContinuation)throw new IllegalArgumentException("Named continuation is an interleaved PHYLIP contract");
        PhylogeneticAlignment result;
        try(Lines lines=new Lines(source,limits)) {
            String header=lines.nonempty();if(header==null)throw new IllegalArgumentException("Missing PHYLIP dimensions");
            String[] dimensions=trim(header).split("[ \\t]+");
            if(dimensions.length!=2 || !dimensions[0].matches("[0-9]+") || !dimensions[1].matches("[0-9]+"))throw new IllegalArgumentException("PHYLIP header requires exactly two positive integer dimensions");
            int taxa=Integer.parseInt(dimensions[0]),columns=Integer.parseInt(dimensions[1]);
            if(taxa<1 || taxa>limits.taxa() || columns<1 || columns>limits.columns() || Math.multiplyExact((long)taxa,columns)>limits.cells())throw new IllegalArgumentException("Complete PHYLIP dimensions exceed declared alignment capacities");
            List<String> ids=new ArrayList<>(),headers=new ArrayList<>();List<StringBuilder> values=new ArrayList<>();
            for(int row=0;row<taxa;row++) {
                String line=lines.nonempty();if(line==null)throw new IllegalArgumentException("Truncated PHYLIP first block");
                Named named=named(line,names,limits);ids.add(named.name());headers.add(named.header());StringBuilder seq=new StringBuilder();append(seq,named.sequence(),kind,columns);values.add(seq);
                if(layout==Layout.SEQUENTIAL) while(seq.length()<columns) {
                    line=lines.nonempty();if(line==null)throw new IllegalArgumentException("Truncated sequential PHYLIP sequence");append(seq,line,kind,columns);
                }
                else if(row>0 && seq.length()!=values.get(0).length())throw new IllegalArgumentException("Unequal PHYLIP interleaved block widths");
            }
            if(layout==Layout.INTERLEAVED) while(values.get(0).length()<columns) {
                int width=-1;
                for(int row=0;row<taxa;row++) {
                    String line=lines.nonempty();if(line==null)throw new IllegalArgumentException("Truncated PHYLIP interleaved block");
                    if(namedContinuation) {
                        Named named=named(line,names,limits);if(!named.name().equals(ids.get(row)))throw new IllegalArgumentException("PHYLIP continuation taxon identity/order mismatch");line=named.sequence();
                    }
                    int count=append(values.get(row),line,kind,columns);
                    if(width<0)width=count;else if(width!=count)throw new IllegalArgumentException("Unequal PHYLIP interleaved block widths");
                }
            }
            if(lines.nonempty()!=null)throw new IllegalArgumentException("Trailing PHYLIP record/content; complete alignment requires exact declared dimensions");
            List<Row> rows=new ArrayList<>();for(int row=0;row<taxa;row++)rows.add(new Row(ids.get(row),headers.get(row),values.get(row).toString()));
            result=new PhylogeneticAlignment(kind,rows,limits);
        }
        return result;
    }
    private record Named(String name,String header,String sequence) { }
    private static Named named(String line,NameField names,Limits limits) {
        int split;
        if(names==NameField.FIXED_TEN_COLUMNS) { if(line.length()<11)throw new IllegalArgumentException("PHYLIP fixed-width row requires ten name columns and sequence content");split=10; }
        else { split=0;while(split<line.length() && line.charAt(split)!=' ' && line.charAt(split)!='\t')split++;if(split==line.length() || split==0)throw new IllegalArgumentException("Relaxed PHYLIP row requires a taxon then a whitespace-separated sequence"); }
        String header=line.substring(0,split),name=trim(header);label(name,limits);return new Named(name,header,line.substring(split));
    }
    private static int append(StringBuilder value,String text,Kind kind,int maximumColumns) {
        int before=value.length();
        for(int i=0;i<text.length();i++) {
            char c=text.charAt(i);if(c==' ' || c=='\t')continue;state(upper(c),kind);
            if(value.length()==maximumColumns)throw new IllegalArgumentException("Complete alignment sequence column capacity/dimension exceeded");value.append(c);
        }
        if(value.length()==before)throw new IllegalArgumentException("Empty alignment sequence chunk");return value.length()-before;
    }
    private static void label(String value,Limits limits) {
        Objects.requireNonNull(value);if(value.isEmpty() || value.length()>limits.labelCharacters())throw new IllegalArgumentException("Empty alignment identity or label capacity exceeded");
        for(int i=0;i<value.length();i++) { char c=value.charAt(i);if(Character.isISOControl(c))throw new IllegalArgumentException("Control character in alignment identity; use an explicit supported header policy");if(Character.isHighSurrogate(c)) { if(++i==value.length() || !Character.isLowSurrogate(value.charAt(i)))throw new IllegalArgumentException("Unpaired surrogate in alignment identity"); }else if(Character.isLowSurrogate(c))throw new IllegalArgumentException("Unpaired surrogate in alignment identity"); }
    }
    private static char upper(char c) { if(c>127)throw new IllegalArgumentException("Non-ASCII alignment state");return c>='a' && c<='z'?(char)(c-32):c; }
    private static char dnaBase(int mask) { return "ACGT".charAt(Integer.numberOfTrailingZeros(mask)); }
    private static int state(char c,Kind kind) {
        String allowed=kind==Kind.AMINO_ACID?AA:DNA;
        if(allowed.indexOf(c)<0)throw new IllegalArgumentException("Unsupported "+kind+" alignment state: "+c);
        if(kind==Kind.AMINO_ACID) {
            int index=AA_BASES.indexOf(c);if(index>=0)return 1<<index;
            return switch(c) {case 'B'->(1<<AA_BASES.indexOf('N'))|(1<<AA_BASES.indexOf('D'));case 'Z'->(1<<AA_BASES.indexOf('Q'))|(1<<AA_BASES.indexOf('E'));case 'J'->(1<<AA_BASES.indexOf('I'))|(1<<AA_BASES.indexOf('L'));default->1048575;};
        }
        return switch(c) {case 'A'->1;case 'C'->2;case 'G'->4;case 'T','U'->8;case 'R'->5;case 'Y'->10;case 'S'->6;case 'W'->9;case 'K'->12;case 'M'->3;case 'B'->14;case 'D'->13;case 'H'->11;case 'V'->7;default->15;};
    }
    private static String trim(String value) { int start=0,end=value.length();while(start<end && (value.charAt(start)==' ' || value.charAt(start)=='\t'))start++;while(end>start && (value.charAt(end-1)==' ' || value.charAt(end-1)=='\t'))end--;return value.substring(start,end); }
    private static String firstField(String value) { int end=0;while(end<value.length() && value.charAt(end)!=' ' && value.charAt(end)!='\t')end++;return value.substring(0,end); }
    private static Reader utf8(Path path) throws IOException { return new InputStreamReader(Files.newInputStream(path),StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)); }
    private static final class Lines implements AutoCloseable {
        private final Reader reader;private final Limits limits;private long characters;private int pending=-2;
        Lines(Reader source,Limits limits) { reader=source instanceof BufferedReader?source:new BufferedReader(Objects.requireNonNull(source));this.limits=limits; }
        private int raw() throws IOException { int c=reader.read();if(c>=0 && Math.incrementExact(characters)>limits.characters())throw new IllegalArgumentException("Complete alignment input character capacity exceeded");if(c>=0)characters++;return c; }
        String next() throws IOException {
            StringBuilder line=new StringBuilder();
            while(true) {
                int c;if(pending!=-2) { c=pending;pending=-2; }else c=raw();
                if(c<0)return line.isEmpty()?null:line.toString();if(c=='\n')return line.toString();
                if(c=='\r') { int following=raw();if(following!='\n')pending=following;return line.toString(); }
                if(c<32 && c!='\t' || c==127)throw new IllegalArgumentException("Invalid control in alignment input");
                if(line.length()==limits.lineCharacters())throw new IllegalArgumentException("Complete alignment line character capacity exceeded");line.append((char)c);
            }
        }
        String nonempty() throws IOException { String line;do { line=next(); }while(line!=null && trim(line).isEmpty());return line; }
        @Override public void close() throws IOException { reader.close(); }
    }
}
