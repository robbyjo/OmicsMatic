"""Freeze independent fixtures using pinned upstream R scoring functions.

Only function definitions are evaluated; complete packages are neither installed
nor claimed as tested. Run this intentionally to refresh fixtures, then review
the source SHAs and resulting diff. Network-free Gradle tests use frozen output.
"""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import urllib.request

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "src/test/resources/signature-scores"
CACHE = ROOT / "build/signature-reference"
SOURCES = [("carmonalab/UCell", "R/HelperFunctions.R", "ucell.R"),
           ("aertslab/AUCell", "R/02_calcAUC.R", "aucell.R"),
           ("rcastelo/GSVA", "R/ssgsea.R", "ssgsea.R")]


def download(url):
    request = urllib.request.Request(url, headers={"User-Agent": "JLinAlg-numerical-reference"})
    return urllib.request.urlopen(request, timeout=60).read()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--rscript", required=True)
    args = parser.parse_args()
    CACHE.mkdir(parents=True, exist_ok=True)
    OUT.mkdir(parents=True, exist_ok=True)
    provenance = []
    for repo, source, local in SOURCES:
        commit = json.loads(download(f"https://api.github.com/repos/{repo}/commits?path={source}&per_page=1"))[0]["sha"]
        url = f"https://raw.githubusercontent.com/{repo}/{commit}/{source}"
        data = download(url)
        (CACHE / local).write_bytes(data)
        provenance.append({"repo": repo, "commit": commit, "url": url,
                           "sha256": hashlib.sha256(data).hexdigest()})
    genes = [f"G{i:02d}" for i in range(1, 41)]
    rows = []
    for sample in range(8):
        values = []
        for gene in range(40):
            value = ((gene * (sample + 3) + sample * 11) % 47) / 3
            if sample == 1:
                value = gene // 4
            if sample == 2:
                value = 0
            if sample == 3:
                value = -value
            if sample == 4 and gene > 10:
                value = 0
            values.append(str(value))
        rows.append([f"S{sample + 1}"] + values)
    (OUT / "expression.tsv").write_text("\n".join("\t".join(row) for row in [["sample_id"] + genes] + rows) + "\n", encoding="utf-8")
    (OUT / "signatures.gmt").write_text(
        "early\tfixture\tG01\tG02\tG03\tG04\n"
        "late\tfixture\tG33\tG34\tG35\n"
        "mixed\tfixture\tG01\tG09\tG16\tG24\tG32\tG40\n"
        "single\tfixture\tG20\n", encoding="utf-8")
    script = r'''
args <- commandArgs(TRUE)
cache <- args[1]; output <- args[2]
load_functions <- function(file, names) {
  for (expr in parse(file)) {
    if (is.call(expr) && identical(expr[[1]], as.name("<-")) &&
        as.character(expr[[2]]) %in% names) eval(expr, envir=.GlobalEnv)
  }
}
load_functions(file.path(cache,"ucell.R"), "u_stat")
load_functions(file.path(cache,"aucell.R"), c(".auc", ".AUC.geneSet_norm"))
load_functions(file.path(cache,"ssgsea.R"), ".fastRndWalk")
x <- read.delim(file.path(output,"expression.tsv"),check.names=FALSE, row.names=1)
sets <- strsplit(readLines(file.path(output,"signatures.gmt")), "\t")
result <- list(); i <- 0L
add <- function(method, sample, set, maxrank, alpha, value) {
  i <<- i + 1L
  result[[i]] <<- data.frame(method=method,sample=sample,signature=set,
    max_rank=maxrank,alpha=alpha,expected=value)
}
for (sample in rownames(x)) {
  expression <- as.numeric(x[sample,]); names(expression) <- colnames(x)
  descending <- rank(-expression,ties.method="average")
  # AUCell ranking RNG is out of scope: supply explicit deterministic ranks.
  unique <- rank(-expression,ties.method="first")
  rank_matrix <- matrix(unique,ncol=1,dimnames=list(names(expression),sample))
  ascending <- as.integer(rank(expression,ties.method="average"))
  ranking <- order(ascending,decreasing=TRUE)
  for (set in sets) {
    idx <- match(set[-(1:2)],names(expression))
    for (cutoff in c(10L,30L,40L)) {
      value <- u_stat(cbind(seq_along(expression),descending),idx,cutoff)
      add("ucell",sample,set[1],cutoff,0.25,value)
      value <- .AUC.geneSet_norm(set[-(1:2)],rank_matrix,cutoff)[1]
      add("aucell",sample,set[1],cutoff,0.25,value)
    }
    for (alpha in c(0,0.25,1)) {
      value <- .fastRndWalk(match(idx,ranking),ranking,1,matrix(ascending^alpha,ncol=1))
      add("ssgsea",sample,set[1],40,alpha,value)
    }
  }
}
write.table(do.call(rbind,result),file.path(output,"reference.tsv"),
  sep="\t",quote=FALSE,row.names=FALSE)
writeLines(capture.output(sessionInfo()),file.path(output,"r-session.txt"))
'''
    path = CACHE / "generate.R"
    path.write_text(script, encoding="utf-8")
    subprocess.run([args.rscript, "--vanilla", str(path), str(CACHE), str(OUT)], check=True)
    (OUT / "provenance.json").write_text(json.dumps({
        "sources": provenance, "scope": "Upstream scoring function outputs on explicit rankings; full package workflows and AUCell random tie generation were not exercised.",
        "reference_sha256": hashlib.sha256((OUT / "reference.tsv").read_bytes()).hexdigest()
    }, indent=2) + "\n", encoding="utf-8")
    print("Frozen upstream function fixtures:", len((OUT / "reference.tsv").read_text().splitlines()) - 1)


if __name__ == "__main__":
    main()
