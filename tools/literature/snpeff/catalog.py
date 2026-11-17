#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Admit/recheck scoped SnpEff annotation after primary, software and source-transition checks."""
from pathlib import Path
import argparse,concurrent.futures,hashlib,json,shutil,subprocess,sys,traceback,zipfile
sys.dont_write_bytecode=True
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from snpeff.primary import ROOT,audit,encoded
from snpeff.model import META,GUIDE,PROGRAM,registry,transition,check
from snpeff.qualification import OLD_READER,pin,source_transition,package_transition
from snpeff.payloads import inspect as inspect_payloads
from wire_signac_catalog import delta
from catalog_source_integrity import reverse_delta
if not __debug__:raise RuntimeError('Catalog assertions must remain enabled')

BASE='e0427e007de95e42345ecfaf05f46d49ac95f69c'
EVIDENCE='docs/literature/snpeff-catalog-integration'
PROOF='docs/literature/omics-2023-2026/snpeff-catalog-validation.json'
COMPATIBILITY='docs/literature/omics-2023-2026/snpeff-reviews-shared-source-compatibility.json'
STATUS='PASS_SCOPED_ORIGINAL_SNPEFF_ANNOTATION_CATALOG_ADMISSION'
ACTION='Build current source. Missing/forbidden prerequisites mean SKIP3 and dependent analysis skipped. Other failures mean FAIL2 and no complete result. Preserve original diagnostics and failed attempts, resolve the named cause and retry NEW output. Legacy runtime/evidence is private academic noncommercial material with separate redistribution permissions. Selected source builds, database snapshots and synthetic inputs do not reproduce author studies. No original verifier, historical fingerprint or Git source is overlaid. The full250-paper goal remains open.'
DISPATCH=b'            if (arguments.length > 0 && arguments[0].equals("snpeff-tools"))\n                return SnpEffWorkflowCli.run(Arrays.copyOfRange(arguments, 1, arguments.length), output, errorOutput);\n'
HELP=b'              java -jar omicsmatic-<version>.jar snpeff-tools --help\n'

def git(*args):return subprocess.check_output(['git','--no-replace-objects',*args],cwd=ROOT)
def rawpin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def write(path,value):
    with Path(path).open('xb') as stream:stream.write(encoded(value))
def owned(name):
    return name in [PROOF,COMPATIBILITY] or name.startswith(('tools/literature/snpeff/','tools/literature/snpeff_original/','examples/snpeff-workflow/','docs/literature/snpeff-workflow-development/',EVIDENCE+'/','src/test/resources/org/jlinalg/genetics/snpeff/')) or name.startswith(('docs/literature/SNPEFF_','src/main/java/org/jlinalg/cli/SnpEff','src/main/java/org/jlinalg/genetics/SnpEff','src/test/java/org/jlinalg/cli/SnpEff','src/test/java/org/jlinalg/genetics/SnpEff'))
def own_sources():
    names={row.decode() for row in git('ls-files','--cached','--others','--exclude-standard','-z').split(b'\0') if row}
    return sorted(name for name in names if owned(name) and name not in [PROOF,COMPATIBILITY] and not name.startswith(EVIDENCE+'/'))
def source_inventory():
    baseline={}
    for row in git('ls-tree','-rz','--full-tree',BASE).split(b'\0'):
        if not row:continue
        header,name=row.split(b'\t',1);mode,kind,oid=header.decode().split();name=name.decode();assert kind=='blob' and mode in ['100644','100755']
        if name not in META and not owned(name):baseline[name]=dict(mode=mode,git_object=oid)
    names={row.decode() for row in git('ls-files','--cached','--others','--exclude-standard','-z').split(b'\0') if row}
    assert {name for name in names if name not in META and not owned(name)}==set(baseline)
    dispatcher='src/main/java/org/jlinalg/cli/JLinAlgCli.java'
    def inspect(entry):
        name,identity=entry;path=ROOT/name;assert path.is_file() and not path.is_symlink();raw=path.read_bytes()
        if name==dispatcher:
            assert raw.count(DISPATCH)==1 and raw.count(HELP)==1
            assert raw.replace(DISPATCH,b'').replace(HELP,b'')==git('show',BASE+':'+name)
        else:
            candidates=[raw]+([raw.replace(b'\r\n',b'\n')] if b'\0' not in raw else [])
            assert any(hashlib.sha1(b'blob '+str(len(value)).encode()+b'\0'+value).hexdigest()==identity['git_object'] for value in candidates),name
        return name,dict(**identity,**rawpin(raw))
    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as workers:rows=dict(workers.map(inspect,baseline.items()))
    return dict(status='PASS_UNRELATED_SCIENTIFIC_SOURCES_UNCHANGED_WITH_EXACT_SNPEFF_DISPATCH',baseline_commit=BASE,files_checked=len(rows),files=rows,shared_dispatcher_change_only_new_route_and_help=True,old_source_or_git_overlaid=False)

