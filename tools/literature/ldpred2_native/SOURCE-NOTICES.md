# Source and license notices

`LdPred2.java` derives its LDpred2 scaling and grid update from Florian Prive's
bigsnpr 1.12.2 `R/LDpred2.R` and `src/ldpred2.cpp`. The original package declares
`License: GPL-3`; the derived Java file is marked `GPL-3.0-only`. Its original
authors and contributors are retained in the unchanged package DESCRIPTION and
the full CRAN source archive accompanying this development evidence.

Other newly written project files are marked `GPL-3.0-or-later`. Distribution
of the combined implementation must respect the GPL version 3 terms applying
to the derived component. The project license text accompanies the source
snapshot.

`LdPred2Random.RStream` implements L'Ecuyer-CMRG uniform generation and R's
Inversion normal draw construction. The R implementation can be inspected in
[`RNG.c`](https://svn.r-project.org/R/tags/R-4-3-3/src/main/RNG.c) and
[`snorm.c`](https://svn.r-project.org/R/tags/R-4-3-3/src/nmath/snorm.c).
Normal quantiles use the project's existing JDistlib dependency. The tested
original C++ models consume the same explicit R seed states; this does not
implement R's integer seed initializer or foreach/doRNG's model allocation.

Original references execute the installed bigsnpr 1.12.2 conda-forge build
`r43h08d816e_0`. Its recipe identifies the CRAN archive with SHA-256
`443a5529ae2596eb3cf8f19760893b96beba4f12a4decbf6bea645db4d2187f6`.
The selected binary package SHA-256 is
`f6d49d701248c1be18d9bcfe0902f5d37ea35c272465c22ef913303c00ac635f`.
The original R function bodies are checked against CRAN source; this binary
has not been independently rebuilt. Numerical comparisons concern actual
observed original computations, without an executable authenticity claim.

The supplied data and large controls are synthetic. Primary-paper intake
publishes identifiers, evidence locations and hashes rather than full article
text. Complete large raw inputs/outputs and candidate JARs remain local.
