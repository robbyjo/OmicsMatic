// Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
// Inspect the actual original common-stage reader after target/reference/scaffold processing.
#define _DECLARE_TOOLBOX_HERE
#include <io/genotype_reader/genotype_reader_header.h>
#include <fstream>
#include <iomanip>
#include <cstring>
int main(int argc,char** argv){
    if(argc!=8||std::ifstream(argv[2]).good())return 2;
    haplotype_set H;genotype_set G;variant_map V;genotype_reader reader(H,G,V);
    reader.setMainFilename(argv[1]);reader.setRegion(argv[3]);reader.setThreads(1);
    if(std::string(argv[4])!="-")reader.addReferenceFilename(argv[4]);
    if(std::string(argv[5])!="-")reader.addScaffoldFilename(argv[5]);
    if(std::string(argv[6])=="1")reader.setFilterSNP();
    if(std::string(argv[7])!="default")reader.setFilterMAF(std::stof(argv[7]));
    reader.scanGenotypes();reader.allocateGenotypes();reader.readGenotypes();
    std::ofstream out(argv[2]);if(!out)return 3;
    out<<"V\tOMICS-SHAPEIT-COMMON-DATA-1\nH\t"<<reader.n_main_samples<<'\t'<<reader.n_ref_samples<<'\t'<<reader.n_variants<<'\n';
    for(size_t i=0;i<G.vecG.size();i++)out<<"S\t"<<i<<'\t'<<G.vecG[i]->name<<'\n';
    for(size_t v=0;v<V.vec_pos.size();v++){
        auto site=V.vec_pos[v];out<<"R\t"<<v<<'\t'<<site->chr<<'\t'<<site->bp<<'\t'<<site->id<<'\t'<<site->ref<<'\t'<<site->alt<<'\t'<<site->cref<<'\t'<<site->calt<<'\t'<<site->cmis<<'\n';
        for(size_t i=0;i<G.vecG.size();i++)out<<"G\t"<<v<<'\t'<<i<<'\t'<<unsigned((G.vecG[i]->Variants[v/2]>>(4*(v%2)))&15)<<'\n';
        for(size_t h=0;h<2*reader.n_ref_samples;h++)out<<"A\t"<<v<<'\t'<<h<<'\t'<<H.H_opt_hap.get(h+2*reader.n_main_samples,v)<<'\n';
    }
    out<<"C";for(auto n:reader.n_genotypes)out<<'\t'<<n;for(auto n:reader.n_alleles)out<<'\t'<<n;out<<'\n';
    out<<"E\t"<<reader.n_variants<<'\t'<<reader.n_variants*reader.n_main_samples<<'\t'<<reader.n_variants*2*reader.n_ref_samples<<'\n';out.flush();return out.good()?0:4;
}
