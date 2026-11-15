// Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
// Calls unchanged original classes and exports complete public quantification state.
// Link to the original translation units, excluding only its command-line main.o.
#include <iostream>
#include <iomanip>
#include <fstream>
#include <memory>
#include <string>
#include <map>
#include <set>
#include <vector>
#include <unordered_map>
#include <random>
#include <chrono>
#include <algorithm>
#include <api/BamReader.h>
#include <api/BamAlignment.h>
#include "Global.h"
#include "Exceptions.h"
#include "TimeUtils.h"
#include "bstring.h"
#include "TextParser.h"
#include "Sequence.h"
#include "ReadFactory.h"

Global *Global::s_instance = 0; // Same singleton definition as the original main.cpp.

template<typename T>
void row(const std::string& kind,const std::string& sample,const std::string& chr,
         const std::string& gene,const std::string& transcript,int ordinal,std::shared_ptr<T> node,
         unsigned int exon=0,unsigned int intron=0,unsigned int unique=0,unsigned int uniqueExon=0,unsigned int uniqueIntron=0) {
    ngs::SPtrSampleData c=nullptr;
    try { c=node->getData().getSampleData(sample); } catch (exceptions::NotFoundException) { }
    std::cout<<kind<<'\t'<<sample<<'\t'<<chr<<'\t'<<gene<<'\t'<<transcript<<'\t'<<ordinal
        <<'\t'<<node->getType()<<'\t'<<node->getStrand()<<'\t'<<node->getStart()<<'\t'<<node->getEnd()
        <<'\t'<<node->getLength()<<'\t'<<exon<<'\t'<<intron<<'\t'<<unique<<'\t'<<uniqueExon<<'\t'<<uniqueIntron
        <<'\t'<<node->isProcessed()<<'\t'<<(c!=nullptr)
        <<'\t'<<(c?c->getReads():0)<<'\t'<<(c?c->getTPM():0)
        <<'\t'<<(c?c->getExonReads():0)<<'\t'<<(c?c->getExonTPM():0)
        <<'\t'<<(c?c->getIntronReads():0)<<'\t'<<(c?c->getIntronTPM():0)
        <<'\t'<<(c?c->getUniqueReads():0)<<'\t'<<(c?c->getUniqueTPM():0)
        <<'\t'<<(c?c->getUniqueExonReads():0)<<'\t'<<(c?c->getUniqueExonTPM():0)
        <<'\t'<<(c?c->getUniqueIntronReads():0)<<'\t'<<(c?c->getUniqueIntronTPM():0)
        <<'\t'<<(c?c->getBridgesReads():0)<<'\t'<<(c?c->getBridgesTPM():0)<<'\n';
}
int main(int argc,char**argv) {
    if(argc<7) return 64;
    ngs::ReadFactory f; auto& genome=f.getGenomeFactory(); genome.setIntronCutOff(std::stoi(argv[2]));
    std::set<std::string> features={"exon"}; std::unordered_map<std::string,std::string> create={{"exon","intron"}};
    genome.processGTFFile(argv[1],"gene_id","transcript_id",features,create);
    std::cout<<std::setprecision(17);
    std::vector<std::string> names;
    for(int a=6;a<argc;a++) {
        std::string arg=argv[a]; auto p=arg.find('='); if(p==std::string::npos)return 65;
        std::string sample=arg.substr(0,p); names.push_back(sample); f.getSamples().push_back(sample);
        int count=f.processReadsFromBAM(arg.substr(p+1),sample,std::stoi(argv[5])!=0,std::stoi(argv[3]),std::stoi(argv[4]));
        std::cout<<"#sample\t"<<sample<<'\t'<<count<<'\n';
    }
    for(auto sample:names) for(auto chr:genome.getChrOrder()) for(auto gene:genome.findChromosome(chr)->getGenes()) {
        row("gene",sample,chr,gene->getId(),"-",0,gene,gene->getExonLength(),gene->getIntronLength(),gene->getUniqueLength(),gene->getUniqueExonLength(),gene->getUniqueIntronLength());
        int n=0; for(auto feature:gene->getFeatures()) row("gene-feature",sample,chr,gene->getId(),"-",++n,feature);
        n=0; for(auto feature:gene->getUniqueFeatures()) row("gene-unique",sample,chr,gene->getId(),"-",++n,feature);
        for(auto transcript:gene->getIsoforms()) {
            row("transcript",sample,chr,gene->getId(),transcript->getId(),0,transcript,transcript->getExonLength(),transcript->getIntronLength());
            n=0; for(auto feature:transcript->getFeatures()) row("transcript-feature",sample,chr,gene->getId(),transcript->getId(),++n,feature);
        }
    }
    f.printResults(names.size()==1,true,true);
}
