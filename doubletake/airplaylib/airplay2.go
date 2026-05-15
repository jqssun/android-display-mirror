package airplaylib

import (
	"context"

	"doubletake/internal/airplay"
)

func SetAppleReceiver(enabled bool) {
	airplay.AppleReceiver = enabled
}

func (s *Session) setupAirPlay2(ctx context.Context, client *airplay.AirPlayClient, pin string) error {
	if _, err := client.GetInfo(); err != nil {
		s.handler.OnError("getinfo: " + err.Error())
		client.Close()
		return err
	}
	if err := client.Pair(ctx, pin); err != nil {
		s.logf("[AIRPLAY2] pairing failed: %v", err)
		if pinErr := client.StartPINDisplay(); pinErr != nil {
			s.logf("[AIRPLAY2] StartPINDisplay failed: %v", pinErr)
		}
		client.Close()
		s.handler.OnPinRequired()
		return err
	}
	s.logf("[AIRPLAY2] pairing succeeded")

	if err := client.FairPlaySetup(ctx); err != nil {
		s.logf("[AIRPLAY2] FairPlay setup failed: %v", err)
	} else {
		s.logf("[AIRPLAY2] FairPlay setup succeeded")
	}
	return nil
}
