# Upstream sources and terms

- nf-core/atacseq 1.2.1, Harshil Patel and contributors, source commit
  `1b3a832db5a53c92c2c89a4d6d79455e860461ad`, MIT license. The complete source
  archive and original license notice are retained in the control ZIP and
  project resources. See the [official source](https://github.com/nf-core/atacseq/tree/1.2.1).
- The pinned public example data come from the original nf-core/test-datasets
  ATAC fixture, commit `cd022b097372b078a68d8afadb172ad7342fd91f`.
  The original design and data/archive identities are retained. Full original
  read/reference payloads are separately provisioned, retained privately and
  not redistributed in this control ZIP. The complete source code license
  does not establish terms for every underlying data set.
- Original `nfcore/atacseq:1.2.1` OCI image and its individual tools retain
  their own upstream terms. The image and Apptainer/Nextflow/Java runtimes are
  separately provisioned dependencies; no image/runtime binary is included in
  this control ZIP. Original tool versions and workflow scripts remain upstream
  behavior. Selected image digest, reproducible SIF identity and managed-runtime
  checks are recorded separately from an author's unreported historical setup.
- JUnit Platform Console Standalone 1.13.4 supplied the Linux test runner,
  obtained with its official checksum. It is a test-only dependency and is not
  bundled with the production application or control ZIP.
- New project code is GPL-3.0-or-later. `LICENSE-GPL-3.txt` supplies the GPL text.
  Original paper XML identities and paragraph locations support the source-role
  census; whole articles are not redistributed by this checkpoint.
