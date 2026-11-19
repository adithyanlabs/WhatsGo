package engine

import (
	"context"

	"go.mau.fi/whatsmeow/types"
)

func (c *Client) SendPresenceAvailable() error {
	if c.inner == nil || c.inner.Store.ID == nil {
		return ErrNotLoggedInLocal
	}
	return c.inner.SendPresence(context.Background(), types.PresenceAvailable)
}

func (c *Client) SendPresenceUnavailable() error {
	if c.inner == nil || c.inner.Store.ID == nil {
		return ErrNotLoggedInLocal
	}
	return c.inner.SendPresence(context.Background(), types.PresenceUnavailable)
}

func (c *Client) SubscribePresence(chatJID string) error {
	if c.inner == nil || c.inner.Store.ID == nil {
		return ErrNotLoggedInLocal
	}
	jid, err := types.ParseJID(chatJID)
	if err != nil {
		return err
	}
	if jid.Server == types.GroupServer || jid.Server == types.BroadcastServer {
		return nil
	}
	ctx := context.Background()
	target := jid
	if jid.Server == types.DefaultUserServer && c.inner.Store.LIDs != nil {
		if lid, err := c.inner.Store.LIDs.GetLIDForPN(ctx, jid); err == nil && !lid.IsEmpty() {
			target = lid
		}
	}
	return c.inner.SubscribePresence(ctx, target)
}

func (c *Client) SendChatPresence(chatJID string, state string, media string) error {
	if c.inner == nil || c.inner.Store.ID == nil {
		return ErrNotLoggedInLocal
	}
	jid, err := types.ParseJID(chatJID)
	if err != nil {
		return err
	}
	if jid.Server == types.DefaultUserServer && c.inner.Store != nil && c.inner.Store.LIDs != nil {
		if lid, err := c.inner.Store.LIDs.GetLIDForPN(context.Background(), jid); err == nil && !lid.IsEmpty() {
			jid = lid
		}
	}
	var chatState types.ChatPresence
	switch state {
	case "composing":
		chatState = types.ChatPresenceComposing
	case "paused":
		chatState = types.ChatPresencePaused
	default:
		chatState = types.ChatPresencePaused
	}
	var mediaType types.ChatPresenceMedia
	if media == "audio" {
		mediaType = types.ChatPresenceMediaAudio
	}
	return c.inner.SendChatPresence(context.Background(), jid, chatState, mediaType)
}
