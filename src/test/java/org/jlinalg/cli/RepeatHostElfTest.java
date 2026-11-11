package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

class RepeatHostElfTest {
    @TempDir Path temp;
    private static final String INTERPRETER = "/lib64/ld-linux-x86-64.so.2";
    private byte[] fixture(boolean dynamic) {
        ByteBuffer b = ByteBuffer.allocate(512).order(ByteOrder.LITTLE_ENDIAN);
        b.put(new byte[]{127, 'E', 'L', 'F', 2, 1, 1}); b.putShort(16, (short)(dynamic?3:2)); b.putShort(18, (short)62); b.putInt(20, 1);
        b.putLong(32, 64); b.putShort(52, (short)64); b.putShort(54, (short)56); b.putShort(56, (short)(dynamic?3:1));
        b.putInt(64, 1); b.putLong(72, 0); b.putLong(96, b.capacity());
        if (dynamic) {
            byte[] path = (INTERPRETER + "\0").getBytes(StandardCharsets.UTF_8);
            b.putInt(120, 3); b.putLong(128, 240); b.putLong(152, path.length); b.position(240); b.put(path);
            b.putInt(176, 2); b.putLong(184, 288); b.putLong(208, 64); b.putLong(288, 1); b.putLong(296, 0);
            b.putLong(304, 5); b.putLong(312, 416); b.putLong(320, 10); b.putLong(328, 10); b.putLong(336, 0);
            b.position(416); b.put("libc.so.6\0".getBytes(StandardCharsets.UTF_8));
        }
        return b.array();
    }
    private RepeatHostElf.Image image(byte[] raw) throws IOException {
        Path path = temp.resolve("image"); Files.write(path, raw); return RepeatHostElf.image(path, 1000000, () -> { });
    }
    private RepeatHostElf.Report parse(RepeatHostElf.Image image, int exit, String out, String err) throws IOException {
        return RepeatHostElf.bindings(image, exit, out.getBytes(StandardCharsets.UTF_8), err.getBytes(StandardCharsets.UTF_8));
    }
    private String dynamicReport(String first, String second) {
        return "\tlinux-vdso.so.1 (0x" + first + ")\n\tlibc.so.6 => /lib/x86_64-linux-gnu/libc.so.6 (0x" + second + ")\n\t" + INTERPRETER + " (0x" + first + ")\n";
    }
    @Test void nonElfFilesRemainNonElfWithoutBeingRun() throws Exception {
        assertNull(image("#!/bin/sh\nexit 99\n".getBytes(StandardCharsets.UTF_8))); assertNull(image(new byte[0]));
    }
    @Test void dynamicMetadataRetainsInterpreterAndNeededEntryCount() throws Exception {
        var value = image(fixture(true)); assertEquals(3, value.type()); assertEquals(INTERPRETER, value.interpreter()); assertEquals(1, value.neededEntries()); assertEquals(List.of("libc.so.6"),value.neededNames());
    }
    @Test void staticMetadataDoesNotInventAnInterpreter() throws Exception {
        var value = image(fixture(false)); assertEquals(2, value.type()); assertNull(value.interpreter()); assertEquals(0, value.neededEntries());
    }
    @Test void truncatedAndUnsupportedHeadersAreRefused() throws Exception {
        assertThrows(IOException.class, () -> image(Arrays.copyOf(fixture(true), 50)));
        for (int field : List.of(4, 5, 6, 16, 18, 20, 52)) {
            byte[] raw = fixture(true); raw[field] = 0; assertThrows(IOException.class, () -> image(raw));
        }
    }
    @Test void programHeaderAndSegmentRangesCannotOverflowOrEscapeTheFile() throws Exception {
        for (int field : List.of(32, 72, 96, 128, 152, 184, 208)) {
            byte[] raw = fixture(true); ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).putLong(field, Long.MAX_VALUE); assertThrows(IOException.class, () -> image(raw));
        }
        for (int count : List.of(0, 4097, 65535)) {
            byte[] raw = fixture(true); ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).putShort(56, (short)count); assertThrows(IOException.class, () -> image(raw));
        }
    }
    @Test void interpreterMustBeCompleteAndUseAnExplicitLinuxPath() throws Exception {
        byte[] unterminated = fixture(true); unterminated[240 + INTERPRETER.length()] = 65; assertThrows(IOException.class, () -> image(unterminated));
        byte[] relative = fixture(true); relative[240] = '.'; assertThrows(IOException.class, () -> image(relative));
        byte[] embeddedNul = fixture(true); embeddedNul[244] = 0; assertThrows(IOException.class, () -> image(embeddedNul));
    }
    @Test void dynamicTableRequiresItsOwnCompleteTerminator() throws Exception {
        byte[] raw = fixture(true); ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).putLong(336, 12); assertThrows(IOException.class, () -> image(raw));
    }
    @Test void wholeFileCapacityIsCheckedBeforeMetadataInspection() throws Exception {
        Path path = temp.resolve("large"); Files.write(path, fixture(true)); assertThrows(IOException.class, () -> RepeatHostElf.image(path, 511, () -> { }));
    }
    @Test void reportedBindingsKeepNamesAndProvidersWhileDiscardingAslrAddresses() throws Exception {
        var metadata = image(fixture(true)); var first = parse(metadata, 0, dynamicReport("1234", "5678"), "");
        assertEquals(first, parse(metadata, 0, dynamicReport("9876", "abcd"), "")); assertTrue(first.virtualDso()); assertFalse(first.noLibraryBindings());
        assertEquals("libc.so.6", first.bindings().get(0).name()); assertEquals("/lib/x86_64-linux-gnu/libc.so.6", first.bindings().get(0).path()); assertTrue(first.bindings().get(1).loader());
    }
    @Test void theOriginalStaticProbeExitOneIsPreservedWithoutPromotingItToHealth() throws Exception {
        var metadata = image(fixture(false));
        assertTrue(parse(metadata, 1, "", "\tnot a dynamic executable\n").noLibraryBindings());
        assertTrue(parse(metadata, 0, "\tstatically linked\n", "").bindings().isEmpty());
        assertThrows(IOException.class, () -> parse(metadata, 2, "statically linked", ""));
    }
    @Test void aStaticReportCannotEraseNeededLibraries() throws Exception {
        var metadata = image(fixture(true)); assertThrows(IOException.class, () -> parse(metadata, 1, "", "not a dynamic executable"));
        assertThrows(IOException.class, () -> parse(metadata, 0, "statically linked", ""));
    }
    @Test void anExplicitInterpreterSurvivesAReportWithNoNeededLibraries() throws Exception {
        byte[] raw = fixture(true); ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).putLong(288, 0); var metadata = image(raw);
        assertEquals(INTERPRETER, metadata.interpreter()); assertTrue(parse(metadata, 1, "", "not a dynamic executable").noLibraryBindings());
    }
    @Test void missingUnknownAndUnterminatedClaimsCannotProducePartialBindings() throws Exception {
        var metadata = image(fixture(true));
        for (String out : List.of("libc.so.6 => not found\n", dynamicReport("1234", "5678") + "trailing unknown claim\n", "", INTERPRETER + " (0x1234)\n"))
            assertThrows(IOException.class, () -> parse(metadata, 0, out, ""));
        assertThrows(IOException.class, () -> parse(metadata, 1, dynamicReport("1234", "5678"), ""));
        assertThrows(IOException.class, () -> parse(metadata, 0, dynamicReport("1234", "5678"), "unexpected warning"));
    }
    @Test void duplicateLibrariesAndDuplicateVirtualDsosAreAmbiguous() throws Exception {
        var metadata = image(fixture(true)); String base = dynamicReport("1234", "5678");
        assertThrows(IOException.class, () -> parse(metadata, 0, base + "libc.so.6 => /another/libc.so.6 (0x9999)\n", ""));
        assertThrows(IOException.class, () -> parse(metadata, 0, base + "linux-vdso.so.1 (0x9999)\n", ""));
    }
    @Test void unsupportedPathsControlsAndInvalidUtf8AreRefusedAsCheckedErrors() throws Exception {
        var metadata = image(fixture(true));
        for (String path : List.of("relative.so", "/lib/x\0.so"))
            assertThrows(IOException.class, () -> parse(metadata, 0, "libc.so.6 => " + path + " (0x1234)\n", ""));
        assertThrows(IOException.class, () -> parse(metadata, 0, "lib\0c.so => /lib/libc.so (0x1234)\n", ""));
        assertThrows(IOException.class, () -> RepeatHostElf.bindings(metadata, 0, new byte[]{-61, 40}, new byte[0]));
    }
    @Test void bindingOutputAndPopulationAreBounded() throws Exception {
        var metadata = image(fixture(true)); assertThrows(IOException.class, () -> RepeatHostElf.bindings(metadata, 0, new byte[1048577], new byte[0]));
        var text = new StringBuilder(); for (int i = 0; i < 4097; i++) text.append("lib").append(i).append(".so => /lib/lib").append(i).append(".so (0x1234)\n");
        assertThrows(IOException.class, () -> parse(metadata, 0, text.toString(), ""));
    }
    @Test void aRequiredLoaderCanAppearAsADirectBindingWithoutANameArrow() throws Exception {
        byte[] raw=fixture(true);byte[] name="ld-linux-x86-64.so.2\0".getBytes(StandardCharsets.UTF_8);
        System.arraycopy(name,0,raw,416,name.length);ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).putLong(328,name.length);
        var metadata=image(raw);var report=parse(metadata,0,INTERPRETER+" (0x1234)\nlinux-vdso.so.1 (0x5678)\n","");
        assertEquals(List.of("ld-linux-x86-64.so.2"),metadata.neededNames());assertEquals(1,report.bindings().size());assertTrue(report.bindings().get(0).loader());
    }
    @Test void virtualOnlyReportsRequireNoDeclaredFileLibraries() throws Exception {
        assertTrue(parse(image(fixture(false)),0,"linux-vdso.so.1 (0x1234)\n","").noLibraryBindings());
        assertThrows(IOException.class,()->parse(image(fixture(true)),0,"linux-vdso.so.1 (0x1234)\n",""));
    }
    @Test void neededNamesUseTheVirtualToFileMappingAndRemainNulTerminated() throws Exception {
        byte[] raw=fixture(true);ByteBuffer b=ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);b.putLong(80,0x400000L);b.putLong(312,0x400000L+416);
        assertEquals(List.of("libc.so.6"),image(raw).neededNames());
        for(int field:List.of(296,312,328)){
            byte[] invalid=fixture(true);ByteBuffer.wrap(invalid).order(ByteOrder.LITTLE_ENDIAN).putLong(field,Long.MAX_VALUE);assertThrows(IOException.class,()->image(invalid));
        }
        byte[] unterminated=fixture(true);unterminated[425]=65;assertThrows(IOException.class,()->image(unterminated));
    }
    @Test void everyDeclaredNeededNameMustBePresentInTheCompleteReport() throws Exception {
        byte[] raw=fixture(true);ByteBuffer b=ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);b.putLong(208,80);
        b.putLong(304,1);b.putLong(312,10);b.putLong(320,5);b.putLong(328,416);b.putLong(336,10);b.putLong(344,20);b.putLong(352,0);
        byte[] names="libc.so.6\0libm.so.6\0".getBytes(StandardCharsets.UTF_8);System.arraycopy(names,0,raw,416,names.length);var metadata=image(raw);
        assertEquals(List.of("libc.so.6","libm.so.6"),metadata.neededNames());assertThrows(IOException.class,()->parse(metadata,0,dynamicReport("1234","5678"),""));
        assertEquals(3,parse(metadata,0,dynamicReport("1234","5678")+"libm.so.6 => /lib/libm.so.6 (0xabcd)\n","").bindings().size());
    }
    @Test void originRelativeDirectoryComponentsRemainInTheOriginalReportedSpelling() throws Exception {
        var metadata=image(fixture(true));
        for(String path:List.of("/runtime/package/../libraries/libc.so.6","/runtime//libraries/libc.so.6")){
            var report=parse(metadata,0,"libc.so.6 => "+path+" (0x1234)\n","");assertEquals(path,report.bindings().get(0).path());
        }
    }
    @Test @EnabledOnOs(OS.LINUX) void actualHostImageAndOwnedLibraryProbePreserveTheirIndependentEvidence() throws Exception {
        Path binary = Path.of("/usr/bin/true").toRealPath(); var metadata = RepeatHostElf.image(binary, 1048576, () -> { }); assertNotNull(metadata); assertTrue(metadata.neededEntries() > 0);
        var run = RepeatProcess.inspectLibraries(binary, Map.of("PATH", "/usr/bin:/bin", "LANG", "C.UTF-8", "LC_ALL", "C.UTF-8"), temp.resolve("probe"), RepeatProcess.Limits.health(), () -> { }, () -> { }, List.of());
        assertEquals(0, run.exitCode()); assertTrue(run.ownedLiveAfterCleanup().isEmpty());
        var report = RepeatHostElf.bindings(metadata, run.exitCode(), Files.readAllBytes(run.stdout()), Files.readAllBytes(run.stderr())); assertFalse(report.bindings().isEmpty());
        String ledger = Files.readString(run.ledger()); assertTrue(ledger.contains("library-binding-inspection-only")); assertTrue(ledger.contains("accepted_process_exit_codes"));
    }
    @Test @EnabledOnOs(OS.LINUX) void ownedStaticProbeExitOneDoesNotRelaxNormalProcessSuccessRequirements() throws Exception {
        Path text = temp.resolve("plain-text"); Files.writeString(text, "controlled non-ELF input\n");
        var run = RepeatProcess.inspectLibraries(text, Map.of("PATH", "/usr/bin:/bin", "LANG", "C.UTF-8", "LC_ALL", "C.UTF-8"), temp.resolve("inspection"), RepeatProcess.Limits.health(), () -> { }, () -> { }, List.of());
        assertEquals(1, run.exitCode()); assertTrue(run.ownedLiveAfterCleanup().isEmpty());
        assertThrows(IOException.class, () -> RepeatProcess.executeOwned(List.of("/usr/bin/ldd", text.toString()), Map.of("PATH", "/usr/bin:/bin"), temp.resolve("normal"), RepeatProcess.Limits.health(), () -> { }, () -> { }));
        assertTrue(Files.readString(temp.resolve("normal/process.json")).contains("FAIL2-retained-no-result"));
    }
}
