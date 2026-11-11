// Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
// Calls the original common-stage scanner and its actual synchronized XCF reader.
#define _DECLARE_TOOLBOX_HERE
#include <io/genotype_reader/genotype_reader_header.h>
#include <utils/xcf.h>
#include <fstream>
#include <cstring>
#include <iomanip>

static uint32_t bits(float value) { uint32_t raw; std::memcpy(&raw, &value, 4); return raw; }
static void stringFields(std::ostream& out, bcf_hdr_t* header, bcf1_t* record) {
    bcf_unpack(record, BCF_UN_ALL);
    out << bcf_hdr_id2name(header, record->rid) << '\t' << record->pos+1 << '\t' << record->rlen << '\t' << record->d.id << '\t' << record->d.allele[0] << '\t';
    for (int i=1; i<record->n_allele; i++) { if (i>1) out << ','; out << record->d.allele[i]; }
}
static int probe(const char* filename, int file, std::ofstream& out) {
    htsFile* input=bcf_open(filename,"r"); if (!input) return 10;
    bcf_hdr_t* header=bcf_hdr_read(input); bcf1_t* record=bcf_init();
    if (!header || !record) return 11;
    int32_t *ac=nullptr,*an=nullptr; int nac=0,nan=0,status; uint64_t ordinal=0;
    while ((status=bcf_read(input,header,record))==0) {
        int ca=bcf_get_info_int32(header,record,"AC",&ac,&nac),cn=bcf_get_info_int32(header,record,"AN",&an,&nan);
        out << "B\t" << file << '\t' << ordinal++ << '\t' << bcf_hdr_nsamples(header) << '\t'; stringFields(out,header,record);
        out << '\t' << ca << '\t'; if(ca>0) out << ac[0]; else out << '-';
        out << '\t' << cn << '\t'; if(cn>0) out << an[0]; else out << '-';
        out << '\t' << (bcf_get_fmt(header,record,"GT")!=nullptr) << '\n';
    }
    free(ac);free(an);bcf_destroy(record);bcf_hdr_destroy(header);
    if(bcf_close(input)!=0 || status!=-1) return 12;
    out << "F\t" << file << '\t' << ordinal << '\n';out.flush();return out.good()?0:13;
}
int main(int argc,char** argv) {
    if(argc!=8 || std::ifstream(argv[7]).good()) return 2;
    bool reference=std::string(argv[2])!="-",scaffold=std::string(argv[3])!="-";
    std::vector<std::string> names{argv[1]}; if(reference) names.push_back(argv[2]);if(scaffold) names.push_back(argv[3]);
    std::ofstream out(argv[7]);if(!out) return 3;
    out << "P\t" << reference << '\t' << scaffold << '\t' << argv[4] << '\t' << argv[5] << '\t' << argv[6] << '\n';out.flush();
    for(size_t i=0;i<names.size();i++){int status=probe(names[i].c_str(),i,out);if(status)return status;}
    haplotype_set H;genotype_set G;variant_map V;genotype_reader reader(H,G,V);
    reader.setMainFilename(argv[1]);reader.setRegion(argv[4]);
    if(reference)reader.addReferenceFilename(argv[2]);if(scaffold)reader.addScaffoldFilename(argv[3]);
    if(std::string(argv[5])=="1")reader.setFilterSNP();
    if(std::string(argv[6])!="default")reader.setFilterMAF(std::strtod(argv[6],nullptr));
    reader.scanGenotypes();
    out << "H\t" << reader.n_main_samples << '\t' << reader.n_ref_samples << '\t' << reader.variant_mask.size() << '\t' << reader.n_variants << '\t' << std::hex << bits(reader.filter_min_maf) << std::dec << '\n';
    xcf_reader joined(argv[4],1);for(auto& name:names)joined.addFile(name);
    for(size_t f=0;f<names.size();f++)out << "N\t" << f << '\t' << joined.getSamples(f) << '\n';
    uint64_t ordinal=0;
    while(joined.nextRecord()) {
        if(ordinal>=reader.variant_mask.size())return 4;
        out << "J\t" << ordinal << '\t' << reader.variant_mask[ordinal];
        for(size_t f=0;f<names.size();f++) {
            bool physical=bcf_sr_has_line(joined.sync_reader,f);
            out << '\t' << physical << '\t' << joined.hasRecord(f) << '\t' << joined.typeRecord(f) << '\t' << joined.getAC(f) << '\t' << joined.getAN(f);
        }
        bool any=false;for(size_t f=0;f<names.size();f++)any=any||joined.hasRecord(f);
        if(any) out << '\t' << joined.chr << '\t' << joined.pos << '\t' << joined.rsid << '\t' << joined.ref << '\t' << joined.alt;
        else out << "\t.\t0\t.\t.\t.";
        out << '\n';
        for(size_t f=0;f<names.size();f++)if(bcf_sr_has_line(joined.sync_reader,f)) {
            out << "R\t" << ordinal << '\t' << f << '\t';
            stringFields(out,joined.sync_reader->readers[f].header,bcf_sr_get_line(joined.sync_reader,f));out << '\n';
        }
        ordinal++;
    }
    joined.close();if(ordinal!=reader.variant_mask.size())return 5;
    for(size_t i=0;i<V.vec_pos.size();i++) {
        auto* v=V.vec_pos[i];out << "K\t" << i << '\t' << v->chr << '\t' << v->bp << '\t' << v->id << '\t' << v->ref << '\t' << v->alt << '\n';
    }
    out << "E\t" << ordinal << '\t' << V.vec_pos.size() << '\n';out.flush();return out.good()?0:6;
}
