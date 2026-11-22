package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlinkRuntimeTest {
    @TempDir Path directory;
    private Path home() {
        String value=System.getenv("JLINALG_PLINK_HOME");
        Assumptions.assumeTrue(value!=null && Files.isRegularFile(Path.of(value,".jlinalg-plink.json")),"Set JLINALG_PLINK_HOME to the permitted sealed original PLINK profile");
        return Path.of(value);
    }
    private Path work(String name)throws Exception { return Files.createDirectory(directory.resolve(name)); }
    private Path copyHome()throws Exception {
        Path copy=work("copied-home");try(var paths=Files.walk(home())) {
            for(Path path:paths.toList()) {
                Path target=copy.resolve(home().relativize(path));if(Files.isDirectory(path))Files.createDirectories(target);else Files.copy(path,target);
            }
        }return copy;
    }
    @Test void missingRuntimeAndInvalidEngineCannotLaunch()throws Exception {
        assertThrows(PlinkRuntime.Unavailable.class,()->new PlinkRuntime(directory.resolve("absent"),"plink1",directory,new LinkedHashMap<>()));
        assertFalse(Files.exists(directory.resolve("version.stdout")));
        assertThrows(IllegalArgumentException.class,()->new PlinkRuntime(directory,"unrecognized",directory,new LinkedHashMap<>()));
        assertTrue(PlinkRuntime.setup().contains("--allow-install"));assertTrue(PlinkRuntime.setup().contains("exit 3"));
    }
    @Test void actualBothVersionsAndOriginalFrequencyOutputsAreRetained()throws Exception {
        for(String engine:List.of("plink1","plink2")) {
            Path out=work(engine);var session=new LinkedHashMap<String,Object>();var runtime=new PlinkRuntime(home(),engine,out,session);
            assertEquals(engine,runtime.engine());
            // Four diploid samples, A1 counts 2,1,0,missing; one variant-major BED row.
            Files.write(out.resolve("input.bed"),new byte[]{0x6c,0x1b,1,0x78});
            Files.writeString(out.resolve("input.bim"),"1 rs1 0 100 C A\n");
            Files.writeString(out.resolve("input.fam"),"F1 I 0 0 1 -9\nF2 I 0 0 2 -9\nF3 I 0 0 1 -9\nF4 I 0 0 2 -9\n");
            runtime.execute(List.of("--bfile","input","--threads","1","--memory",engine.equals("plink1")?"128":"640","--freq","--out","result"),out,"frequency",30);
            String table=Files.readString(out.resolve(engine.equals("plink1")?"result.frq":"result.afreq"));assertTrue(table.contains("rs1"));
            String[] fields=table.lines().skip(1).findFirst().orElseThrow().strip().split("\\s+");assertEquals(.5,Double.parseDouble(fields[fields.length-2]));assertEquals(6,Integer.parseInt(fields[fields.length-1]));
            assertEquals(0,session.get("frequency_exit_code"));assertTrue(String.valueOf(session.get("upstream_version")).startsWith("PLINK v"));
            assertTrue(Files.isRegularFile(out.resolve("frequency.stdout")));assertTrue(Files.isRegularFile(out.resolve("frequency.stderr")));
            runtime.verify();
        }
    }
    @Test void actualFailedOperationRetainsExitAndOriginalCause()throws Exception {
        Path out=work("bad-input");var session=new LinkedHashMap<String,Object>();var runtime=new PlinkRuntime(home(),"plink2",out,session);
        var error=assertThrows(java.io.IOException.class,()->runtime.execute(List.of("--bfile","missing-input","--freq","--out","result"),out,"operation",30));
        assertTrue(error.getMessage().contains("failed with exit"));assertNotEquals(0,session.get("operation_exit_code"));
        assertTrue(Files.readString(out.resolve("operation.stderr")).contains("Error"));
    }
    @Test void changedProfileMemberIsRejectedBeforeVersionExecution()throws Exception {
        Path copy=copyHome();Files.writeString(copy.resolve("LICENSE"),"changed");Path out=work("changed");
        assertThrows(PlinkRuntime.Unavailable.class,()->new PlinkRuntime(copy,"plink1",out,new LinkedHashMap<>()));assertFalse(Files.exists(out.resolve("version.stdout")));
    }
    @Test void extraRuntimeDirectoryIsRejectedWithoutExecuting()throws Exception {
        Path copy=copyHome();Files.createDirectory(copy.resolve("extra"));Path out=work("extra-case");
        assertThrows(PlinkRuntime.Unavailable.class,()->new PlinkRuntime(copy,"plink2",out,new LinkedHashMap<>()));assertFalse(Files.exists(out.resolve("version.stdout")));
    }
    @Test void nonzeroWarningOnlyExitIsStillAFailedOperation()throws Exception {
        Path out=work("warning-only");var session=new LinkedHashMap<String,Object>();var runtime=new PlinkRuntime(home(),"plink2",out,session);
        assertThrows(java.io.IOException.class,()->runtime.execute(List.of("--bfile","missing-input","--out","result"),out,"operation",30));
        assertEquals(1,session.get("operation_exit_code"));assertTrue(Files.readString(out.resolve("operation.stderr")).contains("No output requested"));
    }
    @Test void extraEntriesAfterAdmissionPreventTheNextLaunch()throws Exception {
        Path copy=copyHome();Path out=work("changed-after-version");var runtime=new PlinkRuntime(copy,"plink2",out,new LinkedHashMap<>());
        Files.writeString(copy.resolve("unexpected.dll"),"not allowed");
        assertThrows(PlinkRuntime.Unavailable.class,()->runtime.execute(List.of("--version"),out,"must-not-start",30));
        assertFalse(Files.exists(out.resolve("must-not-start.stdout")));
    }
}
