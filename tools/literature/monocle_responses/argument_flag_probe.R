# Diagnose R 4.3.3 argument-matching state without changing installed functions.
args <- commandArgs(TRUE)
stopifnot(length(args) == 1L, as.character(getRversion()) == "4.3.3")
out <- args[[1]]
source <- quote(function(listw) substitute(listw))
reference <- eval(source, baseenv())
candidate <- eval(source, baseenv())
before_body <- body(candidate)
before <- serialize(before_body, NULL, version=2)
stopifnot(identical(body(reference), body(candidate), ignore.srcref=FALSE, ignore.bytecode=FALSE))
answer <- candidate(1)
after <- serialize(body(candidate), NULL, version=2)
stopifnot(identical(answer, 1), length(before) == length(after))
changed <- which(before != after)
stopifnot(length(changed) == 1L, as.integer(after[changed]) - as.integer(before[changed]) == 16L,
          identical(body(reference), body(candidate), ignore.srcref=FALSE, ignore.bytecode=FALSE),
          identical(formals(reference), formals(candidate), ignore.srcref=FALSE, ignore.bytecode=FALSE))
writeBin(before, file.path(out, "minimal-before.serialize2"))
writeBin(after, file.path(out, "minimal-after.serialize2"))
jsonlite::write_json(list(status="PASS_MINIMAL_R433_SUBSTITUTE_ARGUSED_STATE_REPRODUCTION",
    function_source=as.list(deparse(source)), result=answer, changed_offsets1=as.list(changed),
    before_values=as.list(as.integer(before[changed])), after_values=as.list(as.integer(after[changed])),
    strict_body_formals_identical=TRUE, namespace_modified=FALSE,
    inference="R4.3.3 serializes pairlist LEVELS; substitute invokes matchArgs_NR which records ARGUSED. Raw serialized body bytes include argument-matching state."),
    file.path(out,"RESULT.json"),pretty=TRUE,auto_unbox=TRUE,digits=NA)
