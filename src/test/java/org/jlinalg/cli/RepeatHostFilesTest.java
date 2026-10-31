package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

class RepeatHostFilesTest {
    @TempDir Path temp;
    private final RepeatHostFiles.Limits limits = new RepeatHostFiles.Limits(100000, 1000000, 1000, 16, 1048576);
    private RepeatHostFiles.Snapshot capture(List<Path> roots, List<Path> files) throws IOException {
        return RepeatHostFiles.capture(roots, files, Set.of(temp), Set.of(temp), limits, () -> { });
    }
    private void verify(RepeatHostFiles.Snapshot snapshot) throws IOException {
        RepeatHostFiles.verify(snapshot, Set.of(temp), Set.of(temp), limits, () -> { });
    }
    private Path data(Path path, String text) throws IOException { Files.writeString(path, text); return path; }
    private void symlink(Path link, Path target) throws IOException {
        try { Files.createSymbolicLink(link, target); }
        catch (IOException | UnsupportedOperationException unavailable) {
            if (System.getProperty("os.name").startsWith("Windows"))
                Assumptions.abort("Windows host needs symbolic-link privilege for this filesystem probe");
            throw unavailable;
        }
    }
    @Test void completeCensusIncludesEmptyDirectoriesHiddenFilesAndUnicodeNames() throws Exception {
        Path root = Files.createDirectory(temp.resolve("modules")); Files.createDirectory(root.resolve("empty"));
        Path sub = Files.createDirectory(root.resolve("sub")); data(sub.resolve("λ.pm"), "abc"); data(root.resolve(".hidden"), "12345");
        var snapshot = capture(List.of(root), List.of());
        assertEquals(Set.of(".", "empty", "sub", Path.of("sub", "λ.pm").toString(), ".hidden"), snapshot.trees().get(root.toString()).keySet());
        assertEquals(8, snapshot.logicalBytes());
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", snapshot.trees().get(root.toString()).get(Path.of("sub", "λ.pm").toString()).file().sha256());
        assertTrue(RepeatHostFiles.bytes(snapshot, limits).length <= snapshot.metadataReservation()); verify(snapshot);
    }
    @Test void hashingRetainsBinaryBytesWithoutTextNormalization() throws Exception {
        Path path = temp.resolve("binary"); byte[] raw = {0, -1, 13, 10, 65, 10}; Files.write(path, raw);
        var snapshot = capture(List.of(), List.of(path));
        assertEquals(AdmixtureInputs.digest(raw), snapshot.files().get(path.toString()).file().sha256()); assertEquals(raw.length, snapshot.logicalBytes()); verify(snapshot);
    }
    @Test void missingDefaultRootsAndOptionalConfigurationsHaveExplicitStates() throws Exception {
        Path root = temp.resolve("missing-root"), file = temp.resolve("missing-config"); var snapshot = capture(List.of(root), List.of(file));
        assertEquals("ABSENT", snapshot.roots().get(root.toString()).kind()); assertEquals("ABSENT", snapshot.files().get(file.toString()).kind()); assertTrue(snapshot.trees().isEmpty()); verify(snapshot);
        Files.createDirectory(root); assertThrows(IOException.class, () -> verify(snapshot));
    }
    @Test void newlyAppearingOptionalConfigurationInvalidatesTheContext() throws Exception {
        Path path = temp.resolve("configuration"); var snapshot = capture(List.of(), List.of(path)); data(path, "new policy"); assertThrows(IOException.class, () -> verify(snapshot));
    }
    @Test void aNewModuleInvalidatesTheCompleteMembership() throws Exception {
        Path root = Files.createDirectory(temp.resolve("modules")); data(root.resolve("a.pm"), "abc"); var snapshot = capture(List.of(root), List.of());
        data(root.resolve("later.pm"), "abc"); assertThrows(IOException.class, () -> verify(snapshot));
    }
    @Test void removalOfAnEmptyDirectoryIsNotHiddenByFileOnlyFingerprints() throws Exception {
        Path root = Files.createDirectory(temp.resolve("modules")); Path empty = Files.createDirectory(root.resolve("empty")); var snapshot = capture(List.of(root), List.of());
        Files.delete(empty); assertThrows(IOException.class, () -> verify(snapshot));
    }
    @Test void completeBytesAreReadAgainEvenUnderAnEnclosingFingerprintPass() throws Exception {
        Path path = data(temp.resolve("member"), "first"); var time = Files.getLastModifiedTime(path);
        try (var pass = RepeatFingerprint.enter()) {
            pass.requireActive(); var before = capture(List.of(), List.of(path)); data(path, "other"); Files.setLastModifiedTime(path, time);
            var after = capture(List.of(), List.of(path)); assertNotEquals(before.files(), after.files()); assertTrue(after.physicalHashes() >= 1); assertThrows(IOException.class, () -> verify(before));
        }
    }
    @Test void regularFileRemovalIsNotMisreportedAsAnUnchangedAbsentRoot() throws Exception {
        Path path = data(temp.resolve("member"), "abc"); var snapshot = capture(List.of(), List.of(path)); Files.delete(path); assertThrows(IOException.class, () -> verify(snapshot));
    }
    @Test void relativeAndUnnormalizedRequestsAreRejected() {
        assertThrows(IOException.class, () -> capture(List.of(Path.of("relative")), List.of()));
        assertThrows(IOException.class, () -> capture(List.of(temp.resolve("child").resolve("..")), List.of()));
    }
    @Test void duplicateRequestsCannotHideRootMultiplicity() {
        assertThrows(IOException.class, () -> capture(List.of(temp, temp), List.of()));
    }
    @Test void directoriesAndFilesCannotExchangeDeclaredRoles() throws Exception {
        Path path = data(temp.resolve("file"), "abc");
        assertThrows(IOException.class, () -> capture(List.of(path), List.of())); assertThrows(IOException.class, () -> capture(List.of(), List.of(temp)));
    }
    @Test void perFileCapacityIsCheckedBeforeReadingTheCompleteContent() throws Exception {
        Path path = data(temp.resolve("file"), "123456"); var small = new RepeatHostFiles.Limits(5, 1000, 100, 4, 100000);
        assertThrows(IOException.class, () -> RepeatHostFiles.capture(List.of(), List.of(path), Set.of(temp), Set.of(temp), small, () -> { }));
    }
    @Test void aHardlinkAliasStillConsumesItsFullLogicalByteCapacity() throws Exception {
        Path first = data(temp.resolve("first"), "12345"), second = temp.resolve("second"); Files.createLink(second, first);
        var snapshot = capture(List.of(), List.of(first, second)); assertEquals(10, snapshot.logicalBytes());
        var small = new RepeatHostFiles.Limits(100, 9, 100, 4, 100000);
        assertThrows(IOException.class, () -> RepeatHostFiles.capture(List.of(), List.of(first, second), Set.of(temp), Set.of(temp), small, () -> { }));
    }
    @Test void completeMemberAndMetadataReservationsBoundTheInventory() throws Exception {
        Path root = Files.createDirectory(temp.resolve("root")); data(root.resolve("member"), "abc");
        for (var small : List.of(new RepeatHostFiles.Limits(100, 1000, 2, 4, 100000), new RepeatHostFiles.Limits(100, 1000, 100, 4, 1024)))
            assertThrows(IOException.class, () -> RepeatHostFiles.capture(List.of(root), List.of(), Set.of(temp), Set.of(temp), small, () -> { }));
    }
    @Test void linkedDirectoryGraphIsCompleteAndCyclesDoNotCauseUnboundedTraversal() throws Exception {
        Path a = Files.createDirectory(temp.resolve("a")), b = Files.createDirectory(temp.resolve("b")); data(a.resolve("a.pm"), "abc"); data(b.resolve("b.pm"), "12345");
        symlink(a.resolve("to-b"), b); symlink(b.resolve("to-a"), a);
        var snapshot = capture(List.of(a), List.of()); assertEquals(Set.of(a.toString(), b.toString()), snapshot.trees().keySet()); assertEquals(8, snapshot.logicalBytes()); verify(snapshot);
        data(b.resolve("added.pm"), "later"); assertThrows(IOException.class, () -> verify(snapshot));
    }
    @Test void implicitLinkedRootsAlsoConsumeTheRootCapacity() throws Exception {
        Path a = Files.createDirectory(temp.resolve("a")), b = Files.createDirectory(temp.resolve("b")); symlink(a.resolve("to-b"), b);
        var small = new RepeatHostFiles.Limits(100, 1000, 100, 1, 100000);
        assertThrows(IOException.class, () -> RepeatHostFiles.capture(List.of(a), List.of(), Set.of(temp), Set.of(temp), small, () -> { }));
    }
    @Test void fileSymlinkRetargetingWithIdenticalBytesStillChangesTheBinding() throws Exception {
        Path a = data(temp.resolve("a"), "abc"), b = data(temp.resolve("b"), "abc"), link = temp.resolve("alias"); symlink(link, a);
        var snapshot = capture(List.of(), List.of(link)); assertEquals(a.toString(), snapshot.files().get(link.toString()).realPath());
        Files.delete(link); symlink(link, b); assertThrows(IOException.class, () -> verify(snapshot));
    }
    @Test void danglingLinksAreRecordedAndReDerivedWhenTheyResolve() throws Exception {
        Path target = temp.resolve("future"), link = temp.resolve("alias"); symlink(link, target);
        var snapshot = capture(List.of(link), List.of()); assertEquals("SYMLINK", snapshot.roots().get(link.toString()).kind()); assertEquals("ABSENT", snapshot.roots().get(link.toString()).targetKind()); verify(snapshot);
        Files.createDirectory(target); assertThrows(IOException.class, () -> verify(snapshot));
    }
    @Test void moduleLinksCannotEscapeTheirOwnPolicyThroughBroaderHelperFilePermissions() throws Exception {
        Path root = Files.createDirectory(temp.resolve("modules")), outside = data(temp.resolve("outside"), "private"); symlink(root.resolve("alias"), outside);
        assertThrows(IOException.class, () -> RepeatHostFiles.capture(List.of(root), List.of(), Set.of(root), Set.of(temp), limits, () -> { }));
    }
    @Test void absentRootsOutsideDeclaredTargetsAreAlsoRejected() {
        Path allowed = temp.resolve("allowed"); assertThrows(IOException.class, () -> RepeatHostFiles.capture(List.of(temp.resolve("outside")), List.of(), Set.of(allowed), Set.of(temp), limits, () -> { }));
    }
    @Test void aPopulationMutationDuringTheCensusIsRetainedAsFailure() throws Exception {
        Path root = Files.createDirectory(temp.resolve("modules")); data(root.resolve("a"), "abc"); FileTime time = Files.getLastModifiedTime(root); AtomicInteger calls = new AtomicInteger();
        assertThrows(IOException.class, () -> RepeatHostFiles.capture(List.of(root), List.of(), Set.of(temp), Set.of(temp), limits, () -> {
            if (calls.incrementAndGet() == 3) try { data(root.resolve("late"), "abc"); Files.setLastModifiedTime(root, FileTime.fromMillis(time.toMillis() + 2000)); }
            catch (IOException failure) { throw new UncheckedIOException(failure); }
        }));
    }
    @Test void cancellationCannotProduceACompletedSnapshot() {
        assertThrows(CancellationException.class, () -> RepeatHostFiles.capture(List.of(temp), List.of(), Set.of(temp), Set.of(temp), limits, () -> { throw new CancellationException("controlled cancellation"); }));
    }
    @Test void snapshotPopulationsCannotBeMutatedByTheCaller() throws Exception {
        var snapshot = capture(List.of(temp), List.of()); assertThrows(UnsupportedOperationException.class, () -> snapshot.trees().clear());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.trees().get(temp.toString()).clear()); assertThrows(UnsupportedOperationException.class, () -> snapshot.requestedRoots().clear());
    }
    @Test @EnabledOnOs(OS.LINUX) void modeChangesInvalidateTheContextEvenWhenRootCanStillReadTheFile() throws Exception {
        Path path = data(temp.resolve("file"), "abc"); var snapshot = capture(List.of(), List.of(path)); var permissions = new java.util.HashSet<>(Files.getPosixFilePermissions(path));
        if (!permissions.add(PosixFilePermission.OTHERS_EXECUTE)) permissions.remove(PosixFilePermission.OTHERS_EXECUTE);
        Files.setPosixFilePermissions(path, permissions); assertThrows(IOException.class, () -> verify(snapshot));
    }
    @Test @EnabledOnOs(OS.LINUX) void aSocketCannotBeAdmittedOrReadAsAModuleFile() throws Exception {
        Path socket = temp.resolve("socket");
        try (var channel = ServerSocketChannel.open(StandardProtocolFamily.UNIX)) {
            channel.bind(UnixDomainSocketAddress.of(socket)); assertThrows(IOException.class, () -> capture(List.of(temp), List.of()));
        }
    }
    @Test @EnabledOnOs(OS.LINUX) void specialDirectoryModeBitsArePartOfTheRetainedIdentity() throws Exception {
        Path root=Files.createDirectory(temp.resolve("modules"));var snapshot=capture(List.of(root),List.of());
        int before=((Number)Files.getAttribute(root,"unix:mode")).intValue();Files.setAttribute(root,"unix:mode",before|01000);
        assertThrows(IOException.class,()->verify(snapshot));
    }
    @Test void declaredDirectoryNodesRetainPresenceWithoutReadingUnrelatedContents() throws Exception {
        Path directory=Files.createDirectory(temp.resolve("logs"));Files.write(directory.resolve("unrelated-large-log"),new byte[200000]);
        var snapshot=RepeatHostFiles.capture(List.of(),List.of(),List.of(directory),Set.of(temp),Set.of(temp),limits,()->{});
        assertEquals("DIRECTORY",snapshot.nodes().get(directory.toString()).kind());assertTrue(snapshot.trees().isEmpty());assertEquals(0,snapshot.logicalBytes());verify(snapshot);
    }
    @Test void appearingMissingNodesAndChangingNodeTypesInvalidateTheContext() throws Exception {
        Path directory=temp.resolve("logs");var missing=RepeatHostFiles.capture(List.of(),List.of(),List.of(directory),Set.of(temp),Set.of(temp),limits,()->{});
        Files.createDirectory(directory);assertThrows(IOException.class,()->verify(missing));
        var present=RepeatHostFiles.capture(List.of(),List.of(),List.of(directory),Set.of(temp),Set.of(temp),limits,()->{});
        Files.delete(directory);data(directory,"changed to file");assertThrows(IOException.class,()->verify(present));
    }
    @Test void regularNodesStillConsumeTheirCompleteBytes() throws Exception {
        Path path=data(temp.resolve("configuration"),"abc");var snapshot=RepeatHostFiles.capture(List.of(),List.of(),List.of(path),Set.of(temp),Set.of(temp),limits,()->{});
        assertEquals(3,snapshot.logicalBytes());assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",snapshot.nodes().get(path.toString()).file().sha256());verify(snapshot);
    }
}
