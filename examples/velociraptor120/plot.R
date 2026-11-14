# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Complete original call over the synthetic fit. The R-only profile never fits a model.
call <- readRDS(file.path(input_directory, "call.rds"))
stopifnot(call$export %in% c("plotVelocity", "plotVelocityStream"),
          call$scenario %in% c("plotVelocity-all-panels", "plotVelocityStream-plain",
                              "plotVelocityStream-colored"))
set.seed(as.integer(call$render$seed))
analysis_result <- do.call(getExportedValue("velociraptor", call$export), call$arguments)
analysis_complete <- paste0("COMPLETE_ORIGINAL_FIGURE_", call$scenario)
