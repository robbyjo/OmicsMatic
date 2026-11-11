CreateMotifObject <- function(
  data = NULL,
  pwm = NULL,
  motif.names = NULL,
  positions = NULL,
  meta.data = NULL
) {
  data <- SetIfNull(x = data, y = new(Class = "dgCMatrix"))
  meta.data <- SetIfNull(x = meta.data, y = data.frame())
  if (
    !(inherits(x = data, what = "matrix") |
      inherits(x = data, what = "CsparseMatrix"))
    ) {
    stop("Data must be matrix or sparse matrix class. Supplied ",
         class(x = data))
  }
  if (inherits(x = data, what = "matrix")) {
    data <- as(Class = "CsparseMatrix", object = data)
  }
  if ((nrow(x = data) > 0) & (length(x = pwm) > 0)) {
    if (!all(names(x = pwm) == colnames(x = data))) {
      stop("Motif names in data matrix and PWM list are inconsistent")
    }
  }
  if ((nrow(x = data) > 0) & (nrow(x = meta.data) > 0)) {
    if (!all(rownames(x = meta.data) == rownames(x = data))) {
      stop("Motif names in data matrix and metadata are inconsistent")
    }
  }
  if (inherits(x = pwm, what = "list")) {
    if (is.null(names(x = pwm))) {
      stop("PWM must be a named list")
    }
  }
  if (!is.null(x = motif.names)) {
    if (length(x = motif.names) != length(x = pwm)) {
      stop("Number of motif names supplied does not match the number of motifs")
    }
  }
  if (
    inherits(x = pwm, what = "PFMatrixList") |
    inherits(x = pwm, what = "PWMatrixList")
    ) {
    pwm.converted <- lapply(X = as.list(x = pwm), FUN = PFMatrixToList)
    pwm <- lapply(X = pwm.converted, FUN = "[[", 1)
    motif.names <- lapply(X = pwm.converted, FUN = "[[", 2)
  }
  # ensure names are unique
  motif.id <- names(x = motif.names)
  mn.stash <- as.character(x = motif.names)
  mn.unique <- make.unique(names = as.character(motif.names))
  if (!identical(x = mn.stash, y = mn.unique)) {
    warning("Non-unique motif names supplied, making unique", immediate. = TRUE)
  }
  motif.names <- as.list(x = mn.unique)
  names(x = motif.names) <- motif.id
  pwm <- SetIfNull(x = pwm, y = list())
  if (is.null(x = motif.names)) {
    motif.names <- as.list(x = names(x = pwm))
    names(motif.names) <- names(x = pwm)
  }
  motif.obj <- new(
    Class = "Motif",
    data = data,
    pwm = pwm,
    motif.names = motif.names,
    positions = positions,
    meta.data = meta.data
  )
  return(motif.obj)
}
