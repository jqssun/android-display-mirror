package airplaylib

import (
	"fmt"
	"net"
	"os"
	"strconv"
	"sync"
	"time"

	"doubletake/internal/airplay"
)

// SocketProvider lets the host app supply sockets that reach the LAN when a VPN would otherwise swallow them; return -1 to fall back to the default network stack, any other value is an fd the library takes ownership of
type SocketProvider interface {
	DialTCP(host string, port int, timeoutMs int) (int, error)
	ListenUDP(host string, port int) (int, error)
}

var (
	lanMu       sync.Mutex
	lanProvider SocketProvider
	lanHost     string
)

func SetSocketProvider(p SocketProvider) {
	lanMu.Lock()
	lanProvider = p
	lanMu.Unlock()
}

// UDP listeners don't know the peer, so the session records which receiver they serve
func setLanHost(host string) {
	lanMu.Lock()
	lanHost = host
	lanMu.Unlock()
}

func lanState() (SocketProvider, string) {
	lanMu.Lock()
	defer lanMu.Unlock()
	return lanProvider, lanHost
}

func init() {
	airplay.LanDialHook = lanDialHook
	airplay.LanListenPacketHook = lanListenPacketHook
}

func lanDialHook(network, address string, timeout time.Duration) (net.Conn, error) {
	p, _ := lanState()
	if p == nil {
		return nil, nil
	}
	host, portStr, err := net.SplitHostPort(address)
	if err != nil {
		return nil, nil
	}
	port, _ := strconv.Atoi(portStr)
	ms := int(timeout / time.Millisecond)
	if ms <= 0 {
		ms = 10000
	}
	fd, err := p.DialTCP(host, port, ms)
	if err != nil {
		return nil, fmt.Errorf("dial %s via LAN socket: %w", address, err)
	}
	if fd < 0 {
		return nil, nil
	}
	f := os.NewFile(uintptr(fd), "lan-tcp")
	defer f.Close()
	return net.FileConn(f)
}

func lanListenPacketHook(network, address string) (net.PacketConn, error) {
	p, host := lanState()
	if p == nil || host == "" {
		return nil, nil
	}
	_, portStr, err := net.SplitHostPort(address)
	if err != nil {
		return nil, nil
	}
	port, _ := strconv.Atoi(portStr)
	fd, err := p.ListenUDP(host, port)
	if err != nil {
		return nil, fmt.Errorf("listen udp %s via LAN socket: %w", address, err)
	}
	if fd < 0 {
		return nil, nil
	}
	f := os.NewFile(uintptr(fd), "lan-udp")
	defer f.Close()
	return net.FilePacketConn(f)
}
