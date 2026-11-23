/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import static org.jlinalg.cli.GprofilerWorkflowContracts.*;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;
import org.jlinalg.gprofiler.GprofilerJson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class SquidpyWorkflowNpyTest {
    @TempDir Path temp;
    @Test void originalNumPyFilesPreserveEveryPayloadBitAndAxis()throws Exception {
        var members=new HashMap<String,byte[]>();
        try(var input=getClass().getResourceAsStream("/org/jlinalg/squidpy/primitive-npy-original.zip")){
            assertNotNull(input);try(var archive=new ZipInputStream(input)){
                ZipEntry member;while((member=archive.getNextEntry())!=null)assertNull(members.put(member.getName(),archive.readAllBytes()));
            }
        }
        var provenance=object(GprofilerJson.read(members.get("PROVENANCE.json")));
        assertEquals(true,provenance.get("reference_generated_without_Java_or_draft_reader"));var cases=list(provenance.get("cases"));assertEquals(661,cases.size());
        Path path=temp.resolve("reference.npy");
        for(Object value:cases){
            var c=object(value);byte[] raw=members.get(text(c.get("file"),"reference"));assertNotNull(raw);
            assertEquals(text(c.get("file_sha256"),"SHA"),HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw)));
            Files.write(path,raw);var array=SquidpyWorkflowNpy.inspect(path,65536,1000000,1000000);
            var expected=new ArrayList<Integer>();for(Object dimension:list(c.get("shape")))expected.add((int)integer(dimension,0,Integer.MAX_VALUE));
            assertEquals(expected,array.shape());assertEquals(c.get("descriptor"),array.descriptor());assertEquals(c.get("dtype"),array.dtype());assertEquals(c.get("fortran"),array.fortran());
            assertEquals(integer(c.get("elements"),0,1000000),array.elements());assertEquals(integer(c.get("payload_bytes"),0,1000000),array.payloadBytes());assertEquals(c.get("payload_sha256"),array.payloadSha256());
        }
    }
    private Path array(String expression,byte[] payload)throws IOException {
        byte[] header=(expression+"\n").getBytes(StandardCharsets.US_ASCII);
        var out=new ByteArrayOutputStream();out.write(new byte[]{(byte)147,78,85,77,80,89,1,0});out.write(header.length&255);out.write(header.length>>>8);out.write(header);out.write(payload);
        Path path=temp.resolve("array.npy");Files.write(path,out.toByteArray());return path;
    }
    @Test void multidimensionalCapacityIsCheckedWithoutAllocatingAnArray()throws IOException {
        Path p=array("{'descr':'|u1','fortran_order':False,'shape':(2,3,4)}",new byte[24]);
        assertEquals(24,SquidpyWorkflowNpy.inspect(p,65536,1000,24).elements());
        assertThrows(IOException.class,()->SquidpyWorkflowNpy.inspect(p,65536,1000,23));
        assertThrows(IOException.class,()->SquidpyWorkflowNpy.inspect(p,65536,10,24));
        assertThrows(IOException.class,()->SquidpyWorkflowNpy.inspect(p,4,1000,24));
        Path overflow=array("{'descr':'<u8','fortran_order':False,'shape':(2147483647,2147483647,2147483647)}",new byte[0]);
        assertThrows(IOException.class,()->SquidpyWorkflowNpy.inspect(overflow,65536,1000,Long.MAX_VALUE));
    }
    @Test void malformedHeadersPicklesStructuredTypesAndIncompletePayloadsAreRefused()throws IOException {
        for(String header:List.of(
            "{'descr':'|O8','fortran_order':False,'shape':()}",
            "{'descr':[('x','<i4')],'fortran_order':False,'shape':()}",
            "{'descr':'|u2','fortran_order':False,'shape':()}",
            "{'descr':'<f2','fortran_order':False,'shape':()}",
            "{'descr':'<i+8','fortran_order':False,'shape':()}",
            "{'descr':'|u1','descr':'|u1','fortran_order':False,'shape':()}",
            "{'descr':'|u1','fortran_order':False,'shape':(1)}",
            "{'descr':'|u1','fortran_order':False,'shape':(-1,)}",
            "{'descr':'|u1','fortran_order':False,'shape':("+"1,".repeat(17)+")}")){
            Path p=array(header,new byte[8]);assertThrows(IOException.class,()->SquidpyWorkflowNpy.inspect(p,65536,1000,1000),header);
        }
        Path p=array("{'descr':'<i4','fortran_order':False,'shape':(2,)}",new byte[7]);
        assertThrows(IOException.class,()->SquidpyWorkflowNpy.inspect(p,65536,1000,1000));
        Path trailing=array("{'descr':'<i4','fortran_order':False,'shape':(2,)}",new byte[9]);
        assertThrows(IOException.class,()->SquidpyWorkflowNpy.inspect(trailing,65536,1000,1000));
    }
}
