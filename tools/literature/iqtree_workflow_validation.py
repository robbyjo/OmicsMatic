#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify complete retained IQ-TREE public workflow evidence; launches no inference."""
from pathlib import Path, PurePosixPath
import hashlib,io,json,re,stat,sys,zipfile
from catalog_source_integrity import verify_source

ROOT=Path(__file__).resolve().parents[2]
DATA=ROOT/'docs/literature/omics-2023-2026'
SCIENCE={p.relative_to(ROOT).as_posix() for directory,pattern in [
    ('src/main/java/org/jlinalg/phylogeny','*.java'),('src/main/java/org/jlinalg/cli','Iqtree*.java'),
    ('src/test/java/org/jlinalg/phylogeny','*.java'),('src/test/java/org/jlinalg/cli','Iqtree*Test.java')]
    for p in (ROOT/directory).glob(pattern)}|{
    'src/main/java/org/jlinalg/cli/SimpleJson.java','src/main/java/org/jlinalg/cli/JLinAlgCli.java',
    'tools/literature/install_iqtree.py','tools/literature/test_install_iqtree.py','tools/literature/iqtree-runtime-lock.json',
    'tools/literature/iqtree_workflow_validation.py','tools/literature/test_iqtree_workflow_validation.py',
    'tools/literature/collect_iqtree_workflow_controls.py','LICENSE'}
for prefix in ['tools/literature/iqtree-runtime','src/main/resources/org/jlinalg/omics/iqtree-runtime-setup']:
    SCIENCE|={p.relative_to(ROOT).as_posix() for p in (ROOT/prefix).rglob('*') if p.is_file() and '__pycache__' not in p.parts}
SCIENCE|={'src/main/resources/org/jlinalg/omics/iqtree-runtime-lock.json'}
VERSIONS={'1.6.12','2.1.2','2.1.4-beta','2.2.0.3','2.3.1','2.3.5','2.3.6','2.4.0'}
sha=lambda raw:hashlib.sha256(raw).hexdigest()

def archive(raw):
    result={};size=0
    with zipfile.ZipFile(io.BytesIO(raw)) as bundle:
        for item in bundle.infolist():
            name=item.filename;path=PurePosixPath(name)
            assert not item.is_dir() and not path.is_absolute() and '..' not in path.parts and chr(92) not in name and name not in result
            assert path.as_posix()==name and not re.match(r'^[A-Za-z]:',name)
            assert stat.S_IFMT(item.external_attr>>16) in {0,stat.S_IFREG}
            assert item.file_size<=33554432 and len(result)<10000
            size+=item.file_size;assert size<=134217728
            result[name]=bundle.read(item)
    assert 'SHA256SUMS' in result
    lines=result['SHA256SUMS'].decode().splitlines();pins={}
    for line in lines:
        digest,name=line.split('  ',1);assert name not in pins;pins[name]=digest
    assert set(pins)==set(result)-{'SHA256SUMS'}
    for name,digest in pins.items():assert sha(result[name])==digest,name
    return result

