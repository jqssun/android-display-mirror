package airplaylib

import (
	"context"
	"errors"

	"doubletake/internal/airplay"
)

var credStore *airplay.CredentialStore

// pairing credentials live in app-private storage
func SetCredentialsPath(path string) error {
	cs, err := airplay.NewCredentialStore(path)
	if err != nil {
		return err
	}
	credStore = cs
	return nil
}

// failed transient leaves legacy receivers in error, so start with authenticated SRP
func passwordRequiresPairing(info *airplay.ReceiverInfo) bool {
	return info.RequiresPassword() && (info.RequiresPINPairing() || info.PrefersLegacyPairing())
}

func (s *Session) setupAirPlay2(ctx context.Context, client *airplay.AirPlayClient) (*airplay.ReceiverInfo, error) {
	info, err := client.GetInfo()
	if err != nil {
		return nil, s._setupFailed(client, "getinfo: ", err)
	}
	s.logf("[AIRPLAY2] connected to: %s (model: %s)", info.Name, info.Model)
	s.logf("[AIRPLAY2] features %#016x, statusFlags %#x", info.Features, info.StatusFlags)

	var saved *airplay.SavedCredentials
	if credStore != nil {
		saved = credStore.Lookup(info.DeviceID)
	}

	if saved != nil && saved.HasPairingCredentials() {
		s.logf("[AIRPLAY2] using saved credentials")
		pub, priv := saved.Ed25519Keys()
		client.PairingID = saved.PairingID
		client.PairKeys = &airplay.PairKeys{Ed25519Public: pub, Ed25519Private: priv}
		if err := client.PairVerify(ctx); err != nil {
			s.logf("[AIRPLAY2] pair-verify with saved creds failed: %v, re-pairing", err)
			// the failed pair-verify may have closed the connection
			client.Close()
			if err := client.Connect(ctx); err != nil {
				return nil, s._setupFailed(client, "reconnect: ", err)
			}
			if _, err := client.GetInfo(); err != nil {
				return nil, s._setupFailed(client, "getinfo after reconnect: ", err)
			}
			if err := s._pair(ctx, client, info); err != nil {
				return nil, err
			}
		}
	} else if err := s._pair(ctx, client, info); err != nil {
		return nil, err
	}
	s.logf("[AIRPLAY2] pairing complete")

	// ekey/eiv for the encrypted mirror stream; receivers without FairPlay SAP fall through to the pair-verify DataStream keys
	if client.FpEkey == nil {
		if err := client.FairPlaySetup(ctx); err != nil {
			if !errors.Is(err, airplay.ErrFairPlayUnsupported) {
				return nil, s._setupFailed(client, "fairplay setup: ", err)
			}
			s.logf("[AIRPLAY2] FairPlay SAP unsupported (%v); continuing with pair-verify DataStream setup", err)
		} else {
			s.logf("[AIRPLAY2] FairPlay setup complete")
		}
	}
	return info, nil
}

// pairs on existing connection
func (s *Session) _pair(ctx context.Context, client *airplay.AirPlayClient, info *airplay.ReceiverInfo) error {
	pairWith := func(reveal bool) error {
		p, err := s._credential(ctx, client, reveal)
		if err != nil {
			return err
		}
		if err := client.Pair(ctx, p); err != nil {
			return s._setupFailed(client, "pairing failed: ", err)
		}
		s._saveCredentials(info.DeviceID, client)
		return nil
	}

	switch {
	case passwordRequiresPairing(info):
		return pairWith(false)
	case info.RequiresPINPairing():
		return pairWith(true)
	}

	// answered by digest challenge at SETUP
	if info.RequiresPassword() {
		p, err := s._credential(ctx, client, false)
		if err != nil {
			return err
		}
		client.SetPassword(p)
	}
	if err := client.Pair(ctx, ""); err != nil {
		s.logf("[AIRPLAY2] transient pairing failed: %v, prompting for PIN", err)
		return pairWith(true)
	}
	return nil
}

func (s *Session) _saveCredentials(deviceID string, client *airplay.AirPlayClient) {
	if credStore == nil {
		return
	}
	if err := credStore.Save(deviceID, client.PairingID, client.PairKeys.Ed25519Public, client.PairKeys.Ed25519Private); err != nil {
		s.logf("[AIRPLAY2] failed to save credentials: %v", err)
		return
	}
	s.logf("[AIRPLAY2] credentials saved")
}

func (s *Session) _setupFailed(client *airplay.AirPlayClient, prefix string, err error) error {
	s.handler.OnError(prefix + err.Error())
	client.Close()
	return err
}
