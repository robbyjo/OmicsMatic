/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
 * Reference-only wrapper. Included scientific sources remain unchanged and MIT licensed. */
#include <iostream>
#include <sstream>
#include <string>
#include <vector>
#include <algorithm>
#define main original_hifiasm_main
#include "main.cpp"
#undef main
#include "Trio.cpp"

static std::string unhex(const std::string &text) {
    if(text.size()%2)throw std::runtime_error("Odd hex input");std::string result;
    for(size_t i=0;i<text.size();i+=2)result.push_back((char)std::stoul(text.substr(i,2),nullptr,16));return result;
}
static uint64_t source_hash(const std::string &sequence,int k) {
    uint64_t x[4]={0,0,0,0},mask=k<32?(1ULL<<(2*k))-1:(1ULL<<k)-1;int shift=k<32?2*(k-1):k-1;
    if(sequence.size()!=(size_t)k)throw std::runtime_error("Require one complete window");
    for(unsigned char raw:sequence){int c=seq_nt4_table[raw];if(c>=4)throw std::runtime_error("Ambiguous hash window");if(k<32){x[0]=(x[0]<<2|c)&mask;x[1]=x[1]>>2|(uint64_t)(3-c)<<shift;}else{x[0]=(x[0]<<1|(c&1))&mask;x[1]=(x[1]<<1|(c>>1))&mask;x[2]=x[2]>>1|(uint64_t)(1-(c&1))<<shift;x[3]=x[3]>>1|(uint64_t)(1-(c>>1))<<shift;}}
    return k<32?yak_hash64(x[0]<x[1]?x[0]:x[1],mask):yak_hash_long(x);
}
int main(int argc,char **argv) {
    try {
        if(argc<2)throw std::runtime_error("Require fixed reference mode");std::string mode=argv[1],line;init_aux_table();
        if(mode=="hash")while(std::getline(std::cin,line)){std::istringstream row(line);int k;std::string hex;row>>k>>hex;if(k<1||k>63)throw std::runtime_error("Unsupported k");std::cout<<source_hash(unhex(hex),k)<<'\n';}
        else if(mode=="classify-counts")while(std::getline(std::cin,line)){std::istringstream row(line);int k,sc[2],c[16];double ratio;row>>k>>ratio>>sc[0]>>sc[1];for(int &n:c)row>>n;std::cout<<tb_classify(sc,c,k,ratio)<<'\n';}
        else if(mode=="dump"){
            if(argc!=3)throw std::runtime_error("Require YAK path");auto h=yak_ch_restore_core(nullptr,argv[2],YAK_LOAD_ALL);if(!h)throw std::runtime_error("Original reader rejected YAK");std::cout<<h->k<<'\t'<<h->pre<<'\n';
            for(int bucket=0;bucket<(1<<h->pre);bucket++){auto g=h->h[bucket].h;for(khint_t i=0;i<kh_end(g);i++)if(kh_exist(g,i)){uint64_t key=kh_key(g,i),hash=(key>>YAK_COUNTER_BITS)<<h->pre|bucket;std::cout<<hash<<'\t'<<(key&YAK_MAX_COUNT)<<'\n';}}yak_ch_destroy(h);
        }
        else if(mode=="classify-read"){
            if(argc!=6)throw std::runtime_error("Require paternal,maternal,min,repeat arguments");int minimum=std::stoi(argv[4]),repeat=std::stoi(argv[5]);auto h=yak_ch_restore_core(nullptr,argv[2],YAK_LOAD_TRIOBIN1,minimum,repeat);h=yak_ch_restore_core(h,argv[3],YAK_LOAD_TRIOBIN2,minimum,repeat);if(!h)throw std::runtime_error("Original parent reader rejected input");
            while(std::getline(std::cin,line)){std::istringstream row(line);double ratio;std::string hex;row>>ratio>>hex;std::string sequence=unhex(hex);uint64_t length=sequence.size(),*sites=nullptr,ambiguous=0;for(unsigned char c:sequence)if(seq_nt6_table[c]>=4)ambiguous++;std::vector<uint8_t> packed(length/4+4);ha_compress_base(packed.data(),sequence.data(),length,&sites,ambiguous);uint8_t *data=packed.data(),label=0;All_reads reads={};reads.total_reads=1;reads.read_length=&length;reads.read_sperate=&data;reads.N_site=&sites;reads.trio_flag=&label;UC_Read recovered;init_UC_Read(&recovered);tb_buf_t buffer={};tb_shared_t shared={};shared.k=h->k;shared.ch=h;shared.ratio_thres=ratio;shared.seq=&reads;shared.bseq=&recovered;shared.buf=&buffer;tb_worker(&shared,0,0);std::cout<<(int)label<<'\t';static const char chars[]="0123456789abcdef";for(int64_t i=0;i<recovered.length;i++){unsigned char c=recovered.seq[i];std::cout<<chars[c>>4]<<chars[c&15];}std::cout<<'\n';free(sites);free(buffer.s);destory_UC_Read(&recovered);
            }yak_ch_destroy(h);
        }else throw std::runtime_error("Unknown fixed reference mode");
        return 0;
    }catch(const std::exception &error){std::cerr<<error.what()<<'\n';return 2;}
}
