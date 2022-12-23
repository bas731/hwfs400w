# Mustek S400W iScan Air Wifi How-To

I am running debian. So apt-get will appear in this document. Your mileage may vary.
Resoures used:
- /usr/share/doc/wpasupplicant/README.Debian.gz
- https://ubuntuforums.org/showthread.php?t=1259003
- https://manual.siduction.org/inet-wpa
- https://unix.stackexchange.com/questions/182967/can-i-prevent-a-default-route-being-added-when-bringing-up-an-interface

## Prerequisites

### Hardware

If your board has an internal free PCIe or mini-PCIe slot, you can us this, otherwise just get a cheap wlan stick.
In any case you'll need 802.11n, WPA2 capability, and linux support.

### Software

- You'll probably need firmwares (adjust /etc/apt/sources.lst to main non-free contrib):
	`apt-get install firmware-linux firmware-linux-free firmware-linux-nonfree firmware-ralink`
- We need wpasupplicant:
	`apt-get install wpasupplicant`
- Might also come in handy:
	`apt-get install wireless-tools ifupdown`

Avoid endless waiting at boot:
```sh
mkdir /etc/systemd/system/networking.service.d
edit /etc/systemd/system/networking.service.d/reduce-timeout.conf
```

```ini
[Service]
TimeoutStartSec=60 # or 30
```

DHCP is necessary so if you don't have one: `apt-get install dhclient`
*/etc/dhcp/dhclient.conf* should not have interface entries if you can avoid it
(they will be renewed when our wlan connects, not really problem, just annoying).


If you have dhcp running with a router and you have no resolvconf package installed, you might want to protected
*/etc/resolv.conf*: ```chmod +i /etc/resolv.conf```
If your dhcp client can be configured to not overwrite it, then you don't need it.

If you have or want (```apt-get install resolvconf```) to use resolvconf:
edit */etc/resolvconf/interface-order* and make sure that wlan entries comes after eth.
(If you have a bridge setup, e.g. you have more than ethernet port and use it as switch, don't forget to add a _br*_ wildcard).

## Connecting to the scanner with wpa_supplicant

Ok, dhcp and resolving is set up, lets get started with wpa!
What we want is to detect a turned on scanner within 30 seconds and if it is turned off, notice that fairly quickly, too.
So that interfaces and dhcp leases can be removed (dhcp lease time is rather big, but needs to be redone every time
we connect to the scanner). The older tutorial did this all by hand, but debian has support for this built in
(and probably most other distro): **wifi roaming**!
I'll append the old script at the end, but it never worked as well as the inbuilt stuff.

Note: This documentation assumes our wlan is *wlan0*

### Roaming wpa_supplicant.conf

Create */etc/wpa_supplicant/wpa_supplicant.conf* if not already there, and edit it.
Alter the ssid to match your *DIRECT-<6 digits>-<AirCopy|iScanAir|etc>* and set the correct country:
```ini
# "roaming" config for mustek s400w scanner
update_config=1
ctrl_interface=DIR=/run/wpa_supplicant GROUP=netdev
country=DE
fast_reauth=1
eapol_version=1
ap_scan=1
filter_ssids=1
autoscan=periodic:30
network={
	id_str="s400w"
	ssid="DIRECT-ABCDEF_AirCopy"
	proto=WPA2
	psk="12345678"
	ap_max_inactivity=20
	key_mgmt=WPA-PSK
	pairwise=CCMP
	group=CCMP
	#disabled=1
}
```

Protect it: ```chmod 0600 /etc/wpa_supplicant/wpa_supplicant.conf```

### network/interfaces

edit */etc/network/interfaces* and add the following (choose the right driver):
```
allow-hotplug wlan0
iface wlan0 inet manual
	wpa-driver  nl80211,wext
	wpa-roam    /etc/wpa_supplicant/wpa_supplicant.conf

iface s400w inet dhcp
```

Protect it: ```chmod 0600 /etc/network/interfaces```

restart networking and ifup / down wlan0, maybe reboot:
```sh
ifdown wlan0
/etc/init.d/networking restart
ifup wlan0
```


### Routes

Problem 1: *wlan0* must not overwrite our default route.
A solution for dhclient is to create a file in */etc/dhcp/dhclient-enter-hooks.d*:
edit */etc/dhcp/dhclient-enter-hooks.d/s400w*:
```sh
#!/bin/sh
## prevent DHCP server on wlan0 from forcing a default route on us
case "${reason}.${interface}" in
	RENEW.wlan0|BOUND.wlan0)
		ip route delete default via $new_routers
		#new_routers = ""
		;;
	*)
		;;
esac
```

```chmod 0644 /etc/dhcp/dhclient-enter-hooks.d/s400w``


### Firewall

Problem 2: if somebody fakes being the scanner, the system will happily connect to it and make it vulnerable.
If you don't have a firewall already, you can use:
```sh
iptables -o wlan0 -A OUTPUT -p all -m conntrack --ctstate NEW,ESTABLISHED -j ACCEPT
iptables -i wlan0 -A INPUT  -m conntrack --ctstate RELATED,ESTABLISHED -j ACCEPT
iptables -i wlan0 -A INPUT  -m conntrack --ctstate INVALID -j DROP
iptables -i wlan0 -A INPUT  -p udp -j REJECT --reject-with icmp-port-unreachable
iptables -i wlan0 -A INPUT  -p tcp -j REJECT --reject-with tcp-reset
iptables -i wlan0 -A INPUT  -j REJECT --reject-with icmp-proto-unreachable
```

Save it: ```iptables-save >/etc/iptables.s400w.rules```

edit */etc/rc.local* and add
```sh
/sbin/iptables-restore < /etc/iptables.s400w.rules
```
