/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
 * Observing wrapper; the included original MIT-licensed kseq header is unchanged. */
#include <cstdio>
#include <cstdlib>
#include <cstdint>
#include <zlib.h>
#include "kseq.h"
KSEQ_INIT(gzFile,gzread)
static void hex(const char *s,size_t n){if(n==0){std::putchar('-');return;}for(size_t i=0;i<n;i++)std::printf("%02x",(unsigned char)s[i]);}
int main(int argc,char **argv){
    if(argc!=2)return 2;gzFile file=gzopen(argv[1],"r");if(!file)return 2;kseq_t *seq=kseq_init(file);int code;
    while((code=kseq_read(seq))>=0){hex(seq->name.s,seq->name.l);std::putchar('\t');hex(seq->comment.s,seq->comment.l);std::putchar('\t');hex(seq->seq.s,seq->seq.l);std::putchar('\t');hex(seq->qual.s,seq->qual.l);std::putchar('\n');}
    std::fprintf(stderr,"kseq_terminal_status=%d\n",code);kseq_destroy(seq);int closed=gzclose(file);return code==-1&&closed==Z_OK?0:2;
}
