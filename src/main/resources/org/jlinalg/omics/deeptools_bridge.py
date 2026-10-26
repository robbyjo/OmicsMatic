"""Call pinned original deepTools functions with explicit completion markers."""
import argparse
import hashlib
import importlib
import importlib.metadata
import json
import os
from pathlib import Path
import sys
import subprocess
import traceback

MODULES = {'bamCoverage', 'bigwigCompare', 'computeMatrix', 'plotProfile',
           'plotHeatmap', 'multiBamSummary', 'plotCorrelation', 'alignmentSieve'}

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--report', type=Path, required=True)
    parser.add_argument('--module', choices=sorted(MODULES))
    parser.add_argument('arguments', nargs=argparse.REMAINDER)
    args = parser.parse_args()
    report = dict(status='initializing-dependencies', python=sys.version,
                  executable=sys.executable, prefix=sys.prefix,
                  mpl_cache_declared=os.environ.get('MPLCONFIGDIR'))
    if os.environ.get('MPLCONFIGDIR'):
        report['font_cache_sha256']={path.name:hashlib.sha256(path.read_bytes()).hexdigest()
                                    for path in Path(os.environ['MPLCONFIGDIR']).glob('fontlist*.json')}
    save = lambda: args.report.write_text(json.dumps(report, indent=2)+'\n', encoding='utf-8')
    save()
    try:
        os.environ['MPLBACKEND'] = 'Agg'
        # Required initialization order in the tested private Cygwin profile.
        # No upstream deepTools or dependency scientific source is modified.
        report['initialization_steps'] = []
        for name in ['numpy','scipy.stats','pysam','pyBigWig','matplotlib',
                     'matplotlib._c_internal_utils','PIL.Image','matplotlib._path',
                     'kiwisolver','matplotlib.ft2font','matplotlib.backends._backend_agg',
                     'contourpy','matplotlib._image','matplotlib.pyplot','deeptools']:
            report['initializing_module'] = name
            report['loaded_native_paths'] = {key:str(module.__file__) for key,module in sys.modules.items()
                                             if getattr(module,'__file__',None) and str(module.__file__).endswith(('.dll','.so','.pyd'))}
            if sys.platform.startswith('cygwin'):
                report['python_core_dll_maps'] = '\n'.join(line for line in Path('/proc/self/maps').read_text().splitlines() if 'libpython3.9.dll' in line and '00000000' in line)
            save()
            module=importlib.import_module(name)
            report['initialization_steps'].append({'module':name,'path':str(module.__file__)})
            save()
        matplotlib=sys.modules['matplotlib']
        deeptools=sys.modules['deeptools']
        matplotlib.use('Agg', force=True)
        matplotlib.rcParams['font.family'] = ['DejaVu Sans']
        matplotlib.rcParams['text.usetex'] = False
        packages = ['deeptools','numpy','scipy','pysam','pyBigWig','matplotlib',
                    'contourpy','kiwisolver','Pillow','py2bit','deeptoolsintervals','numpydoc','plotly']
        report['versions'] = {name: importlib.metadata.version(name) for name in packages}
        report['executable_realpath'] = str(Path(sys.executable).resolve())
        report['executable_sha256'] = hashlib.sha256(Path(sys.executable).resolve().read_bytes()).hexdigest()
        source = Path(deeptools.__file__).resolve().parent
        report['source_sha256'] = {path.name: hashlib.sha256(path.read_bytes()).hexdigest() for path in source.glob('*.py')}
        report['loaded_module_paths'] = {name: str(module.__file__) for name,module in sys.modules.items()
                                         if getattr(module,'__file__',None) and (name in packages or name.startswith('matplotlib.') or name.startswith('PIL.'))}
        if args.module is None:
            if args.arguments:
                raise ValueError('Analysis arguments require --module')
            for unused in range(4):
                process=subprocess.run([sys.executable,'-c','import numpy as np; print(np.linalg.eigvalsh([[2.,1.],[1.,2.]]).tolist())'],capture_output=True,text=True,timeout=60)
                if process.returncode or process.stdout.strip()!='[1.0, 3.0]':
                    raise RuntimeError('Loaded-runtime subprocess/eigenvalue probe failed: '+process.stdout+process.stderr)
            report['four_loaded_runtime_forks_and_eigenvalues']='passed'
            report['status'] = 'probe-completed'
        else:
            report.update(status='calling-original', module=args.module)
            save()
            arguments = args.arguments[1:] if args.arguments[:1] == ['--'] else args.arguments
            sys.argv = [args.module, *arguments]
            try:
                importlib.import_module('deeptools.'+args.module).main()
            except SystemExit as exception:
                if exception.code not in (None,0):
                    raise
            report['status'] = 'original-call-completed'
        save()
        print(report['status'], flush=True)
        return 0
    except BaseException as exception:
        report.update(status='failed', error=traceback.format_exc())
        save()
        print(report['error'], file=sys.stderr, flush=True)
        return int(exception.code) if isinstance(exception,SystemExit) and isinstance(exception.code,int) and exception.code else 2

if __name__ == '__main__':
    sys.exit(main())
