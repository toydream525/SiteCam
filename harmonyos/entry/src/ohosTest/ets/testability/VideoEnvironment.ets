/** Native's exact codec-creation failure; bitstream and encoder errors remain failures. */
export function missingVideoCodec(message:string):boolean{return message==='没有可用 H.264 编解码器';}