def qualification(folder,workflow_root,jar,baseline):
    folder,workflow_root,jar,baseline=map(Path,[folder,workflow_root,jar,baseline])
    value=json.loads((folder/'QUALIFICATION.json').read_bytes())
    assert value['status']=='PASS_CURRENT_SOURCE_SCOPED_ORIGINAL_SNPEFF_AND_PUBLISHED_REFERENCE_COMPATIBILITY'
    assert value['jar']==pin(jar) and value['source_transition']==source_transition()
    assert value['package_transition']==package_transition(workflow_root/'development-evidence-1/reader/omicsmatic-0.4.0.jar',jar)
    assert value['retained_java_tests']==dict(tests=35,failures=0,errors=0,skipped=0)
    assert value['python_policy_tests']==3 and (value['retained_public_controls'],value['retained_negative_controls'],value['current_verifications'],value['current_genuine_replays'])==(14,16,14,2)
    assert len(value['current_processes'])==17 and all(row['exit_code']==0 for row in value['current_processes'])
    for row in value['current_processes']:assert json.loads((folder/(row['name']+'.PROCESS.json')).read_bytes())==row
    assert value['full_hg38kg_reference']==dict(variants=969,annotation_rows=11905,diagnostic_rows=1871,error_rows=0)
    assert value['published_p002_reference']==dict(synthetic_snps=67,annotation_rows=78,diagnostic_rows=0,reference_bases=1139633,author_snp_table=False,author_binary=False,author_config_known=False)
    # A genuine historical checkout is verified in place; current sources are never projected into it.
    assert subprocess.check_output(['git','--no-replace-objects','rev-parse','HEAD'],cwd=baseline).decode().strip()==BASE
    assert not subprocess.check_output(['git','status','--porcelain=v1','-z','--untracked-files=all'],cwd=baseline)
    predecessor=baseline/'build/snpeff-predecessor-check-2/COMPLETED.json'
    original=json.loads(predecessor.read_bytes())
    assert original['status']=='PASS_SCOPED_ORIGINAL_SNAKEMAKE_AND_NATIVE_BENCHMARK_CATALOG_ADMISSION' and original['current_source_guard_passed']
    # Bind the old eight-file metadata to the actual baseline commit, not just a green receipt.
    old_proof=json.loads((baseline/'docs/literature/omics-2023-2026/snakemake-catalog-validation.json').read_bytes())
    for name,expected in old_proof['after'].items():assert pin(baseline/name)==expected
    result=dict(value)
    payloads=inspect_payloads(folder,workflow_root)
    assert payloads==json.loads((folder/'PAYLOADS.json').read_bytes())
    result['current_payloads']=payloads
    result['predecessor']=dict(baseline_commit=BASE,verification=pin(predecessor),original_verifier=pin(baseline/'tools/literature/snakemake/catalog.py'),source_checkout_unchanged=True,historical_fingerprints_refreshed=False)
    return result

