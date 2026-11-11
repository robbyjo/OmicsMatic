# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Apply one current-runner metadata delta without changing papers, roles or scientific sources."""
import argparse,json,subprocess,sys,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from curate_shapeit_uses import inverse
from signac_scan_evidence import unpack,byte_pin
from cellphonedb_effective_catalog import HISTORY

def refresh_manifest(data,before):
    hashes={p.name:pin(p)['sha256'] for p in data.iterdir() if p.is_file() and p.name!='SHA256SUMS'};order=[r.split('  ',1)[1] for r in before.decode().splitlines()];assert len(order)==len(set(order)) and set(order)<=set(hashes);order += sorted(set(hashes)-set(order));(data/'SHA256SUMS').write_bytes(''.join(hashes[n]+'  '+n+'\n' for n in order).encode())

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/cellphonedb-runtime') and not out.exists();out.mkdir(parents=True);data=ROOT/'docs/literature/omics-2023-2026';before={n:pin(data/n) for n in ['papers.json','summary.json','cellphonedb-role-coverage.json','cellphonedb-source-audit.json']};reviews=pin(ROOT/'tools/literature/reviewed-uses.json');inventory=(data/'tool-inventory.json').read_bytes();manifest=(data/'SHA256SUMS').read_bytes();(out/'before-tool-inventory.json').write_bytes(inventory);(out/'before-SHA256SUMS').write_bytes(manifest)
    try:
        from cellphonedb_hash_seed_evidence import verify
        runner=verify(emit=False);path=ROOT/'tools/literature/render_review.py';raw=path.read_bytes();(out/'before-render_review.py').write_bytes(raw);assert raw.count(b'from cellphonedb_catalog import ')==8;updated=raw.replace(b'from cellphonedb_catalog import ',b'from cellphonedb_effective_catalog import ');row=inverse(raw,updated);row['path']=path.relative_to(ROOT).as_posix();descriptor=data/'cellphonedb-effective-render-shared-source-compatibility.json';assert not descriptor.exists();save(descriptor,dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',tool_id='cellphonedb',scope='Only redirect metadata imports to the separately validated effective-startup runner provider. No scientific-source exception.',paths=[row]));path.write_bytes(updated)
        readme=ROOT/'README.md';raw=readme.read_bytes();(out/'before-README.md').write_bytes(raw);marker=b'## License';assert raw.count(marker)==1;addition=b'The [current CellPhoneDB runner guide](docs/literature/CELLPHONEDB_HASH_SEED.md)\nuses revision2 with effective Python hash-seed startup and a checked startup\nreceipt. Use the explicit V2 development classes. The earlier guide and runs\nremain historical records; their ignored hash-seed option is not upgraded.\nThe canonical executable is unchanged.\n\n';readme.write_bytes(raw.replace(marker,addition+marker));history=[]
        for name in HISTORY:
            proof=json.loads((data/name.replace('-support.zip','-validation.json')).read_bytes());history.append(dict(archive=name,archive_pin=pin(data/name),document=byte_pin(unpack(data/name,proof)['current-source/README.md'])))
        save(data/'cellphonedb-effective-root-readme-evolution.json',dict(schema_version=1,status='passed-exact-README-documentation-evolution',path='README.md',after=pin(readme),historical_documents=history,scientific_source_exception_added=False))
        command=[sys.executable,'-B',str(path)]
        with (out/'render.stdout').open('xb') as first,(out/'render.stderr').open('xb') as second:run=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=first,stderr=second,timeout=1200,creationflags=subprocess.CREATE_NO_WINDOW)
        save(out/'render.process.json',dict(command=command,exit_code=run.returncode));assert run.returncode==0,'Inspect retained renderer logs; preserve this attempt';readme=data/'README.md';readme.write_bytes(readme.read_bytes().replace(b'\r\n',b'\n'));old={t['id']:t for t in json.loads(inventory)};current={t['id']:t for t in json.loads((data/'tool-inventory.json').read_bytes())};assert set(old)==set(current) and {n for n in old if old[n]!=current[n]}=={'cellphonedb'} and current['cellphonedb']['runner_revision']==2 and current['cellphonedb']['development_candidate']==runner['artifact']['path'];assert all(pin(data/n)==v for n,v in before.items()) and pin(ROOT/'tools/literature/reviewed-uses.json')==reviews;refresh_manifest(data,manifest);save(out/'COMPLETED.json',dict(status='passed-one-CellPhoneDB-effective-startup-catalog-row-delta',changed_tool_rows=['cellphonedb'],unchanged_tool_rows=394,papers_and_roles_unchanged=before,reviews_unchanged=reviews,artifact=runner['artifact'],new_scoped_integrations=85,including_existing_adapters=88,missing=251,whole_goal_complete=False));print('Rendered corrected runner: one row changed;394 other tools,250 papers and980 roles preserved')
    except Exception as error:save(out/'FAILED.json',dict(status='FAIL2_EFFECTIVE_CELLPHONEDB_CATALOG_RENDER',error=repr(error)));(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8',newline='\n');raise

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact metadata-only checks')
    main()
