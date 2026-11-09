// Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
// Test instrumentation only: call original input/preparation/regression public APIs.
#include <algorithm>
#include <cmath>
#include <fstream>
#include <iomanip>
#include <iostream>
#include <map>
#include <string>
#include <vector>
#include "global.h"
#include "regression.h"
#include "mds.h"
#include "chisquaredistr.h"

bool readInputFile(global &,std::ofstream &);
bool calcLambdaGetMAF(int,global &,std::ofstream &);
void getPCfromInputFiles(global &,std::ofstream &,matrixD &);

template<class T> void vectorField(std::ostream &out,const std::vector<T> &v) {
    for(std::size_t i=0;i<v.size();i++) {if(i)out<<',';out<<v[i];}
}

int main(int argc,char **argv) {
    if(argc!=3)return 2;
    global g;std::ifstream setup(argv[1]);std::string key,value;std::vector<std::string> chosen;
    std::map<std::string,std::vector<std::string>*> aliases={
        {"MARKERNAME",&g.name_markerList},{"EA",&g.name_eaList},{"NEA",&g.name_neaList},{"EAF",&g.name_eafList},
        {"BETA",&g.name_betaList},{"SE",&g.name_seList},{"OR",&g.name_orList},{"OR_95L",&g.name_or95lList},
        {"OR_95U",&g.name_or95uList},{"CHROMOSOME",&g.name_chrList},{"POSITION",&g.name_posList},{"N",&g.name_nList}};
    while(setup>>key>>value) {
        if(key=="filelist")g.inputFile=value;
        else if(key=="axes")g.pcCount=std::stoi(value);
        else if(key=="quantitative")g.qt=value=="1";
        else if(key=="precalculated")g.precalculatedPC=value=="1";
        else if(key=="noalleles")g.noalleles=value=="1";
        else if(key=="inputgc")g.gc=value=="1";
        else if(key=="outputgc")g.gco=value=="1";
        else if(key=="standards") {if(value=="1")g.pushStandards();}
        else if(key=="filter")g.filterList.push_back(value);
        else if(key=="selected")chosen.push_back(value);
        else if(key=="alias") {std::string name;if(!(setup>>name)||!aliases.count(value))return 2;aliases.at(value)->push_back(name);}
        else return 2;
    }
    if(!setup.eof())return 2;
    std::string home=argv[2];std::ofstream log(home+"/original-api.log");log<<std::setprecision(17);g.debugMode=true;g.threshold=1;g.parseFilters();
    if(!readInputFile(g,log)||g.fileList.empty())return 2;
    for(std::size_t i=0;i<g.fileList.size();i++){g.cohorts.push_back(cohort());if(!calcLambdaGetMAF(i,g,log))return 2;}
    std::ofstream lambdas(home+"/lambdas.tsv");lambdas<<std::setprecision(17)<<"cohort\tlambda\n";
    for(std::size_t i=0;i<g.cohorts.size();i++)lambdas<<i<<'\t'<<g.cohorts[i].lambda<<'\n';
    matrixD pcs(g.fileList.size(),g.pcCount);
    if(g.precalculatedPC)getPCfromInputFiles(g,log,pcs);
    else {
        if(chosen.empty())return 2;matrixD frequencies(chosen.size(),g.fileList.size());
        for(std::size_t r=0;r<chosen.size();r++) {
            if(!g.markerNum.count(chosen[r]))return 2;auto f=g.markerList[g.markerNum.at(chosen[r])-1].getEAF();
            for(std::size_t c=0;c<f.size();c++)frequencies.put(r,c,f[c]);
        }
        matrixD distances=frequencies.distance();pcs=calculateMDS(distances,g.pcCount);
    }
    std::ofstream coordinates(home+"/coordinates.tsv");coordinates<<std::setprecision(17);
    for(int i=0;i<pcs.getRows();i++){for(int j=0;j<pcs.getCols();j++){if(j)coordinates<<'\t';coordinates<<pcs.get(i,j);}coordinates<<'\n';}
    std::ofstream prepared(home+"/prepared.tsv");prepared<<std::setprecision(17)<<"marker\tfrequencies\teffects\tweights\n";
    std::ofstream fits(home+"/fits.tsv");fits<<std::setprecision(17)<<"marker\tcoefficients\terrors\tcovariance\traw_association\tancestry\tresidual\tlnBF\n";
    std::vector<double> association;
    for(auto &marker:g.markerList) {
        int n=marker.getCohortCount();arrayD y(n),w(n);matrixD x(n,g.pcCount+1);marker.getBeta(&y);marker.getSE(&w,g.gc,g.cohorts);marker.getPC(&x,pcs,g.pcCount);
        prepared<<marker.getName()<<'\t';vectorField(prepared,marker.getEAF());prepared<<'\t';vectorField(prepared,y.toVector());prepared<<'\t';vectorField(prepared,w.toVector());prepared<<'\n';
        if(n<g.pcCount+3)continue;
        lr fit;if(!fit.lr_w(&y,&x,&w))return 2;double a=std::abs(fit.TSS0-fit.RSS);association.push_back(a);
        fits<<marker.getName()<<'\t';vectorField(fits,fit.Cstat->toVector());fits<<'\t';vectorField(fits,fit.SECstat->toVector());fits<<'\t';
        for(int i=0;i<=g.pcCount;i++)for(int j=0;j<=g.pcCount;j++){if(i||j)fits<<',';fits<<fit.covariance->get(i,j);}
        fits<<'\t'<<a<<'\t'<<std::abs(fit.TSS-fit.RSS)<<'\t'<<std::abs(fit.RSS)<<'\t'<<(a-(g.pcCount+1)*std::log(n))/2<<'\n';
    }
    double outputLambda=1;
    if(g.gco) {
        if(association.size()<2)return 2;std::sort(association.begin(),association.end());std::size_t h=association.size()/2;
        double median=association.size()%2 ? association[h-1] : (association[h-1]+association[h])/2;
        outputLambda=median/invchisquaredistribution(g.pcCount+1,.5);
    }
    std::ofstream output(home+"/output-lambda.txt");output<<std::setprecision(17)<<outputLambda<<'\n';
    return 0;
}
