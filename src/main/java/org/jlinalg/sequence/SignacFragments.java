/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import htsjdk.samtools.util.SortingCollection;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.zip.GZIPInputStream;
import org.jlinalg.genetics.GenomicIntervals;

/** Native whole-fragment FeatureMatrix and CountFragments contracts; no Tn5 shifts or read-support weighting. */
public final class SignacFragments {
    private SignacFragments() { }
    static final long MAX_EXACT=9_007_199_254_740_991L;
    public record Limits(int features,int cells,int nonzeroValues,long records,long contributions,
                         long decodedBytes,int lineBytes,int sortRecords) {
        public Limits {if(features<1||features>2_000_000||cells<1||cells>10_000_000||nonzeroValues<0||nonzeroValues>100_000_000||records<1||contributions<1||decodedBytes<1||lineBytes<32||lineBytes>4094||sortRecords<1)throw new IllegalArgumentException("Invalid fragment admission bounds; source-compatible lines are at most4094 bytes");}
        public static Limits defaults(){return new Limits(2_000_000,2_000_000,20_000_000,1_000_000_000L,250_000_000L,250_000_000_000L,4094,100_000);}
    }
    /** One-based closed GRanges coordinates, deliberately distinct from fragment BED coordinates. */
    public record Region(String chromosome,int start,int end) {
        public Region {identity(chromosome);if(start<1||end<start||end>536_870_912)throw new IllegalArgumentException("Require a one-based closed region within the source TBI range");}
        public String name(){return chromosome+"-"+start+"-"+end;}
    }
    public record Cell(String cell,String barcode) {public Cell{identity(cell);identity(barcode);}}
    /** A null map discovers cells; an explicit map follows Signac object-cell -> file-barcode orientation. */
    public record Source(Path path,List<Cell> map) {
        public Source {
            path=Objects.requireNonNull(path).toAbsolutePath().normalize();
            if(map!=null){map=List.copyOf(map);if(map.isEmpty())throw new IllegalArgumentException("Use null for no mapping, not an empty explicit map");Set<String> cells=new HashSet<>(),barcodes=new HashSet<>();for(Cell c:map)if(!cells.add(c.cell())||!barcodes.add(c.barcode()))throw new IllegalArgumentException("Each source map must be one-to-one; ambiguous barcode aliases are not admitted");}
        }
    }
    public record Warning(String code,String message,String requiredAction) { }
    public record Scan(long records,long decodedBytes,boolean compressed,boolean prefixOnly,List<String> chromosomes) {public Scan{chromosomes=List.copyOf(chromosomes);}}
    public record Count(String barcode,long frequencyCount,long mononucleosomal,long nucleosomeFree,long readsCount) { }
    public record CountResult(List<Count> counts,List<Scan> scans,List<Warning> warnings) {public CountResult{counts=List.copyOf(counts);scans=List.copyOf(scans);warnings=List.copyOf(warnings);}}
    public record Matrix(List<String> features,List<String> cells,int[] pointers,int[] rows,long[] values,
                         List<Scan> scans,List<Warning> warnings) {
        public Matrix {features=List.copyOf(features);cells=List.copyOf(cells);pointers=pointers.clone();rows=rows.clone();values=values.clone();scans=List.copyOf(scans);warnings=List.copyOf(warnings);}
        @Override public int[] pointers(){return pointers.clone();}@Override public int[] rows(){return rows.clone();}@Override public long[] values(){return values.clone();}
        public SignacNormalization.Counts asCounts(){
            if(cells.isEmpty())throw new IllegalStateException("No cells overlap the requested regions; skip normalization/LSI rather than invent columns");
            double[] x=new double[values.length];for(int i=0;i<x.length;i++)x[i]=values[i];
            return new SignacNormalization.Counts(features,cells,pointers,rows,x);
        }
    }
    static final class Totals {long count,mono,free,reads;void add(Fragment f)throws IOException{count=exact(count,1);mono=exact(mono,f.end-f.start>147&&f.end-f.start<294?1:0);free=exact(free,f.end-f.start<147?1:0);reads=exact(reads,f.support);}Count result(String barcode){return new Count(barcode,count,mono,free,reads);}}
    record Fragment(String chromosome,int start,int end,String barcode,int support,long ordinal) { }
    @FunctionalInterface interface Consumer {void accept(Fragment fragment)throws IOException;}

