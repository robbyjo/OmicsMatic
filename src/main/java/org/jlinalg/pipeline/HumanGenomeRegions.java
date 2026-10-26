/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.pipeline;

/** GRC 1-based closed human PAR intervals; X and Y PAR coordinates are distinct. */
public final class HumanGenomeRegions {
    private HumanGenomeRegions() { }
    public enum Region { AUTOSOME, X_PAR1, X_PAR2, X_NONPAR, Y_PAR1, Y_PAR2, Y_NONPAR, OTHER }
    public static Region classify(String build,String chromosome,long position) {
        if(!build.equals("GRCh37")&&!build.equals("GRCh38"))throw new IllegalArgumentException("Human ploidy requires GRCh37 or GRCh38");
        String chr=chromosome.replaceFirst("(?i)^chr","").toUpperCase(java.util.Locale.ROOT);
        if(VariantQualityDiagnostics.autosomal(chr))return Region.AUTOSOME;
        boolean b37=build.equals("GRCh37");
        if(chr.equals("X")||chr.equals("23")||chr.equals("PAR1")||chr.equals("PAR2")) {
            if(position<1||position>(b37?155270560L:156040895L))return Region.OTHER;
            Region result=between(position,b37?60001:10001,b37?2699520:2781479)?Region.X_PAR1:between(position,b37?154931044:155701383,b37?155260560:156030895)?Region.X_PAR2:Region.X_NONPAR;
            if(chr.startsWith("PAR")&&!chr.equals(result==Region.X_PAR1?"PAR1":result==Region.X_PAR2?"PAR2":""))return Region.OTHER;
            return result;
        }
        if(chr.equals("Y")||chr.equals("24")) {
            if(position<1||position>(b37?59373566L:57227415L))return Region.OTHER;
            return between(position,10001,b37?2649520:2781479)?Region.Y_PAR1:between(position,b37?59034050:56887903,b37?59363566:57217415)?Region.Y_PAR2:Region.Y_NONPAR;
        }
        return Region.OTHER;
    }
    private static boolean between(long n,long start,long end){return n>=start&&n<=end;}
}
