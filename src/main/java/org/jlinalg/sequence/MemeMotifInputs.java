/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Complete bounded DNA motif inputs for original Tomtom 5.5.9. Raw probability
 * tokens, declared metadata and source defaults remain distinct from consumed
 * models. Files are never normalized, converted or rewritten. This component
 * does not admit an executable, supervised process, analysis or output bundle.
 */
public final class MemeMotifInputs {
    private MemeMotifInputs() { }
    private static final Pattern DECIMAL=Pattern.compile("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?");
    private static final Pattern INTRO=Pattern.compile("^\\s*(BL\\s+)?MOTIF\\s+(\\S+)(.*)$");
    private static final Pattern PARAM=Pattern.compile("\\s*([^\\s=]+)\\s*=\\s*([^\\s=]+)");
    private static final Set<String> MATRIX_KEYS=Set.of("alength","w","nsites","E","P");

    /** A known source behavior needs a separate implementation; do not substitute an input. */
    public static final class UnsupportedInput extends IOException {
        private static final long serialVersionUID=1L;
        /** @param message named unsupported behavior and required action */
        public UnsupportedInput(String message){super(message);}
    }

    /**
     * Explicit complete-file and combined-model capacities, not heap guarantees.
     * @param maximumBytes raw file byte capacity
     * @param maximumCharacters decoded UTF-16 capacity
     * @param maximumMotifs complete motif population capacity, including duplicates
     * @param maximumWidth per-model position capacity
     * @param maximumMatrixValues combined raw A/C/G/T scalar capacity
     * @param xml complete XML tree capacities
     */
    public record Limits(int maximumBytes,int maximumCharacters,int maximumMotifs,int maximumWidth,
                         long maximumMatrixValues,MemeXml.Limits xml) {
        /** Validates capacities before opening an input. */
        public Limits {
            Objects.requireNonNull(xml);
            if(maximumBytes<1||maximumBytes>268435456||maximumCharacters<1||maximumCharacters>268435456
                    ||maximumMotifs<1||maximumMotifs>2000000||maximumWidth<1||maximumWidth>512
                    ||maximumMatrixValues<4||maximumMatrixValues>100000000)
                throw new IllegalArgumentException("Invalid explicit complete MEME motif input capacities");
        }
    }

    /**
     * An original record in file order. Duplicate IDs remain separate records.
     * @param ordinal one-based original record index
     * @param model raw consumed model before source normalization/pseudocounts
     * @param metadata original metadata tokens, with named defaults/mappings
     * @param probabilityTokens unchanged decoded scalar tokens in position/base order
     * @param normalizedPositions one-based rows the source normalizes before pseudocounts
     */
    public record Entry(int ordinal,TomtomResults.Motif model,Map<String,String> metadata,
                        List<List<String>> probabilityTokens,List<Integer> normalizedPositions) {
        /** Copies every collection; the model already owns a defensive raw matrix. */
        public Entry {
            Objects.requireNonNull(model);metadata=Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
            probabilityTokens=probabilityTokens.stream().map(List::copyOf).toList();normalizedPositions=List.copyOf(normalizedPositions);
        }
        /** @return source pseudocount site count; zero metadata defaults to twenty */
        public int pseudocountSites(){return model.sites()>0?(int)model.sites():20;}
    }

    /**
     * Complete raw file identity and original model population; no fit admission.
     * @param format recognized input format
     * @param bytes complete original raw bytes
     * @param sha256 complete original SHA-256
     * @param entries all original records, in file order
     * @param background independent background consumed by original motif reader, before Tomtom epsilon passes
     * @param backgroundRule explicit declared/default/source-specific selection rule
     * @param warnings named source boundaries and required actions
     */
    public record Document(String format,int bytes,String sha256,List<Entry> entries,double[] background,
                           String backgroundRule,List<String> warnings) {
        /** Makes defensive copies. */
        public Document {entries=List.copyOf(entries);background=background.clone();warnings=List.copyOf(warnings);}
        /** @return defensive raw consumed background copy */
        @Override public double[] background(){return background.clone();}
    }

