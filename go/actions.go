package engine

import (
	"context"
	"fmt"
	"sync"
	"time"

	"go.mau.fi/whatsmeow/proto/waE2E"
	"go.mau.fi/whatsmeow/types"
	"go.mau.fi/whatsmeow/types/events"
	"google.golang.org/protobuf/proto"
)

func (c *Client) dispatchMessage(e *events.Message) {
	c.dispatchContent(e)
	c.dispatchMeta(e)
}

func (c *Client) dispatchMeta(e *events.Message) {
	if e.Message == nil {
		return
	}
	if e.Info.Chat.User == "status" && e.Info.Chat.Server == types.BroadcastServer {
		return
	}
	m := e.Message
	chat := c.normJID(e.Info.Chat).String()

	// Delete for everyone (own revoke from another device, other user, or group admin)
	if pm := m.GetProtocolMessage(); pm != nil && pm.GetType() == waE2E.ProtocolMessage_REVOKE {
		if id := pm.GetKey().GetID(); id != "" {
			c.cb.OnMessageRevoked(chat, id, c.normJID(e.Info.Sender).String())
		}
		return
	}

	// Pin / unpin
	if pin := m.GetPinInChatMessage(); pin != nil {
		id := pin.GetKey().GetID()
		if id == "" {
			return
		}
		pinned := pin.GetType() == waE2E.PinInChatMessage_PIN_FOR_ALL
		at := e.Info.Timestamp.Unix()
		var expires int64
		if pinned {
			if d := m.GetMessageContextInfo().GetMessageAddOnDurationInSecs(); d > 0 {
				expires = at + int64(d)
			}
		}
		c.cb.OnMessagePinned(chat, id, pinned, at, expires)
		return
	}

	if isForwardedMsg(m) {
		c.cb.OnMessageForwarded(e.Info.ID)
	}
}

func isForwardedMsg(m *waE2E.Message) bool {
	var ci *waE2E.ContextInfo
	switch {
	case m.GetExtendedTextMessage() != nil:
		ci = m.GetExtendedTextMessage().GetContextInfo()
	case m.GetImageMessage() != nil:
		ci = m.GetImageMessage().GetContextInfo()
	case m.GetVideoMessage() != nil:
		ci = m.GetVideoMessage().GetContextInfo()
	case m.GetAudioMessage() != nil:
		ci = m.GetAudioMessage().GetContextInfo()
	case m.GetDocumentMessage() != nil:
		ci = m.GetDocumentMessage().GetContextInfo()
	}
	return ci.GetIsForwarded()
}

var forwardIDs sync.Map

func (c *Client) MarkForwarded(localID string) {
	if localID != "" {
		forwardIDs.Store(localID, true)
	}
}

func forwardCtx(localID string) *waE2E.ContextInfo {
	if localID == "" {
		return nil
	}
	if _, ok := forwardIDs.LoadAndDelete(localID); !ok {
		return nil
	}
	return &waE2E.ContextInfo{
		IsForwarded:     proto.Bool(true),
		ForwardingScore: proto.Uint32(1),
	}
}

func (c *Client) SendForwardedText(chatJid, text string) (string, error) {
	if c.inner.Store.ID == nil {
		return "", ErrNotLoggedInLocal
	}
	jid, err := types.ParseJID(chatJid)
	if err != nil {
		return "", err
	}
	msg := &waE2E.Message{ExtendedTextMessage: &waE2E.ExtendedTextMessage{
		Text: proto.String(text),
		ContextInfo: &waE2E.ContextInfo{
			IsForwarded:     proto.Bool(true),
			ForwardingScore: proto.Uint32(1),
		},
	}}
	sent, err := c.inner.SendMessage(context.Background(), jid, msg)
	if err != nil {
		c.log.Errorf("forward text failed to %s: %v", chatJid, err)
		return "", err
	}
	return fmt.Sprintf("%s|%d", sent.ID, sent.Timestamp.Unix()), nil
}

func (c *Client) participantFor(chat, sender types.JID) types.JID {
	if sender.IsEmpty() || chat.Server != types.GroupServer || sender.Server != types.DefaultUserServer {
		return sender
	}
	ctx := context.Background()
	gi, err := c.inner.GetGroupInfo(ctx, chat)
	if err != nil || gi.AddressingMode != types.AddressingModeLID {
		return sender
	}
	if c.inner.Store != nil && c.inner.Store.LIDs != nil {
		if lid, err := c.inner.Store.LIDs.GetLIDForPN(ctx, sender); err == nil && !lid.IsEmpty() {
			return lid
		}
	}
	return sender
}

func (c *Client) parseTarget(chatJid, senderJid string) (types.JID, types.JID, error) {
	chat, err := types.ParseJID(chatJid)
	if err != nil {
		return chat, types.EmptyJID, err
	}
	sender := types.EmptyJID
	if senderJid != "" {
		sender, err = types.ParseJID(senderJid)
		if err != nil {
			return chat, types.EmptyJID, err
		}
		sender = c.participantFor(chat, sender)
	}
	return chat, sender, nil
}

func (c *Client) DeleteForEveryone(chatJid, senderJid, messageID string) (string, error) {
	if c.inner.Store.ID == nil {
		return "", ErrNotLoggedInLocal
	}
	chat, sender, err := c.parseTarget(chatJid, senderJid)
	if err != nil {
		return "", err
	}
	sent, err := c.inner.SendMessage(context.Background(), chat, c.inner.BuildRevoke(chat, sender, messageID))
	if err != nil {
		c.log.Errorf("revoke failed in %s: %v", chatJid, err)
		return "", err
	}
	return fmt.Sprintf("%s|%d", sent.ID, sent.Timestamp.Unix()), nil
}

func (c *Client) PinMessage(chatJid, senderJid, messageID string, pin bool, durationSecs int64) (string, error) {
	if c.inner.Store.ID == nil {
		return "", ErrNotLoggedInLocal
	}
	chat, sender, err := c.parseTarget(chatJid, senderJid)
	if err != nil {
		return "", err
	}
	t := waE2E.PinInChatMessage_UNPIN_FOR_ALL
	dur := uint32(0)
	if pin {
		t = waE2E.PinInChatMessage_PIN_FOR_ALL
		dur = uint32(durationSecs)
	}
	msg := &waE2E.Message{
		PinInChatMessage: &waE2E.PinInChatMessage{
			Key:               c.inner.BuildMessageKey(chat, sender, messageID),
			Type:              t.Enum(),
			SenderTimestampMS: proto.Int64(time.Now().UnixMilli()),
		},
		MessageContextInfo: &waE2E.MessageContextInfo{
			MessageAddOnDurationInSecs: proto.Uint32(dur),
			MessageAddOnExpiryType:     waE2E.MessageContextInfo_STATIC.Enum(),
		},
	}
	sent, err := c.inner.SendMessage(context.Background(), chat, msg)
	if err != nil {
		c.log.Errorf("pin failed in %s: %v", chatJid, err)
		return "", err
	}
	return fmt.Sprintf("%s|%d", sent.ID, sent.Timestamp.Unix()), nil
}