# Stable Baki address

## Current

`baki.local` is published by the `baki-mdns.service` system service, which follows the
laptop's current Wi-Fi IP. Verified on the laptop:

```text
baki.local -> 192.168.1.32
```

The service replaces the old static `/etc/avahi/hosts` alias, which collided with Avahi's
existing host record.

## Recommended local setup

1. Reserve the laptop's Wi-Fi MAC address in the router's DHCP table.
2. Add a local DNS record in the router if available:

```text
baki.lan -> <reserved laptop IP>
```

Use that name for the APK page and the app server:

```text
http://baki.lan:8787
http://baki.lan:8787/dl
```

`.local` is the fallback, not the primary, because Android browser/OS mDNS support varies.

## Tailscale (only if away-from-home access is needed)

Pros:

- stable private MagicDNS name, e.g. `baki.tailnet.ts.net`
- works outside the house
- encrypted, no port forwarding or public exposure
- no router changes

Cons:

- account, app install and login on phone and laptop
- always-on VPN/battery cost
- depends on Tailscale availability
- extra layer to debug if discovery/sync fails

Use Tailscale Serve/Funnel only after deciding whether the app should be reachable outside
the home network. Do not expose port 8787 directly to the internet.

## APK

The APK should be linked through the stable name, not a hard-coded IP. The app's server
address should use the same name.
