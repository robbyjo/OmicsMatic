# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze complete selected original XML/table/text contracts, not whole-process admission."""
import argparse
import hashlib
import json
from pathlib import Path
import stat
import xml.etree.ElementTree as ET
import zipfile

ROOT=Path(__file__).resolve().parents[2]


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--control',type=Path,required=True)
    args=parser.parse_args();control=args.control.resolve();proof=json.loads((control/'COMPLETED.json').read_bytes())
    assert proof['families']==proof['successful_processes']==16 and not proof['implementation_credit']
    native=json.loads((control/'NATIVE-INVENTORY.json').read_bytes());captured=control/'captured'
    for name,row in native.items():
        path=captured/name
        if row['kind']=='file':
            raw=path.read_bytes();assert len(raw)==row['bytes'] and sha(raw)==row['sha256']
    out=ROOT/'src/test/resources/org/jlinalg/sequence/meme-suite'
    assert not out.exists();out.mkdir(parents=True)
    selected={};omitted={};xml=[]
    for path in sorted(captured.rglob('*')):
        if not path.is_file():continue
        name=path.relative_to(captured).as_posix();raw=path.read_bytes()
        if path.suffix not in {'.xml','.tsv','.txt','.json','.log','.meme','.fa'}:
            omitted[name]=dict(bytes=len(raw),sha256=sha(raw));continue
        selected[name]=raw
        if path.suffix=='.xml':
            tree=ET.fromstring(raw)
            xml.append(dict(path=name,root=tree.tag,version=tree.attrib['version'],elements=sum(1 for node in tree.iter()),bytes=len(raw),sha256=sha(raw),
                            motifs=len(tree.findall('./motifs/motif')),queries=len(tree.findall('./queries/motif')),targets=len(tree.findall('./targets/motif'))))
    assert len(xml)==14 and len({p['root'] for p in xml})==3
    expected=dict(schema_version=1,profile='original-linux-x86_64-meme-suite-5.5.9',xml_documents=xml,
        families=16,process_exits_zero=16,selected_files=len(selected),omitted_report_assets=omitted,
        omitted_scope='HTML/PNG/EPS report assets remain retained in complete original development families. This fixture freezes complete selected XML/TSV/text contracts and full inputs/logs only. It does not certify an original runtime, numerical fit or complete report family.',
        source_original_controls=sha((control/'COMPLETED.json').read_bytes()),generator_sha256=sha(Path(__file__).read_bytes()))
    selected['EXPECTED.json']=(json.dumps(expected,indent=2)+'\n').encode()
    license_raw=(ROOT/'build/literature-review/fimo-runtime/original-source/meme-5.5.9/COPYING').read_bytes()
    selected['UPSTREAM-COPYING']=license_raw
    target=out/'original-format-contracts.zip';census={}
    with zipfile.ZipFile(target,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for name,raw in sorted(selected.items()):
            info=zipfile.ZipInfo(name,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=(stat.S_IFREG|0o644)<<16
            z.writestr(info,raw);census[name]=dict(bytes=len(raw),sha256=sha(raw))
    with zipfile.ZipFile(target) as z:
        assert z.testzip() is None and len(z.infolist())==len(set(z.namelist()))==len(census)
        for name,row in census.items():
            raw=z.read(name);assert dict(bytes=len(raw),sha256=sha(raw))==row
    fixture=dict(status='frozen-original-MEME-suite-format-contracts-native-audit-pending',archive=dict(path=target.name,bytes=target.stat().st_size,sha256=sha(target.read_bytes())),files=census,expected=expected)
    (out/'manifest.json').write_text(json.dumps(fixture,indent=2)+'\n',encoding='utf-8',newline='\n')
    (out/'.gitattributes').write_text('* -text\n',encoding='ascii',newline='\n')
    (out/'README.txt').write_text('Original MEME Suite5.5.9 generated XML/table/text contracts on reused controlled DNA populations. Native Java parsing is independent of the upstream executable. Preserve literal decimals and declared source roles. These selected contracts do not admit a scientific process or replace full-family checks. See EXPECTED.json for omitted HTML/PNG/EPS asset pins; complete families remain in the ignored development cache. Upstream terms are retained in UPSTREAM-COPYING; educational/research/nonprofit use was directly confirmed by the user. Java parser code remains GPL-3.0-or-later.\n',encoding='utf-8',newline='\n')
    print(json.dumps(dict(status=fixture['status'],archive=fixture['archive'],xml_documents=len(xml),selected_files=len(census))))


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original-family identity checks')
    main()
