/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

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
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Complete literal FASTA inputs and residue-preserving alignment audits.
 * This class does not choose an alignment, infer a biological alphabet, remove
 * residues, translate codons, or score biological alignment accuracy.
 * A supplied reader is owned; EOF and successful close are required.
 */
public final class MafftSequences {
    private MafftSequences() { }
    public enum Alphabet { NUCLEOTIDE, PROTEIN }
    public enum Symbols { BIOLOGICAL, ANY_PRINTABLE }
    public enum Headers { WHOLE_HEADER, FIRST_FIELD }
    public enum Layout { SEQUENCES, ALIGNMENT }
    public enum Gaps { ALLOW, FORBID }
    public enum OutputOrder { INPUT, SOURCE }
    public record Limits(int sequences,int sequenceCharacters,long cells,long decodedCharacters,
                         int lineCharacters,int headerCharacters,long estimatedRetainedBytes) {
        public Limits {
            if(sequences<1||sequenceCharacters<1||cells<1||decodedCharacters<1
                    ||lineCharacters<1||headerCharacters<1||headerCharacters>lineCharacters
                    ||estimatedRetainedBytes<65536)
                throw new IllegalArgumentException("Positive complete FASTA capacities are required");
        }
        public static Limits defaults() {
            return new Limits(1_000_000,10_000_000,100_000_000,1_073_741_824,16_777_216,1_048_576,1_073_741_824);
        }
    }
    public record Row(String taxon,String originalHeader,String sequence) { }
    public record Statistics(long cells,long residues,long gapCells,long sequenceWhitespace,
                             long decodedCharacters,long estimatedRetainedBytes) { }
    public static final class Data {
        private final Alphabet alphabet;
        private final Symbols symbols;
        private final Layout layout;
        private final List<Row> rows;
        private final Map<String,Row> byTaxon;
        private final Statistics statistics;
        private Data(Alphabet alphabet,Symbols symbols,Layout layout,List<Row> rows,Statistics statistics) {
            this.alphabet=alphabet;this.symbols=symbols;this.layout=layout;this.rows=List.copyOf(rows);
            var map=new LinkedHashMap<String,Row>();
            for(Row row:rows)if(map.putIfAbsent(row.taxon(),row)!=null)
                throw new IllegalArgumentException("Duplicate exact FASTA taxon: "+row.taxon());
            this.byTaxon=Collections.unmodifiableMap(map);this.statistics=statistics;
        }
        public Alphabet alphabet(){return alphabet;}
        public Symbols symbols(){return symbols;}
        public Layout layout(){return layout;}
        public List<Row> rows(){return rows;}
        public Set<String> taxa(){return byTaxon.keySet();}
        public Map<String,Row> byTaxon(){return byTaxon;}
        public Statistics statistics(){return statistics;}
        public int columns(){
            if(layout!=Layout.ALIGNMENT)throw new IllegalStateException("Column count requires a complete alignment");
            return rows.get(0).sequence().length();
        }
        public Map<String,String> originalToOpaque(){
            var result=new LinkedHashMap<String,String>();int i=0;
            for(Row row:rows)result.put(row.taxon(),String.format(java.util.Locale.ROOT,"T%09d",++i));
            return Collections.unmodifiableMap(result);
        }
        /**
         * Write every row, optionally removing input gap characters explicitly.
         * Original strings in this data object remain unchanged.
         */
        public void writeFasta(Writer out,Map<String,String> names,boolean removeGaps,int wrap)throws IOException {
            Objects.requireNonNull(out);Objects.requireNonNull(names);
            names=new LinkedHashMap<>(names);
            if(wrap<1||!names.keySet().equals(taxa())||names.values().stream().distinct().count()!=names.size())
                throw new IllegalArgumentException("Require a complete one-to-one FASTA name mapping and positive wrap width");
            for(String label:names.values())validateHeader(label,Headers.WHOLE_HEADER,Integer.MAX_VALUE);
            for(Row row:rows){
                out.write('>');out.write(names.get(row.taxon()));out.write('\n');int column=0;
                for(int i=0;i<row.sequence().length();i++){
                    char c=row.sequence().charAt(i);if(removeGaps&&c=='-')continue;
                    out.write(c);if(++column==wrap){out.write('\n');column=0;}
                }
                if(column!=0)out.write('\n');
                else if(removeGaps&&row.sequence().chars().allMatch(c->c=='-'))out.write('\n');
            }
        }
    }
    public record AlignmentAudit(int taxa,int columns,long originalCells,long originalResidues,
                                 long alignedCells,long alignedGapCells,long allGapColumns,
                                 long caseChangedResidues,Map<String,Integer> originalToOutputRow) {
        public AlignmentAudit {originalToOutputRow=Collections.unmodifiableMap(new LinkedHashMap<>(originalToOutputRow));}
    }
    public enum Backbone { KEEP_LENGTH, EXPAND }
    public record BackboneAudit(int taxa,int originalColumns,int outputColumns,long originalGapOnlyColumns,
                                long outputBackboneGapOnlyColumns,long matchedNonemptyColumns,
                                boolean gapOnlyColumnIdentityInferred) { }
    /** Combine complete populations without changing either parent's rows or symbols. */
    public static Data combine(Data first,Data second,Limits limits)throws IOException {
        Objects.requireNonNull(first);Objects.requireNonNull(second);Objects.requireNonNull(limits);
        if(first.alphabet()!=second.alphabet()||first.symbols()!=second.symbols())
            throw new IllegalArgumentException("Combined FASTA alphabets/symbol policies differ");
        long size=Math.addExact((long)first.rows().size(),second.rows().size());
        long cells=Math.addExact(first.statistics().cells(),second.statistics().cells());
        long decoded=Math.addExact(first.statistics().decodedCharacters(),second.statistics().decodedCharacters());
        if(size>limits.sequences()||cells>limits.cells()||decoded>limits.decodedCharacters())
            throw new IOException("Complete combined FASTA row/cell/character capacity exceeded");
        var rows=new ArrayList<Row>(first.rows());rows.addAll(second.rows());long headers=0,line=0;
        for(Row row:rows){
            if(row.sequence().length()>limits.sequenceCharacters()||row.originalHeader().length()>limits.headerCharacters())
                throw new IOException("Complete combined FASTA row/header capacity exceeded");
            headers=Math.addExact(headers,row.originalHeader().length());line=Math.max(line,row.originalHeader().length()+1L);
        }
        long memory=retained(cells,(int)size,headers,line,limits);
        return new Data(first.alphabet(),first.symbols(),Layout.SEQUENCES,rows,
                new Statistics(cells,Math.addExact(first.statistics().residues(),second.statistics().residues()),
                    Math.addExact(first.statistics().gapCells(),second.statistics().gapCells()),
                    Math.addExact(first.statistics().sequenceWhitespace(),second.statistics().sequenceWhitespace()),
                    decoded,memory));
    }
    /**
     * Check an unchanged existing alignment inside a complete add result.
     * EXPAND admits inserted backbone-gap columns and unidentifiable removal of
     * original all-gap columns. It never invents identities for those columns.
     */
    public static BackboneAudit auditBackbone(Data backbone,Data aligned,Map<String,String> outputToOriginal,Backbone policy){
        Objects.requireNonNull(backbone);Objects.requireNonNull(aligned);Objects.requireNonNull(outputToOriginal);Objects.requireNonNull(policy);
        outputToOriginal=new LinkedHashMap<>(outputToOriginal);
        if(backbone.layout()!=Layout.ALIGNMENT||aligned.layout()!=Layout.ALIGNMENT||backbone.alphabet()!=aligned.alphabet()
                ||!outputToOriginal.keySet().equals(aligned.taxa())||outputToOriginal.values().stream().anyMatch(Objects::isNull)
                ||outputToOriginal.values().stream().distinct().count()!=outputToOriginal.size()
                ||!Set.copyOf(outputToOriginal.values()).containsAll(backbone.taxa()))
            throw new IllegalArgumentException("Require a complete aligned backbone and one-to-one output identities");
        var inverse=new LinkedHashMap<String,Row>();for(Row row:aligned.rows())inverse.put(outputToOriginal.get(row.taxon()),row);
        long originalGaps=0,outputGaps=0,matched=0;int originalColumn=0;
        for(int column=0;column<backbone.columns();column++)if(gapColumn(backbone.rows(),column))originalGaps++;
        if(policy==Backbone.KEEP_LENGTH&&backbone.columns()!=aligned.columns())
            throw new IllegalArgumentException("Source keeplength changed existing column count");
        for(int column=0;column<aligned.columns();column++){
            boolean allGap=true;for(Row row:backbone.rows())if(inverse.get(row.taxon()).sequence().charAt(column)!='-'){allGap=false;break;}
            if(allGap)outputGaps++;
            if(policy==Backbone.EXPAND&&allGap)continue;
            if(policy==Backbone.EXPAND)while(originalColumn<backbone.columns()&&gapColumn(backbone.rows(),originalColumn))originalColumn++;
            if(originalColumn>=backbone.columns())throw new IllegalArgumentException("Source inserted a nonempty backbone column");
            for(Row row:backbone.rows())if(upper(row.sequence().charAt(originalColumn))!=upper(inverse.get(row.taxon()).sequence().charAt(column)))
                throw new IllegalArgumentException("Source changed a backbone column/residue relation");
            originalColumn++;if(!allGap)matched++;
        }
        if(policy==Backbone.EXPAND)while(originalColumn<backbone.columns()&&gapColumn(backbone.rows(),originalColumn))originalColumn++;
        if(originalColumn!=backbone.columns())throw new IllegalArgumentException("Source omitted a nonempty backbone column");
        return new BackboneAudit(backbone.rows().size(),backbone.columns(),aligned.columns(),originalGaps,outputGaps,matched,policy==Backbone.KEEP_LENGTH);
    }
    private static boolean gapColumn(List<Row> rows,int column){
        for(Row row:rows)if(row.sequence().charAt(column)!='-')return false;return true;
    }
    public static Data readFasta(Path path,Alphabet alphabet,Symbols symbols,Headers headers,
                                 Layout layout,Gaps gaps,Limits limits)throws IOException {
        Objects.requireNonNull(path);Objects.requireNonNull(alphabet);Objects.requireNonNull(symbols);
        Objects.requireNonNull(headers);Objects.requireNonNull(layout);Objects.requireNonNull(gaps);Objects.requireNonNull(limits);
        var decoder=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        return readFasta(new InputStreamReader(Files.newInputStream(path),decoder),alphabet,symbols,headers,layout,gaps,limits,()->{});
    }
    public static Data readFasta(Reader source,Alphabet alphabet,Symbols symbols,Headers headers,
                                 Layout layout,Gaps gaps,Limits limits,Runnable cancellation)throws IOException {
        Objects.requireNonNull(source);Objects.requireNonNull(alphabet);Objects.requireNonNull(symbols);
        Objects.requireNonNull(headers);Objects.requireNonNull(layout);Objects.requireNonNull(gaps);
        Objects.requireNonNull(limits);Objects.requireNonNull(cancellation);
        var rows=new ArrayList<Row>();var seen=new java.util.HashSet<String>();
        long[] counts={0,0,0,0,0,0,0};String header=null,taxon=null;var sequence=new StringBuilder();
        try(var reader=new BufferedReader(source)){
            var line=new StringBuilder();boolean ended=false;
            while(!ended){
                cancellation.run();line.setLength(0);int value;
                while((value=reader.read())>=0){
                    if((counts[4]&4095)==0)cancellation.run();
                    if(++counts[4]>limits.decodedCharacters())throw new IOException("Complete FASTA decoded-character capacity exceeded");
                    if(value=='\n'||value=='\r')break;
                    if(line.length()>=limits.lineCharacters())throw new IOException("Complete FASTA line capacity exceeded");
                    if(line.length()+1>counts[6]){
                        counts[6]=line.length()+1;
                        retained(counts[0],seen.size(),counts[5],counts[6],limits);
                    }
                    line.append((char)value);
                }
                ended=value<0;String text=line.toString();
                if(text.startsWith(">")){
                    if(header!=null)add(rows,taxon,header,sequence,layout);
                    if(rows.size()>=limits.sequences())throw new IOException("Complete FASTA sequence capacity exceeded");
                    header=text.substring(1);validateHeader(header,headers,limits.headerCharacters());
                    counts[5]=Math.addExact(counts[5],header.length());
                    taxon=header;
                    if(headers==Headers.FIRST_FIELD){
                        int i=0;while(i<header.length()&&header.charAt(i)!=' '&&header.charAt(i)!='\t')i++;
                        taxon=header.substring(0,i);
                    }
                    if(taxon.isEmpty()||!seen.add(taxon))throw new IOException("Empty or duplicate exact FASTA taxon: "+taxon);
                    sequence.setLength(0);
                    retained(counts[0],rows.size()+1,counts[5],counts[6],limits);
                }else if(!text.isEmpty()){
                    if(header==null)throw new IOException("FASTA sequence precedes its first header");
                    for(int i=0;i<text.length();i++){
                        if((i&4095)==0)cancellation.run();
                        char c=text.charAt(i);
                        if(c==' '||c=='\t'){counts[3]++;continue;}
                        if(!allowed(c,alphabet,symbols)||gaps==Gaps.FORBID&&c=='-')
                            throw new IOException("Unadmitted literal FASTA symbol at taxon "+taxon+": "+c);
                        if(sequence.length()>=limits.sequenceCharacters()||++counts[0]>limits.cells())
                            throw new IOException("Complete FASTA sequence/cell capacity exceeded");
                        if(c=='-')counts[2]++;else counts[1]++;
                        retained(counts[0],rows.size()+1,counts[5],counts[6],limits);sequence.append(c);
                    }
                }
            }
            if(header!=null)add(rows,taxon,header,sequence,layout);
            if(rows.isEmpty())throw new IOException("FASTA has no complete sequence rows");
            cancellation.run();
        }
        long memory=retained(counts[0],rows.size(),counts[5],counts[6],limits);
        return new Data(alphabet,symbols,layout,rows,new Statistics(counts[0],counts[1],counts[2],counts[3],counts[4],memory));
    }
    private static void add(List<Row> rows,String taxon,String header,StringBuilder sequence,Layout layout)throws IOException {
        if(sequence.isEmpty())throw new IOException("Empty FASTA sequence for "+taxon);
        if(layout==Layout.ALIGNMENT&&!rows.isEmpty()&&rows.get(0).sequence().length()!=sequence.length())
            throw new IOException("Unequal complete FASTA alignment row lengths");
        rows.add(new Row(taxon,header,sequence.toString()));
    }
    private static long retained(long cells,int rows,long headers,long longestLine,Limits limits)throws IOException {
        long estimate=Math.addExact(65536,Math.addExact(Math.multiplyExact(8,cells),
                Math.addExact(Math.multiplyExact(512L,rows),Math.multiplyExact(8,Math.addExact(headers,longestLine)))));
        if(estimate>limits.estimatedRetainedBytes())throw new IOException("Estimated retained FASTA capacity exceeded; increase explicit capacities and Java heap, never truncate input");
        return estimate;
    }
    private static boolean allowed(char raw,Alphabet alphabet,Symbols symbols){
        if(raw=='-')return true;
        if(symbols==Symbols.ANY_PRINTABLE)return raw>=33&&raw<=126&&raw!='>';
        char c=upper(raw);
        return (alphabet==Alphabet.NUCLEOTIDE?"ACGTURYSWKMBDHVNX":"ACDEFGHIKLMNPQRSTVWYBJZX").indexOf(c)>=0;
    }
    private static void validateHeader(String header,Headers mode,int maximum)throws IOException {
        if(header==null||header.isEmpty()||header.length()>maximum
                ||header.codePoints().allMatch(c->Character.isWhitespace(c)||Character.isSpaceChar(c)))
            throw new IOException("Require a nonblank bounded FASTA header");
        for(int i=0;i<header.length();i++){
            char c=header.charAt(i);
            if(Character.isHighSurrogate(c)){
                if(i+1>=header.length()||!Character.isLowSurrogate(header.charAt(++i)))throw new IOException("Unpaired FASTA header surrogate");
            }else if(Character.isLowSurrogate(c)||Character.isISOControl(c)&&!(mode==Headers.FIRST_FIELD&&c=='\t'))
                throw new IOException("Unadmitted FASTA header control/surrogate");
        }
    }
    private static char upper(char c){return c>='a'&&c<='z'?(char)(c-'a'+'A'):c;}
    /** Validate complete identities/order independently of a residue-deletion policy. */
    public static Map<String,Integer> checkIdentities(Data original,Data aligned,Map<String,String> outputToOriginal,OutputOrder order){
        Objects.requireNonNull(original);Objects.requireNonNull(aligned);Objects.requireNonNull(outputToOriginal);Objects.requireNonNull(order);
        outputToOriginal=new LinkedHashMap<>(outputToOriginal);
        if(original.alphabet()!=aligned.alphabet()||aligned.layout()!=Layout.ALIGNMENT
                ||outputToOriginal.size()!=original.rows().size()||outputToOriginal.values().stream().anyMatch(Objects::isNull)
                ||!aligned.taxa().equals(outputToOriginal.keySet())
                ||outputToOriginal.values().stream().distinct().count()!=original.rows().size()
                ||!Set.copyOf(outputToOriginal.values()).equals(original.taxa()))
            throw new IllegalArgumentException("Require the same alphabet and exact complete one-to-one aligned taxon set");
        var mapping=new LinkedHashMap<String,Integer>();int ordinal=0;
        for(Row row:aligned.rows()){
            String id=outputToOriginal.get(row.taxon());
            if(order==OutputOrder.INPUT&&!id.equals(original.rows().get(ordinal).taxon()))
                throw new IllegalArgumentException("Source output violates explicit input-row order");
            mapping.put(id,ordinal++);
        }
        return Collections.unmodifiableMap(mapping);
    }
    /**
     * Check every original residue against a complete output alignment.
     * Only ASCII case may change; U/T and all other symbols remain distinct.
     * This contract does not admit keeplength's deleted inserted residues.
     */
    public static AlignmentAudit audit(Data original,Data aligned,Map<String,String> outputToOriginal,OutputOrder order){
        Objects.requireNonNull(outputToOriginal);outputToOriginal=new LinkedHashMap<>(outputToOriginal);
        var mapping=checkIdentities(original,aligned,outputToOriginal,order);long changed=0;
        for(Row result:aligned.rows()){
            String id=outputToOriginal.get(result.taxon());Row input=original.byTaxon().get(id);
            int position=0;
            for(int j=0;j<result.sequence().length();j++){
                char actual=result.sequence().charAt(j);if(actual=='-')continue;
                while(position<input.sequence().length()&&input.sequence().charAt(position)=='-')position++;
                if(position>=input.sequence().length()||upper(input.sequence().charAt(position))!=upper(actual))
                    throw new IllegalArgumentException("Source alignment changed or inserted a literal residue for "+id);
                if(input.sequence().charAt(position)!=actual)changed++;position++;
            }
            while(position<input.sequence().length()&&input.sequence().charAt(position)=='-')position++;
            if(position!=input.sequence().length())throw new IllegalArgumentException("Source alignment omitted a literal residue for "+id);
        }
        long allGap=0;
        for(int column=0;column<aligned.columns();column++){
            boolean gap=true;for(Row row:aligned.rows())if(row.sequence().charAt(column)!='-'){gap=false;break;}
            if(gap)allGap++;
        }
        return new AlignmentAudit(original.rows().size(),aligned.columns(),original.statistics().cells(),
                original.statistics().residues(),aligned.statistics().cells(),aligned.statistics().gapCells(),allGap,changed,mapping);
    }
}
