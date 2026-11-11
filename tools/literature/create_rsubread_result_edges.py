# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Count fresh strand/identifier/empty-junction/extra-attribute/BAM controls in both unmodified original editions."""
import argparse, json, os, subprocess, traceback, zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save, environment
from setup_rsubread_reference import inventory


def run(command, home, name, env=None):
    with (home / (name+'.stdout')).open('xb') as first, (home / (name+'.stderr')).open('xb') as second:
        result = subprocess.run(command, cwd=ROOT, env=env, stdin=subprocess.DEVNULL, stdout=first, stderr=second,
                                timeout=240, creationflags=subprocess.CREATE_NO_WINDOW if os.name=='nt' else 0)
    save(home/(name+'.process.json'), dict(command=command, exit_code=result.returncode))
    assert result.returncode==0, (name,'Preserve complete original/codec diagnostics and all partial outputs')


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve()
    assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/rsubread-runtime');out.mkdir(parents=True)
    state=dict(status='FAIL2_RSUBREAD_FRESH_RESULT_EDGE_CONTROLS',analysis_admitted=False,implementation_credit=False,full_adapter_available=False,whole_goal_complete=False)
    try:
        from rsubread_matrix_evidence import verify
        prior,_=verify(False);assert prior['native_matrix_component_available']
        base=ROOT/'build/literature-review/rsubread-runtime';inputs=out/'inputs';inputs.mkdir();original=base/'original-controls-3/inputs'
        for name in ['single.sam','paired.sam','genes.gtf','genome.fasta']:(inputs/name).write_bytes((original/name).read_bytes())
        (inputs/'no-junction.sam').write_bytes(b''.join(line for line in (inputs/'single.sam').read_bytes().splitlines(keepends=True) if not line.startswith(b'junction\t')))
        header=b'GeneID\tChr\tStart\tEnd\tStrand\n'
        (inputs/'strand-union.saf').write_bytes(header+b'mixed\tchr1\t100\t149\t+\nmixed\tchr1\t125\t174\t+\nmixed\tchr1\t100\t149\t-\nmixed\tchr2\t100\t149\t+\n')
        (inputs/'numeric.saf').write_bytes(header+b'123\tchr1\t101\t150\t+\n456\tchr2\t101\t200\t+\n')
        (inputs/'logical.saf').write_bytes(header+b'TRUE\tchr1\t101\t150\t+\nFALSE\tchr2\t101\t200\t+\nNA\tchr1\t601\t650\t+\n')
        codec=ROOT/'tools/literature/java/org/jlinalg/cli/RsubreadBamFixture.java';jdk=Path('C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin')
        parent=base/'native-build-1/omicsmatic-0.3.7-rsubread-native-development.jar';assert parent.is_file()
        with zipfile.ZipFile(parent) as z:assert 'htsjdk/samtools/SamReaderFactory.class' in z.namelist()
        parentpin=pin(parent);classes=out/'codec-classes';classes.mkdir()
        run([str(jdk/'javac.exe'),'--release','17','-Xlint:all','-Werror','-g','-encoding','UTF-8','-cp',str(parent),'-d',str(classes),str(codec)],out,'codec-compile')
        for name in ['single','paired']:run([str(jdk/'java.exe'),'-Xmx128m','-cp',str(classes)+os.pathsep+str(parent),'org.jlinalg.cli.RsubreadBamFixture',str(inputs/(name+'.sam')),str(inputs/(name+'.bam'))],out,'codec-'+name)
        assert pin(parent)==parentpin;inputpins={q.relative_to(inputs).as_posix():pin(q) for q in inputs.iterdir()}
        script=ROOT/'tools/literature/collect_rsubread_result_edges.R';wire=ROOT/'src/main/resources/org/jlinalg/omics/rsubread_result_wire.R'
        profiles=[('2260','reference-setup-3','2.26.0','4.6.1',Path('C:/Program Files/R/R-4.6.1/bin/Rscript.exe'),base/'reference-intake-1/source/unpacked/R/featureCounts.R'),
                  ('2232-R441','r441-reported-build-1','2.23.2','4.4.1',base/'r441-setup-1/R441/root/bin/x64/Rscript.exe',base/'reported-source-2/source/R/featureCounts.R')]
        refs={}
        for name,setup,version,rversion,rscript,source in profiles:
            receipt=json.loads((base/setup/'COMPLETED.json').read_bytes());lib=ROOT/receipt['private_library']['path']
            assert inventory(lib)==receipt['private_library']['files'];home=out/name;home.mkdir();env=environment()
            if rversion=='4.4.1':env['R_HOME']=rscript.parents[2].as_posix()
            run([str(rscript),'--vanilla',str(script),str(lib),str(home),str(inputs),version,rversion,str(source),str(wire)],out,name,env)
            assert (home/'R-status.txt').read_text().strip()=='PASS9_FRESH_ORIGINAL_RSUBREAD_RESULT_EDGE_AND_BAM_CONTROLS'
            assert inventory(lib)==receipt['private_library']['files']
            refs[name]=dict(setup_receipt=dict(path=(base/setup/'COMPLETED.json').relative_to(ROOT).as_posix(),**pin(base/setup/'COMPLETED.json')),source=dict(path=source.relative_to(ROOT).as_posix(),**pin(source)))
        assert all(pin(inputs/n)==v for n,v in inputpins.items())
        state.update(status='passed-eighteen-fresh-original-Rsubread-result-edge-and-BAM-controls-only',original_cases=18,references=refs,input_pins=inputpins,
                     codec_parent=dict(path=parent.relative_to(ROOT).as_posix(),**parentpin),codec_role='Fixture BAM encoding and complete decoded SAM/BAM field/order equality only; not part of the native value API',
                     source_pins={q.relative_to(ROOT).as_posix():pin(q) for q in [Path(__file__).resolve(),script,wire,codec]},namespace_patched=False,original_scientific_source_unchanged=True)
    except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('passed-') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original-package, input and complete-record guards')
    raise SystemExit(main())
