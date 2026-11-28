/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.genetics.CaddCalibration;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;

/** Raw-score calibration from the entire captured explicitly bound conversion table. */
final class CaddToolsCalibration {
    private CaddToolsCalibration(){ }
    static Map<String,Object> render(Path root,Map<String,Object> r,boolean compare)throws IOException {
        Path table=root.resolve(text(r,"conversion_table"));if(!CaddToolsFiles.pin(table,CaddToolsRequest.number(r,"maximum_resource_bytes")).get("sha256").equals(r.get("conversion_sha256")))throw new IOException("Complete CADD conversion-table hash differs");CaddCalibration calibration;
        try(var reader=Files.newBufferedReader(table,StandardCharsets.UTF_8)){calibration=CaddCalibration.read(reader,(int)CaddToolsRequest.number(r,"maximum_table_rows"),(int)CaddToolsRequest.number(r,"maximum_line_characters"));}
        long rows=0,nonfinite=0;try(var input=new CaddToolsFiles.Lines(root.resolve(text(r,"input_scores")),r);var output=new CaddToolsFiles.Output(root.resolve("results"),r,compare);var writer=output.writer("calibrated.tsv")){
            if(!"id\traw_score".equals(input.next()))throw new IOException("Require exact id/raw_score two-column UTF-8 TSV header");CaddToolsFiles.row(writer,"input_row","id","original_raw_score","source_formatted_raw_score","PHRED","state");String line;
            while((line=input.next())!=null){if(++rows>CaddToolsRequest.number(r,"maximum_variants"))throw new IOException("Complete CADD raw-score row capacity exceeded");String[] fields=line.split("\t",-1);if(fields.length!=2||fields[0].isEmpty()||fields[1].isEmpty())throw new IOException("Require literal ID and raw score for every row");double raw=CaddCalibration.parse(fields[1]);if(!Double.isFinite(raw))nonfinite++;CaddToolsFiles.row(writer,rows,fields[0],fields[1],CaddCalibration.sourceRaw(raw),calibration.phred(raw),Double.isFinite(raw)?"CALIBRATED_RAW_SCORE":"NONFINITE_RAW_UNASSESSABLE");}
        }
        return Map.of("rows",rows,"conversion_boundaries",calibration.boundaries(),"nonfinite_unassessable_rows",nonfinite,"prediction_model_executed",false,"whole_paper_reproduced",false);
    }
}
