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

import gnu.crypto.hash.BaseHash;
import gnu.crypto.hash.Sha512;
import gnu.crypto.hash.Whirlpool;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.imageio.ImageIO;

import org.bytedeco.javacv.CanvasFrame;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.OpenCVFrameGrabber;
import org.bytedeco.javacpp.avcodec;
import org.bytedeco.javacpp.avutil;
import org.bytedeco.javacpp.opencv_core.IplImage;

public class AtomicRNG {
    private static OpenCVFrameGrabber atomicRNGDevice;
    private static String version;

    static Random rand = new Random();
    private static boolean randSecure = false;
    private static int hashCount = 0;
    private static long byteCount = 0;

    static ImageScanner[][] scanners;
    private static FFmpegFrameRecorder videoOut = null;
    private static float ts = 0.0f;

    static int height = 0;
    static int width = 0;
    private static int statXoffset = 0;
    private static int statWidth = 0;

    static boolean firstRun = true;
    private static final ArrayList<Pixel> crosses = new ArrayList<Pixel>();
    
    private static final ArrayList<Pixel> randomImagePixels = new ArrayList<Pixel>();
    private static long randomImageNumber = 0L;
    private static long lastRandomImageSlice;
    
    static boolean stopped = false;
    
    private static BaseHash[] hashAlgos = { new Sha512(), new Whirlpool() };
    
    /**
     * This hashes the number with a hashing algorithm based on xxHash:<br>
     * First the number is hashed with a random seed. After that it's
     * hashed again with a new random seed. Both numbers get mixed to
     * minimalize collisions and as a result maximum randomness.<br>
     * <br>
     * This function doesn't always handle the hash to /dev/random but uses
     * it internally to re-seed the internal RNG (used to get the seeds for
     * xxHash) from time to time. This is to ensure the Java pseudo RNG
     * produces true random numbers.
     * @param number The number to hash and feed to /dev/random
     */
    
    //private static ByteBuffer byteBuffer = null;
    //private static ByteBuffer byteBuffer2 = null;
    
    private static void addZeroesToHash() {
        while(rand.nextInt(100) < 10)
            hash.update((byte) 0);
    }
    
    private static BaseHash hash;
    static void toOSrng(int number) {
        /*
         * If this is the first number we got use it as seed for the internal RNG and exit.
         */
        if(!randSecure) {
            rand.setSeed(System.currentTimeMillis() * number);
            randSecure = true;
            return;
        }

        /*
         * Hash the numbers.
         */
        if(hash == null)
            hash = hashAlgos[rand.nextInt(hashAlgos.length)];
        
        ByteBuffer tmpBuffer = ByteBuffer.allocate(Integer.SIZE >> 3);
        tmpBuffer.putInt(number);
        tmpBuffer.flip();
        
        boolean fb = false;
        byte b;
        while(tmpBuffer.hasRemaining()) {
            b = tmpBuffer.get();
            if(!fb) {
                if(b == 0x00)
                    continue;
                else
                    fb = true;
            }
            addZeroesToHash();
            hash.update(b);
        }
        if(!fb) { // We removed all bytes, so the number we got was zero, re-add.
            addZeroesToHash();
            hash.update((byte) 0x00);
        }
        
        if(rand.nextInt(100) > 10)
            return;
        byte[] bytes = hash.digest();
        hash.reset();
        hash = null;
        
        hashCount++;
        /*
         * From time to time use the result to re-seed the internal RNG and exit.
         */
        if(rand.nextInt(100) < 1) {
            rand.setSeed(ByteBuffer.wrap(bytes).getLong());
            return;
        }
        EntropyQueue.add(bytes);
        byteCount += bytes.length;
    }

    /**
     * This restarts the Alpha-Ray-Vistualizer.<br>
     * Normally this should never be used. After 3
     * failed restarts in a row it will quit the program.
     */
    private static void restartAtomicRNGDevice(Exception why) {
        Exception trace = null;
        System.out.print("Restarting ARV device... ");
        int i;
        for(i = 0; i < 3; i++) {
            try {
                atomicRNGDevice.restart();
                trace = null;
                break;
            } catch (org.bytedeco.javacv.FrameGrabber.Exception e) {
                e.addSuppressed(why);
                trace = e;
            }
            try {
                Thread.sleep(500L);
            } catch (InterruptedException e) {}
        }
        if(trace != null) {
            System.out.println("failed! ("+(i+1)+" tries)");
            trace.printStackTrace();
            System.exit(1);
        }
        System.out.println("done!");
        why.printStackTrace();
    }

