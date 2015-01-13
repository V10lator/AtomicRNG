package de.V10lator;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.bytedeco.javacv.CanvasFrame;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.OpenCVFrameGrabber;
import org.bytedeco.javacpp.avutil;
import org.bytedeco.javacpp.opencv_core.IplImage;

public class AtomicRNG {
    private static int retries = 0;
    private static MessageDigest md = null;
    private static OpenCVFrameGrabber atomicRNGDevice;
    private static FileWriter osRNG = null;
    private static final double version = 0.8d;
    private static final float brightnessFilter = 0.2f;
    private static int numCount = 0;
    private static FFmpegFrameRecorder videoOut = null;
    
    private static void toOSrng(int number, boolean hash) {
        if(hash) {
            byte[] hashStream = md.digest(Integer.toHexString(number).getBytes());
            md.reset();
            hashStream = md.digest(hashStream);
            md.reset();
            for (int i=0;i<hashStream.length;i++)
                try {
                    osRNG.write(Integer.toHexString(0xFF & hashStream[i]));
                } catch (IOException e) {
                    e.printStackTrace();
                }
        } else {
            numCount += String.valueOf(number).length();
            try {
                osRNG.write(String.valueOf(number));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    
    private static boolean restartAtomicRNGDevice() {
        if(retries++ > 3)
            return false;
        try {
            atomicRNGDevice.restart();
        } catch (org.bytedeco.javacv.FrameGrabber.Exception e) {
            return restartAtomicRNGDevice();
        }
        retries = 0;
        return true;
    }
    
    private static class Cleanup extends Thread {
        public void run() {
            System.out.print(System.lineSeparator()+
                    "Cleaning up... ");
            if(osRNG != null) {
                try {
                    osRNG.flush();
                    osRNG.close();
                } catch(IOException e) {
                    e.printStackTrace();
                }
            }
            if(videoOut != null) {
                try {
                    videoOut.stop();
                    videoOut.release();
                } catch (org.bytedeco.javacv.FrameRecorder.Exception e) {
                    e.printStackTrace();
                }
            }
            System.out.println("done!");
        }
    }
    
    public static void main(String[] args) {
        System.out.print("AtomicRNG v"+version+System.lineSeparator()+
                "(c) 2015 by Thomas \"V10lator\" Rohloff."+System.lineSeparator()+
                System.lineSeparator()+
                "Initializing mysterious black box... ");
        
        try {
            md = MessageDigest.getInstance("SHA-512");
        }
        catch(NoSuchAlgorithmException e) {
            e.printStackTrace();
            return;
        }
        
        Runtime.getRuntime().addShutdownHook(new Cleanup());
        
        atomicRNGDevice = new OpenCVFrameGrabber(0);
        try {
            atomicRNGDevice.start();
        } catch(Exception e) {
            if(!restartAtomicRNGDevice()) {
                System.err.println("error!");
                e.printStackTrace();
                return;
            }
        }
        
        // Throw away the first 4 seconds cause of hardware init.
        for(int i = 0; i < (9*4); i++)
            try {
                atomicRNGDevice.grab().release();
            } catch (org.bytedeco.javacv.FrameGrabber.Exception e) {
                if(!restartAtomicRNGDevice()) {
                    System.err.println("Box error!");
                    e.printStackTrace();
                    return;
                }
            }
        
        // Keep the UNIX RNG open all the time
        File osRNGfile = new File("/dev/random");
        if(!osRNGfile.exists() || osRNGfile.isDirectory() || !osRNGfile.canWrite()) { // isDirectory() cause isFile() returns false.
            System.out.println("error ("+osRNGfile.exists()+"/"+(!osRNGfile.isDirectory())+"/"+osRNGfile.canWrite()+") !");
            return;
        }
        try {
            osRNG = new FileWriter(osRNGfile);
        } catch (IOException e) {
            System.out.println("error!");
            e.printStackTrace();
            return;
        }
        
        CanvasFrame canvasFrame = new CanvasFrame("AtomicRNG LiveView");
        canvasFrame.setDefaultCloseOperation(CanvasFrame.EXIT_ON_CLOSE);
        
        System.out.println("done!");
        
        long lastFound = System.currentTimeMillis();
        int fpsCount = 0, hashCount = 0, width = 0, statXoffset = 0, height = 0;
        long lastSlice = lastFound;
        int black = Color.BLACK.getRGB();
        Color yellow = new Color(1.0f, 1.0f, 0.0f, 0.1f);
        BufferedImage statImg = null;
        Font font = new Font("Arial Black", Font.PLAIN, 18);
        while(true) {
            long start = System.currentTimeMillis();
            try {
                if(start - lastSlice >= 10000L) {
                    String stat = "FPS: "+((float)fpsCount/10.0f)+" | Numbers/sec: "+((float)((hashCount*40)+numCount)/10.0f)+" ("+((float)hashCount/10.0f)+" hashes/sec)";
                    canvasFrame.setTitle("AtomicRNG LiveView | "+stat);
                    //System.out.println("FPS: "+fpsCount+" | Numbers/sec: "+((hashCount*40)+numCount)+" ("+hashCount+" hashes/sec)");
                    hashCount = fpsCount = numCount = 0;
                    lastSlice = start;
                    osRNG.flush();
                }
                IplImage img = atomicRNGDevice.grab();
                fpsCount++;
                retries = 0;
                if(img != null && !img.isNull()) {
                    if(statImg == null) {
                        width = img.width();
                        height = img.height();
                        statXoffset = width + 2;
                        int statWidth = statXoffset + width;
                        statImg = new BufferedImage(statWidth, height, BufferedImage.TYPE_INT_RGB);
                        for(int x = width + 1; x < statXoffset; x++)
                            for(int y = 0; y < height; y++)
                                statImg.setRGB(x, y, Color.RED.getRGB());
                        canvasFrame.setCanvasSize(statWidth, height);
                        videoOut = new FFmpegFrameRecorder("~/Private/AtomicRNG-LiveView.mp4",  statWidth, height);
                        videoOut.setVideoCodec(13);
                        videoOut.setFormat("mp4");
                        videoOut.setPixelFormat(avutil.AV_PIX_FMT_YUV420P);
                        videoOut.setFrameRate(9);
                        videoOut.setVideoBitrate(10 * 1024 * 1024);
                        videoOut.start();
                    }
                    BufferedImage bImg = img.getBufferedImage();
                    int rgb, red, green, blue;
                    float b;
                    String sb;
                    Color color;
                    boolean impact = false;
                    for(int y = 0; y < height; y++) {
                        for(int x = 0; x < width; x++) {
                            rgb = bImg.getRGB(x, y);
                            statImg.setRGB(x, y, rgb);
                            color = new Color(rgb);
                            red = color.getRed();
                            green = color.getGreen();
                            blue = color.getBlue();
                            b = Color.RGBtoHSB(red, green, blue, new float[3])[2];
                            if(b < brightnessFilter) {
                                statImg.setRGB(statXoffset + x, y, black);
                                continue;
                            }
                            statImg.setRGB(statXoffset + x, y, rgb);
                            impact = true;
                            b -= brightnessFilter;
                            sb = String.valueOf(b);
                            sb = sb.substring(sb.indexOf(".")+1);
                     //       System.out.println("Impact! X/Y: "+x+"/"+y+" | R/G/B: "+red+"/"+green+"/"+blue+" | brightness: "+b+" ("+sb+")");
                            toOSrng(red, true);
                            toOSrng(green, true);
                            toOSrng(blue, true);
                            toOSrng(x, true);
                            toOSrng(y, true);
                            toOSrng(Integer.parseInt(sb), false);
                            hashCount += 6;
                        }
                    }
                    if(impact) {
                        toOSrng((int)(start - lastFound), false);
                        lastFound = start;
                        hashCount++;
                    }
                    Graphics graphics = statImg.getGraphics();
                    graphics.setColor(yellow);
                    graphics.setFont(font);
                    graphics.drawString("Raw", width / 2 - 25, 25);
                    graphics.drawString("Filtered", statXoffset + (width / 2 - 50), 25);
                    
                    canvasFrame.showImage(statImg);
                    videoOut.record(IplImage.createFrom(statImg));
                    
                    img.release();
                }
            } catch(Exception e) {
                if(!restartAtomicRNGDevice()) {
                    System.err.println("Box error!");
                    e.printStackTrace();
                    return;
                }
            }
            try {
                Thread.sleep(2L);
            } catch (InterruptedException e) {}
        }
    }
}
