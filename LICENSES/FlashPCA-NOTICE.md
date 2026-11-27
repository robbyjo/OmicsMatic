# FlashPCA source profiles

FlashPCA is copyright (C) 2014-2016 Gad Abraham, distributed under
GPL-3.0-or-later. Portions are based on SparSNP, copyright (C) 2011-2012 Gad Abraham
and National ICT Australia. Original source and license:
<https://github.com/gabraham/flashpca>.

The adapter selects unchanged tagged sources separately:

- v1.2.6: `35a6bbee92b3dc025bd3f063d062ee567d88a15a`.
- v2.0: `b45400eabb46dc7942970b59ff95c08e3e5404c3`.

Original source/binaries, dependency packages and raw execution traces remain
private. OmicsMatic bundles its own adapter, complete numerical audit, synthetic
controls and source/file identities. Java code is GPL-3.0-or-later under the
project license. The numeric audit follows genotype decoding, SNP means/binomial
scales, covariance divisors, score/loadings formulas and trace-based PVE in the
tagged data.cpp, util.cpp, randompca.cpp and flashpca.cpp. It does not replace
their partial eigensolvers or implement their experimental CCA modes.

The source-built runtime qualification uses separately retained Eigen3.4.0
(MPL2), Boost1.83 (Boost Software License1.0), and Spectra0.4.0 (MPL2) dependencies.
No dependency code or compiled upstream runtime is distributed in the project JAR.
