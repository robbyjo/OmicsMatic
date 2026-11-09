/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
 * Observes unchanged original MIT-licensed hifiasm tables; no scientific body is patched. */
#include <iostream>
#include <cstdint>
#define main original_hifiasm_main
#include "main.cpp"
#undef main
#include "Trio.cpp"
int main(int argc,char **argv){
    if(argc!=2)return 2;init_aux_table();auto h=yak_ch_restore_core(nullptr,argv[1],YAK_LOAD_ALL);if(!h)return 2;uint64_t total=0,sum=0,xors=0,counts[1024]={};int occupied=0,maximum=0;
    for(int bucket=0;bucket<(1<<h->pre);bucket++){auto g=h->h[bucket].h;int n=0;for(khint_t i=0;i<kh_end(g);i++)if(kh_exist(g,i)){uint64_t key=kh_key(g,i),hash=(key>>YAK_COUNTER_BITS)<<h->pre|bucket;total++;n++;sum+=hash;xors^=hash;counts[key&YAK_MAX_COUNT]++;}if(n)occupied++;if(n>maximum)maximum=n;}
    std::cout<<h->k<<'\t'<<h->pre<<'\t'<<total<<'\t'<<occupied<<'\t'<<maximum<<'\t'<<sum<<'\t'<<xors<<'\n';for(int i=0;i<1024;i++){if(i)std::cout<<'\t';std::cout<<counts[i];}std::cout<<'\n';yak_ch_destroy(h);return 0;
}
