/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NextflowRecipeTest {
    @TempDir Path temp;
    private Map<String,Object> request() {
        var raw=new LinkedHashMap<String,Object>();
        raw.put("schema_version",1);raw.put("engine_profile","linux-x86_64-nextflow-24.04.4");
        raw.put("workflow",Map.of("path",temp.toAbsolutePath().toString(),"entrypoint","main.nf","configs",List.of(),"profiles",List.of("local")));
        raw.put("resources",Map.of("reads",Map.of("path",temp.resolve("reads.fastq").toAbsolutePath().toString())));
        raw.put("parameters",Map.of("exact",new BigInteger("9007199254740993")));
        raw.put("required_tools",List.of());
        raw.put("outputs",List.of(Map.of("path","counts.tsv","kind","file","allow_empty",false)));
        raw.put("require_tasks",true);raw.put("limits",Map.of());return raw;
    }
    private NextflowRecipe.Request read(Map<String,Object> raw)throws IOException {
        Path path=Files.writeString(temp.resolve("recipe.json"),NextflowJson.json(raw));return NextflowRecipe.read(path);
    }
    @Test void typedPathsOutputsAndLargeIntegerParametersRetainTheirDeclarations()throws Exception {
        var got=read(request());assertEquals("linux-x86_64-nextflow-24.04.4",got.profile());
        assertEquals(temp.resolve("reads.fastq").toAbsolutePath(),got.sources().get("reads"));
        assertEquals("{\"exact\":9007199254740993}",NextflowJson.parameters(got.parameters()));
        assertTrue(got.requireTasks());assertFalse(got.outputs().get(0).allowEmpty());assertEquals(3600000,got.process().wallMillis());
    }
    @Test void anUnsupportedExactEditionIsUnavailableWithoutSubstitution()throws Exception {
        var raw=request();raw.put("engine_profile","linux-x86_64-nextflow-24.04");
        assertThrows(NextflowRuntime.Unavailable.class,()->read(raw));
    }
    @Test void outputTraversalAndAnEmptyOutputContractAreRejected()throws Exception {
        var raw=request();raw.put("outputs",List.of(Map.of("path","../outside","kind","file","allow_empty",false)));
        assertThrows(IOException.class,()->read(raw));raw.put("outputs",List.of());assertThrows(IOException.class,()->read(raw));
    }
    @Test void unknownCapacityAndImplicitBooleanChoicesAreRejected()throws Exception {
        var raw=request();raw.put("limits",Map.of("unknown",1));assertThrows(IOException.class,()->read(raw));
        raw.put("limits",Map.of());raw.put("require_tasks","true");assertThrows(IOException.class,()->read(raw));
    }
    @Test void resourceAliasesCannotReplaceTheWorkflow()throws Exception {
        var raw=request();raw.put("resources",Map.of("workflow",Map.of("path",temp.toAbsolutePath().toString())));
        assertThrows(IOException.class,()->read(raw));
    }
}
