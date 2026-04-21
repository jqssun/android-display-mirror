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
		s.logf("[AIRPLAY] pairing failed: %v", err)
		if pinErr := client.StartPINDisplay(); pinErr != nil {
			s.logf("[AIRPLAY] StartPINDisplay failed: %v", pinErr)
		}
		client.Close()
		s.handler.OnPinRequired()
		return err
	}
	s.logf("[AIRPLAY] pairing succeeded")

	if err := client.FairPlaySetup(ctx); err != nil {
		s.logf("[AIRPLAY] FairPlay setup FAILED: %v", err)
	} else {
		s.logf("[AIRPLAY] FairPlay setup succeeded")
	}
	return nil
}
