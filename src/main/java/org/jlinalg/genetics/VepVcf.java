/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.io.*;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/** Complete literal VEP CSQ extraction and variant/allele/gene grouping; never predicts or picks transcripts. */
public final class VepVcf {
    private VepVcf() { }
    private static final Pattern FORMAT=Pattern.compile("(?:^|[ \"])Format: ([^\"<>]+)");
    private static final List<String> IMPACTS=List.of("HIGH","MODERATE","LOW","MODIFIER");
    public enum Policy { EXPLICIT_ALLELE_NUMBERS, SOURCE_CONSEQUENCE_SUMMARY }
    public record Limits(long maximumCharacters,long maximumVariants,long maximumAnnotations,int maximumLineCharacters) {
        public Limits {if(maximumCharacters<1||maximumVariants<1||maximumAnnotations<1||maximumLineCharacters<256)throw new IllegalArgumentException("Require positive complete-file capacities and line capacity >=256");}
    }
    public record Schema(List<String> vcfColumns,List<String> csqFields,List<String> vepHeaders) {
        public Schema {vcfColumns=List.copyOf(vcfColumns);csqFields=List.copyOf(csqFields);vepHeaders=List.copyOf(vepHeaders);}
    }
    public record Variant(long row,List<String> fields,List<Integer> annotationsPerAllele,List<String> maximumObservedImpactsPerAllele,List<Integer> missingImpactAnnotationsPerAllele,int annotations,int unassignedSummaryAnnotations,boolean nonCsqInfoOrderChanged) {
        public Variant {fields=List.copyOf(fields);annotationsPerAllele=List.copyOf(annotationsPerAllele);maximumObservedImpactsPerAllele=List.copyOf(maximumObservedImpactsPerAllele);missingImpactAnnotationsPerAllele=List.copyOf(missingImpactAnnotationsPerAllele);}
    }
    public record Annotation(long row,int index,int alleleNumber,String alternate,Map<String,String> fields,String rawCsq) {
        public Annotation {fields=Collections.unmodifiableMap(new LinkedHashMap<>(fields));}
    }
    public record Gene(long row,int alleleNumber,String alternate,String gene,int annotations,List<String> features,List<String> consequences,List<String> originalImpacts,String maximumOriginalImpact,int missingImpactAnnotations) {
        public Gene {features=List.copyOf(features);consequences=List.copyOf(consequences);originalImpacts=List.copyOf(originalImpacts);}
    }
    public record Summary(Schema schema,long variants,long annotations,long geneGroups,long unannotatedAlleles,long annotationsWithoutGene,long missingImpacts,long unassignedSummaryAnnotations,long nonCsqInfoOrderChanges,boolean originalInputAudited) { }
    public record InputVariant(long row,List<String> fields){public InputVariant{fields=List.copyOf(fields);}}
    public record InputSummary(long variants,long alternateAlleles,List<String> columns){public InputSummary{columns=List.copyOf(columns);}}
    @FunctionalInterface public interface Sink<T> {void accept(T value)throws IOException;}
    /** Inspect every literal unannotated input row without rewriting its representation or alleles. */
    public static InputSummary inspectOriginal(Path original,Limits limits,Sink<InputVariant> variants)throws IOException {
        long rows=0,alleles=0;try(var input=new Vcf(original,new Budget(limits),false)){for(String[] fields;(fields=input.next())!=null;){require(++rows<=limits.maximumVariants(),"Original VCF row capacity exceeded");String[] alt=fields[4].split(",",-1);for(String value:alt)require(!value.isEmpty(),"Empty original ALT");alleles=Math.addExact(alleles,alt.length);variants.accept(new InputVariant(rows,Arrays.asList(fields)));}return new InputSummary(rows,alleles,input.columns);}
    }
    public static Summary inspect(Path annotated,Limits limits,Sink<Schema> schema,Sink<Variant> variants,Sink<Annotation> annotations,Sink<Gene> genes)throws IOException {return inspect(annotated,limits,Policy.EXPLICIT_ALLELE_NUMBERS,schema,variants,annotations,genes);}
    public static Summary inspect(Path annotated,Limits limits,Policy policy,Sink<Schema> schema,Sink<Variant> variants,Sink<Annotation> annotations,Sink<Gene> genes)throws IOException {return process(null,annotated,limits,policy,schema,variants,annotations,genes);}
    /** Every input data row, sample/genotype token and non-CSQ INFO entry must survive; INFO order is recorded. */
    public static Summary audit(Path original,Path annotated,Limits limits,Sink<Schema> schema,Sink<Variant> variants,Sink<Annotation> annotations,Sink<Gene> genes)throws IOException {return audit(original,annotated,limits,Policy.EXPLICIT_ALLELE_NUMBERS,schema,variants,annotations,genes);}
    public static Summary audit(Path original,Path annotated,Limits limits,Policy policy,Sink<Schema> schema,Sink<Variant> variants,Sink<Annotation> annotations,Sink<Gene> genes)throws IOException {Objects.requireNonNull(original);return process(original,annotated,limits,policy,schema,variants,annotations,genes);}
    private static Summary process(Path original,Path annotated,Limits limits,Policy policy,Sink<Schema> schemaSink,Sink<Variant> variantSink,Sink<Annotation> annotationSink,Sink<Gene> geneSink)throws IOException {
        Objects.requireNonNull(annotated);Objects.requireNonNull(limits);Objects.requireNonNull(policy);Objects.requireNonNull(schemaSink);Objects.requireNonNull(variantSink);Objects.requireNonNull(annotationSink);Objects.requireNonNull(geneSink);Budget budget=new Budget(limits);long count=0,total=0,groups=0,unannotated=0,noGene=0,noImpact=0,unassigned=0,infoChanges=0;
        try(Vcf before=original==null?null:new Vcf(original,budget,false);Vcf after=new Vcf(annotated,budget,true)) {
            if(before!=null)require(before.columns.equals(after.columns),"Original VCF sample/FORMAT columns changed");var schema=new Schema(after.columns,after.csqFields,after.versions);schemaSink.accept(schema);Map<String,Integer> index=new LinkedHashMap<>();for(int i=0;i<after.csqFields.size();i++)index.put(after.csqFields.get(i),i);
            for(String[] row;(row=after.next())!=null;) {
                require(++count<=limits.maximumVariants(),"Variant capacity exceeded; no row reduction");boolean infoChanged=false;
                if(before!=null){String[] initial=before.next();require(initial!=null&&initial.length==row.length,"Original output added a row or changed width at "+count);for(int i=0;i<row.length;i++)if(i!=7)require(initial[i].equals(row[i]),"Original identity/quality/filter/sample/genotype changed at row "+count+", column "+(i+1));var a=info(initial[7]);var b=info(row[7]);require(!a.containsKey("CSQ"),"Original input already contains CSQ; supply an explicitly unannotated original without rewriting it here");b.remove("CSQ");require(a.equals(b),"Original non-CSQ INFO changed at row "+count);infoChanged=!new ArrayList<>(a.entrySet()).equals(new ArrayList<>(b.entrySet()));if(infoChanged)infoChanges++;}
                String[] alternatives=row[4].split(",",-1);require(alternatives.length<=100000,"ALT count capacity exceeded");for(String alt:alternatives)require(!alt.isEmpty(),"Empty ALT token");int[] perAllele=new int[alternatives.length],worst=new int[alternatives.length],missing=new int[alternatives.length];Arrays.fill(worst,4);var geneGroups=new LinkedHashMap<GroupKey,GeneBuilder>();var attributes=info(row[7]);String encoded=attributes.get("CSQ");int rowAnnotations=0,rowUnassigned=0;
                if(attributes.containsKey("CSQ")){require(encoded!=null&&!encoded.isEmpty()&&!encoded.equals("."),"Empty/flag CSQ is not a valid annotation");for(String raw:encoded.split(",",-1)){
                    require(++total<=limits.maximumAnnotations(),"Annotation capacity exceeded; no transcript reduction");require(++rowAnnotations>0,"Annotation count overflow");String[] values=raw.split("\\|",-1);require(values.length==after.csqFields.size(),"CSQ field width differs at row "+count);Map<String,String> fields=new LinkedHashMap<>();for(int i=0;i<values.length;i++)fields.put(after.csqFields.get(i),values[i]);String allele=fields.get("ALLELE_NUM");int number=0;if(allele.isEmpty()){require(policy==Policy.SOURCE_CONSEQUENCE_SUMMARY,"Require explicit source ALLELE_NUM; no ambiguous allele-string inference");unassigned++;rowUnassigned++;}else {require(allele.matches("[1-9][0-9]*"),"Invalid source ALLELE_NUM");try{number=Integer.parseInt(allele);}catch(NumberFormatException e){throw new IOException("ALLELE_NUM overflow",e);}require(number<=alternatives.length,"ALLELE_NUM outside original ALT population");require(!fields.get("Allele").isEmpty(),"Missing source allele");perAllele[number-1]++;}require(!fields.get("Consequence").isEmpty(),"Missing source consequence in CSQ");
                    String gene=fields.getOrDefault("Gene","");String impact=fields.getOrDefault("IMPACT","");if(impact.isEmpty()){noImpact++;if(number>0)missing[number-1]++;}else{int severity=IMPACTS.indexOf(impact);require(severity>=0,"Invalid original IMPACT token; no severity inferred");if(number>0)worst[number-1]=Math.min(worst[number-1],severity);}annotationSink.accept(new Annotation(count,rowAnnotations,number,number==0?"":alternatives[number-1],fields,raw));
                    if(gene.isEmpty())noGene++;else if(number>0){GroupKey key=new GroupKey(number,gene);geneGroups.computeIfAbsent(key,k->new GeneBuilder()).add(fields);}
                }}
                for(int n:perAllele)if(n==0)unannotated++;for(var entry:geneGroups.entrySet()){GroupKey key=entry.getKey();geneSink.accept(entry.getValue().finish(count,key.number,alternatives[key.number-1],key.gene));groups++;}
                List<String> maximum=new ArrayList<>();for(int severity:worst)maximum.add(severity==4?"":IMPACTS.get(severity));variantSink.accept(new Variant(count,Arrays.asList(row),Arrays.stream(perAllele).boxed().toList(),maximum,Arrays.stream(missing).boxed().toList(),rowAnnotations,rowUnassigned,infoChanged));
            }
            if(before!=null)require(before.next()==null,"Original output dropped input rows");return new Summary(schema,count,total,groups,unannotated,noGene,noImpact,unassigned,infoChanges,before!=null);
        }
    }
    private record GroupKey(int number,String gene) { }
    private static final class GeneBuilder {
        int count,missing;final Set<String> features=new LinkedHashSet<>(),terms=new LinkedHashSet<>(),impacts=new LinkedHashSet<>();
        void add(Map<String,String> fields)throws IOException {count++;String feature=fields.getOrDefault("Feature","");if(!feature.isEmpty())features.add(feature);for(String term:fields.get("Consequence").split("&",-1)){require(!term.isEmpty(),"Empty composed consequence token");terms.add(term);}String impact=fields.getOrDefault("IMPACT","");if(impact.isEmpty())missing++;else impacts.add(impact);}
        Gene finish(long row,int number,String alternate,String gene){String maximum="";for(String impact:List.of("HIGH","MODERATE","LOW","MODIFIER"))if(impacts.contains(impact)){maximum=impact;break;}return new Gene(row,number,alternate,gene,count,List.copyOf(features),List.copyOf(terms),List.copyOf(impacts),maximum,missing);}
    }
    private static Map<String,String> info(String raw)throws IOException {Map<String,String> fields=new LinkedHashMap<>();if(raw.equals("."))return fields;require(!raw.isEmpty(),"Blank INFO is not VCF missing value");for(String item:raw.split(";",-1)){int split=item.indexOf('=');String key=split<0?item:item.substring(0,split);require(!key.isEmpty(),"Empty INFO ID");String value=split<0?null:item.substring(split+1);require(!fields.containsKey(key),"Duplicate INFO ID: "+key);fields.put(key,value);}return fields;}
    private static final class Vcf implements AutoCloseable {
        final Lines lines;final List<String> columns,csqFields,versions;String pending;long row;boolean ended;
        Vcf(Path path,Budget budget,boolean annotated)throws IOException {
            lines=new Lines(path,budget);List<String> c=null,f=null;var v=new ArrayList<String>();Set<String> definitions=new HashSet<>();boolean format=false;
            try {
                for(String line;(line=lines.next())!=null;){
                    if(line.isEmpty())continue;
                    if(line.startsWith("##fileformat=VCFv4.")){require(!format,"Duplicate VCF format declaration");format=true;continue;}
                    if(line.startsWith("##VEP=")){v.add(line);continue;}
                    if(line.startsWith("##INFO=<ID=")){int end=line.indexOf(',',11);require(end>11,"Invalid INFO declaration");String id=line.substring(11,end);require(definitions.add(id),"Duplicate INFO declaration: "+id);if(id.equals("CSQ")){require(annotated,"Original input already declares CSQ; no implicit annotation overwrite");require(line.contains(",Number=.,")&&line.contains(",Type=String,"),"Require original VEP variable-length String CSQ declaration");var match=FORMAT.matcher(line);require(match.find(),"CSQ lacks explicit Format field declaration");f=List.of(match.group(1).split("\\|",-1));require(!f.isEmpty()&&f.size()<=4096&&new HashSet<>(f).size()==f.size()&&f.stream().noneMatch(String::isEmpty)&&f.containsAll(List.of("Allele","Consequence","ALLELE_NUM")),"Require unique CSQ fields and explicit Allele/Consequence/ALLELE_NUM");}continue;}
                    if(line.startsWith("##"))continue;
                    c=List.of(line.split("\t",-1));require(c.size()>=8&&c.subList(0,8).equals(List.of("#CHROM","POS","ID","REF","ALT","QUAL","FILTER","INFO")),"Missing exact VCF column header");require(format&&(c.size()==8||c.size()>=10&&c.get(8).equals("FORMAT")),"Invalid VCF format/sample columns");if(c.size()>8)require(new HashSet<>(c.subList(9,c.size())).size()==c.size()-9&&c.subList(9,c.size()).stream().noneMatch(String::isEmpty),"Duplicate/empty literal sample ID");break;
                }
                require(c!=null&&(!annotated||f!=null),"Missing VCF header or complete CSQ schema");columns=c;csqFields=f==null?List.of():f;versions=List.copyOf(v);
            }catch(IOException|RuntimeException e){lines.close();throw e;}
        }
        String[] next()throws IOException {if(ended)return null;for(String line;(line=lines.next())!=null;){if(line.isEmpty())continue;require(!line.startsWith("#"),"VCF header/comment inside data rows");String[] result=line.split("\t",-1);require(result.length==columns.size(),"VCF row/sample width differs");require(!result[0].isEmpty()&&!result[2].isEmpty()&&!result[3].isEmpty()&&!result[4].isEmpty(),"Blank VCF identity token");require(result[1].matches("[1-9][0-9]*"),"Require positive literal VCF POS");try{Long.parseLong(result[1]);}catch(NumberFormatException e){throw new IOException("VCF POS overflow",e);}row++;return result;}ended=true;return null;}
        @Override public void close()throws IOException{lines.close();}
    }
    private static final class Budget {final Limits limits;long characters;Budget(Limits limits){this.limits=limits;}void use(int n)throws IOException{require(n<=limits.maximumCharacters()-characters,"Complete decoded VCF character capacity exceeded");characters+=n;}}
    private static final class Lines implements AutoCloseable {
        final Reader input;final Budget budget;final char[] buffer=new char[8192];int at,end;boolean afterCR;
        Lines(Path path,Budget budget)throws IOException {require(Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS),"Require a regular captured VCF");this.budget=budget;InputStream stream=new BufferedInputStream(Files.newInputStream(path));try{stream.mark(2);int a=stream.read(),b=stream.read();stream.reset();if(a==31&&b==139)stream=new GZIPInputStream(stream,65536);input=new InputStreamReader(stream,StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT));}catch(IOException|RuntimeException e){stream.close();throw e;}}
        String next()throws IOException {var line=new StringBuilder();for(;;){if(at==end){end=input.read(buffer);at=0;if(end<0)return line.isEmpty()?null:line.toString();if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("VEP VCF processing interrupted; no complete result");budget.use(end);}char c=buffer[at++];if(afterCR){afterCR=false;if(c=='\n')continue;}if(c=='\r'){afterCR=true;return line.toString();}if(c=='\n')return line.toString();require(line.length()<budget.limits.maximumLineCharacters(),"VCF line capacity exceeded; no record truncation");line.append(c);}}
        @Override public void close()throws IOException{input.close();}
    }
    private static void require(boolean value,String message)throws IOException{if(!value)throw new IOException(message);}
}