    /** maxLines is per file, as in the original; zero means all. Whitelist duplicates retain their first occurrence. */
    public static CountResult countFragments(List<Path> paths,List<String> whitelist,long maxLines,Limits limits,BooleanSupplier cancelled)throws IOException {
        check(cancelled);if(paths.isEmpty()||maxLines<0)throw new IllegalArgumentException("Require fragment sources and a nonnegative per-file line limit");
        LinkedHashMap<String,Totals> result=new LinkedHashMap<>();Set<String> allowed=null;
        if(whitelist!=null){allowed=new LinkedHashSet<>();for(String cell:whitelist){identity(cell);allowed.add(cell);}if(allowed.size()>limits.cells())throw new IOException("Whitelist exceeds cell bound");for(String cell:allowed)result.put(cell,new Totals());}
        final Set<String> keep=allowed;List<Scan> scans=new ArrayList<>();List<Warning> warnings=new ArrayList<>();Set<Path> seen=new HashSet<>();
        for(Path path:paths){check(cancelled);if(!seen.add(path.toAbsolutePath().normalize()))warnings.add(new Warning("REPEATED_SOURCE","A source path was supplied more than once and is counted each time","Review the explicit file list; no automatic deduplication was applied."));
            Scan scan=scan(path,limits,false,maxLines,cancelled,f->{if(keep!=null&&!keep.contains(f.barcode))return;Totals counts=result.get(f.barcode);if(counts==null){if(result.size()>=limits.cells())throw new IOException("Discovered cells exceed the declared bound");counts=new Totals();result.put(f.barcode,counts);}counts.add(f);});scans.add(scan);
            if(scan.prefixOnly())warnings.add(new Warning("PREFIX_ONLY","Only the requested prefix was counted; unread tail/compression integrity is not certified","Use maxLines=0 for complete-file counting/validation. The limit includes non-whitelisted fragment records."));
        }
        List<Count> counts=new ArrayList<>();for(var entry:result.entrySet())counts.add(entry.getValue().result(entry.getKey()));return new CountResult(counts,scans,warnings);
    }

