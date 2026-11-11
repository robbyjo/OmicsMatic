package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class RsemHostTest {
    @TempDir Path root;
    private RepeatHostFiles.Snapshot capture(List<Path> roots)throws Exception{
        return RepeatHostFiles.capture(roots,List.of(),Set.of(root),Set.of(root),new RepeatHostFiles.Limits(1000000,10000000,100,8,1000000),()->{});
    }
    @Test void discovered_lookup_roots_can_extend_the_scope_while_every_original_module_remains_identical()throws Exception{
        Path modules=Files.createDirectory(root.resolve("modules"));Files.writeString(modules.resolve("original.pm"),"original");
        var before=capture(List.of(modules));var after=capture(List.of(modules,root.resolve("missing-hwcaps")));
        RsemHost.requirePreserved(before,after);
        Files.writeString(modules.resolve("original.pm"),"changed");assertThrows(IOException.class,()->RsemHost.requirePreserved(before,capture(List.of(modules,root.resolve("missing-hwcaps")))));
    }
    @Test void expansion_never_hides_missing_modules_or_new_members_in_an_existing_import_root()throws Exception{
        Path modules=Files.createDirectory(root.resolve("modules"));Path file=Files.writeString(modules.resolve("original.pm"),"original");
        var before=capture(List.of(modules));Files.writeString(modules.resolve("new.pm"),"new");assertThrows(IOException.class,()->RsemHost.requirePreserved(before,capture(List.of(modules))));
        Files.delete(file);assertThrows(IOException.class,()->RsemHost.requirePreserved(before,capture(List.of(modules))));
    }
    @Test void perl_import_order_and_duplicate_entries_are_retained_but_partial_or_relative_paths_fail()throws Exception{
        assertEquals(List.of("/first","/second","/first"),RsemHost.perlRoots("/first\n/second\n/first\n".getBytes(StandardCharsets.UTF_8)));
        for(String invalid:List.of("/missing-newline","relative\n","/a/../b\n","/trailing/\n","/control\0\n","/a\n\n"))
            assertThrows(IOException.class,()->RsemHost.perlRoots(invalid.getBytes(StandardCharsets.UTF_8)));
        assertThrows(IOException.class,()->RsemHost.perlRoots(new byte[]{-61,40,10}));
    }
}
