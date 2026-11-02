/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Complete real original-output bundles, including host/probe receipts; never invokes an aligner. */
class MafftWorkflowBundleTest {
    @TempDir Path root;
    static final String DNA="DNA-auto-full-native-replay",KEEP="AA-single-backbone-keep-map",EXPAND="AA-single-backbone-expand-global100";
    static Stream<String> names(){return Stream.of(DNA,KEEP,EXPAND);}
    static String sha(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    Path extract(String name)throws Exception {
        byte[] archive;try(var in=getClass().getResourceAsStream("/org/jlinalg/cli/mafft-workflow/initial-controls.zip")){assertNotNull(in);archive=in.readAllBytes();}
        assertEquals("c77be302d2147188a1dfeb479c89a22bd4cd7ba76ac0e052eb0612c8cef966e2",sha(archive));
        Path out=root.resolve("bundle");Files.createDirectory(out);String prefix=name+"/";
        try(var zip=new ZipInputStream(new java.io.ByteArrayInputStream(archive))){
            for(var entry=zip.getNextEntry();entry!=null;entry=zip.getNextEntry()){
                if(!entry.getName().startsWith(prefix))continue;
                Path target=out.resolve(entry.getName().substring(prefix.length())).normalize();assertTrue(target.startsWith(out));
                if(entry.isDirectory())Files.createDirectories(target);else {Files.createDirectories(target.getParent());Files.copy(zip,target);}
            }
        }
        assertTrue(Files.isRegularFile(out.resolve("COMPLETED.json")));return out;
    }
    @SuppressWarnings("unchecked") Map<String,Object> json(Path path)throws Exception{return (Map<String,Object>)SimpleJson.parse(Files.readString(path,StandardCharsets.UTF_8));}
    void write(Path path,Object value)throws Exception{Files.writeString(path,MafftJson.json(value)+"\n",StandardCharsets.UTF_8);}
    @SuppressWarnings("unchecked") void rehash(Path out,String... paths)throws Exception {
        Path manifest=out.resolve("manifest.json");var m=json(manifest);var files=(Map<String,Object>)m.get("files");
        for(String name:paths){byte[] raw=Files.readAllBytes(out.resolve(name));assertTrue(files.containsKey(name));files.put(name,Map.of("bytes",raw.length,"sha256",sha(raw)));}
        write(manifest,m);Path completed=out.resolve("COMPLETED.json");var c=json(completed);c.put("manifest_sha256",sha(Files.readAllBytes(manifest)));write(completed,c);
    }
    void rejected(Path out){assertThrows(Exception.class,()->MafftBundle.verify(out));}
    @ParameterizedTest @MethodSource("names") void everyCompleteRealSourceAndRestoredPopulationIsPortable(String name)throws Exception {Path out=extract(name);assertEquals(sha(Files.readAllBytes(out.resolve("manifest.json"))),MafftBundle.verify(out).sha256());}
    @Test void changedLateBytesFailBeforeRehashedSemanticReplay()throws Exception {Path out=extract(DNA);Files.writeString(out.resolve("alignment.fasta"),"extra",StandardCharsets.UTF_8,StandardOpenOption.APPEND);rejected(out);}
    @Test void anExtraEmptyDirectoryCannotHideOutsideTheManifest()throws Exception {Path out=extract(DNA);Files.createDirectory(out.resolve("unrecorded"));rejected(out);}
    @Test void aMissingCompletionNeverAdmitsAnOtherwiseValidPopulation()throws Exception {Path out=extract(DNA);Files.delete(out.resolve("COMPLETED.json"));rejected(out);}
    @Test void aRemainingProvisionalCompletionCannotBeMistakenForFinalization()throws Exception {Path out=extract(DNA);Files.copy(out.resolve("COMPLETED.json"),out.resolve("COMPLETION-PROVISIONAL.json"));rejected(out);}
    @Test void aRehashedRestoredHeaderCannotRebindTheOriginalTaxon()throws Exception {Path out=extract(DNA);Path p=out.resolve("alignment.fasta");Files.writeString(p,Files.readString(p).replaceFirst(">[^\\n]+",">different taxon"));rehash(out,"alignment.fasta");rejected(out);}
    @Test void rehashedPreparedCellsMustStillMatchOriginalInputAndUngapping()throws Exception {Path out=extract(DNA);Path p=out.resolve("staged/input.fasta");Files.writeString(p,Files.readString(p).replaceFirst("ACGT","TCGT"));rehash(out,"staged/input.fasta");rejected(out);}
    @Test void rehashedReplayCannotChangeTheOriginalRequestedStrategy()throws Exception {Path out=extract(DNA);Path p=out.resolve("recipe-replay.json");var j=json(p);j.put("strategy","SOURCE_DEFAULT");write(p,j);rehash(out,"recipe-replay.json");rejected(out);}
    @Test void rehashedOriginalRequestCannotBeReplacedByDifferentUserSettings()throws Exception {Path out=extract(DNA);Path p=out.resolve("recipe-request.json");var j=json(p);j.put("strategy","SOURCE_DEFAULT");write(p,j);rehash(out,"recipe-request.json");rejected(out);}
    @SuppressWarnings("unchecked") @Test void rehashedCommandCannotClaimAnUnexecutedIterationMaximum()throws Exception {Path out=extract(EXPAND);Path p=out.resolve("process/process.json");var j=json(p);var command=new ArrayList<>((List<String>)j.get("command"));int index=command.indexOf("--maxiterate");assertTrue(index>=0);command.set(index+1,"999");j.put("command",command);write(p,j);rehash(out,"process/process.json");rejected(out);}
    @SuppressWarnings("unchecked") @Test void rehashedResourceLimitsCannotReplaceTheAdmittedProcessLimits()throws Exception {Path out=extract(DNA);Path p=out.resolve("process/process.json");var j=json(p);((Map<String,Object>)j.get("limits")).put("cpuSeconds",1);write(p,j);rehash(out,"process/process.json");rejected(out);}
    @SuppressWarnings("unchecked") @Test void rehashedRuntimeBytesMustMatchTheExactOriginalReleaseLock()throws Exception {Path out=extract(DNA);Path p=out.resolve("runtime-admission.json");var j=json(p);var files=(Map<String,Object>)j.get("files");var pin=(Map<String,Object>)files.get("mafft.bat");pin.put("sha256","0".repeat(64));write(p,j);rehash(out,"runtime-admission.json");rejected(out);}
    @Test void rehashedHostSnapshotCannotClaimAnotherProfile()throws Exception {Path out=extract(DNA);Path p=out.resolve("host-admission/metadata/snapshot.json");var j=json(p);j.put("profile","linux-x86_64-mafft-7.313");write(p,j);Path h=out.resolve("host-admission.json");var host=json(h);host.put("snapshot_sha256",sha(Files.readAllBytes(p)));write(h,host);rehash(out,"host-admission/metadata/snapshot.json","host-admission.json");rejected(out);}
    @Test void truncatedTaxonMappingCannotAdmitAValidPrefix()throws Exception {Path out=extract(DNA);Path p=out.resolve("taxon-map.json");String text=Files.readString(p);Files.writeString(p,text.substring(0,text.lastIndexOf('\n')-2));rehash(out,"taxon-map.json");rejected(out);}
    @Test void rehashedLateResidueCoordinatesMustStillMatchEveryOriginalResidue()throws Exception {Path out=extract(DNA);Path p=out.resolve("residue-map.tsv");var lines=new ArrayList<>(Files.readAllLines(p));String[] last=lines.get(lines.size()-1).split("\t");last[6]="999";lines.set(lines.size()-1,String.join("\t",last));Files.writeString(p,String.join("\n",lines)+"\n");rehash(out,"residue-map.tsv");rejected(out);}
    @Test void anExtraRehashedResidueRowCannotHideAfterTheCompleteLedger()throws Exception {Path out=extract(DNA);Path p=out.resolve("residue-map.tsv");Files.writeString(p,Files.readAllLines(p).get(1)+"\n",StandardCharsets.UTF_8,StandardOpenOption.APPEND);rehash(out,"residue-map.tsv");rejected(out);}
    @Test void aDeletedInsertionCannotBeReclassifiedAsRetained()throws Exception {Path out=extract(KEEP);Path p=out.resolve("residue-map.tsv");String text=Files.readString(p);assertTrue(text.contains("SOURCE_DELETED_INSERTION"));Files.writeString(p,text.replaceFirst("SOURCE_DELETED_INSERTION","RETAINED"));rehash(out,"residue-map.tsv");rejected(out);}
    @Test void aRehashedTruncatedOriginalMapCannotHideTheLastAddedResidue()throws Exception {Path out=extract(KEEP);Path p=out.resolve("staged/added.fasta.map");var lines=new ArrayList<>(Files.readAllLines(p));lines.remove(lines.size()-1);Files.writeString(p,String.join("\n",lines)+"\n");rehash(out,"staged/added.fasta.map");rejected(out);}
    @Test void restoredLiteralResidueCaseIsCheckedBeyondCaseInsensitiveAlignmentEquality()throws Exception {Path out=extract(KEEP);Path p=out.resolve("alignment.fasta");String text=Files.readString(p);assertTrue(text.contains("ACc"));Files.writeString(p,text.replaceFirst("ACc","ACC"));rehash(out,"alignment.fasta");rejected(out);}
    @SuppressWarnings("unchecked") @Test void aRehashedCaseChangeCountCannotReplaceTheActualRetainedCells()throws Exception {Path out=extract(KEEP);Path p=out.resolve("output-audit.json");var j=json(p);var result=(Map<String,Object>)j.get("result");result.put("sourceCaseChanges",999);write(p,j);rehash(out,"output-audit.json");rejected(out);}
    @SuppressWarnings("unchecked") @Test void laterReturnTimingRemainsDistinctFromTheEarlierLedgerObservation()throws Exception {Path out=extract(DNA);Path p=out.resolve("output-audit.json");var j=json(p);var result=(Map<String,Object>)j.get("result");var process=(Map<String,Object>)result.get("process");process.put("elapsedMillis",((Long)process.get("elapsedMillis"))+1);write(p,j);Path s=out.resolve("session.json");var session=json(s);session.put("result",result);write(s,session);rehash(out,"output-audit.json","session.json");MafftBundle.verify(out);}
    @SuppressWarnings("unchecked") @Test void aReturnReceiptCannotPrecedeItsProcessLedger()throws Exception {Path out=extract(DNA);Path p=out.resolve("output-audit.json");var j=json(p);var result=(Map<String,Object>)j.get("result");((Map<String,Object>)result.get("process")).put("elapsedMillis",0);write(p,j);Path s=out.resolve("session.json");var session=json(s);session.put("result",result);write(s,session);rehash(out,"output-audit.json","session.json");rejected(out);}
    @SuppressWarnings("unchecked") @Test void rehashedRequestsCannotClaimThatGappedAddedInputWasForbidden()throws Exception {
        Path out=extract(KEEP);Path request=out.resolve("recipe-request.json"),replay=out.resolve("recipe-replay.json"),sessionPath=out.resolve("session.json");
        for(Path p:List.of(request,replay)){var j=json(p);j.put("addition_gaps","FORBID");write(p,j);}
        var session=json(sessionPath);var recipe=(Map<String,Object>)session.get("recipe");recipe.put("additionGaps","FORBID");var identity=(Map<String,Object>)recipe.get("identity");byte[] raw=Files.readAllBytes(request);identity.put("bytes",raw.length);identity.put("sha256",sha(raw));write(sessionPath,session);
        rehash(out,"recipe-request.json","recipe-replay.json","session.json");rejected(out);
    }
    @Test void oversizedFixedMetadataIsRejectedBeforeWholeJsonAllocation()throws Exception {
        Path out=extract(DNA);Path p=out.resolve("runtime-admission.json");byte[] block=new byte[8192];Arrays.fill(block,(byte)' ');
        try(var stream=Files.newOutputStream(p)){for(int i=0;i<2049;i++)stream.write(block);}
        rehash(out,"runtime-admission.json");rejected(out);
    }
}
