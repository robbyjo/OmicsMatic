"""Freeze actual independent base-R published-method controls, without upstream parity claims."""
import hashlib
import json
from pathlib import Path
import zipfile

ROOT=Path(__file__).resolve().parents[2]
RUN=ROOT/'build/literature-review/fuma-runtime/expression-preparation-development-1'
SOURCE=RUN/'independent-R-controls-2'
DEST=ROOT/'src/test/resources/org/jlinalg/enrichment/fuma-expression-preparation'


def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())


def main():
    if not __debug__:raise RuntimeError('Never disable evidence assertions')
    assert not DEST.exists(),'Preserve existing fixture'
    files={p.relative_to(SOURCE).as_posix():p.read_bytes() for p in sorted(SOURCE.rglob('*')) if p.is_file()}
    assert len([n for n in files if n.endswith('/expected.tsv')])==16
    assert (RUN/'independent-R-controls-2.log').read_text(encoding='utf-8').strip()=='Completed independent published-method controls:16 cases,192 gene rows'
    for n,raw in files.items():
        if n.endswith(('/input.tsv','/samples.tsv','/labels.tsv','/expected.tsv')):assert raw==(RUN/'independent-R-controls-1'/n).read_bytes(),n
    producer=ROOT/'tools/literature/collect_fuma_expression_preparation_controls.R'
    receipt=dict(scope='Independent base-R calculation of the PUBLISHED FUMA expression transformation, not original FUMA preprocessing code/runtime or historical GTEx resource equivalence.',R_version=files['R-version.txt'].decode().strip(),cases=16,gene_rows=192,producer_pin=pin(producer.read_bytes()),Rscript_executable_pin=pin(Path('C:/Program Files/R/R-4.6.1/bin/Rscript.exe').read_bytes()),locale='LC_ALL=C',prior_locale_warning_run='Retained independent-R-controls-1.log: unsupported C.UTF-8 startup settings. Rerun in C locale; every scientific input/output TSV byte is unchanged.',files={n:pin(raw) for n,raw in files.items()})
    files['producer.R']=producer.read_bytes();files['receipt.json']=(json.dumps(receipt,indent=2)+'\n').encode()
    DEST.mkdir(parents=True);(DEST/'.gitattributes').write_text('* -text\n',encoding='ascii',newline='\n')
    with zipfile.ZipFile(DEST/'independent-R-controls.zip','x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
        for n,raw in sorted(files.items()):
            info=zipfile.ZipInfo(n,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;archive.writestr(info,raw)
    with zipfile.ZipFile(DEST/'independent-R-controls.zip') as archive:
        assert len(archive.infolist())==len(files)==len({e.filename for e in archive.infolist()})
        assert all(archive.read(n)==raw for n,raw in files.items())
    print(json.dumps(dict(status='frozen-independent-published-method-R-controls',fixture=pin((DEST/'independent-R-controls.zip').read_bytes()),cases=16,gene_rows=192,original_FUMA_runtime=False)))


if __name__=='__main__':main()
