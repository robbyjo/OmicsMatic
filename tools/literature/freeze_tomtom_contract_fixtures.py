# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Retain original complete Tomtom comparison contracts, original failures and independent options."""
import argparse
import hashlib
import json
from pathlib import Path
import stat
import xml.etree.ElementTree as ET
import zipfile

ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'build/literature-review/meme-runtime'


def sha(raw):return hashlib.sha256(raw).hexdigest()


def background(path):
    text=path.read_text(encoding='ascii');lines=text.splitlines()
    if path.suffix=='.meme':
        index=lines.index('Background letter frequencies');tokens=lines[index+1].split()
    else:tokens=text.split()
    assert tokens[::2]==list('ACGT');values=[float(v) for v in tokens[1::2]];assert abs(sum(values)-1)<1e-12;return values


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args();out=args.out.resolve()
    assert out==ROOT/'src/test/resources/org/jlinalg/sequence/tomtom-contracts' and not out.exists()
    out.mkdir(parents=True);entries={};cases=[]
    for label,area in [('base',BASE/'original-controls-2'),('edges',BASE/'tomtom-edge-controls-2'),('single',BASE/'tomtom-edge-controls-3')]:
        proof=json.loads((area/'COMPLETED.json').read_bytes());capture=area/'captured'
        native=json.loads((area/'NATIVE-INVENTORY.json').read_bytes())
        for name,row in native.items():
            if row['kind']=='file':raw=(capture/name).read_bytes();assert len(raw)==row['bytes'] and sha(raw)==row['sha256']
        selected=[j for j in proof['jobs'] if j['name'].startswith('tomtom-') or label!='base']
        for path in sorted((capture/'inputs').rglob('*')):
            if path.is_file():entries[label+'/inputs/'+path.name]=path.read_bytes()
        for job in selected:
            name=job['name'];command=job['command'];text=job.get('text_mode',False)
            targets=job.get('target_inputs',['motifs.meme']);query=job.get('query_input','motifs.meme')
            qsource=command[-len(targets)-1];tsources=command[-len(targets):]
            assert Path(qsource).name==query and [Path(p).name for p in tsources]==targets
            def option(key,default):return command[command.index(key)+1] if key in command else default
            rawbg=background(capture/'inputs'/(Path(option('-bfile','')).name if '-bfile' in command else query))
            expectation=dict(command_line=' '.join(['tomtom',*command[1:]]),distance=option('-dist','ed'),pseudo=float(option('-motif-pseudo','0.1')),
                reverse='-norc' not in command,threshold_kind='evalue' if '-evalue' in command else 'qvalue',threshold=float(option('-thresh','0.5')),
                internal='-internal' in command,min_overlap=int(option('-min-overlap','1')),background_source=option('-bfile','--query--'),raw_background=rawbg,
                background_from_query='-bfile' not in command,query_source=qsource,target_sources=tsources,query_input=label+'/inputs/'+query,target_inputs=[label+'/inputs/'+t for t in targets])
            kind='complete-original-contract'
            if text:kind='original-text-contract-separate-audit-pending'
            if job['exit_code']!=0:kind='original-failure-no-result'
            if name=='mixed-zero-allr':kind='original-success-exclusion-ledger-required'
            xml=capture/name/'result/tomtom.xml';rows=None
            if job['exit_code']==0 and not text:
                tree=ET.parse(xml).getroot();rows=sum(len(q) for q in tree.find('matches'))
            cases.append(dict(id=label+'/'+name,kind=kind,exit_code=job['exit_code'],text_mode=text,expected=expectation,complete_reported_rows=rows,
                              source_control_sha256=sha((area/'COMPLETED.json').read_bytes())))
            for path in sorted((capture/name).rglob('*')):
                if path.is_file() and path.suffix in {'.xml','.tsv','.log','.json'}:
                    entries[label+'/'+name+'/'+path.relative_to(capture/name).as_posix()]=path.read_bytes()
        entries[label+'/COMPLETED.json']=(area/'COMPLETED.json').read_bytes()
    assert len(cases)==34
    ordinary=[c for c in cases if c['kind']=='complete-original-contract'];assert len(ordinary)==30
    expected=dict(schema_version=1,profile='original-Linux-x86_64-Tomtom-5.5.9',cases=cases,complete_contracts=len(ordinary),complete_reported_rows=sum(c['complete_reported_rows'] for c in ordinary),
                  interpretation='Expected options/backgrounds/source paths are extracted from retained original commands and complete independent input files. XML/table content supplies only observed output populations/counts. HTML assets remain in complete original families and are not certified by this selected component fixture. Original failures, text-only mode and source-exclusion cases remain separate, not converted to passing complete contracts.',
                  generator_sha256=sha(Path(__file__).read_bytes()))
    entries['EXPECTED.json']=(json.dumps(expected,indent=2)+'\n').encode()
    entries['UPSTREAM-COPYING']=(ROOT/'build/literature-review/fimo-runtime/original-source/meme-5.5.9/COPYING').read_bytes()
    target=out/'original-comparison-contracts.zip';census={}
    with zipfile.ZipFile(target,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for name,raw in sorted(entries.items()):
            info=zipfile.ZipInfo(name,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=(stat.S_IFREG|0o644)<<16;z.writestr(info,raw);census[name]=dict(bytes=len(raw),sha256=sha(raw))
    with zipfile.ZipFile(target) as z:
        assert z.testzip() is None and len(z.infolist())==len(set(z.namelist()))==len(census)
        for name,value in census.items():raw=z.read(name);assert dict(bytes=len(raw),sha256=sha(raw))==value
    manifest=dict(status='frozen-original-Tomtom-comparison-contracts-native-audit-pending',archive=dict(path=target.name,bytes=target.stat().st_size,sha256=sha(target.read_bytes())),files=census,expected=expected)
    (out/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8',newline='\n');(out/'.gitattributes').write_text('* -text\n',encoding='ascii',newline='\n')
    print(json.dumps(dict(status=manifest['status'],archive=manifest['archive'],cases=len(cases),complete_contracts=len(ordinary),complete_reported_rows=expected['complete_reported_rows'])))


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete original family identity checks')
    main()