    private static final class Column {final String cell;final int temporary;int firstFeature=Integer.MAX_VALUE;long firstRecord=Long.MAX_VALUE;Column(String cell,int temporary){this.cell=cell;this.temporary=temporary;}void observed(int feature,long ordinal){if(feature<firstFeature||(feature==firstFeature&&ordinal<firstRecord)){firstFeature=feature;firstRecord=ordinal;}}}
    private record State(Source source,LinkedHashMap<String,Column> columns,Map<String,Column> lookup,boolean discover) { }
    private record Entry(int cell,int feature,int source,long count) { }
    /**
     * Counts every overlapping BED fragment line once per region. The fifth
     * field is ignored here. Feature/cell order, missing-chromosome behavior and
     * multi-file summation follow the original matrix function, including its
     * restoration of all feature rows when combining multiple used sources.
     * scratch must be a NEW directory; only owned sort spill files are removed.
     */
    public static Matrix featureMatrix(List<Source> sources,List<Region> regions,List<String> requestedCells,
                                       boolean keepAllFeatures,int processN,Path scratch,Limits limits,BooleanSupplier cancelled)throws IOException {
        check(cancelled);Objects.requireNonNull(limits);if(sources.isEmpty()||regions.isEmpty()||regions.size()>limits.features())throw new IllegalArgumentException("Require sources and a bounded nonempty region list");
        if(processN<1)throw new IllegalArgumentException("Require positive source process_n");
        Set<String> featureNames=new HashSet<>();List<GenomicIntervals.Interval> intervals=new ArrayList<>();
        for(int i=0;i<regions.size();i++){if((i&1023)==0)check(cancelled);Region r=regions.get(i);if(!featureNames.add(r.name()))throw new IllegalArgumentException("Duplicate region coordinates are ambiguous source row identities");intervals.add(new GenomicIntervals.Interval(r.chromosome,r.start-1L,r.end,Integer.toString(i),"."));}
        LinkedHashSet<String> requested=null;if(requestedCells!=null){requested=new LinkedHashSet<>();for(String cell:requestedCells){identity(cell);if(!requested.add(cell))throw new IllegalArgumentException("Requested matrix cells must be unique");}if(requested.isEmpty()||requested.size()>limits.cells())throw new IllegalArgumentException("Require a bounded nonempty requested cell universe");}
        LinkedHashMap<String,Integer> temporary=new LinkedHashMap<>();List<State> states=new ArrayList<>();
        for(Source source:sources){
            check(cancelled);var columns=new LinkedHashMap<String,Column>();var lookup=new HashMap<String,Column>();boolean discover=source.map==null&&requested==null;
            if(source.map!=null){for(Cell c:source.map)if(requested==null||requested.contains(c.cell)){Column column=new Column(c.cell,cellIndex(temporary,c.cell,limits));columns.put(c.cell,column);lookup.put(c.barcode,column);}if(requested!=null&&columns.isEmpty())continue;}
            else if(requested!=null)for(String c:requested){Column column=new Column(c,cellIndex(temporary,c,limits));columns.put(c,column);lookup.put(c,column);}
            states.add(new State(source,columns,lookup,discover));
        }
        if(states.isEmpty())throw new IOException("No Fragment mapping contains any requested cell; no source matrix is admitted");
        var index=new GenomicIntervals.Index(intervals);check(cancelled);Files.createDirectory(scratch);
        final int[] comparisons={0};Comparator<Entry> order=(a,b)->{if((++comparisons[0]&4095)==0)check(cancelled);int c=Integer.compare(a.cell,b.cell);if(c!=0)return c;c=Integer.compare(a.feature,b.feature);return c!=0?c:Integer.compare(a.source,b.source);};
        var contributions=SortingCollection.newInstance(Entry.class,new Codec(),order,limits.sortRecords(),scratch);SortingCollection<Entry> sorted=null;
        List<Scan> scans=new ArrayList<>();List<Warning> warnings=new ArrayList<>();long[] hitCount={0};boolean cleaned=false;List<BitSet> countingMasks=new ArrayList<>();
        try{
            for(int sourceIndex=0;sourceIndex<states.size();sourceIndex++){
                State state=states.get(sourceIndex);final int sid=sourceIndex;
                Scan scan=scan(state.source.path,limits,true,0,cancelled,f->{
                    Column column=state.lookup.get(f.barcode);if(column==null&&!state.discover)return;
                    List<GenomicIntervals.Interval> hits=index.overlaps(new GenomicIntervals.Interval(f.chromosome,f.start,f.end,"fragment","."),false);
                    if(hits.isEmpty())return;
                    if(column==null){column=new Column(f.barcode,cellIndex(temporary,f.barcode,limits));state.columns.put(f.barcode,column);state.lookup.put(f.barcode,column);}
                    for(var hit:hits){check(cancelled);int row=Integer.parseInt(hit.id());column.observed(row,f.ordinal);if(++hitCount[0]>limits.contributions())throw new IOException("Fragment/region contributions exceed the explicit sort/disk bound");contributions.add(new Entry(column.temporary,row,sid,1));}
                });scans.add(scan);
                long absent=regions.stream().filter(r->!scan.chromosomes.contains(r.chromosome)).count();
                if(absent==regions.size())throw new IOException("No matching chromosomes found in fragment file: "+state.source.path);
                if(absent>0&&!keepAllFeatures)warnings.add(new Warning("SOURCE_MISSING_CHROMOSOMES",absent+" local feature rows lie on chromosomes absent from "+state.source.path,"Single-source output removes these rows; multi-source merging restores all requested rows with zeros where needed, matching the source."));
                int local=regions.size()-(int)absent;BitSet mask=null;
                if(keepAllFeatures&&local>processN){
                    int chunks=(int)(((long)local+processN-1)/processN),first=local/chunks;mask=new BitSet(regions.size());int seenRows=0;
                    for(int row=0;row<regions.size()&&seenRows<first;row++)if(scan.chromosomes.contains(regions.get(row).chromosome)){mask.set(row);seenRows++;}
                    warnings.add(new Warning("UPSTREAM_KEEP_ALL_CHUNK_BEHAVIOR","keep_all_features with multiple source chunks restores all rows in each chunk; the source then selects the first matching row, retaining only the first chunk's counts for this file","This reproduces the observed source behavior. Review process_n; choose a justified single-chunk request (process_n at least the number of local features) for full counts instead of silently correcting historical output."));
                }
                countingMasks.add(mask);
            }
            List<String> cells=new ArrayList<>();Set<String> included=new HashSet<>();
            for(State state:states){List<Column> cols=new ArrayList<>(state.columns.values());if(state.discover)cols.sort(Comparator.comparingInt((Column c)->c.firstFeature).thenComparingLong(c->c.firstRecord));for(Column col:cols)if(included.add(col.cell))cells.add(col.cell);}
            if(requested!=null&&!included.containsAll(requested))warnings.add(new Warning("REQUESTED_CELLS_OUTSIDE_MAPPINGS","Some requested cells are absent from all explicit Fragment mappings and are omitted","Review the output cell inventory; no zero columns were invented outside the source mappings."));
            int[] remap=new int[temporary.size()];Arrays.fill(remap,-1);for(int i=0;i<cells.size();i++)remap[temporary.get(cells.get(i))]=i;
            List<String> features=new ArrayList<>();int[] rowmap=new int[regions.size()];Arrays.fill(rowmap,-1);
            for(int i=0;i<regions.size();i++)if(keepAllFeatures||states.size()>1||scans.get(0).chromosomes.contains(regions.get(i).chromosome)){rowmap[i]=features.size();features.add(regions.get(i).name());}
            sorted=SortingCollection.newInstance(Entry.class,new Codec(),order,limits.sortRecords(),scratch);contributions.doneAdding();int unique=0;
            try(var iterator=contributions.iterator()){
                Entry current=null;long sum=0;
                while(iterator.hasNext()){
                    check(cancelled);Entry e=iterator.next();
                    if(current!=null&&(e.cell!=current.cell||e.feature!=current.feature)){
                        if(sum>0){if(++unique>limits.nonzeroValues())throw new IOException("Sparse nonzero output exceeds its explicit bound");sorted.add(new Entry(remap[current.cell],rowmap[current.feature],-1,sum));}sum=0;
                    }
                    BitSet mask=countingMasks.get(e.source);if(mask==null||mask.get(e.feature))sum=exact(sum,e.count);current=e;
                }
                if(current!=null&&sum>0){if(++unique>limits.nonzeroValues())throw new IOException("Sparse nonzero output exceeds its explicit bound");sorted.add(new Entry(remap[current.cell],rowmap[current.feature],-1,sum));}
            }
            contributions.cleanup();cleaned=true;sorted.doneAdding();int[] pointers=new int[cells.size()+1],rows=new int[unique];long[] values=new long[unique];int at=0;
            try(var iterator=sorted.iterator()){while(iterator.hasNext()){check(cancelled);Entry e=iterator.next();if(e.cell<0||e.feature<0)throw new IOException("Internal cell/feature remapping lost a counted identity");pointers[e.cell+1]++;rows[at]=e.feature;values[at++]=e.count;}}
            if(at!=unique)throw new IOException("Incomplete sparse merge");for(int cell=0;cell<cells.size();cell++)pointers[cell+1]+=pointers[cell];
            if(cells.isEmpty())warnings.add(new Warning("NO_OVERLAPPING_CELLS","No cells overlap the requested regions; the source-compatible matrix has zero columns","Review regions and cell discovery. Skip normalization/LSI instead of inventing cells."));
            check(cancelled);return new Matrix(features,cells,pointers,rows,values,scans,warnings);
        }finally{if(!cleaned)contributions.cleanup();if(sorted!=null)sorted.cleanup();Files.delete(scratch);}
    }
    private static int cellIndex(Map<String,Integer> indices,String cell,Limits limits)throws IOException{Integer old=indices.get(cell);if(old!=null)return old;if(indices.size()>=limits.cells())throw new IOException("Matrix cell universe exceeds the declared bound");int next=indices.size();indices.put(cell,next);return next;}

