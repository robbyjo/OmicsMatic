# Upstream methods and source credits

The residual Moran moments follow Cliff and Ord (1981), p. 203, as implemented
in spdep::lm.morantest by Roger Bivand and contributors. Official spdep 1.4-1
source declares GPL (>=2); the preserved R function carries Copyright 2001-2010
Roger Bivand. Its source, package description, origin and size/SHA-256 records
are retained in the evidence. Installed function body/formals were checked
against that official source before generating the original reference.

The Java implementation and original reference generator are project code
under GPL-3.0-or-later. LICENSE-GPL-3.txt supplies that license version. The
ordinary numerical dependency is bundled unchanged JDistlib 0.10.2; earlier
project license/third-party notices continue to apply.

Official references:
- https://r-spatial.github.io/spdep/reference/lm.morantest.html
- https://cran.r-project.org/src/contrib/Archive/spdep/spdep_1.4-1.tar.gz

P021 author source was only inspected privately to identify the called
statistical primitive and explicit preprocessing/model choices. That author
source is not executed or redistributed. No paper or controlled human dataset
is used by these seeded synthetic controls.
