package xuqor.sound.client.ui;

import org.lwjgl.opengl.*;
import org.lwjgl.BufferUtils;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.*;
import java.util.*;
import static org.lwjgl.opengl.GL33.*;

/** A standalone OpenGL renderer. No Minecraft buttons/fonts/GuiGraphics rendering. */
public final class GlCanvas {
    private int program, vao, vbo, atlas;
    private int viewLoc, rectLoc, radiusLoc, colorLoc, modeLoc;
    private float viewWidth, viewHeight, pixelScale=1, alpha = 1;
    private State saved;
    private final Map<Integer, Glyph> glyphs = new HashMap<>();
    private final float[] quad = new float[24];
    private record Glyph(float u, float v, float uw, float vh, int width, int height, int advance) {}

    private void init() {
        if (program != 0) return;
        int vertex = shader(GL_VERTEX_SHADER, """
            #version 150
            in vec2 position; in vec2 texcoord;
            out vec2 point; out vec2 uv;
            uniform vec2 view;
            void main() {
                point=position; uv=texcoord;
                gl_Position=vec4(position.x/view.x*2.0-1.0,1.0-position.y/view.y*2.0,0.0,1.0);
            }
            """);
        int fragment = shader(GL_FRAGMENT_SHADER, """
            #version 150
            in vec2 point; in vec2 uv; out vec4 frag;
            uniform vec4 rect; uniform float radius; uniform vec4 color;
            uniform int mode; uniform sampler2D image;
            void main() {
                vec2 q=abs(point-(rect.xy+rect.zw*0.5))-rect.zw*0.5+radius;
                float distance=length(max(q,0.0))+min(max(q.x,q.y),0.0)-radius;
                float edge=max(fwidth(distance),0.6);
                float coverage=1.0-smoothstep(-edge,edge,distance);
                vec4 c=color;
                if(mode==1) c*=texture(image,uv);
                if(mode==2) c.a*=texture(image,uv).a;
                frag=vec4(c.rgb,c.a*coverage);
            }
            """);
        program = glCreateProgram();
        glAttachShader(program, vertex); glAttachShader(program, fragment);
        glBindAttribLocation(program, 0, "position"); glBindAttribLocation(program, 1, "texcoord");
        glLinkProgram(program); glDeleteShader(vertex); glDeleteShader(fragment);
        if (glGetProgrami(program, GL_LINK_STATUS) == 0) throw new IllegalStateException(glGetProgramInfoLog(program));
        viewLoc=glGetUniformLocation(program,"view"); rectLoc=glGetUniformLocation(program,"rect");
        radiusLoc=glGetUniformLocation(program,"radius"); colorLoc=glGetUniformLocation(program,"color");
        modeLoc=glGetUniformLocation(program,"mode");
        vao=glGenVertexArrays(); vbo=glGenBuffers();
        glBindVertexArray(vao); glBindBuffer(GL_ARRAY_BUFFER,vbo);
        glBufferData(GL_ARRAY_BUFFER,96,GL_STREAM_DRAW);
        glEnableVertexAttribArray(0); glVertexAttribPointer(0,2,GL_FLOAT,false,16,0);
        glEnableVertexAttribArray(1); glVertexAttribPointer(1,2,GL_FLOAT,false,16,8);
        makeFont();
    }
    private static int shader(int type, String source) {
        int s=glCreateShader(type); glShaderSource(s,source); glCompileShader(s);
        if(glGetShaderi(s,GL_COMPILE_STATUS)==0) throw new IllegalStateException(glGetShaderInfoLog(s));
        return s;
    }
    private void makeFont() {
        try (InputStream in = getClass().getResourceAsStream("/assets/soundcloudmine/fonts/Regular.ttf")) {
            Font font=Font.createFont(Font.TRUETYPE_FONT,Objects.requireNonNull(in)).deriveFont(32f);
            BufferedImage img=new BufferedImage(2048,1024,BufferedImage.TYPE_INT_ARGB);
            Graphics2D g=img.createGraphics();
            g.setFont(font);g.setColor(Color.WHITE);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            FontMetrics fm=g.getFontMetrics();
            int x=2,y=2,h=42;
            for(int cp=32;cp<=0x206F;cp++){
                if (!(cp<=0x52F || (cp>=0x1E00&&cp<=0x1EFF) || cp>=0x2000)) continue;
                if (!font.canDisplay(cp)) continue;
                String s=Character.toString(cp); int advance=fm.stringWidth(s),w=Math.max(2,advance+4);
                if(x+w>=2048){x=2;y+=h;}
                if(y+h>=1024)break;
                g.drawString(s,x+2,y+fm.getAscent());
                glyphs.put(cp,new Glyph(x/2048f,y/1024f,w/2048f,h/1024f,w,h,advance));
                x+=w+2;
            }
            g.dispose(); atlas=texture(img);
        } catch(Exception e){throw new IllegalStateException("Font atlas",e);}
    }
    public static int texture(BufferedImage img) {
        int width=img.getWidth(),height=img.getHeight();
        ByteBuffer data=BufferUtils.createByteBuffer(width*height*4);
        for(int y=0;y<height;y++)for(int x=0;x<width;x++){
            int c=img.getRGB(x,y);data.put((byte)(c>>16)).put((byte)(c>>8)).put((byte)c).put((byte)(c>>24));
        }
        data.flip(); int t=glGenTextures();glBindTexture(GL_TEXTURE_2D,t);
        int alignment=glGetInteger(GL_UNPACK_ALIGNMENT),rowLength=glGetInteger(GL_UNPACK_ROW_LENGTH),
            skipRows=glGetInteger(GL_UNPACK_SKIP_ROWS),skipPixels=glGetInteger(GL_UNPACK_SKIP_PIXELS),
            pbo=glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING);
        glBindBuffer(GL_PIXEL_UNPACK_BUFFER,0);
        glPixelStorei(GL_UNPACK_ALIGNMENT,1);glPixelStorei(GL_UNPACK_ROW_LENGTH,0);
        glPixelStorei(GL_UNPACK_SKIP_ROWS,0);glPixelStorei(GL_UNPACK_SKIP_PIXELS,0);
        glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,width,height,0,GL_RGBA,GL_UNSIGNED_BYTE,data);
        glPixelStorei(GL_UNPACK_ALIGNMENT,alignment);glPixelStorei(GL_UNPACK_ROW_LENGTH,rowLength);
        glPixelStorei(GL_UNPACK_SKIP_ROWS,skipRows);glPixelStorei(GL_UNPACK_SKIP_PIXELS,skipPixels);
        glBindBuffer(GL_PIXEL_UNPACK_BUFFER,pbo);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);
        return t;
    }
    public void begin(int width,int height) {
        saved=new State();
        glActiveTexture(GL_TEXTURE0);glBindSampler(0,0);
        init();
        viewWidth=width;viewHeight=height;
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER,0);
        glViewport(0,0,width,height);
        glDisable(GL_DEPTH_TEST);glDisable(GL_CULL_FACE);glDisable(GL_SCISSOR_TEST);
        glDisable(GL_STENCIL_TEST);glDisable(GL_FRAMEBUFFER_SRGB);
        glEnable(GL_BLEND);glBlendEquationSeparate(GL_FUNC_ADD,GL_FUNC_ADD);
        glBlendFuncSeparate(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA,GL_ONE,GL_ONE_MINUS_SRC_ALPHA);
        glColorMask(true,true,true,true);glPolygonMode(GL_FRONT_AND_BACK,GL_FILL);
        glUseProgram(program);glBindVertexArray(vao);glBindBuffer(GL_ARRAY_BUFFER,vbo);
        glUniform2f(viewLoc,width,height);glUniform1i(glGetUniformLocation(program,"image"),0);
    }
    public void logicalSize(float width,float height,float scale){ viewWidth=width;viewHeight=height;pixelScale=scale;glUniform2f(viewLoc,width,height); }
    public void end(){ if(saved!=null){saved.restore();saved=null;} }
    public void opacity(float a){alpha=a;}
    public void clip(float x,float y,float w,float h){
        glEnable(GL_SCISSOR_TEST);glScissor((int)(x*pixelScale),(int)((viewHeight-y-h)*pixelScale),(int)(w*pixelScale),(int)(h*pixelScale));
    }
    public void unclip(){glDisable(GL_SCISSOR_TEST);}
    public void round(float x,float y,float w,float h,float radius,int color){
        draw(x,y,w,h,radius,color,0,0,0,0,1,1);
    }
    public void image(float x,float y,float w,float h,float r,int tex,float a){
        draw(x,y,w,h,r,((int)(a*255)<<24)|0xFFFFFF,1,tex,0,0,1,1);
    }
    private void draw(float x,float y,float w,float h,float radius,int color,int mode,int tex,float u,float v,float uw,float vh) {
        if(w<=0||h<=0)return;
        vertex(0,x,y,u,v);vertex(1,x+w,y,u+uw,v);vertex(2,x+w,y+h,u+uw,v+vh);
        vertex(3,x,y,u,v);vertex(4,x+w,y+h,u+uw,v+vh);vertex(5,x,y+h,u,v+vh);
        glUniform4f(rectLoc,x,y,w,h);glUniform1f(radiusLoc,radius);
        glUniform4f(colorLoc,((color>>16)&255)/255f,((color>>8)&255)/255f,(color&255)/255f,((color>>>24)&255)/255f*alpha);
        glUniform1i(modeLoc,mode);glBindTexture(GL_TEXTURE_2D,tex);
        glBufferSubData(GL_ARRAY_BUFFER,0,quad);glDrawArrays(GL_TRIANGLES,0,6);
    }
    private void vertex(int n,float x,float y,float u,float v){int k=n*4;quad[k]=x;quad[k+1]=y;quad[k+2]=u;quad[k+3]=v;}
    public float textWidth(String s,float size){
        float w=0;for(int cp:s.codePoints().toArray()){Glyph g=glyphs.getOrDefault(cp,glyphs.get((int)'?'));if(g!=null)w+=g.advance*size/32f;}return w;
    }
    public String fit(String s,float size,float width){
        if(textWidth(s,size)<=width)return s;
        StringBuilder b=new StringBuilder();float used=0,ellipsis=textWidth("...",size);
        for(int cp:s.codePoints().toArray()){float w=textWidth(Character.toString(cp),size);if(used+w+ellipsis>width)break;b.appendCodePoint(cp);used+=w;}
        return b+"...";
    }
    public void text(String s,float x,float y,float size,int color){
        float scale=size/32f;
        for(int cp:s.codePoints().toArray()){
            Glyph g=glyphs.getOrDefault(cp,glyphs.get((int)'?'));if(g==null)continue;
            draw(x-2*scale,y,g.width*scale,g.height*scale,0,color,2,atlas,g.u,g.v,g.uw,g.vh);
            x+=g.advance*scale;
        }
    }
    public void triangle(float x,float y,float size,int color){
        // Vertices use the same shader with a rectangle covering the triangle.
        vertex(0,x,y,0,0);vertex(1,x+size,y+size/2,0,0);vertex(2,x,y+size,0,0);
        glUniform4f(rectLoc,x-1,y-1,size+2,size+2);glUniform1f(radiusLoc,0);glUniform1i(modeLoc,0);
        glUniform4f(colorLoc,((color>>16)&255)/255f,((color>>8)&255)/255f,(color&255)/255f,((color>>>24)&255)/255f*alpha);
        glBufferSubData(GL_ARRAY_BUFFER,0,quad);glDrawArrays(GL_TRIANGLES,0,3);
    }
    public void destroy(){
        if(atlas!=0)glDeleteTextures(atlas);if(vbo!=0)glDeleteBuffers(vbo);
        if(vao!=0)glDeleteVertexArrays(vao);if(program!=0)glDeleteProgram(program);
        atlas=vbo=vao=program=0;glyphs.clear();
    }
    /** Restore real GL state exactly, keeping Minecraft's cached state valid. */
    private static final class State {
        final int program=glGetInteger(GL_CURRENT_PROGRAM),vao=glGetInteger(GL_VERTEX_ARRAY_BINDING),
            array=glGetInteger(GL_ARRAY_BUFFER_BINDING),active=glGetInteger(GL_ACTIVE_TEXTURE),
            framebuffer=glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING),
            srcRgb=glGetInteger(GL_BLEND_SRC_RGB),dstRgb=glGetInteger(GL_BLEND_DST_RGB),
            srcAlpha=glGetInteger(GL_BLEND_SRC_ALPHA),dstAlpha=glGetInteger(GL_BLEND_DST_ALPHA),
            eqRgb=glGetInteger(GL_BLEND_EQUATION_RGB),eqAlpha=glGetInteger(GL_BLEND_EQUATION_ALPHA);
        final int texture,sampler;
        final int[] viewport=new int[4],scissor=new int[4],polygon=new int[2];
        final ByteBuffer masks=BufferUtils.createByteBuffer(4);
        final boolean blend=glIsEnabled(GL_BLEND),depth=glIsEnabled(GL_DEPTH_TEST),cull=glIsEnabled(GL_CULL_FACE),
            clip=glIsEnabled(GL_SCISSOR_TEST),stencil=glIsEnabled(GL_STENCIL_TEST),srgb=glIsEnabled(GL_FRAMEBUFFER_SRGB);
        State(){
            glGetIntegerv(GL_VIEWPORT,viewport);glGetIntegerv(GL_SCISSOR_BOX,scissor);glGetIntegerv(GL_POLYGON_MODE,polygon);
            glGetBooleanv(GL_COLOR_WRITEMASK,masks);
            glActiveTexture(GL_TEXTURE0);texture=glGetInteger(GL_TEXTURE_BINDING_2D);sampler=glGetInteger(GL_SAMPLER_BINDING);
        }
        void restore(){
            glUseProgram(program);glBindVertexArray(vao);glBindBuffer(GL_ARRAY_BUFFER,array);
            glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,texture);glBindSampler(0,sampler);glActiveTexture(active);
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER,framebuffer);
            glViewport(viewport[0],viewport[1],viewport[2],viewport[3]);glScissor(scissor[0],scissor[1],scissor[2],scissor[3]);
            glBlendFuncSeparate(srcRgb,dstRgb,srcAlpha,dstAlpha);glBlendEquationSeparate(eqRgb,eqAlpha);
            glColorMask(masks.get(0)!=0,masks.get(1)!=0,masks.get(2)!=0,masks.get(3)!=0);
            glPolygonMode(GL_FRONT_AND_BACK,polygon[0]);
            enable(GL_BLEND,blend);enable(GL_DEPTH_TEST,depth);enable(GL_CULL_FACE,cull);
            enable(GL_SCISSOR_TEST,clip);enable(GL_STENCIL_TEST,stencil);enable(GL_FRAMEBUFFER_SRGB,srgb);
        }
        private static void enable(int cap,boolean enabled){if(enabled)glEnable(cap);else glDisable(cap);}
    }
}
