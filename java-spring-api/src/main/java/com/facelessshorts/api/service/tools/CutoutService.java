package com.facelessshorts.api.service.tools;
import com.facelessshorts.api.service.ToolService;
import com.facelessshorts.api.service.infrastructure.*;
import com.facelessshorts.api.service.integration.*;

import com.facelessshorts.api.error.ApiException;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.imageio.ImageIO;

import org.springframework.stereotype.Service;

/** ImageIO cutout implementation with U2-Net rembg and white-key fallback. */
@Service
public final class CutoutService {
    private final ProjectPathService paths;
    private final RembgService rembg;
    public CutoutService(ProjectPathService paths, ToolSupport support) {
        this.paths = paths;
        String configured = System.getenv("REMBG_MODEL_PATH");
        Path defaultModel = Path.of(System.getProperty("user.home"), ".u2net", "u2net.onnx");
        this.rembg = new RembgService(configured == null || configured.isBlank() ? defaultModel : Path.of(configured));
    }

    public Map<String, Object> run(Path project, List<String> args, String function) {
        String inputName = positional(args, 0), outputName = positional(args, 1);
        if (inputName == null || outputName == null) throw new ApiException(422, "cutout requires input and output paths");
        String method = option(args, "--method", "auto").toLowerCase(Locale.ROOT);
        if (!method.equals("auto") && !method.equals("key") && !method.equals("rembg")) throw new ApiException(422, "cutout method must be auto, rembg, or key");
        if (method.equals("auto")) method = rembgAvailable() ? "rembg" : "key";
        double tol = number(args, "--tol", 34, 0, 441), soft = number(args, "--soft", 26, 0, 441), feather = number(args, "--feather", 1, 0, 64);
        int border = integer(args, "--border", 0, 0, 1024), pad = integer(args, "--pad", 16, 0, 4096);
        Path input = paths.file(project, inputName), output = paths.file(project, outputName);
        if (input.equals(output)) throw new ApiException(422, "cutout input and output must differ");
        try {
            BufferedImage source = ImageIO.read(input.toFile());
            if (source == null) throw new ApiException(422, "input is not a readable image: " + inputName);
            int[][] matte = method.equals("rembg") ? rembg.matte(source) : matte(source, tol, soft);
            matte = minFilter(matte); if (feather > 0) matte = blur(matte, feather);
            BufferedImage result = rgba(source, matte);
            if (!args.contains("--no-trim")) result = crop(result, pad);
            if (border > 0) result = stickerBorder(result, border);
            Files.createDirectories(output.toAbsolutePath().getParent());
            if (!ImageIO.write(result, "PNG", output.toFile())) throw new ApiException(500, "PNG writer is unavailable");
            Map<String,Object> data = new LinkedHashMap<>(); data.put("path", output.toString()); data.put("ok", true); data.put("method", method); data.put("width", result.getWidth()); data.put("height", result.getHeight()); data.put("opaque_percent", opaquePercent(result));
            return ToolService.envelope("cutout", function, data);
        } catch (IOException e) { throw new ApiException(422, "cannot read or write cutout image", e); }
    }

    private boolean rembgAvailable() {
        String configured = System.getenv("REMBG_MODEL_PATH");
        return Files.isRegularFile(configured == null || configured.isBlank()
                ? Path.of(System.getProperty("user.home"), ".u2net", "u2net.onnx") : Path.of(configured));
    }

