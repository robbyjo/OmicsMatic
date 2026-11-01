#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze original joins only after independent physical-wire and region censuses."""
from pathlib import Path
from collections import Counter
import argparse,base64,gzip,hashlib,json,re,struct,zipfile

ROOT=Path(__file__).resolve().parents[3]
PEER_ROOT='/mnt/d/projects/JLinAlg/'
sha=lambda b:hashlib.sha256(b).hexdigest()
def local(peer):
    assert peer.startswith(PEER_ROOT),peer
    relative=peer.removeprefix(PEER_ROOT)
    assert '..' not in Path(relative).parts
    return ROOT/relative
def records(raw):
    if raw[:2]==b'\x1f\x8b':raw=gzip.decompress(raw)
    assert raw[:5]==b'BCF\2\2'
    header=struct.unpack_from('<I',raw,5)[0];text=raw[9:9+header-1].decode();offset=9+header;contigs={}
    for line in text.splitlines():
        if not line.startswith('##contig='):continue
        name=re.search(r'<ID=([^,>]+)',line).group(1);index=re.search(r'(?:,|<)IDX=(\d+)',line)
        contigs[int(index.group(1)) if index else len(contigs)]=name
    result=[]
    while offset<len(raw):
        shared,individual,rid,position,length=struct.unpack_from('<IIiii',raw,offset);end=offset+8+shared+individual
        assert shared>=24 and position>=0 and length>=1 and end<=len(raw)
        result.append((sha(raw[offset:end]),contigs[rid],position,length));offset=end
    assert offset==len(raw)
    return result
def intervals(lines,group):
    q=next(row for row in lines if row[:2]==['Q',group]);spans=[(base64.b64decode(row[2]).decode(),int(row[3]),int(row[4])) for row in lines if row[:2]==['I',group]]
    def includes(contig,start,end):
        matched=q[2]=='1' or any(c==contig and begin<=end and finish>=start for c,begin,finish in spans)
        return not matched if q[3]=='1' else matched
    return includes
