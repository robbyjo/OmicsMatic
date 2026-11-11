// Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
// Synthetic scanner inputs. XCF binary files are deliberately empty: no fitting/read control is claimed.
#include <htslib/vcf.h>
#include <fstream>
#include <sstream>
#include <vector>
#include <string>

static std::vector<int32_t> integers(std::string value) {
    std::vector<int32_t> result;std::stringstream stream(value);std::string cell;
    while(std::getline(stream,cell,','))result.push_back(cell=="m"?bcf_int32_missing:cell=="e"?bcf_int32_vector_end:std::stoll(cell));
    return result;
}
int main(int argc,char** argv) {
    if(argc!=8 || std::ifstream(argv[2]).good())return 2;
    int samples=std::stoi(argv[3]);bool binary=std::string(argv[4])=="xcf";
    std::string prefix(argv[5]),acType(argv[6]),anType(argv[7]);
    std::ifstream spec(argv[1]);htsFile* output=bcf_open(argv[2],"wb");bcf_hdr_t* header=bcf_hdr_init("w");
    if(!spec || !output || !header)return 3;
    std::vector<std::string> lines={"##contig=<ID=20,length=1000000>","##contig=<ID=21,length=1000000>",
        "##FORMAT=<ID=GT,Number=1,Type=String,Description=Genotype>","##FORMAT=<ID=DP,Number=1,Type=Integer,Description=Depth>",
        "##INFO=<ID=AC,Number=.,Type="+acType+",Description=Declared_counts>","##INFO=<ID=AN,Number=.,Type="+anType+",Description=Declared_counts>",
        "##INFO=<ID=END,Number=1,Type=Integer,Description=Span_end>"};
    if(binary)lines.push_back("##INFO=<ID=SEEK,Number=4,Type=Integer,Description=Binary_record>");
    for(auto& line:lines)if(bcf_hdr_append(header,line.c_str())<0)return 4;
    if(!binary)for(int i=0;i<samples;i++){std::string name=prefix+std::to_string(i);if(bcf_hdr_add_sample(header,name.c_str())<0)return 5;}
    if(bcf_hdr_add_sample(header,nullptr)<0 || bcf_hdr_write(output,header)<0)return 6;
    if(binary) {
        std::string name(argv[2]);name=name.substr(0,name.find_last_of('.'));
        std::ofstream bytes(name+".bin",std::ios::binary);std::ofstream fam(name+".fam");
        if(!bytes || !fam)return 7;
        for(int i=0;i<samples;i++)fam << prefix << i << '\n';
    }
    std::string line;int ordinal=0;
    while(std::getline(spec,line)) {
        std::stringstream stream(line);std::vector<std::string> f;std::string token;
        while(std::getline(stream,token,'\t'))f.push_back(token);
        if(f.size()!=8)return 8;
        bcf1_t* record=bcf_init();record->rid=std::stoi(f[0]);record->pos=std::stoi(f[1])-1;
        if(bcf_update_id(header,record,f[2].c_str())<0 || bcf_update_alleles_str(header,record,f[3].c_str())<0)return 9;
        for(int c=0;c<2;c++)if(f[4+c]!="-") {
            const char* tag=c==0?"AC":"AN";std::string type=c==0?acType:anType;
            if(type=="Integer"){auto values=integers(f[4+c]);if(bcf_update_info_int32(header,record,tag,values.data(),values.size())<0)return 10;}
            else if(type=="String"){if(bcf_update_info_string(header,record,tag,f[4+c].c_str())<0)return 11;}
            else if(type=="Float"){float value=std::stof(f[4+c]);if(bcf_update_info_float(header,record,tag,&value,1)<0)return 12;}
            else return 13;
        }
        if(f[6]!="-"){int32_t end=std::stoi(f[6]);if(bcf_update_info_int32(header,record,"END",&end,1)<0)return 14;}
        int type=std::stoi(f[7]);
        if(binary){int32_t seek[]={type,0,0,0};if(bcf_update_info_int32(header,record,"SEEK",seek,4)<0)return 15;}
        else if(samples>0) {
            std::vector<int32_t> depth(samples,10);if(bcf_update_format_int32(header,record,"DP",depth.data(),samples)<0)return 16;
            if(type!=0){std::vector<int32_t> gt;for(int i=0;i<samples;i++){gt.push_back(bcf_gt_unphased(record->n_allele>1?(i+ordinal)%2:0));gt.push_back(bcf_gt_unphased(record->n_allele>1?(i+ordinal/2)%2:0));}if(bcf_update_genotypes(header,record,gt.data(),gt.size())<0)return 17;}
        }
        if(bcf_write(output,header,record)<0)return 18;bcf_destroy(record);ordinal++;
    }
    bcf_hdr_destroy(header);return bcf_close(output)==0?0:19;
}
