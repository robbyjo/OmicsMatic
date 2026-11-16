# P074 PRS disease-label correction

P074 (PMCID PMC12807872) studies **aortic stenosis**. Its abstract explicitly
defines AS that way. The paper's title is *Genomic and transcriptomic analyses
of aortic stenosis enhance therapeutic target discovery and disease prediction*.
See the [primary article](https://doi.org/10.1038/s41588-025-02417-6).

The previously reviewed PRS-CS use summary incorrectly expanded AS as ankylosing
spondylitis. The same mistake was present in the first new LDpred2 review draft;
that draft was not admitted to the catalog. The draft and the earlier catalog
are retained. The corrected LDpred2 review names aortic stenosis.

The PRS-CS catalog correction changes the disease wording in one review and its
matching paper observation, updates the embedded catalog and adds this notice.
It changes no estimator, input data, numeric result, version label, LD panel,
tuning design, tool count or implementation credit. Other fields in that review
and every unrelated review/observation remain unchanged.

The whole predecessor catalog was verified in an exact clean checkout of
`070ddea4c6393759f244d1781dba86d6b385da37`, using the original unchanged verifier
and the full primary-source cache. Its historical metadata is retained exactly;
old verifiers are not modified to accept later metadata or source.

The separate metadata-only check is:

```powershell
python -B tools/literature/ldpred2_primary/erratum.py --verify --out build/NEW-p074-check
```

This confirms the scoped correction and unchanged population. It is not a new
cumulative review of all development source or a new scientific computation.
LDpred2 catalog admission and the full 250-paper implementation goal remain open.
