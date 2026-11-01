// Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
// Complete synthetic diploid cohort/reference/scaffold inputs; no study data.
#include <htslib/vcf.h>
#include <fstream>
#include <string>
#include <vector>
int main(int argc,char** argv){
    if(argc!=6||std::ifstream(argv[1]).good())return 2;
    std::string role(argv[2]),mode(argv[5]);int samples=std::stoi(argv[3]),mainSamples=std::stoi(argv[4]);
    if(samples<1||mainSamples<1||(role!="target"&&role!="reference"&&role!="scaffold"))return 3;
    htsFile* file=bcf_open(argv[1],"wb");bcf_hdr_t* header=bcf_hdr_init("w");if(!file||!header)return 4;
    for(auto line:{"##contig=<ID=20,length=1000000>","##INFO=<ID=AC,Number=A,Type=Integer,Description=Observed_ALT_count>",
            "##INFO=<ID=AN,Number=1,Type=Integer,Description=Observed_allele_count>","##INFO=<ID=END,Number=1,Type=Integer,Description=Span_end>",
            "##FORMAT=<ID=GT,Number=1,Type=String,Description=Genotype>","##FORMAT=<ID=DP,Number=1,Type=Integer,Description=Depth>"})if(bcf_hdr_append(header,line)<0)return 5;
    for(int i=0;i<samples;i++){
        int sample=role=="scaffold"?(i*3)%mainSamples:i;
        std::string name=(role=="reference"?"R":role=="scaffold"&&mode=="unused-scaffold"?"X":"M")+std::to_string(sample);
        if(bcf_hdr_add_sample(header,name.c_str())<0)return 6;
    }
    if(bcf_hdr_add_sample(header,nullptr)<0||bcf_hdr_write(file,header)<0)return 7;
    for(int v=1;v<=131;v++){
        if((v==124&&role=="target")||(v==125&&role=="reference")||(v==126&&role!="scaffold")||(v>=130&&mode!="duplicates"))continue;
        bcf1_t* row=bcf_init();row->rid=0;row->pos=(v>=130?128500:v*1000)-1;
        // Duplicate controls must stay sorted after the ordinary 129000 position.
        if(v>=130)row->pos=130000-1;
        row->qual=40.25f;
        std::string id=v>=130?"identical-id":"v"+std::to_string(v);
        std::string alleles=v==121?"AG,A":v==122?"A,<DEL>":v==123?"A,C,G":"A,C";
        if(bcf_update_id(header,row,id.c_str())<0||bcf_update_alleles_str(header,row,alleles.c_str())<0)return 8;
        if(v==122){int32_t end=v*1000+90;if(bcf_update_info_int32(header,row,"END",&end,1)<0)return 9;}
        std::vector<int32_t> gt,depth(samples,17);int32_t ac=0,an=0;
        for(int i=0;i<samples;i++){
            int sample=role=="scaffold"?(i*3)%mainSamples:i;int identity=role=="reference"?sample+101:sample;
            bool a0=((identity*13+v/5+v%3)%7)<3,a1=((identity*17+v/7+v%5)%11)<5;
            if(v==30)a0=a1=false;
            if(v==127&&role!="reference"){a0=false;a1=sample==0;}
            bool phased=role!="target";
            int32_t g0=phased?bcf_gt_phased(a0):bcf_gt_unphased(a0),g1=phased?bcf_gt_phased(a1):bcf_gt_unphased(a1);
            if(role=="target"&&(v+11*i)%17==0)g0=g1=bcf_gt_missing;
            if(role=="target"&&v==128&&i==0){g0=bcf_gt_unphased(1);g1=bcf_gt_missing;}
            if(role=="target"&&v==129&&i==0&&mode=="wire-departures"){g0=1;g1=bcf_gt_unphased(1);}
            if(role=="reference"&&v==10&&i==0&&mode=="missing-reference")g0=bcf_gt_missing;
            if(role=="reference"&&v==10&&i==0&&mode=="unphased-reference")g1=bcf_gt_unphased(a1);
            gt.push_back(g0);gt.push_back(g1);
            for(int32_t g:{g0,g1})if(!bcf_gt_is_missing(g)){an++;ac+=bcf_gt_allele(g)==1;}
        }
        int32_t counts[]={ac,0};int number=v==123?2:1;
        if(bcf_update_info_int32(header,row,"AC",counts,number)<0||bcf_update_info_int32(header,row,"AN",&an,1)<0||bcf_update_format_int32(header,row,"DP",depth.data(),samples)<0||bcf_update_genotypes(header,row,gt.data(),gt.size())<0||bcf_write(file,header,row)<0)return 10;
        bcf_destroy(row);
    }
    bcf_hdr_destroy(header);return bcf_close(file)==0?0:11;
}
