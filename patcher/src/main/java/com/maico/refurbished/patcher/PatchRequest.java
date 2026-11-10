package com.maico.refurbished.patcher;
import java.io.File;
public final class PatchRequest {
    public final File baseApk, moduleDex, unsignedApk, outputApk;
    public final String[] names;
    public final byte[][] pngs;
    public final int[] backgrounds; // 0: opaque white, 1: transparent
    public final SigningKey signingKey;
    public final String launcherComponent;
    public final byte[] shortcutIcon;
    public PatchRequest(File baseApk, File moduleDex, File unsignedApk, File outputApk,
                        String[] names, byte[][] pngs, int[] backgrounds, SigningKey signingKey) {
        this(baseApk,moduleDex,unsignedApk,outputApk,names,pngs,backgrounds,signingKey,null);
    }
    public PatchRequest(File baseApk, File moduleDex, File unsignedApk, File outputApk,
                        String[] names, byte[][] pngs, int[] backgrounds,
                        SigningKey signingKey,String launcherComponent) {
        this(baseApk,moduleDex,unsignedApk,outputApk,names,pngs,backgrounds,signingKey,launcherComponent,null);
    }
    public PatchRequest(File baseApk, File moduleDex, File unsignedApk, File outputApk,
                        String[] names, byte[][] pngs, int[] backgrounds,
                        SigningKey signingKey,String launcherComponent,byte[] shortcutIcon) {
        if (baseApk == null || moduleDex == null || unsignedApk == null || outputApk == null
            || signingKey == null || names == null || names.length < 1 || pngs == null || pngs.length != names.length
            || backgrounds == null || backgrounds.length != names.length)
            throw new IllegalArgumentException("Missing patch inputs");
        for (int i = 0; i < names.length; i++)
            if (names[i] == null || names[i].trim().isEmpty() || pngs[i] == null || pngs[i].length == 0
                || (backgrounds[i]!=0 && backgrounds[i]!=1))
                throw new IllegalArgumentException("Missing icon/name pair " + (i + 1));
        this.baseApk=baseApk; this.moduleDex=moduleDex; this.unsignedApk=unsignedApk;
        this.outputApk=outputApk; this.names=names.clone(); this.pngs=pngs.clone(); this.backgrounds=backgrounds.clone(); this.signingKey=signingKey;
        this.launcherComponent=launcherComponent;
        this.shortcutIcon=shortcutIcon==null?null:shortcutIcon.clone();
    }
}
