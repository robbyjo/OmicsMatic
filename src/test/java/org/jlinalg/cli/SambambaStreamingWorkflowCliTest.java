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

class SambambaStreamingWorkflowCliTest {
    @TempDir Path temp;
    private static Properties settings(){
        Properties p=new Properties();p.putAll(Map.of("bam","input.bam","sambamba","/permitted/bin/sambamba","edition","0.8.2","action","mark","threads","1","compression","6"));
        p.putAll(Map.of("hash_table_size","1024","overflow_list_size","64","sort_buffer_mb","16","io_buffer_mb","1","wall_ms","60000","address_space_mb","1024","work_mb","256","max_files","128"));p.putAll(Map.of("audit_compressed_bytes","1099511627776","audit_decoded_bytes","8796093022208","audit_records","1099511627776","audit_record_bytes","16777216","audit_header_bytes","4194304","audit_dictionary_bytes","4194304"));return p;
    }
    @Test void scientificAndResourceOptionsHaveStrictTypesAndNoImplicitDefaults(){
        var p=settings();var c=SambambaStreamingWorkflowFiles.config(p);assertEquals("0.8.2",c.get("edition"));assertFalse(c.remove());
        for(String key:SambambaStreamingWorkflowFiles.KEYS){var missing=settings();missing.remove(key);assertThrows(IllegalArgumentException.class,()->SambambaStreamingWorkflowFiles.config(missing),key);}
        for(String value:List.of("01","1.0","+1","-1","NaN","0","65")){var bad=settings();bad.setProperty("threads",value);assertThrows(IllegalArgumentException.class,()->SambambaStreamingWorkflowFiles.config(bad),value);}
        var extra=settings();extra.setProperty("extra_args","--remove-duplicates");assertThrows(IllegalArgumentException.class,()->SambambaStreamingWorkflowFiles.config(extra));
        for(String edition:List.of("1.0.1","0.8","auto")){var bad=settings();bad.setProperty("edition",edition);assertThrows(IllegalArgumentException.class,()->SambambaStreamingWorkflowFiles.config(bad));}
    }
    @Test void duplicateEscapedKeysAndScientificReplayContradictionsAreRejected()throws Exception {
        assertThrows(IllegalArgumentException.class,()->SambambaStreamingWorkflowFiles.parse("threads=1\n\\u0074hreads=2\n"));
        var original=settings();var effective=settings();effective.setProperty("bam","input.bam");effective.setProperty("sambamba",temp.resolve("runtime/sambamba").toString());
        SambambaStreamingWorkflowFiles.scientificCoherence(SambambaStreamingWorkflowFiles.config(original),SambambaStreamingWorkflowFiles.config(effective));
        for(String key:List.of("action","threads","compression","wall_ms","work_mb")){
            var changed=settings();changed.setProperty("sambamba",effective.getProperty("sambamba"));changed.setProperty(key,switch(key){case "action"->"remove";case "threads"->"2";case "compression"->"5";case "wall_ms"->"60001";default->"257";});
            assertThrows(IllegalArgumentException.class,()->SambambaStreamingWorkflowFiles.scientificCoherence(SambambaStreamingWorkflowFiles.config(original),SambambaStreamingWorkflowFiles.config(changed)),key);
        }
        effective.setProperty("bam","other.bam");assertThrows(IllegalArgumentException.class,()->SambambaStreamingWorkflowFiles.scientificCoherence(SambambaStreamingWorkflowFiles.config(original),SambambaStreamingWorkflowFiles.config(effective)));
    }
    @Test void exactOriginalArgumentsPreserveSpacesAndMarkRemovalSeparation(){
        var c=SambambaStreamingWorkflowFiles.config(settings());Path root=Path.of("/owned/space and 'quote'");
        var mark=SambambaStreamingWorkflowFiles.command(Path.of(c.get("sambamba")),c,root,false);
        var remove=SambambaStreamingWorkflowFiles.command(Path.of(c.get("sambamba")),c,root,true);
        assertFalse(mark.contains("--remove-duplicates"));assertTrue(remove.contains("--remove-duplicates"));
        assertEquals(root.resolve("input.bam").toString(),mark.get(mark.size()-2));
        assertEquals(root.resolve("input.bam").toString(),remove.get(remove.size()-2));
        assertTrue(mark.contains("--tmpdir="+root.resolve("tmp-mark")));assertTrue(remove.contains("--tmpdir="+root.resolve("tmp-remove")));
        assertTrue(SambambaStreamingWorkflowFiles.headerCommand(mark).startsWith("markdup -t 1 -l 6 "));
        assertThrows(IllegalArgumentException.class,()->SambambaStreamingWorkflowFiles.commandPath(Path.of("bad\tpath")));
    }
    @Test void prospectiveOutputAndExistingBundlesArePreservedBeforeAnyHostCheck()throws Exception {
        Path original=Files.createDirectory(temp.resolve("original")),payload=original.resolve("retained.txt");Files.writeString(payload,"historical evidence");
        var console=new ByteArrayOutputStream();
        assertEquals(2,SambambaStreamingWorkflowCli.run(new String[]{"--verify",original.toString(),"--out",original.resolve("nested/output").toString()},new PrintStream(console),new PrintStream(console)));
        assertEquals("historical evidence",Files.readString(payload));try(var members=Files.list(original)){assertEquals(List.of("retained.txt"),members.map(p->p.getFileName().toString()).toList());}
        assertThrows(IOException.class,()->SambambaStreamingWorkflowFiles.intendedNew(original));
        assertEquals(temp.toRealPath().resolve("new/deeper/output"),SambambaStreamingWorkflowFiles.intendedNew(temp.resolve("new/deeper/output")));
        assertFalse(Files.exists(temp.resolve("new")));
    }
    @Test void workspaceBudgetsAndDuplicateOptionsFailWithoutInventedResults()throws Exception {
        var c=SambambaStreamingWorkflowFiles.config(settings());Path out=Files.createDirectory(temp.resolve("work"));Files.writeString(out.resolve("small.txt"),"preserved");
        var observed=SambambaStreamingWorkflowFiles.inventory(out,c,true);assertEquals(1,observed.entries());assertEquals(9,observed.bytes());
        var tiny=settings();tiny.setProperty("max_files","1");Files.createDirectory(out.resolve("extra"));
        assertThrows(IOException.class,()->SambambaStreamingWorkflowFiles.inventory(out,SambambaStreamingWorkflowFiles.config(tiny),false));
        var console=new ByteArrayOutputStream();assertEquals(2,SambambaStreamingWorkflowCli.run(new String[]{"--out",out.toString(),"--out",out.toString()},new PrintStream(console),new PrintStream(console)));
        assertFalse(Files.exists(out.resolve("COMPLETED.properties")));assertEquals("preserved",Files.readString(out.resolve("small.txt")));
        assertEquals(0,SambambaStreamingWorkflowCli.run(new String[]{"--help"},new PrintStream(console),new PrintStream(console)));
    }
    @Test void streamingAndWorkspaceBudgetsUseExplicitCanonicalLongValues(){
        var p=settings();p.setProperty("work_mb","8192");p.setProperty("audit_records",Long.toString(Long.MAX_VALUE));
        var c=SambambaStreamingWorkflowFiles.config(p);assertEquals(8L<<30,c.workBytes());assertEquals(Long.MAX_VALUE,c.auditLimits().records());
        for(String value:List.of("9223372036854775808","01","1.0","-1")){
            var bad=settings();bad.setProperty("audit_records",value);assertThrows(IllegalArgumentException.class,()->SambambaStreamingWorkflowFiles.config(bad));
        }
    }
    @Test void inputCaptureRejectsInsufficientWorkspaceBeforeCreatingTheCapturedFile()throws Exception {
        Path source=temp.resolve("source.bam");Files.write(source,new byte[200000]);Path owned=Files.createDirectory(temp.resolve("owned"));
        Files.write(owned.resolve("retained-metadata"),new byte[900000]);Path target=owned.resolve("input.bam");
        var small=settings();small.setProperty("work_mb","1");String hash=SambambaStreamingWorkflowFiles.sha(source);
        IOException failure=assertThrows(IOException.class,()->SambambaStreamingWorkflowCli.stableCopy(source,target,hash,SambambaStreamingWorkflowFiles.config(small)));
        assertTrue(failure.getMessage().contains("work_mb"));assertFalse(Files.exists(target));assertEquals(200000,Files.size(source));assertEquals(900000,Files.size(owned.resolve("retained-metadata")));
        small.setProperty("work_mb","2");SambambaStreamingWorkflowCli.stableCopy(source,target,hash,SambambaStreamingWorkflowFiles.config(small));
        assertEquals(hash,SambambaStreamingWorkflowFiles.sha(target));
    }
    @Test void samplingConcurrentRegularTemporaryFileReplacementNeverInventsASpecialFile()throws Exception {
        Path owned=Files.createDirectory(temp.resolve("concurrent"));var c=SambambaStreamingWorkflowFiles.config(settings());
        var failure=new java.util.concurrent.atomic.AtomicReference<Throwable>();
        Thread writer=new Thread(()->{
            try{
                for(int i=0;i<500;i++){
                    Path scratch=owned.resolve("new.tmp"),target=owned.resolve("sorted.0.bam");Files.write(scratch,new byte[]{1,2,3});
                    Files.move(scratch,target,java.nio.file.StandardCopyOption.REPLACE_EXISTING);Files.delete(target);
                }
            }catch(Throwable e){failure.set(e);}
        });
        writer.start();int samples=0;
        while(writer.isAlive()){
            try{SambambaStreamingWorkflowFiles.inventory(owned,c,false);samples++;}
            catch(java.io.UncheckedIOException transientWalk){assertInstanceOf(java.nio.file.NoSuchFileException.class,transientWalk.getCause());}
            catch(java.nio.file.AccessDeniedException windowsDeletePending){
                // Preserve the real permission/observability error. Windows can
                // expose a delete-pending file this way; production does not
                // suppress it, and the execution profile itself requires Linux.
                assertTrue(System.getProperty("os.name").startsWith("Windows"));
            }
        }
        writer.join();assertNull(failure.get());assertTrue(samples>0);
        assertEquals(0,SambambaStreamingWorkflowFiles.inventory(owned,c,true).entries());
    }
}
