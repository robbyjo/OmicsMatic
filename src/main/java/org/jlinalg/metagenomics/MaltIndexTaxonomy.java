/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.metagenomics;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Explicit native accession mapping for a NEW, derived taxonomy-only MALT index.
 * The original unclassified index and upstream programs are never modified.
 * This is an alternative indexing method, not MALT0.5.2's --acc2taxa path.
 */
public final class MaltIndexTaxonomy {
    private MaltIndexTaxonomy() { }
    public enum Format { TWO_COLUMN, NCBI_ACCESSION2TAXID }
    public enum Namespace { LITERAL, ACCESSION, ACCESSION_VERSION }
    public record Limits(long fileBytes,int references,long referenceBases,long mappingRows,
                         int lineCharacters,MaltTaxonomy.Limits taxonomy) {
        public Limits {if(fileBytes<1||references<1||referenceBases<1||mappingRows<1||lineCharacters<1)throw new IllegalArgumentException("Positive complete-input capacities required");Objects.requireNonNull(taxonomy);}
    }
    public record Pin(long bytes,String sha256) { }
    private record Reference(String header,String key,int bases,String sequenceSha256) { }
    private record Plan(Map<String,Pin> source,Pin mapping,List<Reference> references,
                        Map<String,Integer> taxa,long mappingRows,long repeatedSelectedRows) { }
    public record Report(long references,long referenceBases,long mappingRows,long repeatedSelectedRows,
                         Map<String,Integer> taxonByAccession,Map<String,Pin> sourceIndex,Pin mapping,
                         Map<String,Pin> derivedIndex) {
        public Report {taxonByAccession=Collections.unmodifiableMap(new LinkedHashMap<>(taxonByAccession));sourceIndex=Map.copyOf(sourceIndex);derivedIndex=Map.copyOf(derivedIndex);}
        public Map<String,Object> document() {
            var out=new LinkedHashMap<String,Object>();out.put("references",references);out.put("reference_bases",referenceBases);out.put("mapping_rows",mappingRows);out.put("repeated_selected_mapping_rows",repeatedSelectedRows);out.put("taxon_by_accession",taxonByAccession);out.put("source_index",documents(sourceIndex));out.put("mapping",Map.of("bytes",mapping.bytes(),"sha256",mapping.sha256()));out.put("derived_index",documents(derivedIndex));return out;
        }
    }
    private static Map<String,Object> documents(Map<String,Pin> values) {var out=new TreeMap<String,Object>();values.forEach((n,p)->out.put(n,Map.of("bytes",p.bytes(),"sha256",p.sha256())));return out;}
    /** Validate a complete contiguous taxonomy-only index inventory, never a sampled subset. */
    public static Map<String,Pin> inventory(Path directory,long maximum)throws IOException {
        if(!Files.isDirectory(directory,LinkOption.NOFOLLOW_LINKS)||!directory.toAbsolutePath().normalize().equals(directory.toRealPath()))throw invalid("Require a canonical, unlinked index directory");
        var result=new TreeMap<String,Pin>();var tables=new TreeSet<Integer>();
        try(var files=Files.list(directory)) {
            for(Path p:files.toList()) {String n=p.getFileName().toString();if(result.size()>=10000)throw invalid("Complete index member capacity exceeded");
                if(n.matches("index[0-9]+\\.idx")){try{tables.add(Integer.valueOf(n.substring(5,n.length()-4)));}catch(NumberFormatException e){throw invalid("Unassessable seed-table ordinal");}}
                result.put(n,pin(p,maximum));
            }
        }
        var expected=new HashSet<String>(Set.of("ref.db","ref.idx","ref.inf","taxonomy.idx","taxonomy.tre","taxonomy.map"));
        if(tables.isEmpty())throw invalid("No complete seed table");
        for(int i=0;i<tables.size();i++){if(!tables.contains(i))throw invalid("Noncontiguous seed tables");expected.add("index"+i+".idx");expected.add("table"+i+".idx");expected.add("table"+i+".db");}
        if(!result.keySet().equals(expected))throw invalid("Require every taxonomy-only index member and no extra classification or file");return Collections.unmodifiableMap(result);
    }
    public static Pin pin(Path file,long maximum)throws IOException {
        if(maximum<1||!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||Files.size(file)>maximum)throw invalid("Missing, linked, nonregular or over-capacity complete file: "+file);
        var digest=digest();long bytes=0;byte[] buffer=new byte[65536];
        try(var input=Files.newInputStream(file)){for(int n;(n=input.read(buffer))!=-1;){bytes=Math.addExact(bytes,n);if(bytes>maximum)throw invalid("Complete byte capacity exceeded: "+file);digest.update(buffer,0,n);}}
        return new Pin(bytes,HexFormat.of().formatHex(digest.digest()));
    }
    public static Report derive(Path source,Path mapping,Format format,Namespace namespace,Path destination,Limits limits)throws IOException {
        Path target=destination.toAbsolutePath().normalize(),original=source.toRealPath();
        if(target.startsWith(original)||original.startsWith(target)||!target.getParent().equals(target.getParent().toRealPath()))throw invalid("Source and NEW derived index paths must not overlap; require a canonical output parent");
        Plan plan=plan(source,mapping,format,namespace,limits);Files.createDirectory(destination);
        for(String name:plan.source().keySet())if(!Set.of("ref.db","ref.idx","taxonomy.idx").contains(name))Files.copy(source.resolve(name),destination.resolve(name));
        try(var input=new DataInputStream(new BufferedInputStream(Files.newInputStream(source.resolve("ref.db"))));
            var db=new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(destination.resolve("ref.db"),StandardOpenOption.CREATE_NEW)));
            var offsets=new DataOutputStream(Files.newOutputStream(destination.resolve("ref.idx"),StandardOpenOption.CREATE_NEW));
            var taxa=new DataOutputStream(Files.newOutputStream(destination.resolve("taxonomy.idx"),StandardOpenOption.CREATE_NEW))) {
            taxa.write("MATaxonomyV1.1".getBytes(StandardCharsets.US_ASCII));taxa.writeInt(plan.references().size());long offset=0;byte[] buffer=new byte[65536];
            for(var reference:plan.references()) {
                int length=input.readInt();if(length!=reference.bases())throw invalid("Original reference changed before derivation");offsets.writeLong(offset);db.writeInt(length);
                for(int left=length;left>0;){int n=input.read(buffer,0,Math.min(left,buffer.length));if(n<1)throw invalid("Truncated complete original reference");db.write(buffer,0,n);left-=n;}
                int h=input.readInt();if(h<2||h>limits.lineCharacters())throw invalid("Original header capacity changed");byte[] header=input.readNBytes(h);if(header.length!=h||!decode(header).equals(reference.header()))throw invalid("Original header changed during derivation");
                int taxon=plan.taxa().get(reference.key());byte[] tagged=tag(reference,taxon).getBytes(StandardCharsets.UTF_8);if(tagged.length>limits.lineCharacters())throw invalid("Derived complete header exceeds capacity");db.writeInt(tagged.length);db.write(tagged);taxa.writeInt(taxon);offset=Math.addExact(offset,8L+length+tagged.length);
            }
            if(input.read()!=-1)throw invalid("Unexpected trailing original reference bytes");
        }
        unchanged(source,mapping,plan,limits);return audit(source,mapping,format,namespace,destination,limits);
    }
    /** Recompute the mapping and check every sequence, header, ordinal and taxonomy id. */
    public static Report audit(Path source,Path mapping,Format format,Namespace namespace,Path derived,Limits limits)throws IOException {
        Plan plan=plan(source,mapping,format,namespace,limits);var output=inventory(derived,limits.fileBytes());
        for(String n:plan.source().keySet())if(!Set.of("ref.db","ref.idx","taxonomy.idx").contains(n)&&!plan.source().get(n).equals(output.get(n)))throw invalid("Unchanged index member differs: "+n);
        var references=references(derived,limits,false);int[] ids=taxonomyIds(derived.resolve("taxonomy.idx"),references.size());long bases=0;
        if(references.size()!=plan.references().size())throw invalid("Derived complete reference count differs");
        for(int i=0;i<references.size();i++) {var original=plan.references().get(i);var actual=references.get(i);int taxon=plan.taxa().get(original.key());
            if(ids[i]!=taxon||!actual.header().equals(tag(original,taxon))||actual.bases()!=original.bases()||!actual.sequenceSha256().equals(original.sequenceSha256()))throw invalid("Derived reference sequence/header/taxonomy differs at ordinal "+i+" ("+original.key()+")");bases=Math.addExact(bases,actual.bases());
        }
        unchanged(source,mapping,plan,limits);if(!output.equals(inventory(derived,limits.fileBytes())))throw invalid("Derived index changed during complete audit");
        return new Report(references.size(),bases,plan.mappingRows(),plan.repeatedSelectedRows(),plan.taxa(),plan.source(),plan.mapping(),output);
    }
    private static Plan plan(Path source,Path mapping,Format format,Namespace namespace,Limits limits)throws IOException {
        Objects.requireNonNull(format);Objects.requireNonNull(namespace);Objects.requireNonNull(limits);
        if(format==Format.TWO_COLUMN&&namespace!=Namespace.LITERAL||format==Format.NCBI_ACCESSION2TAXID&&namespace==Namespace.LITERAL)throw invalid("Declare literal keys for TWO_COLUMN, or ACCESSION/ACCESSION_VERSION for NCBI_ACCESSION2TAXID; no namespace is guessed");
        var files=inventory(source,limits.fileBytes());Pin mapPin=pin(mapping,limits.fileBytes());var refs=references(source,limits,true);int[] previous=taxonomyIds(source.resolve("taxonomy.idx"),refs.size());for(int id:previous)if(id!=0)throw invalid("Native mapping requires a completely unclassified source index; existing assignments are never overwritten");
        var wanted=new LinkedHashMap<String,Integer>();for(var ref:refs)wanted.put(ref.key(),null);long rows=0,repeated=0;
        try(var reader=reader(mapping)) {
            String line=boundedLine(reader,limits.lineCharacters());
            if(format==Format.NCBI_ACCESSION2TAXID){if(!"accession\taccession.version\ttaxid\tgi".equals(line))throw invalid("Require the literal four-column NCBI accession2taxid header");line=boundedLine(reader,limits.lineCharacters());}
            for(;line!=null;line=boundedLine(reader,limits.lineCharacters())) {
                if(++rows>limits.mappingRows())throw invalid("Complete mapping row capacity exceeded");String[] fields=line.split("\t",-1);if(fields.length!=(format==Format.TWO_COLUMN?2:4))throw invalid("Wrong mapping column count at data row "+rows);
                String key=fields[format==Format.TWO_COLUMN||namespace==Namespace.ACCESSION?0:1];if(key.isEmpty()||key.chars().anyMatch(Character::isWhitespace))throw invalid("Empty/nonliteral accession at mapping row "+rows);
                if(format==Format.NCBI_ACCESSION2TAXID&&(fields[0].isEmpty()||fields[1].isEmpty()||fields[0].chars().anyMatch(Character::isWhitespace)||fields[1].chars().anyMatch(Character::isWhitespace)||!fields[3].matches("[0-9]+")))throw invalid("Malformed complete NCBI mapping row "+rows);
                int taxon=positive(fields[format==Format.TWO_COLUMN?1:2]);
                if(wanted.containsKey(key)){Integer old=wanted.put(key,taxon);if(old!=null){if(old!=taxon)throw invalid("Conflicting taxonomy for selected accession "+key);repeated++;}}
            }
        }
        var missing=new ArrayList<String>();for(var entry:wanted.entrySet())if(entry.getValue()==null&&missing.size()<10)missing.add(entry.getKey());if(!missing.isEmpty())throw invalid("Complete reference mapping is missing accessions (first ten): "+missing+"; no suffix is stripped or reference omitted");
        MaltTaxonomy tree;try(var input=Files.newBufferedReader(source.resolve("taxonomy.tre"),StandardCharsets.UTF_8)){tree=MaltTaxonomy.read(input,limits.taxonomy());}
        for(var entry:wanted.entrySet())if(!tree.contains(entry.getValue()))throw invalid("Mapped taxon is absent from the complete index taxonomy: "+entry);
        var result=new Plan(files,mapPin,List.copyOf(refs),Collections.unmodifiableMap(wanted),rows,repeated);unchanged(source,mapping,result,limits);return result;
    }
    private static void unchanged(Path source,Path mapping,Plan plan,Limits limits)throws IOException {if(!plan.source().equals(inventory(source,limits.fileBytes()))||!plan.mapping().equals(pin(mapping,limits.fileBytes())))throw invalid("Original complete index or mapping changed");}
    private static List<Reference> references(Path directory,Limits limits,boolean untagged)throws IOException {
        long bytes=Files.size(directory.resolve("ref.idx"));if(bytes%8!=0||bytes/8<1||bytes/8>limits.references())throw invalid("Invalid/over-capacity complete reference index");int count=Math.toIntExact(bytes/8);var refs=new ArrayList<Reference>(count);var keys=new HashSet<String>();long offset=0,bases=0;byte[] buffer=new byte[65536];
        try(var input=new DataInputStream(new BufferedInputStream(Files.newInputStream(directory.resolve("ref.db"))));var offsets=new DataInputStream(Files.newInputStream(directory.resolve("ref.idx")))) {
            for(int ordinal=0;ordinal<count;ordinal++) {
                if(offsets.readLong()!=offset)throw invalid("Reference offsets do not cover the complete database");int length=input.readInt();if(length<1||(bases=Math.addExact(bases,length))>limits.referenceBases())throw invalid("Invalid/over-capacity complete reference bases");var digest=digest();
                for(int left=length;left>0;){int n=input.read(buffer,0,Math.min(left,buffer.length));if(n<1)throw invalid("Truncated original reference");for(int i=0;i<n;i++)if("ACGTRYSWKMBDHVNUacgtryswkmbdhvnu".indexOf(buffer[i])<0)throw invalid("Non-IUPAC reference byte");digest.update(buffer,0,n);left-=n;}
                int h=input.readInt();if(h<2||h>limits.lineCharacters())throw invalid("Invalid/over-capacity reference header");byte[] raw=input.readNBytes(h);if(raw.length!=h)throw invalid("Truncated reference header");String header=decode(raw);if(!header.startsWith(">")||header.indexOf('\n')>=0||header.indexOf('\r')>=0||header.indexOf('\0')>=0)throw invalid("Malformed reference header");String key=header.substring(1).split("\\s+",2)[0];
                if(key.isEmpty()||!keys.add(key)||untagged&&header.contains("|tax|"))throw invalid("Empty, repeated or already taxonomy-tagged source accession: "+key);refs.add(new Reference(header,key,length,HexFormat.of().formatHex(digest.digest())));offset=Math.addExact(offset,8L+length+h);
            }
            if(input.read()!=-1||offsets.read()!=-1)throw invalid("Trailing complete reference data");
        }
        var info=pin(directory.resolve("ref.inf"),Math.min(limits.fileBytes(),1048576));if(info.bytes()==0)throw invalid("Missing complete reference counts");var counts=new HashMap<String,Long>();
        for(String line:Files.readString(directory.resolve("ref.inf"),StandardCharsets.UTF_8).lines().toList()){String[] f=line.split("\t",-1);if(f.length!=2||!Set.of("sequences","letters").contains(f[0])||!f[1].matches("[0-9]+"))throw invalid("Malformed reference-info count");try{if(counts.putIfAbsent(f[0],Long.valueOf(f[1]))!=null)throw invalid("Duplicate reference-info count");}catch(NumberFormatException e){throw invalid("Unassessable reference-info count");}}
        if(!counts.equals(Map.of("sequences",(long)count,"letters",bases)))throw invalid("Complete reference-info counts differ");return List.copyOf(refs);
    }
    private static int[] taxonomyIds(Path path,int references)throws IOException {
        if(Files.size(path)!=18L+4L*references)throw invalid("Complete taxonomy-index length differs");int[] result=new int[references];
        try(var input=new DataInputStream(Files.newInputStream(path))){if(!Arrays.equals(input.readNBytes(14),"MATaxonomyV1.1".getBytes(StandardCharsets.US_ASCII))||input.readInt()!=references)throw invalid("Taxonomy-index version/count differs");for(int i=0;i<references;i++)result[i]=input.readInt();if(input.read()!=-1)throw invalid("Trailing taxonomy-index content");}return result;
    }
    private static String tag(Reference reference,int taxon){int cut=1+reference.key().length();return reference.header().substring(0,cut)+"|tax|"+taxon+reference.header().substring(cut);}
    private static String decode(byte[] bytes)throws IOException{return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();}
    private static BufferedReader reader(Path path)throws IOException {InputStream input=Files.newInputStream(path);try{return new BufferedReader(new InputStreamReader(path.toString().endsWith(".gz")?new GZIPInputStream(input):input,StandardCharsets.UTF_8.newDecoder()));}catch(IOException e){input.close();throw e;}}
    private static String boundedLine(Reader input,int maximum)throws IOException {var line=new StringBuilder();for(int c;(c=input.read())!=-1;){if(c=='\n'){if(!line.isEmpty()&&line.charAt(line.length()-1)=='\r')line.setLength(line.length()-1);return line.toString();}if(line.length()==maximum)throw invalid("Complete mapping line exceeds capacity");line.append((char)c);}return line.isEmpty()?null:line.toString();}
    private static int positive(String text)throws IOException {if(!text.matches("[0-9]+"))throw invalid("Positive decimal taxonomy id required");try{int n=Integer.parseInt(text);if(n<1)throw invalid("Positive taxonomy id required");return n;}catch(NumberFormatException e){throw invalid("Taxonomy id exceeds integer range");}}
    private static MessageDigest digest(){try{return MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException e){throw new AssertionError(e);}}
    private static IOException invalid(String cause){return new IOException("MALT NATIVE INDEX FAIL2: "+cause+". Required action: retain all source and derived evidence, correct the named complete-input/namespace/format problem, then retry a NEW directory. No accession is guessed, reference dropped or existing index repaired. This alternative method does not qualify the paper's original MALT0.5.2 accession-map path.");}
}
