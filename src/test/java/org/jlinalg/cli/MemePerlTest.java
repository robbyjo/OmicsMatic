/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class MemePerlTest {
    @Test void completeVersionOrderedRootsLoadedModulesAndNativeImagesRemainExplicit()throws Exception {
        var receipt=parse(valid());assertEquals("v5.38.2",receipt.version());assertEquals(java.util.List.of("/home/perl","/usr/share/perl5"),receipt.roots());assertEquals(6,receipt.modules().size());assertEquals(java.util.List.of("/usr/lib/Expat.so"),receipt.dynamicImages());assertTrue(RsemJson.json(receipt.document()).contains("Fixed original report imports"));
        assertThrows(UnsupportedOperationException.class,()->receipt.modules().put("new","/new"));assertThrows(UnsupportedOperationException.class,()->receipt.roots().add("/new"));
    }
    @Test void relativeRootsAndModuleHooksCannotEnterAFullReportTicket()throws Exception {assertThrows(IOException.class,()->parse(valid().replace("INC\t/home/perl","INC\t.")));assertThrows(IOException.class,()->parse(valid().replace("/usr/lib/Expat.so","relative.so")));}
    @Test void missingReportDependencyErrorsAndMalformedUtf8RemainFailures()throws Exception {assertThrows(IOException.class,()->MemePerl.parse(valid().getBytes(StandardCharsets.UTF_8),"Can't locate XML/Parser/Expat.pm".getBytes(StandardCharsets.UTF_8)));assertThrows(IOException.class,()->MemePerl.parse(new byte[]{(byte)0xc3,0x28},new byte[0]));}
    @Test void duplicateModuleOrNativeImageAndMissingRequiredImportsFail()throws Exception {assertThrows(IOException.class,()->parse(valid()+"MODULE\tTomtomSAX.pm\t/home/perl/TomtomSAX.pm\n"));assertThrows(IOException.class,()->parse(valid()+"DYNAMIC\t/usr/lib/Expat.so\n"));assertThrows(IOException.class,()->parse(valid().replace("MODULE\tCheckingSAX.pm\t/home/perl/CheckingSAX.pm\n","")));}
    @Test void truncatedUnknownAndDuplicateVersionRecordsCannotBecomePrefixes()throws Exception {String valid=valid();for(String changed:java.util.List.of(valid.substring(0,valid.length()-1),valid+"UNKNOWN\tvalue\n",valid+"VERSION\tv5.38.2\n",valid.replace("v5.38.2","v6.0.0")))assertThrows(IOException.class,()->parse(changed));}
    @Test void onlyCleanOriginalMissingImportExitsBecomeDependencySkips()throws Exception {
        byte[] missing="Can't locate XML/Parser/Expat.pm in @INC (you may need to install the XML::Parser::Expat module)".getBytes(StandardCharsets.UTF_8);
        assertTrue(MemePerl.unavailableImport(new IOException("Original RSEM process failed, exit 2"),missing));assertTrue(MemePerl.unavailableImport(new IOException("Original RSEM process failed, exit 255"),missing));
        for(String error:java.util.List.of("RSEM wall-time capacity exceeded","Original RSEM process failed, exit 152","RSEM input/runtime/workspace integrity failed after execution"))assertFalse(MemePerl.unavailableImport(new IOException(error),missing));
        var cleanup=new IOException("Original RSEM process failed, exit 2");cleanup.addSuppressed(new IOException("cleanup failed"));assertFalse(MemePerl.unavailableImport(cleanup,missing));
    }
    @Test void nativeXmlLoaderAbsenceIsDistinctFromMemoryOrArbitraryImportErrors()throws Exception {
        var failed=new IOException("Original RSEM process failed, exit 255");String loader="Can't load '/usr/lib/Expat.so' for module XML::Parser::Expat: libexpat.so.1: cannot open shared object file: No such file or directory";
        assertTrue(MemePerl.unavailableImport(failed,loader.getBytes(StandardCharsets.UTF_8)));assertFalse(MemePerl.unavailableImport(failed,(loader+" Cannot allocate memory").getBytes(StandardCharsets.UTF_8)));assertFalse(MemePerl.unavailableImport(failed,"arbitrary parser failure".getBytes(StandardCharsets.UTF_8)));
    }
    private static MemePerl.Receipt parse(String text)throws IOException {return MemePerl.parse(text.getBytes(StandardCharsets.UTF_8),new byte[0]);}
    private static String valid(){var text=new StringBuilder("VERSION\tv5.38.2\nINC\t/home/perl\nINC\t/usr/share/perl5\n");for(String module:java.util.List.of("XML/Parser/Expat.pm","HtmlMonolithWr.pm","TomtomSAX.pm","JsonWr.pm","CheckingSAX.pm","File/Temp.pm"))text.append("MODULE\t").append(module).append("\t/home/perl/").append(module).append('\n');return text.append("DYNAMIC\t/usr/lib/Expat.so\n").toString();}
}
