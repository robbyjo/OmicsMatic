##' @importFrom yulab.utils yulab_msg
.onAttach <- function(libname, pkgname) {
  packageStartupMessage(yulab_msg(pkgname))

  options(ChIPseeker.downstreamDistance = 300)
  options(ChIPseeker.ignore_1st_exon = FALSE)
  options(ChIPseeker.ignore_1st_intron = FALSE)
  options(ChIPseeker.ignore_downstream = FALSE)
  options(ChIPseeker.ignore_promoter_subcategory= FALSE)
  
  options(aplot_align = 'y')

}

