import xuqor.sound.client.api.*;
import javazoom.jl.decoder.*;
import java.io.*;
class HlsSmoke {
 public static void main(String[]args)throws Exception{
  var api=new SoundCloudApi();var original=api.find("lofi").getFirst();
  var onlyHls=new Track(original.id(),original.title(),original.artist(),original.permalink(),"",original.durationMs(),
    original.streams().stream().filter(s->s.protocol().equals("hls")).toList());
  try(var stream=api.audio(onlyHls)){
   var bits=new Bitstream(stream);var decoder=new Decoder();int count=0;
   for(int i=0;i<80;i++){var h=bits.readFrame();if(h==null)break;decoder.decodeFrame(h,bits);bits.closeFrame();count++;}
   bits.close();System.out.println("Real SoundCloud HLS MP3: decoded "+count+" frames");
  }
 }
}
