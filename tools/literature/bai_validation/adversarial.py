#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Retained malformed indexes, genuine lost-query control and legal offset equivalences."""
from pathlib import Path
import argparse,hashlib,json,os,shutil,struct,subprocess,time,zipfile
ROOT=Path(__file__).resolve().parents[3]
def pin(path):
    raw=path.read_bytes();return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def save(path,value):
    with path.open('x',encoding='utf-8',newline='\n') as f:json.dump(value,f,indent=2);f.write('\n')
def properties(path):return dict(line.split('=',1) for line in path.read_text().splitlines() if line and not line.startswith('#') and '=' in line)
def parse(raw):
    assert raw[:4]==b'BAI\1';at=8;references=[];offset_fields=[]
    for _ in range(struct.unpack_from('<I',raw,4)[0]):
        count_at=at;count=struct.unpack_from('<I',raw,at)[0];at+=4;bins={}
        for _ in range(count):
            begin=at;identifier,n=struct.unpack_from('<II',raw,at);at+=8
            bins[identifier]=dict(begin=begin,count=n,fields=[at+16*i for i in range(n)],end=at+16*n)
            if identifier==37450:offset_fields.extend([at,at+8])
            else:
                for i in range(n):offset_fields.extend([at+16*i,at+16*i+8])
            at+=16*n
        n=struct.unpack_from('<I',raw,at)[0];at+=4;linear=at
        offset_fields.extend(at+8*i for i in range(n));at+=8*n
        references.append(dict(count_at=count_at,count=count,bins=bins,linear=linear,linear_count=n))
    assert at+8==len(raw)
    return references,offset_fields