    private static final AtomicBoolean lock = new AtomicBoolean(false);
    /**
     * This is to get the lock.<b>
     * This blocks till the lock could be aquired!
     * @param aggressive This should normally be false as it needs way more CPU power.
     */
    private static boolean getLock(boolean aggressive) {
        long c = 0;
        while(!lock.compareAndSet(false, true)) {
            if(!aggressive) {
                try {
                    Thread.sleep(2L);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } else if(++c > 1000000000L) {
                return false; //TODO: Danger
            }
        }
        return true;
    }

    /**
     * Internal class trapped by Runtime.getRuntime().addShutdownHook();<br>
     * <br>
     * TODO: Make all actions happening in run() threadsafe as currently there are collisions.
     * @author v10lator
     *
     */
    private static class Cleanup extends Thread {
        public void run() {
            System.out.print(System.lineSeparator()+
                    "Cleaning up... ");
            stopped = true;
            EntropyQueue.cleanup();
            /*
             * Flush and close /dev/random.
             */
            if(!getLock(true)) { // Assume crash
                System.err.println("error!");
                return;          // And do nothing;
            }
            if(videoOut != null) {
                lock.set(false);
                toggleRecording();
            } else
                lock.set(false);
            if(randomImageNumber > 0)
                toggleRandomImage();
            if(EntropyQueue.f != null)
                try {
                    EntropyQueue.f.flush();
                    EntropyQueue.f.close();
                } catch(IOException e) {
                    e.printStackTrace();
                }
            try {
                Thread.sleep(20L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("done!");
        }
    }

    static void toggleRecording() {
        getLock(false);
        try {
            if(videoOut == null) {
                System.out.println("Video: "+statWidth+" / "+height);
                videoOut = new FFmpegFrameRecorder("AtomicRNG.avi", statWidth, height);
                videoOut.setVideoCodec(avcodec.AV_CODEC_ID_HUFFYUV);
                videoOut.setAudioCodec(avcodec.AV_CODEC_ID_NONE);
                videoOut.setFormat("avi");
                videoOut.setPixelFormat(avutil.AV_PIX_FMT_RGB32);
                //videoOut.setSampleRate(9);
                videoOut.setFrameRate(9); //TODO: Don't hardcode.
                videoOut.setVideoBitrate(10 * 1024 * 1024);
                //videoOut.setVideoQuality(1.0d);
                videoOut.start();
            } else {
                videoOut.stop();
                videoOut.release();
                ts = 0.0f;
                videoOut = null;
            }
        } catch (org.bytedeco.javacv.FrameRecorder.Exception e) {
            lock.set(false);
            e.printStackTrace();
            System.exit(1);
        }
        lock.set(false);
    }
    
    static void toggleRandomImage() {
        if(randomImageNumber > 0) {
            paintRandomImage();
            randomImageNumber = 0;
        } else {
            lastRandomImageSlice = System.currentTimeMillis();
            randomImageNumber++;
        }
    }

    private static void paintRandomImage() {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics graphics = img.getGraphics();
        graphics.setColor(Color.BLACK);
        graphics.fillRect(0, 0, width, height);
        long oldSeed = rand.nextLong();
        for(Pixel pixel: randomImagePixels) {
            rand.setSeed(pixel.power);
            img.setRGB(pixel.x, pixel.y, new Color(rand.nextInt(256), rand.nextInt(256), rand.nextInt(256)).getRGB());
        }
        rand.setSeed(oldSeed);
        randomImagePixels.clear();
        try {
            File out = new File("Random image #"+(randomImageNumber++)+".png");
            ImageIO.write(img, "png", out);
        } catch(IOException e) {
            e.printStackTrace();
        }
        lastRandomImageSlice = System.currentTimeMillis();
    }
    
    private static Font smallFont = new Font("Arial", Font.PLAIN, 9);
    private static void paintCross(Graphics g, Pixel pixel) {
        int x = statXoffset + pixel.x;
        
        g.drawOval(x - 7, pixel.y - 7, 14, 14);
        
        g.drawLine(x - 6, pixel.y, x - 4, pixel.y);
        g.drawLine(x + 4, pixel.y, x + 6, pixel.y);
        g.drawLine(x, pixel.y - 6, x, pixel.y - 4);
        g.drawLine(x, pixel.y + 4, x, pixel.y + 6);
        
        g.setFont(smallFont);
        g.drawString(String.valueOf(pixel.power), x + 10, pixel.y + 4);
    }

    /**
     * The main function called by the JVM.<br>
     * Most of the action happens in here.
     * @param args
     */
    public static void main(String[] args) {
        org.bytedeco.javacpp.Loader.load(org.bytedeco.javacpp.avcodec.class); // Workaround for java.lang.NoClassDefFoundError: Could not initialize class org.bytedeco.javacpp.avcodec

        /*
         * Automagically get the version from maven.
         */
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
            System.exit(1);
        }

        /*
         * Startup: Print program name and copyright.
         */
        System.out.println("AtomicRNG v"+version+System.lineSeparator()+
                "(c) 2015 by Thomas \"V10lator\" Rohloff."+System.lineSeparator());

        /*
         * Parse commandline arguments.
         */
        boolean quiet = false, doubleView = false/*, experimentalFilter = false*/;
        for(String arg: args) {
            switch(arg) {
            case("-q"):
                quiet = true;
                break;
            case("-f"):
                if(EntropyQueue.f == null) // No multiple inits for multiple args.
                    EntropyQueue.fileInit();
                break;
            case("-d"):
                doubleView = true;
                break;
            /*                case("-ef"):
                    experimentalFilter = true;
                    System.out.println("WARNING: Experimental noise filter activated!"+System.lineSeparator());
                    break;*/
            case "-h":
                System.out.println("Arguments:"+System.lineSeparator()+
                        " -q  : Be quiet."+System.lineSeparator()+
                        " -f  : Enable file output."+System.lineSeparator()+
                        " -d  : Enable double view."+System.lineSeparator()+
                        //                            " -ef : Enable experimental filter."+System.lineSeparator()+
                        " -v  : Enable video recorder."+System.lineSeparator()+
                        " -h  : Show this help."+System.lineSeparator());
                return;
            default:
                System.out.println("Unknown argument: "+arg+System.lineSeparator()+System.lineSeparator());
                System.exit(1);
            }
        }

        /*
         * Tell the user we're going to initialize the ARV device.
         */
        System.out.print("Initializing Alpha Ray Visualizer... ");
        
        /*
         * Extract native libraries for use with JNA
         *
        try {
            /*
             * Create tmp dir and register it as JNAs library path.
             *
            FileAttribute<?>[] empty = {};
            Path tmpDir = Files.createTempDirectory("AtomicRNG_", empty);
            tmpDir.toFile().deleteOnExit();
            System.setProperty("jna.library.path", tmpDir.toString());
            
            tmpDir.toFile().deleteOnExit(); // Delete tmp dir on exit.
            
            JarFile file = new JarFile(AtomicRNG.class.getProtectionDomain().getCodeSource().getLocation().getFile()); // Open our jar.
            
            /*
             * Extract all files.
             *
            String[] files = { "xxhash" };
            String[] prefixes = { ".so", "__LICENSE.txt" };
            String jarDir = "resources/";
            Path jarFile;
            String fileName;
            InputStream inStream;
            for(String suffix: files)
                for(String prefix: prefixes) {
                    fileName = suffix+prefix;
                    inStream = file.getInputStream(file.getJarEntry(jarDir+fileName));
                    jarFile = tmpDir.resolve(fileName);
                    jarFile.toFile().deleteOnExit();
                    Files.copy(inStream, jarFile);
                    inStream.close();
                }
            file.close(); // close jar.
            
        } catch (Exception e) {
            System.err.println("error!");
            e.printStackTrace();
            System.exit(1);
        }

        /*
         * Trap Cleanup().run() to be called when the JVM exits.
         */
        Runtime.getRuntime().addShutdownHook(new Cleanup());

        /*
         * Open and start the webcam inside of the ARV device.
         */
        atomicRNGDevice = new OpenCVFrameGrabber(0);
        try {
            atomicRNGDevice.start();
        } catch(Exception e) {
            restartAtomicRNGDevice(e);
        }

        /*
         *  Throw away the first frame cause of hardware init.
         *  The noise filters will handle the rest.
         */
        try {
            atomicRNGDevice.grab().release();
        } catch (org.bytedeco.javacv.FrameGrabber.Exception e) {
            restartAtomicRNGDevice(e);
        }

        /*
         *  Open the Linux RNG and keep it open all the time.
         *  We close it in Cleanup().run().
         */
        EntropyQueue.resetOSrng();
        new EntropyQueue().start();

        /*
         * In case we should draw the window initialize it and set its title.
         */
        String[] stat = null, statOut = null;
        CanvasFrame canvasFrame = null;
        if(!quiet) {
            canvasFrame = new CanvasFrame("AtomicRNG v"+version);
            stat = new String[3];
            stat[0] = "FPS: %1";
            stat[1] = "%2 kb/s (%3 hashes/sec)";
            stat[2] = "Queue: %4";
            statOut = new String[3];
            statOut[0] = "FPS: N/A";
            statOut[1] = "N/A kb/s (N/A hashes/sec)";
            statOut[2] = "Queue: N/A";
            canvasFrame.setDefaultCloseOperation(CanvasFrame.EXIT_ON_CLOSE);
            canvasFrame.getCanvas().addMouseListener(new AtomicMouseListener());
        }

        /*
         * We initialized everything.
         * Tell the user we're ready!
         */
        System.out.println("done!");

        /*
         * A few Variables we'll need inside of the main loop.
         */
        int fpsCount = 0, avgFPS = 0;
        Color yellow = new Color(1.0f, 1.0f, 0.0f, 0.1f);
        BufferedImage statImg = null;
        Font font = new Font("Arial Black", Font.PLAIN, 18);
        long lastFound = System.currentTimeMillis();
        long lastSlice = lastFound;
        /*
         * All right, let's enter the matrix, eh, the main loop I mean...
         */
        while(true) {
            /*
             * Grab a frame from the webcam.
             */
            IplImage img = null;
            try {
                /*
                 * Grab a frame from the webcam.
                 */
                img = atomicRNGDevice.grab();
            } catch(Exception e) {
                restartAtomicRNGDevice(e);
            }
            if(img != null && !img.isNull()) {
                /*
                 * First get the start time of that loop run.
                 */
                long start = System.currentTimeMillis();

                if(!quiet)
                    fpsCount++;

                /*
                 * After each 4 seconds...
                 */
                if(start - lastSlice >= 4000L) {
                    /*
                     * ...update the windows title with the newest statistics...
                     */
                    if(!quiet) {
                        avgFPS = fpsCount >> 2;
                        if(((float)fpsCount/4.0f) % 2 != 0)
                            avgFPS++;
                        String es = EntropyQueue.getStats();
                        for(int i = 0; i < stat.length; i++)
                            statOut[i] = stat[i].
                                    replaceAll("%1", String.valueOf(avgFPS)).
                                    replaceAll("%2", String.valueOf((float)(byteCount >> 7)/4.0f)).
                                    replaceAll("%3", String.valueOf((float)hashCount/4.0f)).
                                    replaceAll("%4", es);
                        byteCount = hashCount = fpsCount = 0;
                    }
                    /*
                     * prepare to count the next 10 seconds and flush /dev/random.
                     */
                    lastSlice = start;
                }
                
                if(randomImageNumber > 0 && start - lastRandomImageSlice >= 3600000L)
                    paintRandomImage();

                /*
                 * The width is static, so if it's zero we never asked for it and other infos.
                 * Let's do that.
                 */
                if(firstRun) {
                    width = img.width();
                    height = img.height();
                    int rows = height >> 6, columns = width >> 6;
                    int cw = width / columns, ch = height / rows, yi;
                    scanners = new ImageScanner[rows][columns];
                    for(int y = 0; y < rows; y++) {
                        yi = y * ch;
                        for(int x = 0; x < columns; x++)
                            scanners[y][x] = new ImageScanner(x * cw, yi);
                    }
                        
                    /*
                     * Calculate the needed window size and paint the red line in the middle.
                     */
                    if(!quiet) {
                        if(doubleView) {
                            statXoffset = width + 2;
                            statWidth = statXoffset + width;
                        } else
                            statWidth = width;
                        statImg = new BufferedImage(statWidth, height, BufferedImage.TYPE_3BYTE_BGR);
                        if(doubleView)
                            for(int x = width; x < statXoffset; x++)
                                for(int y = 0; y < height; y++)
                                    statImg.setRGB(x, y, Color.RED.getRGB());
                        canvasFrame.setCanvasSize(statWidth, height);
                    }
                }
                Graphics graphics = null;
                if(!quiet) {
                    graphics = statImg.getGraphics();
                    graphics.drawImage(img.getBufferedImage(), 0, 0, null);
                    if(doubleView) {
                        graphics.setColor(Color.BLACK);
                        graphics.fillRect(statXoffset, 0, statXoffset + width, height);
                    }
                    
                   
                }

                /*
                 * Wrap the frame to a Java BufferedImage and parse it pixel by pixel.
                 */
                ByteBuffer buffer = img.getByteBuffer();
                boolean[][] ignoredPixels = new boolean[width][height];
                for(int x = 0; x < width; x++)
                    for(int y = 0; y < height; y++)
                        ignoredPixels[x][y] = false;
                ArrayList<Pixel>[] impacts = new ArrayList[(height >> 6) * (width >> 6)];
                int c = 0;
                for(ImageScanner[] isa: scanners)
                    for(ImageScanner is: isa)
                        impacts[c++] = is.scan(buffer, img.widthStep(), img.nChannels(), start, ignoredPixels);
                
                boolean impact = false;
                for(ArrayList<Pixel> list: impacts)
                    if(!list.isEmpty()) {
                        for(Pixel pix: list) {
                            toOSrng(pix.x);
                            toOSrng(pix.power);
                            toOSrng(pix.y);
                            if(!quiet) {
                                crosses.add(pix);
                                if(randomImageNumber > 0)
                                    randomImagePixels.add(pix);
                            }
                        }
                        toOSrng((int)(start - lastFound));
                        impact = true;
                    }
                
                if(!quiet) {
                    Iterator<Pixel> iter = crosses.iterator();
                    Pixel pix;
                    graphics.setColor(Color.RED);
                    //boolean imp = false;
                    while(iter.hasNext()) {
                        pix = iter.next();
                        if(start - pix.found > 2000L) {
                            iter.remove();
                            continue;
                        }
                        paintCross(graphics, pix);
                        //imp = true;
                    }
                    /* TODO: Debugging stuff
                    if(imp)
                        try {
                            String fn = String.valueOf(in++);
                            while(fn.length() < 5)
                                fn = "0"+fn;
                            File out = new File("debug/"+fn+".png");
                            ImageIO.write(statImg, "png", out);
                        } catch(IOException e) {
                            e.printStackTrace();
                        }*/
                }
                /*
                 * Write the yellow, transparent text onto the window and update it.
                 */
                if(!quiet) {
                    graphics.setColor(yellow);
                    if(doubleView) {
                        graphics.setFont(font);
                        graphics.drawString("Raw", width / 2 - 25, 25);
                        graphics.drawString("Filtered", statXoffset + (width / 2 - 50), 25);
                    }
                    
                    graphics.setFont(smallFont);
                    int ty = 1;
                    for(String st: statOut)
                        graphics.drawString(st, 5, ty++ * 10);

                    graphics.setColor(Color.RED);
                    getLock(false);
                    if(videoOut != null) {
                        try {
                            ts += avgFPS == 0.0f ? start - lastFound : avgFPS;
                            videoOut.setTimestamp((int) ts); //TODO: DEBUG!
                            videoOut.record(IplImage.createFrom(statImg));
                            lock.set(false);
                        } catch (org.bytedeco.javacv.FrameRecorder.Exception e) {
                            lock.set(false);
                            e.printStackTrace();
                            toggleRecording();
                        }
                        graphics.fillOval(statXoffset + width - 25, height - 25, 20, 20);
                    } else {
                        lock.set(false);
                        graphics.drawOval(statXoffset + width - 25, height - 25, 20, 20);
                    }
                    graphics.setColor(Color.GREEN);
                    if(randomImageNumber > 0)
                        graphics.fillOval(statXoffset + width - 50, height - 25, 20, 20);
                    else
                        graphics.drawOval(statXoffset + width - 50, height - 25, 20, 20);
                    if(!quiet)
                        canvasFrame.showImage(statImg);
                }
                
                if(impact)
                    lastFound = start;
                /*
                 * Release the resources of the frame.
                 */
                img.release();
                firstRun = false;
            }

            try {
                /*
                 * Don't let us burn all CPU in case we're under heavy load.
                 */
                Thread.sleep(2L);
            } catch (InterruptedException e) {}
        }
    }
    //static int in = 0;

    static boolean isVideoButton(int x, int y) {
        int vX = statXoffset + width - 25;
        int vY = height - 25;
        return x >= vX && x <= vX + 20 &&
                y >= vY && y <= vY + 20;
    }
    
    static boolean isImageButton(int x, int y) {
        int vX = statXoffset + width - 50;
        int vY = height - 25;
        return x >= vX && x <= vX + 20 &&
                y >= vY && y <= vY + 20;
    }
}
