# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Original graph and plotting functions with an explicit caller edge filter.
bigcat.constellation <- function(arguments,control) {
  stopifnot(bigcat.named(arguments),setequal(names(arguments),
    c("graph_arguments","plot_arguments","outside_fraction_threshold")),
    bigcat.named(arguments$graph_arguments),bigcat.named(arguments$plot_arguments),
    !"knn.cl.df" %in% names(arguments$plot_arguments),
    "out.dir" %in% names(arguments$plot_arguments),"prefix" %in% names(arguments$plot_arguments),
    is.numeric(arguments$outside_fraction_threshold),length(arguments$outside_fraction_threshold)==1L,
    is.finite(arguments$outside_fraction_threshold),arguments$outside_fraction_threshold>=0,
    arguments$outside_fraction_threshold<=1)
  bigcat.output.path(arguments$plot_arguments$out.dir,control)
  bigcat.output.path(arguments$plot_arguments$prefix,control)
  original.call <- bigcat.original.recorder(file.path(control$workspace,"constellation"))
  graph <- original.call("graph","get_knn_graph",arguments$graph_arguments)
  edges <- graph$knn.cl.df
  # Retain all within-type rows, which the original plot uses for node metadata.
  selected <- edges[edges$cl.from==edges$cl.to|edges$frac>arguments$outside_fraction_threshold,,drop=FALSE]
  saveRDS(list(graph=graph,selected_edges=selected,threshold=arguments$outside_fraction_threshold),
    file.path(control$workspace,"constellation/complete-pre-plot-graph-and-selection.rds"),version=3)
  plot <- original.call("plot","plot_constellation",c(list(knn.cl.df=selected),arguments$plot_arguments))
  list(original_graph=graph,selected_edges=selected,original_plot=plot,
    outside_fraction_threshold=arguments$outside_fraction_threshold,
    within_type_rows_retained=TRUE,graph_arguments=arguments$graph_arguments,
    plot_arguments=arguments$plot_arguments,source_rank_one_exclusion_unchanged=TRUE,
    author_neighbor_and_plot_arguments_inferred=FALSE)
}
