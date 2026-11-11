/* Unchanged-original RSEM read-flag driver; GPL-3.0-or-later. */
#include "SingleRead.h"
#include "SingleReadQ.h"
#include "PairedEndRead.h"
#include "PairedEndReadQ.h"
#include <iostream>
#include <string>

bool verbose=false;
int main(){
    std::string id,a,b;int type,seed,poly;
    while(std::cin>>id>>type>>seed>>poly>>a>>b){
        bool low=false;
        if(type==0){SingleRead read(id,a);read.calc_lq(poly!=0,seed);low=read.isLowQuality();}
        else if(type==1){SingleReadQ read(id,a,std::string(a.size(),'I'));read.calc_lq(poly!=0,seed);low=read.isLowQuality();}
        else if(type==2){PairedEndRead read(SingleRead(id,a),SingleRead(id,b));read.calc_lq(poly!=0,seed);low=read.isLowQuality();}
        else if(type==3){PairedEndReadQ read(SingleReadQ(id,a,std::string(a.size(),'I')),SingleReadQ(id,b,std::string(b.size(),'I')));read.calc_lq(poly!=0,seed);low=read.isLowQuality();}
        else return 2;
        std::cout<<id<<'\t'<<(low?1:0)<<'\n';
    }
    return 0;
}
