package xuqor.sound.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.*;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import xuqor.sound.client.SoundcloudmineClient;
import xuqor.sound.client.api.Track;
import xuqor.sound.client.api.SoundCloudApi;
import xuqor.sound.client.audio.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import static xuqor.sound.client.SoundcloudmineClient.*;
import static org.lwjgl.opengl.GL33.*;

public final class PlayerScreen extends Screen {
    private static final GlCanvas G = new GlCanvas();
    private static final int TEXT=0xFFF3F2F7, MUTED=0xFF9695A6, ACCENT=0xFFA98CFF;
    private final Screen parent;
    private final long opened=System.nanoTime();
    private long closing;
    private final Map<String,Float> hover = new HashMap<>();
    private List<SoundCloudApi.SearchResult> results = List.of();
    private String input="",notice="";
    private boolean focused=true,busy,settings,dragVolume,dragSeek,showResults;
    private int scroll;
    private float x,y,w=920,h=640,scale=1,mx,my,appearance=1;
    private long artworkId=Long.MIN_VALUE;
    private volatile BufferedImage pendingArtwork;
    private int artwork;
    private float artworkFade=1;
    private String artworkUrl="";
    private long previous=System.nanoTime();

    private boolean russian(){ return "ru".equals(SoundcloudmineClient.settings.language); }
    private String text(String english, String russian){ return russian()?russian:english; }
    private int accent(){
        if("custom".equals(SoundcloudmineClient.settings.theme)) try {
            return 0xFF000000 | Integer.parseInt(SoundcloudmineClient.settings.customAccent.replace("#",""),16);
        } catch(NumberFormatException ignored) {}
        return switch(SoundcloudmineClient.settings.theme){case "ocean" -> 0xFF66C7FF;case "rose" -> 0xFFFF82A8;default -> ACCENT;};
    }

