from pathlib import Path
R=Path('D:/projects/JLinAlg');T=R/'build/mr-3';B=T/'build/twosamplemr-clump-1';B.mkdir(parents=True,exist_ok=True)
source=(T/'src/main/resources/org/jlinalg/omics/twosamplemr_workflow.R').read_text()
common=source[:source.index('main <- function()')]
common=common.replace('# Owned observer. The original installed namespace is never rewritten.','# Owned clumping observer. The TwoSampleMR namespace stays unchanged. Two ieugwasr functions receive audited observation prefixes; original bodies/formals are retained and restored.')
common=common.replace('tryCatch(do.call(fun,args),error=', 'tryCatch(withCallingHandlers(do.call(fun,args),error=.omics_source_error),error=')
(B/'common.R').write_text(common,encoding='utf-8',newline='\n')
print('Prepared common original-return/source-guard helpers; original workflow sources unchanged')
