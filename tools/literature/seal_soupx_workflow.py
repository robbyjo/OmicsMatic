# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Seal complete typed SoupX workflows, failed attempts and unchanged-source runtime evidence."""
import json,zipfile
from setup_deseq2_reference import ROOT,pin,save
from signac_scan_evidence import pack,byte_pin

BASE='build/literature-review/soupx-runtime/'
FINAL=dict(build='workflow-build-5',native='workflow-native-validation-4',runtime='workflow-runtime-validation-3',platform='workflow-platform-validation-3',literal='workflow-literal-validation-3',example='workflow-example-3',setup='workflow-setup-validation-1')


def main():
    base=ROOT/BASE;target=ROOT/'docs/literature/soupx-workflow-development';assert not target.exists();proofs={k:json.loads((base/n/'COMPLETED.json').read_bytes()) for k,n in FINAL.items()};build=proofs['build'];assert all(p['artifact']==build['artifact'] for k,p in proofs.items() if k!='setup');assert len(proofs['runtime']['protocols'])==14 and len(proofs['native']['JVMs'])==3 and len(proofs['setup']['protocols'])==3
    for p in proofs.values():assert all(pin(ROOT/n)==v for n,v in p['source_pins'].items())
    files={};attempts=[]
    for folder in sorted(base.iterdir()):
        if not folder.is_dir() or not folder.name.startswith(('workflow-','worker-probe-','adjustment-reference-')):continue
        attempts.append(folder.name)
        for path in folder.rglob('*'):
            parts=path.relative_to(folder).parts
            if not path.is_file() or path.suffix in ['.jar','.pyc'] or any(p in ['R-lib','__pycache__'] or p.startswith('Rtmp') for p in parts):continue
            if folder.name.startswith('workflow-seal-attempt-') and path.name=='controls.zip':continue # Previous exact archive remains private; original logical inputs/outputs are retained below.
            files[path.relative_to(ROOT).as_posix()]=path.read_bytes()
    jar=ROOT/build['artifact']['path'];parent=ROOT/build['parent_artifact']['path'];assert pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']}
    with zipfile.ZipFile(parent) as before,zipfile.ZipFile(jar) as after:
        old={n:byte_pin(before.read(n)) for n in before.namelist()};new={n:byte_pin(after.read(n)) for n in after.namelist()};assert all(new[n]==v for n,v in old.items()) and set(new)-set(old)==set(build['added_entries'])
        for name in build['added_entries']:files['candidate-owned-entry/'+name]=after.read(name)
    for label,entries in [('parent',old),('candidate',new)]:files['artifact-'+label+'-entries.json']=(json.dumps(entries,sort_keys=True,indent=2)+'\n').encode('utf8')
    names={p.relative_to(ROOT).as_posix() for p in (ROOT/'tools/literature').glob('*soupx*') if p.is_file() and ('workflow' in p.name or p.name in ['collect_soupx_adjustment_reference.py','collect_soupx_adjustment_reference.R','collect_soupx_literal_reference.R'])};names.update(p.relative_to(ROOT).as_posix() for p in (ROOT/'src/main/java/org/jlinalg/cli').glob('SoupXWorkflow*.java'));names.add('src/test/java/org/jlinalg/cli/SoupXWorkflowTest.java');names.add('docs/literature/SOUPX_WORKFLOWS.md')
    names.update(p.relative_to(ROOT).as_posix() for folder in ['src/main/resources/org/jlinalg/omics/soupx-workflow','src/test/resources/org/jlinalg/singlecell/soupx-workflow'] for p in (ROOT/folder).glob('*') if p.is_file());pins={n:pin(ROOT/n) for n in sorted(names)};files.update({'current-source/'+n:(ROOT/n).read_bytes() for n in pins})
    files['retained-attempts-diagnosis.json']=(json.dumps(dict(first_historical_transport='Original worker completed6 cases, but Java rejected newline-containing original diagnostics. Kept failed attempt; changed only the owned diagnostic decoder.',second_historical_checker='Actual Java workflow admitted6 cases; Python reference checker confused original logical TRUE tokens with saved Boolean true. Kept failed checker and performed a separate read-only comparison recovery without rerunning R.',first_native_validation='Two semantic tamper tests mutated copies rather than nested fixture maps. Corrected only the tests and required specific semantic refusal messages.',first_literal_attempt='R write.table under C locale emitted a Unicode escape while JSON retained the literal Greek identifier. Adapter rejected incoherence. The owned TSV exporter now writes explicit UTF-8 bytes; original SoupX functions remain unchanged.',second_literal_attempt='Historical direct reference used dense-to-sparse input, discarding4 explicit zero slots supplied to the adapter. This changed3 corrected values by a few floating-point ulps. New direct reference retains all12 input coordinates; exact comparison passes. Earlier original objects and comparisons remain retained.',scope='Source-bound original workflows plus native component/audits; no independent gamma/KDE/correction/RNG refit, paper-specific reconstruction, biological calibration, full package or whole-goal completion.'),indent=2)+'\n').encode('utf8')
    target.mkdir();(target/'.gitattributes').write_text('* -text\n',encoding='utf8',newline='\n');layout=pack(target/'controls.zip',files);assert (target/'controls.zip').stat().st_size<100000000,'Keep this retained attempt; split public evidence before committing'
    warning=target/'WARNINGS.md';warning.write_text('''# SoupX workflow: warnings and required actions

**DEVELOPMENT WORKFLOW — REQUIRE EXIT0 AND result_admitted=true.** Use the separate SoupXWorkflowCli and [full guide](../SOUPX_WORKFLOWS.md). Inspect WARNINGS.md, provenance, worker logs and every original case status before interpreting corrected counts. The canonical executable/release is unchanged.

**SKIP3: DEPENDENT ANALYSIS SKIPPED.** Supply the exact permitted Windows R4.6.1/SoupX1.6.2 or1.3.0/dependency/source profile. Linux upstream execution is skipped without interoperability fallback. Analysis installs nothing. If installation is forbidden, skip dependent analysis; the explicit preparation helper without --allow-install launches no installation and writes actionable SKIP3. Never silently change a version or patch original functions.

**FAIL2: NO RESULT FROM THE ATOMIC BATCH IS ADMITTED.** Earlier successful cases and partial outputs remain unadmitted. Preserve the attempt, status, source-status.json, original errors and process cleanup receipts. Correct the named input/model/source/capacity/timeout cause and retry NEW paths. Do not drop observations, repair identifiers, omit NA/NaN/Inf, widen profile ranges or convert source warnings into success.

**CALLER BIOLOGY AND VERSION CHOICES ARE EXPLICIT.** Selected profile droplets are not independently called empty; overlap with filtered cells is prominently warned and retained. Raw-subset counts are proven; independent toy/supplied matrices retain an explicit warning. Supplied masks, manual markers and clusters are not inferred. First cluster FALSE is refused for clustered correction because the original branch bypasses clusters. Historical autoEstCont returns a data frame, current returns a channel; applying a historical estimate requires explicit consent in the request. Historical cell-specific STAN is outside this profile.

**SOURCE NUMERICS REMAIN SOURCE NUMERICS.** Original iterative GLM rho differs slightly from the analytical diagnostic; original estimates/CI/score/iterations remain. No independent gamma, KDE, confidence-interval, correction or RNG refit is claimed. Fractional corrections remain fractional; stochastic rounding requires explicit seed/choice. Never silently convert fractions for downstream DESeq2. Nonfinite source metadata and complete original diagnostics remain visible, while nonfinite count matrices are refused.

**VALIDATION LIMITS.** Complete original toy/reference populations and failures are retained. Exact full corrected matrices match separate original calls, including explicit-zero and Unicode/literal-identifier controls. This is neither a historical paper environment nor biological validation. Native semantic audits check a stated subset of identities, not every upstream algorithm. The full250-paper objective remains active; Cell Ranger remains excluded. Read the catalog's seven primary roles and two supporting paragraphs before attributing settings or corrected-count use to a paper.
''',encoding='utf8',newline='\n')
    parents={('docs/literature/'+n+'/'+f):pin(ROOT/'docs/literature'/n/f) for n in ['tfidf-markers-development','soupx-ambient-core-development'] for f in ['VALIDATION.json','controls.zip']};save(target/'VALIDATION.json',dict(schema_version=1,status='passed-typed-original-SoupX-ambient-workflows',archive=pin(target/'controls.zip'),warnings_document=pin(warning),source_pins=pins,parent_proofs=parents,artifact=build['artifact'],parent_artifact=build['parent_artifact'],unchanged_parent_entries=build['unchanged_parent_entries'],added_entries=build['added_entries'],canonical_executable_unchanged=build['canonical_executable_unchanged'],final_attempts=FINAL,retained_attempts=attempts,tests_per_JVM=30,JVMs=3,runtime_protocols=14,exact_full_corrected_matrix_comparisons=45,setup_protocols=3,adapter_admitted=True,implementation_credit=True,canonical_adapter_available=False,whole_paper_reproduced=False,whole_goal_complete=False,**layout));(target/'SHA256SUMS').write_text(''.join(pin(target/n)['sha256']+'  '+n+'\n' for n in ['controls.zip','WARNINGS.md','VALIDATION.json']),encoding='utf8',newline='\n');print('Sealed',len(files),'logical files;',pin(target/'controls.zip')['bytes'],'archive bytes')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source/reference/workflow checks')
    main()
