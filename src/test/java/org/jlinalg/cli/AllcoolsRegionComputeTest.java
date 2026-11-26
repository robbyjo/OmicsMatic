package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class AllcoolsRegionComputeTest {
    @TempDir Path temp;
    private static final String HEADER="cell\tmc_type\tfeature\tmc\tcov\n";
    private AllcoolsRegionRequest.Spec spec(int cells,int types,int features,long output)throws Exception {
        var raw=AllcoolsRegionCli.example();raw.put("input",Map.of("counts",temp.resolve("original.tsv").toString(),"format","literal-utf8-tsv"));raw.put("dimensions",Map.of("cells",cells,"mc_types",types,"features",features));raw.put("capacity",Map.of("input_bytes",100000,"output_bytes",output,"wall_millis",60000));return AllcoolsRegionRequest.parse(raw,true);
    }
    private void input(String text)throws Exception {Files.createDirectory(temp.resolve("inputs"));Files.createDirectory(temp.resolve("outputs"));Files.writeString(temp.resolve("inputs/counts.tsv"),text,StandardCharsets.UTF_8);}
    @Test void fullRectangleProducesSaturationWarningsAndDirectPosteriorCountsAndReplays()throws Exception {
        input(HEADER+"a\tCGN\tx\t6000000000\t8000000000\na\tCGN\ty\t0\t0\na\tCHN\tx\t1\t5\na\tCHN\ty\t2\t8\nb\tCGN\tx\t0\t0\nb\tCGN\ty\t0\t0\nb\tCHN\tx\t2\t7\nb\tCHN\ty\t9\t10\n");
        var s=spec(2,2,2,100000);var audit=AllcoolsRegionCompute.run(temp,s,false,()->{});
        assertEquals(8L,audit.get("complete_rows"));assertEquals(4L,audit.get("complete_groups"));assertEquals(3L,audit.get("zero_coverage_hyper_one_rows"));assertEquals(1L,audit.get("saturated_mc_rows"));
        String posterior=Files.readString(temp.resolve("outputs/posterior-counts.tsv"));assertTrue(posterior.startsWith(HEADER+"a\tCGN\tx\t4294967295\t4294967295\n"));
        assertEquals(NextflowJson.json(audit),NextflowJson.json(AllcoolsRegionCompute.run(temp,s,true,()->{})));
        Files.writeString(temp.resolve("outputs/values.tsv"),"tampered\n");assertThrows(IOException.class,()->AllcoolsRegionCompute.run(temp,s,true,()->{}));
    }
    @Test void wrongAxesMissingExtraMalformedAndOverflowingCountsAreNeverRepaired()throws Exception {
        String[] bad={"a\tT\tx\t1\t3\n","a\tT\tx\t1\t3\na\tT\tx\t1\t3\n","a\tT\tx\t1\t3\nb\tT\ty\t1\t3\n","a\tT\tx\t1\t3\na\tT\ty\t1\t3\na\tT\tz\t0\t0\n","a\tT\tx\t01\t3\na\tT\ty\t1\t3\n","a\tT\tx\t2\t1\na\tT\ty\t1\t3\n","a\tT\tx\t9223372036854775808\t9223372036854775808\na\tT\ty\t1\t3\n"};
        for(int i=0;i<bad.length;i++) {
            Path root=Files.createDirectory(temp.resolve("bad-"+i));Files.createDirectory(root.resolve("inputs"));Files.createDirectory(root.resolve("outputs"));Files.writeString(root.resolve("inputs/counts.tsv"),HEADER+bad[i]);var s=spec(1,1,2,100000);
            assertThrows(IOException.class,()->AllcoolsRegionCompute.run(root,s,false,()->{}),"case "+i);assertFalse(Files.exists(root.resolve("COMPLETED.json")));
        }
    }
    @Test void invalidUtf8OutputCapacityAndCancellationRefuseWholeComputation()throws Exception {
        input(HEADER+"a\tT\tx\t1\t3\n");var s=spec(1,1,1,1);assertThrows(IOException.class,()->AllcoolsRegionCompute.run(temp,s,false,()->{}));
        Path other=Files.createDirectory(temp.resolve("other"));Files.createDirectory(other.resolve("inputs"));Files.createDirectory(other.resolve("outputs"));Files.write(other.resolve("inputs/counts.tsv"),new byte[]{(byte)0xc3,0x28});var valid=spec(1,1,1,100000);
        assertThrows(IOException.class,()->AllcoolsRegionCompute.run(other,valid,false,()->{}));
        assertThrows(IllegalStateException.class,()->AllcoolsRegionCompute.run(temp,valid,true,()->{throw new IllegalStateException("cancel");}));
    }
    @Test void sourceSemanticsAndSettingsAreExplicitAndUnknownFieldsAreRefused()throws Exception {
        var v=AllcoolsRegionCli.example();assertDoesNotThrow(()->AllcoolsRegionRequest.parse(v,false));v.put("extra",1);assertThrows(IOException.class,()->AllcoolsRegionRequest.parse(v,false));v.remove("extra");
        var settings=new LinkedHashMap<String,Object>();NextflowFiles.object(v.get("settings")).forEach((k,x)->settings.put(k.toString(),x));settings.put("survival","P(X>=mc)");v.put("settings",settings);assertThrows(IOException.class,()->AllcoolsRegionRequest.parse(v,false));settings.put("survival","P(X>mc)");settings.put("hypo_cutoff",Double.NaN);assertThrows(IOException.class,()->AllcoolsRegionRequest.parse(v,false));
    }
}
