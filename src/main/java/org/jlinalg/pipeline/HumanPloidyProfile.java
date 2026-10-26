/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.pipeline;
import java.util.Set;

/** One prespecified chromosome/complement contrast; declared complement is not inferred sex or karyotype certification. */
public record HumanPloidyProfile(String region,String genomeBuild,String haploidSourceScale,String invalidCalls,String unknownSamples) {
    public HumanPloidyProfile {
        if(!Set.of("autosomes","par","x_nonpar_xx","x_nonpar_xy","y_nonpar_xy").contains(region))throw new IllegalArgumentException("Unknown human ploidy region");
        if(!Set.of("GRCh37","GRCh38").contains(genomeBuild))throw new IllegalArgumentException("Human ploidy supports GRCh37/GRCh38 only");
        if(!Set.of("observed","haploid","diploid").contains(haploidSourceScale))throw new IllegalArgumentException("Haploid source scale must be observed, haploid or diploid");
        if(!Set.of("error","missing").contains(invalidCalls)||!Set.of("error","exclude").contains(unknownSamples))throw new IllegalArgumentException("Invalid human ploidy missing/error policy");
    }
    public boolean haploid(){return region.endsWith("_xy");}
    public boolean includesComplement(String value){return value.equals("XX")||value.equals("XY") ? region.endsWith("_xx")?value.equals("XX"):region.endsWith("_xy")?value.equals("XY"):true:false;}
    public boolean includes(HumanGenomeRegions.Region value){return switch(region){case "autosomes"->value==HumanGenomeRegions.Region.AUTOSOME;case "par"->value==HumanGenomeRegions.Region.X_PAR1||value==HumanGenomeRegions.Region.X_PAR2;case "x_nonpar_xx","x_nonpar_xy"->value==HumanGenomeRegions.Region.X_NONPAR;case "y_nonpar_xy"->value==HumanGenomeRegions.Region.Y_NONPAR;default->false;};}
    public String qualityModel(){return haploid()?"haploid":region.equals("par")?"human_par_"+genomeBuild:region.equals("x_nonpar_xx")?"human_xx_"+genomeBuild:"diploid";}
    public String interpretation(){return "Prespecified "+region+" contrast on "+genomeBuild+". Dosage counts physical alternate copies (0..1 for haploid, 0..2 for diploid). Declared XX/XY complements are assumptions, not inferred biological sex, gender or certified karyotypes. PAR uses X-coordinate copies; duplicated Y-PAR records are outside scope. Different contrasts are separate estimands, not automatic independent replication or a pooled X-inactivation model.";}
}