    public PlayerScreen(Screen parent) { super(Component.literal("SoundCloud Mine")); this.parent=parent; }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partial){ }
    @Override public void renderBackground(GuiGraphics graphics,int mx,int my,float delta){ }
    @Override public void onClose(){
        if(!SoundcloudmineClient.settings.animations) minecraft.setScreen(parent);
        else if(closing==0) closing=System.nanoTime();
    }
    @Override public void tick(){
        if(closing!=0 && System.nanoTime()-closing>160_000_000L) minecraft.setScreen(parent);
        updateArtwork();
    }
    @Override public void removed(){
        if(artwork!=0){glDeleteTextures(artwork);artwork=0;}
        pendingArtwork=null;
        SoundcloudmineClient.settings.volume=PLAYER.volume();SoundcloudmineClient.settings.save();
    }
    public static void destroyRenderer(){G.destroy();}
    private void layout(){
        var window=Minecraft.getInstance().getWindow();
        int fw=window.getWidth(),fh=window.getHeight();
        scale=Math.min(fw/980f,fh/700f);
        scale=Math.max(.25f,Math.min(scale,1.6f));
        x=(fw/scale-w)/2;y=(fh/scale-h)/2;
        mx=(float)(minecraft.mouseHandler.xpos()*fw/window.getScreenWidth()/scale);
        my=(float)(minecraft.mouseHandler.ypos()*fh/window.getScreenHeight()/scale);
    }
    private float mouseX(double value){return (float)(value*minecraft.getWindow().getWidth()/width/scale);}
    private float mouseY(double value){return (float)(value*minecraft.getWindow().getHeight()/height/scale);}
    private static boolean in(float a,float b,float x,float y,float w,float h){return a>=x&&a<=x+w&&b>=y&&b<=y+h;}
    private boolean over(float a,float b,float ww,float hh){return in(mx,my,a,b,ww,hh);}
    private void updateArtwork(){
        Track track=PLAYER.track(); long id=track==null?Long.MIN_VALUE:track.id();
        if(id==artworkId)return;
        artworkId=id;artworkUrl=track==null?"":track.artwork();pendingArtwork=null;artworkFade=0;
        if(artwork!=0){glDeleteTextures(artwork);artwork=0;}
        if(artworkUrl.isBlank())return;
        String url=artworkUrl.replace("-large.", "-t300x300.");
        NETWORK.submit(()->{
            try{
                byte[] bytes=API.bytes(url,2_000_000);
                // Inspect dimensions BEFORE decoding: avoid a malicious decompression bomb.
                try(var imageInput=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))){
                    var readers=ImageIO.getImageReaders(imageInput);
                    if(!readers.hasNext())return;
                    var reader=readers.next();
                    try{
                        reader.setInput(imageInput);
                        int iw=reader.getWidth(0),ih=reader.getHeight(0);
                        if(iw<=0||ih<=0||iw>1024||ih>1024)return;
                        BufferedImage image=reader.read(0);
                        minecraft.execute(()->{if(minecraft.screen==this&&artworkId==id)pendingArtwork=image;});
                    }finally{reader.dispose();}
                }
            }catch(Exception ignored){}
        });
    }
    public void renderOverlay(){
        layout();
        long now=System.nanoTime();float dt=Math.min(.05f,(now-previous)/1e9f);previous=now;
        appearance=SoundcloudmineClient.settings.animations?
                smooth(Math.min(1,(now-opened)/220_000_000f)):1;
        if(closing!=0)appearance*=1-smooth(Math.min(1,(now-closing)/160_000_000f));
        var window=minecraft.getWindow();
        try{
            G.begin(window.getWidth(),window.getHeight());
            G.logicalSize(window.getWidth()/scale,window.getHeight()/scale,scale);
            if(pendingArtwork!=null){if(artwork!=0)glDeleteTextures(artwork);artwork=GlCanvas.texture(pendingArtwork);pendingArtwork=null;}
            artworkFade=Math.min(1,artworkFade+dt*4);
            G.opacity(appearance);
            G.round(0,0,window.getWidth()/scale,window.getHeight()/scale,0,0x8808080E);
            float shift=(1-appearance)*12;
            y+=shift;
            for(int i=5;i>0;i--)G.round(x-i*3,y+i*3,w+i*6,h+i*4,25+i*2,0x08000000);
            G.round(x,y,w,h,24,0xFF15151E);
            G.round(x+1,y+1,w-2,80,23,0xFF1B1B26);
            G.round(x+1,y+53,w-2,28,0,0xFF1B1B26);
            G.round(x+28,y+26,31,31,10,accent());
            if("bars".equals(SoundcloudmineClient.settings.icon)) {
                G.round(x+37,y+37,3,11,1,0xFF211936);G.round(x+43,y+32,3,16,1,0xFF211936);
                G.round(x+49,y+35,3,13,1,0xFF211936);
            } else if("circle".equals(SoundcloudmineClient.settings.icon)) G.round(x+37,y+32,16,16,8,0xFF211936);
            else G.text(SoundcloudmineClient.settings.customIcon,x+37,y+31,17,0xFF211936);
        G.text("SoundCloud Mine",x+72,y+22,23,TEXT);
            G.text(text("Music nearby. The world is yours.","Музыка рядом. Мир — перед тобой."),x+73,y+51,12,MUTED);
            button("settings",text("Settings","Настройки"),x+w-177,y+26,113,31,settings,dt);
            button("close","X",x+w-51,y+26,27,31,false,dt);
            drawPlayer(dt,now);
            drawLibrary(dt);
            String footer = busy ? "Поиск…" : !notice.isBlank() ? notice :
                PLAYER.state()==StreamingPlayer.State.ERROR || PLAYER.state()==StreamingPlayer.State.LOADING ?
                PLAYER.message() : "M — открыть   ·   Esc — закрыть   ·   Музыка продолжает играть в фоне";
            G.text(G.fit(footer,12,w-58),x+28,y+h-35,12,
                PLAYER.state()==StreamingPlayer.State.ERROR?0xFFFFAAAA:MUTED);
            if(settings)drawSettings(dt);
        } catch(Exception e){
            notice="Ошибка OpenGL: "+e.getMessage();
            System.err.println("SoundCloudMine renderer: "+e);
        } finally { G.end();G.opacity(1); }
    }
    private static float smooth(float v){return v*v*(3-2*v);}
    private void button(String id,String label,float a,float b,float ww,float hh,boolean active,float dt){
        float old=hover.getOrDefault(id,0f),target=over(a,b,ww,hh)?1:0;
        float v=SoundcloudmineClient.settings.animations?old+(target-old)*Math.min(1,dt*14):target;
        hover.put(id,v);
        int base=active?0xFF3D3159:mix(0xFF262632,0xFF363644,v);
        G.round(a,b,ww,hh,Math.min(10,hh/2),base);
        float tw=G.textWidth(label,14);
        G.text(label,a+(ww-tw)/2,b+(hh-18)/2,14,active?ACCENT:TEXT);
    }
    private static int mix(int a,int b,float t){
        int r=(int)(((a>>16)&255)*(1-t)+((b>>16)&255)*t);
        int g=(int)(((a>>8)&255)*(1-t)+((b>>8)&255)*t);
        int v=(int)((a&255)*(1-t)+(b&255)*t);
        return 0xFF000000|(r<<16)|(g<<8)|v;
    }
    private void drawPlayer(float dt,long now){
        Track t=PLAYER.track();
        float px=x+30,py=y+108;
        G.round(px,py,280,252,18,0xFF242333);
        if(artwork!=0)G.image(px,py,280,252,18,artwork,artworkFade);
        else{
            G.round(px+93,py+72,92,92,46,0xFF383049);
            for(int i=0;i<5;i++){
                float motion=PLAYER.state()==StreamingPlayer.State.PLAYING?(float)(Math.sin(now/220_000_000d+i*.8)*9):0;
                G.round(px+116+i*11,py+106-motion/2,5,23+motion,2,ACCENT);
            }
            G.text("SOUNDCLOUD",px+90,py+190,14,MUTED);
        }
        G.text(G.fit(t==null?text("Your music belongs here","Твоя музыка — здесь"):t.title(),22,280),px,py+267,22,TEXT);
        G.text(G.fit(t==null?text("Find a track or paste a link","Найди трек или добавь ссылку"):t.artist(),14,280),px,py+301,14,MUTED);
        float progress=t!=null&&t.durationMs()>0?Math.clamp((float)PLAYER.positionMs()/t.durationMs(),0f,1f):0;
        G.round(px,py+339,280,4,2,0xFF393747);
        G.round(px,py+339,Math.max(4,280*progress),4,2,ACCENT);
        if(t!=null)G.round(px+280*progress-4,py+336,10,10,5,TEXT);
        G.text(time(PLAYER.positionMs()),px,py+350,11,MUTED);
        String duration=t==null?"0:00":time(t.durationMs());
        G.text(duration,px+280-G.textWidth(duration,11),py+350,11,MUTED);
        button("shuffle","Mix",px,py+381,43,32,PLAYER.shuffle(),dt);
        button("previous","<<",px+52,py+381,39,32,false,dt);
        float pulse=hover.getOrDefault("play",0f),target=over(px+104,py+372,53,51)?1:0;
        pulse+=(target-pulse)*Math.min(1,dt*14);hover.put("play",pulse);
        G.round(px+104,py+372,53,51,25,mix(accent(),0xFFC5AFFF,pulse));
        if(PLAYER.state()==StreamingPlayer.State.PLAYING){
            G.round(px+122,py+389,5,18,2,0xFF241A39);
            G.round(px+131,py+389,5,18,2,0xFF241A39);
        }else G.triangle(px+125,py+388,18,0xFF241A39);
        button("next",">>",px+170,py+381,39,32,false,dt);
        String repeat=switch(PLAYER.repeat()){case OFF->"Loop";case ALL->"All";case ONE->"One";};
        button("repeat",repeat,px+219,py+381,61,32,PLAYER.repeat()!=QueueRules.Repeat.OFF,dt);
        G.text("VOL",px,py+442,10,MUTED);
        G.round(px+35,py+448,194,4,2,0xFF393747);
        G.round(px+35,py+448,Math.max(4,194*PLAYER.volume()),4,2,accent());
        G.round(px+35+194*PLAYER.volume()-4,py+444,10,10,5,TEXT);
        G.text(Integer.toString((int)(PLAYER.volume()*100))+"%",px+241,py+440,11,MUTED);
    }
    private static String time(long ms){long seconds=Math.max(0,ms/1000);return (seconds/60)+":"+String.format(Locale.ROOT,"%02d",seconds%60);}
    private void drawLibrary(float dt){
        float a=x+344,b=y+111,ww=546;
        G.text(text("Find your rhythm","Найди свой ритм"),a,b,20,TEXT);
        G.text(text("Track, playlist, account or URL","Название, ссылка на трек, плейлист или аккаунт"),a,b+31,12,MUTED);
        G.round(a,b+63,ww-78,42,11,focused?0xFF353144:0xFF282833);
        G.clip(a+12,b+69,ww-103,29);
        G.text(input.isBlank()?"Поиск или https://soundcloud.com/…":input,a+12,b+75,14,input.isBlank()?MUTED:TEXT);
        if(focused&&(System.currentTimeMillis()/500)%2==0){
            float caret=Math.min(ww-101,G.textWidth(input,14)+12);G.round(a+caret,b+75,1,17,0,ACCENT);
        }
        G.unclip();
        button("search",text("Search","Найти"),a+ww-68,b+63,68,42,false,dt);
        button("queue",text("Queue · ","Очередь · ")+PLAYER.queue().size(),a,b+122,137,32,!showResults,dt);
        button("results",text("Results","Результаты"),a+147,b+122,119,32,showResults,dt);
        button("clear",text("Clear","Очистить"),a+ww-93,b+122,93,32,false,dt);
        List<?> list=!showResults?PLAYER.queue():results;
        float top=b+170,limit=302;
        G.clip(a,top,ww,limit);
        if(list.isEmpty()){
            G.round(a,top+12,ww,178,14,0xFF1D1D28);
            G.text(text("Start with one track","Начни с одного трека"),a+140,top+60,19,TEXT);
            G.text(text("Add music and the queue keeps going.","Добавляй музыку — очередь продолжится сама."),a+113,top+96,13,MUTED);
            G.text(text("Streaming only. No music files are stored.","Только поток. Без музыкальных файлов на диске."),a+94,top+125,12,MUTED);
        }
        for(int i=scroll;i<Math.min(list.size(),scroll+6);i++){
            Track t=!showResults?(Track)list.get(i):((SoundCloudApi.SearchResult)list.get(i)).track();
            SoundCloudApi.SearchResult result=showResults?(SoundCloudApi.SearchResult)list.get(i):null;
            float row=top+(i-scroll)*50;
            boolean current=!showResults&&i==PLAYER.index();
            G.round(a,row,ww,44,10,current?0xFF342C46:over(a,row,ww,44)?0xFF2A2838:0xFF1E1E29);
            G.round(a+10,row+9,27,27,8,current?ACCENT:0xFF343241);
            G.text(String.format(Locale.ROOT,"%02d",i+1),a+15,row+16,11,current?0xFF211936:MUTED);
            G.text(G.fit(showResults?result.title():t.title(),14,ww-116),a+49,row+5,14,TEXT);
            G.text(G.fit(showResults?result.kind()+" · "+result.subtitle():t.artist(),11,ww-145),a+49,row+26,11,MUTED);
            G.text(!showResults?"x":(result.playable()?"+":"·"),a+ww-29,row+10,19,MUTED);
        }
        G.unclip();
        if(list.size()>6)G.text(text("Scroll · "+list.size()+" results","Прокрутка колёсиком · "+list.size()+" результатов"),a,b+480,11,MUTED);
    }
    private void drawSettings(float dt){
        G.round(x,y,w,h,24,0xC0101018);
        float a=x+208,b=y+112;
        G.round(a,b,504,341,20,0xFF23222F);
        G.text(text("Player settings","Настройки плеера"),a+28,b+26,23,TEXT);
        G.text(text("No listener account is required.","Вход в аккаунт слушателя не нужен."),a+28,b+64,13,MUTED);
        G.text(text("Application client_id (blank = auto)","client_id приложения (пусто = авто)"),a+28,b+109,14,TEXT);
        G.round(a+28,b+141,448,42,10,0xFF353144);
        G.text(G.fit(SoundcloudmineClient.settings.clientId.isBlank()?text("Automatic detection","Автоматическое определение"):SoundcloudmineClient.settings.clientId,14,423),a+40,b+154,14,MUTED);
        button("language",text("Language: English","Язык: русский"),a+28,b+207,187,34,russian(),dt);
        button("theme",text("Theme: "+SoundcloudmineClient.settings.theme,"Тема: "+SoundcloudmineClient.settings.theme),a+223,b+207,150,34,false,dt);
        button("icon",text("Icon: "+SoundcloudmineClient.settings.icon,"Иконка: "+SoundcloudmineClient.settings.icon),a+28,b+250,187,34,false,dt);
        button("animations",text("Animations: ","Анимации: ")+(SoundcloudmineClient.settings.animations?text("on","вкл"):text("off","выкл")),a+223,b+250,150,34,SoundcloudmineClient.settings.animations,dt);
        button("settingsdone",text("Done","Готово"),a+363,b+275,113,38,true,dt);
        G.text(text("Ctrl+V — paste client_id · Backspace — clear","Ctrl+V — вставить client_id · Backspace — очистить"),a+28,b+266,12,MUTED);
        G.text(text("Web API may change. Paid/private tracks are unavailable.","Веб-API может меняться. Платные/закрытые треки недоступны."),a+28,b+307,11,MUTED);
    }
    private void find(){
        if(busy||input.isBlank())return;
        busy=true;notice="";String query=input;
        NETWORK.submit(()->{
            try{
                List<SoundCloudApi.SearchResult> found=API.findResults(query);
                minecraft.execute(()->{
                    if(minecraft.screen!=this)return;
                    busy=false;scroll=0;
                    if(query.startsWith("https://")||query.startsWith("http://")){
                        List<Track> tracks=found.stream().filter(SoundCloudApi.SearchResult::playable).map(SoundCloudApi.SearchResult::track).toList();
                        try{PLAYER.add(tracks);showResults=false;notice="Added tracks: "+tracks.size();}
                        catch(Exception ex){notice=ex.getMessage();}
                    }else{results=found;showResults=true;notice=found.isEmpty()?"No results":"Press + to add a track";}
                });
            }catch(Exception ex){
                minecraft.execute(()->{busy=false;notice="Поиск: "+Objects.toString(ex.getMessage(),"ошибка");});
            }
        });
    }
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean doubleClick){
        if(event.button()!=0||closing!=0)return false;
        layout();float cx=mouseX(event.x()),cy=mouseY(event.y());
        if(settings){
            float a=x+208,b=y+112;
            if(in(cx,cy,a+363,b+275,113,38)){settings=false;API.setClientId(SoundcloudmineClient.settings.clientId);SoundcloudmineClient.settings.save();}
            else if(in(cx,cy,a+28,b+207,187,34))SoundcloudmineClient.settings.language=russian()?"en":"ru";
            else if(in(cx,cy,a+223,b+207,150,34))SoundcloudmineClient.settings.theme=switch(SoundcloudmineClient.settings.theme){case "midnight"->"ocean";case "ocean"->"rose";case "rose"->"custom";default->"midnight";};
            else if(in(cx,cy,a+28,b+250,187,34))SoundcloudmineClient.settings.icon=switch(SoundcloudmineClient.settings.icon){case "bars"->"circle";case "circle"->"custom";default->"bars";};
            else if(in(cx,cy,a+223,b+250,150,34))SoundcloudmineClient.settings.animations=!SoundcloudmineClient.settings.animations;
            return true;
        }
        if(in(cx,cy,x+w-51,y+26,27,31)){onClose();return true;}
        if(in(cx,cy,x+w-177,y+26,113,31)){settings=true;return true;}
        float px=x+30,py=y+108;
        if(in(cx,cy,px+104,py+372,53,51))PLAYER.togglePause();
        else if(in(cx,cy,px,py+381,43,32))PLAYER.toggleShuffle();
        else if(in(cx,cy,px+52,py+381,39,32))PLAYER.previous();
        else if(in(cx,cy,px+170,py+381,39,32))PLAYER.next();
        else if(in(cx,cy,px+219,py+381,61,32))PLAYER.cycleRepeat();
        else if(in(cx,cy,px+30,py+435,205,25)){dragVolume=true;PLAYER.volume((cx-px-35)/194);}
        else if(in(cx,cy,px,py+327,280,28)){dragSeek=true;}
        float a=x+344,b=y+111,ww=546;
        focused=in(cx,cy,a,b+63,ww-78,42);
        if(in(cx,cy,a+ww-68,b+63,68,42)){find();return true;}
        if(in(cx,cy,a,b+122,137,32)){showResults=false;scroll=0;return true;}
        if(in(cx,cy,a+147,b+122,119,32)){showResults=true;scroll=0;return true;}
        if(in(cx,cy,a+ww-93,b+122,93,32)){PLAYER.clear();return true;}
        List<?> list=!showResults?PLAYER.queue():results;
        float top=b+170;
        if(in(cx,cy,a,top,ww,302)){
            int i=scroll+(int)((cy-top)/50);
            if(i<list.size()){
                if(showResults) {
                    SoundCloudApi.SearchResult result=(SoundCloudApi.SearchResult)list.get(i);
                    if(!result.playable()){notice="Only playable tracks can be queued";return true;}
                    try{PLAYER.add(List.of(result.track()));notice="In queue: "+result.title();}
                    catch(Exception ex){notice=ex.getMessage();}
                }else if(cx>a+ww-47)PLAYER.remove(i);
                else PLAYER.play(i);
            }
        }
        return true;
    }
    @Override public boolean mouseDragged(MouseButtonEvent event,double dx,double dy){
        if(dragVolume)PLAYER.volume((mouseX(event.x())-x-65)/194);
        return dragVolume||dragSeek;
    }
    @Override public boolean mouseReleased(MouseButtonEvent event){
        if(dragSeek){PLAYER.seek((mouseX(event.x())-x-30)/280);notice="Перемотка: поток читается заново, без файла";}
        dragSeek=false;dragVolume=false;return true;
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){
        int count=!showResults?PLAYER.queue().size():results.size();
        scroll=Math.clamp(scroll-(int)Math.signum(vertical),0,Math.max(0,count-6));return true;
    }
    @Override public boolean keyPressed(KeyEvent event){
        int key=event.key();
        if(key==GLFW.GLFW_KEY_ESCAPE){if(settings){settings=false;API.setClientId(SoundcloudmineClient.settings.clientId);}else onClose();return true;}
        if(settings){
            if(event.hasControlDown()&&key==GLFW.GLFW_KEY_V){
                String value=minecraft.keyboardHandler.getClipboard().trim();
                if(value.matches("[A-Za-z0-9_-]{1,128}"))SoundcloudmineClient.settings.clientId=value;
                else notice="client_id должен содержать только буквы, цифры, _ и -";
            }else if(event.hasControlDown()&&key==GLFW.GLFW_KEY_A){
                String value=minecraft.keyboardHandler.getClipboard().trim();
                if(value.matches("#?[0-9A-Fa-f]{6}")){SoundcloudmineClient.settings.customAccent=value;SoundcloudmineClient.settings.theme="custom";}
                else if(value.codePointCount(0,value.length())<=2){SoundcloudmineClient.settings.customIcon=value;SoundcloudmineClient.settings.icon="custom";}
            }else if(key==GLFW.GLFW_KEY_BACKSPACE)SoundcloudmineClient.settings.clientId="";
            return true;
        }
        if(focused){
            if(event.hasControlDown()&&key==GLFW.GLFW_KEY_V){
                String paste=minecraft.keyboardHandler.getClipboard().replaceAll("\\p{Cntrl}","");
                input=(input+paste).substring(0,Math.min(2048,input.length()+paste.length()));
            }else if(event.hasControlDown()&&key==GLFW.GLFW_KEY_A)input="";
            else if(key==GLFW.GLFW_KEY_BACKSPACE&&!input.isEmpty())input=input.substring(0,input.offsetByCodePoints(input.length(),-1));
            else if(key==GLFW.GLFW_KEY_ENTER||key==GLFW.GLFW_KEY_KP_ENTER)find();
            return true;
        }
        if(key==GLFW.GLFW_KEY_SPACE){PLAYER.togglePause();return true;}
        return super.keyPressed(event);
    }
    @Override public boolean charTyped(CharacterEvent event){
        if(!settings&&focused&&input.length()<2048&&event.isAllowedChatCharacter()){input+=event.codepointAsString();return true;}
        return false;
    }
}
