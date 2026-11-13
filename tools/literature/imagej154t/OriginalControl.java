/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
import ij.CompositeImage;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.OvalRoi;
import ij.gui.Overlay;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.io.FileSaver;
import ij.io.Opener;
import ij.macro.Interpreter;
import ij.measure.Calibration;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.FloatProcessor;
import ij.process.LUT;
import ij.process.ShortProcessor;
import java.awt.Color;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Independent direct original Java fixture generation and unmodified macro execution. */
public final class OriginalControl {
    private OriginalControl() { }
    public static void main(String[] args)throws Exception {
        try{execute(args);}catch(java.awt.HeadlessException|java.awt.AWTError|UnsatisfiedLinkError e){System.err.println("SKIP3_ORIGINAL_AWT_PREREQUISITE_UNAVAILABLE: "+e);System.exit(3);}
        System.exit(0);
    }
    private static void execute(String[] args)throws Exception {
        if(!IJ.getVersion().equals("1.54t"))throw new IllegalStateException("Wrong original core");
        if(args[0].equals("generate"))generate(Path.of(args[1]));
        else if(args[0].equals("run")){
            Path macro=Path.of(args[1]),work=Path.of(args[2]);Interpreter original=new Interpreter();String returned=original.run(Files.readString(macro,StandardCharsets.UTF_8),work.toString().replace('\\','/'));
            if(original.wasError()||original.getErrorMessage()!=null)throw new IllegalStateException(original.getErrorMessage());
            Files.writeString(work.resolve("original-return.txt"),String.valueOf(returned),StandardCharsets.UTF_8);
        }else if(args[0].equals("dump"))dump(Path.of(args[1]),Path.of(args[2]));
        else throw new IllegalArgumentException("Unknown original control action");
    }
    private static void generate(Path out)throws Exception {
        Files.createDirectory(out);ImageStack bytes=new ImageStack(9,7);
        for(int z=0;z<3;z++){byte[] p=new byte[63];for(int i=0;i<p.length;i++)p[i]=(byte)((i*7+z*19)%181);p[31]=(byte)255;bytes.addSlice("byte-plane-"+z,new ByteProcessor(9,7,p));}save(new ImagePlus("byte-stack",bytes),out.resolve("byte.tif"));
        ImageStack shorts=new ImageStack(7,5);for(int t=0;t<2;t++)for(int z=0;z<3;z++)for(int c=0;c<2;c++){short[] p=new short[35];for(int i=0;i<p.length;i++)p[i]=(short)(c*1100+z*170+t*310+i*23);shorts.addSlice("C"+(c+1)+"-Z"+(z+1)+"-T"+(t+1),new ShortProcessor(7,5,p,null));}
        ImagePlus hyper=new ImagePlus("literal-hyperstack",shorts);hyper.setDimensions(2,3,2);hyper.setOpenAsHyperStack(true);Calibration cal=hyper.getCalibration();cal.pixelWidth=0.25;cal.pixelHeight=0.5;cal.pixelDepth=1.75;cal.setUnit("micron");cal.frameInterval=2.5;cal.setTimeUnit("sec");cal.xOrigin=1.25;cal.yOrigin=-0.5;cal.zOrigin=2.0;cal.setFunction(Calibration.STRAIGHT_LINE,new double[]{1.25,0.5},"calibrated-intensity");
        CompositeImage composite=new CompositeImage(hyper,CompositeImage.COMPOSITE);composite.setChannelLut(LUT.createLutFromColor(Color.RED),1);composite.setChannelLut(LUT.createLutFromColor(Color.GREEN),2);composite.setRoi(new PolygonRoi(new int[]{1,5,5,1},new int[]{1,1,3,3},4,Roi.POLYGON));Overlay overlay=new Overlay();OvalRoi oval=new OvalRoi(1,1,3,2);oval.setName("literal-oval");overlay.add(oval);composite.setOverlay(overlay);composite.setProperty("Info","Synthetic control; all C/Z/T planes and explicit calibration retained.");save(composite,out.resolve("ushort.tif"));
        ImageStack floats=new ImageStack(7,5);for(int z=0;z<3;z++){float[] p=new float[35];for(int i=0;i<p.length;i++)p[i]=(float)((i-17)*0.125+z*0.0625);p[z]=Float.intBitsToFloat(0x7fc00031+z);p[4]=Float.POSITIVE_INFINITY;p[5]=Float.NEGATIVE_INFINITY;p[6]=-0.0f;floats.addSlice("float-plane-"+z,new FloatProcessor(7,5,p));}save(new ImagePlus("float-stack",floats),out.resolve("float.tif"));
        ImageStack rgb=new ImageStack(5,4);for(int z=0;z<2;z++){int[] p=new int[20];for(int i=0;i<p.length;i++)p[i]=((i*11+z*17)%256)<<16|((i*7+z*31)%256)<<8|((i*19+z*43)%256);rgb.addSlice("rgb-plane-"+z,new ColorProcessor(5,4,p));}save(new ImagePlus("rgb-stack",rgb),out.resolve("rgb.tif"));
        byte[] particles=new byte[63];for(int y=1;y<=2;y++)for(int x=1;x<=2;x++)particles[y*9+x]=(byte)255;for(int y=3;y<=5;y++)for(int x=5;x<=7;x++)particles[y*9+x]=(byte)255;save(new ImagePlus("two-particles",new ByteProcessor(9,7,particles)),out.resolve("particles.tif"));
    }
    private static void save(ImagePlus image,Path path)throws Exception {FileSaver saver=new FileSaver(image);if(!(image.getStackSize()>1?saver.saveAsTiffStack(path.toString()):saver.saveAsTiff(path.toString())))throw new IllegalStateException("Original TIFF saving failed");}
    private static void dump(Path file,Path out)throws Exception {
        ImagePlus image=new Opener().openImage(file.toString());if(image==null)throw new IllegalStateException("Original TIFF decoding failed");StringBuilder b=new StringBuilder();int[] d=image.getDimensions();b.append("dimensions");for(int v:d)b.append('\t').append(v);b.append("\nbit-depth\t").append(image.getBitDepth()).append('\n');Calibration c=image.getCalibration();b.append("calibration\t").append(Double.toHexString(c.pixelWidth)).append('\t').append(Double.toHexString(c.pixelHeight)).append('\t').append(Double.toHexString(c.pixelDepth)).append('\t').append(Double.toHexString(c.frameInterval)).append('\t').append(Double.toHexString(c.xOrigin)).append('\t').append(Double.toHexString(c.yOrigin)).append('\t').append(Double.toHexString(c.zOrigin)).append('\t').append(c.getXUnit()).append('\t').append(c.getYUnit()).append('\t').append(c.getZUnit()).append('\t').append(c.getTimeUnit()).append('\n');
        for(int i=1;i<=image.getStackSize();i++){b.append("slice\t").append(i).append('\t').append(image.getStack().getSliceLabel(i)).append('\n');Object p=image.getStack().getPixels(i);if(p instanceof byte[] a)for(byte x:a)b.append(Integer.toHexString(x&255)).append('\n');else if(p instanceof short[] a)for(short x:a)b.append(Integer.toHexString(x&65535)).append('\n');else if(p instanceof float[] a)for(float x:a)b.append(Integer.toHexString(Float.floatToRawIntBits(x))).append('\n');else if(p instanceof int[] a)for(int x:a)b.append(Integer.toHexString(x)).append('\n');else throw new IllegalStateException("Unexpected original pixel type");}
        Files.writeString(out,b.toString(),StandardCharsets.UTF_8);
    }
}
