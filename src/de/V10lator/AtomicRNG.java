/*
 * This file is part of AtomicRNG.
 * (c) 2015 Thomas "V10lator" Rohloff
 *
 * AtomicRNG is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * AtomicRNG is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 */
package de.V10lator;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.bytedeco.javacv.CanvasFrame;
//import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.OpenCVFrameGrabber;
//import org.bytedeco.javacpp.avutil;
import org.bytedeco.javacpp.opencv_core.IplImage;

public class AtomicRNG {
    private static int retries = 0;
    private static MessageDigest md = null;
    private static OpenCVFrameGrabber atomicRNGDevice;
    private static FileWriter osRNG = null;
    private static String version;
    private static final int filter = 12;
//    private static FFmpegFrameRecorder videoOut = null;
    
    private static void toOSrng(int number) {
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
/*            if(videoOut != null) {
                try {
                    videoOut.stop();
                    videoOut.release();
                } catch (org.bytedeco.javacv.FrameRecorder.Exception e) {
                    e.printStackTrace();
                }
            }*/
            System.out.println("done!");
        }
    }
    
    public static void main(String[] args) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(AtomicRNG.class.getResourceAsStream("/META-INF/maven/"+AtomicRNG.class.getPackage().getName()+"/AtomicRNG/pom.properties")));
        String line;
        try {
            while((line = reader.readLine()) != null)
                if(line.substring(0, 8).equals("version=")) {
                    version = line.substring(8);
                    break;
                }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        
        //org.bytedeco.javacpp.Loader.load(org.bytedeco.javacpp.avcodec.class); // Workaround for java.lang.NoClassDefFoundError: Could not initialize class org.bytedeco.javacpp.avcodec
        System.out.println("AtomicRNG v"+version+System.lineSeparator()+
                "(c) 2015 by Thomas \"V10lator\" Rohloff."+System.lineSeparator());
        
        boolean quiet = false;//, experimentalFilter = false;
        for(String arg: args) {
            switch(arg) {
                case("-q"):
                    quiet = true;
                    break;
/*                case("-ef"):
                    experimentalFilter = true;
                    System.out.println("WARNING: Experimental noise filter activated!"+System.lineSeparator());
                    break;*/
                case "-h":
                    System.out.println("Arguments:"+System.lineSeparator()+
                            " -q  : Be quiet."+System.lineSeparator()+
                            " -ef : Enable experimental filter"+System.lineSeparator()+
                            " -h  : Show this help."+System.lineSeparator());
                    return;
                default:
                    System.out.println("Unknown argument: "+arg+System.lineSeparator()+System.lineSeparator());
                    return;
            }
        }
        
        System.out.print("Initializing mysterious black box... ");
        
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
        
        String title = null;
        CanvasFrame canvasFrame = null;
        if(!quiet) {
            title = "AtomicRNG v"+version+" LiveView | FPS: X.X | Numbers/sec: Y.Y (Z.Z hashes/sec)";
            canvasFrame = new CanvasFrame(title);
            canvasFrame.setDefaultCloseOperation(CanvasFrame.EXIT_ON_CLOSE);
        }
        
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
                    if(!quiet) {
                        canvasFrame.setTitle(title.replaceAll("X\\.X", String.valueOf((float)fpsCount/10.0f)).replaceAll("Y\\.Y", String.valueOf((float)hashCount*4.0f)).replaceAll("Z\\.Z", String.valueOf((float)hashCount/10.0f)));
                        hashCount = fpsCount;
                    }
                    lastSlice = start;
                    osRNG.flush();
                }
                IplImage img = atomicRNGDevice.grab();
                retries = 0;
                if(img != null && !img.isNull()) {
                    if(!quiet)
                        fpsCount++;
                    if(statImg == null) {
                        width = img.width();
                        height = img.height();
                        if(!quiet) {
                            statXoffset = width + 2;
                            int statWidth = statXoffset + width;
                            statImg = new BufferedImage(statWidth, height, BufferedImage.TYPE_INT_RGB);
                            for(int x = width + 1; x < statXoffset; x++)
                                for(int y = 0; y < height; y++)
                                    statImg.setRGB(x, y, Color.RED.getRGB());
                            canvasFrame.setCanvasSize(statWidth, height);
                        }
/*                        videoOut = new FFmpegFrameRecorder("~/Private/AtomicRNG-LiveView.mp4",  statWidth, height);
                        videoOut.setVideoCodec(13);
                        videoOut.setFormat("mp4");
                        videoOut.setPixelFormat(0); // Workaround for java.lang.NoClassDefFoundError: Could not initialize class org.bytedeco.javacpp.avcodec
                        videoOut.setFrameRate(9);
                        videoOut.setVideoBitrate(10 * 1024 * 1024);
                        videoOut.start();*/
                    }
                    BufferedImage bImg = img.getBufferedImage();
                    int rgb, red, green, blue;
                    Color color;
                    boolean impact = false;
                    for(int y = 0; y < height; y++) {
                        for(int x = 0; x < width; x++) {
                            rgb = bImg.getRGB(x, y);
                            if(!quiet)
                                statImg.setRGB(x, y, rgb);
                            color = new Color(rgb);
                            red = color.getRed();
                            green = color.getGreen();
                            blue = color.getBlue();
                            if(!(red > filter || green > filter || blue > filter)) {
                                if(!quiet)
                                    statImg.setRGB(statXoffset + x, y, black);
                                continue;
                            }
                            if(!quiet) {
                                statImg.setRGB(statXoffset + x, y, rgb);
                                hashCount += 6;
                            }
                            impact = true;
                     //       System.out.println("Impact! X/Y: "+x+"/"+y+" | R/G/B: "+red+"/"+green+"/"+blue+" | brightness: "+b+" ("+sb+")");
                            toOSrng(red);
                            toOSrng(x);
                            toOSrng(green);
                            toOSrng(y);
                            toOSrng(blue);
                        }
                    }
                    if(impact) {
                        toOSrng((int)(start - lastFound));
                        lastFound = start;
                        if(!quiet)
                            hashCount++;
                    }
                    if(!quiet) {
                        Graphics graphics = statImg.getGraphics();
                        graphics.setColor(yellow);
                        graphics.setFont(font);
                        graphics.drawString("Raw", width / 2 - 25, 25);
                        graphics.drawString("Filtered", statXoffset + (width / 2 - 50), 25);
                        
                        canvasFrame.showImage(statImg);
//                        videoOut.record(IplImage.createFrom(statImg));
                    }
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
