/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.jlinalg.cli.Monocle131ModelRequest.*;

/** Complete caller-prepared cells-by-features TSV; no transforms or identifier repair. */
final class Rphenograph099Inputs {
    private Rphenograph099Inputs() { }
    record Population(List<String> cells,List<String> features) { }
    static Population read(Path path,int maximumEntries)throws IOException {
        var cells=new ArrayList<String>();var seen=new HashSet<String>();
        try(var reader=Files.newBufferedReader(path,StandardCharsets.UTF_8)){
            String header=reader.readLine();require(header!=null,"Missing complete input header");String[] names=header.split("\t",-1);require(names.length>=2&&names[0].equals("cell_id"),"Require cell_id followed by literal feature IDs");
            var features=new ArrayList<String>();var featureSet=new HashSet<String>();for(int j=1;j<names.length;j++){label(names[j]);require(featureSet.add(names[j]),"Duplicate feature ID; no repair");features.add(names[j]);}
            String line;while((line=reader.readLine())!=null){String[] fields=line.split("\t",-1);require(fields.length==names.length,"Incomplete/excess complete input columns");label(fields[0]);require(seen.add(fields[0]),"Duplicate cell ID; no repair");require((long)(cells.size()+1)*features.size()<=maximumEntries,"Complete input entry capacity exceeded; no prefix clustering");for(int j=1;j<fields.length;j++)numeric(fields[j]);cells.add(fields[0]);}
            require(!cells.isEmpty(),"Empty cell population");return new Population(List.copyOf(cells),List.copyOf(features));
        }
    }
    static double numeric(String token)throws IOException {
        require(token.matches("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?"),"Require explicit finite decimal numeric tokens; no missing/nonfinite/hex repair");
        try{double x=Double.parseDouble(token);require(Double.isFinite(x),"Nonfinite input value; no repair");return x;}catch(NumberFormatException e){throw new IOException("Invalid numeric token",e);}
    }
}
