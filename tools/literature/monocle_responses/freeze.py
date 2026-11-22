"""Freeze admitted original-source response and residual-Moran controls; do not execute models."""
from pathlib import Path
import argparse,gzip,hashlib,json

ROOT=Path(__file__).resolve().parents[3]

def pin(path):
    raw=path.read_bytes();return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--reference',type=Path,required=True)
    args=parser.parse_args();reference=args.reference.resolve(strict=True)
    result=json.loads((reference/'RESULT.json').read_bytes())
    assert json.loads((reference/'PROCESS.json').read_bytes())['exit_code']==0
    assert result['status']=='PASS_ORIGINAL_MONOCLE_RESPONSE_BRANCH_AND_SPDEP_CONTROLS'
    assert (result['R'],result['monocle3'],result['spdep'])==('4.3.3','1.0.0','1.4.1')
    assert result['official_source_body_formals_identical'] and result['official_namespace_unchanged']
    assert not result['author_code_executed'] and not result['whole_paper_reproduced']
    assert len(result['source_guard_controls'])==6 and all(row['refused'] and not row['installed_namespace_modified'] for row in result['source_guard_controls'].values())
    cases=list(result['cases'].values());assert len(cases)==6
    assert sum(len(rows) for case in cases for rows in case['tests'].values())==66
    assert sum(len(row) for case in cases for row in case['expected_responses'])==984
    fixture=dict(result,cases=cases)
    raw=(json.dumps(fixture,ensure_ascii=False,sort_keys=True,separators=(',',':'),allow_nan=False)+'\n').encode('utf-8')
    target=ROOT/'src/test/resources/org/jlinalg/singlecell/monocle-responses';target.mkdir(parents=True,exist_ok=False)
    with (target/'original-controls.json.gz').open('xb') as stream:stream.write(gzip.compress(raw,compresslevel=9,mtime=0))
    provenance=dict(status='FROZEN_ADMITTED_ORIGINAL_RESPONSE_AND_SPDEP_CONTROLS',original_versions=dict(R='4.3.3',monocle3='1.0.0',spdep='1.4-1'),cases=6,response_values=984,original_gene_tests=66,owned_changed_function_controls=6,full_graph_test_called=False,author_fork_executed=False,whole_paper_reproduced=False,selected_functions=['monocle3::graph_test response-expression AST only','spdep::lm.morantest'],source_identity_guard='Strict parsed official body and formals plus expected namespace environment before and after; serialized-body hashes retained as argument-state diagnostics',reference_inputs={name:pin(reference/name) for name in ['RESULT.json','PROCESS.json','COMMAND.json','EXECUTED-reference.R','OFFICIAL-graph_test.R','OFFICIAL-lm.morantest.R','SOURCE-GUARDS.json','SOURCE-EXPRESSION.json','SESSION.txt']},fixture=pin(target/'original-controls.json.gz'),decompressed_sha256=hashlib.sha256(raw).hexdigest(),source_urls=['https://github.com/cole-trapnell-lab/monocle3/tree/004c096f3a3bdaf1e6709a65fb36f40ba775f1ab','https://cran.r-project.org/src/contrib/Archive/spdep/spdep_1.4-1.tar.gz','https://cran.r-project.org/src/base/R-4/R-4.3.3.tar.gz'])
    (target/'PROVENANCE.json').write_text(json.dumps(provenance,indent=2)+'\n',encoding='utf-8',newline='\n')
    (target/'.gitattributes').write_text('* -text whitespace=cr-at-eol\n',encoding='utf-8',newline='\n')
    print('FROZEN6 original cases,984 response values and66 residual-Moran tests',flush=True)

if __name__=='__main__':main()
