/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SambambaWorkflowCliTest {
    @TempDir Path temp;
    private static Properties settings(){
        Properties p=new Properties();p.putAll(Map.of("bam","input.bam","sambamba","/permitted/bin/sambamba","edition","0.8.2","action","mark","threads","1","compression","6"));
        p.putAll(Map.of("hash_table_size","1024","overflow_list_size","64","sort_buffer_mb","16","io_buffer_mb","1","wall_ms","60000","address_space_mb","1024","work_mb","256","max_files","128"));return p;
    }
    @Test void scientificAndResourceOptionsHaveStrictTypesAndNoImplicitDefaults(){
        var p=settings();var c=SambambaWorkflowFiles.config(p);assertEquals("0.8.2",c.get("edition"));assertFalse(c.remove());
        for(String key:SambambaWorkflowFiles.KEYS){var missing=settings();missing.remove(key);assertThrows(IllegalArgumentException.class,()->SambambaWorkflowFiles.config(missing),key);}
        for(String value:List.of("01","1.0","+1","-1","NaN","0","65")){var bad=settings();bad.setProperty("threads",value);assertThrows(IllegalArgumentException.class,()->SambambaWorkflowFiles.config(bad),value);}
        var extra=settings();extra.setProperty("extra_args","--remove-duplicates");assertThrows(IllegalArgumentException.class,()->SambambaWorkflowFiles.config(extra));
        for(String edition:List.of("1.0.1","0.8","auto")){var bad=settings();bad.setProperty("edition",edition);assertThrows(IllegalArgumentException.class,()->SambambaWorkflowFiles.config(bad));}
    }
    @Test void duplicateEscapedKeysAndScientificReplayContradictionsAreRejected()throws Exception {
        assertThrows(IllegalArgumentException.class,()->SambambaWorkflowFiles.parse("threads=1\n\\u0074hreads=2\n"));
        var original=settings();var effective=settings();effective.setProperty("bam","input.bam");effective.setProperty("sambamba",temp.resolve("runtime/sambamba").toString());
        SambambaWorkflowFiles.scientificCoherence(SambambaWorkflowFiles.config(original),SambambaWorkflowFiles.config(effective));
        for(String key:List.of("action","threads","compression","wall_ms","work_mb")){
            var changed=settings();changed.setProperty("sambamba",effective.getProperty("sambamba"));changed.setProperty(key,switch(key){case "action"->"remove";case "threads"->"2";case "compression"->"5";case "wall_ms"->"60001";default->"257";});
            assertThrows(IllegalArgumentException.class,()->SambambaWorkflowFiles.scientificCoherence(SambambaWorkflowFiles.config(original),SambambaWorkflowFiles.config(changed)),key);
        }
        effective.setProperty("bam","other.bam");assertThrows(IllegalArgumentException.class,()->SambambaWorkflowFiles.scientificCoherence(SambambaWorkflowFiles.config(original),SambambaWorkflowFiles.config(effective)));
    }
    @Test void exactOriginalArgumentsPreserveSpacesAndMarkRemovalSeparation(){
        var c=SambambaWorkflowFiles.config(settings());Path root=Path.of("/owned/space and 'quote'");
        var mark=SambambaWorkflowFiles.command(Path.of(c.get("sambamba")),c,root,false);
        var remove=SambambaWorkflowFiles.command(Path.of(c.get("sambamba")),c,root,true);
        assertFalse(mark.contains("--remove-duplicates"));assertTrue(remove.contains("--remove-duplicates"));
        assertEquals(root.resolve("input.bam").toString(),mark.get(mark.size()-2));
        assertEquals(root.resolve("input.bam").toString(),remove.get(remove.size()-2));
        assertTrue(mark.contains("--tmpdir="+root.resolve("tmp-mark")));assertTrue(remove.contains("--tmpdir="+root.resolve("tmp-remove")));
        assertTrue(SambambaWorkflowFiles.headerCommand(mark).startsWith("markdup -t 1 -l 6 "));
        assertThrows(IllegalArgumentException.class,()->SambambaWorkflowFiles.commandPath(Path.of("bad\tpath")));
    }
    @Test void prospectiveOutputAndExistingBundlesArePreservedBeforeAnyHostCheck()throws Exception {
        Path original=Files.createDirectory(temp.resolve("original")),payload=original.resolve("retained.txt");Files.writeString(payload,"historical evidence");
        var console=new ByteArrayOutputStream();
        assertEquals(2,SambambaWorkflowCli.run(new String[]{"--verify",original.toString(),"--out",original.resolve("nested/output").toString()},new PrintStream(console),new PrintStream(console)));
        assertEquals("historical evidence",Files.readString(payload));try(var members=Files.list(original)){assertEquals(List.of("retained.txt"),members.map(p->p.getFileName().toString()).toList());}
        assertThrows(IOException.class,()->SambambaWorkflowFiles.intendedNew(original));
        assertEquals(temp.toRealPath().resolve("new/deeper/output"),SambambaWorkflowFiles.intendedNew(temp.resolve("new/deeper/output")));
        assertFalse(Files.exists(temp.resolve("new")));
    }
    @Test void workspaceBudgetsAndDuplicateOptionsFailWithoutInventedResults()throws Exception {
        var c=SambambaWorkflowFiles.config(settings());Path out=Files.createDirectory(temp.resolve("work"));Files.writeString(out.resolve("small.txt"),"preserved");
        var observed=SambambaWorkflowFiles.inventory(out,c,true);assertEquals(1,observed.entries());assertEquals(9,observed.bytes());
        var tiny=settings();tiny.setProperty("max_files","1");Files.createDirectory(out.resolve("extra"));
        assertThrows(IOException.class,()->SambambaWorkflowFiles.inventory(out,SambambaWorkflowFiles.config(tiny),false));
        var console=new ByteArrayOutputStream();assertEquals(2,SambambaWorkflowCli.run(new String[]{"--out",out.toString(),"--out",out.toString()},new PrintStream(console),new PrintStream(console)));
        assertFalse(Files.exists(out.resolve("COMPLETED.properties")));assertEquals("preserved",Files.readString(out.resolve("small.txt")));
        assertEquals(0,SambambaWorkflowCli.run(new String[]{"--help"},new PrintStream(console),new PrintStream(console)));
    }
}
