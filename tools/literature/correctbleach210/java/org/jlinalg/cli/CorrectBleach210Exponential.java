/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import emblcmci.BleachCorrection_ExpoFit;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.measure.CurveFitter;
import ij.measure.Minimizer;
import ij.plugin.Duplicator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/** Observes the fitter actually used by unchanged original core(), with no second fit. */
final class CorrectBleach210Exponential {
    private ImagePlus corrected;
    private Observed original;
    private int timePoints;
    ImagePlus partial(){return corrected;}
    ImagePlus correct(ImagePlus input){
        // Literal BleachCorrection.doCorrection preparation; independent controls use its published dispatcher.
        Roi roi=input.getRoi();
        if(roi!=null){var rect=roi.getBounds();System.out.println("(x,y)=("+rect.x+","+rect.y);System.out.println("Width="+rect.width);System.out.println("Height="+rect.height);}else System.out.println("No ROI");
        input.killRoi();corrected=new Duplicator().run(input);if(roi!=null)corrected.setRoi(roi);
        int[] dims=corrected.getDimensions();timePoints=dims[3]>1&&dims[4]>1?dims[4]:corrected.getStackSize();
        original=new Observed(corrected,roi);original.setHeadlessProcessing(true);original.core();return corrected;
    }
    Map<String,Object> state(){
        var result=new LinkedHashMap<String,Object>();result.put("original_core_executed",original!=null);result.put("observer_overrides_only",java.util.List.of("dcayFitting","decayFitting3D"));result.put("second_fit_performed",false);
        CurveFitter fit=original==null?null:original.used;result.put("fitter_returned",fit!=null);result.put("reportable_fit",false);
        if(fit==null)return result;
        result.put("fit_type",fit.getFit());result.put("status_code",fit.getStatus());result.put("status_text",fit.getStatusString());result.put("iterations",fit.getIterations());result.put("maximum_iterations",fit.getMaxIterations());
        double[] params=fit.getParams();result.put("parameters_including_residual_sum",values(params));result.put("x",values(fit.getXPoints()));result.put("y",values(fit.getYPoints()));result.put("residuals",values(fit.getResiduals()));result.put("r_squared",values(new double[]{fit.getRSquared()}));result.put("fit_goodness",values(new double[]{fit.getFitGoodness()}));result.put("standard_deviation",values(new double[]{fit.getSD()}));
        boolean finite=params.length>=3;for(double p:params)finite&=Double.isFinite(p);
        var predicted=new double[timePoints];var factors=new double[timePoints];
        if(params.length>=3){double reference=original.calcExponentialOffset(params[0],params[1],params[2],0.0);for(int i=0;i<timePoints;i++){predicted[i]=original.calcExponentialOffset(params[0],params[1],params[2],(double)i);factors[i]=reference/predicted[i];finite&=Double.isFinite(predicted[i])&&Double.isFinite(factors[i]);}}
        else{java.util.Arrays.fill(predicted,Double.NaN);java.util.Arrays.fill(factors,Double.NaN);}
        result.put("derived_fitted_intensities",values(predicted));result.put("derived_correction_factors",values(factors));result.put("all_model_parameters_and_correction_factors_finite",finite);result.put("reportable_fit",fit.getStatus()==Minimizer.SUCCESS&&finite);return result;
    }
    private static Object values(double[] values){
        var rows=new ArrayList<Map<String,String>>(values.length);for(double value:values){var row=new LinkedHashMap<String,String>();row.put("value",Double.toString(value));row.put("raw_bits_hex",Long.toHexString(Double.doubleToRawLongBits(value)));rows.add(row);}return rows;
    }
    private static final class Observed extends BleachCorrection_ExpoFit {
        private CurveFitter used;
        Observed(ImagePlus image,Roi roi){super(image,roi);}
        @Override public CurveFitter dcayFitting(){used=super.dcayFitting();return used;}
        @Override public CurveFitter decayFitting3D(int z,int t){used=super.decayFitting3D(z,t);return used;}
    }
}
