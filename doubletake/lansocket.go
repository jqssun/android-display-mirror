package airplay

import (
	"context"
	"net"
	"time"
)

/*
socket hooks so the host app can hand over sockets that bypass a VPN

on Android a non-bypassable VPN drops LAN replies for the app UID, so the app asks a privileged helper (Shizuku) for sockets bound to the LAN interface; a nil hook or a (nil, nil) result means "use the default network stack"
*/
var (
	LanDialHook         func(network, address string, timeout time.Duration) (net.Conn, error)
	LanListenPacketHook func(network, address string) (net.PacketConn, error)
)

func lanDial(network, address string, timeout time.Duration) (net.Conn, error) {
	if hook := LanDialHook; hook != nil {
		conn, err := hook(network, address, timeout)
		if conn != nil || err != nil {
			return conn, err
		}
	}
	return nil, nil
}

func lanDialTimeout(network, address string, timeout time.Duration) (net.Conn, error) {
	if conn, err := lanDial(network, address, timeout); conn != nil || err != nil {
		return conn, err
	}
	return net.DialTimeout(network, address, timeout)
}

func lanDialContext(ctx context.Context, d *net.Dialer, network, address string) (net.Conn, error) {
	timeout := d.Timeout
	if deadline, ok := ctx.Deadline(); ok {
		if left := time.Until(deadline); timeout == 0 || left < timeout {
			timeout = left
		}
	}
	if conn, err := lanDial(network, address, timeout); conn != nil || err != nil {
		return conn, err
	}
	return d.DialContext(ctx, network, address)
}

func lanListenPacket(network, address string) (net.PacketConn, error) {
	if hook := LanListenPacketHook; hook != nil {
		conn, err := hook(network, address)
		if conn != nil || err != nil {
			return conn, err
		}
	}
	return net.ListenPacket(network, address)
}
