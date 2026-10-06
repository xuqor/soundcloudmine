package xuqor.sound.client;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.*;
import java.io.*;
public final class Settings {
    public float volume = .65f;
    public String clientId = "";
    public boolean animations = true;
    public String language = "en";
    public String theme = "midnight";
    public String icon = "bars";
    /** User-editable hexadecimal accent, used when theme is set to custom. */
    public String customAccent = "#A98CFF";
    /** bars, circle, or a single Unicode glyph configured by the user. */
    public String customIcon = "";
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("soundcloudmine.json");
    public static Settings load(){
        try { return new Gson().fromJson(Files.readString(FILE),Settings.class); }
        catch(Exception e){return new Settings();}
    }
    public void save(){
        try {Files.createDirectories(FILE.getParent());Files.writeString(FILE,new GsonBuilder().setPrettyPrinting().create().toJson(this));}
        catch(IOException e){System.err.println("SoundCloudMine: cannot save settings: "+e.getMessage());}
    }
}
