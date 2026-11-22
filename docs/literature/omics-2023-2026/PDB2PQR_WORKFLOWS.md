# Original PDB2PQR preparation with native atom lineage

`pdb2pqr-tools` prepares a local PDB using original PDB2PQR3.8.0,PROPKA3.5.1
and NumPy1.26.4. The development executable is
`build/literature-review/apbs-workflow-candidate-3/jlinalg-0.3.7.jar`,SHA-256
`b1c26fa467769d265115e9d5101f91eaa432da2df906e3a679086f68bfeb49ea`.
The canonical a104 executable is unchanged. No preparation tool is added to the
paper counts: a hash-checked scan of all250 source XMLs found no explicit
PDB2PQR or PROPKA name. This is an explicit dependency option for electrostatics.

**ANALYSIS SKIPPED means no preparation result.** Missing,changed,unsupported or
forbidden dependencies return3. Input,timeout,process or audit failures return2
and quarantine partials. Every analysis writes `WARNINGS.md`,`session.json`,
`analysis-status.json` and a hashed manifest. Correct the named cause and rerun
to a new directory. Analysis never installs or downloads structures.

**Required installation action:** on Windows x86_64 supply CPython3.12 x64 and,
where permitted,run:

```powershell
python tools/literature/install_pdb2pqr.py --python CPYTHON312_EXECUTABLE --home build/private-pdb2pqr-3.8.0 --cache build/pdb2pqr-wheels --allow-install --allow-download
java -jar build/literature-review/apbs-workflow-candidate-3/jlinalg-0.3.7.jar pdb2pqr-tools --options-file examples/apbs-tools/prepare-parse.json --home build/private-pdb2pqr-3.8.0 --out build/new-preparation
```

Omit `--allow-download` with an already complete cache. The installer verifies
ten exact original public wheels and1031 original package/data members,creates
a private environment and seals its installed files plus the shared CPython
base. The tested home has1079 private files and733 base files. No global Python
upgrade,package or PATH change occurs. Preserve changed homes and corrupt caches;
do not re-seal them. If installation is forbidden,**skip preparation** until an
administrator supplies an admitted home. Linux is not admitted by this Windows
runtime profile; native input/lineage code remains Java17-compatible.

The [official usage guide](https://pdb2pqr.readthedocs.io/en/latest/using/index.html)
describes preparation before an APBS solve and warns that not every exposed
PROPKA option is supported. The guide currently labels itself3.7.1; this adapter
uses the separately pinned original3.8.0 package and its actual flags/source.
The site asks users to register for funding counts. No personal details were
submitted; the local public BSD-licensed wheels were used. Retain the upstream
[license notice](../../../tools/literature/licenses/pdb2pqr-3.8.0-LICENSE.md).

All scientific policies are explicit:

- Choose `AMBER`,`CHARMM`,`PARSE`,`TYL06`,`PEOEPB` or `SWANSON`. No custom force
  field,naming conversion or MOL2 ligand model is exposed.
- Choose `prepare` or `assign-only`. Assignment-only requires no debumping or
  full hydrogen optimization,forcefield protonation,repair=error and
  unparameterized=error; ignored scientific choices are rejected.
- Choose `forcefield` or `propka`. Forcefield does **not** imply pH7. PROPKA needs
  an explicit pH in[0,14]. Neutral N/C terminal flags are only exposed for PARSE.
- Declare debumping,full hydrogen optimization,water retention,heavy-atom
  repair and unparameterized-atom omission. Original `--noopt` can still optimize
  water. Omitted/unmatched atoms are reported; an incomplete model needs review.
- Select one input-order state ordinal and either reject labeled alternates or
  retain blank plus one explicit label. Chosen labels are cleared only in the
  staged PDB. No occupancy,assembly or preferred-state heuristic is inferred.
- Declare Angstrom coordinates and a maximum retained-heavy displacement.
  Native checks add0.001 Angstrom for text serialization. Source/selected/prepared
  structures and full lineage are retained. PDB segment IDs remain in lineage;
  identities that would collide after conversion fail.

Native Java independently parses the prepared PDB and PQR,checks atom identities,
order/counts,coordinates,charges and nonnegative radii,and joins exact chain,
residue/insertion and atom names. Compatible protonation residue aliases are
explicit. Output-only heavy identities require repair=allow; missing source
identities require omission permission except explicitly dropped waters and
hydrogen changes. New identities can reflect repair **or** renaming; the audit
does not invent chemical lineage. PQR must match the independently parsed
prepared PDB. Rounded total charge is reported with its text-rounding bound;
it is not forcibly rounded to an integer.

**ZERO RADII ARE RETAINED.** The PARSE1CRN example has642 atoms,net charge near0
and236 zero-radius atoms,including charged hydrogens. No atom/charge is removed
and no replacement radius is assigned. The example disables debumping/full
hydrogen optimization and requests forcefield protonation without a pH titration.
Those are reproducible fixture choices,not a preferred biological model.

Fourteen packaged comparisons cover all six force fields,PROPKA pH2/7/12,
optimization,neutral termini,assignment-only and a heavy-oxygen repair. All8988
atom rows and44,940 numeric scalars match independently retained original runs.
Five failure/skip controls,state-ordinal selection and a Java25 Unicode workflow
passed. Sixteen actual installer/runtime policy cases cover permissions,allowed
downloads,reuse,corrupt caches,changed/unlisted/linked homes,hostile environments,
valid poisoned bytecode and owned timeout termination. The14-case candidate was
`apbs-workflow-candidate-1` (SHA starts`2b1b4d49`); all11 preparation/PQR class
payloads are byte-identical in candidate3. The combined107-test/10-suite gate,
strict Java17 compilation,Javadoc,packaging and107 standalone tests on each
Java21/25 passed. See [the exact validation record](apbs-workflow-validation.json).

Use the resulting explicitly modeled `prepared.pqr` with
[APBS](APBS_WORKFLOWS.md),then render an actual map with
[potential-surface](POTENTIAL_SURFACE_WORKFLOWS.md).
