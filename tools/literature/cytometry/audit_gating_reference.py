#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Audit complete native set-2 memberships without repairing the ISAC originals.

All selected extracted standard files must equal their original archive bytes.
FlowUtils complete polygon reference files and prepared scale values are supplied
as independent, retained reference artifacts. This is an evidence audit, not a
claim that the whole historical 80-gate suite passes. Requires Python stdlib only.
"""
import argparse
import csv
import hashlib
import json
import xml.etree.ElementTree as ET
import zipfile
from fractions import Fraction
from pathlib import Path

ARCHIVE = dict(bytes=2228245,sha256='f3ec0bd75cfecd5faf30ac2bc73104307464405cc09349b8db8da44fccdc7eae')
G = '{http://www.isac-net.org/std/Gating-ML/v2.0/gating}'
D = '{http://www.isac-net.org/std/Gating-ML/v2.0/datatypes}'

def pin(path):
    raw=path.read_bytes()
    return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())

def bitmap(path):
    rows=path.read_bytes().splitlines()
    assert len(rows)==10000 and all(x in (b'0',b'1') for x in rows),str(path)
    return rows

def main():
    p=argparse.ArgumentParser(description=__doc__)
    for name in ('archive','standard','native-run','flowutils-polygons','prepared-scale','out'):
        p.add_argument('--'+name,type=Path,required=True)
    a=p.parse_args();a.out.mkdir(parents=True,exist_ok=False)
    assert pin(a.archive)==ARCHIVE,'Original ISAC archive pin differs'
    intact={}
    with zipfile.ZipFile(a.archive) as z:
        for path in sorted(a.standard.rglob('*')):
            if not path.is_file():continue
            member=a.standard.name+'/'+path.relative_to(a.standard).as_posix()
            assert path.read_bytes()==z.read(member),'Extracted source differs: '+member
            intact[member]=pin(path)
    source=a.standard/'Compliance tests';xml=ET.parse(source/'set 2/gates2.xml').getroot()
    ids=[e.attrib[G+'id'] for e in xml if e.tag.startswith(G) and G+'id' in e.attrib]
    assert len(ids)==30 and len(set(ids))==30
    completion=json.loads((a.native_run/'COMPLETED.json').read_bytes());assert completion['profile']=='native-fcs-gatingml20-v1'
    report=json.loads((a.native_run/'analysis.json').read_bytes());assert report['result_admitted'] is True and report['events']==10000
    with (a.native_run/'memberships.tsv').open(encoding='utf8',newline='') as stream:
        rows=list(csv.reader(stream,delimiter='\t'))
    assert rows[0]==['event_index']+ids and len(rows)==10001
    assert all(row[0]==str(i) and len(row)==31 and all(x in ('0','1') for x in row[1:]) for i,row in enumerate(rows[1:],1))
    native={gate:[r[j+1].encode() for r in rows[1:]] for j,gate in enumerate(ids)}
    comparisons=[]
    for gate in ids:
        expected=source/'set 2'/('Results_'+gate+'.txt')
        if expected.exists():
            original=bitmap(expected);bad=[i+1 for i,(x,y) in enumerate(zip(native[gate],original)) if x!=y]
            comparisons.append(dict(gate=gate,status='EXACT_ORIGINAL_MATCH' if not bad else 'RETAINED_ORIGINAL_DISCREPANCY',native_inside=native[gate].count(b'1'),original_inside=original.count(b'1'),mismatches=len(bad),event_indices_1based=bad,original=pin(expected)))
        else:
            comparisons.append(dict(gate=gate,status='EXPECTED_FILENAME_MISSING',native_inside=native[gate].count(b'1')))
    assert sum(r['status']=='EXACT_ORIGINAL_MATCH' for r in comparisons)==24
    assert {r['gate'] for r in comparisons if r['status']=='RETAINED_ORIGINAL_DISCREPANCY'}=={'Poly1cl','Poly1uh','Poly1ch','Poly1ua','Poly1ca'}
    assert [r['gate'] for r in comparisons if r['status']=='EXPECTED_FILENAME_MISSING']==['Poly1ul']
    summary=list(csv.DictReader((source/'Summary.csv').read_text().splitlines()))
    anomalies=[r for r in summary if r['Folder']=='set 2' and r['Gate'] not in ids]
    assert len(anomalies)==1 and anomalies[0]['Gate']=='RectMix1.txt'
    orphans=[path.name for path in (source/'set 2').glob('Results_*.txt') if path.name not in {'Results_'+x+'.txt' for x in ids}]
    assert orphans==['Results_Poly1l.txt']
    independent=[];boundary=[]
    with a.prepared_scale.open() as stream:
        names=stream.readline().rstrip('\n').split('\t');data=[list(map(float,row.rstrip('\n').split('\t'))) for row in stream]
    assert len(data)==10000
    for polygon in xml.findall(G+'PolygonGate'):
        gate=polygon.attrib[G+'id'];reference=a.flowutils_polygons/('FlowUtils_'+gate+'.txt');reference_values=bitmap(reference)
        bad=[i for i,(x,y) in enumerate(zip(native[gate],reference_values)) if x!=y]
        if gate=='Poly1u':
            assert len(bad)==3
            dimensions=polygon.findall(G+'dimension');columns=[names.index(e.find(D+'fcs-dimension').attrib[D+'name']) for e in dimensions]
            vertices=[[Fraction(float(c.attrib[D+'value'])) for c in vertex] for vertex in polygon.findall(G+'vertex')]
            for index in bad:
                point=[Fraction(data[index][col]) for col in columns];edges=[]
                for j,v in enumerate(vertices):
                    w=vertices[(j+1)%len(vertices)]
                    cross=(w[0]-v[0])*(point[1]-v[1])-(w[1]-v[1])*(point[0]-v[0])
                    if cross==0 and all(min(v[k],w[k])<=point[k]<=max(v[k],w[k]) for k in (0,1)):edges.append(j+1)
                assert edges and native[gate][index]==b'1' and reference_values[index]==b'0'
                boundary.append(dict(event_1based=index+1,exact_binary_rational_point=list(map(str,point)),boundary_edges_1based=edges,native_and_original=1,flowutils=0))
        else:assert not bad,gate
        independent.append(dict(gate=gate,events=10000,differences=len(bad),reference=pin(reference)))
    assert len(independent)==8
    invalid=next(e for e in ET.parse(source/'set 1/gates1.xml').getroot().findall(G+'EllipsoidGate') if e.attrib[G+'id']=='Ellipsoid3D')
    covariance=[[float(v.attrib[D+'value']) for v in row] for row in invalid.find(G+'covarianceMatrix')]
    assert covariance[0][2]==17.5 and covariance[2][0]==15.5
    result=dict(status='PASS_BOUNDED_NATIVE_SET2_REFERENCE_AUDIT_WITH_RETAINED_CONTRADICTIONS',native_memberships=300000,original_exact_gates=24,independent_transformed_polygons_exact=6,set2_comparisons=comparisons,independent_polygon_comparisons=independent,exact_inclusive_boundary_proof=boundary,summary_gate_id_anomalies=anomalies,unbound_orphan_expected_files=orphans,invalid_set1_covariance=covariance,invalid_set1_rule='Section5.3.3(j) requires symmetric positive-definite covariance. Full-file admission refused; original definition remains unchanged.',archive=ARCHIVE,all_extracted_source_files_match_archive=intact,prepared_scale=pin(a.prepared_scale),full_standard_suite_passed=False,new_catalog_integrations=0)
    (a.out/'AUDIT.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf8')
    print('PASS 300000 native memberships: 24 gates match original bitmaps; 6 transformed polygons match independent FlowUtils. Original contradictions and 3 exact boundary differences retained.')

if __name__=='__main__':
    main()
