/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.plugin.Duplicator;
import org.jlinalg.imaging.CorrectBleachHistogram210;
import java.io.DataOutputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Complete native port of CorrectBleach2.1.0 histogram correction; original ImageJ pixel operations. */
final class CorrectBleach210NativeHistogram {
    private ImagePlus corrected;
    ImagePlus partial(){return corrected;}
    ImagePlus correct(ImagePlus input,long comparisons,Path mapFile)throws IOException {
        Roi roi=input.getRoi();input.killRoi();corrected=new Duplicator().run(input);if(roi!=null)corrected.setRoi(roi);
        int[] dims=corrected.getDimensions();boolean volume=dims[3]>1&&dims[4]>1;int z=volume?dims[3]:1,t=volume?dims[4]:corrected.getStackSize();
        IJ.log("slices"+dims[3]+"  -- frames"+dims[4]);
        if(volume&&z*t!=corrected.getStackSize()){IJ.showMessage("slice and time frames do not match with the length of the stack. Please correct!");return corrected;}
        int bins=corrected.getBitDepth()==8?256:65536;int[] reference=new int[bins];
        for(int plane=0;plane<z;plane++){int[] histogram=corrected.getStack().getProcessor(plane+1).getHistogram();for(int k=0;k<bins;k++)reference[k]+=histogram[k];}
        try(var maps=new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(mapFile),65536))){
            maps.writeUTF("CORRECTBLEACH210_COMPLETE_HISTOGRAM_MAPS_V1");maps.writeInt(bins);maps.writeInt(t-1);
            for(int time=1;time<t;time++){
                int[] target=new int[bins];for(int plane=0;plane<z;plane++){int[] histogram=corrected.getStack().getProcessor(time*z+plane+1).getHistogram();for(int k=0;k<bins;k++)target[k]+=histogram[k];}
                int[] mapping=CorrectBleachHistogram210.matchHistograms(target,reference,comparisons);maps.writeInt(time+1);for(int value:mapping)maps.writeInt(value);
                for(int plane=0;plane<z;plane++)corrected.getStack().getProcessor(time*z+plane+1).applyTable(mapping);
                IJ.log((volume?"corrected time point: ":"corrected frame: ")+(time+1));
            }
        }
        return corrected;
    }
}
