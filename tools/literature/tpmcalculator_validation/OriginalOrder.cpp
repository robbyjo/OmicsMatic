// Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
// Independent original comparison runtime's actual standard-library map and hash.
#include <iostream>
#include <string>
#include <unordered_map>
std::string key(int i) {
    if(i==0)return "";
    if(i%17==0)return "gene α"+std::to_string(i);
    if(i%19==0)return "ENSG00000"+std::to_string(i);
    if(i%23==0)return "RNA/漢字/"+std::to_string(i);
    if(i%31==0)return std::string(64,'A')+std::to_string(i);
    return "transcript:"+std::to_string(i);
}
int main() {
    std::unordered_map<std::string,int> map;size_t buckets=map.bucket_count();
    for(int i=0;i<100000;i++) {
        map.emplace(key(i),i);
        if(map.bucket_count()!=buckets) { buckets=map.bucket_count();std::cout<<"#buckets\t"<<i+1<<'\t'<<buckets<<'\n'; }
    }
    for(auto p:map)std::cout<<p.second<<'\t'<<std::hash<std::string>{}(p.first)<<'\n';
}
