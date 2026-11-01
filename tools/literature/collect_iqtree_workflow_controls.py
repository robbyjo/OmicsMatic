#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze complete existing public controls; never run inference or replace a capture."""
from pathlib import Path
import argparse,io,json,os,sys,zipfile
from iqtree_workflow_validation import SCIENCE,sha,archive

ROOT=Path(__file__).resolve().parents[2]

def collect(cache,publication,output,report):
    if output.exists() or report.exists():raise ValueError('Preserve existing artifact/report; use NEW paths')
    capture=cache/'push-public-controls-1';terminal=json.loads((capture/'running.json').read_bytes())
    assert terminal==dict(status='terminal',protocols=21,all_expected=True)
    protocols=json.loads((capture/'observed.json').read_bytes());assert len(protocols)==21
    gate=json.loads((publication/'validation.json').read_bytes());candidate=ROOT/'build/cli/omicsmatic-0.3.7.jar'
    assert sha(candidate.read_bytes())==gate['packaged']['candidate_sha256']
    entries={};directories={};pins={}
    def add(name,raw):assert name not in entries;entries[name]=raw
    for name in sorted(SCIENCE):
        raw=(ROOT/name).read_bytes();pins[name]=sha(raw);add('source/'+name,raw)
    for protocol in protocols:
        assert protocol['error'] is None and protocol['expected_exit']==protocol['exit_code']
        root=capture/'linux'/protocol['name'];files={p.relative_to(root).as_posix():p for p in root.rglob('*') if p.is_file()}
        assert set(files)==set(protocol['files'])
        directories[protocol['name']]=sorted(p.relative_to(root).as_posix() for p in root.rglob('*') if p.is_dir())
        for name,path in files.items():
            assert not path.is_symlink();raw=path.read_bytes();pin=protocol['files'][name]
            assert len(raw)==pin['bytes'] and sha(raw)==pin['sha256'];add('cases/'+protocol['name']+'/'+name,raw)
    add('protocols.json',(json.dumps(protocols,indent=2)+'\n').encode())
    add('directories.json',(json.dumps(directories,indent=2)+'\n').encode())
    add('controller/run_public_controls.py',(publication/'run_public_controls.py').read_bytes())
    for source,target in [('validation.json','publication.json'),('portable-validation.json','portable.json'),('push-receipt.json','source-publication-receipt.json')]:add('validation/'+target,(publication/source).read_bytes())
    for folder in ['native-matrix']:
        for name in ['observed.json','runs.json','source-before.json','windows-java21.stdout','windows-java25.stdout','linux-java21.stdout']:
            add('validation/'+folder+'/'+name,(publication/folder/name).read_bytes())
    for name in ['packaged.stdout','packaged.stderr','packaged-validation.json','portable-copy.stdout','changed-bytes.stdout','extra-empty-directory.stdout','runtime-export.stdout']:
        add('validation/'+name,(publication/name).read_bytes())
    add('validation/java-whole-project.log',(cache/'push-validation-java-1.log').read_bytes())
    add('validation/python-whole-project.log',(cache/'push-validation-python-3.log').read_bytes())
    add('validation/documentation-build.log',(publication/'documentation-build.log').read_bytes())
    selected={};payload=io.BytesIO()
    with zipfile.ZipFile(candidate) as original,zipfile.ZipFile(payload,'w',compression=zipfile.ZIP_DEFLATED) as target:
        for name in original.namelist():
            if name.endswith('.class') and (name.startswith('org/jlinalg/phylogeny/') or name.startswith('org/jlinalg/cli/Iqtree') or name in ['org/jlinalg/cli/JLinAlgCli.class','org/jlinalg/cli/SimpleJson.class','org/jlinalg/cli/SimpleJson$Parser.class']):
                raw=original.read(name);target.writestr(name,raw);selected[name]=sha(raw)
    assert len(selected)>50
    add('packaged/tested-owned-classes.jar',payload.getvalue());add('packaged/member-sha256.json',(json.dumps(selected,indent=2)+'\n').encode())
    totals=dict(public_protocols=21,completed_fits=17,expected_skips=2,expected_failures=2,bootstrap_trees=0,support_edges=0,site_cells=0,native_JC_cells=0,model_candidates=0)
    for protocol in protocols:
        if protocol['exit_code']!=0:continue
        audit=json.loads(entries['cases/'+protocol['name']+'/bundle/output-audit.json'])
        for key,source in [('bootstrap_trees','bootstrap_trees'),('support_edges','support_edges')]:totals[key]+=audit[source]
        totals['site_cells']+=audit['sites']['sites'];totals['native_JC_cells']+=audit['sites']['nativeComparisons']
        totals['model_candidates']+=len((audit['report']['selection'] or {}).get('candidates',[]))
    add('SHA256SUMS',''.join(sha(raw)+'  '+name+'\n' for name,raw in sorted(entries.items())).encode())
    output.parent.mkdir(parents=True,exist_ok=True);report.parent.mkdir(parents=True,exist_ok=True)
    partial=output.with_name(output.name+'.partial-'+str(os.getpid()))
    with zipfile.ZipFile(partial,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as target:
        for name,raw in sorted(entries.items()):
            info=zipfile.ZipInfo(name,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100444<<16;target.writestr(info,raw)
    decoded=archive(partial.read_bytes());assert decoded==entries
    os.link(partial,output);partial.unlink()
    result=dict(schema_version=1,status='passed-scoped-IQ-TREE-single-alignment-public-workflow',artifact=dict(path=output.name,bytes=output.stat().st_size,sha256=sha(output.read_bytes())),source_sha256=pins,observed=totals,
        packaged_candidate='build/cli/omicsmatic-0.3.7.jar',packaged_candidate_sha256=gate['packaged']['candidate_sha256'],tested_owned_class_members=len(selected),
        exact_editions=['1.6.12','2.1.2','2.1.4-beta','2.2.0.3','2.3.1','2.3.5','2.3.6','2.4.0'],
        whole_paper_reproduced=False,biological_accuracy_certified=False,partitions_available=False,dating_available=False,batch_gene_trees_available=False,custom_mixture_models_available=False,released_or_deployed=False,
        scope='Complete retained single-alignment public command controls and actual packaged/source proof. All failed/skipped cases and raw source values remain retained. The current command does not certify biological accuracy, historical settings beyond the eight exact editions, partitions, dating, custom/mixture models or whole-paper reconstruction. Source publication and this catalog proof are separate from release/deployment.')
    with report.open('x',encoding='utf-8') as target:target.write(json.dumps(result,indent=2)+'\n')
    print(json.dumps(dict(status='frozen-complete-public-workflow-evidence',**totals,archive_members=len(entries),artifact_sha256=result['artifact']['sha256'])))

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--cache',type=Path,default=ROOT/'build/literature-review/iqtree-runtime');p.add_argument('--publication',type=Path,required=True);p.add_argument('--output',type=Path,required=True);p.add_argument('--report',type=Path,required=True);a=p.parse_args()
    collect(a.cache,a.publication,a.output,a.report)

if __name__=='__main__':
    try:main()
    except Exception as failure:
        print('IQ-TREE CAPTURE FAIL2: '+str(failure)+'\nRequired action: preserve all captures and partial files, correct the named cause and use NEW artifact/report paths. No analysis is run or admitted by this collector.',file=sys.stderr);sys.exit(2)
