# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Seal complete original Monocle2 controls and retained failures; award no Java integration credit."""
import json
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from signac_scan_evidence import pack


def main():
    base=ROOT/'build/literature-review/monocle-runtime';target=ROOT/'docs/literature/monocle2-reference-development';assert not target.exists();target.mkdir()
    audit=json.loads((base/'monocle2-readiness-audit-1/COMPLETED.json').read_bytes());assert audit['total_original_tests']==360
    final=json.loads((base/'monocle2-legacy-readiness-probe-6/RESULT.json').read_bytes());assert final['original_package_loaded'] is True and all(c['all_status_OK'] for c in final['cases'])
    files={}
    # Completed/failed scientific transports and all raw synthetic objects are
    # retained. Runtime binaries/caches are bound by inventories, not distributed.
    for name in ['monocle2-readiness-probe-1','monocle2-constructor-diagnostic-1','monocle2-readiness-audit-1',*[f'monocle2-legacy-readiness-probe-{i}' for i in range(1,7)]]:
        for path in (base/name).rglob('*'):
            if path.is_file() and not set(path.relative_to(base/name).parts)&{'home','tmp','cache'}:files[path.relative_to(ROOT).as_posix()]=path.read_bytes()
    for name in [*[f'monocle2-reference-setup-{i}' for i in range(1,5)],*[f'monocle2-linux-reference-setup-{i}' for i in range(1,4)],'monocle2-hsmm-reference-setup-1','monocle2-dplyr-intake-1','monocle2-hsmm-intake-1','monocle2-source-intake-1']:
        for path in (base/name).iterdir():
            if path.is_file() and path.suffix not in ['.gz','.zip','.bz2']:files[path.relative_to(ROOT).as_posix()]=path.read_bytes()
        snapshot=base/name/'source-snapshot'
        for path in snapshot.rglob('*'):
            if path.is_file():files[path.relative_to(ROOT).as_posix()]=path.read_bytes()
    # Exact open-source Monocle2 package and full notices. The external HSMM
    # experiment-data archive is referenced by its URL/MD5/SHA rather than copied.
    for path in (base/'monocle2-source-intake-2').rglob('*'):
        if path.is_file():files[path.relative_to(ROOT).as_posix()]=path.read_bytes()
    license_path=Path('C:/Program Files/R/R-4.6.1/share/licenses/Artistic-2.0');files['licenses/Artistic-2.0.txt']=license_path.read_bytes()
    names=['intake_monocle2_source.py','setup_monocle2_reference.py','install_monocle2_reference.R','probe_monocle2_reference.py','probe_monocle2_reference.R',
        'setup_monocle2_legacy_reference.py','probe_monocle2_legacy_reference.py','probe_monocle2_legacy_reference.R','intake_monocle2_hsmm.py','install_monocle2_hsmm.py','audit_monocle2_readiness.py','seal_monocle2_reference.py','monocle2_reference_evidence.py']
    sources=['tools/literature/'+n for n in names]+['.gitattributes','docs/literature/MONOCLE2_CURRENT_READINESS.md','docs/literature/omics-2023-2026/monocle2-reference-checkout-shared-source-compatibility.json']
    sources+=sorted(p.relative_to(ROOT).as_posix() for p in (ROOT/'tools/literature/monocle2-legacy-reference').rglob('*') if p.is_file())
    pins={n:pin(ROOT/n) for n in sources};files.update({'current-source/'+n:(ROOT/n).read_bytes() for n in pins})
    layout=pack(target/'controls.zip',files);warnings=target/'WARNINGS.md'
    warnings.write_text('''# Original Monocle2 reference readiness only

No Java Monocle2 adapter/user analysis is admitted and no catalog integration credit is awarded. Use MONOCLE2_CURRENT_READINESS.md. Exact original 2.26.0 source and selected eight R bodies/formals match; original size-factor/dispersion estimation and 360 differential tests return OK on declared synthetic inputs. Independent finite-probability/BH checks and complete unchanged rerun tables pass. Preserve every original object and warning; this is not paper reproduction, native-DLL authentication or biological certification.

**SKIP3 for the attempted current R4.6 profile:** current dplyr makes group_by_ defunct, and original dplyr1.1.4 fails compilation on removed R4.6 C APIs. Install compatible original dependencies only into NEW private libraries where permitted; otherwise skip dependent Monocle2 analysis. Never patch original functions, drop genes, repair values or substitute Monocle3 to force success.

**Legacy warnings remain prominent:** selected runtime R4.2.1 loads Matrix/Biobase/ggplot2/VGAM/DDRTree/irlba binaries built under R4.2.3 and retains original group_by_/select_ deprecation warnings. Complete controls passed with those warnings; arbitrary workflow compatibility is unproven. The community conda lock listed HSMMSingleCell, but the installed candidate lacked its R namespace. Original HSMMSingleCell1.18.0 is supplied in a separate private library, which is required for this profile.

All earlier failed/invalidated setup, symlink inventory, time-capacity, report-accessor and summary-flag attempts remain retained. The selected probe is monocle2-legacy-readiness-probe-6, with metadata boolean fixed and complete scientific test tables identical to probe-5. Corrected metadata does not change original scientific values. Namespace/constructor success alone is insufficient. A typed Java adapter, complete result admission and all remaining paper roles are still open work.
''',encoding='utf8',newline='\n')
    save(target/'VALIDATION.json',dict(schema_version=1,status='passed-original-Monocle2-reference-readiness-not-Java-adapter-admission',archive=pin(target/'controls.zip'),warnings_document=pin(warnings),source_pins=pins,
        original_tests=360,genes_per_case=120,cells=200,source_functions_guarded=8,runtime_files_and_links_unchanged=34145,original_version='2.26.0',R_version='4.2.1',dplyr='1.1.4',HSMMSingleCell='1.18.0',
        Java_adapter_validated=False,implementation_credit=False,whole_paper_reproduced=False,whole_goal_complete=False,pushed=False,**layout))
    (target/'SHA256SUMS').write_text(''.join(pin(target/n)['sha256']+'  '+n+'\n' for n in ['controls.zip','WARNINGS.md','VALIDATION.json']),encoding='utf8',newline='\n')
    print('Sealed complete original Monocle2 readiness and retained failed attempts;',len(files),'logical files')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete original source/readiness checks')
    main()
