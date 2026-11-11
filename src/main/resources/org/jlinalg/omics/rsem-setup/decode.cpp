/* Source-linked RSEM alignment decoder; GPL-3.0-or-later.
 * Build against the exact original runtime's unmodified bundled HTSlib.
 * The SAM header text and effective per-record dictionary remain distinct. */
#include "htslib/sam.h"
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>

int main(int argc,char** argv){
    if(argc!=4&&argc!=5){fprintf(stderr,"Usage: decode INPUT NEW.sam NEW.metadata [LOCAL_REFERENCE.fa]\n");return 2;}
    FILE* source=fopen(argv[1],"rb");if(source==NULL){fprintf(stderr,"Input must be a local readable file\n");return 2;}
    char magic[4]={0,0,0,0};size_t n=fread(magic,1,4,source);fclose(source);
    const char* reference=argc==5?argv[4]:NULL;
    if(n==4&&memcmp(magic,"CRAM",4)==0&&reference==NULL){fprintf(stderr,"Required action: supply the exact permitted local CRAM reference and its .fai;dependent decoding is skipped\n");return 3;}
    if(reference!=NULL){
        FILE* f=fopen(reference,"rb");if(f==NULL){fprintf(stderr,"Required local CRAM reference is unavailable\n");return 3;}fclose(f);
        std::string index=std::string(reference)+".fai";f=fopen(index.c_str(),"rb");if(f==NULL){fprintf(stderr,"Required local CRAM reference .fai is unavailable\n");return 3;}fclose(f);
    }
    setenv("REF_PATH","",1);setenv("REF_CACHE","",1);
    samFile* input=sam_open(argv[1],"r");if(input==NULL)return 2;
    if(reference!=NULL&&hts_set_fai_filename(input,reference)<0){sam_close(input);return 2;}
    bam_hdr_t* header=sam_hdr_read(input);if(header==NULL){sam_close(input);return 2;}
    FILE* reserved=fopen(argv[2],"wx");if(reserved==NULL){fprintf(stderr,"Canonical output already exists or cannot be created\n");bam_hdr_destroy(header);sam_close(input);return 2;}fclose(reserved);
    FILE* metadata=fopen(argv[3],"wx");if(metadata==NULL){fprintf(stderr,"Metadata output already exists or cannot be created\n");bam_hdr_destroy(header);sam_close(input);return 2;}
    samFile* output=sam_open(argv[2],"w");if(output==NULL){fclose(metadata);bam_hdr_destroy(header);sam_close(input);return 2;}
    if(sam_hdr_write(output,header)<0){fclose(metadata);sam_close(output);bam_hdr_destroy(header);sam_close(input);return 2;}
    fprintf(metadata,"rsem-decoded-records-v1\nordinal\tquery_name\tflags\treference\tposition\tread_length\tcigar\teffective_reference_length\n");
    bam1_t* b=bam_init1();unsigned long long ordinal=0;int code=0,ret;
    const char* operations="MIDNSHP=XB";
    while((ret=sam_read1(input,header,b))>=0){
        ++ordinal;
        if(sam_write1(output,header,b)<0){code=2;break;}
        const char* name=bam_get_qname(b);const char* target=b->core.tid>=0&&b->core.tid<header->n_targets?header->target_name[b->core.tid]:"*";
        unsigned long length=b->core.tid>=0&&b->core.tid<header->n_targets?header->target_len[b->core.tid]:0;
        fprintf(metadata,"%llu\t%s\t%u\t%s\t%d\t%d\t",ordinal,name,(unsigned)b->core.flag,target,b->core.pos+1,b->core.l_qseq);
        if(b->core.n_cigar==0)fputc('*',metadata);
        else{
            uint32_t* cigar=bam_get_cigar(b);
            for(unsigned j=0;j<b->core.n_cigar;j++){
                unsigned op=cigar[j]&15;if(op>=10){code=2;break;}
                fprintf(metadata,"%u%c",cigar[j]>>4,operations[op]);
            }
        }
        fprintf(metadata,"\t%lu\n",length);
        if(code!=0||ferror(metadata)){code=2;break;}
    }
    if(ret<-1){fprintf(stderr,"Original decoder failed before complete end of input\n");code=2;}
    if(sam_close(output)<0)code=2;
    if(code==0)fprintf(metadata,"END\t%llu\n",ordinal);
    if(fclose(metadata)!=0)code=2;
    bam_destroy1(b);bam_hdr_destroy(header);if(sam_close(input)<0)code=2;
    return code;
}
