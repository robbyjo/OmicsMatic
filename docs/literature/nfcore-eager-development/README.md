# Eager development component

**Full eager analyses remain unvalidated. No new tool/catalog credit.**

The two Java 17-compatible JARs provide only native inspection of already parsed
sample-table rows for eager 2.3.4 and 2.4.4. They have no runtime dependency.
See [the guide](../NFCORE_EAGER.md) for a copyable Java example, original source
behavior, all version discrepancies and required user actions.

Run `python tools/literature/verify_nfcore_eager_checkpoint.py` from the repository
root. It checks the complete source/artifact/control inventory and replays all
98 original source-version cases with each compiler build on the selected Java
runtime. Java 17+ is required. Missing Java skips verification with exit 3;
integrity or case mismatches refuse it with exit 2. No software is installed,
network used, pipeline executed or scientific result admitted by this verifier.

The unchanged ChIP-seq 2.0.0 development JAR supplies a saved JSON reader only to
the diagnostic helper. It is not a dependency of the eager library. The complete
original-closure comparison used two compiler builds on Windows Java21/25 and
Linux Java21: 588 case comparisons. All complete source tuples and literal cells
were checked. TSV parsing, channel behavior and pipeline execution remain outside
this component's scope.

`controls.zip.xz` preserves both complete original source trees, original row
closure scripts and all 49 cases per version, provider-image identity and
version/help evidence, full package records, the original paper XML and
supplement, three source-bound role drafts and prominent warnings. It includes
failed setup/compilation/retrieval receipts. Container SIFs and managed runtimes
are private prerequisites and are not redistributed. The XZ envelope contains a
ZIP with a complete `MANIFEST.json` mapping all logical witness paths to exact
sizes and SHA-256 values. Each unique byte sequence is stored once under
`objects/<sha256>`. The verifier checks every logical witness and both archive
layers. This changes storage only; no witness content is discarded or edited.
The first verified plain-ZIP checkpoint remains
retained privately as an earlier distribution. The OCI selections establish
current byte identities; they do not prove author image or Conda history.

The Nature paper and unchanged supplementary DOCX are attributed to Barquera and
colleagues, *Ancient genomes reveal a deep history of Treponema pallidum in the
Americas*, DOI [10.1038/s41586-024-08515-5](https://doi.org/10.1038/s41586-024-08515-5),
The Author(s), 2024, under [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/).
The paper XML retains its original permissions and third-party material notice.
Derived role reviews and text pointers are new annotations; the saved DOCX is
unchanged. nf-core source remains under its retained MIT license; native project
code is GPL-3.0-or-later. Checksums provide integrity, not authentication.

If exact permitted tools, installation or complete role inputs are unavailable,
**skip the dependent analysis**, retain errors/warnings, and supply the named
prerequisite or use an allowed environment before retrying. A small control
database cannot replace the authors' historical screening database. The GATK,
DeDup, ancestry-provider and package/executable discrepancies in the guide must
remain explicit.
