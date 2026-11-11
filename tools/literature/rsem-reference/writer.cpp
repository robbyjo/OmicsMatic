/* Original RSEM writer probe; GPL-3.0-or-later.
 * Compile against an unchanged pinned release. These are explicit synthetic
 * writer inputs, not fitted EM parameters or biological inference. */
#include "PairedEndQModel.h"
#include "WriteResults.h"
#include <iomanip>
#include <iostream>
#include <limits>
#include <string>

bool verbose = false;

int main(int argc, char** argv) {
    if (argc != 5) return 2;
    std::string reference(argv[1]), modelFile(argv[2]), mode(argv[3]), output(argv[4]);
    std::string seq = reference + ".seq";
    std::vector<char> seqName(seq.begin(),seq.end()); seqName.push_back(0);
    Refs refs; refs.loadRefs(&seqName[0],1);
    Transcripts transcripts; transcripts.readFrom((reference + ".ti").c_str());
    int M = transcripts.getM(); if (M != 6 || refs.getM() != M) return 3;
    PairedEndQModel model(&refs); model.read(modelFile.c_str());
    std::vector<double> eel, theta(M+1,0.0), tpm, fpkm;
    calcExpectedEffectiveLengths(M,refs,model,eel);
    bool found = false;
    const double backgrounds[] = {0.9,0.75,0.5,0.95,0.6,0.7};
    for (int k=0;k<6 && !found;k++) {
        theta.assign(M+1,0.0); theta[3]=backgrounds[k]; theta[0]=1.0-theta[3];
        double center = EPSILON * theta[3] * eel[1] / eel[3] / 1e6;
        double low=center, high=center;
        for (int step=0;step<2048 && !found;step++) {
            for (int side=0;side<2 && !found;side++) {
                theta[1]=side==0 ? low : high;
                calcExpressionValues(M,theta,eel,tpm,fpkm);
                if (tpm[1] == EPSILON) found=true;
            }
            if (!found) {low=::nextafter(low,0.0);high=::nextafter(high,std::numeric_limits<double>::infinity());}
        }
    }
    if (!found) return 4;
    double exact=theta[1];
    if (mode=="below-epsilon") theta[1]=exact*0.5;
    else if (mode=="above-epsilon") theta[1]=exact*2.0;
    else if (mode=="tiny-weighted") {theta[1]=exact*4.0;theta[2]=exact*2.0;}
    else if (mode!="equal-epsilon") return 5;
    calcExpressionValues(M,theta,eel,tpm,fpkm);
    double geneTpm=tpm[1]+tpm[2];
    if ((mode=="below-epsilon" && !(geneTpm<EPSILON)) || (mode=="equal-epsilon" && geneTpm!=EPSILON)
            || ((mode=="above-epsilon" || mode=="tiny-weighted") && !(geneTpm>EPSILON))) return 6;
    double counts[7]={2,10,12,10,0,0,0};
    writeResultsEM(M,reference.c_str(),output.c_str(),transcripts,theta,eel,counts,false);
    FILE* thetaFile=fopen((output+".theta").c_str(),"w"); if(thetaFile==NULL)return 7;
    fprintf(thetaFile,"%d\n",M+1);
    for(int line=0;line<2;line++)for(int j=0;j<=M;j++)fprintf(thetaFile,"%.15g%c",theta[j],j==M?'\n':' ');
    fclose(thetaFile);
    std::cout<<std::setprecision(17)<<mode<<'\t'<<geneTpm<<'\t'<<theta[1]<<'\t'<<theta[2]<<'\n';
    return 0;
}
