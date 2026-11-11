# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Validate complete native LOWESS curves against original R3.6.2, including DESeq2 filtering."""
import argparse, json, shutil, subprocess, traceback
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from validate_nfcore_engine import BASE, DISTRO, JAVA, JAVA25, linux, wsl


def main():
    parser = argparse.ArgumentParser(description=__doc__); parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--r-source', type=Path, required=True); parser.add_argument('--model-intake', type=Path, required=True)
    args = parser.parse_args(); out = args.out.resolve(); assert out.is_relative_to(BASE) and not out.exists(); out.mkdir()
    sources = [ROOT / 'src/main/java/org/jlinalg/loess/RLowess.java', ROOT / 'tools/literature/java/RLowessControl.java', Path(__file__)]
    source_pins = {p.relative_to(ROOT).as_posix(): pin(p) for p in sources}; state = dict(status='FAIL2_NATIVE_R_LOWESS_CONTROLS', new_tool_credit=0, full_nfcore_adapter=False)
    try:
        for name in source_pins:
            target = out / 'sources' / name; target.parent.mkdir(parents=True, exist_ok=True); shutil.copyfile(ROOT / name, target)
        rsource = args.r_source.resolve(); source = json.loads((rsource / 'SOURCE.json').read_bytes())
        assert source['sha256'] == 'bd65a45cddfb88f37370fbcee4ac8dd3f1aebeebe47c2f968fd9770ba2bbc954'
        for name in ['lowess.c', 'lowess.R', 'COPYING']:
            assert pin(rsource / name) == source['extracted'][name]; shutil.copyfile(rsource / name, out / name)
        save(out / 'ORIGINAL-SOURCE.json', source)
        model = args.model_intake.resolve(); assert json.loads((model / 'process.json').read_bytes())['exit_code'] == 0
        shutil.copyfile(model / 'SPT5_T0vsSPT5_T15.metadata.R', out / 'original-deseq126-metadata.R')
        shutil.copyfile(model / 'original-dds-rld.RData', out / 'original-dds-rld.RData')

        def run(label, command, timeout=120):
            command = list(map(str, command))
            with (out / (label + '.stdout')).open('xb') as so, (out / (label + '.stderr')).open('xb') as se:
                result = subprocess.run(command, stdin=subprocess.DEVNULL, stdout=so, stderr=se, timeout=timeout)
            save(out / (label + '.process.json'), dict(command=command, exit_code=result.returncode)); assert result.returncode == 0, (label, (out / (label + '.stderr')).read_bytes()[-3000:])

        script = r'''args <- commandArgs(trailingOnly=TRUE)
stopifnot(as.character(getRversion()) == "3.6.2")
root <- args[1]
ref <- new.env();eval(parse(file.path(root,"lowess.R"),keep.source=FALSE),envir=ref)
stopifnot(identical(body(ref$lowess),body(stats::lowess)),identical(formals(ref$lowess),formals(stats::lowess)))
writeLines(capture.output(sessionInfo()),file.path(root,"original-sessionInfo.txt"))
cases <- list(
 singleton=list(x=3,y=-2,f=2/3,iter=3L,delta=0),
 two_unsorted=list(x=c(2,1),y=c(1,9),f=2/3,iter=3L,delta=0),
 repeats=list(x=c(2,1,1,2,3,3,3,4),y=c(1,10,-3,4,7,8,100,5),f=0.5,iter=3L,delta=0),
 all_ties=list(x=rep(1,7),y=c(1,2,3,4,5,6,100),f=0.25,iter=3L,delta=0),
 negative_zero_ties=list(x=c(0,-0,0,1,2),y=c(1,2,3,4,5),f=0.5,iter=3L,delta=0),
 robustness_outliers=list(x=seq_len(51),y=sin(seq_len(51)/7)+seq_len(51)/20,f=0.4,iter=3L,delta=0),
 no_robustness=list(x=seq_len(51),y=sin(seq_len(51)/7)+seq_len(51)/20,f=0.4,iter=0L,delta=0),
 delta_interpolation=list(x=rev(seq(0,20,by=0.1)),y=cos(rev(seq(0,20,by=0.1))),f=0.3,iter=3L,delta=0.37),
 wide_span=list(x=seq_len(19),y=sin(seq_len(19)),f=2.5,iter=3L,delta=0),
 narrow_neighborhood=list(x=seq_len(25),y=sin(seq_len(25)),f=0.00001,iter=3L,delta=0),
 exact_line=list(x=c(1,3,4,7,11,19,25),y=2*c(1,3,4,7,11,19,25)+3,f=2/3,iter=3L,delta=0),
 constant_y=list(x=seq_len(37),y=rep(1.25,37),f=0.5,iter=3L,delta=0),
 uneven_density=list(x=c(rep(0,10),seq(0.001,0.1,length.out=101),seq(1,20,length.out=31)),y=sin(c(rep(0,10),seq(0.001,0.1,length.out=101),seq(1,20,length.out=31))),f=0.3,iter=3L,delta=0.02),
 large_offset=list(x=1e12+seq(0,100,by=0.25),y=cos(seq(0,100,by=0.25)/20),f=0.3,iter=3L,delta=0),
 tiny_y=list(x=seq_len(37),y=1e-200*(2+sin(seq_len(37))),f=0.4,iter=3L,delta=0),
 subnormal_y=list(x=seq_len(37),y=seq_len(37)*1e-320,f=0.4,iter=3L,delta=0))
cases$robustness_outliers$y[c(10,31)] <- c(50,-100)
cases$no_robustness$y[c(10,31)] <- c(50,-100)
library(DESeq2)
stopifnot(as.character(packageVersion("DESeq2")) == "1.26.0")
e <- new.env(); load(file.path(root,"original-dds-rld.RData"),envir=e)
m <- metadata(results(e$dds,contrast=c("condition","SPT5_T0","SPT5_T15")))
cases$complete_deseq126_filter_curve <- list(x=m$filterNumRej$theta,y=m$filterNumRej$numRej,f=1/5,iter=3L,delta=0.01*diff(range(m$filterNumRej$theta)))
fmt <- function(x) sprintf("%.17g",x)
for (name in names(cases)) {
 c <- cases[[name]];directory <- file.path(root,name);dir.create(directory)
 expected <- stats::lowess(c$x,c$y,f=c$f,iter=c$iter,delta=c$delta)
 if (name == "complete_deseq126_filter_curve") stopifnot(identical(expected,m$lo.fit))
 write.table(data.frame(x=fmt(c$x),y=fmt(c$y)),file.path(directory,"input.tsv"),sep="\t",quote=FALSE,row.names=FALSE)
 write.table(data.frame(x=fmt(expected$x),fitted=fmt(expected$y),source_index=order(c$x)-1L),file.path(directory,"expected.tsv"),sep="\t",quote=FALSE,row.names=FALSE)
 write.table(data.frame(span=fmt(c$f),iterations=c$iter,delta=fmt(c$delta)),file.path(directory,"options.tsv"),sep="\t",quote=FALSE,row.names=FALSE)
}
writeLines(names(cases),file.path(root,"cases.txt"))
writeLines("PASS_ORIGINAL_R362_COMPLETE_LOWESS_FIXTURES")
'''
        (out / 'original-controls.R').write_text(script, encoding='utf8', newline='\n')
        image = '/var/tmp/omicsmatic-nfcore-images-1/nfcore-chipseq-1.2.1.sif'
        expected_image = dict(bytes=1131143168, sha256='50bd3f475b82608dd305138dff30b4789973d930994a5f710a7e5dd8acbb1e38')
        def image_pin():
            return json.loads(wsl('/usr/bin/python3', '-c', 'import hashlib,json,sys;from pathlib import Path;p=Path(sys.argv[1]);print(json.dumps(dict(bytes=p.stat().st_size,sha256=hashlib.file_digest(p.open("rb"),"sha256").hexdigest())))', image, timeout=120))
        assert image_pin() == expected_image
        run('original-r362', ['wsl.exe', '-d', DISTRO, '--exec', '/var/tmp/omicsmatic-nfcore-apptainer-1/bin/apptainer', 'exec', '--userns', '--no-home', '--pid', '-B', linux(ROOT) + ':' + linux(ROOT), image, 'Rscript', '--vanilla', linux(out / 'original-controls.R'), linux(out)], timeout=180)
        cases = (out / 'cases.txt').read_text(encoding='utf8').splitlines(); assert len(cases) == 17
        platforms = []; class_pins = {}
        for compiler, tools in [('21', JAVA), ('25', JAVA25)]:
            classes = out / ('classes' + compiler); classes.mkdir()
            run('javac' + compiler, [tools / 'javac.exe', '--release', '17', '-encoding', 'UTF-8', '-Xlint:all', '-Werror', '-d', classes, *sources[:2]])
            for platform, vm in [('windows21', JAVA / 'java.exe'), ('windows25', JAVA25 / 'java.exe')]:
                for mode, argument in [('portable', '--portable'), ('original', out)]:
                    run(compiler + '-' + platform + '-' + mode, [vm, '-cp', classes, 'org.jlinalg.loess.RLowessControl', argument], timeout=180)
                platforms.append(dict(compiler=compiler, platform=platform, complete_original_cases=17))
            for mode, argument in [('portable', '--portable'), ('original', linux(out))]:
                run(compiler + '-linux21-' + mode, ['wsl.exe', '-d', DISTRO, '--exec', '/usr/bin/java', '-cp', linux(classes), 'org.jlinalg.loess.RLowessControl', argument], timeout=180)
            platforms.append(dict(compiler=compiler, platform='linux21', complete_original_cases=17))
            class_pins[compiler] = {p.relative_to(classes).as_posix(): pin(p) for p in classes.rglob('*.class') if not p.name.startswith('RLowessControl')}
        assert image_pin() == expected_image and all(pin(ROOT / n) == v for n, v in source_pins.items())
        state.update(status='PASS_NATIVE_LOWESS_AGAINST_ORIGINAL_R362_ONLY', cases=cases, platforms=platforms, source_pins=source_pins,
            original_image=expected_image, original_source=source, native_classes=class_pins,
            actual_complete_deseq126_filter_curve=True, biological_accuracy_certified=False)
    except Exception as error:
        state.update(cause=repr(error), traceback=traceback.format_exc())
        (out / 'WARNINGS.md').write_text('# FAIL2 NO LOWESS RESULT\n\n' + repr(error) + '\n\nRequired action: preserve complete original curves, software, parameters and failed controls. Inspect the named numeric/order/capacity discrepancy. Do not drop points, rescale inputs or replace source output to force agreement. Use a new control path after correction.\n', encoding='utf8')
    save(out / 'COMPLETED.json', state); print(state['status'], state.get('cause', ''), flush=True)
    return 0 if state['status'].startswith('PASS_') else 2


if __name__ == '__main__':
    if not __debug__: raise RuntimeError('Never disable complete original LOWESS source, curve or numeric checks')
    raise SystemExit(main())
