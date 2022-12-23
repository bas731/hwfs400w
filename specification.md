# Mustek S400W Specification

## Communication

- Optional: Check if your WIFI is connected to a SSID 'DIRECT-<mac>_<model>'
- Optional: Check if <mac> matches MAC address of 192.168.33.18
- Open TCP connection to 192.168.33.18:23
- Close connection after finishing action: status, calibration, cleaning, scanning, resolution, unless chained together (aka status + resolution + scan)
- Socket timeout 60 seconds
- A small delay is necessary after sending a command (200 - 500ms).
- A small delay is necessary after reading a response(100 - 500ms) before issuing a new command.
- Polling is not recommended, please use non blocking sockets and select.
	For example if you start a scan, then issue get jpeg size, you will have to wait for
	io on the socket for a while, until the scan finishes.


## Legend
- <byte>: octet bits
- <number>: 4 byte, little endian
- <mac>: 6 characters making up hexadecimal number
- <model>: 'AirCopy' | 'iScanAir' | ?


## Responses

Are usually less than 16 <byte>s (except preview and jpeg data), so a buffer of 16 bytes is enough.
Known responses map to ascii strings for the first n <byte> followed by padding (which can be ignored):

```devbusy```
  Device is busy. Generic, so abort action, or try again later (if you want to read status).
  Shouldn't happen while you wait for a response, only if you are connecting to an already busy device.

```battlow```
  Battery low. Not sure yet if only in idle, or as response on other requests.

```nopaper```
  Device idle. Nothing inserted, thus error for scan, calibrate, clean.

```scanready```
  Paper sensor triggered. Ready to scan, calibrate or clean.

```calgo```
  Device busy, calibration has started.

```calibrate```
  Device busy, calibration finished.

```cleango```
  Device busy, cleaning has started.

```cleanend```
  Device busy, cleaning finished.

```dpistd```
  Resolution set to 300 DPI.

```dpifine```
  Resolution set to 600 DPI.
  Note that resolution is sometimes reset to 300 DPI after a successful scan!

```scango```
  Device busy, scanning has started.

### Special Responses

```previewend```
  Preview finished.

```jpegsize```
  Jpeg size aka jpeg available.


## Commands

Send as <number>s.
Waiting after sending a command is recommended. Between 200 ms and 500 ms.
Waiting some more after a function finished (cleaning, calibration, scanning) is recommended, too. 500 ms.

```20203030```: get version
  read: {1; 9} <byte>; known response = error

  Example: IO0a.032
  - ASCII string
  - first 2 <byte> = manufacturer code, known: "NB" = Mustek and "IO" ion
  - bytes after the '.' are a decimal number aka firmware version. Versions >=26 support 600 DPI.

```50006000```: get status
  wait 200ms
  read: known response
  - ```battlow```
  - ```nopaper```
  - ```devbusy```
  - ```scanready```

```70708080```: clean (requires status ```scanready```)
  wait 500ms
  read ```cleango```
  wait 10s
  read ```cleanend```; bad: ```nopaper```, ```devbusy```, ```battlow```


- ```a000b000```: calibrate (requires status ```scanready```)
  wait 500ms
  read ```calgo```
  wait 10s
  read ```calibrate```; bad: ```nopaper```, ```devbusy```, ```battlow```


- ```10203040```: set DPI 300
  wait 200ms
  read ```dpistd```


- ```50607080```: set DPI 600
  wait 200ms
  read ```dpifine```


- ```10002000```: start scan
  wait 200ms
  read ```scango```


- ```30304040```: send preview data
  wait 1s
  read n * 1920 <byte> (640 pixel per line, 3 <byte> per pixel: RGB, most likely 1200 lines max)
  ends on ```previewend``` <byte>
  wait 1s


- ```c000d000```: send jpeg size
  wait 200ms
  read ```jpegsize``` <number>


- ```e000f000```: send jpeg data
  wait 500ms
  read $jpegsize <byte>

### Unofficial Commands

```40405050```: WifiBattery State
  read ~0x287 or 0x284 or so = usb power, ~0x240 = battery full?

```30004000```: unknown
  seems to expect more data

```70008000```: DevpowerOff
  Turns of device


## Example

How to get a 300 DPI scan:

1. open socket
2. ```50006000```
3. if response not ```scanready```, abort
4. ```10002000```
5. if response not ```scango```, abort
6. ```c000d000```
7. if response not ```jpegsize```, abort
8. allocate file for given size
9. ```e000f000```
10. read jpeg data

