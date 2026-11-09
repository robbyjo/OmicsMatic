# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Read and materialize integrity-checked CellChat evidence without executing R."""
import argparse,hashlib,json,zipfile
from pathlib import Path,PurePosixPath
from setup_deseq2_reference import ROOT,pin

HOME=ROOT/'docs/literature/cellchat-workflow-development'

def member(name):
    p=PurePosixPath(name)
    assert name and p.as_posix()==name and not p.is_absolute() and '..' not in p.parts and '.' not in p.parts and not any(c in name for c in '\\:\0\r\n')
    return name

class Evidence:
    def __init__(self,home):
        self.home=home.resolve();self.proof=json.loads((self.home/'VALIDATION.json').read_bytes());self.handles={}
        p=self.proof;assert p['status']=='PASS_ORIGINAL_CELLCHAT_TYPED_WORKFLOW_DEVELOPMENT_ONLY' and not p['new_catalog_credit'] and not p['whole_goal_complete']
        assert (p['new_scoped_integrations'],p['including_existing_adapters'],p['missing'])==(93,96,248)
        seen=set()
        for name,wanted in p['shards'].items():
            member(name);assert pin(self.home/name)==wanted
            z=zipfile.ZipFile(self.home/name);self.handles[name]=z;infos=z.infolist();assert len(infos)==len({i.filename for i in infos}) and len(infos)<=20000
            for info in infos:
                assert not info.is_dir() and info.filename.startswith('blobs/') and len(info.filename)==70 and info.file_size<=67108864
                digest=info.filename[6:];assert len(digest)==64 and all(c in '0123456789abcdef' for c in digest) and digest not in seen;seen.add(digest)
                record=p['blobs'][digest];assert record['shard']==name and record['bytes']==info.file_size
                with z.open(info) as stream:assert hashlib.file_digest(stream,'sha256').hexdigest()==digest
        assert seen==set(p['blobs'])
        for name,wanted in p['files'].items():
            member(name);assert wanted['sha256'] in p['blobs'] and wanted['bytes']==p['blobs'][wanted['sha256']]['bytes']
        assert sum(v['bytes'] for v in p['blobs'].values())<=1073741824
        for name,wanted in p['artifacts'].items():member(name);assert pin(self.home/name)==wanted
    def read(self,name):
        wanted=self.proof['files'][member(name)];record=self.proof['blobs'][wanted['sha256']]
        raw=self.handles[record['shard']].read('blobs/'+wanted['sha256']);assert len(raw)==wanted['bytes'] and hashlib.sha256(raw).hexdigest()==wanted['sha256'];return raw
    def document(self,name):return json.loads(self.read(name))
    def materialize(self,prefix,out):
        prefix=member(prefix.rstrip('/'))+'/';out=out.resolve();assert not out.exists(),'Preserve existing output and choose a NEW directory'
        names=[name for name in self.proof['files'] if name.startswith(prefix)];assert names,'No such retained evidence prefix'
        out.mkdir(parents=True)
        for name in names:
            relative=member(name[len(prefix):]);target=out/relative;assert target.resolve().is_relative_to(out)
            target.parent.mkdir(parents=True,exist_ok=True)
            with target.open('xb') as stream:stream.write(self.read(name))
        return out
    def close(self):
        for z in self.handles.values():z.close()

def verify(home=HOME,check_current=False):
    evidence=Evidence(home);p=evidence.proof
    try:
        if check_current:
            for name,wanted in p['source_pins'].items():assert pin(ROOT/name)==wanted,(name,'current source changed')
            for name,wanted in p['catalog_unchanged'].items():assert pin(ROOT/name)==wanted
            assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==p['canonical_executable_unchanged']
        def record(folder):return evidence.document('evidence/'+folder+'/COMPLETED.json')
        for folder in ['typed-controls-7','typed-controls-8']:
            result=record(folder);assert result['status']=='PASS_ACTUAL_JAR_ORIGINAL_CELLCHAT_TWO_CONDITION_SIX_SAMPLE_WORKFLOW_ONLY'
            completed=evidence.document('evidence/'+folder+'/analysis/COMPLETED.json');assert completed['result_admitted'] and completed['original_centrality_fallbacks']==122
            prefix='evidence/'+folder+'/analysis/'
            for name,wanted in completed['files'].items():assert evidence.proof['files'][prefix+name]==wanted
        edges=record('workflow-edges-2');assert edges['status']=='PASS_TWO_ACTUAL_ORIGINAL_CELLCHAT_REPLICATE_BOUNDARY_AND_NONFINITE_FAMILIES'
        for family in edges['families']:
            if family['profile']=='replicate-zero-and-rare':
                r=family['provenance'];assert (r['verified_pooled_VEGFA_edges_removed_by_three_sample_filter'],r['verified_rare_edges_zeroed_at_exact_min_cells10'],r['positive_pooled_rows_with_zero_selected_sample_scores'],r['not_tested_sample_comparisons'],r['original_nonfinite_sample_test_fields'])==(54,1428,448,48,224)
            else:assert family['profile']=='constant-comparison' and family['provenance']['original_nonfinite_sample_test_fields']==315
        failures=record('workflow-failures-2');assert failures['status']=='PASS17_ACTUAL_JAR_DISPOSITIONS_EIGHT_REHASHED_BUNDLE_REFUSALS_ONLY' and len(failures['dispositions'])==17 and len(failures['rehashed_refusals'])==8 and failures['timeout_owned_process_terminated']
        assert record('direct-reference-1')['status']=='PASS_EIGHT_SEPARATE_DIRECT_CELLCHAT_PACKAGE_REFITS_ONLY'
        assert record('direct-comparison-2')['status']=='PASS_BOTH_FINAL_TYPED_JAVA_R_WORKFLOWS_AGAINST_EIGHT_SEPARATE_DIRECT_PACKAGE_REFITS'
        assert record('standard-resource-check-1')['status']=='PASS_STANDARD_GRADLE_PROCESS_RESOURCES_BYTES_MATCH_TESTED_JAR_PAYLOAD'
        assert record('portable-verification-1')['status']=='PASS30_FROZEN_CELLCHAT_BUNDLE_CHECKS_ACROSS_TWO_BUILDS_THREE_JVMS'
        assert record('guide-example-check-1')['status']=='PASS_LITERAL_GUIDE_EXAMPLE_AND_CURRENT_CATALOG_MAFFT_CHECKS'
        assert record('presto-reference-setup-1')['status']=='SKIP3_ORIGINAL_PRESTO_REFERENCE_UNAVAILABLE'
        lock=evidence.document('source-snapshot/tools/literature/cellchat-workflow-source-lock.json')['sources']['2.2.0-2025-release']
        for name,wanted in lock['files'].items():assert p['files']['original-source/CellChat2.2.0/'+name]==wanted
        presto=evidence.document('evidence/presto-source-intake-1/original-members.json')
        for name,wanted in presto.items():assert p['files']['original-source/presto1.0.0/'+name]==wanted
        return p
    finally:evidence.close()

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete evidence/source checks')
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--home',type=Path,default=HOME);parser.add_argument('--check-current-source',action='store_true');parser.add_argument('--materialize');parser.add_argument('--out',type=Path);args=parser.parse_args()
    verify(args.home,args.check_current_source)
    if args.materialize:
        assert args.out is not None,'--out NEW_DIRECTORY is required';store=Evidence(args.home)
        try:store.materialize(args.materialize,args.out)
        finally:store.close()
        print('Materialized exact retained evidence; run the paired Java JAR --verify for native transport replay')
    else:print('Verified frozen original CellChat development evidence; no R refit, producer authentication or new catalog credit')