    /**
     * Reads minimal or learned MEME text and original MEME/STREME 5.5.9 XML.
     * HTML needs a separately tested embedded-data reader and produces a named
     * unsupported-input exception. XML producer paths and URLs remain strings.
     * No database duplicate, ALLR zero or query-width exclusions are applied.
     * @param path complete captured original regular UTF-8 file
     * @param limits explicit positive complete-population capacities
     * @param cancellation caller cancellation check
     * @return complete input component receipt, with original defaults exposed
     * @throws IOException on changed, malformed, unsupported or excessive input
     */
    public static Document read(Path path,Limits limits,Runnable cancellation)throws IOException {
        Objects.requireNonNull(path);Objects.requireNonNull(limits);Objects.requireNonNull(cancellation);cancellation.run();
        BasicFileAttributes before=Files.readAttributes(path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        byte[] raw=raw(path,limits.maximumBytes());
        if(!before.isRegularFile()||before.size()!=raw.length)throw new IOException("Require bounded complete regular MEME motif input");
        String identity=sha(raw),text;
        try{text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(raw)).toString();}
        catch(java.nio.charset.CharacterCodingException e){throw new IOException("MEME motif input must be complete UTF-8",e);}
        if(text.length()>limits.maximumCharacters()||text.indexOf('\0')>=0)throw new IOException("Invalid MEME input decoded capacity or NUL");
        cancellation.run();String start=text.stripLeading();Document document;
        if(start.regionMatches(true,0,"<!DOCTYPE HTML",0,14)||start.regionMatches(true,0,"<html",0,5))
            throw new UnsupportedInput("MEME/STREME HTML embedded motif data is not yet implemented. Preserve the original HTML; explicitly supply an independently retained text/XML export or skip the dependent comparison. No automatic conversion was performed.");
        if(start.startsWith("<"))document=xml(path,raw.length,identity,limits,cancellation);
        else document=text(text,raw.length,identity,limits,cancellation);
        cancellation.run();BasicFileAttributes after=Files.readAttributes(path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(!after.isRegularFile()||after.size()!=before.size()||!after.lastModifiedTime().equals(before.lastModifiedTime())
                ||!Objects.equals(after.fileKey(),before.fileKey())||!sha(raw(path,limits.maximumBytes())).equals(identity))
            throw new IOException("MEME motif input changed during complete parsing");
        return document;
    }

    private static Document text(String text,int bytes,String identity,Limits limits,Runnable check)throws IOException {
        String[] lines=text.split("\\R",-1);boolean version=false,alphabet=false,backgroundSeen=false;
        double[] background={.25,.25,.25,.25};var entries=new ArrayList<Entry>();Builder current=null;long scalars=0;
        for(int i=0;i<lines.length;i++) {
            check.run();String line=lines[i].strip();
            if(line.startsWith("MEME version ")){if(version)throw new IOException("Repeated MEME text version header");version=true;continue;}
            if(line.startsWith("ALPHABET")){
                if(alphabet||!line.matches("ALPHABET\\s*=\\s*ACGT"))throw new UnsupportedInput("Require one original A/C/G/T DNA text alphabet; custom alphabets need a separate reader");alphabet=true;continue;
            }
            if(line.startsWith("Background letter frequencies")){
                if(backgroundSeen||current!=null)throw new IOException("Repeated or late MEME text background");backgroundSeen=true;
                int next=i+1;while(next<lines.length&&lines[next].isBlank())next++;
                if(next==lines.length)throw new IOException("Missing MEME text background values");
                String[] fields=lines[next].strip().split("\\s+");if(fields.length!=8)throw new UnsupportedInput("Require a complete four-base MEME text background row");
                for(int b=0;b<4;b++){if(!fields[b*2].equals("ACGT".substring(b,b+1)))throw new IOException("MEME text background alphabet/order differs");background[b]=decimal(fields[b*2+1]).doubleValue();}
                validateRawRow(background);i=next;continue;
            }
            var intro=INTRO.matcher(line);
            if(intro.matches()){
                if(!version||!alphabet)throw new IOException("Motif precedes complete MEME text header/alphabet");
                boolean block=intro.group(1)!=null;String id=intro.group(2);
                if(current!=null&&current.id.equals(id)&&current.block!=block&&!current.repeatedBlock){current.repeatedBlock=true;continue;}
                if(current!=null)entries.add(finish(current,entries.size()+1));
                if(entries.size()>=limits.maximumMotifs())throw new IOException("Complete MEME input motif capacity exceeded");
                current=new Builder(id,alternate(intro.group(3)),block);continue;
            }
            if(line.startsWith("letter-probability matrix:")){
                if(current==null||current.rows!=null)throw new IOException("Missing or repeated MEME probability motif section");
                Map<String,String> fields=parameters(line.substring(line.indexOf(':')+1));
                if(!MATRIX_KEYS.containsAll(fields.keySet()))throw new UnsupportedInput("Unknown MEME probability metadata needs a separate source mapping");
                if(!fields.getOrDefault("alength","").equals("4"))throw new IOException("Require declared four-base MEME probability alphabet");
                int width=width(fields.get("w"),limits);scalars=capacity(scalars,width,limits);
                current.metadata.putAll(fields);current.rows=new ArrayList<>();
                for(int row=0;row<width;row++){
                    check.run();if(++i>=lines.length)throw new IOException("Incomplete terminal MEME probability matrix");
                    String[] values=lines[i].strip().split("\\s+");if(values.length!=4)throw new IOException("Require every complete four-base MEME probability row");
                    current.rows.add(List.of(values));
                }
                if(i+1<lines.length){String[] extra=lines[i+1].strip().split("\\s+");if(extra.length==4&&java.util.Arrays.stream(extra).allMatch(v->DECIMAL.matcher(v).matches()))throw new IOException("Extra original probability row beyond the declared complete motif width");}
                continue;
            }
            if(line.startsWith("log-odds matrix:")){
                if(current==null||current.scoresSeen)throw new IOException("Missing or repeated MEME score motif section");current.scoresSeen=true;
                Map<String,String> fields=parameters(line.substring(line.indexOf(':')+1));int width=width(fields.get("w"),limits);
                if(!fields.getOrDefault("alength","").equals("4"))throw new IOException("Require four-base MEME score alphabet");
                if(fields.containsKey("E"))current.metadata.put("scores.E",fields.get("E"));current.metadata.put("scores.w",Integer.toString(width));
                for(int row=0;row<width;row++){
                    check.run();if(++i>=lines.length)throw new IOException("Incomplete MEME score matrix");String[] values=lines[i].strip().split("\\s+");
                    if(values.length!=4)throw new IOException("Incomplete MEME score matrix row");for(String value:values)decimal(value);
                }
                continue;
            }
            if(line.startsWith("URL")){
                if(current==null||current.urlSeen||!line.matches("URL(?:\\s+\\S*)?"))throw new IOException("Invalid or repeated original motif URL");
                current.urlSeen=true;current.url=line.length()>3?line.substring(3).strip():"";
            }
        }
        if(current!=null)entries.add(finish(current,entries.size()+1));
        if(!version||!alphabet||entries.isEmpty())throw new IOException("Require a complete nonempty MEME text motif population");
        return document("MEME-text",bytes,identity,entries,background,backgroundSeen?"declared-text-background":"missing-text-background-default-uniform",List.of());
    }

    private static Document xml(Path path,int bytes,String identity,Limits limits,Runnable check)throws IOException {
        var parsed=MemeXml.read(path,limits.xml(),check);if(!parsed.sha256().equals(identity)||parsed.bytes()!=bytes)throw new IOException("MEME XML changed between raw/tree capture");
        var root=parsed.root();boolean streme=root.name().equals("STREME");
        if(!streme&&!root.name().equals("MEME"))throw new UnsupportedInput("Require original learned MEME or STREME XML, not a Tomtom result or another XML format");
        if(!root.attribute("version").equals("5.5.9"))throw new UnsupportedInput("Learned XML preprocessing is tested for MEME/STREME 5.5.9; other editions need separate source controls");
        var model=root.one("model");var alphabet=streme?model.one("alphabet"):root.one("training_set").one("alphabet");dna(alphabet);
        var bg=model.one("background_frequencies");
        if(!bg.attribute("order").equals("0"))throw new UnsupportedInput("Higher-order learned XML backgrounds require a separately tested source projection");
        double[] declared=alphabetValues(bg.one("alphabet_array"));validateRawRow(declared);
        if(!streme&&Math.abs(java.util.Arrays.stream(declared).sum()-1)>0.01)throw new IOException("MEME XML background exceeds the original format-specific 0.01 mass tolerance");
        double[] background=streme?new double[]{.25,.25,.25,.25}:declared;
        var entries=new ArrayList<Entry>();long scalars=0;
        for(var node:root.one("motifs").children()){
            check.run();if(!node.name().equals("motif")||entries.size()>=limits.maximumMotifs())throw new IOException("Invalid complete learned XML motif population/capacity");
            int width=width(node.attribute("width"),limits);scalars=capacity(scalars,width,limits);
            String id=node.attribute(streme?"id":"name"),alt=node.attribute("alt");
            if(!streme&&alt.getBytes(StandardCharsets.UTF_8).length>8)throw new UnsupportedInput("Original x86_64 MEME XML reader copies at most eight alternate-ID bytes. Preserve the raw ID and skip until that source truncation has a tested disposition ledger.");
            Builder builder=new Builder(id,alt,false);builder.rows=new ArrayList<>();builder.metadata.putAll(node.attributes());
            builder.metadata.put("nsites",streme?Long.toString(Math.addExact(count(node,"train_pos_count"),count(node,"test_pos_count"))):node.attribute("sites"));
            if(streme){
                boolean test=Math.addExact(count(node,"test_pos_count"),count(node,"test_neg_count"))>0;
                String log=node.attribute(test?"test_log_pvalue":"train_log_pvalue");builder.metadata.put("source.log10-E",log);
                builder.metadata.put("E",power10(log).toString());builder.metadata.put("E.rule",test?"source-test-P-from-log10-not-E":"source-train-P-from-log10-not-E");
                if(node.children().size()!=width)throw new IOException("Incomplete or extra STREME XML probability positions");
                for(var row:node.children()){
                    if(!row.name().equals("pos")||!row.attributes().keySet().equals(Set.of("A","C","G","T"))||!row.children().isEmpty()||!row.text().isBlank())throw new IOException("Invalid STREME XML probability row");
                    builder.rows.add(List.of(row.attribute("A"),row.attribute("C"),row.attribute("G"),row.attribute("T")));
                }
            }else{
                builder.metadata.put("E",node.attribute("e_value"));builder.url=node.attributes().getOrDefault("url","");
                var matrix=node.one("probabilities").one("alphabet_matrix");if(matrix.children().size()!=width)throw new IOException("Incomplete MEME XML probability positions");
                for(var row:matrix.children())builder.rows.add(alphabetTokens(row));
            }
            entries.add(finish(builder,entries.size()+1));
        }
        if(entries.isEmpty())throw new IOException("Require a nonempty complete learned XML motif population");
        List<String> warnings=streme?List.of("STREME_XML_5_5_9_SOURCE_MAPPING: original Tomtom's reader consumes train_pos_count + test_pos_count as sites and the held-out log10 P (training log10 P without held-out counts) as its E metadata. It does not consume test_evalue or total_sites. Its new-schema background callback leaves the reader background unset, selecting uniform DNA instead of the declared XML background. Raw attributes/background remain in the unchanged input; text/XML exports therefore need not produce identical comparisons."):List.of();
        return document(streme?"STREME-XML-5.5.9":"MEME-XML-5.5.9",bytes,identity,entries,background,streme?"source-STREME-5.5.9-new-schema-unset-background-default-uniform":"declared-MEME-XML-background",warnings);
    }

    private static Document document(String format,int bytes,String sha,List<Entry> entries,double[] background,String rule,List<String> extra){
        var warnings=new ArrayList<>(extra);warnings.add("INPUT_COMPONENT_ONLY: no runtime, process, complete output family or user analysis has been admitted. Preserve raw files and verify independent execution/output gates.");
        if(entries.stream().anyMatch(e->e.model().sites()==0))warnings.add("ZERO_OR_MISSING_SITES: original metadata remains zero; pseudocounts use twenty, while source PSSM conversion uses a separate hundred-site fallback. No nsites XML attribute is printed for zero metadata.");
        if(entries.stream().anyMatch(e->!e.normalizedPositions().isEmpty()))warnings.add("SOURCE_ROW_NORMALIZATION: rows outside inclusive 1 +/- 1e-5 normalize in memory before pseudocounts; closer rounded rows retain their mass. Raw files and tokens remain unchanged.");
        if(entries.stream().map(e->e.model().id()).distinct().count()!=entries.size())warnings.add("DUPLICATE_IDS: all original records remain. Original filtering/order and exclusions require a separate complete disposition ledger; do not construct an unfiltered analysis database from this receipt.");
        return new Document(format,bytes,sha,entries,background,rule,warnings);
    }
    private static Entry finish(Builder builder,int ordinal)throws IOException {
        if(builder.alt.getBytes(StandardCharsets.UTF_8).length>200)throw new UnsupportedInput("Original alternate ID exceeds the source 200-byte copy boundary. Preserve the full identifier and skip until truncation has a tested disposition ledger.");
        if(builder.rows==null)throw new UnsupportedInput("Every original motif must have a complete probability matrix; score-only conversion needs a separately tested implementation");
        if(builder.metadata.containsKey("scores.w")&&Integer.parseInt(builder.metadata.get("scores.w"))!=builder.rows.size())throw new IOException("Original score/probability widths differ");
        String site=builder.metadata.getOrDefault("nsites","0"),ev=builder.metadata.get("E");
        if(ev==null)ev=builder.metadata.get("P");if(ev==null)ev=builder.metadata.getOrDefault("scores.E","1");
        if(builder.metadata.containsKey("scores.E")&&decimal(builder.metadata.get("scores.E")).compareTo(decimal(ev))!=0)throw new IOException("Original score/probability E metadata differs");
        var metadata=new LinkedHashMap<>(builder.metadata);metadata.put("sites.rule",builder.metadata.containsKey("nsites")?"declared":"missing-default-zero");
        metadata.putIfAbsent("E.rule",builder.metadata.containsKey("E")?"declared-E":builder.metadata.containsKey("P")?"declared-P-as-E":builder.metadata.containsKey("scores.E")?"declared-score-E":"missing-default-log10-zero-E-one");
        double[][] matrix=new double[builder.rows.size()][4];var normalized=new ArrayList<Integer>();
        for(int i=0;i<matrix.length;i++){
            if(builder.rows.get(i).size()!=4)throw new IOException("Incomplete original DNA probability position");double total=0;
            for(int b=0;b<4;b++){matrix[i][b]=decimal(builder.rows.get(i).get(b)).doubleValue();total+=matrix[i][b];}
            validateRawRow(matrix[i]);if(total<1.0-0.00001||total>1.0+0.00001)normalized.add(i+1);
        }
        try{return new Entry(ordinal,new TomtomResults.Motif(builder.id,builder.alt,decimal(site).doubleValue(),matrix,decimal(ev),builder.url),metadata,builder.rows,normalized);}
        catch(IllegalArgumentException e){throw new UnsupportedInput("Original motif metadata/matrix exceeds the implemented safe source contract: "+e.getMessage()+". Preserve inputs and skip dependent execution until the named source behavior is supported.");}
    }
    private static String alternate(String remainder){
        String rest=remainder.strip();if(rest.isEmpty())return "";String[] fields=rest.split("\\s+",2);
        return fields[0].contains("=")||fields.length==2&&fields[1].stripLeading().startsWith("=")?"":fields[0];
    }
    private static Map<String,String> parameters(String text)throws IOException {
        var values=new LinkedHashMap<String,String>();var matcher=PARAM.matcher(text);int end=0;
        while(matcher.find()){if(!text.substring(end,matcher.start()).isBlank()||values.putIfAbsent(matcher.group(1),matcher.group(2))!=null)throw new IOException("Invalid or repeated MEME matrix metadata");end=matcher.end();}
        if(!text.substring(end).isBlank())throw new IOException("Unparsed terminal MEME matrix metadata");return values;
    }
    private static int width(String value,Limits limits)throws IOException {try{int width=Integer.parseInt(value);if(width<1||width>limits.maximumWidth())throw new IOException("Complete MEME motif width exceeds declared capacity");return width;}catch(NumberFormatException e){throw new IOException("Require declared bounded original motif width",e);}}
    private static long capacity(long prior,int width,Limits limits)throws IOException {long next=Math.addExact(prior,width*4L);if(next>limits.maximumMatrixValues())throw new IOException("Combined complete MEME matrix scalar capacity exceeded");return next;}
    private static BigDecimal decimal(String token)throws IOException {
        if(token==null||token.length()>4096||!DECIMAL.matcher(token).matches())throw new IOException("Invalid bounded original decimal token");
        try{BigDecimal value=new BigDecimal(token);if(Math.abs((long)value.scale())>4096)throw new IOException("Original decimal exponent exceeds bounded domain");return value;}
        catch(NumberFormatException e){throw new IOException("Invalid original decimal",e);}
    }
    private static BigDecimal power10(String token)throws IOException {
        double log=decimal(token).doubleValue();if(!Double.isFinite(log)||log>4095||log< -4095)throw new UnsupportedInput("STREME log10 metadata exceeds implemented finite decimal domain");
        int exponent=(int)Math.floor(log);return BigDecimal.valueOf(Math.pow(10,log-exponent)).scaleByPowerOfTen(exponent);
    }
    private static long count(MemeXml.Node node,String key)throws IOException {try{long count=Long.parseLong(node.attribute(key));if(count<0||count>Integer.MAX_VALUE)throw new IOException("Invalid bounded STREME count");return count;}catch(NumberFormatException e){throw new IOException("Invalid STREME integer count",e);}}
    private static void validateRawRow(double[] values)throws IOException {double total=0;for(double value:values){if(!Double.isFinite(value)||value<0||value>1)throw new IOException("Invalid original DNA probability");total+=value;}if(Math.abs(total-1)>0.1)throw new IOException("Raw original DNA row mass exceeds source text tolerance");}
    private static List<String> alphabetTokens(MemeXml.Node row)throws IOException {
        if(!row.name().equals("alphabet_array")||row.children().size()!=4)throw new IOException("Require complete original DNA alphabet array");var values=new ArrayList<String>();
        for(int b=0;b<4;b++){var node=row.children().get(b);if(!node.name().equals("value")||!node.attribute("letter_id").equals("ACGT".substring(b,b+1))||node.attributes().size()!=1||!node.children().isEmpty())throw new IOException("Original DNA alphabet array order/content differs");values.add(node.text().strip());}return values;
    }
    private static double[] alphabetValues(MemeXml.Node row)throws IOException {var tokens=alphabetTokens(row);double[] values=new double[4];for(int b=0;b<4;b++)values[b]=decimal(tokens.get(b)).doubleValue();return values;}
    private static void dna(MemeXml.Node alphabet)throws IOException {
        var core=alphabet.named("letter").stream().filter(n->!n.attributes().containsKey("equals")).toList();
        if(core.size()!=4)throw new UnsupportedInput("Require original four-base DNA XML alphabet");
        for(int b=0;b<4;b++)if(!core.get(b).attribute("id").equals("ACGT".substring(b,b+1))||!core.get(b).attribute("symbol").equals("ACGT".substring(b,b+1)))throw new UnsupportedInput("Original XML core DNA alphabet/order differs");
    }
    private static byte[] raw(Path path,int maximum)throws IOException {
        var attributes=Files.readAttributes(path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);if(!attributes.isRegularFile()||attributes.size()>maximum)throw new IOException("Require bounded regular MEME motif input");
        try(var input=Files.newInputStream(path)){byte[] raw=input.readNBytes(Math.addExact(maximum,1));if(raw.length>maximum)throw new IOException("Complete raw MEME input byte capacity exceeded");return raw;}
    }
    private static String sha(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(NoSuchAlgorithmException e){throw new AssertionError(e);}}
    private static final class Builder {
        final String id,alt;final boolean block;boolean repeatedBlock,scoresSeen,urlSeen;String url="";List<List<String>> rows;final Map<String,String> metadata=new LinkedHashMap<>();
        Builder(String id,String alt,boolean block){this.id=id;this.alt=alt;this.block=block;}
    }
}
