# MatrixEQTL source behavior

The native additive linear workflow implements the numerical behavior of
MatrixEQTL 2.3 by Andrey A. Shabalin (2019), distributed under LGPL-3.
Source: <https://cran.r-project.org/src/contrib/Archive/MatrixEQTL/MatrixEQTL_2.3.tar.gz>.
CRAN mirror tag 2.3: <https://github.com/cran/MatrixEQTL/tree/2.3>.

The source profile is `R/Matrix_eQTL_engine.R`, particularly
`.SetNanRowMean`, `Matrix_eQTL_main`, `modelLINEAR`, covariate QR,
gene/SNP orthogonalization, `.pv.nz`, and `.OutputSaver_FRD$getResults`.
The Java adaptation is GPL-3.0-or-later as part of OmicsMatic. The existing
project GPL license applies. The original source and scientific traces are
retained privately for independent validation; no original R code is bundled.

Changes include a strict typed input contract, complete ordered coordinates,
positive genomic positions, explicit capacity/deadline/cancellation refusal,
FP64 Java CPU matrix products, a Householder covariate basis, replayable input
capture, deterministic input-order tie breaking, and separately derived SE.
There are no ANOVA/interaction models, graphical histogram functions, direct
memory-saving output mode, automatic partial-coordinate matching, or claims of
whole-package or paper reproduction. All-missing rows, source zero-row handling,
infinite t statistics, and the normal-minimum p-value floor remain explicit.
