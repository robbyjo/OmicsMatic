# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Bind current SnpEff software to retained original executions and new genuine replays."""
from pathlib import Path
import hashlib,json,subprocess,time,xml.etree.ElementTree as ET,zipfile
from .primary import ROOT,encoded

DEVELOPMENT='4973f101c20e4679ec574256f06fe638d2a12407'
OLD_READER=dict(bytes=193530225,sha256='012bb0a32c165c609dad229df7a9c1ea0c1c461e8b842f9622ac64924d5c2643')
OLD_NOTICE=b'DEVELOPMENT PROFILE: source-built OmicsMatic only; no new catalog credit.'
NEW_NOTICE=b'SOURCE-BUILD PROFILE: catalog qualification is separate from command success.'

def pin(path):
    path=Path(path)
    with path.open('rb') as stream:sha=hashlib.file_digest(stream,'sha256').hexdigest()
    return dict(bytes=path.stat().st_size,sha256=sha)
def write(path,value):
    with Path(path).open('xb') as stream:stream.write(encoded(value))
def git(*args):return subprocess.check_output(['git','--no-replace-objects',*args],cwd=ROOT)
def source_transition():
    result={}
    for name in [line.decode() for line in git('ls-tree','-rz','--name-only',DEVELOPMENT).split(b'\0') if line]:
        science=(name.startswith('src/') and ('SnpEff' in name or '/snpeff/' in name)) or name.startswith(('tools/literature/snpeff_original/','examples/snpeff-workflow/'))
        if not science:continue
        old=git('show',DEVELOPMENT+':'+name);current=(ROOT/name).read_bytes()
        if name=='src/main/java/org/jlinalg/cli/SnpEffWorkflowCli.java':
            assert current.count(NEW_NOTICE)==1 and current.replace(NEW_NOTICE,OLD_NOTICE)==old,'SnpEff execution/scientific code changed beyond its notice'
        elif name=='src/test/java/org/jlinalg/cli/SnpEffWorkflowCliTest.java':
            assert current.replace(b'catalog qualification is separate',b'no new catalog credit')==old,'Scientific test changed beyond its notice expectation'
        else:assert current==old,'Qualified source/profile changed: '+name
        result[name]=dict(before=dict(bytes=len(old),sha256=hashlib.sha256(old).hexdigest()),after=pin(ROOT/name))
    assert len(result)>=25
    return dict(status='PASS_NOTICE_ONLY_FORWARD_SOURCE_TRANSITION',development_commit=DEVELOPMENT,files=result,scientific_sources_and_setup_recipe_unchanged=True,old_fingerprints_refreshed=False,old_source_or_git_overlaid=False)

def package_transition(old,jar):
    assert pin(old)==OLD_READER
    with zipfile.ZipFile(old) as before,zipfile.ZipFile(jar) as after:
        a={name for name in before.namelist() if not name.endswith('/')};b={name for name in after.namelist() if not name.endswith('/')}
        assert a==b
        changed=[name for name in sorted(a) if before.read(name)!=after.read(name)]
        assert changed==['org/jlinalg/cli/SnpEffWorkflowCli.class'],changed
    return dict(status='PASS_ALL_PACKAGED_MEMBERS_UNCHANGED_EXCEPT_NOTICE_OWNER',before=pin(old),after=pin(jar),changed_members=changed,scientific_readers_unchanged=True)

