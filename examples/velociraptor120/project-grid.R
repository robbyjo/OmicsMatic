# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# All 160 cells retain their original order. No identifiers or nonfinite values are repaired.
fit <- readRDS(file.path(input_directory, "fit.rds"))
stopifnot(identical(dim(fit), c(12L, 160L)))
coordinates <- SingleCellExperiment::reducedDim(fit, "X_pca")[, 1:2, drop=FALSE]
embedded <- velociraptor::embedVelocity(coordinates, vobj=fit)
stopifnot(identical(dim(embedded), c(160L, 2L)))
grid <- velociraptor::gridVectors(coordinates, embedded=embedded, resolution=8L,
    scale=TRUE, as.data.frame=FALSE, return.intermediates=TRUE)
stopifnot(length(grid$grp)==160L, identical(dim(grid$categories), c(160L, 2L)))
analysis_result <- list(coordinates=coordinates, embedded=embedded, grid=grid)
analysis_complete <- "COMPLETE_EXAMPLE_PROJECTION_GRID_160_CELLS"
