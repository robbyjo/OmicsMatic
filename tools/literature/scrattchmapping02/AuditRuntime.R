# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Audit only; never replace functions or infer a paper environment.
audit.mapping02 <- function(control, label) {
  profile <- jsonlite::fromJSON(control$profile, simplifyVector=FALSE)
  stopifnot(as.character(getRversion()) == profile$selected_R)
  library(scrattch.mapping)
  for(package in names(profile$selected_dependency_versions)) {
    stopifnot(as.character(packageVersion(package)) == profile$selected_dependency_versions[[package]])
  }
  namespace <- asNamespace("scrattch.mapping"); expected <- list()
  for(path in sort(list.files(file.path(control$source,"R"),pattern="[.][rR]$",full.names=TRUE))) {
    for(expression in parse(path,keep.source=FALSE)) {
      if(is.call(expression) && as.character(expression[[1L]]) %in% c("<-","=") &&
         is.symbol(expression[[2L]]) && is.call(expression[[3L]]) &&
         identical(expression[[3L]][[1L]],as.name("function"))) {
        expected[[as.character(expression[[2L]])]] <- eval(expression[[3L]],envir=namespace)
      }
    }
  }
  stopifnot(identical(sort(names(expected)),sort(unlist(profile$namespace_functions,use.names=FALSE))))
  for(name in names(expected)) {
    actual <- get(name,envir=namespace,inherits=FALSE)
    stopifnot(is.function(actual),identical(formals(actual),formals(expected[[name]])),
              identical(body(actual),body(expected[[name]]),ignore.environment=FALSE))
  }
  python <- reticulate::py_config()
  python.version <- reticulate::py_to_r(reticulate::import("platform",convert=FALSE)$python_version())
  stopifnot(normalizePath(python$python)==normalizePath(Sys.getenv("RETICULATE_PYTHON")),
            identical(python.version,profile$selected_Python))
  for(module in names(profile$selected_python_modules)) {
    imported <- reticulate::import(module,convert=FALSE)
    version <- reticulate::py_to_r(reticulate::py_get_attr(imported,"__version__"))
    stopifnot(identical(version,profile$selected_python_modules[[module]]))
  }
  saveRDS(list(original_functions=names(expected),Python=python,session=sessionInfo()),
          file.path(control$output,paste0(label,"-namespace-and-session.rds")),version=3)
}
