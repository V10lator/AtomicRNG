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
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

import net.jpountz.xxhash.XXHash64;
import net.jpountz.xxhash.XXHashFactory;

import org.bytedeco.javacv.CanvasFrame;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.OpenCVFrameGrabber;
import org.bytedeco.javacpp.avcodec;
import org.bytedeco.javacpp.avutil;
import org.bytedeco.javacpp.opencv_core.IplImage;

public class AtomicRNG {
    private static XXHash64 xxHash = null;
    private static OpenCVFrameGrabber atomicRNGDevice;
    private static FileWriter osRNG = null;
    private static String version;
    private static final int filter = 255;

    static Random rand = new Random();
    private static boolean randSecure = false;
    private static int hashCount = 0;
    private static long numCount = 0;

    private static PixelGroup[][] lastPixel;
    private static FFmpegFrameRecorder videoOut = null;

    private static int height = 0;
    private static int width = 0;
    private static int statXoffset = 0;
    private static int statWidth = 0;

    static boolean firstRun = true;

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
    private static void toOSrng(long number) {
        /*
         * If this is the first number we got use it as seed for the internal RNG and exit.
         */
        if(!randSecure) {
            rand.setSeed(System.currentTimeMillis() * number);
            randSecure = true;
            return;
        }

        /*
         * Hash the numbers 2 times with different random seeds and mix the hashes randomly.
         */
        ByteBuffer numberBuffer = ByteBuffer.wrap(Long.toHexString(number).getBytes());
        long out = xxHash.hash(numberBuffer, rand.nextLong());
        numberBuffer.flip();
        number = xxHash.hash(numberBuffer, rand.nextLong());
        int r = rand.nextInt(100);
        if(r < 34)
            out += number;
        else if(r < 67) {
            if(out < number) {
                number -= out;
                out = number;
            } else
                out -= number;
        } else
            out = (out / 2) + (number / 2);

        hashCount += 2;
        /*
         * From time to time use the result to re-seed the internal RNG and exit.
         */
        if(rand.nextInt(100) < 5) {
            rand.setSeed(out);
            return;
        }
        /*
         * Write the result to /dev/random and update the statistics.
         */
        String ret = Long.toBinaryString(out);
        getLock(false);
        try {
            osRNG.write(ret);
            numCount += ret.length();
        } catch (IOException e) {
            e.printStackTrace();
        }
        lock.set(false);
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
            /*
             * Flush and close /dev/random.
             */
            if(!getLock(true)) { // Assume crash
                System.err.println("error!");
                return;          // And do nothing;
            }
            if(osRNG != null) {
                try {
                    osRNG.flush();
                    osRNG.close();
                } catch(IOException e) {
                    e.printStackTrace();
                }
            }
            if(videoOut != null) {
                lock.set(false);
                toggleRecording();
            } else
                lock.set(false);
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
                videoOut = null;
            }
        } catch (org.bytedeco.javacv.FrameRecorder.Exception e) {
            lock.set(false);
            e.printStackTrace();
            System.exit(1);
        }
        lock.set(false);
    }

    private static ArrayList<Integer> getOrCreate(int key, HashMap<Integer, ArrayList<Integer>> map) {
        ArrayList<Integer> list = map.get(key);
        if(list == null) {
            list = new ArrayList<Integer>();
            map.put(key, list);
        }
        return list;
    }

    private static Pixel filter(int x, int y, ByteBuffer buffer, int wS, int nC, HashMap<Integer, ArrayList<Integer>> ignore, Pixel pixel) {
        int index = (y * wS) + (x * nC);
        int[] bgr = new int[3];
        for(int i = 0; i < 3; i++)
            bgr[i] = buffer.get(index + i) & 0xFF;

            PixelGroup pixelGroup;
            if(firstRun)
                lastPixel[x >> 5][y >> 5] = new PixelGroup(filter);
            pixelGroup = lastPixel[x >> 5][y >> 5];
            int strength = pixelGroup.getStrength(bgr);
            if(strength == 0 || firstRun)
                return pixel;
            if(pixel == null)
                pixel = new Pixel(x, y, strength);
            else
                pixel.charge(x, y, strength);
            getOrCreate(y, ignore).add(x);
            raytrace(x, y, buffer, wS, nC, ignore, pixel);
            return pixel;
    }

    private static void raytrace(int x, int y, ByteBuffer buffer, int wS, int nC, HashMap<Integer, ArrayList<Integer>> ignore, Pixel pixel) {
        ArrayList<Integer> yList;
        x -= 1;
        y -= 1;
        for(int ym = y; ym < y + 3; ym++) {
            if(ym < 0 || ym >= height)
                continue;
            yList = getOrCreate(ym, ignore);
            for(int xm = x; xm < x + 3; xm++) {
                if(xm < 0 || xm >= width)
                    continue;
                if(!yList.contains(xm))
                    filter(xm, ym, buffer, wS, nC, ignore, pixel);
            }
        }
    }
    
    private static void paintCross(Graphics g, int x, int y) {
        g.drawLine(x - 5, y - 5, x + 5, y + 5);
        g.drawLine(x + 5, y - 5, x - 5, y + 5);
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
        boolean quiet = false/*, experimentalFilter = false*/;
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
         * Initialize the fastest available xxHash algorithm.
         */
        xxHash = XXHashFactory.fastestInstance().hash64();

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
         *  Throw away the first 5 seconds cause of hardware init.
         */
        long realStart = System.currentTimeMillis();
        while(System.currentTimeMillis() - realStart < 5000L) {
            try {
                atomicRNGDevice.grab().release();
            } catch (org.bytedeco.javacv.FrameGrabber.Exception e) {
                restartAtomicRNGDevice(e);
            }
        }

        /*
         *  Open the Linux RNG and keep it open all the time.
         *  We close it in Cleanup().run().
         */
        File osRNGfile = new File("/dev/random");
        if(!osRNGfile.exists() || osRNGfile.isDirectory() || !osRNGfile.canWrite()) { // isDirectory() cause isFile() returns false.
            System.out.println("error ("+osRNGfile.exists()+"/"+(!osRNGfile.isDirectory())+"/"+osRNGfile.canWrite()+") !");
            System.exit(1);
        }
        getLock(false);
        try {
            osRNG = new FileWriter(osRNGfile);
        } catch (IOException e) {
            System.out.println("error!");
            e.printStackTrace();
            System.exit(1);
        }
        lock.set(false);

        /*
         * In case we should draw the window initialize it and set its title.
         */
        String title = null;
        CanvasFrame canvasFrame = null;
        if(!quiet) {
            title = "AtomicRNG v"+version+" | FPS: X.X | Numbers/sec: Y.Y (Z.Z hashes/sec)";
            canvasFrame = new CanvasFrame(title);
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
        int fpsCount = 0, strength,
                pixelGroupX, pixelGroupY, lastPixelGroupX = -1, lastPixelGroupY = -1;
        int black = Color.BLACK.getRGB();
        int white = Color.WHITE.getRGB();
        Color yellow = new Color(1.0f, 1.0f, 0.0f, 0.1f);
        BufferedImage statImg = null;
        Font font = new Font("Arial Black", Font.PLAIN, 18);
        long lastFound = System.currentTimeMillis();
        long lastSlice = lastFound;
        PixelGroup pixelGroup = null;
        /*
         * All right, let's enter the matrix, eh, the main loop I mean...
         */
        realStart = System.currentTimeMillis();
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
                 * After each ten seconds...
                 */
                if(start - lastSlice >= 10000L) {
                    /*
                     * ...update the windows title with the newest statistics...
                     */
                    if(!quiet) {
                        canvasFrame.setTitle(title.replaceAll("X\\.X", String.valueOf((float)fpsCount/10.0f)).replaceAll("Y\\.Y", String.valueOf((float)numCount/10.0f)).replaceAll("Z\\.Z", String.valueOf((float)hashCount/10.0f)));
                        numCount = hashCount = fpsCount = 0;
                    }
                    /*
                     * prepare to count the next 10 seconds and flush /dev/random.
                     */
                    lastSlice = start;
                    getLock(false);
                    try {
                        osRNG.flush();
                    } catch (IOException e) {
                        e.printStackTrace(); //TODO Handle or ignorable?
                    }
                    lock.set(false);
                }

                /*
                 * The width is static, so if it's zero we never asked for it and other infos.
                 * Let's do that.
                 */
                if(firstRun) {
                    width = img.width();
                    height = img.height();
                    lastPixel = new PixelGroup[width >> 5][height >> 5];
                    /*
                     * Calculate the needed window size and paint the red line in the middle.
                     */
                    if(!quiet) {
                        statXoffset = width + 2;
                        statWidth = statXoffset + width;
                        statImg = new BufferedImage(statWidth, height, BufferedImage.TYPE_3BYTE_BGR);
                        for(int x = width; x <= statXoffset; x++)
                            for(int y = 0; y < height; y++)
                                statImg.setRGB(x, y, Color.RED.getRGB());
                        if(!quiet)
                            canvasFrame.setCanvasSize(statWidth, height);
                    }
                }
                Graphics graphics = null;
                if(!quiet) {
                    graphics = statImg.getGraphics();
                    graphics.drawImage(img.getBufferedImage(), 0, 0, null);
                    graphics.setColor(Color.BLACK);
                    graphics.fillRect(statXoffset, 0, statXoffset + width, height);
                }

                /*
                 * Wrap the frame to a Java BufferedImage and parse it pixel by pixel.
                 */
                int[] bgr = new int[3];
                ByteBuffer buffer = img.getByteBuffer();
                int index;
                HashMap<Integer, ArrayList<Integer>> ignoreBlocks = new HashMap<Integer, ArrayList<Integer>>();
                ArrayList<Integer> yList;
                ArrayList<Pixel> impacts = new ArrayList<Pixel>();
                Pixel pixel;
                for(int y = 0; y < height; y++) {
                    for(int x = 0; x < width; x++) {
                        if(ignoreBlocks.containsKey(y)) {
                            yList = ignoreBlocks.get(y);
                            if(yList.contains(x))
                                continue;
                        }
                        pixel = filter(x, y, buffer, img.widthStep(), img.nChannels(), ignoreBlocks, null);
                        if(pixel != null)
                            impacts.add(pixel);

                        /*
                         * If there's data highlight the pixel on the filtered image.
                         *
                        if(!impacts.isEmpty()) {
                            for()
                                statImg.setRGB(statXoffset + x, y, white);
                        }
                        /*
                         * If we got data on that frame get the ms since this was the case last time and feed it to /dev/random.
                         */
                    }
                }
                
                if(!quiet)
                    graphics.setColor(Color.RED);
                
                if(!impacts.isEmpty()) {
                    for(Pixel pix: impacts) {
                        toOSrng(pix.x);
                        toOSrng(pix.power);
                        toOSrng(pix.y);
                        if(!quiet)
                            paintCross(graphics, statXoffset + pix.x, pix.y);
                    }
                    toOSrng((int)(start - lastFound));
                    lastFound = start;
                }
                /*
                 * Write the yellow, transparent text onto the window and update it.
                 */
                if(!quiet) {
                    graphics.setColor(yellow);
                    graphics.setFont(font);
                    graphics.drawString("Raw", width / 2 - 25, 25);
                    graphics.drawString("Filtered", statXoffset + (width / 2 - 50), 25);

                    graphics.setColor(Color.RED);
                    getLock(false);
                    if(videoOut != null) {
                        try {
                            //videoOut.setTimestamp(start - realStart); //TODO: DEBUG!
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
                    if(!quiet)
                        canvasFrame.showImage(statImg);
                }
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

    static boolean isVideoButton(int x, int y) {
        int vX = statXoffset + width - 25;
        int vY = height - 25;
        return x >= vX && x <= vX + 20 &&
                y >= vY && y <= vY + 20;
    }
}
