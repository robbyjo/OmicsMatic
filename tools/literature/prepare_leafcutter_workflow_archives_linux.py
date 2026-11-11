# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Copy existing exact cached dependency archives into a NEW flat read-only-input cache; install/download nothing and preserve every original cache file."""
import argparse,json,shutil,sys,traceback
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_hail_reference_linux import ROOT,pin,save


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();assert sys.platform=='linux' and out.is_relative_to(ROOT/'build/literature-review/leafcutter-runtime') and not out.exists();out.mkdir();state=dict(status='SKIP3_EXACT_EXISTING_DEPENDENCY_ARCHIVES_UNAVAILABLE',software_installed=False,software_downloaded=False,original_cache_modified=False,archives=[])
    try:
        setup=ROOT/'build/literature-review/leafcutter-runtime/r-python-setup-1';records=json.loads((setup/'complete-conda-package-records.json').read_bytes());locks=json.loads((ROOT/'src/main/resources/org/jlinalg/omics/leafcutter-workflow/setup/dependency-locks.json').read_bytes())['packages'];by_name={r['name']:r for r in records.values()};cached=setup/'private-cache/mamba-root/pkgs';fallback={}
        for file in cached.rglob('*'):
            if file.is_file() and (file.name.endswith('.conda') or file.name.endswith('.tar.bz2')):fallback.setdefault(file.name,[]).append(file)
        for row in locks:
            original=by_name[row['name']];wanted={k:row[k] for k in ['bytes','sha256']};candidates=[Path(original['package_tarball_full_path']),Path(original['package_tarball_full_path']).with_name(row['filename']),*fallback.get(row['filename'],[])];source=next((f for f in candidates if f.is_file() and pin(f)==wanted),None);assert source is not None,'Exact cached archive missing; use permitted download in setup: '+row['filename'];dest=out/row['filename'];shutil.copyfile(source,dest);assert pin(source)==pin(dest)==wanted;state['archives'].append(dict(filename=row['filename'],source=str(source),**wanted));print(len(state['archives']),'/',len(locks),'verified cached copies',flush=True)
        assert len(state['archives'])==239;state['status']='PASS_EXACT_FLAT_CACHED_LEAFCUTTER_DEPENDENCY_ARCHIVES_ONLY'
    except Exception as failure:state['error']=repr(failure);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8',newline='\n')
    state['required_action']='Keep all original cache files and partial flat copies. Missing cached exact archives require the explicitly permitted setup --allow-download flag; do not substitute a package/hash or treat cache preparation as analysis admission.';save(out/'COMPLETED.json',state);print(state['status']);return 0 if state['status'].startswith('PASS_') else 3


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact archive/input checks')
    raise SystemExit(main())