    private static int[][] matte(BufferedImage image, double tol, double soft) { int w=image.getWidth(),h=image.getHeight(); int[][] a=new int[h][w]; for(int y=0;y<h;y++)for(int x=0;x<w;x++){int p=image.getRGB(x,y),r=255-(p>>16&255),g=255-(p>>8&255),b=255-(p&255);double d=Math.sqrt(r*r+g*g+b*b);a[y][x]=soft==0?(d>tol?255:0):(int)Math.round(255*clamp((d-tol)/soft));}return a; }
    private static int[][] minFilter(int[][] in) { int h=in.length,w=in[0].length;int[][] o=new int[h][w];for(int y=0;y<h;y++)for(int x=0;x<w;x++){int m=255;for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++)m=Math.min(m,in[Math.max(0,Math.min(h-1,y+dy))][Math.max(0,Math.min(w-1,x+dx))]);o[y][x]=m;}return o; }
    private static int[][] blur(int[][] in,double radius) { int r=Math.max(1,(int)Math.ceil(radius*2.5)),h=in.length,w=in[0].length;double[] k=new double[2*r+1];double sum=0,s=Math.max(.01,radius);for(int i=-r;i<=r;i++){k[i+r]=Math.exp(-i*i/(2*s*s));sum+=k[i+r];}for(int i=0;i<k.length;i++)k[i]/=sum;double[][] t=new double[h][w];int[][] o=new int[h][w];for(int y=0;y<h;y++)for(int x=0;x<w;x++)for(int i=-r;i<=r;i++)t[y][x]+=in[y][Math.max(0,Math.min(w-1,x+i))]*k[i+r];for(int y=0;y<h;y++)for(int x=0;x<w;x++){double v=0;for(int i=-r;i<=r;i++)v+=t[Math.max(0,Math.min(h-1,y+i))][x]*k[i+r];o[y][x]=(int)Math.round(v);}return o; }
    private static BufferedImage rgba(BufferedImage s,int[][] a){BufferedImage o=new BufferedImage(s.getWidth(),s.getHeight(),BufferedImage.TYPE_INT_ARGB);for(int y=0;y<o.getHeight();y++)for(int x=0;x<o.getWidth();x++)o.setRGB(x,y,a[y][x]<<24|s.getRGB(x,y)&0xffffff);return o;}
    private static BufferedImage crop(BufferedImage in,int pad){int minX=in.getWidth(),minY=in.getHeight(),maxX=-1,maxY=-1;for(int y=0;y<in.getHeight();y++)for(int x=0;x<in.getWidth();x++)if((in.getRGB(x,y)>>>24)>0){minX=Math.min(minX,x);minY=Math.min(minY,y);maxX=Math.max(maxX,x);maxY=Math.max(maxY,y);}if(maxX<0)throw new ApiException(422,"cutout matte is fully transparent â€” check tol / source image");int l=Math.max(0,minX-pad),t=Math.max(0,minY-pad),r=Math.min(in.getWidth(),maxX+pad+1),b=Math.min(in.getHeight(),maxY+pad+1);return in.getSubimage(l,t,r-l,b-t);}
    private static BufferedImage stickerBorder(BufferedImage in,int radius){int w=in.getWidth(),h=in.getHeight();int[][] a=new int[h][w];for(int y=0;y<h;y++)for(int x=0;x<w;x++){int m=0;for(int dy=-radius;dy<=radius;dy++)for(int dx=-radius;dx<=radius;dx++)if(dx*dx+dy*dy<=radius*radius)m=Math.max(m,(in.getRGB(Math.max(0,Math.min(w-1,x+dx)),Math.max(0,Math.min(h-1,y+dy)))>>>24));a[y][x]=m;}a=blur(a,.6);BufferedImage o=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);for(int y=0;y<h;y++)for(int x=0;x<w;x++){int under=a[y][x]<<24|0xffffff,over=in.getRGB(x,y),ao=over>>>24,au=under>>>24,oa=ao+(au*(255-ao)+127)/255;int r=oa==0?0:(((over>>16&255)*ao+(under>>16&255)*au*(255-ao)/255)/oa),g=oa==0?0:(((over>>8&255)*ao+(under>>8&255)*au*(255-ao)/255)/oa),b=oa==0?0:(((over&255)*ao+(under&255)*au*(255-ao)/255)/oa);o.setRGB(x,y,oa<<24|r<<16|g<<8|b);}return o;}
    private static int opaquePercent(BufferedImage i){int n=0,total=i.getWidth()*i.getHeight();for(int y=0;y<i.getHeight();y++)for(int x=0;x<i.getWidth();x++)if((i.getRGB(x,y)>>>24)>8)n++;return total==0?0:n*100/total;}
    private static String positional(List<String>a,int n){return a.size()>n&&!a.get(n).startsWith("--")?a.get(n):null;}
    private static String option(List<String>a,String k,String d){int i=a.indexOf(k);return i>=0&&i+1<a.size()?a.get(i+1):d;}
    private static double number(List<String>a,String k,double d,double min,double max){try{double v=Double.parseDouble(option(a,k,Double.toString(d)));if(!Double.isFinite(v)||v<min||v>max)throw new Exception();return v;}catch(Exception e){throw new ApiException(422,k+" must be between "+min+" and "+max);}}
    private static int integer(List<String>a,String k,int d,int min,int max){double v=number(a,k,d,min,max);if(v!=(int)v)throw new ApiException(422,k+" must be an integer");return(int)v;}
    private static double clamp(double v){return Math.max(0,Math.min(1,v));}
}