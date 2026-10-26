/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.util.Locale;
import java.util.Set;

/** Explicit test-only association rows never manufacture effect estimates. */
final class StudyResultShape {
    private StudyResultShape() { }
    static boolean pOnly(DelimitedData table,String[] row) {
        return table.header().contains("result_type")&&"p_value_only".equals(row[table.column("result_type")]);
    }
    static boolean missing(String value){return value==null||value.isBlank()||Set.of("NA","NAN","NULL",".").contains(value.toUpperCase(Locale.ROOT));}
    static boolean missingEffects(DelimitedData table,String[] row) {
        return (!table.header().contains("beta")||missing(row[table.column("beta")]))&&(!table.header().contains("se")||missing(row[table.column("se")]));
    }
}
