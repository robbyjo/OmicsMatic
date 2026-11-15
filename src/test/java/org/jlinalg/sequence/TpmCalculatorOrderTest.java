/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;

class TpmCalculatorOrderTest {
    private static String key(int i) {
        if(i==0)return "";
        if(i%17==0)return "gene α"+i;
        if(i%19==0)return "ENSG00000"+i;
        if(i%23==0)return "RNA/漢字/"+i;
        if(i%31==0)return "A".repeat(64)+i;
        return "transcript:"+i;
    }
    @Test void oneHundredThousandActualOriginalRuntimeHashesAndEveryRehashOrderMatch() throws Exception {
        byte[] archive;
        try(var in=getClass().getResourceAsStream("/org/jlinalg/sequence/tpmcalculator-original-order.zip")) {assertNotNull(in);archive=in.readAllBytes();}
        assertEquals("1dcdb7a5347e9b0785bcf04a57936af0f5a053f3cb4bdeda00eab3724951e848",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(archive)));
        String expected=null;
        try(var zip=new ZipInputStream(new java.io.ByteArrayInputStream(archive))) {
            for(var e=zip.getNextEntry();e!=null;e=zip.getNextEntry())if(e.getName().equals("actual-order.stdout")) expected=new String(zip.readAllBytes(),StandardCharsets.UTF_8);
        }
        assertNotNull(expected);var map=new LinkedHashMap<String,Integer>();for(int i=0;i<100000;i++)map.put(key(i),i);
        var order=new ArrayList<Integer>();int growth=0;
        for(String line:expected.split("\n")) {
            if(line.startsWith("#buckets\t")){growth++;continue;}
            String[] fields=line.split("\t");int index=Integer.parseInt(fields[0]);
            assertEquals(Long.parseUnsignedLong(fields[1]),TpmCalculatorOrder.hash(key(index)),"Original std::hash string index "+index);order.add(index);
        }
        assertEquals(100000,order.size());assertTrue(growth>10);
        assertEquals(order,TpmCalculatorOrder.entries(map).stream().map(java.util.Map.Entry::getValue).toList());
    }
}
