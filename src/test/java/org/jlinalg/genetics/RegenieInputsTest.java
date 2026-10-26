/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RegenieInputsTest {
    @TempDir Path directory;
    private Path fixture(String name)throws Exception{return Path.of(getClass().getResource("/regenie/"+name).toURI());}
    @Test void officialGenotypesRetainIdenticalSampleOrderAndCounts()throws Exception {
        var bed=RegenieInputs.inspect("bed",fixture("example.bed").resolveSibling("example"),null);
        var pgen=RegenieInputs.inspect("pgen",fixture("example.pgen").resolveSibling("example"),null);
        var bgen=RegenieInputs.inspect("bgen",fixture("example.bgen"),null);
        assertEquals(500,bed.sampleKeys().size());assertEquals(1000,bed.variants());assertEquals(bed.sampleKeys(),pgen.sampleKeys());assertEquals(bed.sampleKeys(),bgen.sampleKeys());assertEquals(bed.variants(),bgen.variants());
        var external=RegenieInputs.inspect("bgen",fixture("example_3chr.bgen"),fixture("example_3chr.sample"));assertEquals(500,external.sampleKeys().size());assertEquals(500,external.variants());
    }
    @Test void truncatedBedAndAmbiguousPairIdentitiesFail()throws Exception {
        Path prefix=directory.resolve("g");Files.writeString(Path.of(prefix+".bim"),"1 v1 0 1 A C\n");Files.writeString(Path.of(prefix+".fam"),"a_b c 0 0 0 -9\na b_c 0 0 0 -9\n");Files.write(Path.of(prefix+".bed"),new byte[]{0x6c,0x1b,1,0});
        assertTrue(assertThrows(java.io.IOException.class,()->RegenieInputs.inspect("bed",prefix,null)).getMessage().contains("colliding"));
        Files.writeString(Path.of(prefix+".fam"),"a c 0 0 0 -9\n");Files.write(Path.of(prefix+".bed"),new byte[]{0x6c,0x1b,1});assertTrue(assertThrows(java.io.IOException.class,()->RegenieInputs.inspect("bed",prefix,null)).getMessage().contains("byte count"));
    }
    @Test void pgenIidOnlyAndMultiallelicPvarAreRejectedExplicitly()throws Exception {
        Path prefix=directory.resolve("p");Files.write(Path.of(prefix+".pgen"),new byte[]{0x6c,0x1b,0x10});Files.writeString(Path.of(prefix+".pvar"),"#CHROM POS ID REF ALT\n1 v1 1 A G\n");Files.writeString(Path.of(prefix+".psam"),"#IID\na\n");
        assertTrue(assertThrows(java.io.IOException.class,()->RegenieInputs.inspect("pgen",prefix,null)).getMessage().contains("#FID IID"));
        Files.writeString(Path.of(prefix+".psam"),"#FID IID\na a\n");Files.writeString(Path.of(prefix+".pvar"),"#CHROM POS ID REF ALT\n1 1 v1 A G,T\n");assertTrue(assertThrows(java.io.IOException.class,()->RegenieInputs.inspect("pgen",prefix,null)).getMessage().contains("biallelic"));
    }
    @Test void sourceFixtureHashesRemainExact()throws Exception {
        String provenance=Files.readString(fixture("provenance.json"));for(String name:List.of("example.bed","example.bgen","example.pgen")){
            String sha=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(fixture(name))));assertTrue(provenance.contains(sha));
        }
    }
}
