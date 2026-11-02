# Original covariance preparation and native mash fitting

These 120 synthetic features have three conditions, 100 representative training
rows, explicit strong rows and 20 target rows. They reproduce the retained
`canonical_pca_ed-fixed-Rcpp` original-control input; no patient data are included.
The source fixture is pinned in
`src/test/resources/org/jlinalg/cli/mashr-workflow/original-controls.zip`
(SHA-256 `28187ad99751081f608473ffe6a24f2a7e61081e9eb5bdddfddc6de361239d42`).

Follow [the setup and run guide](../../docs/literature/MASHR_ORIGINAL_PREPARATION.md).
Only the five TSV files are analysis inputs; copy them into a new input directory
as shown there. Keep this README and `.gitattributes` outside that directory.

`effects.tsv` and `standard-errors.tsv` contain the same ordered feature and
condition identities. `populations.tsv` assigns literal `0`/`1` train, strong and
target flags. `preparation.tsv` selects original canonical + uncentered PCA +
Bovy ED covariance learning. `settings.tsv` selects the native fit and posterior
method. Each row must belong to at least one population. Populations can overlap
only when explicitly requested.

This is a development bridge example. The complete paper-specific mashr
integration and study routes remain open.
