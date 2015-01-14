# AtomicRNG
[![Build Status](http://ci.eyrenetwork.net/job/AtomicRNG/badge/icon)](http://ci.eyrenetwork.net/job/AtomicRNG/)

An experimental RNG feeding the Linux RNG with the help of an Alpha-Ray-Visualizer[¹].

This tool is useless without an Alpha-Ray-Visualizer, so build one first.
After that connect it to your PC and run the jar like that (no root access required):
```
java -jar /path/to/AtomicRNG-*-SNAPSHOT.jar
```

After your Alpha-Ray-Visualizer has been initialized (which simply means loading
the webcam and giving it some time for white balance) a window will open.
On the left side of the window you'll see the raw webcam image (with dark noise
as well as noise introduced by EMI if your shielding isn't good enough).
On the right side you'll see the filtered image: All pixels not used to get
randomness are blacked out. All you see on that image is the impact of ions
on the cameras CCD and sometimes, if you're lucky, cherenkov radiation[²]. Both
are true random events.

The windows title will show you the average FPS (which should equal with the FPS
of your webcam) as well as the average numbers generated per second for the last
10 seconds. All these numbers are automatically filled into the kernel entropy
pool by writing them to /dev/random. Random Numbers are generated from the X and
Y coordinates of lighted pixels as well as their brightness and color. These are
translated from the impact coordinates of the alpha ray (+ the blooming[³]
coordinates) and the energy it carried or the light of the cherenkov radiation.
Also the time between images with impacts is measured as you'll surely get
totally black images from time to time.
Some of the numbers are hashed 2 times to get even more numbers.


My average numbers/sec is around ~~1000~~ 2500. This number might depend on the
material used in your Alpha-Ray-Visualizer, its build quality and other factors.


Next steps:<br>
 - Optimize this even more.<br>
 - Work with the raw OpenCV data instead of wrapping it to a BufferedImage.<br>
 - Port to C++.<br>
 - Get rid of the OpenCV library.<br>
 - Port to C.<br>
 - Get rid of all dependencies we might have at that point.<br>
 - Port to kernel-space.<br>


[¹] http://www.inventgeek.com/alpha-radiation-visualizer/<br>
[²] http://en.wikipedia.org/wiki/Cherenkov_radiation<br>
[³] http://en.wikipedia.org/wiki/Blooming_(CCD)
