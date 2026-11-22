"""Audit the bounded handover publication, including every ZIP member.

Run only before committing the exact proposed checkpoint. The receipt is written
NEW. This checks the selected public artifacts; ignored local evidence remains
private and is not recursively traversed.
"""
from pathlib import Path
import hashlib,io,json,re,subprocess,zipfile
ROOT=Path(__file__).resolve().parents[3]
FOLDER=Path(__file__).resolve().parent
BASE='68c0f1957bfbc5bb3cef0165e76b9ca81ed67d2e'
FORBIDDEN={'.pdf','.bam','.bai','.vcf','.bcf','.rds','.rda','.rdata','.jar','.class','.exe','.dll','.so','.whl','.pyc'}
TOKEN=re.compile(rb'(?:gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|(?:AKIA|ASIA)[A-Z0-9]{16}|-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----)')
FULLTEXT=re.compile(rb'(?:<!DOCTYPE\s+article\b|<article\s+xmlns|'+b'<'+rb'PubmedArticle\b)')

def require(condition,message):
    if not condition: raise ValueError(message)

def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())

def check(name,raw):
    require(not name.startswith('/') and '..' not in Path(name).parts,'Unsafe member name: '+name)
    require(Path(name).suffix.lower() not in FORBIDDEN,'Private/runtime binary in publication: '+name)
    require(not TOKEN.search(raw),'Credential candidate; manual review required: '+name)
    require(not FULLTEXT.search(raw),'Primary full-text candidate; manual review required: '+name)
    if name.endswith('.xml'):
        require(name.startswith('test-reports/') and b'<testsuite ' in raw[:500],'Unapproved XML source: '+name)

def main():
    process=subprocess.run(['git','status','--porcelain=v1','-z','--untracked-files=all'],cwd=ROOT,check=True,capture_output=True)
    files={};members={};receipt=FOLDER/'PUBLICATION-DATA-AUDIT.json'
    require(not receipt.exists(),'Retain the old audit; choose a NEW checkpoint instead of overwriting')
    allowed={'docs/literature/HANDOVER_CURRENT.md','docs/literature/ACAT091_NATIVE.md','docs/literature/HANDOVER_2026-11-22_ACAT_SKAT.md','src/main/java/org/jlinalg/cli/JLinAlgCli.java','src/main/java/org/jlinalg/cli/AcatToolsCli.java','src/main/java/org/jlinalg/settest/Acat091.java','src/main/java/org/jlinalg/settest/RLongDoubleSum.java','src/test/java/org/jlinalg/cli/Acat091ReferenceTest.java','src/test/java/org/jlinalg/cli/AcatToolsCliTest.java','src/test/java/org/jlinalg/settest/Acat091Test.java','src/test/java/org/jlinalg/settest/RLongDoubleSumTest.java'}
    prefixes=('docs/literature/acat091-development/','tools/literature/acat091/','examples/acat091/','src/main/resources/org/jlinalg/omics/acat091/','src/test/resources/org/jlinalg/settest/acat091/')
    for field in process.stdout.split(b'\0'):
        if not field:continue
        require(field[:2] in [b' M',b'??'],'Unexpected staged/deleted/renamed change')
        name=field[3:].decode();require(name in allowed or name.startswith(prefixes),'Outside publication allowlist: '+name)
        path=ROOT/name;require(path.is_file() and not path.is_symlink(),'Not a regular publication file: '+name)
        raw=path.read_bytes();check(name,raw);files[name]=pin(raw)
        if name.endswith('.zip'):
            require(name=='docs/literature/acat091-development/validation.zip','Unapproved archive: '+name)
            with zipfile.ZipFile(io.BytesIO(raw)) as archive:
                require(len(archive.namelist())==len(set(archive.namelist())),'Duplicate archive entries')
                for info in archive.infolist():
                    require(info.file_size<=20_000_000,'Oversized archive member: '+info.filename)
                    require(not info.filename.endswith(('.zip','.gz','.tgz')),'Unexpected nested compressed artifact')
                    data=archive.read(info);check(info.filename,data);members[info.filename]=pin(data)
    q=json.loads((FOLDER/'QUALIFICATION.json').read_bytes())
    require(members==q['validation_members'],'Qualification/archive population mismatch')
    require(sum(name.startswith('historical-root-variants/') for name in members)==23,'Incomplete root variant preservation')
    require(q['original_matrix_cases']==274 and q['original_R_sum_mean_pairs']==192,'Incomplete actual original fixtures')
    require(q['private_transfer_completed'] is False and q['whole_goal_complete'] is False,'Unexpected completion claim')
    result=dict(status='PASS_BOUNDED_HANDOVER_PUBLICATION_DATA_AUDIT',baseline=BASE,audited_top_level_files=dict(sorted(files.items())),validation_members=members,credential_candidates=0,primary_full_text_candidates=0,private_runtime_binaries_RDS_bundled_SKAT_genotypes_XML_PDF_or_human_input_data_published=False,retained_primary_census_is_pointers_and_hashes_only=True,original_oracle_data_are_artificial=True,root_older_variants_archived_without_restoring_over_main=23,private_transfer_completed=False,whole_goal_complete=False)
    with receipt.open('x',encoding='utf-8',newline='\n') as stream:json.dump(result,stream,indent=2);stream.write('\n')
    print('PASS public data audit:',len(files),'selected files;',len(members),'archive members;23 historical root variants;no private binary/full-text/credential candidates.')
    return 0

if __name__=='__main__':raise SystemExit(main())
