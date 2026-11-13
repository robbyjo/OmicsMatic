/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
import emblcmci.BleachCorrection;
import ij.IJ;
import org.scijava.util.VersionUtils;

/** Link every declared runtime package and version without creating or correcting an image. */
public final class Readiness {
    private Readiness() { }
    public static void main(String[] args)throws Exception {
        if(!IJ.getVersion().equals("1.53t")||!"2.1.0".equals(VersionUtils.getVersion(BleachCorrection.class)))throw new IllegalStateException("Original runtime edition differs");
        for(String name:new String[]{"emblcmci.BleachCorrection_SimpleRatio","emblcmci.BleachCorrection_ExpoFit","emblcmci.BleachCorrection_MH","histogram2.HistogramMatcher","org.scijava.parsington.ExpressionParser","org.bushe.swing.event.EventBus"})Class.forName(name,false,Readiness.class.getClassLoader());
        System.out.println("READY_ORIGINAL_CORRECTBLEACH2.1.0_IMAGEJ1.53t_NO_ANALYSIS headless="+java.awt.GraphicsEnvironment.isHeadless());
    }
}