def collect(report,output):
    observed=json.loads((report/'observed.json').read_bytes())
    assert observed['status']=='captured-original-joined-wire-v2-controls'
    assert observed['source_commit']=='c34d4db3e99a2f7e23deb727671ae260901a5886'
    members={};cases=[];events=0
    def add(name,raw):
        assert not name.startswith('/') and '..' not in Path(name).parts
        if name in members:assert members[name]==raw,name
        else:members[name]=raw
    for item in observed['cases']:
        raw=(report/item['result']).read_bytes();assert sha(raw)==item['result_sha256'];rows=[line.split('\t') for line in raw.decode().splitlines()]
        assert rows[0]==['V','OMICS-SHAPEIT-JOIN-WIRE-2',str(len(item['inputs']))]
        assert rows[1]==['U',base64.b64encode(item['region'].encode()).decode()]
        bindings=[];before={};after={}
        for row in rows:
            if row[0]!='F':continue
            peer=base64.b64decode(row[4],validate=True).decode();source=local(peer);content=source.read_bytes()
            assert (sha(content),len(content))==(row[5],int(row[6]))
            key=(int(row[2]),row[3],peer)
            target=before if row[1]=='B' else after;assert key not in target;target[key]=(row[5],int(row[6]))
            if row[1]=='B':
                name='inputs/'+sha(content)[:16]+'-'+source.name;add(name,content)
                bindings.append(dict(panel=key[0],kind=key[1],peer_path=peer,member=name,sha256=sha(content),bytes=len(content)))
        if item['complete']:
            assert item['exit_code']==0 and rows[-1]==['Z'] and before==after
            region=intervals(rows,'R');target=intervals(rows,'T');expected=[];seen=[];physical=[]
            for i,peer in enumerate(item['inputs']):
                source=local(peer).read_bytes();assert sha(source)==item['input_sha256'][i]
                recs=records(source);physical.append({r[0]:r for r in recs})
                expected.append(Counter(r[0] for r in recs if region(r[1],r[2],r[2]+r[3]-1) and target(r[1],r[2],r[2])));seen.append(Counter())
            count=0
            for row in rows:
                if row[0]!='J':continue
                assert int(row[1])==count and len(row)==4+5*len(seen)
                for i in range(len(seen)):
                    digest=row[4+5*i]
                    if digest=='-':continue
                    rec=physical[i][digest];assert (rec[1],rec[2]+1)==(base64.b64decode(row[2]).decode(),int(row[3]))
                    seen[i][digest]+=1
                count+=1
            assert seen==expected,(item['name'],seen,expected)
            end=next(row for row in rows if row[0]=='E');assert list(map(int,end[1:]))==[count,*[sum(c.values()) for c in seen]]
            events+=count
        else:
            assert item['exit_code']==1 and not any(row[0] in ['E','Z'] for row in rows)
            assert 'AN field is needed' in (report/(item['name']+'.stdout')).read_text()
        add('capture/'+item['result'],raw)
        for suffix in ['stdout','stderr']:add('capture/'+item['name']+'.'+suffix,(report/(item['name']+'.'+suffix)).read_bytes())
        cases.append(dict(name=item['name'],region=item['region'],roles=item['roles'],complete=item['complete'],exit_code=item['exit_code'],protocol_member='capture/'+item['result'],protocol_sha256=sha(raw),bindings=bindings))
    for name in ['JoinedWireProtocol.cpp','MakeJoinedWireDictionaryFixture.cpp','collect_joined_wire_evidence.py']:add('protocol/'+name,(Path(__file__).parent/name).read_bytes())
    assert sha(members['protocol/JoinedWireProtocol.cpp'])==observed['source_protocol_sha256']
    assert sha(members['protocol/MakeJoinedWireDictionaryFixture.cpp'])==observed['dictionary_generator_sha256']
    source=(ROOT/'src/test/resources/org/jlinalg/genetics/shapeit-reference/initial-reader.zip').read_bytes()
    assert sha(source)==observed['original_source_closure_sha256']=='7ce0722fbe1499ed72d5a46d81c9945d1141b24a7abbec07e5e1d3ce799e8f02'
    add('source/original-reader-source-closure.zip',source)
    for name in ['observed.json','runs.json']:add('capture/'+name,(report/name).read_bytes())
    for name in ['compile','compile-dictionary-generator','reverse-dictionary-fixture','index-reverse-dictionary']:
        for suffix in ['stdout','stderr']:add('capture/'+name+'.'+suffix,(report/(name+'.'+suffix)).read_bytes())
    add('cases.json',(json.dumps(cases,indent=2)+'\n').encode())
    add('README.txt',b'Original XCF/HTSlib join-wire v2 controls. Raw physical BCF hashes and complete canonical-region occurrence censuses checked independently before freezing. Includes duplicate identities, merged intervals, empty regions, reversed contig dictionaries, XCF header companions and twelve raw codec fixtures. Two codec inputs lack source-required AN and are retained failures. Empty XCF binaries do not establish binary genotype validity. No runtime admission, phasing accuracy or public phasing adapter is certified.\n')
    add('SHA256SUMS',''.join(sha(raw)+'  '+name+'\n' for name,raw in sorted(members.items())).encode())
    output.parent.mkdir(parents=True,exist_ok=True)
    with output.open('xb') as stream:
        with zipfile.ZipFile(stream,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
            for name,raw in sorted(members.items()):
                info=zipfile.ZipInfo(name,(1980,1,1,0,0,0));info.external_attr=0o100644<<16;info.compress_type=zipfile.ZIP_DEFLATED;archive.writestr(info,raw)
    result=dict(status='frozen-original-wire-identities-and-independent-region-censuses',cases=len(cases),complete=sum(c['complete'] for c in cases),expected_failures=sum(not c['complete'] for c in cases),events=events,members=len(members),decoded_bytes=sum(map(len,members.values())),fixture_sha256=sha(output.read_bytes()),public_phasing_adapter_available=False)
    print(json.dumps(result,indent=2));return result
if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--report',required=True,type=Path);parser.add_argument('--output',required=True,type=Path);args=parser.parse_args();collect(args.report,args.output)