def qualify(folder,jar,out,java,build_root):
    folder=Path(folder);jar=Path(jar);out=Path(out);out.mkdir(parents=True,exist_ok=False)
    source=source_transition();closure=folder/'development-evidence-1'
    index=json.loads((closure/'INDEX.json').read_bytes());closed=json.loads((folder/'checkpoint-1/CLOSED.json').read_bytes())
    assert pin(closure/'INDEX.json')==closed['index'] and index['reader']==OLD_READER
    assert index['primary_papers']==250 and index['bundles']==11
    package=package_transition(closure/'reader/omicsmatic-0.4.0.jar',jar)
    for name,row in index['files'].items():
        target=closure/name;blob=closure/row['object'];assert target.is_file() and not target.is_symlink() and pin(blob)==row['pin']
        assert target.samefile(blob) or pin(target)==row['pin']
    cases={name:json.loads((folder/path).read_bytes()) for name,path in dict(public='public-controls-4/POSITIVE.json',negative='public-controls-4/NEGATIVE.json',original_errors='public-controls-4/ERROR-CONTROLS.json',human='human-controls-2/COMPLETED.json',copied='copied-reader-controls-1/COMPLETED.json',copied_replay='copied-reader-controls-1/REPLAY-COMPLETED.json',published_reference='p002-control-1/RESULT.json').items()}
    assert all(record['status'].startswith('PASS_') for record in cases.values())
    assert [len(cases[key]['cases']) for key in ['public','negative','original_errors','human','copied','published_reference']]==[14,10,6,4,11,3]
    assert cases['public']['jar_sha256']==OLD_READER['sha256']
    for key in ['public','negative','original_errors']:
        assert all(row['exit_code']==row['expected_exit'] for row in cases[key]['cases'])
    assert all(row['exit_code']==0 for key in ['human','copied','published_reference'] for row in cases[key]['cases'])
    assert cases['published_reference']['input']['synthetic_variants']==67 and cases['published_reference']['input']['reference_bases']==1139633
    assert not cases['published_reference']['input']['original_author_snp_table'] and not cases['published_reference']['input']['author_binary']
    reference=json.loads((folder/'p002-reference-1/REFERENCE.json').read_bytes())
    expected={'genes.gff':'fe21cb9bb3e5c68823e74350739d6978d1054b39963e13cd8ccec7f72bf23720','snpEffectPredictor.bin':'a2001d782cbea89311bd61a92be4339013a579c2c53c6f692b5a4af9b853a484'}
    for row in reference['files']:assert pin(folder/'p002-reference-1'/row['name'])==dict(bytes=row['bytes'],sha256=expected[row['name']])
    assert reference['repository_commit']=='d20dc3bd479c81fcfd165ad189bbd2fc1fb21b9f'
    counts=dict(tests=0,failures=0,errors=0,skipped=0)
    for path in (Path(build_root)/'test-results').glob('TEST-*.xml'):
        suite=ET.parse(path).getroot()
        for key in counts:counts[key]+=int(suite.get(key,'0'))
    assert counts==dict(tests=35,failures=0,errors=0,skipped=0),counts
    processes=[]
    def execute(name,args,timeout=300):
        command=[str(java),'-jar',str(jar),'snpeff-tools']+args;began=time.monotonic()
        with (out/(name+'.stdout')).open('xb') as so,(out/(name+'.stderr')).open('xb') as se:
            proc=subprocess.run(command,cwd=out,stdout=so,stderr=se,stdin=subprocess.DEVNULL,timeout=timeout)
        row=dict(name=name,command=command,exit_code=proc.returncode,seconds=round(time.monotonic()-began,3));write(out/(name+'.PROCESS.json'),row);processes.append(row)
        assert proc.returncode==0,'Inspect retained current-reader '+name+' diagnostics'
    execute('current-help',['--help'],30)
    help_text=(out/'current-help.stdout').read_text();assert 'catalog qualification is separate' in help_text and 'SKIP3' in help_text and 'FAIL2' in help_text
    bundles={path.name:path for path in (closure/'bundles').iterdir()}
    bundles['p002-published-reference']=folder/'p002-control-1/run'
    for name,bundle in sorted(bundles.items()):
        execute('verify-'+name,['verify','--bundle',str(bundle),'--out',str(out/('verify-'+name))])
    for name,bundle in [('human',bundles['human-original']),('p002',bundles['p002-published-reference'])]:
        execute('replay-'+name,['replay','--bundle',str(bundle),'--out',str(out/('replay-'+name))])
        execute('verify-replay-'+name,['verify','--bundle',str(out/('replay-'+name)),'--out',str(out/('verify-replay-'+name))])
    write(out/'SOURCE-TRANSITION.json',source);write(out/'PACKAGE-TRANSITION.json',package)
    for name,record in cases.items():write(out/(name+'.json'),record)
    value=dict(status='PASS_CURRENT_SOURCE_SCOPED_ORIGINAL_SNPEFF_AND_PUBLISHED_REFERENCE_COMPATIBILITY',jar=pin(jar),source_transition=source,package_transition=package,retained_java_tests=counts,python_policy_tests=3,retained_public_controls=14,retained_negative_controls=16,current_verifications=14,current_genuine_replays=2,current_processes=processes,full_hg38kg_reference=dict(variants=969,annotation_rows=11905,diagnostic_rows=1871,error_rows=0),published_p002_reference=dict(synthetic_snps=67,annotation_rows=78,diagnostic_rows=0,reference_bases=1139633,author_snp_table=False,author_binary=False,author_config_known=False),private_legacy_redistribution_restrictions=True,whole_tool_qualified=False,whole_paper_reproduced=False,whole_goal_complete=False)
    write(out/'QUALIFICATION.json',value)
    return value
