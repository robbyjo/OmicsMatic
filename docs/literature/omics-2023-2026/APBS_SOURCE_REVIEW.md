# APBS source roles and implementation boundary

Two of the frozen250 papers explicitly name APBS. They are now catalogued as
independent solver roles while retaining their existing PyMOL roles and evidence.

| Paper | Primary evidence | Actual use | Unstated information |
|---|---|---|---|
| [P013](https://doi.org/10.1038/s41586-024-08333-9) | Methods / Bioinformatics / Protein structure visualization,paragraph124 | APBS plugin in PyMOL calculated electrostatic surface charge; prediction and alignment were separate steps. | APBS version,charge/protonation preparation,grid,boundary,dielectrics,salt and temperature. |
| [P026](https://doi.org/10.1038/s41586-024-08493-8) | Methods / Figure preparation,paragraph54 | APBS electrostatics plugin in PyMOL generated maps for structural figures. | Solver version,charge preparation and electrostatic/grid parameters. Nearby PyMOL2.5.4/3.0.2 are not APBS versions. |

The74 appended to APBS in P026's flattened XML is a citation number. The registry
allows that citation suffix without accepting an alphabetic continuation. Both
reviewed APBS versions are deliberately empty. Evidence pointers are authenticated
against the source XML hashes; no source excerpts are exported in these reviews.

A registry-independent scan of all250 cached primary XMLs found these two APBS
uses and no explicit PDB2PQR use. PDB2PQR is an implementation dependency option,
not a newly invented paper observation. The first scan's strict word boundary
missed APBS74; that discovery attempt and the corrected scan are both retained.

The present development adapter runs untouched official APBS3.5.0 from an
explicitly supplied PQR model. Native Java checks its coordinates,charges,radii,
grid geometry,every potential value and raw energy. Original LPBE/NPBE examples,
charge reversal/scaling and an independent Born-sphere refinement comparison
support this bounded solver subset. They do not establish historical-version
parity or reconstruction of either paper's unstated model or surface figure.

Original PDB2PQR3.8.0 prepared the retained1CRN PARSE fixture in a private Python
environment. No declared-pH titration was requested. The public typed preparation
adapter and actual PyMOL potential-to-surface rendering are now available in
workflow candidate b1c26fa4. Read [PDB2PQR_WORKFLOWS.md](PDB2PQR_WORKFLOWS.md),
[POTENTIAL_SURFACE_WORKFLOWS.md](POTENTIAL_SURFACE_WORKFLOWS.md) and
[apbs-workflow-validation.json](apbs-workflow-validation.json). No source-paper
preparation,solver settings or historical versions are inferred.

See [APBS_WORKFLOWS.md](APBS_WORKFLOWS.md),
[apbs-adapter-validation.json](apbs-adapter-validation.json),
[PYMOL_SOURCE_REVIEW.md](PYMOL_SOURCE_REVIEW.md) and
[PYMOL_WORKFLOWS.md](PYMOL_WORKFLOWS.md).
