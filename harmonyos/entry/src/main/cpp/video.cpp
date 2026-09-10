#include <napi/native_api.h>
#include <multimedia/player_framework/native_avcodec_videodecoder.h>
#include <multimedia/player_framework/native_avcodec_videoencoder.h>
#include <multimedia/player_framework/native_avsource.h>
#include <multimedia/player_framework/native_avdemuxer.h>
#include <multimedia/player_framework/native_avmuxer.h>
#include <multimedia/player_framework/native_avbuffer.h>
#include <native_window/external_window.h>
#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES3/gl3.h>
#include <atomic>
#include <mutex>
#include <condition_variable>
#include <vector>
#include <string>
#include <stdexcept>
#include <chrono>
#include <fcntl.h>
#include <unistd.h>
#include <sys/stat.h>

static std::atomic<bool> busy{false}, cancelled{false};
static std::atomic<double> percent{0};
static void check(OH_AVErrCode e,const char* where){if(e!=AV_ERR_OK)throw std::runtime_error(std::string(where)+": "+std::to_string(e));}
struct Job {
 napi_async_work work{};napi_deferred deferred{};std::string source,dest,error;std::vector<uint8_t> overlay;
 int w=0,h=0,stride=0,slice=0,ow=0,oh=0,rotation=0,in=-1,out=-1,videoTrack=-1,audioTrack=-1,muxVideo=-1,muxAudio=-1;
 int64_t duration=0;OH_AVSource* src=nullptr;OH_AVDemuxer* demux=nullptr;OH_AVMuxer* mux=nullptr;
 OH_AVCodec *decoder=nullptr,*encoder=nullptr;OHNativeWindow* window=nullptr;
 std::mutex lock,inputLock;std::condition_variable cv;bool done=false,failed=false,muxStarted=false;std::atomic<bool> inputEos{false};
 EGLDisplay display=EGL_NO_DISPLAY;EGLContext context=EGL_NO_CONTEXT;EGLSurface surface=EGL_NO_SURFACE;GLuint program=0,tex[3]{};
 void fail(const std::string& text){std::lock_guard<std::mutex> guard(lock);if(!failed)error=text;failed=true;cv.notify_all();}
 void glInit(){
  display=eglGetDisplay(EGL_DEFAULT_DISPLAY);if(display==EGL_NO_DISPLAY||!eglInitialize(display,nullptr,nullptr))throw std::runtime_error("EGL 初始化失败");
  const EGLint attrs[]={EGL_SURFACE_TYPE,EGL_WINDOW_BIT,EGL_RENDERABLE_TYPE,EGL_OPENGL_ES3_BIT,EGL_RED_SIZE,8,EGL_GREEN_SIZE,8,EGL_BLUE_SIZE,8,EGL_ALPHA_SIZE,8,EGL_NONE};EGLConfig config;EGLint count;if(!eglChooseConfig(display,attrs,&config,1,&count)||!count)throw std::runtime_error("EGL 配置不可用");
  const EGLint ca[]={EGL_CONTEXT_CLIENT_VERSION,3,EGL_NONE};context=eglCreateContext(display,config,EGL_NO_CONTEXT,ca);surface=eglCreateWindowSurface(display,config,(EGLNativeWindowType)window,nullptr);if(context==EGL_NO_CONTEXT||surface==EGL_NO_SURFACE||!eglMakeCurrent(display,surface,surface,context))throw std::runtime_error("编码画布不可用");
  const char* vs="#version 300 es\nlayout(location=0) in vec2 p;out vec2 uv;void main(){gl_Position=vec4(p,0,1);uv=vec2((p.x+1.)*.5,(1.-p.y)*.5);}";
  const char* fs="#version 300 es\nprecision highp float;in vec2 uv;out vec4 c;uniform sampler2D yTex;uniform sampler2D uvTex;uniform sampler2D wm;uniform vec2 ratio;uniform int rotation;void main(){vec2 q=uv;if(rotation==90)q=vec2(uv.y,1.-uv.x);else if(rotation==180)q=1.-uv;else if(rotation==270)q=vec2(1.-uv.y,uv.x);q*=ratio;float y=(texture(yTex,q).r-16./255.)*1.164;vec2 u=texture(uvTex,q).rg-vec2(.5);vec3 rgb=vec3(y+1.596*u.y,y-.392*u.x-.813*u.y,y+2.017*u.x);vec4 m=texture(wm,uv);c=vec4(m.rgb+rgb*(1.-m.a),1.);}";
  auto shader=[](GLenum type,const char* code){GLuint s=glCreateShader(type);glShaderSource(s,1,&code,nullptr);glCompileShader(s);GLint ok;glGetShaderiv(s,GL_COMPILE_STATUS,&ok);if(!ok)throw std::runtime_error("视频合成着色器编译失败");return s;};
  GLuint v=shader(GL_VERTEX_SHADER,vs),f=shader(GL_FRAGMENT_SHADER,fs);program=glCreateProgram();glAttachShader(program,v);glAttachShader(program,f);glLinkProgram(program);glDeleteShader(v);glDeleteShader(f);GLint ok;glGetProgramiv(program,GL_LINK_STATUS,&ok);if(!ok)throw std::runtime_error("视频合成程序链接失败");glGenTextures(3,tex);glUseProgram(program);
  for(int i=0;i<3;i++){glActiveTexture(GL_TEXTURE0+i);glBindTexture(GL_TEXTURE_2D,tex[i]);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_LINEAR);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_LINEAR);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);}
  glUniform1i(glGetUniformLocation(program,"yTex"),0);glUniform1i(glGetUniformLocation(program,"uvTex"),1);glUniform1i(glGetUniformLocation(program,"wm"),2);glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA,ow,oh,0,GL_RGBA,GL_UNSIGNED_BYTE,overlay.data());
 }
 void draw(OH_AVBuffer* buffer,const OH_AVCodecBufferAttr& a){
  if(display==EGL_NO_DISPLAY)glInit();else if(!eglMakeCurrent(display,surface,surface,context))throw std::runtime_error("EGL 线程切换失败");
  const uint8_t* data=OH_AVBuffer_GetAddr(buffer);const int64_t size=OH_AVBuffer_GetCapacity(buffer);if(!data||size<int64_t(stride)*slice*3/2+a.offset)throw std::runtime_error("解码图像跨距或缓冲区不完整");data+=a.offset;
  glUseProgram(program);glPixelStorei(GL_UNPACK_ALIGNMENT,1);glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,tex[0]);glTexImage2D(GL_TEXTURE_2D,0,GL_R8,stride,slice,0,GL_RED,GL_UNSIGNED_BYTE,data);
  glActiveTexture(GL_TEXTURE1);glBindTexture(GL_TEXTURE_2D,tex[1]);glTexImage2D(GL_TEXTURE_2D,0,GL_RG8,stride/2,slice/2,0,GL_RG,GL_UNSIGNED_BYTE,data+stride*slice);
  glUniform2f(glGetUniformLocation(program,"ratio"),float(w)/stride,float(h)/slice);glUniform1i(glGetUniformLocation(program,"rotation"),rotation);glViewport(0,0,ow,oh);const GLfloat p[]={-1,-1,1,-1,-1,1,1,1};glVertexAttribPointer(0,2,GL_FLOAT,GL_FALSE,0,p);glEnableVertexAttribArray(0);glDrawArrays(GL_TRIANGLE_STRIP,0,4);
  auto presentation=(PFNEGLPRESENTATIONTIMEANDROIDPROC)eglGetProcAddress("eglPresentationTimeANDROID");if(!presentation)throw std::runtime_error("编码时间戳接口不可用");if(presentation(display,surface,a.pts*1000)!=EGL_TRUE)throw std::runtime_error("视频时间戳提交失败");if(!eglSwapBuffers(display,surface))throw std::runtime_error("视频帧提交失败");if(!eglMakeCurrent(display,EGL_NO_SURFACE,EGL_NO_SURFACE,EGL_NO_CONTEXT))throw std::runtime_error("解除编码画布失败");if(duration>0)percent.store(std::min(.95,double(a.pts)/duration));
 }
 void cleanup(){
  if(decoder){OH_VideoDecoder_Stop(decoder);OH_VideoDecoder_Destroy(decoder);decoder=nullptr;}
  if(encoder){OH_VideoEncoder_Stop(encoder);OH_VideoEncoder_Destroy(encoder);encoder=nullptr;}
  if(display!=EGL_NO_DISPLAY){eglMakeCurrent(display,surface,surface,context);if(program)glDeleteProgram(program);glDeleteTextures(3,tex);eglMakeCurrent(display,EGL_NO_SURFACE,EGL_NO_SURFACE,EGL_NO_CONTEXT);if(surface!=EGL_NO_SURFACE)eglDestroySurface(display,surface);if(context!=EGL_NO_CONTEXT)eglDestroyContext(display,context);eglTerminate(display);}
  if(window)OH_NativeWindow_DestroyNativeWindow(window);
  if(mux){if(muxStarted)OH_AVMuxer_Stop(mux);OH_AVMuxer_Destroy(mux);}if(demux)OH_AVDemuxer_Destroy(demux);if(src)OH_AVSource_Destroy(src);if(in>=0)close(in);if(out>=0)close(out);
 }
};
static void codecError(OH_AVCodec*,int32_t code,void* u){((Job*)u)->fail("视频编解码错误："+std::to_string(code));}
static void decoderFormat(OH_AVCodec*,OH_AVFormat* format,void* u){auto j=(Job*)u;OH_AVFormat_GetIntValue(format,OH_MD_KEY_VIDEO_STRIDE,&j->stride);OH_AVFormat_GetIntValue(format,OH_MD_KEY_VIDEO_SLICE_HEIGHT,&j->slice);int32_t pixel=0;OH_AVFormat_GetIntValue(format,OH_MD_KEY_PIXEL_FORMAT,&pixel);if(pixel!=AV_PIXEL_FORMAT_NV12)j->fail("解码器未提供 NV12 格式");}
static void decoderInput(OH_AVCodec* codec,uint32_t index,OH_AVBuffer* buffer,void* u){auto j=(Job*)u;std::lock_guard<std::mutex> inputGuard(j->inputLock);if(j->inputEos.load())return;try{if(cancelled.load()){j->fail("已取消视频处理");return;}check(OH_AVDemuxer_ReadSampleBuffer(j->demux,j->videoTrack,buffer),"读取视频帧");OH_AVCodecBufferAttr a{};check(OH_AVBuffer_GetBufferAttr(buffer,&a),"读取帧属性");j->inputEos.store(a.flags&AVCODEC_BUFFER_FLAGS_EOS);check(OH_VideoDecoder_PushInputBuffer(codec,index),"提交解码");}catch(const std::exception& e){j->fail(e.what());}}
static void decoderOutput(OH_AVCodec* codec,uint32_t index,OH_AVBuffer* buffer,void* u){auto j=(Job*)u;try{OH_AVCodecBufferAttr a{};check(OH_AVBuffer_GetBufferAttr(buffer,&a),"解码输出属性");if(a.flags&AVCODEC_BUFFER_FLAGS_EOS){check(OH_VideoEncoder_NotifyEndOfStream(j->encoder),"结束编码");}else if(a.size>0&&!cancelled.load()){j->draw(buffer,a);}check(OH_VideoDecoder_FreeOutputBuffer(codec,index),"释放解码帧");}catch(const std::exception& e){OH_VideoDecoder_FreeOutputBuffer(codec,index);j->fail(e.what());}}
static void encoderFormat(OH_AVCodec*,OH_AVFormat* format,void* u){auto j=(Job*)u;try{std::lock_guard<std::mutex> guard(j->lock);if(!j->muxStarted){check(OH_AVMuxer_AddTrack(j->mux,&j->muxVideo,format),"添加视频轨");check(OH_AVMuxer_Start(j->mux),"启动封装");j->muxStarted=true;}}catch(const std::exception& e){j->fail(e.what());}}
static void encoderInput(OH_AVCodec*,uint32_t,OH_AVBuffer*,void*){}
static void encoderOutput(OH_AVCodec* codec,uint32_t index,OH_AVBuffer* buffer,void* u){auto j=(Job*)u;try{OH_AVCodecBufferAttr a{};check(OH_AVBuffer_GetBufferAttr(buffer,&a),"编码输出属性");{std::lock_guard<std::mutex> guard(j->lock);if(a.size>0&&!(a.flags&AVCODEC_BUFFER_FLAGS_CODEC_DATA)){if(!j->muxStarted)throw std::runtime_error("视频封装未启动");check(OH_AVMuxer_WriteSampleBuffer(j->mux,j->muxVideo,buffer),"写入视频");}if(a.flags&AVCODEC_BUFFER_FLAGS_EOS){j->done=true;j->cv.notify_all();}}check(OH_VideoEncoder_FreeOutputBuffer(codec,index),"释放编码帧");}catch(const std::exception& e){OH_VideoEncoder_FreeOutputBuffer(codec,index);j->fail(e.what());}}
static void execute(napi_env,void* data){auto j=(Job*)data;try {
 j->in=open(j->source.c_str(),O_RDONLY);j->out=open(j->dest.c_str(),O_RDWR|O_CREAT|O_TRUNC,0600);if(j->in<0||j->out<0)throw std::runtime_error("无法打开视频文件");struct stat st{};if(fstat(j->in,&st)!=0||st.st_size<=0)throw std::runtime_error("无法读取视频文件大小");j->src=OH_AVSource_CreateWithFD(j->in,0,st.st_size);if(!j->src)throw std::runtime_error("无法解析视频");j->demux=OH_AVDemuxer_CreateWithSource(j->src);j->mux=OH_AVMuxer_Create(j->out,AV_OUTPUT_FORMAT_MPEG_4);if(!j->demux||!j->mux)throw std::runtime_error("媒体服务不可用");
 auto sf=OH_AVSource_GetSourceFormat(j->src);int32_t count=0;OH_AVFormat_GetIntValue(sf,OH_MD_KEY_TRACK_COUNT,&count);OH_AVFormat_GetLongValue(sf,OH_MD_KEY_DURATION,&j->duration);OH_AVFormat_Destroy(sf);OH_AVFormat* vf=nullptr;
 for(int i=0;i<count;i++){auto f=OH_AVSource_GetTrackFormat(j->src,i);if(!f)throw std::runtime_error("无法读取媒体轨道格式");int32_t type=0;if(!OH_AVFormat_GetIntValue(f,OH_MD_KEY_TRACK_TYPE,&type)){OH_AVFormat_Destroy(f);throw std::runtime_error("媒体轨道类型缺失");}if(type==MEDIA_TYPE_VID&&j->videoTrack<0){j->videoTrack=i;vf=f;}else{if(type==MEDIA_TYPE_AUD&&j->audioTrack<0){j->audioTrack=i;check(OH_AVMuxer_AddTrack(j->mux,&j->muxAudio,f),"添加音轨");}OH_AVFormat_Destroy(f);}}
 if(!vf)throw std::runtime_error("视频轨缺失");const char* mime=nullptr;OH_AVFormat_GetStringValue(vf,OH_MD_KEY_CODEC_MIME,&mime);OH_AVFormat_GetIntValue(vf,OH_MD_KEY_WIDTH,&j->w);OH_AVFormat_GetIntValue(vf,OH_MD_KEY_HEIGHT,&j->h);OH_AVFormat_GetIntValue(vf,OH_MD_KEY_ROTATION,&j->rotation);j->stride=j->w;j->slice=j->h;
 if((j->rotation%180?j->h:j->w)!=j->ow||(j->rotation%180?j->w:j->h)!=j->oh){OH_AVFormat_Destroy(vf);throw std::runtime_error("水印与视频显示尺寸不匹配");}
 j->decoder=OH_VideoDecoder_CreateByMime(mime);j->encoder=OH_VideoEncoder_CreateByMime("video/avc");if(!j->decoder||!j->encoder){OH_AVFormat_Destroy(vf);throw std::runtime_error("没有可用 H.264 编解码器");}
 OH_AVCodecCallback dc{codecError,decoderFormat,decoderInput,decoderOutput},ec{codecError,encoderFormat,encoderInput,encoderOutput};check(OH_VideoDecoder_RegisterCallback(j->decoder,dc,j),"注册解码回调");check(OH_VideoEncoder_RegisterCallback(j->encoder,ec,j),"注册编码回调");
 double sourceFps=0;OH_AVFormat_GetDoubleValue(vf,OH_MD_KEY_FRAME_RATE,&sourceFps);OH_AVFormat_SetIntValue(vf,OH_MD_KEY_PIXEL_FORMAT,AV_PIXEL_FORMAT_NV12);OH_AVFormat_SetIntValue(vf,OH_MD_KEY_ROTATION,0);check(OH_VideoDecoder_Configure(j->decoder,vf),"配置解码");OH_AVFormat_Destroy(vf);
 auto ef=OH_AVFormat_Create();if(!ef)throw std::runtime_error("编码格式不可用");OH_AVFormat_SetStringValue(ef,OH_MD_KEY_CODEC_MIME,"video/avc");OH_AVFormat_SetIntValue(ef,OH_MD_KEY_WIDTH,j->ow);OH_AVFormat_SetIntValue(ef,OH_MD_KEY_HEIGHT,j->oh);OH_AVFormat_SetIntValue(ef,OH_MD_KEY_PIXEL_FORMAT,AV_PIXEL_FORMAT_SURFACE_FORMAT);OH_AVFormat_SetLongValue(ef,OH_MD_KEY_BITRATE,8000000);OH_AVFormat_SetDoubleValue(ef,OH_MD_KEY_FRAME_RATE,sourceFps>0?sourceFps:30);check(OH_VideoEncoder_Configure(j->encoder,ef),"配置编码");OH_AVFormat_Destroy(ef);check(OH_VideoEncoder_GetSurface(j->encoder,&j->window),"创建编码 Surface");check(OH_VideoEncoder_Prepare(j->encoder),"准备编码");check(OH_VideoDecoder_Prepare(j->decoder),"准备解码");check(OH_AVDemuxer_SelectTrackByID(j->demux,j->videoTrack),"选择视频轨");check(OH_VideoEncoder_Start(j->encoder),"开始编码");check(OH_VideoDecoder_Start(j->decoder),"开始解码");
 {std::unique_lock<std::mutex> guard(j->lock);auto last=std::chrono::steady_clock::now();double previous=-1;while(!j->done&&!j->failed){j->cv.wait_for(guard,std::chrono::milliseconds(250));if(cancelled.load())throw std::runtime_error("已取消视频处理");double now=percent.load();if(now!=previous){last=std::chrono::steady_clock::now();previous=now;}if(std::chrono::steady_clock::now()-last>std::chrono::seconds(45))throw std::runtime_error("视频处理超时，原片已保留");}if(j->failed)throw std::runtime_error(j->error);}
 if(j->audioTrack>=0){check(OH_AVDemuxer_SelectTrackByID(j->demux,j->audioTrack),"选择音轨");check(OH_AVDemuxer_SeekToTime(j->demux,0,SEEK_MODE_PREVIOUS_SYNC),"定位音轨起点");auto b=OH_AVBuffer_Create(1024*1024);if(!b)throw std::runtime_error("音频缓冲区不可用");try{while(true){if(cancelled.load())throw std::runtime_error("已取消视频处理");check(OH_AVDemuxer_ReadSampleBuffer(j->demux,j->audioTrack,b),"读取音轨");OH_AVCodecBufferAttr a{};OH_AVBuffer_GetBufferAttr(b,&a);if(a.flags&AVCODEC_BUFFER_FLAGS_EOS)break;check(OH_AVMuxer_WriteSampleBuffer(j->mux,j->muxAudio,b),"保留音轨");}}catch(...){OH_AVBuffer_Destroy(b);throw;}OH_AVBuffer_Destroy(b);}
 check(OH_AVMuxer_Stop(j->mux),"完成视频文件");j->muxStarted=false;percent.store(1);
 }catch(const std::exception& e){j->error=e.what();}j->cleanup();if(!j->error.empty())unlink(j->dest.c_str());}
