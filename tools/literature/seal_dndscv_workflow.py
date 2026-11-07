# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Seal exact original dndscv comparisons and failures; retain public opaque-object omission receipts."""
import json,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from signac_scan_evidence import pack,byte_pin

BASE='build/literature-review/dndscv-runtime/'
FINAL=dict(build='workflow-build-3',native='workflow-native-validation-3',runtime='runtime-validation-4',platform='platform-validation-1',setup='setup-validation-1',example='example-validation-1',probe='workflow-probe-2')


def main():
    base=ROOT/BASE;target=ROOT/'docs/literature/dndscv-workflow-development';assert not target.exists();reports={k:json.loads((base/n/'COMPLETED.json').read_bytes()) for k,n in FINAL.items()};build=reports['build'];assert all(r['artifact']==build['artifact'] for k,r in reports.items() if k!='setup');assert reports['runtime']['status']=='passed-original-dndscv-runtime-options-and-atomic-failure-protocols' and reports['example']['result_admitted'] and reports['probe']['result_admitted'];assert len(reports['native']['JVMs'])==3 and all(r['tests']==17 and r['exit_code']==0 for r in reports['native']['JVMs']);assert len(reports['platform']['protocols'])==2 and len(reports['setup']['protocols'])==3
    coding=json.loads((base/'coding-validation-3/COMPLETED.json').read_bytes());assert coding['component_admitted'] and all(r['tests']==64 and r['exit_code']==0 for r in coding['JVMs'])
    for r in [*reports.values(),coding]:assert all(pin(ROOT/n)==v for n,v in r['source_pins'].items())
    profile=json.loads((ROOT/'src/main/resources/org/jlinalg/omics/dndscv-workflow/SOURCE-PROFILE.json').read_bytes());files={};omitted={};attempts=[]
    opaque={'original-return.rds','arguments.rds','reference-input.rds','poisson-model.rds','nb-model.rds','indel-model.rds'}
    for path in sorted(base.rglob('*')):
        if not path.is_file():continue
        relative=path.relative_to(base);parts=relative.parts
        if any(p=='R-lib' or p=='__pycache__' or p.startswith('Rtmp') or p=='catalog' or p.startswith('catalog-') for p in parts) or path.suffix in ['.jar','.pyc']:continue
        if len(parts)==2 and path.name=='COMPLETED.json':attempts.append(parts[0])
        reason=None
        if path.name in opaque:reason='Opaque complete R/model/reference object retained unchanged locally; complete scientific typed tables/arrays/diagnostics are public, but this opaque object is not embedded.'
        elif 'source-seal-attempt-1' in parts and path.suffix=='.zip':reason='Oversized first source evidence attempt retained locally; public exact original archive/source export is bound separately.'
        elif path.name in ['workflow-transports-fixture-v1-retained.zip']:reason='Oversized original full-RDS fixture retained locally; v2 exposes complete typed transports and explicit opaque-object receipts.'
        else:
            # Repeated original trees and the same official tarball are publicly reconstructable
            # from the separately committed source archive; avoid dozens of78MB copies.
            for marker in [('source','original'),('source',)]:
                index=next((i for i in range(len(parts)-len(marker)+1) if tuple(parts[i:i+len(marker)])==marker),None)
                if index is not None:
                    name=Path(*parts[index+len(marker):]).as_posix()
                    if name in profile['files'] and pin(path)==profile['files'][name]:reason='Exact original source member publicly reconstructable from dndscv-source/source.zip: '+name;break
            if path.name=='source.tar.gz' and pin(path)==json.loads((base/'source-intake-1/SOURCE.json').read_bytes())['archive']:reason='Exact official archive is public in dndscv-source/source.zip.'
        name=path.relative_to(ROOT).as_posix()
        if reason:omitted[name]=dict(**pin(path),reason=reason)
        else:files[name]=path.read_bytes()
    files['PUBLIC-OMISSION-RECEIPTS.json']=(json.dumps(dict(status='explicit-public-omissions-originals-retained-locally',files=omitted,objects_replaced_or_recomputed=False,required_action='Opaque RDS receipts establish captured-byte integrity only; they do not make those objects available offline. Use the complete public typed outputs and native tests for the stated audits, or explicitly rerun the pinned original source for new opaque objects. Preserve all local originals.'),indent=2)+'\n').encode('utf8')
    jar=ROOT/build['artifact']['path'];parent=ROOT/build['parent_artifact']['path'];assert pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']}
    with zipfile.ZipFile(parent) as before,zipfile.ZipFile(jar) as after:
        old={n:byte_pin(before.read(n)) for n in before.namelist()};new={n:byte_pin(after.read(n)) for n in after.namelist()};assert all(new[n]==v for n,v in old.items()) and set(new)-set(old)==set(build['added_entries'])
        for name in build['added_entries']:files['candidate-owned-entry/'+name]=after.read(name)
    for label,entries in [('parent',old),('candidate',new)]:files['artifact-'+label+'-entries.json']=(json.dumps(entries,sort_keys=True,indent=2)+'\n').encode('utf8')
    names={p.relative_to(ROOT).as_posix() for p in (ROOT/'tools/literature').glob('*dndscv*') if p.is_file() and not any(s in p.name for s in ['catalog','use-reviews','curate_','review_context','roles'])};names.update('tools/literature/'+n for n in ['build_somatic_coding.py','pack_somatic_coding_controls.py','validate_somatic_coding.py']);names.update(p.relative_to(ROOT).as_posix() for p in (ROOT/'src/main/java/org/jlinalg/cli').glob('DndscvWorkflow*.java'));names.update(['src/main/java/org/jlinalg/genetics/SomaticCoding.java','src/test/java/org/jlinalg/cli/SomaticCodingTest.java','src/test/java/org/jlinalg/cli/DndscvWorkflowTest.java','docs/literature/DNDSCV_WORKFLOWS.md']);names.update(p.relative_to(ROOT).as_posix() for folder in ['src/main/resources/org/jlinalg/omics/dndscv-workflow','src/test/resources/org/jlinalg/genetics/dndscv'] for p in (ROOT/folder).glob('*') if p.is_file());pins={n:pin(ROOT/n) for n in sorted(names)};files.update({'current-source/'+n:(ROOT/n).read_bytes() for n in pins})
    comparisons=len(reports['probe']['reference_comparisons'])+sum(len(r.get('reference_comparisons',[])) for r in reports['runtime']['protocols'])+sum(bool(r['reference_comparison']) for r in reports['platform']['protocols'])+1;assert comparisons>=24
    target.mkdir();(target/'.gitattributes').write_text('* -text\n',encoding='utf8',newline='\n');layout=pack(target/'controls.zip',files);assert (target/'controls.zip').stat().st_size<100000000,'Retain this failed seal and split evidence before commit';warning=target/'WARNINGS.md';warning.write_text('''# dndscv: warnings and required actions

**DEVELOPMENT ADAPTER: REQUIRE EXIT0 AND result_admitted=true.** Read [DNDSCV_WORKFLOWS.md](../DNDSCV_WORKFLOWS.md), every original status/log, provenance and native event ledger. The canonical executable/release remains unchanged.

**SKIP3: DEPENDENT ANALYSIS SKIPPED.** Supply the exact permitted Windows R4.6.1/source/private dependency profile or skip. Linux upstream execution explicitly skips without interop fallback. Analysis installs nothing; offline preparation without --allow-install starts no installer. Never substitute a package/source version or patch original functions.

**FAIL2: NO RESULT FROM THE ATOMIC BATCH IS ADMITTED**, including earlier completed cases. Preserve originals, warnings, partials, failures and cleanup receipts; fix the named input/reference/model/capacity/timeout cause and retry NEW paths. Never drop events, repair identifiers/alleles, replace NA/NaN/Inf or change a cutoff merely to hide a failure.

**BIOLOGICAL INPUTS REQUIRE CALLER PROVENANCE.** Supply independent mutation/clone events and the actual reference, panel and coverage. Read/spot counts do not establish independent events. Custom weighted opportunities remain explicitly as supplied; no inferred coverage, mouse reference, panel or liftover. Source selection fits/likelihoods/CI, gene-set fitting and site extraction are not independently refitted. Original site-depth integer coercion and nonfinite metadata remain.

**PAPER/VERSION LIMITS REMAIN OPEN.** P056's reported0.1.0 differs from selected official source0.0.1.0. No paper commit or historical environment is inferred. P146's pentanucleotide figure model is not established by the192-channel trinucleotide interface. IntOGen consensus, any-method thresholds and ambiguous paper postfilters remain separate.

**PUBLIC EVIDENCE LIMIT.** Complete scientific typed tables, N/L arrays, model diagnostics and failure records are public. Large opaque complete return/model/reference RDS objects remain unchanged locally and have explicit public file-hash/omission receipts; they are not all embedded in controls.zip. Repeated exact source copies are reconstructable from the separately committed dndscv-source archive. Hashes are integrity receipts, not signatures or independent biological validation. Full-package, whole-paper and whole-goal completion are not claimed.
''',encoding='utf8',newline='\n');parents={n:pin(ROOT/n) for n in ['docs/literature/soupx-workflow-development/VALIDATION.json','docs/literature/dndscv-source/VALIDATION.json','docs/literature/dndscv-source/source.zip']};save(target/'VALIDATION.json',dict(schema_version=1,status='passed-typed-original-dndscv-selection-workflows',archive=pin(target/'controls.zip'),warnings_document=pin(warning),source_pins=pins,parent_proofs=parents,artifact=build['artifact'],parent_artifact=build['parent_artifact'],unchanged_parent_entries=build['unchanged_parent_entries'],added_entries=build['added_entries'],canonical_executable_unchanged=build['canonical_executable_unchanged'],final_attempts=FINAL,retained_attempts=sorted(set(attempts)),native_coding_tests_per_JVM=64,workflow_tests_per_JVM=17,JVMs=3,runtime_protocols=len(reports['runtime']['protocols']),complete_original_case_comparisons=comparisons,public_omission_receipts=len(omitted),adapter_admitted=True,implementation_credit=True,canonical_adapter_available=False,whole_paper_reproduced=False,whole_goal_complete=False,**layout));(target/'SHA256SUMS').write_text(''.join(pin(target/n)['sha256']+'  '+n+'\n' for n in ['controls.zip','WARNINGS.md','VALIDATION.json']),encoding='utf8',newline='\n');print('Sealed',len(files),'public logical files;',pin(target/'controls.zip')['bytes'],'bytes;',len(omitted),'explicit omission receipts')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source/reference/workflow checks')
    main()
