// Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
// Original XCF/HTSlib joins with exact BCF record-wire identities and resource hashes.
// Development capture only: no native record linkage, admission or phasing certificate.
#define _DECLARE_TOOLBOX_HERE
#include <utils/xcf.h>
#include <openssl/evp.h>
#include <fstream>
#include <sstream>
#include <iomanip>
#include <cstring>
#include <array>
#include <sys/stat.h>

class digest {
    EVP_MD_CTX* context;
public:
    digest():context(EVP_MD_CTX_new()){if(!context||EVP_DigestInit_ex(context,EVP_sha256(),nullptr)!=1)throw std::runtime_error("SHA256 unavailable");}
    ~digest(){EVP_MD_CTX_free(context);}
    void update(const void* data,size_t length){if(length&&EVP_DigestUpdate(context,data,length)!=1)throw std::runtime_error("SHA256 update failed");}
    std::string finish(){unsigned char result[EVP_MAX_MD_SIZE];unsigned length=0;if(EVP_DigestFinal_ex(context,result,&length)!=1||length!=32)throw std::runtime_error("SHA256 final failed");std::ostringstream s;for(unsigned i=0;i<length;i++)s<<std::hex<<std::setw(2)<<std::setfill('0')<<unsigned(result[i]);return s.str();}
};
static void little(uint8_t* p,uint32_t value){for(int i=0;i<4;i++)p[i]=(value>>(8*i))&255;}
static std::string wire(bcf1_t* record){
    if(record->shared.l>UINT32_MAX-24||record->indiv.l>UINT32_MAX||record->pos<0||record->pos>INT32_MAX||record->rlen<1||record->rlen>INT32_MAX)throw std::runtime_error("BCF record outside verified wire domain");
    uint8_t fixed[32];uint32_t quality;std::memcpy(&quality,&record->qual,4);
    little(fixed,record->shared.l+24);little(fixed+4,record->indiv.l);little(fixed+8,record->rid);little(fixed+12,record->pos);
    little(fixed+16,record->rlen);little(fixed+20,quality);little(fixed+24,uint32_t(record->n_info)|(uint32_t(record->n_allele)<<16));
    little(fixed+28,uint32_t(record->n_sample)|(uint32_t(record->n_fmt)<<24));
    digest hash;hash.update(fixed,32);hash.update(record->shared.s,record->shared.l);hash.update(record->indiv.s,record->indiv.l);return hash.finish();
}
static std::string encoded(const std::string& value){
    if(value.size()>INT32_MAX)throw std::runtime_error("Text exceeds Base64 capacity");
    std::string result(4*((value.size()+2)/3),'\0');if(!value.empty())EVP_EncodeBlock(reinterpret_cast<unsigned char*>(&result[0]),reinterpret_cast<const unsigned char*>(value.data()),value.size());return result;
}
struct resource{std::string kind,path,hash;uint64_t size;};
static resource fingerprint(std::string kind,std::string path){
    struct stat before,after;if(stat(path.c_str(),&before)||!S_ISREG(before.st_mode))throw std::runtime_error("Require regular resource: "+path);
    std::ifstream file(path,std::ios::binary);if(!file)throw std::runtime_error("Cannot open resource: "+path);
    digest hash;std::array<char,65536> buffer;uint64_t length=0;
    while(file){file.read(buffer.data(),buffer.size());auto count=file.gcount();if(count>0){hash.update(buffer.data(),count);length+=count;}}
    if(!file.eof()||stat(path.c_str(),&after)||before.st_ino!=after.st_ino||before.st_dev!=after.st_dev||before.st_size!=after.st_size||before.st_mtim.tv_sec!=after.st_mtim.tv_sec||before.st_mtim.tv_nsec!=after.st_mtim.tv_nsec||length!=uint64_t(before.st_size))throw std::runtime_error("Resource changed or incomplete: "+path);
    return {kind,path,hash.finish(),length};
}
static void intervals(std::ofstream& output,char group,std::string region,bool target){
    bool exclude=target&&!region.empty()&&region[0]=='^';if(exclude)region=region.substr(1);
    output<<"Q\t"<<group<<'\t'<<region.empty()<<'\t'<<exclude<<'\n';if(region.empty())return;
    bcf_sr_regions_t* parsed=bcf_sr_regions_init(region.c_str(),0,0,1,-2);if(!parsed)throw std::runtime_error("Cannot parse original region/targets");
    while(bcf_sr_regions_next(parsed)==0)output<<"I\t"<<group<<'\t'<<encoded(parsed->seq_names[parsed->iseq])<<'\t'<<parsed->start<<'\t'<<parsed->end<<'\n';
    bcf_sr_regions_destroy(parsed);
}
int main(int argc,char** argv){
    if(argc<4||argc>6||std::ifstream(argv[2]).good())return 2;
    try{
        std::string region(argv[1]);std::ofstream out(argv[2]);if(!out)return 3;int files=argc-3;
        std::vector<std::vector<resource>> resources(files);bool gap=false;
        for(int f=0;f<files;f++){
            std::string name(argv[3+f]);htsFile* file=bcf_open(name.c_str(),"r");if(!file)throw std::runtime_error("Cannot open BCF header");
            bcf_hdr_t* header=bcf_hdr_read(file);if(!header)throw std::runtime_error("Cannot read BCF header");
            bool seek=bcf_hdr_idinfo_exists(header,BCF_HL_INFO,bcf_hdr_id2int(header,BCF_DT_ID,"SEEK"));int samples=bcf_hdr_nsamples(header);
            bcf_hdr_destroy(header);if(bcf_close(file))throw std::runtime_error("Cannot close header reader");
            if(seek&&samples)throw std::runtime_error("BINARY_WITH_HEADER_SAMPLES: original XCF format does not admit this combination");
            bool empty=!seek&&!samples;if(empty)gap=true;else if(gap)throw std::runtime_error("VOID_HEADER_GAP: skip dependent phasing; supply nonempty appropriate inputs or a separately verified upstream fix");
            resources[f].push_back(fingerprint("B",name));
            if(std::ifstream(name+".csi").good())resources[f].push_back(fingerprint("I",name+".csi"));
            if(seek){std::string stem=helper_tools::get_name_from_vcf(name);resources[f].push_back(fingerprint("D",stem+".bin"));resources[f].push_back(fingerprint("M",stem+".fam"));}
        }
        out<<"V\tOMICS-SHAPEIT-JOIN-WIRE-1\t"<<files<<'\n';intervals(out,'R',region,false);intervals(out,'T',region,true);
        for(int f=0;f<files;f++)for(auto& item:resources[f])out<<"F\tB\t"<<f<<'\t'<<item.kind<<'\t'<<encoded(item.path)<<'\t'<<item.hash<<'\t'<<item.size<<'\n';
        xcf_reader joined(region,1);for(int f=0;f<files;f++)joined.addFile(argv[3+f]);
        for(int f=0;f<files;f++){
            int physical=bcf_hdr_nsamples(joined.sync_reader->readers[f].header);std::vector<std::string> names;joined.getSamples(f,names);
            out<<"N\t"<<f<<'\t'<<joined.typeFile(f)<<'\t'<<physical<<'\t'<<names.size()<<'\n';
            for(size_t s=0;s<names.size();s++)out<<"S\t"<<f<<'\t'<<s<<'\t'<<encoded(names[s])<<'\n';
        }
        out<<"H\n";out.flush();uint64_t events=0;std::vector<uint64_t> visits(files,0);
        while(joined.nextRecord()){
            bcf1_t* first=nullptr;bcf_hdr_t* firstHeader=nullptr;
            for(int f=0;f<files&&!first;f++)if(bcf_sr_has_line(joined.sync_reader,f)){first=bcf_sr_get_line(joined.sync_reader,f);firstHeader=joined.sync_reader->readers[f].header;}
            if(!first)throw std::runtime_error("Original join returned an empty physical event");
            out<<"J\t"<<events<<'\t'<<encoded(bcf_hdr_id2name(firstHeader,first->rid))<<'\t'<<first->pos+1;
            for(int f=0;f<files;f++){
                bool present=bcf_sr_has_line(joined.sync_reader,f);if(present)visits[f]++;
                out<<'\t'<<(present?wire(bcf_sr_get_line(joined.sync_reader,f)):"-")<<'\t'<<joined.hasRecord(f)<<'\t'<<joined.typeRecord(f)<<'\t'<<joined.getAC(f)<<'\t'<<joined.getAN(f);
            }
            out<<'\n';events++;
        }
        joined.close();out<<"E\t"<<events;for(auto count:visits)out<<'\t'<<count;out<<'\n';
        for(int f=0;f<files;f++)for(auto& before:resources[f]){
            auto after=fingerprint(before.kind,before.path);if(after.hash!=before.hash||after.size!=before.size)throw std::runtime_error("Resource changed during original join");
            out<<"F\tA\t"<<f<<'\t'<<after.kind<<'\t'<<encoded(after.path)<<'\t'<<after.hash<<'\t'<<after.size<<'\n';
        }
        out<<"Z\n";out.flush();return out.good()?0:4;
    }catch(const std::exception& error){std::cerr<<"JOIN-WIRE-FAILURE: "<<error.what()<<'\n';return 5;}
}
