/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.util.*;
import org.jlinalg.gprofiler.GprofilerJson;
import static org.jlinalg.cli.GprofilerWorkflowContracts.*;

/** Human-readable projections retain bounds, conditional NA and admission flags. */
final class MrPressoWorkflowTables {
    private MrPressoWorkflowTables() { }
    static Map<String,String> tables(Map<String,Object> result)throws IOException {
        var output=new LinkedHashMap<String,String>();var exposures=list(result.get("exposure_names"));
        var main=new StringBuilder("exposure\tanalysis\tbeta\tstandard_error\tstatistic\tp_value\tinference_admitted\tstatus\n");
        for(var pair:List.of(Map.entry("Raw","raw_mr"),Map.entry("Outlier-corrected","corrected_mr"))){
            var fit=object(result.get(pair.getValue()));boolean available=!"SOURCE_NOT_ESTIMATED".equals(fit.get("status"));
            for(int j=0;j<exposures.size();j++){
                main.append(exposures.get(j)).append('\t').append(pair.getKey());
                for(String key:List.of("beta","standard_error","statistic","p_value"))main.append('\t').append(available?cell(list(fit.get(key)).get(j)):"NA");
                main.append('\t').append(fit.get("inference_admitted")).append('\t').append(fit.get("status")).append('\n');
            }
        }
        output.put("mr-results.tsv",main.toString());var global=object(result.get("global_p_value"));
        output.put("global-test.tsv","rss\tp_value\tp_value_kind\texceedances\tdistributions\tinference_admitted\n"+cell(result.get("global_rss"))+"\t"+probability(global)+"\t"+global.get("kind")+"\t"+token(global.get("exceedances"))+"\t"+token(global.get("distributions"))+"\t"+global.get("inference_admitted")+"\n");
        if(Boolean.TRUE.equals(result.get("outlier_test_performed"))){
            var table=new StringBuilder("row_id\tretained_ordinal\tsquared_residual\tbonferroni_p_value\tp_value_kind\tinference_admitted\n");
            for(Object raw:list(result.get("outliers"))){var row=object(raw);var p=object(row.get("p_value"));
                table.append(row.get("row_id")).append('\t').append(token(row.get("retained_ordinal"))).append('\t').append(cell(row.get("squared_residual"))).append('\t').append(probability(p)).append('\t').append(p.get("kind")).append('\t').append(p.get("inference_admitted")).append('\n');
            }
            output.put("outlier-test.tsv",table.toString());
        }
        if(Boolean.TRUE.equals(result.get("distortion_branch_performed"))){
            var row=object(result.get("distortion"));var p=object(row.get("p_value"));boolean calculated="CALCULATED".equals(row.get("status"));
            var table=new StringBuilder("exposure\tdistortion_percent\tp_value\tp_value_kind\tinference_admitted\tstatus\n");
            for(int j=0;j<exposures.size();j++)table.append(exposures.get(j)).append('\t').append(calculated?cell(list(row.get("coefficient_percent")).get(j)):"NA").append('\t').append(calculated?probability(p):"NA").append('\t').append(p.get("kind")).append('\t').append(row.get("inference_admitted")).append('\t').append(row.get("status")).append('\n');
            output.put("distortion-test.tsv",table.toString());
        }
        return output;
    }
    private static String cell(Object raw)throws IOException {
        var value=object(raw);return "FINITE".equals(value.get("kind"))?token(value.get("value")):text(value.get("literal"),"nonfinite literal");
    }
    private static String probability(Map<String,Object> value)throws IOException {
        if("STRICT_UPPER_BOUND".equals(value.get("kind")))return "<"+cell(value.get("reported_bound"));return cell(value.get("internal_source_value"));
    }
    private static String token(Object raw){return raw instanceof GprofilerJson.Numeric n?n.token():raw.toString();}
}
