package engine

import (
	"context"
	"encoding/json"
	"fmt"

	wa "go.mau.fi/whatsmeow"
	"go.mau.fi/whatsmeow/types"
	"go.mau.fi/whatsmeow/types/events"
)

type channelJSON struct {
	JID         string `json:"jid"`
	Name        string `json:"name"`
	Description string `json:"description"`
	Subscribers int64  `json:"subscribers"`
	Verified    bool   `json:"verified"`
	Muted       bool   `json:"muted"`
	PictureURL  string `json:"pictureUrl"`
}

type channelInfoJSON struct {
	channelJSON
	Invite string `json:"invite"`
	Role   string `json:"role"`
}

const waMediaHost = "https://mmg.whatsapp.net"

func (c *Client) GetSubscribedChannelsJSON() (string, error) {
	if c.inner == nil || c.inner.Store.ID == nil {
		return "", ErrNotLoggedInLocal
	}
	list, err := c.inner.GetSubscribedNewsletters(context.Background())
	if err != nil {
		return "", err
	}
	out := make([]channelJSON, 0, len(list))
	for _, n := range list {
		if n == nil || n.ThreadMeta.Name.Text == "" {
			continue
		}
		cj := channelJSON{
			JID:         n.ID.String(),
			Name:        n.ThreadMeta.Name.Text,
			Description: n.ThreadMeta.Description.Text,
			Subscribers: int64(n.ThreadMeta.SubscriberCount),
			Verified:    n.ThreadMeta.VerificationState == types.NewsletterVerificationStateVerified,
			PictureURL:  channelPicURL(n),
		}
		if n.ViewerMeta != nil {
			cj.Muted = n.ViewerMeta.Mute == types.NewsletterMuteOn
		}
		out = append(out, cj)
	}
	b, err := json.Marshal(out)
	return string(b), err
}

func (c *Client) LoadChannelHistory(chatJID string, count int64) error {
	if c.inner == nil || c.inner.Store.ID == nil {
		return ErrNotLoggedInLocal
	}
	jid, err := types.ParseJID(chatJID)
	if err != nil {
		return err
	}
	msgs, err := c.inner.GetNewsletterMessages(context.Background(), jid,
		&wa.GetNewsletterMessagesParams{Count: int(count)})
	if err != nil {
		return err
	}
	for _, m := range msgs {
		c.dispatchChannelMessage(jid, m)
	}
	return nil
}

func (c *Client) SubscribeChannelLive(chatJID string) (int64, error) {
	if c.inner == nil || c.inner.Store.ID == nil {
		return 0, ErrNotLoggedInLocal
	}
	jid, err := types.ParseJID(chatJID)
	if err != nil {
		return 0, err
	}
	d, err := c.inner.NewsletterSubscribeLiveUpdates(context.Background(), jid)
	if err != nil {
		return 0, err
	}
	return int64(d.Seconds()), nil
}

func (c *Client) handleChannelLive(e *events.NewsletterLiveUpdate) {
	for _, m := range e.Messages {
		c.dispatchChannelMessage(e.JID, m)
	}
}

func (c *Client) dispatchChannelMessage(jid types.JID, m *types.NewsletterMessage) {
	if m == nil || m.Message == nil {
		return
	}
	id := m.MessageID
	if id == "" {
		id = fmt.Sprintf("nl_%d", m.MessageServerID)
	}
	c.dispatchMessage(&events.Message{
		Info: types.MessageInfo{
			MessageSource: types.MessageSource{Chat: jid, Sender: jid},
			ID:            id,
			Timestamp:     m.Timestamp,
		},
		Message: m.Message,
	})
}

func (c *Client) GetChannelInfoJSON(chatJID string) (string, error) {
	if c.inner == nil || c.inner.Store.ID == nil {
		return "", ErrNotLoggedInLocal
	}
	jid, err := types.ParseJID(chatJID)
	if err != nil {
		return "", err
	}
	n, err := c.inner.GetNewsletterInfo(context.Background(), jid)
	if err != nil {
		return "", err
	}
	if n.ID.IsEmpty() {
		return "", fmt.Errorf("channel not found")
	}
	out := channelInfoJSON{
		channelJSON: channelJSON{
			JID:         n.ID.String(),
			Name:        n.ThreadMeta.Name.Text,
			Description: n.ThreadMeta.Description.Text,
			Subscribers: int64(n.ThreadMeta.SubscriberCount),
			Verified:    n.ThreadMeta.VerificationState == types.NewsletterVerificationStateVerified,
			PictureURL:  channelPicURL(n),
		},
		Invite: n.ThreadMeta.InviteCode,
	}

	if n.ViewerMeta != nil {
		out.Muted = n.ViewerMeta.Mute == types.NewsletterMuteOn
		out.Role = string(n.ViewerMeta.Role)
	}
	b, err := json.Marshal(out)
	return string(b), err
}

func channelPicURL(n *types.NewsletterMetadata) string {
	if p := n.ThreadMeta.Picture; p != nil {
		if p.URL != "" {
			return p.URL
		}
		if p.DirectPath != "" {
			return waMediaHost + p.DirectPath
		}
	}
	if n.ThreadMeta.Preview.URL != "" {
		return n.ThreadMeta.Preview.URL
	}
	if n.ThreadMeta.Preview.DirectPath != "" {
		return waMediaHost + n.ThreadMeta.Preview.DirectPath
	}
	return ""
}
