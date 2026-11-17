package engine

import (
	"strings"
	"context"

	"go.mau.fi/whatsmeow/types"
	"go.mau.fi/whatsmeow/types/events"
)

func (c *Client) handleHistorySync(e *events.HistorySync) {
	hs := e.Data
	if hs == nil {
		return
	}
	ctx := context.Background()

	for _, m := range hs.GetPhoneNumberToLidMappings() {
		pn, err1 := types.ParseJID(m.GetPnJID())
		lid, err2 := types.ParseJID(m.GetLidJID())
		if err1 == nil && err2 == nil {
			_ = c.inner.Store.LIDs.PutLIDMapping(ctx, lid, pn)
		}
	}

	for _, p := range hs.GetPushnames() {
		id, name := p.GetID(), p.GetPushname()
		if id == "" || name == "" {
			continue
		}
		if !strings.Contains(id, "@") {
			id += "@s.whatsapp.net"
		}
		c.cb.OnHistoryContact(c.normJIDStr(id), name)
	}

	for _, conv := range hs.GetConversations() {
		jid, err := types.ParseJID(conv.GetID())
		if err != nil || jid.Server == types.BroadcastServer {
			continue
		}
		if jid.Server == types.HiddenUserServer && conv.GetPnJID() != "" {
			if pn, err := types.ParseJID(conv.GetPnJID()); err == nil {
				jid = pn
			}
		}
		jid = c.normJID(jid)

		for _, hm := range conv.GetMessages() {
			wm := hm.GetMessage()
			if wm == nil {
				continue
			}
			evt, err := c.inner.ParseWebMessage(jid, wm)
			if err != nil {
				continue
			}
			c.dispatchMessage(evt)
		}
		name := conv.GetName()
		if name == "" {
			name = conv.GetDisplayName()
		}
		c.cb.OnHistoryChat(jid.String(), name, int64(conv.GetUnreadCount()))
	}

	c.cb.OnHistorySync(hs.GetSyncType().String(), int64(hs.GetProgress()))
}