def verify(root=ROOT,data=DATA,emit=True):
    if not __debug__:raise RuntimeError('Run validation with assertions enabled, never Python -O')
    root,data=Path(root),Path(data)
    record=json.loads((data/'iqtree-workflow-validation.json').read_bytes())
    assert record['status']=='passed-scoped-IQ-TREE-single-alignment-public-workflow'
    assert record['source_sha256'].keys()==SCIENCE
    for field in ['whole_paper_reproduced','biological_accuracy_certified','partitions_available','dating_available','batch_gene_trees_available','custom_mixture_models_available','released_or_deployed']:
        assert record[field] is False,field
    pin=record['artifact'];assert pin['path']=='iqtree-workflow-controls.zip'
    raw=(data/pin['path']).read_bytes();assert len(raw)==pin['bytes'] and sha(raw)==pin['sha256'];bundle=archive(raw)
    for name,digest in record['source_sha256'].items():
        assert sha(bundle['source/'+name])==digest,name
        if name=='src/main/java/org/jlinalg/cli/JLinAlgCli.java':verify_source(root,data,name,digest)
        else:assert sha((root/name).read_bytes())==digest,name
    obj=lambda name:json.loads(bundle[name])
    protocols=obj('protocols.json');assert len(protocols)==21 and len({p['name'] for p in protocols})==21
    directories=obj('directories.json');versions=set();counts={0:0,2:0,3:0};sites=trees=edges=native_cells=models=0;pruned={}
    for protocol in protocols:
        name=protocol['name'];code=protocol['exit_code'];assert protocol['error'] is None and code==protocol['expected_exit'] and code in counts
        counts[code]+=1;prefix='cases/'+name+'/'
        actual={n[len(prefix):] for n in bundle if n.startswith(prefix)}
        assert actual==set(protocol['files']),name
        for path,identity in protocol['files'].items():
            value=bundle[prefix+path];assert len(value)==identity['bytes'] and sha(value)==identity['sha256'],path
        base=prefix+'bundle/';session=obj(base+'session.json');manifest=obj(base+'manifest.json')
        assert session['exit_code']==code and session['scientific_result_admitted'] is False
        actual={p[len(base):] for p in bundle if p.startswith(base)}
        assert actual==set(manifest['files'])|{'manifest.json'}|({'COMPLETED.json'} if code==0 else set()),name
        actual_dirs={p[len('bundle'):].lstrip('/') for p in directories[name] if p=='bundle' or p.startswith('bundle/')}
        assert set(manifest['directories'])==actual_dirs and len(manifest['directories'])==len(actual_dirs)
        for path,identity in manifest['files'].items():
            value=bundle[base+path];assert len(value)==identity['bytes'] and sha(value)==identity['sha256'],path
        assert b'Required action' in bundle[base+'WARNINGS.md']
        if code!=0:
            assert not any(p.endswith('COMPLETED.json') or p.endswith('COMPLETION-PROVISIONAL.json') for p in actual)
            assert session['status'].startswith('SKIP3' if code==3 else 'FAIL2'),name
            continue
        completed=obj(base+'COMPLETED.json');assert completed['scientific_result_admitted'] is True and completed['biological_accuracy_certified'] is False
        assert completed['manifest_sha256']==sha(bundle[base+'manifest.json'])
        assert manifest['status']=='audited-provisional-awaiting-finalization'
        runtime=obj(base+'runtime-admission.json');version=runtime['version'];versions.add(version)
        assert runtime['profile']=='linux-x86_64-iqtree-'+version
        process=obj(base+'process/process.json');assert process['status']=='completed-process-only' and process['exit_code']==0 and process['owned_live_after_cleanup']==[]
        audit=obj(base+'output-audit.json');assert audit['scientific_result_admitted'] is False and audit['sites']['totalAgrees'] and audit['sites']['nativeDisagreements']==0
        population=obj(base+'analyzed-population.json');recipe=session['recipe']
        assert audit['report']['taxa']==population['input_taxa']==len(protocol['original_sequences'])
        assert audit['sites']['sites']==audit['report']['sites']
        assert 'sample one α' in protocol['original_sequences'] or name=='sequential-PHYLIP'
        sites+=audit['sites']['sites'];trees+=audit['bootstrap_trees'];edges+=audit['support_edges'];native_cells+=audit['sites']['nativeComparisons']
        models+=len((audit['report']['selection'] or {}).get('candidates',[]))
        taxa=obj(base+'inputs/taxa.json')
        assert {row['original_taxon'] for row in taxa['rows']}==set(protocol['original_sequences'])
        if name.startswith('duplicates-'):
            ignored=population['source_ignored_relationships'];assert len(ignored)==12 and len(population['analyzed_taxa'])==24 and population['input_taxa']==36
            assert population['policy']=='SOURCE_REMOVE_RESTORE';pruned[version]=len(ignored)
        else:assert population['policy']=='KEEP' and not population['source_ignored_relationships']
        expected=recipe['support']['replicates'];assert expected==audit['bootstrap_trees']
    assert counts=={0:17,2:2,3:2} and versions==VERSIONS and pruned=={'1.6.12':12,'2.4.0':12}
    assert (trees,edges,models)==(1100,36,71),(trees,edges,models)
    observed=dict(public_protocols=21,completed_fits=17,expected_skips=2,expected_failures=2,bootstrap_trees=trees,support_edges=edges,site_cells=sites,native_JC_cells=native_cells,model_candidates=models)
    assert record['observed']==observed,(record['observed'],observed)
    gate=obj('validation/publication.json');assert gate['java']==dict(tests=6457,failures=0,errors=0,skipped=254,passed=6203)
    assert gate['focused']==dict(tests=530,linux_passed=530,windows21_passed=495,windows25_passed=495,windows_platform_aborts=35)
    assert gate['packaged']['focused_Linux_passed']==530 and gate['packaged']['preserved_SHAPEIT_members']==122
    assert gate['packaged']['candidate_sha256']==record['packaged_candidate_sha256']
    portable=obj('validation/portable.json');assert [p['exit_code'] for p in portable['protocols']]==[0,2,2] and portable['setup_export_exact'] is True
    result=dict(status='passed',**observed,exact_editions=sorted(versions),whole_paper_reproduced=False,scope='Retained complete public bundles, current scientific-source pins and original model/site/support audits. No inference, download or scientific accuracy certification is performed by this verifier.')
    if emit:print(json.dumps(result,indent=2))
    return result

if __name__=='__main__':
    try:verify()
    except Exception as failure:
        print('IQ-TREE EVIDENCE FAIL2: '+str(failure)+'\nRequired action: preserve the evidence, correct the named source/archive mismatch and repeat validation. Do not promote the tool or admit a partial result.',file=sys.stderr);sys.exit(2)
