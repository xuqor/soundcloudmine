import xuqor.sound.client.api.*;
import xuqor.sound.client.audio.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.io.*;
import java.util.*;
public class LifecycleSmoke {
 static void waitFor(java.util.function.BooleanSupplier ok,String name)throws Exception{
  long until=System.nanoTime()+15_000_000_000L;
  while(!ok.getAsBoolean()&&System.nanoTime()<until)Thread.sleep(10);
  if(!ok.getAsBoolean())throw new AssertionError(name);
 }
 public static void main(String[] args)throws Exception{
  Process ff=new ProcessBuilder("ffmpeg","-v","error","-f","lavfi","-i","sine=frequency=440:duration=1.5","-f","mp3","pipe:1").start();
  byte[] mp3=ff.getInputStream().readAllBytes();if(ff.waitFor()!=0)throw new AssertionError("ffmpeg");
  HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/tone.mp3",e->{e.sendResponseHeaders(200,mp3.length);e.getResponseBody().write(mp3);e.close();});
  server.start();
  String url="http://127.0.0.1:"+server.getAddress().getPort()+"/tone.mp3";
  try(var player=new StreamingPlayer(new SoundCloudApi())){
   Track one=new Track(1,"Test one","Generated sine",url,"",1500,List.of(new Track.Stream(url,"direct","audio/mpeg")));
   Track two=new Track(2,"Test two","Generated sine",url,"",1500,one.streams());
   player.add(List.of(one,two));
   waitFor(()->player.state()==StreamingPlayer.State.PLAYING,"playing");
   player.togglePause();if(player.state()!=StreamingPlayer.State.PAUSED)throw new AssertionError("pause");
   Thread.sleep(70);player.togglePause();
   waitFor(()->player.index()==1,"auto-next");
   System.out.println("Pause/resume and automatic next: PASS");
   waitFor(()->player.state()==StreamingPlayer.State.IDLE,"natural stop");
   if(player.queue().size()!=2)throw new AssertionError("metadata queue");
   player.cycleRepeat();player.cycleRepeat(); // ONE
   player.play(0);
   waitFor(()->player.state()==StreamingPlayer.State.PLAYING,"repeat start");
   Thread.sleep(1800);
   if(player.index()!=0||player.state()==StreamingPlayer.State.ERROR)throw new AssertionError("repeat one");
   System.out.println("Repeat-one: PASS");
   player.remove(0);
   waitFor(()->player.state()==StreamingPlayer.State.PLAYING,"remove current");
   player.clear();
   if(!player.queue().isEmpty()||player.state()!=StreamingPlayer.State.IDLE)throw new AssertionError("clear");
   System.out.println("Remove-current, clear and resource shutdown: PASS");
  }finally{server.stop(0);}
 }
}
