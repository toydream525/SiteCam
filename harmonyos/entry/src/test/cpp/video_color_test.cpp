#include "../../main/cpp/video_color.h"
#include <cmath>
#include <iostream>
#include <stdexcept>

static void near(float actual,float expected,const char* label){if(std::abs(actual-expected)>0.012f)throw std::runtime_error(label);}
static void sample(VideoColor color,float y,float u,float v,float r,float g,float b){
 const auto transform=yuvTransform(color);const float l=(y/255-transform.yOffset)*transform.yScale,cb=u/255-transform.chromaOffset,cr=v/255-transform.chromaOffset;
 near(l+transform.redV*cr,r,"red mismatch");near(l+transform.greenU*cb+transform.greenV*cr,g,"green mismatch");near(l+transform.blueU*cb,b,"blue mismatch");
}
int main(){
 for(int matrix:{1,5,6})for(int range:{0,1}){
  VideoColor color{range,matrix,1,matrix};sample(color,range?0:16,128,128,0,0,0);sample(color,range?255:235,128,128,1,1,1);
 }
 sample({0,6,1,6},81,90,240,1,0,0);sample({0,1,1,1},63,102,240,1,0,0);
 sample({1,6,1,6},76,85,255,1,0,0);sample({1,1,1,1},54,99,255,1,0,0);
 const auto hd=resolveVideoColor({},1920,1080),sd=resolveVideoColor({},720,480);
 if(hd.matrix!=1||sd.matrix!=6)throw std::runtime_error("unspecified matrix fallback mismatch");
 bool hdrRejected=false;try{resolveVideoColor({0,9,16,9},1920,1080);}catch(const std::exception&){hdrRejected=true;}
 if(!hdrRejected)throw std::runtime_error("HDR was silently treated as SDR");
 std::cout<<"video color fixtures passed\n";
}
