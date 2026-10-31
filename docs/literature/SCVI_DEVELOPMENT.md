# scvi-tools integration: development status

**The public neural-model adapter is unfinished. scvi-tools remains outside the completed tool count.** The Java APIs below provide input and likelihood audits; model fitting still uses the original upstream software in private validation controls.

Two native Java components are available:

- `org.jlinalg.singlecell.ScviGeneProjection` records exact gene order, shared genes selected before reference training, or explicit zero padding and removal of query-only genes. It retains every missing/removed identifier and both index mappings. Gene symbols, stable identifiers and cross-species orthotypes are not automatically interchangeable.
- `org.jlinalg.singlecell.ScviLikelihood` evaluates the default scvi-tools 1.3.0 NB/ZINB scalar kernels in float64. It preserves additive epsilon and the original softplus threshold. The `auditNegativeBinomial` and `auditZeroInflatedNegativeBinomial` methods also report numerical conditioning. These methods do not train a VAE or predict cell labels.

**Numerical warning:** the initial 11,664-case source grid includes 5,184 cases with a large cancellation diagnostic. Extreme-count evaluations differ from original float64 arithmetic by as much as 64 log-likelihood units. Preserve those discrepancies; a generous rounding envelope is not proof of accuracy. The 320 tested parameters from an actual trained model agree in float64 to about 1.1e-14, while native float64 and original float32 results differ by up to about 9.9e-6. Never silently substitute a float64 audit value for the original training loss.

**Required action:** inspect the returned conditioning flag and retain flagged values and warnings. Use the original model's dtype and loss for training. If the required runtime, model family, paper version, input layer or reference trust cannot be established, skip the dependent analysis until those requirements are supplied and validated.

The source review distinguishes SCVI integration, SCANVI annotation/query transfer, exploratory comparisons and SysVI on transformed expression. In particular, P062 reports SysVI after Pearson normalization; it must not be converted into an NB raw-count scVI workflow. Some papers do not specify their model count layer or annotation model. Those choices require review.

Private synthetic CPU controls exercise SCVI training, SCANVI reference/query transfer, SysVI with standard-normal and default VampPrior settings, and saved-model reloads. They preserve 96 reference cells, 32 query cells and explicit gene padding/order. The provider uses scvi-tools 1.3.0, PyTorch 2.6.0+cpu and Python 3.12.3. This does not reproduce the complete historical 1.0.3/1.2.0 environments or P062's reported Python 3.10 environment, certify preprocessing or establish biological accuracy/convergence.

Classification exports must preserve the learned registry/column order, which need not be alphabetical. The synthetic models return `type_B, type_A`; all 128 native probability argmax labels are checked against the original hard predictions using that order.

The next gates are protected raw-count and signed-expression input admission, a typed public workflow, runtime/model provenance, saved-model trust checks, complete output verification and runnable examples. The eleven source-use drafts are validated separately and have not yet changed the public catalog. The prior catalog remains 703 reviewed uses and 203 supporting pointers.

The likelihood port retains the [upstream BSD license](../../LICENSES/scvi-tools-BSD-3-Clause.txt), also bundled in the executable JAR under `META-INF/licenses/`.

## Protected matrix and covariate inputs

Internal native admission now handles gene-by-cell Matrix Market data, one exact UTF-8 identifier per gene/cell line, and a metadata TSV headed by `cell_id`. Plain UTF-8 and explicit gzip encodings are supported. Every gzip member, CRC and physical trailing byte is checked. Original bytes are copied and hashed; complete gene/cell populations and source paths remain in the audit. No normalization, highly variable gene selection or cell filtering occurs.

Choose the matrix meaning explicitly:

- `RAW_COUNTS` requires nonnegative integer counts. Duplicate coordinates are rejected or summed exactly before checking float32 representation. Zero-library cells fail with their identity; they are not removed. These numeric checks cannot prove that values came from a genuine raw-count layer.
- `SIGNED_EXPRESSION` permits the positive, negative and zero values needed by SysVI. Duplicate summation, when requested, follows source order in float64. Float32 rounding requires explicit permission; every changed coordinate and underflow to zero is recorded. Signed zero-expression cells remain present.

Metadata rows are joined to every matrix cell by exact ID and written in matrix order. Missing, duplicate or unknown cells fail. Batch, categorical, continuous and label roles are explicit; unused columns and literal `NA` strings are retained. Continuous covariate rounding has its own permission and complete conversion ledger. Reference labels, unlabeled-only queries and queries restricted to an explicit reference-label set have distinct contracts. Observed category counts never establish the model's learned class order. Cell summaries use exact count totals or float64 sums of canonical float32 values; they do not replace upstream tensor reductions.

Preparation checks complete input, decoded-byte, sorting, retained-memory and workspace capacities. Source or staged-byte changes and unexpected members invalidate preparation. Hashes check integrity, not authenticity. **`PREPARED_MATRIX_ONLY` and `PREPARED_COVARIATES_ONLY` do not mean a neural analysis completed.**

Validation covers 31 exact-package tests on each of Windows Java 21/25 and Linux Java 21, with no skips. Three complete synthetic datasets produce identical canonical files across all profiles. Feeding admitted data through the five original model controls preserves all 416 latent rows and 128 classification rows bit for bit, including the two SysVI priors and scANVI query transfer. This is a synthetic numerical control, not paper preprocessing or biological validation.

Applying cross-dataset gene projections, post-projection raw-library checks, the public typed model command, runtime admission, saved-model trust and final output verification remain unfinished. **Required action:** keep the dependent public model analysis skipped until those gates exist. Review the actual count layer or signed-expression provenance and every conversion ledger before downstream use. The current input-replay controls set `benchmark=False` explicitly and retain upstream performance warnings in their logs; do not infer GPU or large-study performance from these fixtures.
