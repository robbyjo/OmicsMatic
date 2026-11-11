/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.genetics.ShapeitGenotypes;

/** Complete c34d4db ordinary-GT output contract. Does not establish process/runtime admission or phase accuracy. */
final class ShapeitCommonOutputs {
    private ShapeitCommonOutputs() { }
    record Result(ShapeitGenotypes.Report genotypes,ShapeitBcf.Report rawOutput,long appliedScaffoldConstraintsChecked,
                  long targetAlleleCountRows,long sourceANIncludesReferenceRows,Path targetCounts,ShapeitRawIndex.Identity targetCountsIdentity,
                  List<String> warnings) {
        Result {warnings=List.copyOf(warnings);}
    }
    static Result compare(ShapeitCommonData.Prepared prepared,Path output,ShapeitBcf.Limits outputLimits,Path destination,long tableBytes,Runnable check) throws IOException {
        Objects.requireNonNull(prepared);Objects.requireNonNull(outputLimits);Objects.requireNonNull(check);
        if(tableBytes<1)throw new IllegalArgumentException("Require positive complete target-count table capacity");
        Path work=destination.toAbsolutePath().normalize();if(work.getParent()==null||!work.getParent().toRealPath().equals(work.getParent()))throw new IOException("Require a canonical existing audit parent");Files.createDirectory(work);
        Path countsPath=work.resolve("target-allele-counts.tsv");
        var readers=new EnumMap<ShapeitJoinedRecords.Role,ShapeitRawIndex.Reader>(ShapeitJoinedRecords.Role.class);
        try {
            prepared.verify(check);var before=ShapeitRawIndex.identity(output,outputLimits.inputBytes(),check);
            int samples=prepared.callsHeader().target().size(),reference=prepared.callsHeader().reference().size();
            var audit=new ShapeitGenotypes.Audit(prepared.callsHeader().target(),prepared.callsHeader().target(),prepared.selection().selectedSites().size(),Math.multiplyExact((long)samples,prepared.selection().selectedSites().size()));
            long[] rows={0},scaffold={0},anReference={0};ShapeitBcf.Report raw;
            try(var ledger=new DataInputStream(new BufferedInputStream(Files.newInputStream(prepared.ledger())));
                var table=new BufferedWriter(new OutputStreamWriter(new BoundedOutput(Files.newOutputStream(countsPath,StandardOpenOption.CREATE_NEW),tableBytes),StandardCharsets.UTF_8))){
                table.write("selected_ordinal\tcontig\tposition\tid\tref\talt\ttarget_AC\ttarget_AN\tsource_AC\tsource_AN\n");
                for(var input:prepared.inputs())readers.put(input.role(),input.store().open());
                raw=ShapeitBcf.read(output,outputLimits,new ShapeitBcf.Visitor(){
                    @Override public void header(ShapeitBcf.HeaderView header) throws IOException {if(!header.samples().equals(prepared.callsHeader().target()))throw new IOException("Common-stage output changed target sample identity/order or emitted reference samples");}
                    @Override public void row(ShapeitBcf.Row actual) throws IOException {
                        check.run();if(rows[0]>=prepared.selection().selectedSites().size())throw new IOException("Common-stage output added variants beyond the complete selected population");
                        var expected=prepared.selection().selectedSites().get(Math.toIntExact(rows[0]));ShapeitCommonData.sameSite(expected,actual);
                        long[] ordinals={ledger.readLong(),ledger.readLong(),ledger.readLong()};
                        var target=ShapeitCommonData.row(readers,ShapeitJoinedRecords.Role.TARGET,ordinals[0]);var ref=ShapeitCommonData.row(readers,ShapeitJoinedRecords.Role.REFERENCE,ordinals[1]);var scaf=ShapeitCommonData.row(readers,ShapeitJoinedRecords.Role.SCAFFOLD,ordinals[2]);
                        ShapeitCommonData.sameSite(expected,target);
                        var source=ShapeitCommonCalls.decode(prepared.callsHeader(),ShapeitCommonData.calls(target,samples),ref==null?List.of():ShapeitCommonData.calls(ref,reference),scaf==null||scaf.alternates().size()!=1?null:ShapeitCommonData.calls(scaf,prepared.callsHeader().scaffold().size()));
                        var result=ShapeitCommonData.calls(actual,samples);var tokens=new ArrayList<String>();long ac=0;
                        for(int i=0;i<samples;i++){
                            var gt=result.get(i);if(gt.values().stream().anyMatch(v->v!=3&&v!=5))throw new IOException("Common-stage output does not contain the complete original writer's phased diploid GT encoding");
                            int a=(gt.values().get(0)>>1)-1,b=(gt.values().get(1)>>1)-1;tokens.add(a+"|"+b);ac+=a+b;
                            int state=source.target().get(i).scaffoldPacked();
                            if((state&3)==3){if(a!=((state>>2)&1)||b!=((state>>3)&1))throw new IOException("Common-stage output changed an applied original scaffold constraint");scaffold[0]++;}
                        }
                        String identity=key(expected.contig(),expected.position(),expected.id(),expected.alleles(),rows[0]);
                        audit.accept(identity,source.target().stream().map(ShapeitCommonCalls.Target::raw).toList(),identity,tokens);
                        long sourceAC=integerTag(actual,"AC"),sourceAN=integerTag(actual,"AN"),physicalAN=Math.multiplyExact(2L,samples),expectedAN=Math.multiplyExact(2L,(long)samples+reference);
                        if(sourceAC!=ac||sourceAN!=expectedAN)throw new IOException("Common-stage output AC/AN differs from the exact original target-AC/combined-haplotype-AN writer contract");
                        if(sourceAN!=physicalAN)anReference[0]++;
                        for(String value:List.of(expected.contig(),expected.id(),expected.alleles().get(0),expected.alleles().get(1)))if(value.codePoints().anyMatch(Character::isISOControl))throw new IOException("Control characters cannot form an unambiguous target-count TSV; preserve raw output and correct invalid source identifiers");
                        table.write(rows[0]+"\t"+expected.contig()+"\t"+expected.position()+"\t"+expected.id()+"\t"+expected.alleles().get(0)+"\t"+expected.alleles().get(1)+"\t"+ac+"\t"+physicalAN+"\t"+sourceAC+"\t"+sourceAN+"\n");rows[0]++;
                    }
                },check);
                if(!raw.complete()||rows[0]!=prepared.selection().selectedSites().size()||ledger.read()!=-1)throw new IOException("Common-stage output/selected-ledger population is incomplete");
            }finally{for(var reader:readers.values())reader.close();}
            if(scaffold[0]!=prepared.totals().scaffoldApplied())throw new IOException("Complete applied-scaffold output census differs from the source reader");
            var genotypes=audit.finish();prepared.verify(check);
            if(!before.equals(ShapeitRawIndex.identity(output,outputLimits.inputBytes(),check)))throw new IOException("Common-stage output changed during complete audit");
            var warnings=new ArrayList<String>();
            if(anReference[0]!=0)warnings.add("SOURCE AN INCLUDES REFERENCE HAPLOTYPES: "+anReference[0]+" rows report combined target-plus-reference AN while AC counts target haplotypes only. Required action: use the verified target_AC/target_AN columns in target-allele-counts.tsv for target frequencies, or create and verify a separately retagged copy. Preserve the untouched original BCF; do not use its source AC/AN ratio as target frequency.");
            if(genotypes.observedInputAllelesIgnoredInMissingCalls()!=0)warnings.add("UPSTREAM PARTIAL-MISSING POLICY: "+genotypes.observedInputAllelesIgnoredInMissingCalls()+" supplied observed alleles occur in calls the original reader treats as wholly missing. Required action: review those original calls and the source imputation policy; dosage preservation is guaranteed only for reader-defined observed calls.");
            if(genotypes.missingAlleleReadAsReferenceCalls()!=0)warnings.add("UPSTREAM PHASE-MARKED MISSING: "+genotypes.missingAlleleReadAsReferenceCalls()+" target calls have missing alleles the original reader treats as reference. Required action: review raw GT phase bits and supply a separately verified corrected input if that source behavior is unintended; do not reinterpret this audit as preservation of biologically observed alleles.");
            if(prepared.risks().contains(ShapeitCommonCalls.Risk.REFERENCE_PHASE_MARKED_MISSING_READ_AS_REFERENCE))warnings.add("REFERENCE PHASE-MARKED MISSING: the original reader can accept phase-marked missing reference alleles as reference bases. Required action: supply a verified complete phased reference panel before admitting a reference-based analysis; this source-state/output audit does not certify that the reference data are complete.");
            if(prepared.risks().contains(ShapeitCommonCalls.Risk.SCAFFOLD_PHASE_MARKED_MISSING))warnings.add("SCAFFOLD PHASE-MARKED MISSING: the original literal-zero test can misread phased missing scaffold alleles. Required action: review raw scaffold GT values and applied-constraint ledgers; use verified called/phased constraints before admitting scaffold-based results.");
            if(genotypes.vectorEndInputCalls()!=0||prepared.risks().contains(ShapeitCommonCalls.Risk.REFERENCE_VECTOR_END)||prepared.risks().contains(ShapeitCommonCalls.Risk.SCAFFOLD_VECTOR_END))warnings.add("UNADMITTED PLOIDY REPRESENTATION: selected rows contain vector-end allele padding. Required action: use the separate validated haploid/pedigree contract before admitting those analyses; an ordinary-GT source-state match does not establish correct haploid interpretation.");
            if(prepared.callsHeader().unmatchedScaffoldNames()!=0)warnings.add("UNMATCHED SCAFFOLD SAMPLES: "+prepared.callsHeader().unmatchedScaffoldNames()+" scaffold sample names do not map to targets. Required action: review sample identities and applied-constraint counts; an upstream exit zero does not establish that scaffold information was used.");
            if(!prepared.callsHeader().scaffold().isEmpty()&&scaffold[0]==0)warnings.add("NO SCAFFOLD CONSTRAINTS APPLIED: the supplied scaffold contributed no eligible selected heterozygous/phased constraints. Required action: review sample/variant overlap, GT phase bits and target missingness before claiming scaffold-based phasing.");
            warnings.add("SCIENTIFIC REVIEW REQUIRED: complete selected-variant/sample, raw output EOF, source-defined dosage, AC/AN and applied-scaffold checks passed. Runtime/process admission, pedigree/haploid adjustments, rare/ligation/posterior contracts and biological phase accuracy require their own verification.");
            var identity=ShapeitRawIndex.identity(countsPath,tableBytes,check);
            Files.writeString(work.resolve("WARNINGS.md"),String.join("\n\n",warnings)+"\n",StandardOpenOption.CREATE_NEW);
            Files.writeString(work.resolve("status.txt"),"COMPLETE ORDINARY-GT COMMON OUTPUT AUDIT\nThis is not a runtime/process admission or phase-accuracy certificate.\n",StandardOpenOption.CREATE_NEW);
            return new Result(genotypes,raw,scaffold[0],rows[0],anReference[0],countsPath,identity,warnings);
        }catch(IOException|RuntimeException error){
            try{Files.writeString(work.resolve("status.txt"),"FAILED; no common fitting result is admitted\n"+error+"\nPreserve original input/output and failed audit state; correct the cause and use a fresh directory.\n",StandardOpenOption.CREATE_NEW);}catch(IOException diagnostic){error.addSuppressed(diagnostic);}
            throw new IOException("SHAPEIT common output audit failed; no result is admitted: "+error.getMessage(),error);
        }
    }
    private static long integerTag(ShapeitBcf.Row row,String name) throws IOException {var tag=row.info().get(name);if(tag==null||tag.type()<1||tag.type()>3)throw new IOException("Common-stage output requires original integer INFO/"+name);int[] values=tag.cellIntegers(0);if(values.length!=1||values[0]<0)throw new IOException("Common-stage output INFO/"+name+" is incomplete or invalid");return values[0];}
    private static String key(String contig,int position,String id,List<String> alleles,long ordinal){var encoder=Base64.getEncoder();var fields=new ArrayList<String>();fields.add(contig);fields.add(Integer.toString(position));fields.add(id);fields.addAll(alleles);return ordinal+":"+String.join(":",fields.stream().map(v->encoder.encodeToString(v.getBytes(StandardCharsets.UTF_8))).toList());}
    private static final class BoundedOutput extends FilterOutputStream {
        private final long maximum;private long written;
        BoundedOutput(OutputStream output,long maximum){super(output);this.maximum=maximum;}
        private void reserve(int count) throws IOException {if((written=Math.addExact(written,count))>maximum)throw new IOException("Complete target-count table exceeds declared capacity; raise it without truncating variants");}
        @Override public void write(int b) throws IOException {reserve(1);out.write(b);}
        @Override public void write(byte[] b,int offset,int count) throws IOException {reserve(count);out.write(b,offset,count);}
    }
}
