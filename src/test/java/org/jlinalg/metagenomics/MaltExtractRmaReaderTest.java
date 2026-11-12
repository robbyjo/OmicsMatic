/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.metagenomics;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class MaltExtractRmaReaderTest {
 @TempDir Path temporary;
 @Test void missingAndWrongProviderSkipWithRequiredRecoveryAndNoProvisionalRow()throws Exception {
  Path input=temporary.resolve("source.rma6"),provider=temporary.resolve("provider");Files.writeString(input,"retained source bytes");var caps=new MaltExtractRmaReader.Limits(1000,10,10,10,1000,10);
  for(int i=0;i<2;i++){if(i==1){Files.createDirectory(provider);Files.writeString(provider.resolve("MaltExtract1.7.jar"),"unrecognized runtime");}var error=assertThrows(MaltExtractRmaReader.MissingRuntimeException.class,()->MaltExtractRmaReader.read(provider,input,caps,row->fail("A missing runtime cannot emit a provisional read")));assertTrue(error.getMessage().contains("SKIP3"));assertTrue(error.getMessage().contains("Required action:"));assertTrue(error.getMessage().contains("skip this dependent RMA analysis"));assertEquals("retained source bytes",Files.readString(input));}
 }
}