def main():
    p=argparse.ArgumentParser(description=__doc__)
    for name in ['jar','fixtures','wide_results','java_home','out']:p.add_argument('--'+name.replace('_','-'),type=Path,required=True)
    a=p.parse_args();out=a.out.resolve();out.mkdir(parents=True,exist_ok=False)
    fixture=a.fixtures.resolve(strict=True)/'wide_no_tail';source=a.wide_results.resolve(strict=True)/'wide_no_tail/native-input'
    bam=fixture/'input.bam';bai=source/'cli-output/alignment.bai';queries=fixture/'queries.tsv';offsets=source/'offsets.tsv';originals={str(path):pin(path) for path in [bam,bai,queries,offsets]}
    save(out/'INPUT-PINS.json',originals)
    sources={p.relative_to(ROOT).as_posix():pin(p) for p in Path(__file__).parent.iterdir() if p.is_file()};save(out/'SOURCE-PINS.json',sources)
    with zipfile.ZipFile(out/'own-source-snapshot.zip','x',compression=zipfile.ZIP_DEFLATED) as z:
        for name in sources:z.write(ROOT/name,name)
        z.write(ROOT/'LICENSE','LICENSE-project.txt')
    candidate=pin(a.jar);jar=out/'candidate.jar';shutil.copy2(a.jar,jar);assert pin(jar)==candidate==pin(a.jar)
    checks=[]
    def run(where,label,command,expected):
        command=list(map(str,command));start=time.monotonic()
        with (where/(label+'.stdout')).open('xb') as so,(where/(label+'.stderr')).open('xb') as se:
            process=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=so,stderr=se,timeout=120)
        row=dict(label=label,command=command,exit_code=process.returncode,expected_exit_code=expected,seconds=round(time.monotonic()-start,2));save(where/(label+'.process.json'),row);checks.append(row)
        if process.returncode!=expected:raise RuntimeError(str(where)+' '+label+' differed; inspect raw consoles')
    classes=out/'probe-classes';classes.mkdir();run(out,'compile-probe',[a.java_home/'bin/javac.exe','--release','17','-cp',jar,'-d',classes,Path(__file__).with_name('BaiQueryProbe.java')],0)
    java=[a.java_home/'bin/java.exe','-Xmx128m','-cp',str(jar)+os.pathsep+str(classes),'BaiQueryProbe']
    raw=bai.read_bytes();refs,offset_fields=parse(raw);first=refs[1];second=refs[3];native_offsets={line.split('\t')[0]:int(line.split('\t')[5]) for line in offsets.read_text().splitlines()[1:]}
    controls=[]
    def add(name,data,audit_exit,query_exit=None,query=None,why=''):
        controls.append(dict(name=name,raw=bytes(data),audit_exit=audit_exit,query_exit=query_exit,query=query,why=why))
    late=bytearray(raw);struct.pack_into('<Q',late,first['linear']+8*20,native_offsets['after_long_gap'])
    add('linear_empty_gap_skips_long_alignment',late,2,2,'0\tchrA\t327681\t700001\tlong_gap\n','An earlier empty tile points past a long alignment. Its own tiles remain unchanged; a real regional query must lose it.')
    wrong_bin=bytearray(raw);struct.pack_into('<I',wrong_bin,first['bins'][4681]['begin'],4682)
    add('wrong_hierarchical_bin',wrong_bin,2,2,'0\tchrA\t1\t1\tfirst_reference_start\n')
    interior=bytearray(raw);chunk=first['bins'][4681]['fields'][0];struct.pack_into('<Q',interior,chunk,native_offsets['first_reference_start']+1)
    add('chunk_starts_inside_record',interior,2)
    crossing=bytearray(raw);chunk=second['bins'][4681]['fields'][0];struct.pack_into('<Q',crossing,chunk,native_offsets['first_reference_start'])
    add('chunk_extends_into_previous_reference',crossing,2)
    nonexistent=bytearray(raw);struct.pack_into('<Q',nonexistent,second['bins'][4681]['fields'][0]+8,1<<16)
    add('nonexistent_bgzf_block_address',nonexistent,2)
    counts=bytearray(raw);struct.pack_into('<Q',counts,first['bins'][37450]['fields'][1],99)
    add('false_mapped_count',counts,2)
    counts=bytearray(raw);struct.pack_into('<Q',counts,len(counts)-8,1)
    add('false_global_unplaced_count',counts,2)
    blocks=json.loads((fixture/'CASE.json').read_bytes())['blocks'];last=blocks[-2];terminal=blocks[-1];logical_end=terminal['compressed']<<16
    assert last['decoded_bytes']<65536 and last['compressed']+last['compressed_bytes']==terminal['compressed']
    for name,value in [('legal_short_member_end_alias',last['compressed']<<16|last['decoded_bytes']),('legal_physical_eof_end_alias',bam.stat().st_size<<16)]:
        data=bytearray(raw);changed=0
        for at in offset_fields:
            if struct.unpack_from('<Q',data,at)[0]==logical_end:struct.pack_into('<Q',data,at,value);changed+=1
        assert changed>0;add(name,data,0,0,why='Equivalent logical end offset; original index bytes remain untouched.')
    add('optional_global_count_absent',raw[:-8],0,0)
    meta=first['bins'][37450];data=bytearray(raw[:meta['begin']]+raw[meta['end']:]);struct.pack_into('<I',data,first['count_at'],first['count']-1)
    add('optional_reference_metadata_absent',data,0,0)
    add('unexpected_trailing_byte',raw+b'\0',2)
    add('truncated_optional_uint64',raw[:-1],2)
    results=[]
    for control in controls:
        where=out/control['name'];where.mkdir();target=where/'controlled.bai';target.write_bytes(control.pop('raw'));query=control.pop('query');exit_code=control.pop('audit_exit');query_exit=control.pop('query_exit')
        run(where,'native-audit',[*java,'audit',bam,target,where/'AUDIT.properties'],exit_code)
        report=properties(where/'AUDIT.properties')
        if exit_code==0:assert report['status']=='PASS_NATIVE_COMPLETE_BAM_BAI_AUDIT'
        else:assert report['status']=='FAIL2_NO_ACCEPTED_BAI_AUDIT'
        if control['name']=='linear_empty_gap_skips_long_alignment':assert 'linear index can skip' in report['error']
        if control['name']=='nonexistent_bgzf_block_address':assert 'actual BGZF block' in report['error']
        query_report=None
        if query_exit is not None:
            if query is not None:
                query_path=where/'independent-query.tsv';query_path.write_bytes(query.encode())
            else:query_path=queries
            run(where,'actual-indexed-query',[*java,'query',bam,target,query_path,where/'queries-observed.tsv',where/'QUERIES.properties'],query_exit);query_report=properties(where/'QUERIES.properties')
            if query_exit:assert query_report['status']=='FAIL2_INDEXED_QUERY_EXPECTATIONS_DIFFER'
            else:assert query_report['status']=='PASS_INDEPENDENT_GEOMETRIC_MAPPED_QUERY_EXPECTATIONS'
        row=dict(**control,index=pin(target),audit=report,indexed_queries=query_report,incorrect_controls_are_not_accepted_analyses=exit_code!=0);results.append(row);print(json.dumps(dict(control=control['name'],expected_audit_exit=exit_code,actual_query_exit=query_exit,status='PASS_CONTROL_OBSERVED')),flush=True)
    for name,identity in originals.items():assert pin(Path(name))==identity
    for name,identity in sources.items():assert pin(ROOT/name)==identity
    assert candidate==pin(jar)
    save(out/'COMPLETED.json',dict(status='PASS_NATIVE_BAI_CORRUPTION_AND_ACTUAL_QUERY_CONTROLS',candidate=candidate,results=results,checks=checks,new_catalog_credit=0,whole_goal_complete=False))
if __name__=='__main__':
    try:main()
    except Exception as failure:print('FAIL2_INDEX_ADVERSARIAL_CONTROL_INCOMPLETE',repr(failure),flush=True);raise SystemExit(2)
