package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

class RepeatHostTest {
    @TempDir Path temp;
    private byte[] utf8(String text) { return text.getBytes(StandardCharsets.UTF_8); }
    private Map<String,Object> python() {
        return new LinkedHashMap<>(Map.of("stdlib","/usr/lib/python3.12","paths",List.of("/usr/lib/python312.zip","/usr/lib/python3.12","/usr/lib/python3.12/lib-dynload"),"version","3.12.3"));
    }
    @Test void perlDiscoveryPreservesSearchOrderAliasesAndCompleteUnicodePaths() throws Exception {
        assertEquals(List.of("/etc/perl","/usr/share/perl/5.38","/usr/share/perl/5.38","/usr/local/share/模块"),RepeatHost.perlRoots(utf8("/etc/perl\n/usr/share/perl/5.38\n/usr/share/perl/5.38\n/usr/local/share/模块\n")));
    }
    @Test void truncatedRelativeHookAndNonNormalizedPerlRootsAreRefused() {
        for(String text:List.of("/usr/lib","",".\n","relative\n","CODE(0x1234)\n","/usr/../lib\n","/usr//lib\n","/usr/lib\r\n","/usr/x\0\n"))
            assertThrows(IOException.class,()->RepeatHost.perlRoots(utf8(text)));
    }
    @Test void completeDiscoveryTextAndRootCountsAreBounded() {
        assertThrows(IOException.class,()->RepeatHost.perlRoots(utf8("/usr/lib\n".repeat(65))));
        assertThrows(IOException.class,()->RepeatHost.perlRoots(new byte[1048577]));
        assertThrows(IOException.class,()->RepeatHost.perlRoots(new byte[]{-61,40}));
    }
    @Test void isolatedPythonDiscoveryRetainsThePotentialZipImportBeforeTheStdlib() throws Exception {
        var result=RepeatHost.pythonPaths(RepeatBundle.json(python()));assertEquals("3.12.3",result.version());
        assertEquals("/usr/lib/python312.zip",result.paths().get(0));assertEquals("/usr/lib/python3.12",result.stdlib());
        assertThrows(UnsupportedOperationException.class,()->result.paths().clear());
    }
    @Test void anotherPythonVersionOrAnIncompleteDiscoveryIsNotSubstituted() {
        for(String key:List.of("stdlib","paths","version")){
            var value=python();value.remove(key);assertThrows(IOException.class,()->RepeatHost.pythonPaths(RepeatBundle.json(value)));
        }
        var other=python();other.put("version","3.12.4");assertThrows(IOException.class,()->RepeatHost.pythonPaths(RepeatBundle.json(other)));
        var extra=python();extra.put("site_packages","/unreviewed");assertThrows(IOException.class,()->RepeatHost.pythonPaths(RepeatBundle.json(extra)));
    }
    @Test void PythonDefaultPathsCannotSmuggleCwdOrNonPathEntries() {
        for(Object item:List.of("", ".", "relative", "/usr/../lib", "/usr//lib", 42)){
            var value=python();value.put("paths",List.of(item));assertThrows(IOException.class,()->RepeatHost.pythonPaths(RepeatBundle.json(value)));
        }
        var empty=python();empty.put("paths",List.of());assertThrows(IOException.class,()->RepeatHost.pythonPaths(RepeatBundle.json(empty)));
    }
    @Test void malformedUtf8IsRejectedByCompleteDiscoveryDecoding() {
        byte[] text=utf8("{\"stdlib\":\"/usr/lib/python3.12\",\"paths\":[\"/usr/lib/x\"],\"version\":\"3.12.3\"}");
        int position=new String(text,StandardCharsets.UTF_8).indexOf("/usr/lib/x")+9;text[position]=(byte)0xff;
        assertThrows(IOException.class,()->RepeatHost.pythonPaths(text));
    }
    @Test void hostProbeCountAndDiagnosticCapacitiesMustBeExplicitAndBounded() {
        var files=new RepeatHostFiles.Limits(1048576,134217728,10000,64,16777216);var process=RepeatProcess.Limits.health();
        assertThrows(IllegalArgumentException.class,()->new RepeatHost.Limits(files,process,1));
        assertThrows(IllegalArgumentException.class,()->new RepeatHost.Limits(files,process,2049));
        assertEquals(512,new RepeatHost.Limits(files,process,512).probes());
    }
    @Test @EnabledOnOs(OS.WINDOWS) void unavailableOriginalHostAdmissionDoesNotCreateOrInstallAnything() {
        Path output=temp.resolve("new-host");var files=new RepeatHostFiles.Limits(1048576,134217728,10000,64,16777216);
        assertThrows(RepeatProcess.UnavailableDependency.class,()->RepeatHost.capture(null,output,new RepeatHost.Limits(files,RepeatProcess.Limits.health(),512),()->{}));assertFalse(Files.exists(output));
    }
}
