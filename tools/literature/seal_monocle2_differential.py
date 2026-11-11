# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Seal complete Monocle2 adapter controls, original failures and exact additive artifact entries."""
import json
from pathlib import Path
import zipfile
from setup_deseq2_reference import ROOT,pin,save
from signac_scan_evidence import pack,byte_pin


def main():
    base=ROOT/'build/literature-review/monocle-runtime';target=ROOT/'docs/literature/monocle2-differential-development';assert not target.exists();target.mkdir()
    names=['differential-build-4','differential-validation-4','differential-variants-4','differential-contracts-3','differential-example-2']
    proofs=[json.loads((base/n/'COMPLETED.json').read_bytes()) for n in names];build=proofs[0];artifact=build['artifact'];assert all(p['artifact']==artifact for p in proofs)
    assert len(proofs[1]['comparisons'])==6 and sum(c['rows'] for c in proofs[1]['comparisons'].values())==720
    assert len(proofs[2]['protocols'])==13 and proofs[3]['tests_per_JVM']==10 and len(proofs[3]['JVMs'])==3 and proofs[4]['comparison']['rows']==12
    jar=ROOT/artifact['path'];parent=ROOT/build['parent_artifact']['path'];assert pin(jar)=={k:artifact[k] for k in ['bytes','sha256']}
    assert pin(parent)=={k:build['parent_artifact'][k] for k in ['bytes','sha256']}
    files={};attempts=['differential-profile-1',*[f'differential-build-{i}' for i in range(1,5)],*[f'differential-validation-{i}' for i in range(1,5)],*[f'differential-variants-{i}' for i in range(1,5)],*[f'differential-contracts-{i}' for i in range(1,4)],'differential-example-1','differential-example-2']
    for name in attempts:
        for path in (base/name).rglob('*'):
            if path.is_file() and path.suffix!='.jar':files[path.relative_to(ROOT).as_posix()]=path.read_bytes()
    with zipfile.ZipFile(parent) as before,zipfile.ZipFile(jar) as after:
        old={n:byte_pin(before.read(n)) for n in before.namelist()};new={n:byte_pin(after.read(n)) for n in after.namelist()}
        assert all(new[n]==v for n,v in old.items()) and set(new)-set(old)==set(build['added_entries'])
        for n in build['added_entries']:files['candidate-owned-entry/'+n]=after.read(n)
    for label,entries in [('parent',old),('candidate',new)]:files['artifact-'+label+'-entries.json']=(json.dumps(entries,sort_keys=True,indent=2)+'\n').encode('utf8')
    source_names=set(build['source_pins'])
    for proof in proofs[1:]:
        if 'source_pin' in proof:
            name={'differential-validation-4':'validate_monocle2_differential.py','differential-variants-4':'validate_monocle2_differential_variants.py'}[names[proofs.index(proof)]];assert pin(ROOT/'tools/literature'/name)==proof['source_pin'];source_names.add('tools/literature/'+name)
        source_names.update(n for n in proof.get('source_pins',{}) if not n.startswith('build/'))
    source_names.update(['tools/literature/prepare_monocle2_differential_profile.py','tools/literature/seal_monocle2_differential.py','tools/literature/monocle2_differential_evidence.py','.gitattributes','docs/literature/MONOCLE2_DIFFERENTIAL_DEVELOPMENT.md','docs/literature/omics-2023-2026/monocle2-differential-checkout-shared-source-compatibility.json'])
    source_names.update(p.relative_to(ROOT).as_posix() for p in (ROOT/'examples/monocle2-differential').glob('*') if p.is_file())
    pins={n:pin(ROOT/n) for n in sorted(source_names)}
    for p in proofs:
        for n,v in p.get('source_pins',{}).items():assert pin(ROOT/n)==v
    files.update({'current-source/'+n:(ROOT/n).read_bytes() for n in pins})
    parents={n:pin(ROOT/n) for n in ['docs/literature/monocle2-reference-development/VALIDATION.json','docs/literature/monocle-tools-development/VALIDATION.json']}
    layout=pack(target/'controls.zip',files)
    warning=target/'WARNINGS.md';warning.write_text('''# Monocle2 differential development adapter — warnings and required actions

Require analysis-status.json result_admitted=true before interpreting results. The separate additive development JAR preserves every parent entry and the canonical executable; it is not the released/root Monocle3 command. Use MONOCLE2_DIFFERENTIAL_DEVELOPMENT.md and its executed Linux example.

**SKIP3:** Windows Java, unavailable/forbidden Linux runtime, mismatched edition, or missing exact dependencies skip dependent analysis. Use Linux Java through WSL with Linux paths and the recorded R4.2.1/Monocle2.26.0/private dependency profile, including the separate original HSMMSingleCell1.18.0 library, only where installation is permitted. Analysis installs nothing. Otherwise retain the skip and do not report results.

**FAIL2:** any original failed gene, input mismatch, original error, timeout, capacity breach or audit failure admits no atomic batch result, including earlier completed cases. Original failed-gene p/q=1 values remain failures, not valid nulls. Correct the named cause and retry NEW paths; preserve every attempt and never force success by dropping genes, repairing IDs, imputation or replacing nonfinite values.

**Original family failures are retained:** free-dispersion negbinomial and binary/proportion binomialff fail with the condition has length > 1 in the original helper under this VGAM profile. Original bodies/family vectors are not patched; no results are admitted. Fixed-size negbinomial.size (relative/unscaled) and continuous uninormal controls match independent original runs. Dispersion controls preserve original dplyr deprecation warnings. Packages built under R4.2.3 run under R4.2.1 with original warnings retained.

Earlier attempts include an independent probe aborting at the original family error, a Java temporary-file traversal race during R cleanup, and source bindings correctly rejecting a validation after source edits. They remain unadmitted. The final worker uses no-follow file attributes and tolerates only disappearing temporary paths; other file errors and symlinks remain failures.

Exact selected versions and eleven original R bodies/formals are checked, not every native binary/dependency source. Native Java checks probabilities and BH, not independent refitting. The original differential function does not return internal VGAM fitted models. Logical capacities are not OS quotas. No biological certification, historical patch/formula reconstruction, Monocle3/custom graph-test/Cicero/py-monocle equivalence, additional catalog integration credit or whole-goal completion is claimed.
''',encoding='utf8',newline='\n')
    save(target/'VALIDATION.json',dict(schema_version=1,status='passed-typed-original-Monocle2-differential-development-controls',archive=pin(target/'controls.zip'),warnings_document=pin(warning),source_pins=pins,parent_proofs=parents,artifact=artifact,parent_artifact=build['parent_artifact'],unchanged_parent_entries=build['unchanged_parent_entries'],added_entries=build['added_entries'],canonical_executable_unchanged=build['canonical_executable_unchanged'],dense_sparse_original_rows=720,additional_success_rows=36,example_rows=12,contract_tests_per_JVM=10,JVMs=3,original_family_failures=3,protocol_runs=13,original_version='2.26.0',R_version='4.2.1',adapter_admitted=True,implementation_credit=False,whole_paper_reproduced=False,whole_goal_complete=False,pushed=False,**layout))
    (target/'SHA256SUMS').write_text(''.join(pin(target/n)['sha256']+'  '+n+'\n' for n in ['controls.zip','WARNINGS.md','VALIDATION.json']),encoding='utf8',newline='\n');print('Sealed',len(files),'logical files; full original outcomes and retained failed attempts')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source/control bindings')
    main()
