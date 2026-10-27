/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/** Functional records retain each feature, transcript, track, model and source context. */
final class StudySuppliedVariantRows {
    private StudySuppliedVariantRows() { }
    private static final List<String> DIMENSIONS=List.of("dataset_id","feature_id","kind","gene_id","transcript_id","consequence","mode","scorer","ontology_curie","track_id","interval","model_version","resource_version","source_record","input_source","source_operation_id");
    private static final Set<String> EXTRA=Set.of("kind","mode","track_id","interval","source_record","input_source","source_operation_id");
    static boolean applies(String kind){return Set.of("variant_annotation","variant_scoring").contains(kind);}
    static boolean retain(String field){return EXTRA.contains(field);}
    static void validate(List<String> header,String[] row,List<String> identity) throws IOException {
        for(String field:DIMENSIONS)if(header.contains(field)){
            String value=row[header.indexOf(field)];
            if(value.chars().anyMatch(Character::isISOControl))throw new IOException("Invalid supplied variant context: "+field);
            identity.add(Set.of("","NA",".").contains(value)?"":field.equals("source_record")?StudyFindingInventory.hash(value):value);
        }
        if(header.contains("quantile_score")){
            double value=StudyCohortResults.numeric(row[header.indexOf("quantile_score")]);
            if(Double.isFinite(value)&&(value< -1||value>1))throw new IOException("Supplied signed quantile_score must lie in [-1,1]");
        }
    }
}
