# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# The explicit 15-iteration caller budget is not proof of convergence.
values <- readRDS(file.path(input_directory, "inputs.rds"))
parameters <- readRDS(file.path(input_directory, "parameters.rds"))
stopifnot(identical(parameters$mode, "dynamical"))
set.seed(882341L)
spec <- get("velo.env", envir=asNamespace("velociraptor"), inherits=FALSE)
basilisk::basiliskRun(env=spec, fun=function(seed) {
    reticulate::import("numpy")$random$seed(as.integer(seed))
    NULL
}, seed=882341L)
analysis_result <- do.call(velociraptor::scvelo, c(list(x=values$SCE_input), parameters))
stopifnot(identical(dim(analysis_result), c(12L, 160L)),
          identical(rownames(analysis_result), rownames(values$list_input$X)),
          identical(colnames(analysis_result), colnames(values$list_input$X)))
saveRDS(parameters, file.path(result_directory, "complete-caller-parameters.rds"), version=3)
analysis_complete <- "COMPLETE_EXAMPLE_DYNAMICAL_160_CELLS_12_GENES"
