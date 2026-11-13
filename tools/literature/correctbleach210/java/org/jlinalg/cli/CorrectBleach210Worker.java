/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import emblcmci.BleachCorrection;
import ij.IJ;
import ij.ImagePlus;
import ij.io.FileSaver;
import ij.io.RoiDecoder;
import org.scijava.util.VersionUtils;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import static org.jlinalg.cli.CorrectBleach210Request.*;

/** Private headless worker executing complete original correction or the explicit complete native histogram port. */
public final class CorrectBleach210Worker {
    private CorrectBleach210Worker() { }
    public static void main(String[] args){
        int code=2;String cause="FAIL2_CORRECTBLEACH210_WORKER";Path out=null;BleachCorrection original=null;CorrectBleach210NativeHistogram nativeHistogram=null;CorrectBleach210Exponential exponential=null;ImagePlus returned=null;boolean initialized=false;var terminal=new LinkedHashMap<String,Object>();
        try{
            require(args.length==1,"Require owned worker directory");out=Path.of(args[0]).toAbsolutePath().normalize();NextflowFiles.directory(out);var request=object(NextflowFiles.json(out.resolve("worker-request.json"),1048576));
            try{require(IJ.getVersion().equals("1.53t")&&"2.1.0".equals(VersionUtils.getVersion(BleachCorrection.class)),"Original runtime edition differs");}catch(NoClassDefFoundError|UnsatisfiedLinkError unavailable){throw new Prerequisite("Required original runtime unavailable: "+unavailable);}
            initialized=true;IJ.redirectErrorMessages(true);Path imageFile=Path.of(text(request.get("image")));long maximum=NextflowFiles.integer(request.get("maximum_decoded_pixels"),1,1000000000000L);ImagePlus image=CorrectBleach210ImageState.open(imageFile,maximum);require(image.getStackSize()>=2,"Original menu entry requires a complete image stack");require(image.getBitDepth()==8||image.getBitDepth()==16,"Original menu entry supports unsigned8/16-bit images; no implicit conversion");String roiMode=text(request.get("roi_mode"));if(roiMode.equals("NONE"))image.killRoi();else if(roiMode.equals("ROI_FILE")){var roi=new RoiDecoder(text(request.get("roi_file"))).getRoi();require(roi!=null,"Original ROI decoding failed");image.setRoi(roi);}var inputState=CorrectBleach210ImageState.state(image);NextflowFiles.writeJson(out.resolve("input-image-state.json"),inputState);String method=text(request.get("method")),engine=text(request.get("engine"));
            if(engine.equals("ORIGINAL")&&method.equals("EXPONENTIAL_FIT")){
                exponential=new CorrectBleach210Exponential();returned=exponential.correct(image);
            }else if(engine.equals("ORIGINAL")){
                int selected=switch(method){case "SIMPLE_RATIO"->BleachCorrection.SIMPLE_RATIO;case "EXPONENTIAL_FIT"->BleachCorrection.EXPONENTIAL_FIT;case "HISTOGRAM_MATCHING"->BleachCorrection.HISTOGRAM_MATCHING;default->throw new IOException("Unknown original method");};BleachCorrection.setCorrectionMethod(selected);original=new BleachCorrection();original.setHeadlessProcessing(true);if(method.equals("SIMPLE_RATIO"))original.setSimpleRatioBaseline(finite(request.get("baseline")));returned=original.doCorrection(image);
            }else{
                require(engine.equals("JAVA_NATIVE_HISTOGRAM210")&&method.equals("HISTOGRAM_MATCHING"),"Unknown native correction engine");nativeHistogram=new CorrectBleach210NativeHistogram();returned=nativeHistogram.correct(image,NextflowFiles.integer(request.get("maximum_native_comparisons_per_map"),1,1000000000000L),out.resolve("native-complete-lookups.bin"));
            }
            require(returned!=null,"Original returned no corrected image");terminal.put("input_roi_after_original_call",image.getRoi()==null?null:CorrectBleach210ImageState.state(image).get("roi"));saveImage(returned,out.resolve("corrected.tif"));var file=NextflowFiles.pin(out.resolve("corrected.tif"),137438953472L,()->{});terminal.put("output_file",file);terminal.put("returned_image",CorrectBleach210ImageState.state(returned));ImagePlus decoded=CorrectBleach210ImageState.open(out.resolve("corrected.tif"),maximum);terminal.put("decoded_saved_image",CorrectBleach210ImageState.state(decoded));decoded.changes=false;decoded.close();terminal.put("method",method);terminal.put("engine",engine);terminal.put("original_source_patched",false);terminal.put("input_units",request.get("input_units"));terminal.put("input_provenance",request.get("input_provenance"));
            int[] d=image.getDimensions();require(!(d[3]>1&&d[4]>1&&((long)d[3]*d[4]!=image.getStackSize())),"Original returned early after its channel/Z/T product warning; complete unchanged/partial output retained, not admitted");String originalError=IJ.getErrorMessage();require(originalError==null,"Original ImageJ error: "+originalError);if(exponential!=null){var model=exponential.state();terminal.put("actual_original_exponential_model",model);require(Boolean.TRUE.equals(model.get("reportable_fit")),"Original exponential fit failed or produced nonfinite parameters/correction factors; original model and pixels retained, no usable correction admitted");}System.out.flush();String logs=Files.readString(out.resolve("process.stdout"),StandardCharsets.UTF_8);require(!Pattern.compile("\\bratio=(?:NaN|-?Infinity)\\b").matcher(logs).find(),"Original produced nonfinite correction ratio; original pixels/logs retained, no usable correction admitted");terminal.put("status","PASS_COMPLETE_CORRECTBLEACH210_CORRECTION");terminal.put("reportable_correction",true);NextflowFiles.writeJson(out.resolve("worker-status.json"),terminal);cause="PASS_COMPLETE_CORRECTBLEACH210_CORRECTION";code=0;
        }catch(Prerequisite e){cause="SKIP3: "+e.getMessage();code=3;}catch(Throwable e){String originalError=initialized?IJ.getErrorMessage():null;terminal.put("original_imagej_error",originalError);cause="FAIL2: "+e+(originalError==null?"":"; original ImageJ: "+originalError);code=2;e.printStackTrace(System.err);}
        try{if(out!=null&&code!=0){if(exponential!=null){try{terminal.put("actual_original_exponential_model",exponential.state());}catch(Throwable e){terminal.put("original_model_capture_failure",e.toString());}}ImagePlus partial=returned!=null?returned:original!=null?original.getCorrectedImagePlus():nativeHistogram!=null?nativeHistogram.partial():exponential!=null?exponential.partial():null;if(partial!=null){try{saveImage(partial,out.resolve("unadmitted-original-image.tif"));terminal.put("unadmitted_image",CorrectBleach210ImageState.state(partial));}catch(Throwable e){terminal.put("unadmitted_image_capture_failure",e.toString());}}terminal.put("status",cause);terminal.put("exit_code",code);terminal.put("reportable_correction",false);NextflowFiles.writeJson(out.resolve("worker-status.json"),terminal);}}catch(IOException e){e.printStackTrace(System.err);code=2;}
        System.err.println(cause);System.exit(code);
    }
    private static void saveImage(ImagePlus image,Path file)throws IOException {var saver=new FileSaver(image);require(image.getStackSize()>1?saver.saveAsTiffStack(file.toString()):saver.saveAsTiff(file.toString()),"Original complete TIFF export failed");}
    private static final class Prerequisite extends IOException {private static final long serialVersionUID=1L;Prerequisite(String s){super(s);}}
}
