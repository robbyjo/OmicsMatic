/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class TomtomCapturedReportsTest {
    @TempDir Path temporary;
    static Stream<String> cases()throws IOException {return records(archive()).stream().map(r->(String)r.get("id"));}
    @ParameterizedTest @MethodSource("cases")
    void everyCompleteOriginalCapturedInputTextAndHtmlDisplayFieldPassesPortably(String id)throws Exception {
        var f=fixture(id);var inputs=TomtomCapturedInputs.read(f.root().resolve("prepared"),(String)f.record().get("producer_input_root"),()->{});var result=TomtomOutput.read(f.root().resolve("process"),inputs,NextflowFiles.strings(f.record().get("command")),()->{});
        assertEquals(number(f.record(),"rows"),result.matches().size());assertEquals(number(f.record(),"pairs"),result.pairs().size());
        Path pairs=f.root().resolve("pairs.tsv");TomtomOutput.writePairs(pairs,result,()->{});TomtomOutput.verifyPairs(pairs,result,()->{});
        if(f.record().get("mode").equals("FULL_REPORT"))assertTrue((Boolean)NextflowFiles.object(result.document().get("HTML")).get("all_original_display_data_matches_audited_XML"));else assertEquals(Map.of(),result.document().get("HTML"));
    }
    @Test void fullReportDisplayMetadataAndNumericChangesCannotHideBehindAnUnchangedXmlTable()throws Exception {
        var f=fixture("metadata-url-FULL_REPORT");var inputs=TomtomCapturedInputs.read(f.root().resolve("prepared"),(String)f.record().get("producer_input_root"),()->{});Path html=f.root().resolve("process/result/tomtom.html");String original=Files.readString(html);
        for(String changed:List.of(original.replace("https://example.test/motif?x=1&y=2","https://example.test/changed"),original.replaceFirst("\"nsites\": 20.5","\"nsites\": 21.5"),original.replaceFirst("\"pv\": \"[^\"]+\"","\"pv\": \"0.5\""))){assertNotEquals(original,changed);Files.writeString(html,changed);assertThrows(IOException.class,()->TomtomOutput.read(f.root().resolve("process"),inputs,NextflowFiles.strings(f.record().get("command")),()->{}));}
    }
    @Test void truncatedHtmlUnknownDataAndMissingRequestedAssetFailWholeReport()throws Exception {
        var f=fixture("mass-1.0-FULL_REPORT");var inputs=TomtomCapturedInputs.read(f.root().resolve("prepared"),(String)f.record().get("producer_input_root"),()->{});var command=NextflowFiles.strings(f.record().get("command"));Path html=f.root().resolve("process/result/tomtom.html");String original=Files.readString(html);
        for(String changed:List.of(original.substring(0,original.length()-1),original.replace("var data = {","var data = {\"unknown\":true,"),original.replace("// @JSON_VAR data","// removed data marker"))){assertNotEquals(original,changed);Files.writeString(html,changed);assertThrows(IOException.class,()->TomtomOutput.read(f.root().resolve("process"),inputs,command,()->{}));}
        Files.writeString(html,original);Files.delete(html);assertThrows(IOException.class,()->TomtomOutput.read(f.root().resolve("process"),inputs,command,()->{}));
    }
    @Test void rawCapturedBytesAndEveryMotifMetadataDispositionRemainBound()throws Exception {
        var f=fixture("zero-sites-TEXT");Path ledger=f.root().resolve("prepared/motif-ledger.jsonl");String original=Files.readString(ledger);String changed=original.replace("\"pseudocount_sites\":20","\"pseudocount_sites\":21");assertNotEquals(original,changed);Files.writeString(ledger,changed);assertThrows(IOException.class,()->TomtomCapturedInputs.read(f.root().resolve("prepared"),(String)f.record().get("producer_input_root"),()->{}));
        Files.writeString(ledger,original);var context=TomtomCapturedInputs.read(f.root().resolve("prepared"),(String)f.record().get("producer_input_root"),()->{});Path raw=f.root().resolve("prepared").resolve(context.files().get(0).member());Files.writeString(raw,Files.readString(raw)+"\n");assertThrows(IOException.class,()->TomtomCapturedInputs.read(f.root().resolve("prepared"),(String)f.record().get("producer_input_root"),()->{}));
    }
    @Test void unreportedPairsKeepUnavailableValuesAndCannotAcquireZeros()throws Exception {
        var f=fixture("paper-P067-text-scoped-current-edition");var inputs=TomtomCapturedInputs.read(f.root().resolve("prepared"),(String)f.record().get("producer_input_root"),()->{});var result=TomtomOutput.read(f.root().resolve("process"),inputs,NextflowFiles.strings(f.record().get("command")),()->{});assertEquals(2,result.matches().size());assertEquals(366,result.pairs().size());Path ledger=f.root().resolve("pairs.tsv");TomtomOutput.writePairs(ledger,result,()->{});String text=Files.readString(ledger);assertTrue(text.contains("false\tNA\tNA\tNA"));Files.writeString(ledger,text.replaceFirst("false\tNA","false\t0"));assertThrows(IOException.class,()->TomtomOutput.verifyPairs(ledger,result,()->{}));
    }
    @Test void unknownOutputFilesAndEmptyDirectoriesAreRejected()throws Exception {
        var f=fixture("mass-1.0-TEXT");var inputs=TomtomCapturedInputs.read(f.root().resolve("prepared"),(String)f.record().get("producer_input_root"),()->{});Files.createDirectory(f.root().resolve("process/extra"));assertThrows(IOException.class,()->TomtomOutput.read(f.root().resolve("process"),inputs,NextflowFiles.strings(f.record().get("command")),()->{}));
    }
    @Test void cancellationReturnsNoCompleteHtmlReceipt()throws Exception {
        var f=fixture("mass-1.0-FULL_REPORT");var inputs=TomtomCapturedInputs.read(f.root().resolve("prepared"),(String)f.record().get("producer_input_root"),()->{});var calls=new AtomicInteger();assertThrows(CancellationException.class,()->TomtomOutput.read(f.root().resolve("process"),inputs,NextflowFiles.strings(f.record().get("command")),()->{if(calls.incrementAndGet()==100)throw new CancellationException("controlled");}));
    }
    private record Fixture(Path root,Map<?,?> record) { }
    private Fixture fixture(String id)throws IOException {
        var files=archive();var row=records(files).stream().filter(r->r.get("id").equals(id)).findFirst().orElseThrow();Path root=temporary.resolve(id);Files.createDirectory(root);
        for(var entry:files.entrySet())if(entry.getKey().startsWith(id+"/")){Path path=root.resolve(entry.getKey().substring(id.length()+1));Files.createDirectories(path.getParent());Files.write(path,entry.getValue());}return new Fixture(root,row);
    }
    private static long number(Map<?,?> row,String key){return ((Number)row.get(key)).longValue();}
    static Map<String,byte[]> archive()throws IOException {byte[] raw;try(var in=TomtomCapturedReportsTest.class.getResourceAsStream("/org/jlinalg/cli/tomtom-reports/original-prepared-reports.zip")){assertNotNull(in);raw=in.readAllBytes();}assertEquals("f7e0cfc33105f2f6b9e40352ea91ba9bb1eebc896cf46f670e2c5cbe1150f1e7",NextflowFiles.sha(raw));var files=new LinkedHashMap<String,byte[]>();try(var z=new ZipInputStream(new java.io.ByteArrayInputStream(raw))){for(var e=z.getNextEntry();e!=null;e=z.getNextEntry()){assertFalse(e.isDirectory());assertNull(files.put(e.getName(),z.readAllBytes()));}}return files;}
    private static List<Map<?,?>> records(Map<String,byte[]> files)throws IOException {var expected=NextflowFiles.object(SimpleJson.parse(new String(files.get("EXPECTED.json"),StandardCharsets.UTF_8)));var rows=new ArrayList<Map<?,?>>();for(Object value:NextflowFiles.list(expected.get("cases")))rows.add(NextflowFiles.object(value));return List.copyOf(rows);}
}
