import xuqor.sound.client.api.*;
import javazoom.jl.decoder.*;
import java.io.*;
public class NetworkSmoke {
    public static void main(String[] args) throws Exception {
        SoundCloudApi api=new SoundCloudApi();
        var tracks=api.find("lofi");
        if(tracks.isEmpty())throw new AssertionError("No public tracks");
        Track track=tracks.stream().filter(t->!t.streams().isEmpty()).findFirst().orElseThrow();
        System.out.println("Search: "+tracks.size()+" public tracks; selected: "+track.title());
        var resolved=api.find(track.permalink());
        if(resolved.getFirst().id()!=track.id())throw new AssertionError("Resolve id mismatch");
        System.out.println("Resolve: OK");
        try(InputStream audio=api.audio(track);BufferedInputStream in=new BufferedInputStream(audio,65536)){
            Bitstream bits=new Bitstream(in);Decoder decoder=new Decoder();long samples=0;
            for(int i=0;i<80;i++){
                Header h=bits.readFrame();if(h==null)throw new AssertionError("Too short stream");
                SampleBuffer out=(SampleBuffer)decoder.decodeFrame(h,bits);
                samples+=out.getBufferLength();bits.closeFrame();
            }
            bits.close();
            System.out.println("Network MP3 decode: 80 frames, "+samples+" PCM samples; stream closed, audio files not created.");
        }
    }
}
