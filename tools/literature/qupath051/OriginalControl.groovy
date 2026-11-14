// Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
// Synthetic controls only. Every image, parameter, ROI and fixture UUID is explicit.
import java.nio.file.*
import java.nio.charset.StandardCharsets
import qupath.lib.common.ThreadTools
import qupath.lib.images.ImageData
import qupath.lib.images.servers.ImageServers
import qupath.lib.objects.PathObjects
import qupath.lib.roi.ROIs
import qupath.lib.regions.ImagePlane
import qupath.lib.io.PathIO
import qupath.lib.io.GsonTools
import static qupath.lib.scripting.QP.*
import static qupath.lib.gui.scripting.QPEx.*

def root=Paths.get(args[0])
def settings=GsonTools.getInstance().fromJson(Files.readString(root.resolve('inputs/settings.json'),StandardCharsets.UTF_8),Map.class)
ThreadTools.setParallelism(1)
def summary=[]
for (job in settings.jobs) {
    def server=ImageServers.buildServer(root.resolve('inputs/'+job.image).toUri())
    try {
        def imageData=new ImageData(server,ImageData.ImageType.BRIGHTFIELD_H_DAB)
        setBatchProjectAndImage(null,imageData)
        assert setPixelSizeMicrons(imageData,job.pixel_size_microns,job.pixel_size_microns,null)
        assert setColorDeconvolutionStains(GsonTools.getInstance().toJson(settings.stains))
        def box=job.roi
        def annotation=PathObjects.createAnnotationObject(ROIs.createRectangleROI(box[0],box[1],box[2],box[3],ImagePlane.getDefaultPlane()))
        annotation.setName('fixture ROI '+job.id)
        addObject(annotation)
        selectObjects([annotation])
        boolean complete=runPlugin('qupath.imagej.detect.cells.PositiveCellDetection',imageData,GsonTools.getInstance().toJson(job.parameters))
        assert complete : 'Original plugin did not complete '+job.id
        def hierarchy=imageData.getHierarchy()
        def detections=hierarchy.getDetectionObjects()
        // Retain the raw original hierarchy before assigning deterministic synthetic labels.
        PathIO.writeImageData(root.resolve('results/'+job.id+'.raw.qpdata'),imageData)
        def ordered=hierarchy.getAllObjects(true).toList().sort {a,b ->
            if (!a.hasROI())return b.hasROI()?-1:0
            if (!b.hasROI())return 1
            int first=Double.compare(a.getROI().getCentroidY(),b.getROI().getCentroidY())
            return first!=0?first:Double.compare(a.getROI().getCentroidX(),b.getROI().getCentroidX())
        }
        ordered.eachWithIndex {object,index ->
            object.setID(UUID.nameUUIDFromBytes((job.id+':'+index).getBytes(StandardCharsets.UTF_8)))
            if(object.isDetection())object.setName(String.format(Locale.ROOT,'000%03d.1_α',index))
        }
        saveDetectionMeasurements(imageData,root.resolve('results/'+job.id+'.measurements.tsv').toString())
        exportObjectsToGeoJson(hierarchy.getAllObjects(false),root.resolve('results/'+job.id+'.objects.geojson').toString(),'FEATURE_COLLECTION','PRETTY_JSON')
        PathIO.writeImageData(root.resolve('results/'+job.id+'.qpdata'),imageData)
        def classes=new TreeMap<String,Integer>()
        detections.each {object ->String key=String.valueOf(object.getPathClass());classes[key]=(classes[key]?:0)+1}
        summary.add([id:job.id,detections:detections.size(),class_counts:classes,
            complete_original_measurement_names:detections.collectMany {it.getMeasurementList().getMeasurementNames()}.unique().sort(),
            pixel_width_microns:server.getPixelCalibration().getPixelWidthMicrons(),
            pixel_height_microns:server.getPixelCalibration().getPixelHeightMicrons(),
            fixture_UUIDs_assigned_after_raw_capture:true])
    } finally {
        resetBatchProjectAndImage()
        server.close()
    }
}
Files.writeString(root.resolve('results/complete-summary.json'),GsonTools.getInstance(true).toJson(summary),StandardCharsets.UTF_8)
return 'QUPATH051_COMPLETE_SYNTHETIC_CELL_POPULATION'
