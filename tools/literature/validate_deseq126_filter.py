# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Compare full native default filtering to original DESeq2 1.26.0 and all 50 BH families."""
import argparse, json, shutil, subprocess, traceback
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from validate_nfcore_engine import BASE, DISTRO, JAVA, JAVA25, linux, wsl


def main():
    parser = argparse.ArgumentParser(description=__doc__); parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--lowess-controls', type=Path, required=True); parser.add_argument('--model-intake', type=Path, required=True)
    args = parser.parse_args(); out = args.out.resolve(); assert out.is_relative_to(BASE) and not out.exists(); out.mkdir()
    sources = [ROOT / 'src/main/java/org/jlinalg/loess/RLowess.java', ROOT / 'src/main/java/org/jlinalg/sequence/Deseq2IndependentFiltering.java', ROOT / 'tools/literature/java/Deseq2FilterControl.java', Path(__file__)]
    source_pins = {p.relative_to(ROOT).as_posix(): pin(p) for p in sources}; state = dict(status='FAIL2_NATIVE_DESEQ126_FILTER_CONTROLS', new_tool_credit=0, full_nfcore_adapter=False)
    try:
        lowess = json.loads((args.lowess_controls.resolve() / 'COMPLETED.json').read_bytes())
        assert lowess['status'] == 'PASS_NATIVE_LOWESS_AGAINST_ORIGINAL_R362_ONLY' and lowess['actual_complete_deseq126_filter_curve']
        assert all(pin(ROOT / n) == v for n, v in lowess['source_pins'].items())
        for name in source_pins:
            target = out / 'sources' / name; target.parent.mkdir(parents=True, exist_ok=True); shutil.copyfile(ROOT / name, target)
        model = args.model_intake.resolve(); assert json.loads((model / 'process.json').read_bytes())['exit_code'] == 0
        model_pin = pin(model / 'original-dds-rld.RData')
        assert model_pin == dict(bytes=620018, sha256='693a2f95d345a9f3f52724c82db708181c65a71ad5abf7744a21cb607f2289ac')
        shutil.copyfile(model / 'original-dds-rld.RData', out / 'original-dds-rld.RData')

        def run(label, command, timeout=180):
            command = list(map(str, command))
            with (out / (label + '.stdout')).open('xb') as so, (out / (label + '.stderr')).open('xb') as se:
                result = subprocess.run(command, stdin=subprocess.DEVNULL, stdout=so, stderr=se, timeout=timeout)
            save(out / (label + '.process.json'), dict(command=command, exit_code=result.returncode)); assert result.returncode == 0, (label, (out / (label + '.stderr')).read_bytes()[-3500:])

        script = r'''args <- commandArgs(trailingOnly=TRUE)
library(DESeq2)
stopifnot(as.character(getRversion())=="3.6.2",as.character(packageVersion("DESeq2"))=="1.26.0")
root <- args[1]; e <- new.env();load(file.path(root,"original-dds-rld.RData"),envir=e)
actual <- results(e$dds,contrast=c("condition","SPT5_T0","SPT5_T15"))
cases <- list(
 complete_original_1472=list(bm=actual$baseMean,p=actual$pvalue,alpha=0.1),
 at_most_ten_rejections=list(bm=seq_len(100),p=seq(0.0001,1,length.out=100),alpha=0.1),
 adaptive_many_rejections=list(bm=seq_len(300),p=c(rep(0.8,150),seq(0.00001,0.2,length.out=150)),alpha=0.1),
 all_zero_means=list(bm=rep(0,20),p=rep(NA_real_,20),alpha=0.1),
 mostly_zero_means=list(bm=c(rep(0,97),1,2,3),p=c(rep(NA_real_,97),0.001,0.2,0.01),alpha=0.1),
 mixed_missing=list(bm=seq_len(15),p=c(NA_real_,NaN,seq(0.0001,0.01,length.out=13)),alpha=0.1),
 strict_alpha_equal=list(bm=rep(1,40),p=rep(0.1,40),alpha=0.1),
 strict_alpha_just_below=list(bm=rep(1,40),p=rep(0.1-.Machine$double.eps*0.1,40),alpha=0.1),
 tied_base_means=list(bm=rep(c(0,1,10,100),each=30),p=c(rep(NA_real_,30),seq(0.5,0.9,length.out=30),seq(0.001,0.05,length.out=30),seq(0.00001,0.02,length.out=30)),alpha=0.1),
 singleton=list(bm=2,p=0.05,alpha=0.1),
 all_missing=list(bm=seq_len(15),p=rep(c(NA_real_,NaN,NA_real_),5),alpha=0.1),
 extreme_probabilities=list(bm=seq_len(100),p=c(0,10^seq(-320,-1,length.out=99)),alpha=1e-12))
fmt <- function(x) {
 ifelse(is.nan(x),"NaN",ifelse(is.na(x),"NA",sprintf("%.17g",x)))
}
for (name in names(cases)) {
 c <- cases[[name]]; directory <- file.path(root,name);dir.create(directory)
 res <- DESeqResults(S4Vectors::DataFrame(baseMean=c$bm,log2FoldChange=rep(0,length(c$bm)),lfcSE=rep(1,length(c$bm)),stat=rep(0,length(c$bm)),pvalue=c$p,padj=rep(NA_real_,length(c$bm))))
 S4Vectors::mcols(res) <- S4Vectors::DataFrame(type=rep("results",6),description=rep("",6))
 result <- DESeq2:::pvalueAdjustment(res,independentFiltering=TRUE,alpha=c$alpha,pAdjustMethod="BH")
 m <- metadata(result); theta <- m$filterNumRej$theta; cutoffs <- quantile(c$bm,theta)
 families <- genefilter::filtered_p(filter=c$bm,test=c$p,theta=theta,method="BH")
 threshold <- "NOT_CALCULATED"
 if (max(m$filterNumRej$numRej)>10) {
  residual <- m$filterNumRej$numRej[m$filterNumRej$numRej>0]-m$lo.fit$y[m$filterNumRej$numRej>0]
  threshold <- fmt(max(m$lo.fit$y)-sqrt(mean(residual^2)))
 }
 write.table(data.frame(base_mean=fmt(c$bm),pvalue=fmt(c$p)),file.path(directory,"input.tsv"),sep="\t",quote=FALSE,row.names=FALSE)
 writeLines(fmt(c$alpha),file.path(directory,"alpha.txt"))
 write.table(data.frame(theta=fmt(theta),cutoff=fmt(cutoffs),rejections=m$filterNumRej$numRej,smoothed=fmt(m$lo.fit$y)),file.path(directory,"curve.tsv"),sep="\t",quote=FALSE,row.names=FALSE)
 write.table(data.frame(index=match(m$filterTheta,theta)-1L,theta=fmt(m$filterTheta),cutoff=fmt(m$filterThreshold),threshold=threshold),file.path(directory,"selected.tsv"),sep="\t",quote=FALSE,row.names=FALSE)
 write.table(data.frame(adjusted=fmt(result$padj)),file.path(directory,"adjusted.tsv"),sep="\t",quote=FALSE,row.names=FALSE)
 text <- matrix(fmt(families),nrow=nrow(families),ncol=ncol(families))
 write.table(text,file.path(directory,"all-families.tsv"),sep="\t",quote=FALSE,row.names=FALSE,col.names=paste0("theta",seq_len(50)))
}
writeLines(names(cases),file.path(root,"cases.txt"))
writeLines(capture.output(sessionInfo()),file.path(root,"sessionInfo.txt"))
writeLines("PASS_ORIGINAL_DESEQ126_DEFAULT_FILTER_FIXTURES")
'''
        (out / 'original-controls.R').write_text(script, encoding='utf8', newline='\n')
        image = '/var/tmp/omicsmatic-nfcore-images-1/nfcore-chipseq-1.2.1.sif'
        expected_image = lowess['original_image']
        def image_pin():
            return json.loads(wsl('/usr/bin/python3', '-c', 'import hashlib,json,sys;from pathlib import Path;p=Path(sys.argv[1]);print(json.dumps(dict(bytes=p.stat().st_size,sha256=hashlib.file_digest(p.open("rb"),"sha256").hexdigest())))', image, timeout=120))
        assert image_pin() == expected_image
        run('original-deseq126', ['wsl.exe', '-d', DISTRO, '--exec', '/var/tmp/omicsmatic-nfcore-apptainer-1/bin/apptainer', 'exec', '--userns', '--no-home', '--pid', '-B', linux(ROOT) + ':' + linux(ROOT), image, 'Rscript', '--vanilla', linux(out / 'original-controls.R'), linux(out)])
        cases = (out / 'cases.txt').read_text(encoding='utf8').splitlines(); assert len(cases) == 12
        predecessor = ROOT / 'docs/literature/nfcore-consensus-development/nfcore-consensus-javac21.jar'
        proof = json.loads((predecessor.parent / 'VALIDATION.json').read_bytes()); assert pin(predecessor) == proof['artifacts'][predecessor.name]
        native = {}; platforms = []
        for compiler, tools in [('21', JAVA), ('25', JAVA25)]:
            classes = out / ('classes' + compiler); classes.mkdir()
            run('javac' + compiler, [tools / 'javac.exe', '--release', '17', '-encoding', 'UTF-8', '-Xlint:all', '-Werror', '-cp', predecessor, '-d', classes, *sources[:3]])
            for platform, vm in [('windows21', JAVA / 'java.exe'), ('windows25', JAVA25 / 'java.exe')]:
                for label, argument in [('portable', '--portable'), ('original', out)]:
                    run(compiler + '-' + platform + '-' + label, [vm, '-cp', str(classes) + ';' + str(predecessor), 'org.jlinalg.sequence.Deseq2FilterControl', argument])
                platforms.append(dict(compiler=compiler, platform=platform, complete_populations=12))
            for label, argument in [('portable', '--portable'), ('original', linux(out))]:
                run(compiler + '-linux21-' + label, ['wsl.exe', '-d', DISTRO, '--exec', '/usr/bin/java', '-cp', linux(classes) + ':' + linux(predecessor), 'org.jlinalg.sequence.Deseq2FilterControl', argument])
            platforms.append(dict(compiler=compiler, platform='linux21', complete_populations=12))
            native[compiler] = {p.relative_to(classes).as_posix(): pin(p) for p in classes.rglob('*.class') if not p.name.startswith('Deseq2FilterControl')}
        assert image_pin() == expected_image and pin(out / 'original-dds-rld.RData') == model_pin
        assert all(pin(ROOT / n) == v for n, v in source_pins.items())
        state.update(status='PASS_COMPLETE_NATIVE_DESEQ126_DEFAULT_FILTERING_ONLY', cases=cases, platforms=platforms,
            source_pins=source_pins, native_classes=native, lowess_controls=pin(args.lowess_controls.resolve() / 'COMPLETED.json'),
            original_image=expected_image, original_fitted_object=model_pin, actual_hypotheses=1472, quantile_BH_families_per_population=50, missing_and_NaN_preserved=True,
            strict_source_alpha_boundary_preserved=True, fitted_model_admitted=False)
    except Exception as error:
        state.update(cause=repr(error), traceback=traceback.format_exc())
        (out / 'WARNINGS.md').write_text('# FAIL2 NO INDEPENDENT-FILTER RESULT\n\n' + repr(error) + '\n\nRequired action: preserve original full p-value/base-mean populations and all failed controls. Inspect the named family, quantile, missing-value, LOWESS or strict-boundary discrepancy. Do not repair inputs, substitute filtering alpha or select a smaller population. Rerun corrected controls to a new path.\n', encoding='utf8')
    save(out / 'COMPLETED.json', state); print(state['status'], state.get('cause', ''), flush=True)
    return 0 if state['status'].startswith('PASS_') else 2


if __name__ == '__main__':
    if not __debug__: raise RuntimeError('Never disable complete original filter-family, source or strict-boundary checks')
    raise SystemExit(main())
