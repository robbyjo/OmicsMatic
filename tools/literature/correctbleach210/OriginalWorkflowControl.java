/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
import emblcmci.BleachCorrection;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.OvalRoi;
import ij.gui.Overlay;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.io.FileSaver;
import ij.io.Opener;
import ij.io.RoiDecoder;
import ij.io.RoiEncoder;
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.ShortProcessor;
import org.scijava.util.VersionUtils;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Independent original-release fixture generation and complete public correction API calls. */
public final class OriginalWorkflowControl {
    private OriginalWorkflowControl() { }
    public static void main(String[] args)throws Exception {
        if(!IJ.getVersion().equals("1.53t")||!"2.1.0".equals(VersionUtils.getVersion(BleachCorrection.class)))throw new IllegalStateException("Wrong original version");
        if(args[0].equals("generate")){generate(Path.of(args[1]));System.exit(0);}
        Path out=Path.of(args[2]);BleachCorrection original=new BleachCorrection();
        try{
            ImagePlus image=new Opener().openImage(args[1]);if(image==null)throw new IllegalStateException("Original TIFF decode failed");String method=args[3],roi=args[5];if(roi.equals("NONE"))image.killRoi();else if(!roi.equals("IMAGE_ROI"))image.setRoi(new RoiDecoder(roi).getRoi());
            BleachCorrection.setCorrectionMethod(switch(method){case "SIMPLE_RATIO"->BleachCorrection.SIMPLE_RATIO;case "EXPONENTIAL_FIT"->BleachCorrection.EXPONENTIAL_FIT;case "HISTOGRAM_MATCHING"->BleachCorrection.HISTOGRAM_MATCHING;default->throw new IllegalArgumentException("Unknown method");});original.setHeadlessProcessing(true);if(method.equals("SIMPLE_RATIO"))original.setSimpleRatioBaseline(Double.parseDouble(args[4]));ImagePlus result=original.doCorrection(image);if(result==null)throw new IllegalStateException("Original returned null");save(result,out.resolve("corrected.tif"));Files.writeString(out.resolve("original-return.txt"),"RETURNED_COMPLETE_ORIGINAL_IMAGE\n"+IJ.getVersion()+"\n"+VersionUtils.getVersion(BleachCorrection.class)+"\nroi_after="+(image.getRoi()==null?"null":image.getRoi().toString())+"\n",StandardCharsets.UTF_8);System.exit(0);
        }catch(Throwable e){try(var writer=new PrintWriter(Files.newBufferedWriter(out.resolve("original-failure.txt"),StandardCharsets.UTF_8))){e.printStackTrace(writer);}if(original.getCorrectedImagePlus()!=null)save(original.getCorrectedImagePlus(),out.resolve("unadmitted-original-image.tif"));e.printStackTrace(System.err);System.exit(2);}
    }
    private static void generate(Path out)throws Exception {
        Files.createDirectory(out);make(out,"byte2d",8,31,23,1,1,12,"DECAY");make(out,"ushort2d",16,29,19,1,1,10,"DECAY");make(out,"byte3d",8,23,17,1,3,8,"DECAY");make(out,"ushort3d",16,19,13,1,3,7,"DECAY");make(out,"linear-stack",8,17,13,1,9,1,"DECAY");make(out,"channels-linear",8,13,11,2,1,6,"DECAY");make(out,"zero",8,11,9,1,1,5,"ZERO");make(out,"increasing",8,17,13,1,1,8,"INCREASING");make(out,"channels-volume",8,13,11,2,3,4,"DECAY");make(out,"two-point",8,17,13,1,1,2,"DECAY");
        Roi selected=new PolygonRoi(new int[]{2,15,22,18,4},new int[]{2,2,8,15,12},5,Roi.POLYGON);selected.setName("literal-external-polygon");new RoiEncoder(out.resolve("selected.roi").toString()).write(selected);new RoiEncoder(out.resolve("region-original-017.roi").toString()).write(new Roi(3,2,11,9));ImageStack floatStack=new ImageStack(7,5);for(int z=0;z<3;z++)floatStack.addSlice(new FloatProcessor(7,5,new float[35]));save(new ImagePlus("float-menu-unsupported",floatStack),out.resolve("float.tif"));save(new ImagePlus("single-menu-unsupported",new ByteProcessor(9,7)),out.resolve("single.tif"));
    }
    private static void make(Path out,String name,int bits,int width,int height,int channels,int slices,int frames,String kind)throws Exception {
        ImageStack stack=new ImageStack(width,height);int index=0;
        for(int t=0;t<frames;t++)for(int z=0;z<slices;z++)for(int c=0;c<channels;c++){
            int time=frames>1?t:index;double decay=Math.exp(-0.12*time);int baseline=bits==8?7:75;short[] shorts=bits==16?new short[width*height]:null;byte[] bytes=bits==8?new byte[width*height]:null;
            for(int y=0;y<height;y++)for(int x=0;x<width;x++){
                double spatial=bits==8?90+(x*7+y*11+z*9+c*13)%91:18000+(x*337+y*593+z*1709+c*2203)%27000;double value=kind.equals("ZERO")?0:kind.equals("INCREASING")?20+time*15+(x+y)%9:baseline+spatial*decay+(x*3+y*5+time)%5;
                if(bytes!=null)bytes[y*width+x]=(byte)Math.max(0,Math.min(255,(int)Math.round(value)));else shorts[y*width+x]=(short)Math.max(0,Math.min(65535,(int)Math.round(value)));
            }
            stack.addSlice("literal-C"+(c+1)+"-Z"+(z+1)+"-T"+(t+1),bits==8?new ByteProcessor(width,height,bytes):new ShortProcessor(width,height,shorts,null));index++;
        }
        ImagePlus image=new ImagePlus(name,stack);image.setDimensions(channels,slices,frames);image.setOpenAsHyperStack(channels>1||frames>1);var cal=image.getCalibration();cal.pixelWidth=0.2;cal.pixelHeight=0.3;cal.pixelDepth=1.75;cal.frameInterval=2.0;cal.setUnit("micron");cal.setTimeUnit("sec");cal.xOrigin=0.25;cal.yOrigin=-0.5;cal.zOrigin=1.25;image.setRoi(new PolygonRoi(new int[]{1,width-3,width-4,2},new int[]{1,2,height-3,height-2},4,Roi.POLYGON));Overlay overlay=new Overlay();OvalRoi oval=new OvalRoi(2,2,5,4);oval.setName("literal-overlay");overlay.add(oval);image.setOverlay(overlay);image.setProperty("Info","Complete synthetic bleaching control; literal C/Z/T axes, ROI and calibration. No biological inference.");save(image,out.resolve(name+".tif"));
    }
    private static void save(ImagePlus image,Path path)throws Exception {FileSaver saver=new FileSaver(image);if(!(image.getStackSize()>1?saver.saveAsTiffStack(path.toString()):saver.saveAsTiff(path.toString())))throw new IllegalStateException("Original complete TIFF export failed");}
}
