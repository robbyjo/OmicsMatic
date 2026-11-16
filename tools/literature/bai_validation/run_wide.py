#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Actual original indexes and marked/removed sidecars, plus native Java alternatives."""
from pathlib import Path
import argparse,concurrent.futures,hashlib,json,os,shutil,subprocess,time,zipfile

ROOT=Path(__file__).resolve().parents[3]
HASHES={'0.6.6':'8b06691f80bc89c733267c523dbbb3a4c36b51cae42ef7b2a171e17c68516dc2','0.8.2':'bbccbd914ebf8c2b09a5bb6a922dba99ac24c09d25ddd4925f5b2cc8dc359543'}
def pin(path):
    raw=path.read_bytes();return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def save(path,value):
    with path.open('x',encoding='utf-8',newline='\n') as f:json.dump(value,f,indent=2);f.write('\n')
def properties(path):return dict(line.split('=',1) for line in path.read_text().splitlines() if line and not line.startswith('#') and '=' in line)
def main():
    parser=argparse.ArgumentParser(description=__doc__)
    for name in ['jar','fixtures','binary066','binary082','java_home','out']:parser.add_argument('--'+name.replace('_','-'),type=Path,required=True)
    parser.add_argument('--wsl',required=True);parser.add_argument('--cases',nargs='+');a=parser.parse_args()
    out=a.out.resolve();out.mkdir(parents=True,exist_ok=False);fixtures=a.fixtures.resolve(strict=True)
    identities={p.relative_to(fixtures).as_posix():pin(p) for p in fixtures.rglob('*') if p.is_file()}
    save(out/'INPUT-PINS.json',identities)
    jar=out/'candidate.jar';candidate=pin(a.jar);shutil.copy2(a.jar,jar);assert pin(jar)==candidate==pin(a.jar)
    sources={p.relative_to(ROOT).as_posix():pin(p) for p in Path(__file__).parent.glob('*') if p.is_file()}
    for name in ['src/main/java/org/jlinalg/alignment/BamBaiAudit.java','src/main/java/org/jlinalg/alignment/BamIndexStream.java','src/main/java/org/jlinalg/alignment/SambambaStreamingBamAudit.java']:sources[name]=pin(ROOT/name)
    save(out/'SOURCE-PINS.json',sources)
    with zipfile.ZipFile(out/'own-source-snapshot.zip','x',compression=zipfile.ZIP_DEFLATED) as z:
        for name in sources:z.write(ROOT/name,name)
        z.write(ROOT/'LICENSE','LICENSE-project.txt')
    checks=[]
    def run(where,label,command,expected=0):
        command=list(map(str,command));start=time.monotonic()
        with (where/(label+'.stdout')).open('xb') as so,(where/(label+'.stderr')).open('xb') as se:
            process=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=so,stderr=se,timeout=180)
        row=dict(label=label,command=command,exit_code=process.returncode,expected_exit_code=expected,seconds=round(time.monotonic()-start,2));save(where/(label+'.process.json'),row);checks.append(row)
        if process.returncode!=expected:raise RuntimeError(str(where)+' '+label+' unexpected exit '+str(process.returncode)+'; inspect retained consoles')
        return row
    classes=out/'probe-classes';classes.mkdir()
    run(out,'compile-probe',[a.java_home/'bin/javac.exe','--release','17','-cp',jar,'-d',classes,Path(__file__).with_name('BaiQueryProbe.java')])
    java=[a.java_home/'bin/java.exe','-Xmx128m','-cp',str(jar)+os.pathsep+str(classes),'BaiQueryProbe']
    def linux(path):
        path=Path(path).resolve();return '/mnt/'+path.as_posix()[0].lower()+path.as_posix()[2:]
    def original(binary):return ['wsl.exe','-d',a.wsl,'--','/usr/bin/timeout','--signal=TERM','--kill-after=5','120','/usr/bin/setpriv','--pdeathsig','KILL','/usr/bin/prlimit','--as='+str(2<<30),'--',linux(binary)]
    binaries={'0.6.6':a.binary066.resolve(strict=True),'0.8.2':a.binary082.resolve(strict=True)}
    for edition,binary in binaries.items():assert pin(binary)['sha256']==HASHES[edition]
    results=[];failures=[]
    def query_and_audit(where,bam,bai,query_path,expected_bad=False):
        run(where,'audit',[*java,'audit',bam,bai,where/'AUDIT.properties'],2 if expected_bad else 0)
        report=properties(where/'AUDIT.properties')
        if expected_bad:assert report['status']=='FAIL2_NO_ACCEPTED_BAI_AUDIT' and 'metadata reference extent' in report['error']
        else:assert report['status']=='PASS_NATIVE_COMPLETE_BAM_BAI_AUDIT'
        run(where,'indexed-queries',[*java,'query',bam,bai,query_path,where/'queries-observed.tsv',where/'QUERIES.properties'])
        return dict(audit=report,queries=properties(where/'QUERIES.properties'),bam=pin(bam),bai=pin(bai))
    def one(case):
        directory=out/case.name;directory.mkdir();bam=case/'input.bam';query_path=case/'queries.tsv';manifest=json.loads((case/'CASE.json').read_bytes());rows=[]
        native=directory/'native-input';native.mkdir();bai=native/'alignment.bai'
        # Exercise the project's existing command, not a duplicate indexing implementation.
        run(directory,'native-cli-index-input',[a.java_home/'bin/java.exe','-Xmx128m','-jar',jar,'hts-tools','--engine','native','--method','sam-index','--input',bam,'--out',native/'cli-output','--no-config'])
        bai=native/'cli-output/alignment.bai';rows.append(dict(producer='native-java-input',result=query_and_audit(native,bam,bai,query_path)))
        run(native,'offsets',[*java,'offsets',bam,native/'offsets.tsv'])
        for edition,binary in binaries.items():
            version=directory/edition;version.mkdir()
            direct=version/'direct-index';direct.mkdir();bai=direct/'original.bai'
            run(direct,'original-index',[*original(binary),'index','-t','2',linux(bam),linux(bai)])
            known=case.name=='wide_unplaced_tail' and edition=='0.6.6'
            rows.append(dict(producer=edition+'-direct-index',expected_metadata_refusal=known,result=query_and_audit(direct,bam,bai,query_path,known)))
            for action in ['mark','remove']:
                where=version/action;where.mkdir();tmp=where/'tmp';tmp.mkdir();target=where/'alignment.bam'
                command=[*original(binary),'markdup','-t','2','-l','6','--hash-table-size=1024','--overflow-list-size=64','--sort-buffer-size=16','--io-buffer-size=1','--tmpdir='+linux(tmp)]
                if action=='remove':command.append('--remove-duplicates')
                score_overflow=case.name=='cross_bgzf_members' and edition=='0.6.6'
                original_process=run(where,'original-'+action,[*command,linux(bam),linux(target)],1 if score_overflow else 0)
                if score_overflow:
                    assert 'Conversion positive overflow' in (where/('original-'+action+'.stderr')).read_text()
                    assert any(record['length']*30>65535 for record in manifest['records'] if not record['flag']&4)
                    refusal=dict(producer=edition+'-'+action,status='FAIL2_ORIGINAL_066_SCORE_OVERFLOW_NO_ACCEPTED_ANALYSIS',process=original_process,
                        action='Retain this source failure. No marked/removed BAM or native index of such a BAM is accepted. Use a separately qualified permitted tool or edition that supports the original input; do not trim reads or alter qualities to make the test pass.')
                    save(where/'FAILURE.json',refusal);rows.append(refusal);continue
                original_bai=Path(str(target)+'.bai')
                rows.append(dict(producer=edition+'-'+action,expected_metadata_refusal=known,result=query_and_audit(where,target,original_bai,query_path,known)))
                fresh=where/'native-index';fresh.mkdir();index=fresh/'fresh.bai'
                run(fresh,'native-library-index',[*java,'index',target,index,fresh/'CREATED.properties'])
                rows.append(dict(producer='native-java-after-'+edition+'-'+action,result=query_and_audit(fresh,target,index,query_path)))
        row=dict(case=case.name,manifest=manifest,results=rows);save(directory/'CASE-RESULTS.json',row);print(json.dumps(dict(case=case.name,assessed_index_pairs=len(rows),status='PASS_EXPECTED_AUDITS_AND_INDEPENDENT_QUERIES')),flush=True);return row
    cases=sorted(p for p in fixtures.iterdir() if p.is_dir());assert len(cases)==5
    if a.cases:
        assert len(a.cases)==len(set(a.cases)) and set(a.cases).issubset({p.name for p in cases})
        cases=[case for case in cases if case.name in a.cases]
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        futures={pool.submit(one,case):case.name for case in cases}
        for future in concurrent.futures.as_completed(futures):
            try:results.append(future.result())
            except Exception as failure:
                row=dict(case=futures[future],error=repr(failure));failures.append(row);print(json.dumps(row),flush=True)
    save(out/'RESULTS.json',dict(results=results,failures=failures,checks=checks))
    assert not failures and len(results)==len(cases),'Requested wide cases incomplete; retain all failed attempts'
    assert identities=={p.relative_to(fixtures).as_posix():pin(p) for p in fixtures.rglob('*') if p.is_file()},'Independent input changed'
    assert candidate==pin(jar)
    for name,identity in sources.items():assert pin(ROOT/name)==identity,'Source changed during validation'
    for edition,binary in binaries.items():assert pin(binary)['sha256']==HASHES[edition]
    all_rows=[row for case in results for row in case['results']];rows=[r for r in all_rows if 'result' in r]
    source_refusals=[r for r in all_rows if 'result' not in r];rejections=sum(r['result']['audit']['status'].startswith('FAIL2') for r in rows)
    query_count=sum(int(r['result']['queries']['queries']) for r in rows)
    save(out/'COMPLETED.json',dict(status='PASS_REQUESTED_WIDE_CASE_CONTROLS_WITH_EXPLICIT_ORIGINAL_REFUSALS',candidate=candidate,cases=[case.name for case in cases],index_pairs=len(rows),expected_original_metadata_refusals=rejections,original_score_overflow_refusals=source_refusals,independent_mapped_queries=query_count,checks=checks,new_catalog_credit=0,whole_goal_complete=False))
    print(json.dumps(dict(status='PASS_REQUESTED_WIDE_CASE_CONTROLS_WITH_EXPLICIT_ORIGINAL_REFUSALS',cases=[case.name for case in cases],index_pairs=len(rows),expected_original_metadata_refusals=rejections,source_overflow_refusals=len(source_refusals),queries=query_count)),flush=True)
if __name__=='__main__':
    try:main()
    except Exception as failure:print('FAIL2_WIDE_INDEX_CONTROL_INCOMPLETE',repr(failure),flush=True);raise SystemExit(2)
