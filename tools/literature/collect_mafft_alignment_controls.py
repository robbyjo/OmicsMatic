#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze complete existing MAFFT controls; no inference, installer or overwrite."""
from pathlib import Path,PurePosixPath
import argparse,hashlib,io,json,os,sys,zipfile
ROOT=Path(__file__).resolve().parents[2]
sha=lambda raw:hashlib.sha256(raw).hexdigest()

def collect(cache,output,report):
    if output.exists() or report.exists():raise ValueError('Preserve existing captures; use NEW artifact/report paths')
    original=cache/'original-controls-1';terminal=json.loads((original/'running.json').read_bytes())
    assert terminal==dict(status='terminal',protocols=60,all_owned_groups_terminal=True)
    rows=json.loads((original/'observed.json').read_bytes());assert len(rows)==60
    entries={};cases=[];versions=set();fits=refusals=probes=0
    def add(name,value):
        assert name not in entries and not PurePosixPath(name).is_absolute() and '..' not in PurePosixPath(name).parts
        entries[name]=value
    for row in rows:
        version,name=row['version'],row['name'];versions.add(version)
        assert row['owned_live_after_cleanup']==[]
        expected=1 if name=='AA-alphabet' else 0;assert row['exit_code']==expected
        directory=original/'linux'/version/name;actual={p.relative_to(directory).as_posix():p for p in directory.rglob('*') if p.is_file()}
        assert set(actual)==set(row['files'])
        prefix='original/'+version+'/'+name+'/'
        for relative,path in actual.items():
            assert not path.is_symlink();value=path.read_bytes();pin=row['files'][relative]
            assert len(value)==pin['bytes'] and sha(value)==pin['sha256'];add(prefix+relative,value)
        kind='NUCLEOTIDE' if name.startswith('DNA') else 'PROTEIN'
        symbols='ANY_PRINTABLE' if name=='AA-alphabet-any' else 'BIOLOGICAL'
        if name=='version':probes+=1
        elif expected:refusals+=1;assert b"Illegal character U" in entries[prefix+'process.stderr']
        else:fits+=1
        cases.append('\t'.join([version,name,str(expected),kind,symbols,prefix]))
    assert versions=={'7.313','7.475','7.487','7.490','7.505','7.526'} and (fits,refusals,probes)==(48,6,6)
    add('cases.tsv',('version\tname\texit\talphabet\tsymbols\tprefix\n'+'\n'.join(cases)+'\n').encode())
    for name in ['controller.py','observed.json','running.json']:add('capture/'+name,(original/name).read_bytes())
    for folder in ['download-portable-1','portable-native-setup-1']:
        add('capture/'+folder+'/observed.json',(cache/folder/'observed.json').read_bytes())
    add('capture/UPSTREAM-BSD-NOTICE.txt',(cache/'portable-native-setup-1/original-BSD-notice.txt').read_bytes())
    for name in ['tools/literature/collect_mafft_alignment_controls.py','tools/literature/install_iqtree.py','src/main/java/org/jlinalg/sequence/MafftSequences.java','src/main/java/org/jlinalg/sequence/MafftAddMap.java','src/test/java/org/jlinalg/sequence/MafftSequencesTest.java','src/test/java/org/jlinalg/sequence/MafftAddMapTest.java','src/test/java/org/jlinalg/cli/MafftOriginalReplayTest.java','LICENSE']:
        add('source/'+name,(ROOT/name).read_bytes())
    summary=dict(protocols=60,alignment_fits=48,alphabet_refusals=6,version_probes=6,exact_versions=sorted(versions),scientific_analysis_admitted=False,
        scope='Complete synthetic original-release controls, including raw source gaps/case/unknown-symbol behavior and keeplength deletion maps. These are private development inputs for native auditing, not a public MAFFT adapter, biological alignment benchmark or paper reconstruction. Original programs, official archives and native executables are not redistributed. The borrowed owned process controller is retained verbatim as provenance; it is not an IQ-TREE scientific analysis.')
    add('scope.json',(json.dumps(summary,indent=2)+'\n').encode())
    add('SHA256SUMS',''.join(sha(value)+'  '+name+'\n' for name,value in sorted(entries.items())).encode())
    output.parent.mkdir(parents=True,exist_ok=True);report.parent.mkdir(parents=True,exist_ok=True)
    partial=output.with_name(output.name+'.partial-'+str(os.getpid()))
    with zipfile.ZipFile(partial,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as bundle:
        for name,value in sorted(entries.items()):
            item=zipfile.ZipInfo(name,(1980,1,1,0,0,0));item.compress_type=zipfile.ZIP_DEFLATED;item.external_attr=0o100444<<16;bundle.writestr(item,value)
    with zipfile.ZipFile(partial) as bundle:
        assert set(bundle.namelist())==set(entries)
        for name,value in entries.items():assert bundle.read(name)==value,name
    os.link(partial,output);partial.unlink()
    result=dict(status='frozen-complete-original-MAFFT-alignment-controls',**summary,artifact_sha256=sha(output.read_bytes()),artifact_bytes=output.stat().st_size,members=len(entries),decoded_bytes=sum(map(len,entries.values())))
    with report.open('x',encoding='utf-8') as target:target.write(json.dumps(result,indent=2)+'\n')
    print(json.dumps({key:value for key,value in result.items() if key!='scope'}))

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--cache',type=Path,default=ROOT/'build/literature-review/mafft-runtime');p.add_argument('--output',type=Path,required=True);p.add_argument('--report',type=Path,required=True);a=p.parse_args();collect(a.cache,a.output,a.report)
if __name__=='__main__':
    try:main()
    except Exception as failure:
        print('MAFFT CAPTURE FAIL2: '+str(failure)+'\nRequired action: preserve all captures and partial files, correct the named cause and use NEW artifact/report paths. No analysis is admitted by this collector.',file=sys.stderr);sys.exit(2)
