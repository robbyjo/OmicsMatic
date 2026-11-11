// Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
// Synthetic control only: translate an existing two-contig fixture to the reverse dictionary order.
#include <htslib/vcf.h>
#include <algorithm>
#include <fstream>
#include <sstream>
#include <string>
#include <vector>
#include <tuple>
int main(int argc,char** argv){
    if(argc!=3||std::ifstream(argv[2]).good())return 2;
    htsFile* input=bcf_open(argv[1],"r");if(!input)return 3;
    bcf_hdr_t* old=bcf_hdr_read(input);if(!old)return 4;
    kstring_t formatted={0,0,nullptr};if(bcf_hdr_format(old,0,&formatted)<0)return 5;
    std::stringstream lines(std::string(formatted.s,formatted.l));std::string line,text;bool inserted=false;
    while(std::getline(lines,line)){
        if(line.rfind("##contig=",0)==0)continue;
        text+=line+"\n";
        if(!inserted&&line.rfind("##fileformat=",0)==0){text+="##contig=<ID=21,length=1000000>\n##contig=<ID=20,length=1000000>\n";inserted=true;}
    }
    free(formatted.s);bcf_hdr_t* header=bcf_hdr_init("r");std::vector<char> bytes(text.begin(),text.end());bytes.push_back(0);
    if(!inserted||bcf_hdr_parse(header,bytes.data())<0||bcf_hdr_name2id(header,"21")!=0||bcf_hdr_name2id(header,"20")!=1)return 6;
    std::vector<std::pair<size_t,bcf1_t*>> records;int status;
    for(;;){bcf1_t* record=bcf_init();status=bcf_read(input,old,record);if(status<0){bcf_destroy(record);break;}if(bcf_translate(header,old,record)<0)return 7;records.push_back({records.size(),record});}
    if(status!=-1||bcf_close(input)!=0)return 8;
    std::stable_sort(records.begin(),records.end(),[](auto& a,auto& b){return std::tie(a.second->rid,a.second->pos,a.first)<std::tie(b.second->rid,b.second->pos,b.first);});
    htsFile* output=bcf_open(argv[2],"wb");if(!output||bcf_hdr_write(output,header)<0)return 9;
    for(auto& item:records){if(bcf_write(output,header,item.second)<0)return 10;bcf_destroy(item.second);}
    bcf_hdr_destroy(old);bcf_hdr_destroy(header);return bcf_close(output)==0?0:11;
}