    /** Complete scans validate gzip/BGZF CRCs; sampled prefix scans explicitly do not certify the unread tail. */
    static Scan scan(Path path,Limits limits,boolean sorted,long stopAfter,BooleanSupplier cancelled,Consumer consumer)throws IOException {
        check(cancelled);long records=0,bytes=0;boolean compressed;int columns=0;String contig=null;int previousStart=-1;LinkedHashSet<String> chromosomes=new LinkedHashSet<>();boolean data=false,prefix=false;
        try(var physical=new BufferedInputStream(Files.newInputStream(path),65536)){
            physical.mark(2);int a=physical.read(),b=physical.read();physical.reset();compressed=a==0x1f&&b==0x8b;
            try(InputStream decoded=compressed?new BufferedInputStream(new GZIPInputStream(physical,65536),65536):physical){
                while(true){
                    check(cancelled);if(stopAfter>0&&records>=stopAfter){prefix=true;break;}
                    ByteArrayOutputStream buffer=new ByteArrayOutputStream(128);int value;boolean any=false;
                    while((value=decoded.read())>=0){any=true;if(++bytes>limits.decodedBytes())throw new IOException("Decoded fragment bytes exceed the declared bound");if(value=='\n')break;if(buffer.size()>=limits.lineBytes())throw new IOException("Fragment/header line exceeds the source-compatible byte bound");buffer.write(value);}
                    if(!any)break;byte[] raw=buffer.toByteArray();int length=raw.length;if(length>0&&raw[length-1]=='\r')length--;
                    String line=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(raw,0,length)).toString();
                    if(line.startsWith("#")){if(data)throw new IOException("Fragment headers must precede all data records");continue;}
                    data=true;String[] fields=line.split("\t",-1);if(columns==0)columns=fields.length;if((columns!=5&&columns!=6)||fields.length!=columns)throw new IOException("Require consistently five or six tab-separated fragment fields");
                    identityIO(fields[0]);identityIO(fields[3]);int start=number(fields[1]),end=number(fields[2]),support=number(fields[4]);if(end<=start)throw new IOException("Require nonempty zero-based half-open fragment intervals");
                    if(columns==6&&!Set.of("+","-",".").contains(fields[5]))throw new IOException("Sixth fragment strand field must be +, - or .");
                    if(++records>limits.records())throw new IOException("Fragment record count exceeds its bound");
                    if(sorted){if(end>536_870_912)throw new IOException("FeatureMatrix source coordinates exceed the TBI domain");if(!fields[0].equals(contig)){if(!chromosomes.add(fields[0]))throw new IOException("Fragment chromosome appears in disjoint blocks");contig=fields[0];previousStart=-1;}if(start<previousStart)throw new IOException("FeatureMatrix requires coordinate-sorted source records");previousStart=start;}
                    else chromosomes.add(fields[0]);
                    consumer.accept(new Fragment(fields[0],start,end,fields[3],support,records));
                }
            }
        }
        if(!data)throw new IOException("Fragment source contains no data records");return new Scan(records,bytes,compressed,prefix,new ArrayList<>(chromosomes));
    }
    private static int number(String token)throws IOException{try{if(!token.matches("[0-9]+"))throw new NumberFormatException();long n=Long.parseLong(token);if(n>Integer.MAX_VALUE)throw new NumberFormatException();return(int)n;}catch(NumberFormatException error){throw new IOException("Fragment coordinates/read support require nonnegative decimal integers within the upstream atoi domain",error);}}
    static long exact(long a,long b)throws IOException{if(a<0||b<0||a>MAX_EXACT-b)throw new IOException("Fragment statistic exceeds exact R/Java integer domain 2^53-1");return a+b;}
    static void identity(String value){if(value==null||value.isBlank()||value.length()>4094||value.chars().anyMatch(c->c=='\t'||c=='\r'||c=='\n'||c==0))throw new IllegalArgumentException("Require a nonblank identity without tab/newline/NUL, at most4094 characters");}
    private static void identityIO(String value)throws IOException{try{identity(value);}catch(IllegalArgumentException e){throw new IOException(e.getMessage(),e);}}
    static void check(BooleanSupplier cancelled){if(Thread.currentThread().isInterrupted()||Objects.requireNonNull(cancelled).getAsBoolean())throw new CancellationException("Native Signac fragment operation cancelled; no external process was launched");}
    private static final class Codec implements SortingCollection.Codec<Entry>{
        private DataInputStream input;private DataOutputStream output;
        @Override public void setInputStream(InputStream in){input=new DataInputStream(in);}@Override public void setOutputStream(OutputStream out){output=new DataOutputStream(out);}
        @Override public void encode(Entry e){try{output.writeInt(e.cell);output.writeInt(e.feature);output.writeInt(e.source);output.writeLong(e.count);}catch(IOException error){throw new UncheckedIOException(error);}}
        @Override public Entry decode(){try{int first=input.read();if(first<0)return null;int cell=first<<24|input.readUnsignedByte()<<16|input.readUnsignedByte()<<8|input.readUnsignedByte();return new Entry(cell,input.readInt(),input.readInt(),input.readLong());}catch(IOException error){throw new UncheckedIOException(error);}}
        @Override public Codec clone(){return new Codec();}
    }
}
