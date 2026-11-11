# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Narrow API bridge for unchanged historical functions on current SeuratObject.
# Does not alter matrices, metadata, selections or the source algorithm.
signac_motif_legacy_get_assay_data<-function(object,assay=NULL,slot=NULL,layer=NULL,...){
  if(!is.null(slot)&&!is.null(layer))stop('Historical access bridge refuses simultaneous slot/layer')
  selected<-if(is.null(slot))layer else slot
  if(length(selected)!=1L||!selected%in%c('meta.features','counts','data'))stop('Historical motif bridge permits only explicit meta.features/counts/data access')
  if(is.null(assay))SeuratObject::GetAssayData(object=object,layer=selected,...) else SeuratObject::GetAssayData(object=object,assay=assay,layer=selected,...)
}