static void complete(napi_env env,napi_status,void* data){auto j=(Job*)data;napi_value v;if(j->error.empty()){napi_get_undefined(env,&v);napi_resolve_deferred(env,j->deferred,v);}else{napi_value msg;napi_create_string_utf8(env,j->error.c_str(),NAPI_AUTO_LENGTH,&msg);napi_create_error(env,nullptr,msg,&v);napi_reject_deferred(env,j->deferred,v);}napi_delete_async_work(env,j->work);delete j;busy.store(false);}
static std::string stringArg(napi_env env,napi_value v){size_t n=0;napi_get_value_string_utf8(env,v,nullptr,0,&n);std::vector<char> b(n+1);napi_get_value_string_utf8(env,v,b.data(),b.size(),&n);return std::string(b.data(),n);}
static napi_value transcode(napi_env env,napi_callback_info info){size_t n=5;napi_value args[5];napi_get_cb_info(env,info,&n,args,nullptr,nullptr);if(n!=5||busy.exchange(true)){napi_throw_error(env,nullptr,"视频任务正在运行或参数不完整");return nullptr;}auto j=new Job();j->source=stringArg(env,args[0]);j->dest=stringArg(env,args[1]);napi_get_value_int32(env,args[3],&j->ow);napi_get_value_int32(env,args[4],&j->oh);void* p=nullptr;size_t length=0;napi_get_arraybuffer_info(env,args[2],&p,&length);if(!p||j->ow<=0||j->oh<=0||length!=size_t(j->ow)*j->oh*4){delete j;busy.store(false);napi_throw_error(env,nullptr,"水印缓冲区无效");return nullptr;}j->overlay.assign((uint8_t*)p,(uint8_t*)p+length);cancelled.store(false);percent.store(0);napi_value promise,name;napi_create_promise(env,&j->deferred,&promise);napi_create_string_utf8(env,"SiteCamVideo",NAPI_AUTO_LENGTH,&name);napi_create_async_work(env,nullptr,name,execute,complete,j,&j->work);napi_queue_async_work(env,j->work);return promise;}
static napi_value cancel(napi_env env,napi_callback_info){cancelled.store(true);napi_value v;napi_get_undefined(env,&v);return v;}
static napi_value progress(napi_env env,napi_callback_info){napi_value v;napi_create_double(env,percent.load(),&v);return v;}
static napi_value init(napi_env env,napi_value exports){napi_property_descriptor props[]={{"transcode",nullptr,transcode,nullptr,nullptr,nullptr,napi_default,nullptr},{"cancel",nullptr,cancel,nullptr,nullptr,nullptr,napi_default,nullptr},{"progress",nullptr,progress,nullptr,nullptr,nullptr,napi_default,nullptr}};napi_define_properties(env,exports,3,props);return exports;}
static napi_module mod={1,0,nullptr,init,"sitecam",nullptr,{0}};
extern "C" __attribute__((constructor)) void registerModule(){napi_module_register(&mod);}
