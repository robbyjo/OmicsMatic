/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.genetics.ShapeitCommonVariants;
import org.jlinalg.genetics.ShapeitGeneticMap;
import org.jlinalg.genetics.ShapeitGenotypes;

/** Complete ordinary-GT common-stage data preparation, from a bound original join. No execution admission. */
final class ShapeitCommonData {
    private ShapeitCommonData() { }
    record Input(ShapeitJoinedRecords.Role role,ShapeitRawIndex.Store store,List<ShapeitJoinedRecords.Resource> resources) {
        Input {Objects.requireNonNull(role);Objects.requireNonNull(store);resources=List.copyOf(resources);}
    }
    record Spec(String contig,String region,boolean snpsOnly,Double minimumMaf,Path map,
                ShapeitCommonVariants.Limits selectionLimits,ShapeitGeneticMap.Limits mapLimits,
                ShapeitCommonCalls.Limits callLimits,ShapeitJoinedRecords.Limits joinLimits,long ledgerBytes) {
        Spec {
            identity(contig);Objects.requireNonNull(region);Objects.requireNonNull(selectionLimits);Objects.requireNonNull(mapLimits);Objects.requireNonNull(callLimits);Objects.requireNonNull(joinLimits);
            if(ledgerBytes<24)throw new IllegalArgumentException("Require adequate complete common-stage ledger capacity");
            if(map!=null)map=map.toAbsolutePath().normalize();
        }
    }
    record Totals(long homozygousReference,long heterozygous,long homozygousAlternate,long missing,long scaffoldApplied,
                  long referenceAlleles0,long referenceAlleles1,long ignoredObservedAllelesInMissingCalls) { }
    static final class Prepared {
        private final Spec spec;private final Path work,ledger,protocol;private final List<Input> inputs;
        private final ShapeitRawIndex.Identity ledgerIdentity,protocolIdentity,mapIdentity;
        private final ShapeitCommonCalls.Header callsHeader;private final ShapeitCommonVariants.Report selection;
        private final ShapeitGeneticMap.Mapping mapping;private final Totals totals;private final Set<ShapeitCommonCalls.Risk> risks;
        private Prepared(Spec spec,Path work,Path ledger,Path protocol,List<Input> inputs,ShapeitRawIndex.Identity ledgerIdentity,
                         ShapeitRawIndex.Identity protocolIdentity,ShapeitRawIndex.Identity mapIdentity,ShapeitCommonCalls.Header callsHeader,
                         ShapeitCommonVariants.Report selection,ShapeitGeneticMap.Mapping mapping,Totals totals,Set<ShapeitCommonCalls.Risk> risks){
            this.spec=spec;this.work=work;this.ledger=ledger;this.protocol=protocol;this.inputs=List.copyOf(inputs);this.ledgerIdentity=ledgerIdentity;this.protocolIdentity=protocolIdentity;this.mapIdentity=mapIdentity;
            this.callsHeader=callsHeader;this.selection=selection;this.mapping=mapping;this.totals=totals;this.risks=Collections.unmodifiableSet(risks.isEmpty()?EnumSet.noneOf(ShapeitCommonCalls.Risk.class):EnumSet.copyOf(risks));
        }
        Spec spec(){return spec;}Path work(){return work;}Path ledger(){return ledger;}List<Input> inputs(){return inputs;}
        ShapeitCommonCalls.Header callsHeader(){return callsHeader;}ShapeitCommonVariants.Report selection(){return selection;}
        ShapeitGeneticMap.Mapping mapping(){return mapping;}Totals totals(){return totals;}Set<ShapeitCommonCalls.Risk> risks(){return risks;}
        void verify(Runnable check) throws IOException {
            if(!ledgerIdentity.equals(ShapeitRawIndex.identity(ledger,spec.ledgerBytes(),check))||!protocolIdentity.equals(ShapeitRawIndex.identity(protocol,spec.joinLimits().protocolBytes(),check)))throw new IOException("Common-stage selection/capture ledger changed; no fitting result is admitted");
            if(mapIdentity!=null&&!mapIdentity.equals(ShapeitRawIndex.identity(spec.map(),mapBytes(spec.mapLimits()),check)))throw new IOException("Common-stage genetic map changed");
            for(Input input:inputs){input.store().verify();for(var resource:input.resources())if(!resource.kind().equals("B")&&!resource.identity().equals(ShapeitRawIndex.identity(resource.localPath(),spec.joinLimits().resourceBytes(),check)))throw new IOException("Common-stage input companion changed");}
        }
    }
    static Prepared prepare(Spec spec,List<Input> supplied,Path protocol,String captureSha256,Path destination,Runnable check) throws IOException {
        Objects.requireNonNull(spec);Objects.requireNonNull(check);List<Input> inputs=List.copyOf(supplied);
        if(inputs.isEmpty()||inputs.get(0).role()!=ShapeitJoinedRecords.Role.TARGET)throw new IOException("Complete common-stage preparation requires target-first original inputs");
        Path work=destination.toAbsolutePath().normalize();canonicalParent(work);Files.createDirectory(work);
        Path eventLedger=work.resolve("joined.ordinals"),selectedLedger=work.resolve("selected.ordinals");
        try {
            var roles=EnumSet.noneOf(ShapeitJoinedRecords.Role.class);var panels=new ArrayList<ShapeitJoinedRecords.Panel>();var byRole=new EnumMap<ShapeitJoinedRecords.Role,Input>(ShapeitJoinedRecords.Role.class);
            for(Input input:inputs){
                if(!roles.add(input.role()))throw new IOException("Repeated common-stage source role");
                if(input.store().header().infoTags().contains("SEEK"))throw new IOException("XCF binary genotype decoding is not admitted by this ordinary-GT common-stage contract; use a separately validated binary workflow");
                validateSamples(input.store().header().samples());
                panels.add(ShapeitJoinedRecords.Panel.bind(input.role(),input.store(),input.resources(),spec.joinLimits(),check));byRole.put(input.role(),input);
            }
            var header=new ShapeitCommonCalls.Header(names(byRole,ShapeitJoinedRecords.Role.TARGET),names(byRole,ShapeitJoinedRecords.Role.REFERENCE),names(byRole,ShapeitJoinedRecords.Role.SCAFFOLD),spec.callLimits());
            ShapeitCommonVariants.Audit[] audit=new ShapeitCommonVariants.Audit[1];ShapeitJoinedRecords.Report joined;
            try(var output=new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(eventLedger,StandardOpenOption.CREATE_NEW)))){
                joined=ShapeitJoinedRecords.link(protocol,captureSha256,spec.region(),panels,work.resolve("link"),spec.joinLimits(),new ShapeitJoinedRecords.Visitor(){
                    @Override public void header(ShapeitJoinedRecords.Headers h){audit[0]=new ShapeitCommonVariants.Audit(h.target(),h.reference(),h.scaffold(),ShapeitCommonVariants.Options.fromDouble(spec.snpsOnly(),spec.minimumMaf()),spec.selectionLimits());}
                    @Override public void event(ShapeitJoinedRecords.Event event) throws IOException {
                        check.run();if(Math.multiplyExact(event.ordinal()+1,24)>spec.ledgerBytes())throw new IOException("Complete common-stage event ledger exceeds capacity; raise the limit without truncating events");
                        audit[0].accept(event.scannerEvent());long[] ordinals=new long[3];for(int i=0;i<inputs.size();i++){var row=event.rawRows().get(i);ordinals[inputs.get(i).role().ordinal()]=row==null?0:row.ordinal();}
                        for(long ordinal:ordinals)output.writeLong(ordinal);
                    }
                },check);
            }
            var selection=audit[0].finish(joined.events());
            var readers=new EnumMap<ShapeitJoinedRecords.Role,ShapeitRawIndex.Reader>(ShapeitJoinedRecords.Role.class);
            long[] totals=new long[8];var risks=EnumSet.noneOf(ShapeitCommonCalls.Risk.class);
            try(var events=new DataInputStream(new BufferedInputStream(Files.newInputStream(eventLedger)));var output=new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(selectedLedger,StandardOpenOption.CREATE_NEW)))){
                for(Input input:inputs)readers.put(input.role(),input.store().open());
                long kept=0;
                for(var decision:selection.decisions()){
                    check.run();long[] ordinals={events.readLong(),events.readLong(),events.readLong()};if(!decision.kept())continue;
                    if(!decision.site().contig().equals(spec.contig()))throw new IOException("Common fitting requires one declared contig; split a multi-chromosome request into independently audited runs");
                    var target=row(readers,ShapeitJoinedRecords.Role.TARGET,ordinals[0]);var reference=row(readers,ShapeitJoinedRecords.Role.REFERENCE,ordinals[1]);var scaffold=row(readers,ShapeitJoinedRecords.Role.SCAFFOLD,ordinals[2]);
                    sameSite(decision.site(),target);
                    var state=ShapeitCommonCalls.decode(header,calls(target,header.target().size()),reference==null?List.of():calls(reference,header.reference().size()),scaffold==null||scaffold.alternates().size()!=1?null:calls(scaffold,header.scaffold().size()));
                    var c=state.counts();long[] add={c.homozygousReference(),c.heterozygous(),c.homozygousAlternate(),c.missing(),c.scaffoldApplied(),c.referenceAlleles0(),c.referenceAlleles1(),c.ignoredObservedAllelesInMissingCalls()};for(int i=0;i<totals.length;i++)totals[i]=Math.addExact(totals[i],add[i]);risks.addAll(state.risks());
                    if(Math.multiplyExact(++kept,24)>spec.ledgerBytes())throw new IOException("Complete selected common-stage ledger exceeds capacity");for(long ordinal:ordinals)output.writeLong(ordinal);
                }
                if(events.read()!=-1||kept!=selection.selectedSites().size())throw new IOException("Common-stage event/selected ledgers did not close against the complete selector");
            }finally{for(var reader:readers.values())reader.close();}
            int[] positions=selection.selectedSites().stream().mapToInt(ShapeitCommonVariants.Site::position).toArray();
            ShapeitGeneticMap.Mapping mapping;ShapeitRawIndex.Identity mapIdentity=null;
            if(spec.map()==null)mapping=ShapeitGeneticMap.uniform(positions,spec.mapLimits());
            else{
                mapIdentity=ShapeitRawIndex.identity(spec.map(),mapBytes(spec.mapLimits()),check);
                try(var lines=new MapLines(spec.map(),spec.mapLimits(),check)){
                    var parsed=ShapeitGeneticMap.parse(lines,spec.mapLimits());mapping=ShapeitGeneticMap.interpolate(parsed,positions,ShapeitGeneticMap.Profile.SHAPEIT5_C34D,ShapeitGeneticMap.Arithmetic.FUSED_BINARY64,spec.mapLimits());
                }
                if(!mapIdentity.equals(ShapeitRawIndex.identity(spec.map(),mapBytes(spec.mapLimits()),check)))throw new IOException("Common-stage genetic map changed during complete parsing");
            }
            var total=new Totals(totals[0],totals[1],totals[2],totals[3],totals[4],totals[5],totals[6],totals[7]);
            var prepared=new Prepared(spec,work,selectedLedger,protocol,inputs,ShapeitRawIndex.identity(selectedLedger,spec.ledgerBytes(),check),joined.protocolIdentity(),mapIdentity,header,selection,mapping,total,risks);
            prepared.verify(check);Files.writeString(work.resolve("status.txt"),"COMPLETE COMMON-DATA PREPARATION\nNo runtime execution or phasing result is admitted by this preparation alone.\n",StandardOpenOption.CREATE_NEW);return prepared;
        }catch(IOException|RuntimeException error){
            try{Files.writeString(work.resolve("status.txt"),"FAILED; dependent common fitting must not run\n"+error+"\nPreserve original inputs and failed state; correct the cause and use a fresh directory.\n",StandardOpenOption.CREATE_NEW);}catch(IOException diagnostic){error.addSuppressed(diagnostic);}
            throw new IOException("SHAPEIT common-data preparation failed; no fitting result is admitted: "+error.getMessage(),error);
        }
    }
    static ShapeitBcf.Row row(Map<ShapeitJoinedRecords.Role,ShapeitRawIndex.Reader> readers,ShapeitJoinedRecords.Role role,long ordinal) throws IOException {
        if(ordinal==0)return null;var reader=readers.get(role);if(reader==null)throw new IOException("Common-stage ledger has an unconfigured physical input role");return reader.row(reader.entry(ordinal-1));
    }
    static List<ShapeitGenotypes.WireCall> calls(ShapeitBcf.Row row,int samples) throws IOException {
        if(row==null||row.alternates().size()!=1)throw new IOException("Selected ordinary common-stage record must be biallelic");var gt=row.format("GT");
        if(gt==null||gt.width()!=2||gt.cells()!=samples)throw new IOException("Selected common-stage GT requires the complete declared diploid storage frame; missing/triploid/all-haploid data require an appropriate separately validated workflow");
        int[] values=gt.integers();var calls=new ArrayList<ShapeitGenotypes.WireCall>();for(int i=0;i<samples;i++)calls.add(new ShapeitGenotypes.WireCall(List.of(values[2*i],values[2*i+1])));return calls;
    }
    static void sameSite(ShapeitCommonVariants.Site expected,ShapeitBcf.Row actual) throws IOException {
        if(actual==null)throw new IOException("Selected common-stage target occurrence is missing");var alleles=new ArrayList<String>();alleles.add(actual.reference());alleles.addAll(actual.alternates());
        if(!expected.equals(new ShapeitCommonVariants.Site(actual.contig(),actual.position(),actual.id(),alleles)))throw new IOException("Selected common-stage raw target identity/order differs from the original scanner");
    }
    private static List<String> names(Map<ShapeitJoinedRecords.Role,Input> inputs,ShapeitJoinedRecords.Role role){return inputs.containsKey(role)?inputs.get(role).store().header().samples():List.of();}
    private static void validateSamples(List<String> names) throws IOException {if(names.isEmpty())throw new IOException("Ordinary-GT common input requires a nonempty sample population");var seen=new HashSet<String>();for(String name:names){identity(name);if(!seen.add(name))throw new IOException("Duplicate common-stage sample identity; supply uniquely identified samples before fitting");}}
    private static void identity(String value){if(value==null||value.isEmpty()||value.codePoints().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Require exact nonempty single-line common-stage identities");}
    private static long mapBytes(ShapeitGeneticMap.Limits limits){return Math.multiplyExact(4L,limits.totalCharacters());}
    private static void canonicalParent(Path path) throws IOException {if(path.getParent()==null||!path.getParent().toRealPath().equals(path.getParent()))throw new IOException("Require a canonical existing parent for fresh common-stage state");}
    private static final class MapLines implements Iterable<String>,AutoCloseable {
        private final Reader reader;private final ShapeitGeneticMap.Limits limits;private final Runnable check;
        MapLines(Path path,ShapeitGeneticMap.Limits limits,Runnable check) throws IOException {this.reader=new BufferedReader(new InputStreamReader(Files.newInputStream(path),StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)),65536);this.limits=limits;this.check=check;}
        @Override public Iterator<String> iterator(){return new Iterator<>(){String next;boolean loaded;
            private void load(){if(loaded)return;loaded=true;check.run();var line=new StringBuilder();try{for(int ch;(ch=reader.read())!=-1;){if(ch=='\n'){next=line.toString();return;}if(line.length()>=limits.lineCharacters())throw new IOException("Complete genetic-map line capacity exceeded");line.append((char)ch);}next=line.isEmpty()?null:line.toString();}catch(IOException error){throw new UncheckedIOException(error);}}
            @Override public boolean hasNext(){load();return next!=null;}
            @Override public String next(){load();if(next==null)throw new NoSuchElementException();String value=next;loaded=false;next=null;return value;}
        };}
        @Override public void close() throws IOException {reader.close();}
    }
}
