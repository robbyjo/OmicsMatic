#!/usr/bin/env python3
"""Check the public pruning JAR against original masks and independently decoded reference calls."""
from pathlib import Path
import argparse
import csv
import datetime as dt
import hashlib
import json
import re
import shutil
import subprocess
import sys

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(Path(__file__).resolve().parent))
import install_plink

def sha(path):return hashlib.sha256(Path(path).read_bytes()).hexdigest()
def table(path):
    with Path(path).open(encoding='utf-8',newline='') as stream:
        rows=list(csv.DictReader(stream,delimiter='\t'))
    assert all(None not in row and all(v is not None for v in row.values()) for row in rows),path
    return rows

def options(home,folder,engine,out):
    return dict(task='prune',engine=engine,home=str(home),bfile=str(folder/'known'),samples=str(folder/'samples.tsv'),out=str(out),**{
        'genome-build':'unspecified','chromosome-scope':'autosomes','unresolved-alleles':'error','reference-population':'founders',
        'window-unit':'variants','window-size':'20','step-size':'5','r2':'.2','pair-order':'legacy',
        'missing-genotypes':'upstream','minimum-reference-calls':'3'})

def verify_output(out,folder,case,resources,aliases=None):
    aliases=aliases or {}
    bim=[row.split() for row in (folder/'known.bim').read_text(encoding='utf-8').splitlines()]
    fam=[row.split() for row in (folder/'known.fam').read_text(encoding='utf-8').splitlines()]
    selections={(row['fid'],row['iid']):row for row in table(folder/'samples.tsv')}
    reference=[i for i,row in enumerate(fam) if selections[tuple(row[:2])]['include']=='yes' and row[2:4]==['0','0']]
    assert len(reference)==123
    expected_in={aliases.get(v,v) for v in (resources/case['in_file']).read_text().splitlines()}
    expected_out={aliases.get(v,v) for v in (resources/case['out_file']).read_text().splitlines()}
    retained=(out/'prune.in').read_text(encoding='utf-8').splitlines()
    removed=(out/'prune.out').read_text(encoding='utf-8').splitlines()
    assert set(retained)==expected_in and len(retained)==len(expected_in)==int(case['retained'])
    assert set(removed)==expected_out and len(removed)==len(expected_out)==int(case['removed'])>0
    assert not expected_in&expected_out and expected_in|expected_out=={row[1] for row in bim}
    assert retained==[row[1] for row in bim if row[1] in expected_in]
    ledger=table(out/'sample-reference.tsv');assert len(ledger)==len(fam)==128
    by_original={(row['fid'],row['iid']):row for row in ledger};assert len(by_original)==128
    for i,row in enumerate(fam):
        item=by_original[tuple(row[:2])]
        assert item['group_unused']==selections[tuple(row[:2])]['group']
        assert (item['initial_include']=='true')==(selections[tuple(row[:2])]['include']=='yes')
        assert (item['FAM_founder']=='true')==(row[2:4]==['0','0'])
        assert (item['used_for_reference']=='true')==(i in reference)
    assert any(row['group_unused']=='case' and row['used_for_reference']=='true' for row in ledger)
    keep={tuple(row.split()) for row in (out/'upstream/reference.keep').read_text().splitlines()}
    assert keep=={(row['upstream_fid'],row['upstream_iid']) for row in ledger if row['used_for_reference']=='true'}
    raw=(folder/'known.bed').read_bytes();stride=(len(fam)+3)//4
    assert raw[:3]==b'\x6c\x1b\x01' and len(raw)==3+stride*len(bim)
    stats={row['variant']:row for row in table(out/'variant-reference.tsv')};assert len(stats)==len(bim)==91
    for v,row in enumerate(bim):
        calls=[]
        for i in reference:
            code=(raw[3+v*stride+i//4]>>(2*(i%4)))&3
            if code!=1:calls.append({0:2,2:1,3:0}[code])
        truth=dict(called=str(len(calls)),missing=str(len(reference)-len(calls)),reference_samples=str(len(reference)),
                   minimum_dosage=str(min(calls)),maximum_dosage=str(max(calls)),status='ADMITTED')
        assert all(stats[row[1]][key]==value for key,value in truth.items()),(row[1],stats[row[1]],truth)
    decisions=table(out/'variant-decisions.tsv');assert len(decisions)==91
    for i,row in enumerate(decisions):
        original=bim[i];assert row['variant']==original[1] and row['source_ordinal']==str(i+1)
        assert row['initial_include']=='true' and (row['retained_for_pruned_set']=='true')==(original[1] in expected_in)
        assert row['decision']==('ORIGINAL_RETAINED' if original[1] in expected_in else 'ORIGINAL_PRUNED')
        assert [row['chromosome'],row['position'],row['original_a1'],row['original_a2']]==[original[0],original[3],original[4],original[5]]
    stdout=(out/'upstream/prune.stdout').read_text(encoding='utf-8')
    assert re.search(r'--keep: 123 (?:people|samples) remaining\.',stdout)
    if case['engine']=='plink1':assert '123 founders and 0 nonfounders present' in stdout
    else:assert re.search(r'123 samples \([^\n]*123 founders\) remaining after',stdout)
    expected_window=case['window']+('kb' if case['unit']=='kb' else '')
    assert f'--indep-pairwise {expected_window} {case["step"]} 0.2' in stdout
    if case['engine']=='plink2':assert '--indep-order '+('1' if case['pair_order']=='legacy' else '2') in stdout
    assert (out/'upstream/version.stdout').read_text(encoding='utf-8').strip()==install_plink.LOCK['engines'][case['engine']]['version_stdout']
    assert not (out/'genotypes.bed').exists() and not (out/'partial').exists()
    return dict(retained=len(retained),removed=len(removed),reference_founders=len(reference),
                independent_variant_call_rows=len(stats),sample_identity_rows=len(ledger),variant_decision_rows=len(decisions))

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar',type=Path,required=True);parser.add_argument('--home',type=Path,required=True)
    parser.add_argument('--work',type=Path,required=True);parser.add_argument('--java',action='append',required=True)
    args=parser.parse_args();jar,home,work=args.jar.resolve(),args.home.resolve(),args.work.resolve()
    if work.is_relative_to(home) or home.is_relative_to(work):parser.error('--work must be outside the original runtime home')
    if not str(work).isascii() or not str(jar).isascii():parser.error('Use ASCII harness/JAR paths; Unicode analysis paths are stored inside UTF-8 recipes')
    work.mkdir(parents=True,exist_ok=False)
    record=dict(status='running',jar_sha256=sha(jar),producer_sha256=sha(__file__),started_utc=dt.datetime.now(dt.timezone.utc).isoformat(),
                steps=[],workflows=[],negative_controls=0,scope='Actual public packaged original --indep-pairwise on Java runtimes;authentic frozen original partitions,independent decoded call counts and exact identity/family checks. No native pruning estimator,global LD-independence or whole-project gate claim.')
    shutil.copy2(__file__,work/'producer-snapshot.py')
    def save():(work/'verification.json').write_text(json.dumps(record,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    def run(java,values,label,expected=0,preflight=False):
        recipe=work/(label+'.json');recipe.write_text(json.dumps(values,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
        command=[java,'-jar',str(jar),'plink-tools','--options-file',str(recipe)]
        result=subprocess.run(command,cwd=ROOT,capture_output=True,timeout=240)
        stdout,stderr=work/(label+'.stdout'),work/(label+'.stderr');stdout.write_bytes(result.stdout);stderr.write_bytes(result.stderr)
        record['steps'].append(dict(command=command,exit_code=result.returncode,expected=expected,recipe_sha256=sha(recipe),stdout_sha256=sha(stdout),stderr_sha256=sha(stderr)));save()
        assert result.returncode==expected,(label,result.returncode,result.stderr.decode(errors='replace'))
        out=Path(values['out'])
        if expected:
            record['negative_controls']+=1
            assert not (out/'prune.in').exists() and not (out/'prune.out').exists()
            if preflight:assert not out.exists()
            else:assert (out/'WARNINGS.md').is_file() and not (out/'upstream/prune.stdout').exists()
        return out
    save()
    try:
        if sys.platform!='win32' or not install_plink.available(home):
            record.update(status='skipped-original-runtime-unavailable')
            (work/'WARNINGS.md').write_text('# PRUNING VALIDATION SKIPPED\n\nRequired action: on supported Windows x86_64,run python tools/literature/install_plink.py --home NEW_PRIVATE_HOME --allow-install;add --allow-download only where downloads are permitted. Supply the resulting sealed home. Where setup/execution is forbidden,skip this analysis. No validation passed.\n',encoding='utf-8');save();return 3
        resources=ROOT/'src/test/resources/org/jlinalg/genetics/plink-pruning';provenance=json.loads((resources/'provenance.json').read_text(encoding='utf-8'))
        for name,digest in provenance['input_and_mask_sha256'].items():assert sha(resources/name)==digest,name
        cases=table(resources/'cases.tsv');assert len(cases)==6
        folders={}
        for name in ['base','unicode','sparse','nonvariable','no-calls','zero-position','unsorted']:
            folder=work/('输入-'+name);folder.mkdir();folders[name]=folder
            for filename in ['known.bed','known.bim','known.fam','samples.tsv']:shutil.copy2(resources/filename,folder/filename)
        folder=folders['unicode'];fam=[row.split() for row in (folder/'known.fam').read_text().splitlines()];samples=table(folder/'samples.tsv')
        for ordinal,pair in zip([2,3,4],[['A_B','C'],['A','B_C'],['家族','成员']],strict=True):
            fam[ordinal][:2]=pair;samples[ordinal]['fid'],samples[ordinal]['iid']=pair
        (folder/'known.fam').write_text(''.join(' '.join(row)+'\n' for row in fam),encoding='utf-8')
        with (folder/'samples.tsv').open('w',encoding='utf-8',newline='') as stream:
            writer=csv.DictWriter(stream,fieldnames=['fid','iid','include','group'],delimiter='\t',lineterminator='\n');writer.writeheader();writer.writerows(samples)
        (folder/'known.bim').write_text((folder/'known.bim').read_text().replace(' V6 ',' 变异_六 '),encoding='utf-8')
        folder=folders['sparse'];rows=table(folder/'samples.tsv')
        for row in rows[30:]:row['include']='no'
        with (folder/'samples.tsv').open('w',encoding='utf-8',newline='') as stream:
            writer=csv.DictWriter(stream,fieldnames=['fid','iid','include','group'],delimiter='\t',lineterminator='\n');writer.writeheader();writer.writerows(rows)
        for name,code in [('nonvariable',255),('no-calls',85)]:
            path=folders[name]/'known.bed';raw=bytearray(path.read_bytes());raw[3:35]=bytes([code])*32;path.write_bytes(raw)
        for name,position in [('zero-position','0'),('unsorted','7000')]:
            path=folders[name]/'known.bim';rows=[row.split() for row in path.read_text().splitlines()];rows[0][3]=position;path.write_text(''.join(' '.join(row)+'\n' for row in rows))
        record['input_sha256']={name:{f.name:sha(f) for f in folder.iterdir()} for name,folder in folders.items()};save()
        for j,java in enumerate(args.java):
            for case in cases:
                label=f'jvm{j}-{case["case"]}';values=options(home,folders['base'],case['engine'],work/('输出-'+label));values.update({'window-unit':case['unit'],'window-size':case['window'],'step-size':case['step'],'pair-order':case['pair_order']})
                out=run(java,values,label);stats=verify_output(out,folders['base'],case,resources);record['workflows'].append(dict(label=label,java=java,engine=case['engine'],**stats));save()
            case=next(c for c in cases if c['case']=='plink2-count-legacy');label=f'jvm{j}-unicode';values=options(home,folders['unicode'],'plink2',work/('输出-'+label))
            out=run(java,values,label);stats=verify_output(out,folders['unicode'],case,resources,{'V6':'变异_六'});record['workflows'].append(dict(label=label,java=java,engine='plink2',**stats));save()
            controls=[('missing-home','base',{'home':str(work/'missing-home'),'bfile':'not-read'},3,False),('missing-call-error','base',{'missing-genotypes':'error'},2,False),
                ('small-founders','sparse',{},2,False),('nonvariable','nonvariable',{},2,False),('no-calls','no-calls',{},2,False),('zero-position','zero-position',{},2,False),('unsorted','unsorted',{},2,False),
                ('kb-step','base',{'window-unit':'kb','step-size':'5'},2,True),('bad-r2','base',{'r2':'1'},2,True),('unused-QC','base',{'hwe-mode':'off'},2,True),('bad-minimum','base',{'minimum-reference-calls':'2'},2,True)]
            for name,folder,changes,code,preflight in controls:
                label=f'jvm{j}-control-{name}';values=options(home,folders[folder],'plink2',work/('输出-'+label));values.update(changes);run(java,values,label,code,preflight)
            label=f'jvm{j}-control-plink1-backward';values=options(home,folders['base'],'plink1',work/('输出-'+label));values['pair-order']='backward';run(java,values,label,2,True)
            for name,files in record['input_sha256'].items():
                for filename,digest in files.items():assert sha(folders[name]/filename)==digest
            assert sha(jar)==record['jar_sha256'] and sha(__file__)==record['producer_sha256']
        assert install_plink.available(home)
        record.update(status='passed',actual_JVMs=len(args.java),completed_utc=dt.datetime.now(dt.timezone.utc).isoformat());save()
        print(json.dumps(dict(status='passed',workflows=len(record['workflows']),negative_controls=record['negative_controls'],independent_variant_call_rows=sum(w['independent_variant_call_rows'] for w in record['workflows']))));return 0
    except BaseException as failure:
        record.update(status='failed-retained',error=repr(failure));save()
        (work/'WARNINGS.md').write_text('# PACKAGED PRUNING VALIDATION FAILED\n\n'+repr(failure)+'\n\nRequired action: inspect verification.json,producer snapshot,recipes,stdout/stderr and original upstream reports. Identify the input,verifier,adapter or original-program cause;correct it and rerun to a new work directory. This failed attempt does not establish a working implementation.\n',encoding='utf-8');raise

if __name__=='__main__':raise SystemExit(main())
