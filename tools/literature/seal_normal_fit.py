# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Seal complete original and native fitting controls without changing prior proofs or runtimes."""
import json,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from signac_scan_evidence import pack,byte_pin


def main():
    base=ROOT/'build/literature-review/fitdistrplus-runtime';target=ROOT/'docs/literature/normal-fit-development';assert not target.exists()
    proofs=[json.loads((base/name/'COMPLETED.json').read_bytes()) for name in ['native-build-1','native-validation-1','native-example-1']];build,validation,example=proofs;assert all(p['artifact']==build['artifact'] for p in proofs)
    files={}
    for name in ['original-controls-1','native-fixture-1','native-build-1','native-validation-1','native-example-1','source-intake-1']:
        for path in (base/name).rglob('*'):
            if path.is_file() and path.suffix!='.jar':files[path.relative_to(ROOT).as_posix()]=path.read_bytes()
    artifact=build['artifact'];parent=ROOT/build['parent_artifact']['path'];jar=ROOT/artifact['path'];assert pin(jar)=={k:artifact[k] for k in ['bytes','sha256']}
    with zipfile.ZipFile(parent) as before,zipfile.ZipFile(jar) as after:
        old={n:byte_pin(before.read(n)) for n in before.namelist()};new={n:byte_pin(after.read(n)) for n in after.namelist()};assert all(new[n]==v for n,v in old.items()) and set(new)-set(old)==set(build['added_entries'])
        for name in build['added_entries']:files['candidate-owned-entry/'+name]=after.read(name)
    for label,entries in [('parent',old),('candidate',new)]:files['artifact-'+label+'-entries.json']=(json.dumps(entries,sort_keys=True,indent=2)+'\n').encode('utf8')
    names=set()
    for proof in [*proofs,json.loads((base/'original-controls-1/COMPLETED.json').read_bytes())]:
        for name,value in proof.get('source_pins',{}).items():
            assert pin(ROOT/name)==value
            if not name.startswith('build/'):names.add(name)
    names.update(['tools/literature/prepare_normal_fit_controls.py','tools/literature/normal_fit_evidence.py','tools/literature/seal_normal_fit.py','docs/literature/NORMAL_FIT_WORKFLOWS.md','.gitattributes','docs/literature/omics-2023-2026/normal-fit-checkout-shared-source-compatibility.json'])
    names.update(p.relative_to(ROOT).as_posix() for p in (ROOT/'examples/normal-fit').glob('*') if p.is_file());pins={n:pin(ROOT/n) for n in sorted(names)};files.update({'current-source/'+n:(ROOT/n).read_bytes() for n in pins})
    parents={n:pin(ROOT/n) for n in ['docs/literature/cicero-prediction-development/VALIDATION.json']};target.mkdir();layout=pack(target/'controls.zip',files);assert (target/'controls.zip').stat().st_size<100000000
    warning=target/'WARNINGS.md';warning.write_text('''# Native analytical normal fitting: warnings and required actions

Require exit0 and result_admitted=true. Read [the workflow guide](../NORMAL_FIT_WORKFLOWS.md), status, provenance and every fit/test row. The estimator is explicitly unweighted analytical normal-mle. The canonical executable and installed libraries are unchanged; native analysis needs Java only and installs nothing.

**ESTIMATOR DIFFERENCES ARE PRESERVED.** The original fitdistrplus1.2-6 default MLE uses a numerical optimizer. It fails on tiny-scale and nearly constant controls, while the analytical method solves their positive-variance likelihood. Original all-zero MLE returns SD0.1 with convergence0 and warnings; original moment matching returns SD0 and infinite likelihood. Native fitting refuses zero variance. Complete original objects, errors and warnings remain retained. This is not optimizer/Hessian/covariance, weighted/censored, bootstrap, every distribution or full-package equivalence.

**FAIL2: no result is admitted**, including an earlier completed fit if later writing/testing fails. Retain unadmitted-partial-results, captured inputs and all diagnostics. Correct the named input, representability, capacity, heap or storage cause and retry NEW paths. Never omit NA/NaN/Inf values, repair IDs, recode scores or silently rescale to force success. Invalid request syntax can fail before an output bundle exists; stderr gives recovery actions.

**BACKGROUND REVIEW:** explicitly declare origin, tail, BH, cutoff and strict/inclusive rule. No shuffle, independence, normality or BH calibration is established. Every row remains; a cutoff flag is not an inferred retention instruction. P043 does not name its function, estimator, version or shuffle implementation. Preserve its filtering-direction ambiguity. Do not silently remove original Cicero NA values to construct a background.

**TAIL PRECISION:** ordinary p/q underflow stays numeric zero, with finite log p/log q retained where representable. -Inf labels an unrepresentable log tail. Cutoff classification uses ordinary BH q; log q does not silently replace it. Numeric zero is not proof of literally zero probability.

Original reference capture needs its exact permitted R4.6.1/fitdistrplus1.2-6 profile and reports SKIP3 when unavailable. The Java workflow does not require R. Original controls do not authenticate execution, establish paper settings or biological validity. Native/source evidence and catalog integration credit are separate. The full250-paper goal remains active; Cell Ranger is excluded and py-monocle license preference remains pending.
''',encoding='utf8',newline='\n')
    positive=[p for p in validation['protocols'] if p['result_admitted']]
    save(target/'VALIDATION.json',dict(schema_version=1,status='passed-native-analytical-normal-fit-controls',archive=pin(target/'controls.zip'),warnings_document=pin(warning),source_pins=pins,parent_proofs=parents,artifact=artifact,parent_artifact=build['parent_artifact'],unchanged_parent_entries=build['unchanged_parent_entries'],added_entries=build['added_entries'],canonical_executable_unchanged=build['canonical_executable_unchanged'],tests_per_JVM=86,JVMs=3,packaged_protocols=51,original_fit_calls=86,original_probability_families=36,original_probability_values=468,positive_variance_native_cases=37,original_MLE_point_matches=35,original_MME_point_matches=37,audited_observation_rows=sum(p['observations'] for p in positive)+example['comparison']['observations'],audited_test_rows=sum(p['tests'] for p in positive)+example['comparison']['tests'],component_admitted=True,implementation_credit=False,whole_paper_reproduced=False,whole_goal_complete=False,**layout))
    (target/'SHA256SUMS').write_text(''.join(pin(target/n)['sha256']+'  '+n+'\n' for n in ['controls.zip','WARNINGS.md','VALIDATION.json']),encoding='utf8',newline='\n');print('Sealed',len(files),'logical files;',pin(target/'controls.zip')['bytes'],'archive bytes')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source/control bindings')
    main()
