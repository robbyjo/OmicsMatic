# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Seal native SMR component evidence and original-source controls without crediting unfinished tool adapters."""
import json,zipfile
from setup_deseq2_reference import ROOT,pin,save
from signac_scan_evidence import pack,byte_pin

BASE='build/literature-review/smr-runtime/'
FINAL=dict(build='statistics-build-1',native='statistics-validation-1',kernel='kernel-reference-2',example='statistics-example-2',source_131='reference-1.3.1-build-2',source_143='reference-1.4.3-build-2',roles='role-drafts-1')


def main():
    base=ROOT/BASE;target=ROOT/'docs/literature/smr-statistics-development';assert not target.exists();reports={k:json.loads((base/n/'COMPLETED.json').read_bytes()) for k,n in FINAL.items()};build=reports['build'];assert reports['native']['component_admitted'] and not reports['native']['SMR_or_HEIDI_workflow_admitted'];assert all(r['tests']==81 and r['exit_code']==r['skipped']==0 for r in reports['native']['JVMs']) and len(reports['native']['JVMs'])==3;assert reports['kernel']['cases']==72 and reports['example']['snippet_compiled_from_document'];assert reports['roles']['primary_roles']==12 and not reports['roles']['catalog_changed'];assert all(reports[k]['status']=='built-unchanged-original-SMR-reference-only' and reports[k]['original_source_and_dependencies_unchanged'] for k in ['source_131','source_143'])
    for report in reports.values():assert all(pin(ROOT/n)==v for n,v in report['source_pins'].items())
    files={};omitted={}
    for path in sorted(base.rglob('*')):
        if not path.is_file():continue
        parts=path.relative_to(base).parts;name=path.relative_to(ROOT).as_posix()
        if '__pycache__' in parts or path.suffix in ['.pyc','.jar']:continue
        if parts[0]=='cmake-setup-1' and 'wheel' in parts:continue # Complete publisher/member pins remain in its setup receipt.
        reason=None
        if path.suffix=='.whl':reason='Build-only private CMake wheel; exact PyPI publisher record, SHA256 and complete member pins are public in its setup receipt.'
        elif 'owned-source' in parts or 'owned-dependencies' in parts:reason='Owned build copy retained locally; exact original source/dependency archives and original member pins are public. Generated build-only metadata is not scientific input/output.'
        elif 'build' in parts[1:] and path.name not in ['smr','compile_commands.json','link.txt','flags.make','CMakeCache.txt'] and '.so' not in path.name:reason='Compiler object or generated build-only file retained locally; source, flags, full logs, final original executables and scientific results are public.'
        elif path.suffix in ['.o','.a'] or path.name.endswith('.o.d'):reason='Compiler intermediate retained locally; source, flags and full logs are public.'
        if reason:omitted[name]=dict(**pin(path),reason=reason)
        else:files[name]=path.read_bytes()
    files['BUILD-ONLY-OMISSIONS.json']=(json.dumps(dict(status='build-only-omissions-not-scientific-result-omissions',files=omitted,CMake_extracted_wheel_members_recorded_in_setup_receipt=True,scientific_input_or_output_replaced=False,required_action='Use the exact saved source/dependency archives and build flags or the complete native fixtures. Build-only CMake and compiler intermediates are not all embedded. Existing binaries are reference artifacts, not an exported portable runtime profile.'),indent=2)+'\n').encode('utf8')
    jar=ROOT/build['artifact']['path'];parent=ROOT/build['parent_artifact']['path'];assert pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']}
    with zipfile.ZipFile(parent) as before,zipfile.ZipFile(jar) as after:
        old={n:byte_pin(before.read(n)) for n in before.namelist()};new={n:byte_pin(after.read(n)) for n in after.namelist()};assert all(new[n]==v for n,v in old.items()) and set(new)-set(old)==set(build['added_entries'])
        for name in build['added_entries']:files['candidate-owned-entry/'+name]=after.read(name)
    for label,entries in [('parent',old),('candidate',new)]:files['artifact-'+label+'-entries.json']=(json.dumps(entries,sort_keys=True,indent=2)+'\n').encode('utf8')
    names={p.relative_to(ROOT).as_posix() for p in (ROOT/'tools/literature').glob('*smr*') if p.is_file()};names.update(['src/main/java/org/jlinalg/mr/SmrStatistics.java','src/test/java/org/jlinalg/cli/SmrStatisticsTest.java','docs/literature/SMR_STATISTICS_DEVELOPMENT.md']);names.update(p.relative_to(ROOT).as_posix() for p in (ROOT/'src/test/resources/org/jlinalg/mr/smr').glob('*') if p.is_file());pins={n:pin(ROOT/n) for n in sorted(names)};files.update({'current-source/'+n:(ROOT/n).read_bytes() for n in pins});held=json.loads((ROOT/'build/literature-review/push-checkpoint-2026-11-07-dndscv-workflow-1/selection.json').read_bytes())['preserved'];assert len(held)==18 and all(pin(ROOT/n)==v for n,v in held.items());files['preserved-unrelated-worktree-pins.json']=(json.dumps(held,indent=2)+'\n').encode('utf8');target.mkdir();(target/'.gitattributes').write_text('* -text\n',encoding='utf8',newline='\n');layout=pack(target/'controls.zip',files);assert (target/'controls.zip').stat().st_size<100000000,'Preserve failed seal and split before committing';warning=target/'WARNINGS.md';warning.write_text('''# Native SMR component: warnings and required actions

**COMPONENT VALIDATED; FULL SMR/HEIDI ADAPTER UNFINISHED.** Read [SMR_STATISTICS_DEVELOPMENT.md](../SMR_STATISTICS_DEVELOPMENT.md). Native Java ratio and supplied-instrument covariance/deviation arithmetic are available. No native HEIDI P value, SNP selection, LD pruning, multi-SNP fit, sample-overlap estimation or biological calibration is claimed. Catalog tool statuses/counts remain unchanged; the full goal remains active.

**NATIVE ERRORS RETURN NO RESULT.** Supply aligned finite effects/positive SEs, nonzero exposure effects and complete valid LD. Inspect the named IllegalArgumentException for unrepresentable or nonpositive variance. Never drop instruments, clamp a variance, repair IDs/alleles or substitute a generic heterogeneity P value. Displayed P0 with finite log tail is underflow, not exact zero.

**SKIP3 FOR UNAVAILABLE/FORBIDDEN ORIGINAL DEPENDENCIES.** Official binary downloads returned HTTP403 and remain failed attempts. Permitted exact source builds succeeded privately; existing source/dependency trees, canonical JAR and shared libraries remain unchanged. Native Java needs none of those external dependencies. If source downloads/compilation or the declared Windows/WSL profile are unavailable, skip dependent original work; read full logs and retry NEW paths with permitted exact dependencies.

**ORIGINAL HELP EXIT1 IS METADATA BEHAVIOR, NOT MODEL SUCCESS.** The corrected builder checks the exact original banner/options. Actual model commands still require exit0 and complete outputs. Earlier failed health checks, compiler warnings and the thin-harness compile failure remain retained. No original scientific function was patched.

**SOURCE/VERSION/PAPER LIMITS.** Exact captured1.3.1/1.4.3 source labels do not identify historical paper binaries; reported1.03 remains distinct. Twelve role drafts are unapplied. Preserve P072's reused results, paper-specific reference/correction families, P183's literal1.57e-8 HEIDI threshold, reported-count conditions and asymptotic-versus-empirical P distinctions. Non-rejection of heterogeneity does not prove causality.

**EVIDENCE SCOPE.** Complete scientific controls/tables and original source/dependency archives are public. Private build-only CMake/compiler files have explicit omission receipts. Reference executables are not a general exported runtime. All native classes are additive to the validated parent JAR. No full-package/paper, release, deployment or whole-goal completion is claimed.
''',encoding='utf8',newline='\n');canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar');assert canonical==build['canonical_executable_unchanged'];parents={n:pin(ROOT/n) for n in ['docs/literature/dndscv-workflow-development/VALIDATION.json','docs/literature/dndscv-workflow-development/controls.zip','docs/literature/omics-2023-2026/dndscv-catalog-validation.json']};save(target/'VALIDATION.json',dict(schema_version=1,status='passed-native-SMR-statistics-component',archive=pin(target/'controls.zip'),warnings_document=pin(warning),source_pins=pins,parent_proofs=parents,artifact=build['artifact'],parent_artifact=build['parent_artifact'],unchanged_parent_entries=build['unchanged_parent_entries'],added_entries=build['added_entries'],canonical_executable_unchanged=canonical,final_attempts=FINAL,tests_per_JVM=81,JVMs=3,original_covariance_cases=72,complete_covariance_entries=25116,original_chisq_controls=924,complete_original_tables=10,unapplied_primary_reviews=12,unapplied_supporting_pointers=5,build_only_omission_receipts=len(omitted),component_admitted=True,SMR_or_HEIDI_workflow_admitted=False,original_HEIDI_tail_independently_implemented=False,implementation_credit=False,new_scoped_integrations=78,missing_integrations=253,catalog_changed=False,whole_paper_reproduced=False,whole_goal_complete=False,**layout));(target/'SHA256SUMS').write_text(''.join(pin(target/n)['sha256']+'  '+n+'\n' for n in ['controls.zip','WARNINGS.md','VALIDATION.json']),encoding='utf8',newline='\n');print('Sealed native component:',len(files),'logical files;',pin(target/'controls.zip')['bytes'],'bytes; no catalog tool credit')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable component/source/evidence checks')
    main()
