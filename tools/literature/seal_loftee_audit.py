# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Seal the native LOFTEE audit component and complete original synthetic controls; no LOFTEE execution-adapter credit."""
import json,os,zipfile
from setup_deseq2_reference import ROOT,pin,save
from signac_scan_evidence import pack,byte_pin


def main():
    base=ROOT/'build/literature-review/loftee-runtime';target=ROOT/'docs/literature/loftee-audit-development';assert not target.exists();build=json.loads((base/'audit-build-2/COMPLETED.json').read_bytes());native=json.loads((base/'audit-native-validation-3/COMPLETED.json').read_bytes());example=json.loads((base/'audit-example-validation-1/COMPLETED.json').read_bytes());fixture=json.loads((base/'audit-fixtures-1/COMPLETED.json').read_bytes());assert native['tests_per_JVM']==43 and len(native['JVMs'])==3 and all(r['exit_code']==r['skipped']==0 for r in native['JVMs']);assert example['status']=='passed-exact-documented-LOFTEE-native-audit-example' and native['artifact']==example['artifact']==build['artifact'];assert fixture['original_variants']==60 and fixture['original_annotations']==320
    reports=[build,native,example,fixture]
    for name in ['original-probe-4','original-probe-5-cited-fork']:
        p=json.loads((base/name/'COMPLETED.json').read_bytes());assert p['status'].startswith('passed-original-') and p['artifact']==build['artifact'] and len(p['cases'])==2;reports.append(p)
    for p in reports:
        for name,value in p.get('source_pins',{}).items():assert pin(ROOT/name)==value,name
    names={p.relative_to(ROOT).as_posix() for p in (ROOT/'tools/literature').glob('*loftee*') if p.is_file()};names.update(['src/main/java/org/jlinalg/genetics/LofteeVcfAudit.java','src/test/java/org/jlinalg/cli/LofteeVcfAuditTest.java','src/test/java/org/jlinalg/cli/LofteeOriginalAuditTest.java','src/test/resources/org/jlinalg/genetics/loftee-original/controls-v1.zip','src/test/resources/org/jlinalg/genetics/loftee-original/.gitattributes','docs/literature/LOFTEE_AUDIT_DEVELOPMENT.md']);source_pins={n:pin(ROOT/n) for n in sorted(names)};files={'current-source/'+n:(ROOT/n).read_bytes() for n in names};omissions={}
    paths=[]
    for directory,subdirs,filenames in os.walk(base,followlinks=False):
        folder=ROOT.__class__(directory);relative=folder.relative_to(base).parts
        subdirs[:]=sorted(d for d in subdirs if d!='__pycache__' and not(relative==('reference-setup-1',) and d in ['environment','mamba-root']) and not(relative==('bioperl-setup-1',) and d=='api'))
        paths.extend(folder/name for name in sorted(filenames))
    for path in paths:
        if not path.is_file():continue
        parts=path.relative_to(base).parts
        if '__pycache__' in parts or path.suffix=='.pyc':continue
        omit=None
        if parts[0]=='reference-setup-1' and (len(parts)>1 and parts[1] in ['environment','mamba-root','micromamba','micromamba.tar.bz2']):
            continue # complete published package records, exact URLs/digests and module-load identities are retained below
        if parts[0]=='bioperl-setup-1' and len(parts)>1 and parts[1]=='api':continue
        if parts[0]=='paper-source-review-1':omit='Primary XML/working excerpts remain in the literature cache; public role drafts retain DOI/PMCID/paragraph digests and paraphrases.'
        if parts[0]=='source-intake-1' and len(parts)>1 and parts[1].startswith('brava-'):omit='Cited pipeline source has no captured redistribution license; exact snapshot retained privately with commit/member/archive identities. Original Apache-2.0 LOFTEE source used in controls is public below.'
        if path.suffix=='.jar':omit='Full development JAR retained locally; source and all candidate-owned entries plus exact parent/candidate member identities are retained publicly.'
        name=path.relative_to(ROOT).as_posix()
        if omit:omissions[name]=dict(**pin(path),reason=omit)
        else:files[name]=path.read_bytes()
    files['EXPLICIT-OMISSIONS.json']=(json.dumps(dict(private_or_unlicensed_files=omissions,private_dependency_environment_omitted=True,private_BioPerl_API_tree_omitted=True,original_conda_package_records_and_download_URLs_retained=True,loaded_module_paths_and_hashes_retained=True,all_final_original_synthetic_inputs_outputs_logs_complete=True,LOFTEE_execution_adapter_admitted=False),indent=2)+'\n').encode();jar=ROOT/build['artifact']['path'];parent=ROOT/build['parent_artifact']['path']
    with zipfile.ZipFile(parent) as before,zipfile.ZipFile(jar) as after:
        old={n:byte_pin(before.read(n)) for n in before.namelist()};new={n:byte_pin(after.read(n)) for n in after.namelist()};assert all(new[n]==v for n,v in old.items()) and set(new)-set(old)==set(build['added_entries'])
        for name in build['added_entries']:files['candidate-owned-entry/'+name]=after.read(name)
    for name,items in [('parent',old),('candidate',new)]:files['artifact-'+name+'-entries.json']=(json.dumps(items,sort_keys=True,indent=2)+'\n').encode()
    data=ROOT/'docs/literature/omics-2023-2026';counts=json.loads((data/'summary.json').read_bytes())['implementation_status_counts'];assert (counts['native-subset-validated'],counts['upstream-adapter-validated'],counts['missing'])==(17,63,251);inventory=json.loads((data/'tool-inventory.json').read_bytes());assert next(t for t in inventory if t['id']=='loftee')['implementation_status']=='missing'
    for name in ['summary.json','tool-inventory.json']:files['catalog-baseline/'+name]=(data/name).read_bytes()
    canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar');assert canonical==build['canonical_executable_unchanged'];held=json.loads((ROOT/'build/literature-review/push-checkpoint-2026-11-07-smr-workflow-1/selection.json').read_bytes())['preserved'];assert len(held)==18 and all(pin(ROOT/n)==v for n,v in held.items());files['preserved-unrelated-worktree-pins.json']=(json.dumps(held,indent=2)+'\n').encode();target.mkdir();(target/'.gitattributes').write_text('* -text\n',encoding='utf8',newline='\n');layout=pack(target/'controls.zip',files);assert (target/'controls.zip').stat().st_size<100000000
    license_text=(base/'license-intake-1/APACHE-2.0.txt').read_bytes();assert len(license_text)<=65536 and b'Apache License' in license_text and b'Version 2.0' in license_text
    (target/'APACHE-2.0.txt').write_bytes(license_text);(target/'WARNINGS.md').write_text('''# Warnings and required actions

**NATIVE AUDIT COMPONENT ONLY.** Read LOFTEE_AUDIT_DEVELOPMENT.md. Real unchanged VEP105/LOFTEE controls ran, but the typed execution adapter, production runtime supervision and paper workflows remain unfinished. LOFTEE receives no implementation credit; the catalog baseline remains80 new scoped integrations/251 missing. The canonical executable is unchanged.

**FAIL2: no complete audited result after any failure.** Preserve original VCFs, logs, warnings and provisional callback/ledger rows. Correct the named cause and retry NEW paths. Do not impute HC/LC, collapse transcripts/alleles, strip flags, repair IDs, replace NaN/Inf or admit a partial stream. HC flags remain flags; non-target empty confidence is not a benign call. BCF/symbolic alleles require a separate validated profile.

**SKIP3: skip dependent annotation when dependencies or resources are unavailable/forbidden.** Java audit installs nothing. Experimental private setup helpers are not a complete fresh-checkout annotation installer. The first setup JSON-reader failure, missing Bio::Perl, WSL inline-command failure, absent cache, overly strict custom-field check and incorrect shared LC expectation remain retained. Original VEP installer NOTESTS and smartmatch warnings are not hidden or relabeled as passed upstream suites.

**SOURCE AND SCIENTIFIC LIMITS.** Both original LOFTEE source editions, complete synthetic inputs/VCFs/ledgers/logs and eight independent Python source-ledger comparisons are retained. GTF file-mode controls use a synthetic4000-base reference, not human GRCh38/GENCODE or a paper cache. The short-intron control did not exercise SMALL_INTRON/GC_TO_GT_DONOR. Original GERP/percentile and Perl0/false semantics stay distinct. No biological calibration, HC/LC prediction reimplementation or paper reproduction is claimed.

**EXPLICIT OMISSIONS.** Private full JARs/dependency/BioPerl trees and unlicensed cited pipeline source are not redistributed. Public package URLs/digests, module-load identities, commit/archive/member identities, candidate class entries and original Apache-2.0 LOFTEE source remain available. No registered dbNSFP resource or human cache was obtained. Eight paper-role drafts are unapplied and preserve version/transcript/mask distinctions.
''',encoding='utf8',newline='\n');parents={n:pin(ROOT/n) for n in ['docs/literature/smr-workflow-development/VALIDATION.json','docs/literature/smr-workflow-development/controls.zip','docs/literature/omics-2023-2026/smr-catalog-validation.json','docs/literature/omics-2023-2026/smr-catalog-support.zip']};save(target/'VALIDATION.json',dict(schema_version=1,status='passed-native-LOFTEE-output-audit-component',source_pins=source_pins,parent_proofs=parents,artifact=build['artifact'],parent_artifact=build['parent_artifact'],added_entries=build['added_entries'],unchanged_parent_entries=build['unchanged_parent_entries'],canonical_executable_unchanged=canonical,archive=pin(target/'controls.zip'),license=pin(target/'APACHE-2.0.txt'),warnings=pin(target/'WARNINGS.md'),final_build='audit-build-2',final_native='audit-native-validation-3',final_example='audit-example-validation-1',final_fixture='audit-fixtures-1',final_original_probes=['original-probe-4','original-probe-5-cited-fork'],tests_per_JVM=43,JVMs=3,original_cases=4,original_variant_rows=60,original_annotation_rows=320,independent_source_ledgers=8,unapplied_primary_role_drafts=8,unapplied_supporting_pointers=1,component_admitted=True,LOFTEE_execution_adapter_admitted=False,implementation_credit=False,catalog_changed=False,whole_paper_reproduced=False,whole_goal_complete=False,**layout));(target/'SHA256SUMS').write_text(''.join(pin(target/n)['sha256']+'  '+n+'\n' for n in ['controls.zip','APACHE-2.0.txt','WARNINGS.md','VALIDATION.json']),encoding='utf8',newline='\n');assert all(pin(ROOT/n)==v for n,v in source_pins.items());print('Sealed native LOFTEE audit component:',len(files),'files;',4,'original cases; no named-tool credit')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable component/source/evidence checks')
    main()
