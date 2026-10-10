#pragma once
#include <stdexcept>

// H.273 values used by the media format keys. Keep this calculation independent
// of the codec so range/matrix fixtures can exercise the actual shader inputs.
struct VideoColor {
 int range=0,primaries=2,transfer=2,matrix=2;
};
struct YuvTransform {
 float yOffset,yScale,chromaOffset,redV,greenU,greenV,blueU;
};
inline VideoColor resolveVideoColor(VideoColor value,int width,int height){
 if(value.matrix==2)value.matrix=(width>=1280||height>576)?1:6;
 if(value.primaries==2)value.primaries=value.matrix;
 if(value.transfer==2)value.transfer=1;
 value.range=value.range?1:0;
 if(value.matrix!=1&&value.matrix!=5&&value.matrix!=6)throw std::runtime_error("暂不支持此视频的色彩矩阵，原片已保留");
 if(value.primaries!=1&&value.primaries!=5&&value.primaries!=6)throw std::runtime_error("暂不支持此视频的色域，原片已保留");
 if(value.transfer!=1&&value.transfer!=6&&!(value.transfer==13&&value.primaries==1))throw std::runtime_error("暂不支持此视频的传递特性，原片已保留");
 return value;
}
inline YuvTransform yuvTransform(const VideoColor& value){
 const float kr=value.matrix==1?0.2126f:0.299f,kb=value.matrix==1?0.0722f:0.114f,kg=1-kr-kb;
 const float scale=value.range?1.0f:255.0f/224.0f;
 return {value.range?0.0f:16.0f/255.0f,value.range?1.0f:255.0f/219.0f,128.0f/255.0f,
         2*(1-kr)*scale,-2*kb*(1-kb)/kg*scale,-2*kr*(1-kr)/kg*scale,2*(1-kb)*scale};
}
