nextflow.enable.dsl=2
params.outdir=null
process EMIT {
 input:
 tuple val(label),val(sequence)
 output:
 path("${label}.txt")
 script:
 """
 printf '%s\\n' '$sequence' > '${label}.txt'
 """
}
process GATHER {
 publishDir params.outdir,mode:'copy'
 input:
 path parts
 output:
 path 'result.txt'
 script:
 'cat *.txt | LC_ALL=C sort > result.txt'
}
workflow {
 EMIT(Channel.of(tuple('a','ACGT'),tuple('b','TTAA')))
 GATHER(EMIT.out.collect())
}
