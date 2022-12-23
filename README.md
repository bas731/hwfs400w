# Mustek S400W iScan Air / ion Air Copy [+E-Post Edition] / Century CPS-A4WF 転写パットリくん A4 Wi-Fiポータブルスキャナー / HALO Wireless Portable Scanner

Last Update: 2017-08-24: updated information

## Mustek S400W iScan Air

The Mustek S400W iScan Air aka ion Air Copy [+E-Post Edition] aka Century CPS-A4WF 転写パットリくん A4 Wi-Fiポータブルスキャナー aka HALO Wireless Portable Scanner
is a simple document scanner for up to A4 wide documents with up to 40.5 cm length (almost ~9570 vertical pixel for 600 DPI!).
Prices range from high (>100 €/$) to very low (30 € incl. VAT) in special sales - like the ion E-Post edition.

Its predecessor is the Mustek S400 iScan docking station for iPads.

It creates its own WIFI network always named *DIRECT_<mac>-<model>* where <mac> are the last six digits
of the scanner's MAC address and the model is `AirCopy` or `iScanAir` etc.
The ip address is always `192.168.18.33` and the scanner service is listening on port `23`
The scanner provides DHCP and only allows clients that successfully aquired an IP via DHCP access to the scanning service.

While it is well suited for what it is advertised for - easily scan a document on the go with your tablet or phone
(especially if you don't have some kind of 'camera scanner' software or the camera of your tablet / phone has
a too low resolution) - the low prices it is sold for warrant a much better usage model. (I did get one for my mom, though ;)
[Connect it to a server](wlan.md) and scan, ocr, pdf, and index documents.

## The Scanner

### Hardware
Manufacturer: Mustek, sold as:

- Mustek iScan Air
  - [https://www.mustek.com.tw/S400W/S400W.html](https://www.mustek.com.tw/S400W/S400W.html)
- ionaudio Air Copy
  - [https://www.ionaudio.com/products/details/air-copy](https://www.ionaudio.com/products/details/air-copy)
  - [https://www.ionaudio.de/air-copy](https://www.ionaudio.de/air-copy)
  - [https://www.conrad.de/ce/de/product/650917/Dokumentenscanner-A4-Mustek-iScan-Air-A4-S400W-600-x-600-dpi-USB-microSD-microSDHC?ref=list](https://www.conrad.de/ce/de/product/650917/Dokumentenscanner-A4-Mustek-iScan-Air-A4-S400W-600-x-600-dpi-USB-microSD-microSDHC?ref=list)
- ionaudio Air Copy E-Post Edition
  - [https://www.meinpaket.de/de/air-copy-e-post-edition/p499782459/](https://www.meinpaket.de/de/air-copy-e-post-edition/p499782459/)
  - [https://www.conrad.de/ce/de/product/1193466/Dokumentenscanner-A4-ION-Audio-Air-Copy-E-Post-Edition-USB-WLAN-80211-bgn](https://www.conrad.de/ce/de/product/1193466/Dokumentenscanner-A4-ION-Audio-Air-Copy-E-Post-Edition-USB-WLAN-80211-bgn)
- Century CPS-A4WF 転写パットリくん A4 Wi-Fiポータブルスキャナー
  - [https://www.century.co.jp/products/pc/cat420/cps-a4wf.html](https://www.century.co.jp/products/pc/cat420/cps-a4wf.html)
- HALO Wireless Portable Scanner for Photos and Documents
  - [https://www.qvc.com/HALO-Wireless-Portable-Scanner-for-Photos-and-Documents.product.E224976.html](https://www.qvc.com/HALO-Wireless-Portable-Scanner-for-Photos-and-Documents.product.E224976.html)


### Pictures / Data

- [FCC](https://fccid.net/number.php?fcc=HWFS400W&id=336275)
- [Bottom](assets/images/img_1290.jpg)
- [Inside](assets/images/img_1291.jpg)
- [Inside Closeup](assets/images/img_1292.jpg)

To open the scanner you need to remove 5 Phillips screws, 4 located beneath the rubber feet, and one beneath the white plug thing in the middle.
There is a 6th vicious tri-wing screw beneath the FCC label.
Note the smiley drawn with ballpoint pen on mine, some assembly worker knowing the casual hacker will have a nasty surprise here.
It's possible to remove it with a thin flat head screwdriver if you are careful.

The scanner is powered by a Zoran Quatro 4310 chip that can run a custom zoran OS or Linux. The memory chip provides 64 MiB RAM.

There is an unsoldered mini USB connector pad. I tried to connect it to a pc after soldering a cable there, didn't work.
No idea if this is a USB host or needs to be enabled via software. I have not yet tried the JTAG/serial pads either.

The battery is fairly large, but not required for the scanner to work.
I have not determined yet if calibration settings persist without battery.

It's also interesting to see that the power button is actually two buttons.

UART is on TP26 = receive and TP25 = transmit. Settings are 57600,8,n,1,xon/xoff for putty.
The console is non interactive and there is some interesting info while:
- [booting](assets/logs/uart_boot.txt),
- [connecting](assets/logs/uart_dhcp.txt), and
- [scanning](assets/logs/uart_scan.txt).

The device seems to have a unpopulated SD card slot right next to the CPU. There is also an unsoldered USB port,
if a keyboard is connected to it, then one can press "a" to abort normal booting
and an attempt is made to load the firmware from SD card.<p>


### Software

The original software is very, very simple. You can't do more than take a scan and save it as jpeg or pdf or clean/calibrate the scanner.
No OCR, no multi page PDF. Mustek's software is multi language, 3rd party usually not.
Unfortunately, the windows version doesn't understand 3rd party hardware model codes.
iOS / Android apps do not check the model and work fine.

- Appstore: iOS, OS X, Androind, Win8, Win Phone 8
  earch for iscan air, air copy
- Windows: [https://www.mustek.com.tw/S400W/S400W.html](https://www.mustek.com.tw/S400W/S400W.html)
  [ftp://ftp.mustek.com.tw/pub/driver/iScanAir/](ftp://ftp.mustek.com.tw/pub/driver/iScanAir/)
- Windows: [https://www.ionaudio.com/products/details/air-copy](https://www.ionaudio.com/products/details/air-copy)
  [https://www.ionaudio.com/downloads/Air_Copy_for_Windows_7_v1.0.3.zip](https://www.ionaudio.com/downloads/Air_Copy_for_Windows_7_v1.0.3.zip)
- Windows: [https://www.century.co.jp/support/download/iscanair-win7vx.html](https://www.century.co.jp/support/download/iscanair-win7vx.html)
  [https://www.century.co.jp/support/download/lib/iScanAir_win7_vista_xp.exe](https://www.century.co.jp/support/download/lib/iScanAir_win7_vista_xp.exe)

## Custom Access

### Download

I am providing free source code and applications:
- a sleek [java library](code/java.zip) supporting everything.
- a simple java [command line scanner](code/java.zip) for demonstration purposes.
- a simple [c library](code/c.zip) supporting everything, too.
- a simple [command line scanner](code/c.zip) with binaries for [windows and linux](release/).

The java version needs a few changes I figured out while implementing the c version.
I have to admit that I had not coded C for around a decade before implementing this...


## Specification

See [Specification](specification.md)
