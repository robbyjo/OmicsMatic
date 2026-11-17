/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SnakemakeOriginalSoftwareCaptureTest {
    @TempDir Path temp;
    private static final SnakemakeOriginalCapture.Limits LIMITS=new SnakemakeOriginalCapture.Limits(100,10000,10000);
    private static SnakemakeOriginalSoftwareCapture.Node node(String kind,SnakemakeOriginalFiles.Pin pin,String target){return new SnakemakeOriginalSoftwareCapture.Node(kind,0755,pin,target,"recorded-key","recorded-time",1,0);}
    private static Object encoded(SnakemakeOriginalSoftwareCapture.Snapshot snapshot)throws IOException{return SnakemakeOriginalParameters.parse(SnakemakeOriginalJson.json(snapshot),100000,10000);}
    private static SnakemakeOriginalSoftwareCapture.Snapshot snapshot(Map<String,SnakemakeOriginalSoftwareCapture.Node> nodes,long bytes){return new SnakemakeOriginalSoftwareCapture.Snapshot(1,SnakemakeOriginalSoftwareCapture.SCOPE,Map.of("software",new SnakemakeOriginalSoftwareCapture.Tree("/unavailable/source-prefix",nodes)),bytes,nodes.size());}
    @Test void literalContainerLinksAndSpecialMetadataDecodeWithoutHostResolution()throws Exception {
        String target="/unavailable/container-root/usr/bin/awk";var saved=snapshot(Map.of("",node("directory",null,null),"link",node("symlink",null,target),"fifo",node("fifo",null,null)),target.length());assertEquals(saved,SnakemakeOriginalSoftwareCapture.decode(encoded(saved),LIMITS));
    }
    @Test void independentlyRecomputedTotalsCannotHideConflictingSharedBlobLengths()throws Exception {
        String hash="0".repeat(64);var saved=snapshot(Map.of("",node("directory",null,null),"a",node("file",new SnakemakeOriginalFiles.Pin(1,hash),null),"b",node("file",new SnakemakeOriginalFiles.Pin(2,hash),null)),3);assertThrows(IOException.class,()->SnakemakeOriginalSoftwareCapture.decode(encoded(saved),LIMITS));
    }
    @Test void completeSoftwareParentCensusCannotBeOmitted()throws Exception {
        var saved=snapshot(Map.of("",node("directory",null,null),"missing/child",node("directory",null,null)),0);assertThrows(IOException.class,()->SnakemakeOriginalSoftwareCapture.decode(encoded(saved),LIMITS));
    }
    @Test void aChangedCapturedSoftwareObjectCannotPassWithAnUnchangedRecord()throws Exception {
        byte[] original={1,2,3};var pin=new SnakemakeOriginalFiles.Pin(original.length,SnakemakeOriginalFiles.sha(original));var saved=snapshot(Map.of("",node("directory",null,null),"value",node("file",pin,null)),original.length);Files.createDirectory(temp.resolve("objects"));Path object=Files.write(temp.resolve("objects").resolve(pin.sha256()),original);assertDoesNotThrow(()->SnakemakeOriginalSoftwareCapture.verifyObjects(temp,saved,LIMITS,()->{}));Files.write(object,new byte[]{3,2,1});assertThrows(IOException.class,()->SnakemakeOriginalSoftwareCapture.verifyObjects(temp,saved,LIMITS,()->{}));
    }
    @Test void specialMetadataCannotClaimOrdinaryFileBytesOrAnUnknownKind()throws Exception {
        for(String kind:java.util.List.of("fifo","socket","unqualified-object")){var saved=snapshot(Map.of("",node("directory",null,null),"object",node(kind,new SnakemakeOriginalFiles.Pin(1,"0".repeat(64)),null)),1);assertThrows(IOException.class,()->SnakemakeOriginalSoftwareCapture.decode(encoded(saved),LIMITS),kind);}
    }
}
