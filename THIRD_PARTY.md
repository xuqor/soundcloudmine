# Third-party components

- **JLayer 1.0.1** — JavaZoom, GNU LGPL 2.1.
  Included as an unmodified nested dependency JAR via Fabric Loom `include`.
  The LGPL applies to JLayer, not to the original mod source.
  Source artifact: https://repo.maven.apache.org/maven2/javazoom/jlayer/1.0.1/jlayer-1.0.1-sources.jar
  LGPL text: https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html
  The upstream library source is included in `third-party/jlayer-1.0.1-sources.jar`.
  The mod does not modify JLayer. You can replace/rebuild the dependency through `build.gradle`.
- **Liberation Sans** — SIL Open Font License 1.1.
  Font and copyright/license are in `src/client/resources/assets/soundcloudmine/fonts/`.
- Fabric Loader, Fabric API, LWJGL, Gson and Minecraft-provided libraries are
  external platform dependencies; Fabric API is installed separately.
- Minecraft binaries and proprietary assets are **not distributed** with this project ZIP.
