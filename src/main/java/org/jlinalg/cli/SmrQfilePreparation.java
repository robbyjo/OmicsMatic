/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import jdistlib.ChiSquare;
import static org.jlinalg.cli.Monocle3WorkflowRequest.require;

/** Independent version-specific preparation arithmetic; raw supplied SE/P are retained separately. */
final class SmrQfilePreparation {
    private SmrQfilePreparation() { }
    record Stored(float beta,float se,String branch,boolean suppliedSeChanged) { }
    static Stored store(String version,double beta,double suppliedSe,Double suppliedP)throws IOException {
        require(version.equals("1.3.1")||version.equals("1.4.3"),"Unsupported declared Qfile preparation version");double effectiveSe=suppliedSe;String branch="source-supplied-SE";
        if(version.equals("1.3.1")&&suppliedP!=null){
            double p=suppliedP==0?Double.MIN_NORMAL:suppliedP;
            if(p>=1e-150&&p<1){double chi=ChiSquare.quantile(p,1,false,false);effectiveSe=Math.abs(beta)/Math.sqrt(chi);branch="original1.3.1-SE-derived-from-beta-and-P";}
            else branch=suppliedP==0?"original1.3.1-P0-adjusted-to-min-normal-then-supplied-SE":"original1.3.1-extreme-P-or-P1-keeps-supplied-SE";
        }
        float b=(float)beta,se=(float)effectiveSe;require(Float.isFinite(b)&&Float.isFinite(se)&&se>0&&Math.abs(b+9)>1e-6,"Original version-specific Qfile beta/SE preparation is unrepresentable, nonpositive or a sentinel; no replacement");return new Stored(b,se,branch,Float.floatToRawIntBits(se)!=Float.floatToRawIntBits((float)suppliedSe));
    }
}
