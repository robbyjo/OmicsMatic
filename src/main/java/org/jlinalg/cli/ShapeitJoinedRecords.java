/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.genetics.ShapeitCommonVariants;

/** Binds a completed original XCF join capture to untouched, fully decoded raw records. */
final class ShapeitJoinedRecords {
    private ShapeitJoinedRecords() { }
    enum Role { TARGET, REFERENCE, SCAFFOLD }
    record Limits(long protocolBytes,int lineBytes,int intervals,int samples,long resourceBytes) {
        Limits {if(protocolBytes<1||lineBytes<1||intervals<1||samples<1||resourceBytes<1)throw new IllegalArgumentException("Require positive complete original-join capacities");}
        static Limits defaults(){return new Limits(1073741824L,1048576,100000,1000000,1099511627776L);}
    }
    record Resource(String kind,Path localPath,String peerPath,ShapeitRawIndex.Identity identity) {
        Resource {
            if(!Set.of("B","I","D","M").contains(kind)||peerPath.isEmpty()||peerPath.indexOf(0)>=0)throw new IllegalArgumentException("Invalid original-join resource binding");
            localPath=localPath.toAbsolutePath().normalize();Objects.requireNonNull(identity);
        }
        static Resource bind(String kind,Path local,String peer,long maximum,Runnable check) throws IOException {return new Resource(kind,local,peer,ShapeitRawIndex.identity(local,maximum,check));}
    }
    static final class Panel {
        private final Role role;private final ShapeitRawIndex.Store store;private final List<Resource> resources;
        private final ShapeitCommonVariants.FileKind kind;private final List<String> samples;
        private Panel(Role role,ShapeitRawIndex.Store store,List<Resource> resources,ShapeitCommonVariants.FileKind kind,List<String> samples){this.role=role;this.store=store;this.resources=List.copyOf(resources);this.kind=kind;this.samples=List.copyOf(samples);}
        static Panel bind(Role role,ShapeitRawIndex.Store store,List<Resource> supplied,Limits limits,Runnable check) throws IOException {
            Objects.requireNonNull(role);Objects.requireNonNull(store);var byKind=new HashMap<String,Resource>();
            for(Resource r:supplied){if(byKind.put(r.kind(),r)!=null)throw new IOException("Repeated original-join resource kind");if(!r.identity().equals(ShapeitRawIndex.identity(r.localPath(),limits.resourceBytes(),check)))throw new IOException("Original-join resource changed while binding");}
            Resource bcf=byKind.get("B");if(bcf==null||!bcf.localPath().equals(store.source())||!bcf.identity().equals(store.sourceIdentity()))throw new IOException("Original-join BCF resource does not bind the completed raw source");
            boolean binary=store.header().infoTags().contains("SEEK");
            var kind=binary?ShapeitCommonVariants.FileKind.BINARY:store.header().samples().isEmpty()?ShapeitCommonVariants.FileKind.VOID:ShapeitCommonVariants.FileKind.BCF;
            if(binary&&!store.header().samples().isEmpty())throw new IOException("BINARY_WITH_HEADER_SAMPLES: original XCF does not admit this header combination; dependent analysis SKIPPED");
            if(byKind.containsKey("I")&&!byKind.get("I").peerPath().equals(bcf.peerPath()+".csi"))throw new IOException("CSI peer path differs from the exact original reader resource path");
            List<String> samples=store.header().samples();
            if(binary){
                String name=bcf.peerPath();int dot=name.lastIndexOf('.');String stem=dot<0?name:name.substring(0,dot); // Preserve the original helper's unconditional final-extension removal.
                if(!byKind.containsKey("D")||!byKind.containsKey("M")||!byKind.get("D").peerPath().equals(stem+".bin")||!byKind.get("M").peerPath().equals(stem+".fam"))throw new IOException("XCF binary/sample resources are missing or differ from the original derived paths; dependent analysis SKIPPED");
                samples=famSamples(byKind.get("M").localPath(),limits,check);
            }else if(byKind.containsKey("D")||byKind.containsKey("M"))throw new IOException("Ordinary BCF cannot claim unreferenced XCF companion resources");
            if(samples.size()>limits.samples())throw new IOException("Complete original sample population exceeds capacity; raise the limit without dropping samples");
            var resources=new ArrayList<Resource>();for(String k:List.of("B","I","D","M"))if(byKind.containsKey(k))resources.add(byKind.get(k));
            var panel=new Panel(role,store,resources,kind,samples);panel.verifyCompanions(limits,check);return panel;
        }
        private void verifyCompanions(Limits limits,Runnable check) throws IOException {for(Resource r:resources)if(!r.kind().equals("B")&&!r.identity().equals(ShapeitRawIndex.identity(r.localPath(),limits.resourceBytes(),check)))throw new IOException("Original-join companion changed; dependent analysis SKIPPED; preserve inputs and rebuild/re-run in fresh directories");}
        private ShapeitCommonVariants.Header header(){return new ShapeitCommonVariants.Header(samples.size(),kind);}
    }
    record Headers(ShapeitCommonVariants.Header target,ShapeitCommonVariants.Header reference,ShapeitCommonVariants.Header scaffold) { }
    record Event(long ordinal,List<ShapeitBcf.Row> rawRows,ShapeitCommonVariants.Event scannerEvent) {
        Event {rawRows=Collections.unmodifiableList(new ArrayList<>(rawRows));}
    }
    @FunctionalInterface interface Visitor {
        default void header(Headers headers) throws IOException { }
        void event(Event event) throws IOException;
    }
    record Report(long events,List<Long> physicalVisits,ShapeitRawIndex.Identity protocolIdentity,boolean complete) {
        Report {physicalVisits=List.copyOf(physicalVisits);}
    }
    private record Span(long start,long end) { }
    private record Regions(boolean any,boolean exclude,Map<String,List<Span>> spans) {
        boolean matches(String contig,long start,long end){
            boolean included=any;
            var list=spans.get(contig);
            if(!included&&list!=null){int low=0,high=list.size();while(low<high){int mid=(low+high)>>>1;if(list.get(mid).end()<start)low=mid+1;else high=mid;}included=low<list.size()&&list.get(low).start()<=end;}
            return exclude?!included:included;
        }
    }