def main():
    parser=argparse.ArgumentParser(description=__doc__,epilog=ACTION);mode=parser.add_mutually_exclusive_group(required=True);mode.add_argument('--apply',action='store_true');mode.add_argument('--verify',action='store_true')
    for name in ['primary-cache','qualification-root','workflow-evidence-root','jar','predecessor-checkout','out']:parser.add_argument('--'+name,type=Path,required=True)
    args=parser.parse_args();out=None;before=after=None;started=False;created_proof=None
    try:
        target=args.out.absolute();assert target.resolve().is_relative_to(ROOT/'build');target.mkdir(parents=True,exist_ok=False);out=target
        write(out/'STARTED.json',dict(applying=args.apply,required_action=ACTION))
        sources={name:pin(ROOT/name) for name in own_sources()};source=source_inventory();science=qualification(args.qualification_root,args.workflow_evidence_root,args.jar,args.predecessor_checkout)
        if args.apply:
            assert all(not (ROOT/name).exists() for name in [PROOF,COMPATIBILITY,EVIDENCE])
            before={name:(ROOT/name).read_bytes() for name in META}
            previous=json.loads((ROOT/'docs/literature/omics-2023-2026/snakemake-catalog-validation.json').read_bytes())
            assert {name:rawpin(raw) for name,raw in before.items()}==previous['after'],'Prior catalog metadata changed; retain it and review'
        else:
            proof=json.loads((ROOT/PROOF).read_bytes());assert proof['status']==STATUS and proof['sources']==sources
            for name,expected in proof['evidence'].items():assert pin(ROOT/name)==expected,name
            with zipfile.ZipFile(ROOT/EVIDENCE/'catalog-before.zip') as saved:
                assert len(saved.namelist())==8 and set(saved.namelist())==set(META);before={name:saved.read(name) for name in META}
            assert source==json.loads((ROOT/EVIDENCE/'SOURCE-TREE.json').read_bytes()) and science==json.loads((ROOT/EVIDENCE/'QUALIFICATION.json').read_bytes())
        primary=audit(args.primary_cache,json.loads(before[META[0]]));after=transition(before,primary);result=check(before,after,primary)
        changes=[delta(name,before[name],after[name]) for name in META]
        assert all(reverse_delta(after[row['path']],row)==before[row['path']] for row in changes)
        journal=dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',tool_id='snpeff',scope='Exact inverse of the SnpEff-only eight-file metadata transition. Historical verifiers and Git sources are never overlaid.',paths=changes)
        assert len(registry())==485
        if args.apply:
            assert all((ROOT/name).read_bytes()==raw for name,raw in before.items())
            evidence=ROOT/EVIDENCE;evidence.mkdir();(evidence/'.gitattributes').write_bytes(b'* -text\n')
            with zipfile.ZipFile(evidence/'catalog-before.zip','x',zipfile.ZIP_DEFLATED,compresslevel=9) as saved:
                for name,raw in before.items():saved.writestr(name,raw)
            for name,value in [('SOURCE-TREE.json',source),('QUALIFICATION.json',science),('CENSUS.json',primary),('REGISTRY.json',registry())]:write(evidence/name,value)
            for name in ['SOURCE-TRANSITION.json','PACKAGE-TRANSITION.json','PAYLOADS.json','public.json','negative.json','original_errors.json','human.json','copied.json','copied_replay.json','published_reference.json']:shutil.copyfile(args.qualification_root/name,evidence/name)
            (evidence/'WARNINGS.md').write_text('# Scope and recovery\n\n'+ACTION+'\n',encoding='utf-8');write(ROOT/COMPATIBILITY,journal)
            started=True
            for name,raw in after.items():(ROOT/name).write_bytes(raw)
            assert source_inventory()==source and {name:pin(ROOT/name) for name in own_sources()}==sources
            files={path.relative_to(ROOT).as_posix():pin(path) for path in evidence.rglob('*') if path.is_file()};files[COMPATIBILITY]=pin(ROOT/COMPATIBILITY)
            proof=dict(status=STATUS,**result,sources=sources,evidence=files,before={name:rawpin(raw) for name,raw in before.items()},after={name:pin(ROOT/name) for name in META},qualification=science,unrelated_metadata_preserved=True,unrelated_scientific_sources_unchanged=True,original_metadata_snapshot_retained=True,old_verifiers_or_git_overlaid=False,source_build_required=True,released_binary_contains_workflow_adapter=False,whole_tool_qualified=False,whole_paper_reproduced=False)
            write(ROOT/PROOF,proof);created_proof=encoded(proof)
        else:
            assert all((ROOT/name).read_bytes()==raw for name,raw in after.items())
            assert proof['after']=={name:pin(ROOT/name) for name in META} and proof['qualification']==science
            assert json.loads((ROOT/COMPATIBILITY).read_bytes())==journal
        assert {name:pin(ROOT/name) for name in own_sources()}==sources
        write(out/'COMPLETED.json',dict(status=STATUS,**result,current_source_guard_passed=True,original_software_qualification_retained=True))
        print(json.dumps(dict(status=STATUS,**result)),flush=True);return 0
    except Exception:
        detail=traceback.format_exc();restored=[]
        if started and before is not None and after is not None:
            for name,raw in before.items():
                current=(ROOT/name).read_bytes()
                if current==after[name]:(ROOT/name).write_bytes(raw);restored.append(dict(path=name,status='original-restored'))
                elif current==raw:restored.append(dict(path=name,status='original-retained'))
                else:restored.append(dict(path=name,status='concurrent-content-preserved-review-required'))
        if created_proof is not None and (ROOT/PROOF).exists():
            original=(ROOT/PROOF).read_bytes();(out/'INVALIDATED-PROOF.json').write_bytes(original)
            if original==created_proof:(ROOT/PROOF).unlink()
        if out is not None:write(out/'FAILURE.json',dict(status='FAIL2_NO_CATALOG_ADMISSION',detail=detail,rollback=restored,required_action=ACTION))
        print('FAIL2: no SnpEff catalog admission. Retain all diagnostics and resolve the named discrepancy before retrying NEW output.\n'+detail,file=sys.stderr);return 2
if __name__=='__main__':raise SystemExit(main())
