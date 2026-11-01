package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

final class RsemFileGraphTest {
    @TempDir Path root;
    private RsemFileGraph.Limits limits(){return new RsemFileGraph.Limits(1000000,10000000,1000,8,1000000);}
    @Test void complete_compiler_tree_and_metadata_are_rechecked_without_rewriting_the_manifest()throws Exception{
        Path sdk=Files.createDirectory(root.resolve("sdk")),file=Files.writeString(sdk.resolve("header.h"),"original");Path manifest=root.resolve("manifest.jsonl");
        var snapshot=RsemFileGraph.capture(List.of(sdk),List.of(),limits(),manifest,()->{});byte[] original=Files.readAllBytes(manifest);snapshot.verify(()->{});assertArrayEquals(original,Files.readAllBytes(manifest));
        Files.writeString(file,"altered");assertThrows(IOException.class,()->snapshot.verify(()->{}));
    }
    @Test void new_members_and_previously_absent_inputs_cannot_escape_the_compiler_identity()throws Exception{
        Path sdk=Files.createDirectory(root.resolve("sdk")),missing=root.resolve("absent-config");
        var before=RsemFileGraph.capture(List.of(sdk),List.of(missing),limits(),root.resolve("before"),()->{});Files.writeString(sdk.resolve("shadow.h"),"new");assertThrows(IOException.class,()->before.verify(()->{}));
        var after=RsemFileGraph.capture(List.of(sdk),List.of(missing),limits(),root.resolve("after"),()->{});Files.writeString(missing,"new config");assertThrows(IOException.class,()->after.verify(()->{}));
    }
    @Test void manifest_population_bytes_and_placement_are_bounded_before_success()throws Exception{
        Path sdk=Files.createDirectory(root.resolve("sdk"));Files.write(sdk.resolve("large.h"),new byte[100]);
        assertThrows(IOException.class,()->RsemFileGraph.capture(List.of(sdk),List.of(),new RsemFileGraph.Limits(99,1000000,100,8,1000000),root.resolve("limited-file"),()->{}));
        assertThrows(IOException.class,()->RsemFileGraph.capture(List.of(sdk),List.of(),new RsemFileGraph.Limits(1000000,99,100,8,1000000),root.resolve("limited-total"),()->{}));
        assertThrows(IOException.class,()->RsemFileGraph.capture(List.of(sdk),List.of(),new RsemFileGraph.Limits(1000000,1000000,1,8,1000000),root.resolve("limited-count"),()->{}));
        assertThrows(IOException.class,()->RsemFileGraph.capture(List.of(sdk),List.of(),limits(),sdk.resolve("recursive-manifest"),()->{}));assertFalse(Files.exists(sdk.resolve("recursive-manifest")));
    }
    @Test void cancellation_never_returns_a_complete_snapshot()throws Exception{
        Path sdk=Files.createDirectory(root.resolve("sdk"));Files.writeString(sdk.resolve("data"),"data");
        assertThrows(java.util.concurrent.CancellationException.class,()->RsemFileGraph.capture(List.of(sdk),List.of(),limits(),root.resolve("cancelled"),()->{throw new java.util.concurrent.CancellationException("controlled");}));
    }
    @Test @EnabledOnOs(OS.LINUX) void symlink_resolution_precedes_dot_collapsing_and_records_the_physical_target()throws Exception{
        Path sdk=Files.createDirectory(root.resolve("sdk")),physical=Files.createDirectories(sdk.resolve("physical/subdir"));Files.writeString(sdk.resolve("value.h"),"lexical");
        Path wanted=Files.writeString(physical.getParent().resolve("value.h"),"physical");Files.createSymbolicLink(sdk.resolve("link"),Path.of("physical/subdir"));Files.createSymbolicLink(sdk.resolve("use.h"),Path.of("link/../value.h"));
        Path manifest=root.resolve("links");var snapshot=RsemFileGraph.capture(List.of(sdk),List.of(),limits(),manifest,()->{});snapshot.verify(()->{});
        Map<?,?> selected=null;for(String line:Files.readAllLines(manifest)){
            Map<?,?> row=(Map<?,?>)SimpleJson.parse(line);if(sdk.resolve("use.h").toString().equals(row.get("path")))selected=row;
        }
        assertNotNull(selected);assertEquals(wanted.toString(),selected.get("real"));Files.writeString(wanted,"changed physical");assertThrows(IOException.class,()->snapshot.verify(()->{}));
    }
    @Test @EnabledOnOs(OS.LINUX) void links_cannot_normalize_a_file_into_a_directory_or_hide_a_resolution_loop()throws Exception{
        Path sdk=Files.createDirectory(root.resolve("sdk"));Files.writeString(sdk.resolve("file"),"ordinary");Files.createSymbolicLink(sdk.resolve("bad"),Path.of("file/../file"));
        assertThrows(IOException.class,()->RsemFileGraph.capture(List.of(sdk),List.of(),limits(),root.resolve("bad-link"),()->{}));
        Path looping=Files.createDirectory(root.resolve("looping"));Files.createSymbolicLink(looping.resolve("self"),Path.of("self"));
        assertThrows(IOException.class,()->RsemFileGraph.capture(List.of(looping),List.of(),limits(),root.resolve("loop"),()->{}));
    }
    @Test @EnabledOnOs(OS.LINUX) void compiler_permissions_and_same_byte_timestamp_drift_are_not_silently_accepted()throws Exception{
        Path sdk=Files.createDirectory(root.resolve("sdk")),file=Files.writeString(sdk.resolve("header"),"bytes");var snapshot=RsemFileGraph.capture(List.of(sdk),List.of(),limits(),root.resolve("metadata"),()->{});
        Files.setLastModifiedTime(file,java.nio.file.attribute.FileTime.fromMillis(Files.getLastModifiedTime(file).toMillis()+10000));assertThrows(IOException.class,()->snapshot.verify(()->{}));
    }
}
