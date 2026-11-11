/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.metagenomics;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MaltSupportTest {
    private Map<String,String> fixture()throws Exception {
        var files=new HashMap<String,String>();
        try(var zip=new ZipInputStream(Objects.requireNonNull(getClass().getResourceAsStream("/malt-support-controls.zip")))) {
            for(var entry=zip.getNextEntry();entry!=null;entry=zip.getNextEntry()) {
                byte[] raw=zip.readNBytes(16777217);assertTrue(raw.length<=16777216);assertFalse(files.containsKey(entry.getName()));files.put(entry.getName(),new String(raw,StandardCharsets.UTF_8));
            }
        }
        return files;
    }
    @Test void allSmallOriginalBytecodeReferencesMatchIncludingFloatOrderAndPrunedSubtrees()throws Exception {
        var data=fixture();var trees=new HashMap<String,MaltSupport>();
        for(String line:data.get("trees.tsv").lines().skip(1).toList()) {
            String[] f=line.split("\t",-1);var taxonomy=MaltTaxonomy.read(new StringReader(data.get(f[1])),new MaltTaxonomy.Limits(10000,100,100));trees.put(f[0],new MaltSupport(taxonomy));
        }
        var expected=new HashMap<String,String>();for(String line:data.get("relocations.tsv").lines().skip(1).toList()){String[] f=line.split("\t",-1);assertNull(expected.put(f[0],f[1]));}
        int checked=0;
        for(String line:data.get("cases.tsv").lines().skip(1).toList()) {
            String[] f=line.split("\t",-1);var weights=new HashMap<Integer,Float>();var disabled=new HashSet<Integer>();
            if(!f[3].isEmpty())for(String token:f[3].split(","))disabled.add(Integer.valueOf(token));
            if(!f[4].isEmpty())for(String token:f[4].split(";")){String[] pair=token.split("=",-1);weights.put(Integer.valueOf(pair[0]),pair[1].equals("NULL")?null:Float.valueOf(pair[1]));}
            assertEquals(expected.remove(f[0]),trees.get(f[1]).relocate(weights,Integer.parseInt(f[2]),disabled).toString(),line);checked++;
        }
        assertEquals(8150,checked);assertTrue(expected.isEmpty());assertEquals(6,trees.size());
    }
    @Test void returnedMappingsAndReusableTopologyDoNotShareMutableCallerState()throws Exception {
        var taxonomy=MaltTaxonomy.read(new StringReader("(2,3)1;"),new MaltTaxonomy.Limits(100,100,10));var support=new MaltSupport(taxonomy);
        var weights=new HashMap<Integer,Float>();weights.put(2,1f);var disabled=new HashSet<Integer>();
        var result=support.relocate(weights,2,disabled);assertEquals(Map.of(2,-2),result);assertEquals(Map.of(2,1f),weights);assertTrue(disabled.isEmpty());
        assertThrows(UnsupportedOperationException.class,()->result.put(2,1));weights.put(3,1f);
        assertEquals(Map.of(2,1,3,1),support.relocate(weights,2,disabled));assertEquals(Map.of(2,-2),result);
        weights.remove(3);assertEquals(result,support.relocate(weights,2,disabled));assertEquals(6,taxonomy.size());
        assertThrows(NullPointerException.class,()->support.relocate(null,1,disabled));
        assertThrows(NullPointerException.class,()->support.relocate(weights,1,null));
    }
}
