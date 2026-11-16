#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Capture only synthetic original/native BAM/index pairs for offline regression checks."""
from pathlib import Path
import argparse,hashlib,json,zipfile
ROOT=Path(__file__).resolve().parents[3]
def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def load(path):return json.loads(path.read_bytes())
def capture(fixtures,first,second,adversarial,output):
    prior=load(first/'RESULTS.json');later=load(second/'RESULTS.json');complete=load(second/'COMPLETED.json');controls=load(adversarial/'COMPLETED.json')
    assert len(prior['results'])==4 and len(prior['failures'])==1 and prior['failures'][0]['case']=='cross_bgzf_members' and not (first/'COMPLETED.json').exists()
    assert len(later['results'])==1 and not later['failures'] and complete['cases']==['cross_bgzf_members'] and len(complete['original_score_overflow_refusals'])==2
    assert {row['case'] for row in prior['results']+later['results']}=={p.name for p in fixtures.iterdir() if p.is_dir()}
    assert controls['status']=='PASS_NATIVE_BAI_CORRUPTION_AND_ACTUAL_QUERY_CONTROLS'
    archive={};pairs=[];negative=[];source_failures=[]
    def include(name,path):
        raw=path.read_bytes()
        if name in archive:assert archive[name]==raw
        archive[name]=raw
    for base,rows in [(first,prior['results']),(second,later['results'])]:
        for case in rows:
            name=case['case'];queries='expected/'+name+'/queries.tsv';include(queries,fixtures/name/'queries.tsv');include('expected/'+name+'/CASE.json',fixtures/name/'CASE.json')
            for row in case['results']:
                producer=row['producer'];stem='pairs/'+name+'/'+producer
                if 'result' not in row:
                    source_failures.append(dict(case=name,**row));continue
                if producer=='native-java-input':bam=fixtures/name/'input.bam';bai=base/name/'native-input/cli-output/alignment.bai'
                elif producer.endswith('-direct-index'):
                    edition=producer.split('-')[0];bam=fixtures/name/'input.bam';bai=base/name/edition/'direct-index/original.bai'
                elif producer.startswith('native-java-after-'):
                    edition,action=producer.removeprefix('native-java-after-').split('-');bam=base/name/edition/action/'alignment.bam';bai=base/name/edition/action/'native-index/fresh.bai'
                else:
                    edition,action=producer.split('-');bam=base/name/edition/action/'alignment.bam';bai=Path(str(bam)+'.bai')
                observed=row['result'];assert pin(bam.read_bytes())==observed['bam'] and pin(bai.read_bytes())==observed['bai']
                success=observed['audit']['status']=='PASS_NATIVE_COMPLETE_BAM_BAI_AUDIT'
                expected_error='' if success else 'metadata reference extent'
                include(stem+'/data.bam',bam);include(stem+'/index.bai',bai)
                pairs.append([name+'/'+producer,name,producer,stem+'/data.bam',stem+'/index.bai',queries,str(success).lower(),expected_error,str(len(case['manifest']['records']))])
    assert len(pairs)==51 and sum(row[6]=='false' for row in pairs)==3 and len(source_failures)==2
    archive['PAIRS.tsv']=('id\tcase\tproducer\tbam\tbai\tqueries\taudit_success\terror_contains\trecords\n'+'\n'.join('\t'.join(row) for row in sorted(pairs))+'\n').encode()
    for row in controls['results']:
        name=row['name'];stem='controls/'+name;include(stem+'/index.bai',adversarial/name/'controlled.bai')
        success=row['audit']['status']=='PASS_NATIVE_COMPLETE_BAM_BAI_AUDIT';query=adversarial/name/'independent-query.tsv'
        if query.exists():query_name=stem+'/queries.tsv';include(query_name,query)
        elif row['indexed_queries'] is not None:query_name='expected/wide_no_tail/queries.tsv'
        else:query_name=''
        error='' if success else row['audit']['error'].replace('\\:',':')
        negative.append([name,'pairs/wide_no_tail/native-java-input/data.bam',stem+'/index.bai',query_name,str(success).lower(),error])
    assert len(negative)==13
    archive['CONTROLS.tsv']=('name\tbam\tbai\tqueries\taudit_success\terror_contains\n'+'\n'.join('\t'.join(row) for row in sorted(negative))+'\n').encode()
    archive['ORIGINAL-SCORE-REFUSALS.json']=(json.dumps(source_failures,indent=2)+'\n').encode()
    archive['LICENSE-project.txt']=(ROOT/'LICENSE').read_bytes()
    manifest=dict(status='SYNTHETIC_ORIGINAL_AND_NATIVE_INDEX_REGRESSION_CAPTURE_NOT_BIOLOGICAL_RESULTS',pairs=51,expected_metadata_refusals=3,adversarial_controls=13,source_score_refusals=2,
        source_refusals_are_not_BAM_results=True,candidate=complete['candidate'],members={name:pin(raw) for name,raw in archive.items()},
        source_records={str(base.relative_to(ROOT)):pin((base/'RESULTS.json').read_bytes()) for base in [first,second]},
        original_runtime_hashes={'0.6.6':'8b06691f80bc89c733267c523dbbb3a4c36b51cae42ef7b2a171e17c68516dc2','0.8.2':'bbccbd914ebf8c2b09a5bb6a922dba99ac24c09d25ddd4925f5b2cc8dc359543'},
        independent_expected_queries=pin((fixtures/'COMPLETED.json').read_bytes()),new_catalog_credit=0,whole_goal_complete=False)
    archive['MANIFEST.json']=(json.dumps(manifest,indent=2)+'\n').encode()
    output.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(output,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for name,raw in sorted(archive.items()):z.writestr(name,raw)
    print(json.dumps(dict(status='SYNTHETIC_REGRESSION_FIXTURE_CAPTURED',archive=str(output),identity=pin(output.read_bytes()),members=len(archive),pairs=51,controls=13)),flush=True)
if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__)
    for name in ['fixtures','first','second','adversarial','out']:p.add_argument('--'+name,type=Path,required=True)
    a=p.parse_args();capture(a.fixtures.resolve(),a.first.resolve(),a.second.resolve(),a.adversarial.resolve(),a.out.resolve())