    /** Callback data are provisional until the whole stream, resources and independent raw census close. */
    static Report link(Path protocol,String expectedProtocolSha256,String requestedRegion,List<Panel> supplied,Path destination,Limits limits,Visitor visitor,Runnable check) throws IOException {
        Objects.requireNonNull(visitor);Objects.requireNonNull(check);Objects.requireNonNull(requestedRegion);
        List<Panel> panels=List.copyOf(supplied);validateRoles(panels);
        Path workspace=destination.toAbsolutePath().normalize(),parent=workspace.getParent();
        if(parent==null||!parent.toRealPath().equals(parent))throw new IOException("Require an existing canonical original-join workspace parent");Files.createDirectory(workspace);
        var cursors=new ArrayList<ShapeitRawIndex.Cursor>();
        try {
            var original=ShapeitRawIndex.identity(protocol,limits.protocolBytes(),check);
            if(!original.sha256().equals(expectedProtocolSha256))throw new IOException("Original join capture differs from the explicitly bound capture digest");
            for(Panel p:panels)p.verifyCompanions(limits,check);
            Report completed;
            try(var lines=new Lines(protocol,limits.lineBytes(),limits.protocolBytes(),check)){
                String[] version=lines.next("V",3);if(!version[1].equals("OMICS-SHAPEIT-JOIN-WIRE-2")||number(version[2])!=panels.size())throw new IOException("Require the bound original join-wire v2 file population");
                String[] query=lines.next("U",2);if(!decoded(query[1]).equals(requestedRegion))throw new IOException("Original join query differs from the requested region");
                Regions regions=regions(lines,"R",limits),targets=regions(lines,"T",limits);
                ShapeitRawIndex.Filter filter=(c,p,l)->regions.matches(c,p-1L,p+(long)l-2)&&targets.matches(c,p-1L,p-1L);
                for(int i=0;i<panels.size();i++){
                    for(Resource resource:panels.get(i).resources)resource(lines,"B",i,resource);
                    cursors.add(new ShapeitRawIndex.Cursor(panels.get(i).store,workspace.resolve("occurrences-"+i+".bits"),filter));
                }
                for(int i=0;i<panels.size();i++){
                    Panel panel=panels.get(i);String[] n=lines.next("N",5);
                    int kind=switch(panel.kind){case VOID->0;case BCF->1;case BINARY->2;};
                    if(number(n[1])!=i||number(n[2])!=kind||number(n[3])!=panel.store.header().samples().size()||number(n[4])!=panel.samples.size())throw new IOException("Original join header differs from the complete raw/FAM header");
                    for(int s=0;s<panel.samples.size();s++){String[] row=lines.next("S",4);if(number(row[1])!=i||number(row[2])!=s||!decoded(row[3]).equals(panel.samples.get(s)))throw new IOException("Original join sample identity/order differs from its bound raw/FAM source");}
                }
                lines.next("H",1);visitor.header(headers(panels));long events=0;
                while(Objects.equals(lines.peekType(),"J")){
                    String[] j=lines.next("J",4+5*panels.size());if(number(j[1])!=events)throw new IOException("Original join event order is incomplete");
                    String contig=decoded(j[2]);int position=integer(j[3]);if(contig.isEmpty()||position<1)throw new IOException("Invalid original physical event coordinate");
                    var rawRows=new ArrayList<ShapeitBcf.Row>();var byRole=new EnumMap<Role,ShapeitCommonVariants.PanelRow>(Role.class);boolean physical=false;
                    for(int i=0;i<panels.size();i++){
                        int offset=4+5*i;String hash=j[offset];boolean present=bit(j[offset+1]);int type=signedInteger(j[offset+2]);long ac=uint32(j[offset+3]),an=uint32(j[offset+4]);
                        if(hash.equals("-")){if(present||type!=0||ac!=0||an!=0)throw new IOException("Absent physical join row has nonzero source metadata");rawRows.add(null);continue;}
                        if(!hash.matches("[a-f0-9]{64}"))throw new IOException("Malformed original raw wire identity");
                        ShapeitBcf.Row row=cursors.get(i).take(contig,position,hash);physical=true;rawRows.add(row);
                        boolean biallelic=row.alternates().size()==1;
                        if(present!=biallelic)throw new IOException("Original biallelic flag differs from untouched raw alleles");
                        ShapeitCommonVariants.CountValue countAc=null,countAn=null;int expectedType=0;
                        if(biallelic){
                            countAc=count(row,"AC");countAn=count(row,"AN");
                            if(countAc.returned()!=1||countAn.returned()!=1||Integer.toUnsignedLong(countAc.first())!=ac||Integer.toUnsignedLong(countAn.first())!=an)throw new IOException("Original AC/AN getter values differ from the exact raw INFO payload");
                            if(panels.get(i).kind==ShapeitCommonVariants.FileKind.BCF)expectedType=1;
                            else if(panels.get(i).kind==ShapeitCommonVariants.FileKind.BINARY){var seek=count(row,"SEEK");if(seek.returned()<1)throw new IOException("Original binary record lacks its raw SEEK type");expectedType=seek.first();}
                        }else if(ac!=0||an!=0)throw new IOException("Non-biallelic original event has nonzero AC/AN state");
                        if(expectedType!=type)throw new IOException("Original record type differs from raw source header/SEEK metadata");
                        var alleles=new ArrayList<String>();alleles.add(row.reference());alleles.addAll(row.alternates());
                        byRole.put(panels.get(i).role,new ShapeitCommonVariants.PanelRow(new ShapeitCommonVariants.Site(row.contig(),row.position(),row.id(),alleles),type,countAc,countAn,row.format("GT")!=null));
                    }
                    if(!physical)throw new IOException("Original join event contains no physical source record");
                    visitor.event(new Event(events,rawRows,new ShapeitCommonVariants.Event(byRole.get(Role.TARGET),byRole.get(Role.REFERENCE),byRole.get(Role.SCAFFOLD))));events++;
                }
                String[] end=lines.next("E",2+panels.size());if(number(end[1])!=events)throw new IOException("Original event footer differs from the complete event stream");
                var visits=new ArrayList<Long>();for(int i=0;i<panels.size();i++)visits.add(number(end[2+i]));
                for(int i=0;i<panels.size();i++)for(Resource resource:panels.get(i).resources)resource(lines,"A",i,resource);
                lines.next("Z",1);if(lines.peekType()!=null)throw new IOException("Trailing content after the original join completion marker");
                if(!original.equals(ShapeitRawIndex.identity(protocol,limits.protocolBytes(),check)))throw new IOException("Original join capture changed during linkage");
                for(int i=0;i<panels.size();i++){cursors.get(i).finish(visits.get(i));panels.get(i).verifyCompanions(limits,check);}
                for(var cursor:cursors)cursor.close();
                completed=new Report(events,visits,original,true);
            }
            Files.writeString(workspace.resolve("status.txt"),"COMPLETE RAW-RECORD LINKAGE\nOriginal join capture and raw occurrence census closed. No runtime admission or phasing certificate.\n",StandardOpenOption.CREATE_NEW);
            return completed;
        }catch(IOException|RuntimeException error){
            try{Files.writeString(workspace.resolve("status.txt"),"FAILED; dependent analysis SKIPPED\n"+error+"\nPreserve original inputs/capture and failed state; correct the cause and use fresh spool/link directories. Callback data are provisional.\n",StandardOpenOption.CREATE_NEW);}catch(IOException status){error.addSuppressed(status);}
            throw new IOException("SHAPEIT raw-record linkage failed; dependent analysis SKIPPED. Preserve "+workspace+"; "+error.getMessage(),error);
        }finally{IOException closing=null;for(var cursor:cursors)try{cursor.close();}catch(IOException error){if(closing==null)closing=error;else closing.addSuppressed(error);}if(closing!=null)throw closing;}
    }
    private static void validateRoles(List<Panel> panels) throws IOException {
        if(panels.isEmpty()||panels.size()>3||panels.get(0).role!=Role.TARGET)throw new IOException("Original join requires target followed by optional reference/scaffold panels");
        var roles=EnumSet.noneOf(Role.class);boolean gap=false;int previous=-1;
        for(Panel p:panels){if(!roles.add(p.role)||p.role.ordinal()<=previous)throw new IOException("Original join panel roles are repeated or out of order");previous=p.role.ordinal();
            if(p.kind==ShapeitCommonVariants.FileKind.VOID)gap=true;else if(gap)throw new IOException("VOID_HEADER_GAP: original XCF sample vectors are misaligned; SKIP dependent analysis and supply appropriate nonempty inputs or a separately verified fix");}
    }
    private static Headers headers(List<Panel> panels){ShapeitCommonVariants.Header target=null,reference=null,scaffold=null;for(Panel p:panels)switch(p.role){case TARGET->target=p.header();case REFERENCE->reference=p.header();case SCAFFOLD->scaffold=p.header();}return new Headers(target,reference,scaffold);}
    private static ShapeitCommonVariants.CountValue count(ShapeitBcf.Row row,String tag) throws IOException {var f=row.info().get(tag);if(f==null)return new ShapeitCommonVariants.CountValue(-3,0);if(f.type()<1||f.type()>3)return new ShapeitCommonVariants.CountValue(-2,0);int[] values=f.cellIntegers(0);return new ShapeitCommonVariants.CountValue(values.length,values.length==0?0:values[0]);}
    private static void resource(Lines lines,String stage,int index,Resource resource) throws IOException {
        String[] f=lines.next("F",7);
        if(!f[1].equals(stage)||number(f[2])!=index||!f[3].equals(resource.kind())||!decoded(f[4]).equals(resource.peerPath())||!f[5].equals(resource.identity().sha256())||number(f[6])!=resource.identity().bytes())throw new IOException("Original before/after resource fingerprint/path differs from its explicitly bound raw resource");
    }
    private static Regions regions(Lines lines,String group,Limits limits) throws IOException {
        String[] q=lines.next("Q",4);if(!q[1].equals(group))throw new IOException("Original region/target stage is out of order");boolean any=bit(q[2]),exclude=bit(q[3]);
        if(group.equals("R")&&exclude)throw new IOException("Original regions cannot claim target-exclusion semantics");
        var spans=new HashMap<String,List<Span>>();int count=0;
        while(Objects.equals(lines.peekType(),"I")){
            String[] f=lines.next("I",5);if(any||!f[1].equals(group)||++count>limits.intervals())throw new IOException("Incomplete or excessive original canonical intervals");
            String contig=decoded(f[2]);long start=number(f[3]),end=number(f[4]);if(contig.isEmpty()||start>end)throw new IOException("Invalid original interval bounds");spans.computeIfAbsent(contig,k->new ArrayList<>()).add(new Span(start,end));
        }
        if(any&&exclude)throw new IOException("Unresolved empty exclusion-target boundary; dependent analysis SKIPPED; supply an explicit, independently validated region");
        var sorted=new HashMap<String,List<Span>>();
        for(var entry:spans.entrySet()){
            entry.getValue().sort(Comparator.comparingLong(Span::start));var merged=new ArrayList<Span>();
            for(Span s:entry.getValue()){if(!merged.isEmpty()&&s.start()<=merged.get(merged.size()-1).end()){Span old=merged.remove(merged.size()-1);merged.add(new Span(old.start(),Math.max(old.end(),s.end())));}else merged.add(s);}sorted.put(entry.getKey(),List.copyOf(merged));
        }
        return new Regions(any,exclude,Map.copyOf(sorted));
    }
    private static List<String> famSamples(Path path,Limits limits,Runnable check) throws IOException {
        var names=new ArrayList<String>();try(var lines=new Lines(path,limits.lineBytes(),limits.resourceBytes(),check,false)){
            for(String line=lines.line();line!=null;line=lines.line()){
                if(names.size()>=limits.samples())throw new IOException("Complete XCF FAM sample capacity exceeded");
                if(line.isEmpty()){names.add("");continue;}
                var tokens=new ArrayList<String>();int i=0;while(i<line.length()){while(i<line.length()&&(line.charAt(i)==' '||line.charAt(i)=='\t'))i++;int begin=i;while(i<line.length()&&line.charAt(i)!=' '&&line.charAt(i)!='\t')i++;if(i>begin)tokens.add(line.substring(begin,i));}
                if(tokens.isEmpty())throw new IOException("SOURCE_FAM_WHITESPACE_BOUNDARY: original splitter dereferences an empty token vector; SKIP dependent analysis and supply valid sample metadata");
                int last=tokens.size()-1;String value=tokens.get(last);if(value.endsWith("\r"))tokens.set(last,value.substring(0,value.length()-1));names.add(tokens.get(0));
            }
        }return List.copyOf(names);
    }
    private static String decoded(String text) throws IOException {try{byte[] raw=Base64.getDecoder().decode(text);if(!Base64.getEncoder().encodeToString(raw).equals(text))throw new IOException("Noncanonical Base64 in original join capture");return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(raw)).toString();}catch(IllegalArgumentException error){throw new IOException("Invalid original join Base64",error);}}
    private static boolean bit(String text) throws IOException {if(text.equals("0"))return false;if(text.equals("1"))return true;throw new IOException("Invalid original join boolean");}
    private static long number(String text) throws IOException {try{long n=Long.parseLong(text);if(n<0||!Long.toString(n).equals(text))throw new NumberFormatException();return n;}catch(NumberFormatException error){throw new IOException("Invalid original join unsigned counter",error);}}
    private static int integer(String text) throws IOException {long n=number(text);if(n>Integer.MAX_VALUE)throw new IOException("Original join integer exceeds capacity");return (int)n;}
    private static int signedInteger(String text) throws IOException {try{int n=Integer.parseInt(text);if(!Integer.toString(n).equals(text))throw new NumberFormatException();return n;}catch(NumberFormatException error){throw new IOException("Invalid original join signed record type",error);}}
    private static long uint32(String text) throws IOException {long n=number(text);if(n>0xffffffffL)throw new IOException("Original join uint32 exceeds capacity");return n;}
    private static final class Lines implements AutoCloseable {
        private final InputStream input;private final int lineCapacity;private final long capacity;private final Runnable check;private final boolean protocol;private long bytes;private String pending;private boolean loaded;
        Lines(Path path,int lineCapacity,long capacity,Runnable check) throws IOException {this(path,lineCapacity,capacity,check,true);}
        Lines(Path path,int lineCapacity,long capacity,Runnable check,boolean protocol) throws IOException {input=new BufferedInputStream(Files.newInputStream(path),65536);this.lineCapacity=lineCapacity;this.capacity=capacity;this.check=check;this.protocol=protocol;}
        String line() throws IOException {
            check.run();var output=new ByteArrayOutputStream();
            for(int b;(b=input.read())!=-1;){if(++bytes>capacity||output.size()>=lineCapacity)throw new IOException("Complete original join line/file capacity exceeded; raise declared limits without truncating data");if(b=='\n')return utf8(output.toByteArray());if(protocol&&(b=='\r'||b==0))throw new IOException("Require original join LF framing without raw CR/NUL");output.write(b);if((bytes&65535)==0)check.run();}
            if(output.size()!=0){if(protocol)throw new IOException("Incomplete final original join line");return utf8(output.toByteArray());}return null;
        }
        String peekType() throws IOException {if(!loaded){pending=line();loaded=true;}if(pending==null)return null;int tab=pending.indexOf('\t');return tab<0?pending:pending.substring(0,tab);}
        String[] next(String type,int fields) throws IOException {if(!Objects.equals(peekType(),type))throw new IOException("Incomplete/out-of-order original join capture; expected "+type+", observed "+peekType());String value=pending;pending=null;loaded=false;String[] row=value.split("\t",-1);if(row.length!=fields)throw new IOException("Original join field count differs at "+type);return row;}
        private static String utf8(byte[] raw) throws IOException {return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(raw)).toString();}
        @Override public void close() throws IOException {input.close();}
    }
}
