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

/*
upstream pairing flow: full pair-setup when caller supplies PIN, else pair-verify against saved credentials, else transient pairing. skipping the credential store makes every connect a fresh pair-setup, which Apple receivers answer with another PIN prompt
*/
func (s *Session) setupAirPlay2(ctx context.Context, client *airplay.AirPlayClient, pin string) (*airplay.ReceiverInfo, error) {
	info, err := client.GetInfo()
	if err != nil {
		return nil, s._setupFailed(client, "getinfo: ", err)
	}
	s.logf("[AIRPLAY2] connected to: %s (model: %s)", info.Name, info.Model)
	s.logf("[AIRPLAY2] features %#016x, statusFlags %#x", info.Features, info.StatusFlags)

	var saved *airplay.SavedCredentials
	if pin == "" && credStore != nil {
		saved = credStore.Lookup(info.DeviceID)
	}

	switch {
	case pin != "":
		if err := client.Pair(ctx, pin); err != nil {
			return nil, s._setupFailed(client, "pairing failed: ", err)
		}
		s._saveCredentials(info.DeviceID, client)

	case saved != nil:
		s.logf("[AIRPLAY2] using saved credentials")
		pub, priv := saved.Ed25519Keys()
		client.PairingID = saved.PairingID
		client.PairKeys = &airplay.PairKeys{Ed25519Public: pub, Ed25519Private: priv}
		if err := client.PairVerify(ctx); err != nil {
			s.logf("[AIRPLAY2] pair-verify with saved creds failed: %v, falling back to transient pairing", err)
			// the failed pair-verify may have closed the connection
			client.Close()
			if err := client.Connect(ctx); err != nil {
				return nil, s._setupFailed(client, "reconnect: ", err)
			}
			if _, err := client.GetInfo(); err != nil {
				return nil, s._setupFailed(client, "getinfo after reconnect: ", err)
			}
			if err := client.PairTransientAuto(ctx); err != nil {
				return nil, s._requestPIN(client, err)
			}
		}

	default:
		if err := client.PairTransientAuto(ctx); err != nil {
			return nil, s._requestPIN(client, err)
		}
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

// upstream reconnects before pair-setup; here the UI hands the PIN to the next Connect
func (s *Session) _requestPIN(client *airplay.AirPlayClient, cause error) error {
	s.logf("[AIRPLAY2] transient pairing failed: %v, prompting for PIN", cause)
	if err := client.StartPINDisplay(); err != nil {
		s.logf("[AIRPLAY2] StartPINDisplay failed: %v", err)
	}
	client.Close()
	s.handler.OnPinRequired()
	return cause
}

func (s *Session) _setupFailed(client *airplay.AirPlayClient, prefix string, err error) error {
	s.handler.OnError(prefix + err.Error())
	client.Close()
	return err
}
